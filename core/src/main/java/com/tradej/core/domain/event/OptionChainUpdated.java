package com.tradej.core.domain.event;

import com.tradej.core.domain.model.OptionChainSnapshot;

public record OptionChainUpdated(
        EventMetadata metadata,
        OptionChainSnapshot chain
) implements DomainEvent {

    @Override
    public void accept(DomainEventVisitor visitor) {
        visitor.visit(this);
    }
}
