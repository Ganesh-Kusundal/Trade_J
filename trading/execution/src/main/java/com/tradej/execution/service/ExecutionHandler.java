package com.tradej.execution.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.event.DomainEventVisitor;
import com.tradej.core.domain.event.EventMetadata;
import com.tradej.core.domain.event.KillSwitchEngaged;
import com.tradej.core.domain.event.OrderAccepted;
import com.tradej.core.domain.event.OrderFilled;
import com.tradej.core.domain.event.OrderRejected;
import com.tradej.core.domain.event.SignalPendingExecution;
import com.tradej.core.domain.event.SignalSuppressed;
import com.tradej.core.domain.event.TradeOpened;
import com.tradej.core.domain.event.TradeUpdated;
import com.tradej.core.domain.model.Order;
import com.tradej.core.domain.model.OrderRequest;
import com.tradej.core.domain.model.Trade;
import com.tradej.core.domain.oms.OrderAcknowledged;
import com.tradej.core.domain.oms.OrderEvent;
import com.tradej.core.domain.oms.OrderFullyFilled;
import com.tradej.core.domain.oms.OrderPartiallyFilled;
import com.tradej.core.domain.oms.OrderProjection;
import com.tradej.core.domain.oms.OrderSubmitted;
import com.tradej.core.domain.port.DeadLetterQueue;
import com.tradej.core.domain.runtime.RuntimeModeHolder;
import com.tradej.core.domain.time.TradingClock;
import com.tradej.core.domain.value.FillReconciliation;
import com.tradej.core.domain.value.OrderStatus;
import com.tradej.core.domain.value.Side;
import com.tradej.core.support.MdcHelper;
import com.tradej.execution.identity.OrderIdentityRegistry;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.stream.LongStream;

public final class ExecutionHandler implements com.tradej.core.domain.event.DomainEventVisitor {
    private static final Logger log = LoggerFactory.getLogger(ExecutionHandler.class);
    private static final int MAX_FILL_DEFER_ATTEMPTS = FillReconciliation.MAX_FILL_DEFER_ATTEMPTS;
    private static final long FILL_DEFER_DELAY_MS = FillReconciliation.FILL_DEFER_DELAY_MS;
    /** Default execution queue capacity — increased from 50 to 1000 (E-01 fix). */
    private static final int DEFAULT_QUEUE_CAPACITY = 1000;

    /** Maximum time to wait for a broker order placement response. */
    private static final long DEFAULT_ORDER_PLACEMENT_TIMEOUT_MS = 10_000L;

    /** Default number of execution partitions (W1-5). */
    private static final int DEFAULT_PARTITION_COUNT = 4;

