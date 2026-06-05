package com.tradej.core.domain.event;

import java.time.LocalDate;

public record MaxPainComputed(
        EventMetadata metadata,
        String underlying,
        LocalDate expiry,
        long maxPainStrikePaisa,
        long totalPainPaisa
) implements DomainEvent {

    @Override
    public void accept(DomainEventVisitor visitor) {
        visitor.visit(this);
    }
}
