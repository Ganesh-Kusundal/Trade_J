package com.tradej.core.domain.event;

public record GammaExposureComputed(
        EventMetadata metadata,
        String underlying,
        double netGamma,
        long expiryEpochMs
) implements DomainEvent {

    @Override
    public void accept(DomainEventVisitor visitor) {
        visitor.visit(this);
    }
}
