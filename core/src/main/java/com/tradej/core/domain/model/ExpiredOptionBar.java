package com.tradej.core.domain.model;

/**
 * OHLCV + open interest for an expired option contract (Upstox-complete; no IV/spot).
 */
public record ExpiredOptionBar(
        long timestampMs,
        long openPaisa,
        long highPaisa,
        long lowPaisa,
        long closePaisa,
        long volume,
        long oi
) {
}
