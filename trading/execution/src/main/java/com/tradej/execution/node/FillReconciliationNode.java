package com.tradej.execution.node;

import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.event.EventMetadata;
import com.tradej.core.domain.event.OrderFilled;
import com.tradej.core.domain.event.OrderFullyFilled;
import com.tradej.core.domain.event.OrderPartiallyFilled;
import com.tradej.core.domain.event.TradeOpened;
import com.tradej.core.domain.event.TradeUpdated;
import com.tradej.core.domain.model.Order;
import com.tradej.core.domain.model.Trade;
import com.tradej.core.domain.oms.OrderProjection;
import com.tradej.core.domain.runtime.RuntimeModeHolder;
import com.tradej.core.domain.value.FillReconciliation;
import com.tradej.core.domain.value.Side;
import com.tradej.execution.identity.OrderIdentityRegistry;
import com.tradej.execution.service.OrderManagementService;
import com.tradej.pipeline.runtime.BasePipelineNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.LongStream;

/**
 * Pipeline node that reconciles broker fill reports with OMS state.
 * <p>
 * Handles:
 * <ul>
 *   <li>Fill identity resolution (broker-order-id → internal-order-id)</li>
 *   <li>OMS event sourcing (partial/full fill events)</li>
 *   <li>Trade lifecycle (TradeOpened once, TradeUpdated on subsequent fills)</li>
 *   <li>Fill deferral when identity mapping is not yet available</li>
 * </ul>
 * <p>
 * This node handles the <b>fill reconciliation</b> concern previously embedded
 * inside {@link com.tradej.execution.service.ExecutionHandler}.
 */
public final class FillReconciliationNode extends BasePipelineNode {

    private static final Logger log = LoggerFactory.getLogger(FillReconciliationNode.class);
    private static final int MAX_FILL_DEFER_ATTEMPTS = FillReconciliation.MAX_FILL_DEFER_ATTEMPTS;
    private static final long FILL_DEFER_DELAY_MS = FillReconciliation.FILL_DEFER_DELAY_MS;

    private final OrderManagementService orderManagementService;
    private final OrderIdentityRegistry identityRegistry;
    private final RuntimeModeHolder runtimeModeHolder;
    private final AtomicLong droppedFillCount = new AtomicLong();
    private final Set<String> tradeOpenedEmitted = ConcurrentHashMap.newKeySet();
    private final ScheduledExecutorService deferExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "fill-defer");
        t.setDaemon(true);
        return t;
    });

    public FillReconciliationNode(
            OrderManagementService orderManagementService,
            OrderIdentityRegistry identityRegistry,
            RuntimeModeHolder runtimeModeHolder
    ) {
        this.orderManagementService = orderManagementService;
        this.identityRegistry = identityRegistry;
        this.runtimeModeHolder = runtimeModeHolder;
    }

    @Override
    protected void onInit() {
    }

    @Override
    protected void processEvent(DomainEvent event) {
        if (!(event instanceof OrderFilled orderFilled)) {
            return;
        }
        processFill(orderFilled, 0);
    }

    private void processFill(OrderFilled orderFilled, int attempt) {
        Order order = orderFilled.order();
        String internalOrderId = resolveInternalOrderId(order);

        if (internalOrderId == null) {
            if (attempt < MAX_FILL_DEFER_ATTEMPTS) {
                log.warn("Fill identity not yet available brokerOrderId={} correlationId={} attempt={}/{} — deferring",
                        order.orderId(), order.correlationId(), attempt + 1, MAX_FILL_DEFER_ATTEMPTS);
                scheduleRetry(orderFilled, attempt + 1);
                return;
            }
            droppedFillCount.incrementAndGet();
            log.error("Fill dropped after {} defer attempts brokerOrderId={} correlationId={}",
                    MAX_FILL_DEFER_ATTEMPTS, order.orderId(), order.correlationId());
            return;
        }

        reconcileFill(internalOrderId, order, orderFilled);
    }

    private void scheduleRetry(OrderFilled orderFilled, int nextAttempt) {
        deferExecutor.schedule(() -> processFill(orderFilled, nextAttempt),
                FILL_DEFER_DELAY_MS, TimeUnit.MILLISECONDS);
    }

    private void reconcileFill(String internalOrderId, Order order, OrderFilled orderFilled) {
        if (orderFilled.fills().isEmpty()) {
            log.warn("Empty fill list for internalOrderId={}", internalOrderId);
            emitTradeEvent(internalOrderId, order, orderFilled, 0L);
            return;
        }

        OrderProjection projection = orderManagementService.getOrderProjection(internalOrderId).orElse(null);
        if (projection == null) {
            log.warn("No OMS projection found for internalOrderId={}", internalOrderId);
            emitTradeEvent(internalOrderId, order, orderFilled, 0L);
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
            orderManagementService.onBrokerEvent(com.tradej.core.domain.oms.OrderFullyFilled.event(internalOrderId, totalQty, avgPrice));
            context.publish(new com.tradej.core.domain.event.OrderFullyFilled(
                    EventMetadata.correlated(order.correlationId(), orderFilled.sequenceId()),
                    order, orderFilled.fills()
            ));
        } else {
            orderManagementService.onBrokerEvent(com.tradej.core.domain.oms.OrderPartiallyFilled.event(internalOrderId, fillQty, avgPrice));
            context.publish(new com.tradej.core.domain.event.OrderPartiallyFilled(
                    EventMetadata.correlated(order.correlationId(), orderFilled.sequenceId()),
                    order, orderFilled.fills()
            ));
        }

        emitTradeEvent(internalOrderId, order, orderFilled, avgPrice);
    }

    private String resolveInternalOrderId(Order order) {
        String internal = identityRegistry.resolveInternalId(order.orderId());
        if (internal != null) return internal;
        if (order.correlationId() != null && !order.correlationId().isBlank()) {
            return identityRegistry.resolveBySignalId(order.correlationId());
        }
        return null;
    }

    private void emitTradeEvent(String orderId, Order order, OrderFilled orderFilled, long fillPrice) {
        long price = fillPrice > 0 ? fillPrice : order.pricePaisa();
        Trade fill = orderFilled.fills().isEmpty() ? null : orderFilled.fills().get(0);
        long qty = fill != null ? fill.quantity() : order.filledQuantity();

        if (!tradeOpenedEmitted.add(orderId)) {
            // Already opened → emit TradeUpdated
            context.publish(new TradeUpdated(
                    EventMetadata.correlated(order.correlationId(), orderFilled.sequenceId()),
                    orderId, order.symbol(), price, 0L, 0L
            ));
            return;
        }

        // First fill → emit TradeOpened
        context.publish(new TradeOpened(
                EventMetadata.correlated(order.correlationId(), orderFilled.sequenceId()),
                orderId, orderId, order.correlationId(),
                order.symbol(),
                order.side() == Side.SELL ? Side.SHORT : Side.LONG,
                qty, price, 0L, 0L
        ));
    }

    public long droppedFillCount() {
        return droppedFillCount.get();
    }
}
