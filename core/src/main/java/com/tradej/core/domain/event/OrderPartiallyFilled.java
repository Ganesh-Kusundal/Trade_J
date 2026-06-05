package com.tradej.core.domain.event;

import com.tradej.core.domain.model.Order;
import com.tradej.core.domain.model.Trade;

import java.util.List;

public record OrderPartiallyFilled(
        EventMetadata metadata,
        Order order,
        List<Trade> fills
) implements OrderUpdateEvent {
    public OrderPartiallyFilled {
        fills = List.copyOf(fills);
    }

    @Override
    public void accept(DomainEventVisitor visitor) {
        visitor.visit(this);
    }
}
