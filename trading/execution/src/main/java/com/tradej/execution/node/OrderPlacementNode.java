package com.tradej.execution.node;

import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.event.EventMetadata;
import com.tradej.core.domain.event.OrderAccepted;
import com.tradej.core.domain.event.OrderFilled;
import com.tradej.core.domain.event.OrderRejected;
import com.tradej.core.domain.event.SignalPendingExecution;
import com.tradej.core.domain.event.SignalSuppressed;
import com.tradej.core.domain.model.Order;
import com.tradej.core.domain.model.OrderRequest;
import com.tradej.core.domain.oms.OrderAcknowledged;
import com.tradej.core.domain.oms.OrderSubmitted;
import com.tradej.core.domain.runtime.RuntimeModeHolder;
import com.tradej.core.domain.value.OrderStatus;
import com.tradej.execution.identity.OrderIdentityRegistry;
import com.tradej.execution.service.OrderManagementService;
import com.tradej.execution.service.TradingCircuitBreaker;
import com.tradej.persistence.oms.EventSourcedOrderRepository;
import com.tradej.pipeline.runtime.BasePipelineNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Pipeline node that places orders via broker or simulation.
 * <p>
 * Handles:
 * <ul>
 *   <li>OMS event sourcing ({@link OrderSubmitted})</li>
 *   <li>Identity mapping registration</li>
 *   <li>Broker order placement with timeout</li>
 *   <li>Simulated fill emission in REPLAY/BACKTEST modes</li>
 *   <li>Circuit breaker success/failure recording</li>
 * </ul>
 * <p>
 * This node handles the <b>order placement</b> concern previously embedded
 * inside {@link com.tradej.execution.service.ExecutionHandler}.
 */
public final class OrderPlacementNode extends BasePipelineNode {

    private static final Logger log = LoggerFactory.getLogger(OrderPlacementNode.class);
    private static final long ORDER_PLACEMENT_TIMEOUT_MS = 10_000L;

    private final EventSourcedOrderRepository omsRepo;
    private final OrderManagementService orderManagementService;
    private final RuntimeModeHolder runtimeModeHolder;
    private final TradingCircuitBreaker circuitBreaker;
    private final OrderIdentityRegistry identityRegistry;
    private final AtomicLong orderIdCounter = new AtomicLong(0);

    public OrderPlacementNode(
            EventSourcedOrderRepository omsRepo,
            OrderManagementService orderManagementService,
            RuntimeModeHolder runtimeModeHolder,
            TradingCircuitBreaker circuitBreaker,
            OrderIdentityRegistry identityRegistry
    ) {
        this.omsRepo = omsRepo;
        this.orderManagementService = orderManagementService;
        this.runtimeModeHolder = runtimeModeHolder;
        this.circuitBreaker = circuitBreaker;
        this.identityRegistry = identityRegistry;
    }

    @Override
    protected void onInit() {
    }

    @Override
    protected void processEvent(DomainEvent event) {
        // Receive either SignalPendingExecution (from SignalGateNode) or raw signals
        if (!(event instanceof SignalPendingExecution pending)) {
            return;
        }

        String orderId = "ORD-" + orderIdCounter.incrementAndGet();
        OrderRequest request = pending.orderRequest();

        // 1. OMS event sourcing
        omsRepo.append(OrderSubmitted.create(
                orderId, pending.signalId(), request.symbol(), request.quantity()
        ));
        identityRegistry.register(orderId, null, pending.signalId());

        // 2. Place order with timeout
        try {
            Order order = placeOrderWithTimeout(request);
            if (circuitBreaker != null) {
                circuitBreaker.recordSuccess();
            }

            if (order.status().isRejected()) {
                log.warn("Order rejected by broker orderId={} reason={}", orderId, order.rejectionReason());
                omsRepo.append(new com.tradej.core.domain.oms.OrderRejected(orderId, order.rejectionReason()));
                identityRegistry.remove(orderId);
                context.publish(new OrderRejected(
                        EventMetadata.correlated(order.correlationId(), pending.sequenceId()),
                        order, order.rejectionReason()
                ));
            } else {
                log.info("Order accepted orderId={} brokerOrderId={} symbol={} qty={}",
                        orderId, order.orderId(), order.symbol(), order.quantity());
                omsRepo.append(OrderAcknowledged.event(orderId, order.orderId()));
                identityRegistry.acknowledge(orderId, order.orderId());
                context.publish(new OrderAccepted(
                        EventMetadata.correlated(order.correlationId(), pending.sequenceId()),
                        order
                ));
                emitSimulatedFillIfNeeded(order, pending);
            }
        } catch (Exception e) {
            log.error("Order placement failed signalId={} error={}", pending.signalId(), e.getMessage());
            if (circuitBreaker != null) {
                circuitBreaker.recordFailure();
            }
            omsRepo.append(new com.tradej.core.domain.oms.OrderRejected(
                    orderId, "Order placement failed: " + e.getMessage()));
            identityRegistry.remove(orderId);
            context.publish(new SignalSuppressed(
                    EventMetadata.correlated(pending.signalId(), pending.sequenceId()),
                    pending.signalId(), request.symbol(),
                    "Order placement failed: " + e.getMessage(),
                    pending.decisionContext()
            ));
        }
    }

    private Order placeOrderWithTimeout(OrderRequest request) throws Exception {
        CompletableFuture<Order> future = CompletableFuture.supplyAsync(
                () -> orderManagementService.placeOrder(request));
        try {
            return future.get(ORDER_PLACEMENT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new RuntimeException("Order placement timed out after " + ORDER_PLACEMENT_TIMEOUT_MS + "ms");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (cause instanceof RuntimeException re) throw re;
            throw new RuntimeException("Order placement failed", cause);
        }
    }

    private void emitSimulatedFillIfNeeded(Order order, SignalPendingExecution pending) {
        if (!runtimeModeHolder.mode().usesSimulatedExecution() || order.status() != OrderStatus.TRADED) {
            return;
        }
        orderManagementService.lastSimulatedMatch().ifPresent(match -> {
            if (!match.fills().isEmpty()) {
                context.publish(new OrderFilled(
                        EventMetadata.correlated(order.correlationId(), pending.sequenceId()),
                        order, match.fills()
                ));
            }
        });
    }
}
