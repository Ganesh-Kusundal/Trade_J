package com.tradej.core.domain.event;

public record TradeUpdated(
        EventMetadata metadata,
        String tradeId,
        String symbol,
        long currentPricePaisa,
        long unrealizedPnlPaisa,
        long updatedStopLossPaisa
) implements DomainEvent {

    @Override
    public void accept(DomainEventVisitor visitor) {
        visitor.visit(this);
    }
}
