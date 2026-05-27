package com.tradej.core.domain.model;

public record Candle(
        String symbol,
        String interval,
        long startTimeMs,
        long endTimeMs,
        long openPaisa,
        long highPaisa,
        long lowPaisa,
        long closePaisa,
        long volume,
        boolean closed
) {
}
