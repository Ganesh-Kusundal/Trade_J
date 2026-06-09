package com.tradej.persistence.replay;

/**
 * Lightweight record types returned by {@link HistoricalRangeService} queries.
 */
public final class HistoricalRecords {

    private HistoricalRecords() {}

    public record HistoricalOrder(
            String eventId,
            String orderId,
            String correlationId,
            String symbol,
            String status,
            long quantity,
            long pricePaisa
    ) {}

    public record HistoricalFill(
            String eventId,
            String orderId,
            String tradeId,
            String symbol,
            long quantity,
            long pricePaisa
    ) {}

    public record HistoricalFillEvent(
            String eventId,
            String eventType,
            String orderId,
            String correlationId,
            String symbol,
            long quantity,
            long pricePaisa,
            int fillCount
    ) {}

    public record HistoricalTradeEvent(
            String eventId,
            String eventType,
            String tradeId,
            String orderId,
            String signalId,
            String symbol,
            String side,
            long size,
            long entryPricePaisa,
            long stopLossPaisa,
            long takeProfitPaisa,
            long exitPricePaisa,
            long realizedPnlPaisa,
            String closeReason
    ) {}

    public record RangeStats(
            String symbol,
            long fromMs,
            long toMs,
            long tickCount,
            long candleCount,
            long orderCount,
            long fillCount,
            long fillEventCount,
            long firstTickMs,
            long lastTickMs,
            long firstCandleMs,
            long lastCandleMs
    ) {
        public boolean hasData() {
            return tickCount > 0L || candleCount > 0L || orderCount > 0L
                    || fillCount > 0L || fillEventCount > 0L;
        }
    }
}
