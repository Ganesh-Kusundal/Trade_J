package com.tradej.execution.service;

import com.tradej.core.domain.event.EventMetadata;
import com.tradej.core.domain.event.OrderFilled;
import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.model.Order;
import com.tradej.core.domain.model.Trade;
import com.tradej.core.domain.oms.OrderFullyFilled;
import com.tradej.core.domain.oms.OrderPartiallyFilled;
import com.tradej.core.domain.oms.OrderProjection;
import com.tradej.core.domain.port.DeadLetterQueue;
import com.tradej.execution.identity.OrderIdentityRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.stream.LongStream;

/**
 * Reconciles broker fill events against the in-memory order state machine.
 * Centralises the common logic shared by {@code processFill} and
 * {@code processBrokerFill} in {@link ExecutionHandler}.
 *
 * <p>Single responsibility: given a (possibly partial) broker fill, decide
 * whether the order is fully or partially filled, persist the OMS state
 * transition, publish the corresponding downstream event, and emit
 * {@code TradeOpened} / {@code TradeUpdated}.
 */
public final class FillReconciler {

    private static final Logger log = LoggerFactory.getLogger(FillReconciler.class);

    private final OrderManagementService orderManagementService;
    private final OrderIdentityRegistry identityRegistry;
    private final DeadLetterQueue deadLetterQueue;
    private final ExecutionTradeEmitter tradeEmitter;
    private final AtomicLong droppedFillCount;

    public FillReconciler(
            OrderManagementService orderManagementService,
            OrderIdentityRegistry identityRegistry,
            DeadLetterQueue deadLetterQueue,
            ExecutionTradeEmitter tradeEmitter,
            AtomicLong droppedFillCount
    ) {
        this.orderManagementService = orderManagementService;
        this.identityRegistry = identityRegistry;
        this.deadLetterQueue = deadLetterQueue;
        this.tradeEmitter = tradeEmitter;
        this.droppedFillCount = droppedFillCount;
    }

    /**
     * Reconciles a fill originating from an internal {@link OrderFilled} event.
     * Returns {@code true} if the caller should continue with deferred processing,
     * {@code false} if the fill was dropped or fully handled.
     */
    public boolean reconcileFromOrderFilled(OrderFilled orderFilled, Consumer<DomainEvent> downstream) {
        Order order = orderFilled.order();
        String internalOrderId = resolveInternalOrderId(order);
        if (internalOrderId == null) {
            log.warn("Fill identity not yet available brokerOrderId={} correlationId={}",
                    order.orderId(), order.correlationId());
            return false; // signal: identity missing, caller decides to defer
        }

        if (orderFilled.fills().isEmpty()) {
            log.warn("Empty fill list for internalOrderId={}", internalOrderId);
            tradeEmitter.emitTradeOpened(internalOrderId, order, orderFilled, downstream);
            return true;
        }

        OrderProjection projection = orderManagementService.getOrderProjection(internalOrderId).orElse(null);
        if (projection == null) {
            log.warn("No OSM projection found for internalOrderId={} brokerOrderId={}", internalOrderId, order.orderId());
            tradeEmitter.emitTradeOpened(internalOrderId, order, orderFilled, downstream);
            return true;
        }

        long fillQty = orderFilled.fills().stream().mapToLong(Trade::quantity).sum();
        long filledSoFar = projection.filledQuantity() + fillQty;
        long totalQty = projection.totalQuantity();
        long[] prices = orderFilled.fills().stream().mapToLong(Trade::pricePaisa).toArray();
        long avgPrice = prices.length > 0
                ? Math.round((double) LongStream.of(prices).sum() / prices.length)
                : 0L;

        if (filledSoFar >= totalQty) {
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

        tradeEmitter.emitTradeOpened(internalOrderId, order, orderFilled, downstream);
        return true;
    }

    /**
     * Reconciles a fill originating from a direct broker callback (no upstream
     * {@link OrderFilled} event). Identity may be unresolved — caller is
     * responsible for retry/defer policy and reports back via {@code deferAttempts}.
     *
     * @return {@code true} if reconciliation was performed, {@code false} if
     *         the caller should defer the fill.
     */
    public boolean reconcileFromBrokerCallback(
            Order order,
            EventMetadata metadata,
            List<Trade> fills,
            boolean fullyFilled,
            Consumer<DomainEvent> downstream
    ) {
        String internalOrderId = resolveInternalOrderId(order);
        if (internalOrderId == null) {
            return false; // caller defers
        }

        OrderProjection projection = orderManagementService.getOrderProjection(internalOrderId).orElse(null);
        if (projection == null) {
            log.warn("No OSM projection for broker fill internalOrderId={} brokerOrderId={}",
                    internalOrderId, order.orderId());
            return true;
        }

        long fillQty = fills.isEmpty()
                ? Math.max(0L, order.filledQuantity() - projection.filledQuantity())
                : fills.stream().mapToLong(Trade::quantity).sum();
        if (fillQty <= 0 && !fullyFilled) {
            log.debug("Skipping zero-qty broker fill update orderId={}", order.orderId());
            return true;
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
        tradeEmitter.emitTradeOpened(internalOrderId, order, synthetic, downstream);
        return true;
    }

    /** Records a dropped fill to the dead-letter queue and increments the drop counter. */
    public void recordDroppedFill(String reason, OrderFilled orderFilled) {
        droppedFillCount.incrementAndGet();
        deadLetterQueue.append("execution-handler", orderFilled, reason);
    }

    /** Records a dropped broker fill (identity still missing after defer exhaustion). */
    public void recordDroppedBrokerFill(String reason, Order order, EventMetadata metadata, List<Trade> fills) {
        droppedFillCount.incrementAndGet();
        deadLetterQueue.append("execution-handler", new OrderFilled(metadata, order, fills), reason);
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
}
