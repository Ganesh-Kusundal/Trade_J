package com.tradej.core.domain.event;

public record TradeClosed(
        EventMetadata metadata,
        String tradeId,
        String symbol,
        long exitPricePaisa,
        long realizedPnlPaisa,
        String reason
) implements DomainEvent {
}
