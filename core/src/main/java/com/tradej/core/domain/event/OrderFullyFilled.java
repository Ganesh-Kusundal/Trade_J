package com.tradej.core.domain.event;

import com.tradej.core.domain.model.Order;
import com.tradej.core.domain.model.Trade;

import java.util.List;

public record OrderFullyFilled(
        EventMetadata metadata,
        Order order,
        List<Trade> fills
) implements OrderUpdateEvent, OsmEventConvertible {
    public OrderFullyFilled {
        fills = List.copyOf(fills);
    }

    @Override
    public com.tradej.core.domain.oms.OrderEvent toOsmEvent() {
        long totalQty = fills.stream().mapToLong(Trade::quantity).sum();
        long pricePaisa = fills.isEmpty() ? 0L : fills.getLast().pricePaisa();
        return new com.tradej.core.domain.oms.OrderFullyFilled(order.orderId(), totalQty, pricePaisa);
    }

    @Override
    public com.tradej.core.domain.oms.OrderEvent toOsmEvent(String omsOrderId) {
        long totalQty = fills.stream().mapToLong(Trade::quantity).sum();
        long pricePaisa = fills.isEmpty() ? 0L : fills.getLast().pricePaisa();
        return new com.tradej.core.domain.oms.OrderFullyFilled(omsOrderId, totalQty, pricePaisa);
    }

    @Override
    public void accept(DomainEventVisitor visitor) {
        visitor.visit(this);
    }
}
