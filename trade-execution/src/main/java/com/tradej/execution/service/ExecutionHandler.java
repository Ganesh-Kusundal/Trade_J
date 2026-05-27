package com.tradej.execution.service;

import org.springframework.stereotype.Service;
import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.event.EventMetadata;
import com.tradej.core.domain.event.KillSwitchEngaged;
import com.tradej.core.domain.event.OrderAccepted;
import com.tradej.core.domain.event.OrderFilled;
import com.tradej.core.domain.event.OrderRejected;
import com.tradej.core.domain.event.SignalPendingExecution;
import com.tradej.core.domain.event.SignalSuppressed;
import com.tradej.core.domain.event.TradeOpened;
import com.tradej.core.domain.model.Order;
import com.tradej.core.domain.model.OrderRequest;
import com.tradej.core.domain.model.Trade;
import com.tradej.core.domain.oms.OrderAcknowledged;
import com.tradej.core.domain.oms.OrderFullyFilled;
import com.tradej.core.domain.oms.OrderProjection;
import com.tradej.core.domain.oms.OrderSubmitted;
import com.tradej.core.domain.value.Side;
import com.tradej.persistence.oms.EventSourcedOrderRepository;

import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

@Service
public final class ExecutionHandler {
    private final BlockingQueue<ExecutionCommand> queue = new ArrayBlockingQueue<>(50);
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "execution-handler");
        thread.setDaemon(true);
        return thread;
    });
    private final EventSourcedOrderRepository omsRepo;
    private final OrderManagementService orderManagementService;
    private final TradingCircuitBreaker circuitBreaker;
    private volatile boolean running;
    // Test-only synchronization hook — counted down after each process() call
    private volatile CountDownLatch processingLatch;

    public ExecutionHandler(
            EventSourcedOrderRepository omsRepo,
            OrderManagementService orderManagementService,
            TradingCircuitBreaker circuitBreaker
    ) {
        this.omsRepo = omsRepo;
        this.orderManagementService = orderManagementService;
        this.circuitBreaker = circuitBreaker;
    }

    public void start() {
        if (running) {
            return;
        }
        running = true;
        executor.submit(this::runLoop);
    }

    public void stop() {
        running = false;
        executor.shutdownNow();
    }

    /**
     * Set a latch that is counted down after each event is processed.
     * Used for test synchronization to avoid brittle Thread.sleep().
     */
    void setProcessingLatch(CountDownLatch latch) {
        this.processingLatch = latch;
    }

    public void onDomainEvent(DomainEvent event, Consumer<DomainEvent> downstream) {
        if (event instanceof SignalPendingExecution pendingExecution) {
            if (!queue.offer(new ExecutionCommand(pendingExecution, downstream))) {
                downstream.accept(new SignalSuppressed(
                        EventMetadata.correlated(pendingExecution.signalId(), pendingExecution.sequenceId()),
                        pendingExecution.signalId(),
                        pendingExecution.orderRequest().symbol(),
                        "Execution queue full",
                        pendingExecution.decisionContext()
                ));
            }
            return;
        }
        if (event instanceof OrderFilled orderFilled) {
            handleOmsFilled(orderFilled, downstream);
        }
    }

    private void runLoop() {
        while (running) {
            try {
                ExecutionCommand command = queue.take();
                process(command.pendingExecution(), command.downstream());
                CountDownLatch latch = this.processingLatch;
                if (latch != null) {
                    latch.countDown();
                }
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private void process(SignalPendingExecution pendingExecution, Consumer<DomainEvent> downstream) {
        if (!circuitBreaker.allowsRequest()) {
            downstream.accept(new KillSwitchEngaged(
                    EventMetadata.correlated(pendingExecution.signalId(), pendingExecution.sequenceId()),
                    "Execution circuit breaker open",
                    0L,
                    0L,
                    0L,
                    0
            ));
            return;
        }

        // Generate order ID and append OrderSubmitted to OSM
        String orderId = generateOrderId();
        var orderRequest = pendingExecution.orderRequest();
        omsRepo.append(OrderSubmitted.create(
                orderId,
                pendingExecution.signalId(),
                orderRequest.symbol(),
                orderRequest.quantity()
        ));

        try {
            Order order = orderManagementService.placeOrder(orderRequest);
            circuitBreaker.recordSuccess();

            if (order.status().isRejected()) {
                omsRepo.append(new com.tradej.core.domain.oms.OrderRejected(orderId, order.rejectionReason()));
                downstream.accept(new OrderRejected(
                        EventMetadata.correlated(order.correlationId(), pendingExecution.sequenceId()),
                        order,
                        order.rejectionReason()
                ));
            } else {
                omsRepo.append(OrderAcknowledged.event(orderId, order.orderId()));
                downstream.accept(new OrderAccepted(
                        EventMetadata.correlated(order.correlationId(), pendingExecution.sequenceId()),
                        order
                ));
            }
        } catch (Exception exception) {
            circuitBreaker.recordFailure();
            omsRepo.append(new com.tradej.core.domain.oms.OrderRejected(
                    orderId,
                    "Order placement failed: " + exception.getMessage()
            ));
            downstream.accept(new SignalSuppressed(
                    EventMetadata.correlated(pendingExecution.signalId(), pendingExecution.sequenceId()),
                    pendingExecution.signalId(),
                    pendingExecution.orderRequest().symbol(),
                    "Order placement failed: " + exception.getMessage(),
                    pendingExecution.decisionContext()
            ));
        }
    }

    private void handleOmsFilled(OrderFilled orderFilled, Consumer<DomainEvent> downstream) {
        Order order = orderFilled.order();
        String orderId = order.orderId();

        // Guard against empty fills (should not happen, but be defensive)
        if (orderFilled.fills().isEmpty()) {
            emitTradeOpened(orderFilled, downstream);
            return;
        }

        // Rebuild current OSM state
        OrderProjection projection = omsRepo.rebuild(orderId);
        if (projection == null) {
            // Order not tracked by OSM — emit TradeOpened directly for backward compat
            emitTradeOpened(orderFilled, downstream);
            return;
        }

        // Compute fill quantity from fills
        long fillQty = orderFilled.fills().stream().mapToLong(Trade::quantity).sum();
        long filledSoFar = projection.filledQuantity() + fillQty;
        long totalQty = projection.totalQuantity();
        long avgPrice = orderFilled.fills().stream().mapToLong(Trade::pricePaisa).sum() / orderFilled.fills().size();

        if (filledSoFar >= totalQty) {
            omsRepo.append(OrderFullyFilled.event(orderId, totalQty, avgPrice));
        } else {
            omsRepo.append(new com.tradej.core.domain.oms.OrderPartiallyFilled(orderId, fillQty, avgPrice));
        }

        emitTradeOpened(orderFilled, downstream);
    }

    private void emitTradeOpened(OrderFilled orderFilled, Consumer<DomainEvent> downstream) {
        Order order = orderFilled.order();
        Trade fill = orderFilled.fills().isEmpty() ? null : orderFilled.fills().get(0);
        long fillPrice = fill != null ? fill.pricePaisa() : order.pricePaisa();
        long fillQty = fill != null ? fill.quantity() : order.filledQuantity();
        downstream.accept(new TradeOpened(
                EventMetadata.correlated(order.correlationId(), orderFilled.sequenceId()),
                order.orderId(),
                order.orderId(),
                order.correlationId(),
                order.symbol(),
                order.side() == Side.SELL ? Side.SHORT : Side.LONG,
                fillQty,
                fillPrice,
                0L,
                0L
        ));
    }

    private static String generateOrderId() {
        return "ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private record ExecutionCommand(SignalPendingExecution pendingExecution, Consumer<DomainEvent> downstream) {
    }
}
