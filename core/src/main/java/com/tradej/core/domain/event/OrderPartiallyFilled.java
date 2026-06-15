package com.tradej.core.domain.event;

import com.tradej.core.domain.model.Order;
import com.tradej.core.domain.model.Trade;

import java.util.List;

public record OrderPartiallyFilled(
        EventMetadata metadata,
        Order order,
        List<Trade> fills
) implements OrderUpdateEvent, OsmEventConvertible {
    public OrderPartiallyFilled {
        fills = List.copyOf(fills);
    }

    @Override
    public com.tradej.core.domain.oms.OrderEvent toOsmEvent() {
        long filledQty = fills.stream().mapToLong(Trade::quantity).sum();
        long pricePaisa = fills.isEmpty() ? 0L : fills.getLast().pricePaisa();
        return new com.tradej.core.domain.oms.OrderPartiallyFilled(order.orderId(), filledQty, pricePaisa);
    }

    @Override
    public com.tradej.core.domain.oms.OrderEvent toOsmEvent(String omsOrderId) {
        long filledQty = fills.stream().mapToLong(Trade::quantity).sum();
        long pricePaisa = fills.isEmpty() ? 0L : fills.getLast().pricePaisa();
        return new com.tradej.core.domain.oms.OrderPartiallyFilled(omsOrderId, filledQty, pricePaisa);
    }

    @Override
    public void accept(DomainEventVisitor visitor) {
        visitor.visit(this);
    }
}
