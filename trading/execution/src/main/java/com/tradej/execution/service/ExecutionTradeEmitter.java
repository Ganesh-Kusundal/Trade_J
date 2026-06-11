package com.tradej.execution.service;

import com.tradej.core.domain.event.EventMetadata;
import com.tradej.core.domain.event.OrderFilled;
import com.tradej.core.domain.event.TradeOpened;
import com.tradej.core.domain.event.TradeUpdated;
import com.tradej.core.domain.model.Order;
import com.tradej.core.domain.value.Side;
import com.github.benmanes.caffeine.cache.Cache;

import java.util.function.Consumer;

final class ExecutionTradeEmitter {
    private final Cache<String, Boolean> tradeOpenedEmitted;

    ExecutionTradeEmitter(Cache<String, Boolean> tradeOpenedEmitted) {
        this.tradeOpenedEmitted = tradeOpenedEmitted;
    }

    void emitTradeOpened(String orderId, Order order, OrderFilled orderFilled, Consumer<com.tradej.core.domain.event.DomainEvent> downstream) {
        if (tradeOpenedEmitted.asMap().putIfAbsent(orderId, Boolean.TRUE) != null) {
            TradeOpenedEventPayload payload = payload(order, orderFilled, false);
            if (payload.fillQty() == 0 && payload.unrealizedPnl() == 0) {
                return;
            }
            downstream.accept(new TradeUpdated(
                    EventMetadata.correlated(order.correlationId(), orderFilled.sequenceId()),
                    orderId,
                    order.symbol(),
                    payload.fillPrice(),
                    payload.unrealizedPnl(),
                    0L
            ));
            return;
        }

        TradeOpenedEventPayload payload = payload(order, orderFilled, true);
        downstream.accept(new TradeOpened(
                EventMetadata.correlated(order.correlationId(), orderFilled.sequenceId()),
                orderId,
                orderId,
                order.correlationId(),
                order.symbol(),
                order.side() == Side.SELL ? Side.SHORT : Side.LONG,
                payload.fillQty(),
                payload.fillPrice(),
                0L,
                0L
        ));
    }

    private static TradeOpenedEventPayload payload(Order order, OrderFilled orderFilled, boolean firstEmission) {
        com.tradej.core.domain.model.Trade fill = orderFilled.fills().isEmpty() ? null : orderFilled.fills().get(0);
        long fillPrice = fill != null ? fill.pricePaisa() : order.pricePaisa();
        long fillQty = fill != null ? fill.quantity() : firstEmission ? order.filledQuantity() : 0L;
        return new TradeOpenedEventPayload(fillPrice, fillQty, 0L);
    }

    private record TradeOpenedEventPayload(long fillPrice, long fillQty, long unrealizedPnl) {
    }
}
