package com.tradej.core.domain.model;

/**
 * One OHLCV bar from Dhan expired rolling option history, with greeks context fields.
 */
public record RollingOptionBar(
        long timestampMs,
        long openPaisa,
        long highPaisa,
        long lowPaisa,
        long closePaisa,
        long volume,
        double iv,
        long oi,
        long spotPaisa,
        long strikePaisa
) {
}
