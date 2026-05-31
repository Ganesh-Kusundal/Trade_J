package com.tradej.core.domain.event;

import com.tradej.core.domain.value.Side;

public record TradeOpened(
        EventMetadata metadata,
        String tradeId,
        String orderId,
        String signalId,
        String symbol,
        Side side,
        long size,
        long entryPricePaisa,
        long stopLossPaisa,
        long takeProfitPaisa
) implements DomainEvent {
}