    private final long orderPlacementTimeoutMs;
    private final int partitionCount;
    private final BlockingQueue<ExecutionCommand>[] queues;
    private final ExecutorService[] executors;
    private final ScheduledExecutorService fillDeferExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "execution-fill-defer");
        thread.setDaemon(true);
        return thread;
    });
    private final OrderManagementService orderManagementService;
    private final RuntimeModeHolder runtimeModeHolder;
    private final TradingClock clock;
    private final TradingCircuitBreaker circuitBreaker;
    private final OrderIdentityRegistry identityRegistry;
    private final DeadLetterQueue deadLetterQueue;
    private final AtomicLong droppedFillCount = new AtomicLong();
    /** Tracks which orders have already emitted TradeOpened, so subsequent fills emit TradeUpdated. */
    private final Cache<String, Boolean> tradeOpenedEmitted = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(Duration.ofHours(24))
            .build();
    private final AtomicLong orderIdSequence = new AtomicLong();
    private volatile boolean running;
    private volatile CountDownLatch processingLatch;

    /**
     * Primary constructor — accepts an {@link ExecutionConfig} for all tunable parameters.
     *
     * @param config execution configuration (queue capacity, timeout, partitions, downstream)
     */
    public ExecutionHandler(
            OrderManagementService orderManagementService,
            RuntimeModeHolder runtimeModeHolder,
            TradingClock clock,
            TradingCircuitBreaker circuitBreaker,
            OrderIdentityRegistry identityRegistry,
            DeadLetterQueue deadLetterQueue,
            ExecutionConfig config
    ) {
        this.orderManagementService = orderManagementService;
        this.runtimeModeHolder = runtimeModeHolder;
        this.clock = clock;
        this.circuitBreaker = circuitBreaker;
        this.identityRegistry = identityRegistry;
        this.deadLetterQueue = deadLetterQueue == null ? DeadLetterQueue.noop() : deadLetterQueue;
        this.orderPlacementTimeoutMs = config.orderPlacementTimeoutMs() > 0
                ? config.orderPlacementTimeoutMs()
                : ExecutionConfig.DEFAULT_ORDER_PLACEMENT_TIMEOUT_MS;
        this.injectedDownstream = config.downstream();
        this.partitionCount = Math.max(1, config.partitionCount());
        @SuppressWarnings("unchecked")
        BlockingQueue<ExecutionCommand>[] qs = new BlockingQueue[this.partitionCount];
        this.queues = qs;
        this.executors = new ExecutorService[this.partitionCount];
        for (int i = 0; i < this.partitionCount; i++) {
            this.queues[i] = new ArrayBlockingQueue<>(config.queueCapacity());
            final int idx = i;
            this.executors[i] = Executors.newSingleThreadExecutor(r -> {
                Thread thread = new Thread(r, "execution-handler-" + idx);
                thread.setDaemon(true);
                return thread;
            });
        }
    }

    public void start() {
        if (running) {
            return;
        }
        running = true;
        for (int i = 0; i < partitionCount; i++) {
            final int idx = i;
            executors[idx].submit(() -> runLoop(idx));
        }
    }

    public void stop() {
        running = false;
        for (ExecutorService exec : executors) {
            exec.shutdownNow();
        }
        fillDeferExecutor.shutdownNow();
    }

    public int queueDepth() {
        int total = 0;
        for (BlockingQueue<ExecutionCommand> q : queues) {
            total += q.size();
        }
        return total;
    }

    public int queueRemainingCapacity() {
        int min = Integer.MAX_VALUE;
        for (BlockingQueue<ExecutionCommand> q : queues) {
            min = Math.min(min, q.remainingCapacity());
        }
        return min;
    }

    public long droppedFillCount() {
        return droppedFillCount.get();
    }

    void setProcessingLatch(CountDownLatch latch) {
        this.processingLatch = latch;
    }

    /**
     * Processes a domain event using the constructor-injected downstream consumer (P0-6).
     *
     * @param event the domain event to process
     */
    public void onDomainEvent(DomainEvent event) {
        MdcHelper.enrich(event, "execution");
        try {
            event.accept(this);
        } finally {
            MdcHelper.clear();
        }
    }

    // P0-6: Downstream consumer is constructor-injected via ExecutionConfig.
    // Falls back to no-op if not configured (e.g., simple constructors).
    private final Consumer<DomainEvent> injectedDownstream;

    private Consumer<DomainEvent> effectiveDownstream() {
        if (injectedDownstream != null) return injectedDownstream;
        return e -> {};
    }

    @Override
    public void visit(SignalPendingExecution pendingExecution) {
        log.info("Signal enqueued symbol={} signalId={}", pendingExecution.orderRequest().symbol(), pendingExecution.signalId());
        int partition = partitionFor(pendingExecution.orderRequest().symbol());
        if (!queues[partition].offer(new SignalCommand(pendingExecution, effectiveDownstream()))) {
            log.warn("Execution queue full — suppressing signal symbol={} signalId={}",
                    pendingExecution.orderRequest().symbol(), pendingExecution.signalId());
            deadLetterQueue.append("execution-handler", pendingExecution, "Execution queue full");
            effectiveDownstream().accept(new SignalSuppressed(
                    EventMetadata.correlated(pendingExecution.signalId(), pendingExecution.sequenceId()),
                    pendingExecution.signalId(),
                    pendingExecution.orderRequest().symbol(),
                    "Execution queue full",
                    pendingExecution.decisionContext()
            ));
        }
    }

    @Override
    public void visit(OrderFilled orderFilled) {
        int partition = partitionFor(orderFilled.order().symbol());
        if (!queues[partition].offer(new FillCommand(orderFilled, effectiveDownstream(), 0))) {
            log.warn("Execution queue full — deferring fill eventId={} orderId={}",
                    orderFilled.eventId(), orderFilled.order().orderId());
            scheduleFillRetry(orderFilled, effectiveDownstream(), 0);
        }
    }

    @Override
    public void visit(com.tradej.core.domain.event.OrderPartiallyFilled partial) {
        int partition = partitionFor(partial.order().symbol());
        if (!queues[partition].offer(new BrokerFillCommand(partial.order(), partial.metadata(), partial.fills(), false, effectiveDownstream(), 0))) {
            scheduleBrokerFillRetry(partial.order(), partial.metadata(), partial.fills(), false, effectiveDownstream(), 0);
        }
    }

    @Override
    public void visit(com.tradej.core.domain.event.OrderFullyFilled fullyFilled) {
        int partition = partitionFor(fullyFilled.order().symbol());
        if (!queues[partition].offer(new BrokerFillCommand(
                fullyFilled.order(), fullyFilled.metadata(), fullyFilled.fills(), true, effectiveDownstream(), 0))) {
            scheduleBrokerFillRetry(
                    fullyFilled.order(), fullyFilled.metadata(), fullyFilled.fills(), true, effectiveDownstream(), 0);
        }
    }

    /** Routes a symbol to its partition index. */
    private int partitionFor(String symbol) {
        if (symbol == null) return 0;
        return (symbol.hashCode() & 0x7FFFFFFF) % partitionCount;
    }

    private void scheduleFillRetry(OrderFilled orderFilled, Consumer<DomainEvent> downstream, int nextAttempt) {
        if (nextAttempt >= MAX_FILL_DEFER_ATTEMPTS) {
            droppedFillCount.incrementAndGet();
            deadLetterQueue.append("execution-handler", orderFilled, "Fill defer attempts exhausted");
            log.error("Fill dropped after {} defer attempts brokerOrderId={}", nextAttempt, orderFilled.order().orderId());
            return;
        }
        int scheduledAttempt = nextAttempt + 1;
        fillDeferExecutor.schedule(() -> {
            int partition = partitionFor(orderFilled.order().symbol());
            if (!queues[partition].offer(new FillCommand(orderFilled, downstream, scheduledAttempt))) {
                scheduleFillRetry(orderFilled, downstream, scheduledAttempt);
            }
        }, FILL_DEFER_DELAY_MS, TimeUnit.MILLISECONDS);
    }

    private void runLoop(int partitionIndex) {
        BlockingQueue<ExecutionCommand> partitionQueue = queues[partitionIndex];
        while (running) {
            try {
                ExecutionCommand command = partitionQueue.take();
                process(command);
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

    private void process(ExecutionCommand command) {
        switch (command) {
            case SignalCommand cmd -> processSignal(cmd.pendingExecution(), cmd.downstream());
            case FillCommand cmd -> processFill(cmd.orderFilled(), cmd.downstream(), cmd.deferAttempts());
            case BrokerFillCommand cmd -> processBrokerFill(
                    cmd.order(), cmd.metadata(), cmd.fills(), cmd.fullyFilled(), cmd.downstream(), cmd.deferAttempts());
        }
    }

    private void scheduleBrokerFillRetry(
            Order order,
            EventMetadata metadata,
            java.util.List<Trade> fills,
            boolean fullyFilled,
            Consumer<DomainEvent> downstream,
            int nextAttempt
    ) {
        if (nextAttempt >= MAX_FILL_DEFER_ATTEMPTS) {
            droppedFillCount.incrementAndGet();
            deadLetterQueue.append("execution-handler",
                    new OrderFilled(metadata, order, fills), "Broker fill defer attempts exhausted");
            log.error("Broker fill dropped after {} defer attempts brokerOrderId={}", nextAttempt, order.orderId());
            return;
        }
        int scheduledAttempt = nextAttempt + 1;
        fillDeferExecutor.schedule(() -> {
            int partition = partitionFor(order.symbol());
            if (!queues[partition].offer(new BrokerFillCommand(order, metadata, fills, fullyFilled, downstream, scheduledAttempt))) {
                scheduleBrokerFillRetry(order, metadata, fills, fullyFilled, downstream, scheduledAttempt);
            }
        }, FILL_DEFER_DELAY_MS, TimeUnit.MILLISECONDS);
    }

    private void processBrokerFill(
            Order order,
            EventMetadata metadata,
            java.util.List<Trade> fills,
            boolean fullyFilled,
            Consumer<DomainEvent> downstream,
            int deferAttempts
    ) {
        try {
            String internalOrderId = resolveInternalOrderId(order);
            if (internalOrderId == null) {
                if (deferAttempts < MAX_FILL_DEFER_ATTEMPTS) {
                    scheduleBrokerFillRetry(order, metadata, fills, fullyFilled, downstream, deferAttempts);
                    return;
                }
                droppedFillCount.incrementAndGet();
                deadLetterQueue.append("execution-handler",
                        new OrderFilled(metadata, order, fills), "Broker fill identity unavailable");
                return;
            }

            OrderProjection projection = orderManagementService.getOrderProjection(internalOrderId).orElse(null);
            if (projection == null) {
                log.warn("No OSM projection for broker fill internalOrderId={} brokerOrderId={}",
                        internalOrderId, order.orderId());
                return;
            }

            long fillQty = fills.isEmpty()
                    ? Math.max(0L, order.filledQuantity() - projection.filledQuantity())
                    : fills.stream().mapToLong(Trade::quantity).sum();
            if (fillQty <= 0 && !fullyFilled) {
                log.debug("Skipping zero-qty broker fill update orderId={}", order.orderId());
                return;
            }
            long avgPrice = fills.isEmpty()
                    ? order.pricePaisa()
                    : Math.round((double) fills.stream().mapToLong(Trade::pricePaisa).sum() / fills.size());
            long filledSoFar = projection.filledQuantity() + fillQty;
            long totalQty = projection.totalQuantity();

            if (fullyFilled || filledSoFar >= totalQty) {
                long reportQty = fullyFilled ? Math.max(totalQty, order.filledQuantity()) : totalQty;
                orderManagementService.onBrokerEvent(OrderFullyFilled.event(internalOrderId, reportQty, avgPrice));
                downstream.accept(new com.tradej.core.domain.event.OrderFullyFilled(metadata, order, fills));
            } else {
                orderManagementService.onBrokerEvent(OrderPartiallyFilled.event(internalOrderId, fillQty, avgPrice));
                downstream.accept(new com.tradej.core.domain.event.OrderPartiallyFilled(metadata, order, fills));
            }

            OrderFilled synthetic = new OrderFilled(metadata, order, fills);
            emitTradeOpened(internalOrderId, order, synthetic, downstream);
        } finally {
            MdcHelper.clear();
        }
    }

    private void processSignal(SignalPendingExecution pendingExecution, Consumer<DomainEvent> downstream) {
        try {
            MdcHelper.enrich(pendingExecution, "execution");
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
            var orderRequest = pendingExecution.orderRequest();
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
                    log.warn("Order rejected by broker orderId={} reason={}", orderId, order.rejectionReason());
                    var rejected = new com.tradej.core.domain.event.OrderRejected(
                            EventMetadata.correlated(order.correlationId(), pendingExecution.sequenceId()),
                            order,
                            order.rejectionReason()
                    );
                    orderManagementService.onBrokerEvent(rejected.toOsmEvent(orderId));
                    identityRegistry.remove(orderId);
                    downstream.accept(rejected);
                } else {
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
            } catch (Exception exception) {
                log.error("Order placement failed signalId={} error={}", pendingExecution.signalId(), exception.getMessage(), exception);
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
        } finally {
            MdcHelper.clear();
        }
    }

    private void processFill(OrderFilled orderFilled, Consumer<DomainEvent> downstream, int deferAttempts) {
        MdcHelper.enrich(orderFilled, "execution");
        try {
            Order order = orderFilled.order();
            String internalOrderId = resolveInternalOrderId(order);

            if (internalOrderId == null) {
                if (deferAttempts < MAX_FILL_DEFER_ATTEMPTS) {
                    log.warn("Fill identity not yet available brokerOrderId={} correlationId={} attempt={} — deferring",
                            order.orderId(), order.correlationId(), deferAttempts);
                    scheduleFillRetry(orderFilled, downstream, deferAttempts);
                    return;
                }
                droppedFillCount.incrementAndGet();
                deadLetterQueue.append("execution-handler", orderFilled, "Identity mapping unavailable after defer");
                log.error("Fill dropped — no identity mapping brokerOrderId={} correlationId={}",
                        order.orderId(), order.correlationId());
                return;
            }

            if (orderFilled.fills().isEmpty()) {
                log.warn("Empty fill list for internalOrderId={}", internalOrderId);
                emitTradeOpened(internalOrderId, order, orderFilled, downstream);
                return;
            }

            OrderProjection projection = orderManagementService.getOrderProjection(internalOrderId).orElse(null);
            if (projection == null) {
                log.warn("No OSM projection found for internalOrderId={} brokerOrderId={}", internalOrderId, order.orderId());
                emitTradeOpened(internalOrderId, order, orderFilled, downstream);
                return;
            }

            long fillQty = orderFilled.fills().stream().mapToLong(Trade::quantity).sum();
            long filledSoFar = projection.filledQuantity() + fillQty;
            long totalQty = projection.totalQuantity();
            long[] prices = orderFilled.fills().stream().mapToLong(Trade::pricePaisa).toArray();
            long avgPrice = prices.length > 0
                    ? Math.round((double) LongStream.of(prices).sum() / prices.length)
                    : 0L;

            if (filledSoFar >= totalQty) {
                // Use >= to handle the case where the broker reports a cumulative fill
                // that matches or exceeds the full order quantity
                orderManagementService.onBrokerEvent(OrderFullyFilled.event(internalOrderId, totalQty, avgPrice));
                downstream.accept(new com.tradej.core.domain.event.OrderFullyFilled(
                        EventMetadata.correlated(order.correlationId(), orderFilled.sequenceId()),
                        order,
                        orderFilled.fills()
                ));
            } else {
                orderManagementService.onBrokerEvent(OrderPartiallyFilled.event(internalOrderId, fillQty, avgPrice));
                downstream.accept(new com.tradej.core.domain.event.OrderPartiallyFilled(
                        EventMetadata.correlated(order.correlationId(), orderFilled.sequenceId()),
                        order,
                        orderFilled.fills()
                ));
            }

            emitTradeOpened(internalOrderId, order, orderFilled, downstream);
        } finally {
            MdcHelper.clear();
        }
    }

    private String resolveInternalOrderId(Order order) {
        String internal = identityRegistry.resolveInternalId(order.orderId());
        if (internal != null) {
            return internal;
        }
        if (order.correlationId() != null && !order.correlationId().isBlank()) {
            return identityRegistry.resolveBySignalId(order.correlationId());
        }
        return null;
    }

    /**
     * Places an order with a bounded timeout so a hung broker connection
     * cannot block the single-threaded execution executor indefinitely.
     */
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

    private void emitTradeOpened(String orderId, Order order, OrderFilled orderFilled,
                                  Consumer<DomainEvent> downstream) {
        // Guard: only emit TradeOpened once per order (fixes C-05).
        // Subsequent fills emit TradeUpdated instead.
        if (tradeOpenedEmitted.asMap().putIfAbsent(orderId, Boolean.TRUE) != null) {
            Trade fill = orderFilled.fills().isEmpty() ? null : orderFilled.fills().get(0);
            long fillPrice = fill != null ? fill.pricePaisa() : order.pricePaisa();
            long fillQty = fill != null ? fill.quantity() : 0L;
            long unrealizedPnl = 0L;
            // P0-5: Skip zero-value TradeUpdated placeholders
            if (fillQty == 0 && unrealizedPnl == 0) {
                return;
            }
            downstream.accept(new TradeUpdated(
                    EventMetadata.correlated(order.correlationId(), orderFilled.sequenceId()),
                    orderId,
                    order.symbol(),
                    fillPrice,
                    unrealizedPnl,
                    0L
            ));
            return;
        }

        Trade fill = orderFilled.fills().isEmpty() ? null : orderFilled.fills().get(0);
        long fillPrice = fill != null ? fill.pricePaisa() : order.pricePaisa();
        long fillQty = fill != null ? fill.quantity() : order.filledQuantity();
        downstream.accept(new TradeOpened(
                EventMetadata.correlated(order.correlationId(), orderFilled.sequenceId()),
                orderId,
                orderId,
                order.correlationId(),
                order.symbol(),
                order.side() == Side.SELL ? Side.SHORT : Side.LONG,
                fillQty,
                fillPrice,
                0L,
                0L
        ));
    }

    private String generateOrderId() {
        return "ORD-" + clock.millis() + "-" + orderIdSequence.incrementAndGet();
    }

    private sealed interface ExecutionCommand permits SignalCommand, FillCommand, BrokerFillCommand {
    }

    private record SignalCommand(SignalPendingExecution pendingExecution, Consumer<DomainEvent> downstream) implements ExecutionCommand {
    }

    private record FillCommand(OrderFilled orderFilled, Consumer<DomainEvent> downstream, int deferAttempts) implements ExecutionCommand {
    }

    private record BrokerFillCommand(
            Order order,
            EventMetadata metadata,
            java.util.List<Trade> fills,
            boolean fullyFilled,
            Consumer<DomainEvent> downstream,
            int deferAttempts
    ) implements ExecutionCommand {
    }
}
