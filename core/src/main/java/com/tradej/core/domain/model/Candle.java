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
        boolean closed,
        long oi,
        long trades
) {
    public Candle(String symbol, String interval, long startTimeMs, long endTimeMs,
                  long openPaisa, long highPaisa, long lowPaisa, long closePaisa,
                  long volume, boolean closed) {
        this(symbol, interval, startTimeMs, endTimeMs, openPaisa, highPaisa,
                lowPaisa, closePaisa, volume, closed, 0L, 0L);
    }
}
