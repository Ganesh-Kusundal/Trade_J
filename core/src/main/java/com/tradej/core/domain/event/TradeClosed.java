package com.tradej.core.domain.event;

public record TradeClosed(
    EventMetadata metadata,
    String tradeId,
    String symbol,
    long exitPricePaisa,
    long realizedPnlPaisa,
    long size,
    String reason
) implements DomainEvent {

    @Override
    public void accept(DomainEventVisitor visitor) {
        visitor.visit(this);
    }
}
