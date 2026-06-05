package com.tradej.core.domain.event;

import com.tradej.core.domain.model.OptionGreeks;
import com.tradej.core.domain.model.InstrumentKey;

public record GreeksComputed(
        EventMetadata metadata,
        InstrumentKey instrumentKey,
        OptionGreeks greeks
) implements DomainEvent {

    @Override
    public void accept(DomainEventVisitor visitor) {
        visitor.visit(this);
    }
}
