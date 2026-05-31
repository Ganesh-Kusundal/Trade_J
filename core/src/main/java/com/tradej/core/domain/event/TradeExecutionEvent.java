package com.tradej.core.domain.event;

import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.Side;

/**
 * Canonical trade execution event — raw fill report from a broker adapter.
 * Carries the broker-order-id and broker-trade-id for traceability.
 */
public record TradeExecutionEvent(
        EventMetadata metadata,
        String orderId,
        String tradeId,
        String symbol,
        ExchangeSegment exchangeSegment,
        Side side,
        long executedQuantity,
        long executedPricePaisa,
        long exchangeTimestampMs
) implements DomainEvent {
}
