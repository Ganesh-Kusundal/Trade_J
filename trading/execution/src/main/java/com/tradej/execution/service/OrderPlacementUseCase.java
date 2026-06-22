package com.tradej.execution.service;

import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.event.EventMetadata;
import com.tradej.core.domain.event.KillSwitchEngaged;
import com.tradej.core.domain.event.OrderAccepted;
import com.tradej.core.domain.event.OrderFilled;
import com.tradej.core.domain.event.SignalPendingExecution;
import com.tradej.core.domain.event.SignalSuppressed;
import com.tradej.core.domain.model.Order;
import com.tradej.core.domain.model.OrderRequest;
import com.tradej.core.domain.oms.OrderAcknowledged;
import com.tradej.core.domain.oms.OrderSubmitted;
import com.tradej.core.domain.runtime.RuntimeModeHolder;
import com.tradej.core.domain.time.TradingClock;
import com.tradej.core.domain.value.OrderStatus;
import com.tradej.execution.identity.OrderIdentityRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Places broker orders for pending execution signals and emits order outcomes.
 */
public final class OrderPlacementUseCase {

    private static final Logger log = LoggerFactory.getLogger(OrderPlacementUseCase.class);

    private final OrderManagementService orderManagementService;
    private final RuntimeModeHolder runtimeModeHolder;
    private final TradingClock clock;
    private final TradingCircuitBreaker circuitBreaker;
    private final OrderIdentityRegistry identityRegistry;
    private final long orderPlacementTimeoutMs;
    private final AtomicLong orderIdSequence = new AtomicLong();

    public OrderPlacementUseCase(
            OrderManagementService orderManagementService,
            RuntimeModeHolder runtimeModeHolder,
            TradingClock clock,
            TradingCircuitBreaker circuitBreaker,
            OrderIdentityRegistry identityRegistry,
            long orderPlacementTimeoutMs
    ) {
        this.orderManagementService = orderManagementService;
        this.runtimeModeHolder = runtimeModeHolder;
        this.clock = clock;
        this.circuitBreaker = circuitBreaker;
        this.identityRegistry = identityRegistry;
        this.orderPlacementTimeoutMs = orderPlacementTimeoutMs;
    }

    public void place(SignalPendingExecution pendingExecution, Consumer<DomainEvent> downstream) {
        if (!circuitBreaker.allowsRequest()) {
            log.warn("Circuit breaker open — suppressing signalId={}", pendingExecution.signalId());
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

        String orderId = generateOrderId();
        OrderRequest orderRequest = pendingExecution.orderRequest();
        orderManagementService.onBrokerEvent(OrderSubmitted.create(
                orderId,
                pendingExecution.signalId(),
                orderRequest.symbol(),
                orderRequest.quantity()
        ));

        identityRegistry.register(orderId, null, pendingExecution.signalId());

        try {
            Order order = placeOrderWithTimeout(orderRequest);
            circuitBreaker.recordSuccess();

            if (order.status().isRejected()) {
                handleRejectedOrder(orderId, pendingExecution, order, downstream);
            } else {
                handleAcceptedOrder(orderId, pendingExecution, order, downstream);
            }
        } catch (Exception exception) {
            handlePlacementFailure(orderId, pendingExecution, exception, downstream);
        }
    }

    private void handleRejectedOrder(
            String orderId,
            SignalPendingExecution pendingExecution,
            Order order,
            Consumer<DomainEvent> downstream
    ) {
        log.warn("Order rejected by broker orderId={} reason={}", orderId, order.rejectionReason());
        var rejected = new com.tradej.core.domain.event.OrderRejected(
                EventMetadata.correlated(order.correlationId(), pendingExecution.sequenceId()),
                order,
                order.rejectionReason()
        );
        orderManagementService.onBrokerEvent(rejected.toOsmEvent(orderId));
        identityRegistry.remove(orderId);
        downstream.accept(rejected);
    }

    private void handleAcceptedOrder(
            String orderId,
            SignalPendingExecution pendingExecution,
            Order order,
            Consumer<DomainEvent> downstream
    ) {
        log.info("Order accepted by broker orderId={} brokerOrderId={} symbol={} qty={}",
                orderId, order.orderId(), order.symbol(), order.quantity());
        orderManagementService.onBrokerEvent(OrderAcknowledged.event(orderId, order.orderId()));
        identityRegistry.acknowledge(orderId, order.orderId());
        downstream.accept(new OrderAccepted(
                EventMetadata.correlated(order.correlationId(), pendingExecution.sequenceId()),
                order
        ));
        publishSimulatedFillIfNeeded(order, pendingExecution, downstream);
    }

    private void handlePlacementFailure(
            String orderId,
            SignalPendingExecution pendingExecution,
            Exception exception,
            Consumer<DomainEvent> downstream
    ) {
        log.error("Order placement failed signalId={} error={}",
                pendingExecution.signalId(), exception.getMessage(), exception);
        circuitBreaker.recordFailure();
        orderManagementService.onBrokerEvent(new com.tradej.core.domain.oms.OrderRejected(
                orderId,
                "Order placement failed: " + exception.getMessage()
        ));
        identityRegistry.remove(orderId);
        downstream.accept(new SignalSuppressed(
                EventMetadata.correlated(pendingExecution.signalId(), pendingExecution.sequenceId()),
                pendingExecution.signalId(),
                pendingExecution.orderRequest().symbol(),
                "Order placement failed: " + exception.getMessage(),
                pendingExecution.decisionContext()
        ));
    }

    private void publishSimulatedFillIfNeeded(
            Order order,
            SignalPendingExecution pendingExecution,
            Consumer<DomainEvent> downstream
    ) {
        if (!runtimeModeHolder.mode().usesSimulatedExecution() || order.status() != OrderStatus.TRADED) {
            return;
        }
        orderManagementService.lastSimulatedMatch().ifPresent(match -> {
            if (!match.fills().isEmpty()) {
                downstream.accept(new OrderFilled(
                        EventMetadata.correlated(order.correlationId(), pendingExecution.sequenceId()),
                        order,
                        match.fills()
                ));
            }
        });
    }

    private Order placeOrderWithTimeout(OrderRequest request) throws Exception {
        CompletableFuture<Order> placement = CompletableFuture.supplyAsync(
                () -> orderManagementService.placeOrder(request));
        try {
            return placement.get(orderPlacementTimeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            placement.cancel(true);
            log.warn("Order placement timed out after {}ms", orderPlacementTimeoutMs);
            throw new RuntimeException("Order placement timed out after "
                    + orderPlacementTimeoutMs + "ms", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (cause instanceof RuntimeException re) throw re;
            throw new RuntimeException("Order placement failed", cause);
        }
    }

    private String generateOrderId() {
        return "ORD-" + clock.millis() + "-" + orderIdSequence.incrementAndGet();
    }
}
