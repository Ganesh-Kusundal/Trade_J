package com.tradej.execution.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.event.EventMetadata;
import com.tradej.core.domain.event.OrderFilled;
import com.tradej.core.domain.event.TradeOpened;
import com.tradej.core.domain.event.TradeUpdated;
import com.tradej.core.domain.model.Order;
import com.tradej.core.domain.model.Trade;
import com.tradej.core.domain.value.Side;

import java.time.Duration;
import java.util.function.Consumer;

/**
 * Emits trade lifecycle events from order fill reconciliation.
 */
public final class TradeEventEmissionService {

    private final Cache<String, Boolean> tradeOpenedEmitted = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(Duration.ofHours(24))
            .build();

    public void emit(String orderId, Order order, OrderFilled orderFilled, Consumer<DomainEvent> downstream) {
        if (tradeOpenedEmitted.asMap().putIfAbsent(orderId, Boolean.TRUE) != null) {
            emitTradeUpdated(orderId, order, orderFilled, downstream);
            return;
        }
        emitTradeOpened(orderId, order, orderFilled, downstream);
    }

    private void emitTradeUpdated(String orderId, Order order, OrderFilled orderFilled, Consumer<DomainEvent> downstream) {
        Trade fill = orderFilled.fills().isEmpty() ? null : orderFilled.fills().get(0);
        long fillPrice = fill != null ? fill.pricePaisa() : order.pricePaisa();
        long fillQty = fill != null ? fill.quantity() : 0L;
        long unrealizedPnl = 0L;
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
    }

    private void emitTradeOpened(String orderId, Order order, OrderFilled orderFilled, Consumer<DomainEvent> downstream) {
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
}
