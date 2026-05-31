package com.tradej.replay.engine;

import com.tradej.core.domain.market.CandleBucketPolicy;
import com.tradej.core.domain.model.Candle;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages aggregation and synchronization of candles across multiple timeframes (1m, 5m, 15m, Daily)
 * to ensure that strategy evaluation has zero look-ahead bias during replay.
 */
public final class MultiTimeframeContext {

    private final Map<String, Candle> active5m = new HashMap<>();
    private final Map<String, Candle> active15m = new HashMap<>();
    private final Map<String, Candle> activeDaily = new HashMap<>();

    public record TimeframeResult(
        List<Candle> closedCandles,
        List<Candle> developingCandles
    ) {}

    /**
     * Consumes a newly closed 1m candle, updates in-progress timeframes,
     * and returns any higher-timeframe candles that closed as a result.
     *
     * @param oneMinCandle the newly closed 1m candle
     * @return TimeframeResult containing closed and developing candles
     */
    public synchronized TimeframeResult process1mCandle(Candle oneMinCandle) {
        String symbol = oneMinCandle.symbol();
        List<Candle> closed = new ArrayList<>();
        List<Candle> developing = new ArrayList<>();

        // Process 5m Timeframe
        Candle new5m = processInterval(oneMinCandle, active5m, 5, "5m", closed);
        if (new5m != null) {
            developing.add(new5m);
        }

        // Process 15m Timeframe
        Candle new15m = processInterval(oneMinCandle, active15m, 15, "15m", closed);
        if (new15m != null) {
            developing.add(new15m);
        }

        // Process Daily Timeframe
        Candle newDaily = processDaily(oneMinCandle, activeDaily, closed);
        if (newDaily != null) {
            developing.add(newDaily);
        }

        return new TimeframeResult(closed, developing);
    }

    private Candle processInterval(
        Candle candle1m,
        Map<String, Candle> activeMap,
        int periodMin,
        String intervalName,
        List<Candle> closedList
    ) {
        String symbol = candle1m.symbol();
        long minuteEpoch = candle1m.startTimeMs() / 60000L;
        long expectedStartMinute = (minuteEpoch / periodMin) * periodMin;
        long expectedStartMs = expectedStartMinute * 60000L;

        Candle current = activeMap.get(symbol);

        if (current != null && current.startTimeMs() != expectedStartMs) {
            // The timeframe boundary was crossed; close the old one and emit
            Candle closedCandle = new Candle(
                current.symbol(), current.interval(), current.startTimeMs(), current.endTimeMs(),
                current.openPaisa(), current.highPaisa(), current.lowPaisa(), current.closePaisa(),
                current.volume(), true
            );
            closedList.add(closedCandle);
            current = null;
        }

        if (current == null) {
            long expectedEndMs = (expectedStartMinute + periodMin) * 60000L - 1;
            current = new Candle(
                symbol, intervalName, expectedStartMs, expectedEndMs,
                candle1m.openPaisa(), candle1m.highPaisa(), candle1m.lowPaisa(), candle1m.closePaisa(),
                candle1m.volume(), false
            );
        } else {
            current = new Candle(
                symbol, intervalName, current.startTimeMs(), current.endTimeMs(),
                current.openPaisa(),
                Math.max(current.highPaisa(), candle1m.highPaisa()),
                Math.min(current.lowPaisa(), candle1m.lowPaisa()),
                candle1m.closePaisa(),
                current.volume() + candle1m.volume(),
                false
            );
        }

        activeMap.put(symbol, current);
        return current;
    }

    private Candle processDaily(Candle candle1m, Map<String, Candle> activeMap, List<Candle> closedList) {
        String symbol = candle1m.symbol();
        LocalDate candleDate = Instant.ofEpochMilli(candle1m.startTimeMs())
            .atZone(CandleBucketPolicy.IST)
            .toLocalDate();

        Candle current = activeMap.get(symbol);

        if (current != null) {
            LocalDate activeDate = Instant.ofEpochMilli(current.startTimeMs())
                .atZone(CandleBucketPolicy.IST)
                .toLocalDate();

            if (!activeDate.equals(candleDate)) {
                // Date changed; close old daily candle and emit
                Candle closedCandle = new Candle(
                    current.symbol(), current.interval(), current.startTimeMs(), current.endTimeMs(),
                    current.openPaisa(), current.highPaisa(), current.lowPaisa(), current.closePaisa(),
                    current.volume(), true
                );
                closedList.add(closedCandle);
                current = null;
            }
        }

        if (current == null) {
            long startOfDayMs = candleDate.atStartOfDay(CandleBucketPolicy.IST).toInstant().toEpochMilli();
            long endOfDayMs = candleDate.plusDays(1).atStartOfDay(CandleBucketPolicy.IST).toInstant().toEpochMilli() - 1;
            current = new Candle(
                symbol, "Daily", startOfDayMs, endOfDayMs,
                candle1m.openPaisa(), candle1m.highPaisa(), candle1m.lowPaisa(), candle1m.closePaisa(),
                candle1m.volume(), false
            );
        } else {
            current = new Candle(
                symbol, "Daily", current.startTimeMs(), current.endTimeMs(),
                current.openPaisa(),
                Math.max(current.highPaisa(), candle1m.highPaisa()),
                Math.min(current.lowPaisa(), candle1m.lowPaisa()),
                candle1m.closePaisa(),
                current.volume() + candle1m.volume(),
                false
            );
        }

        activeMap.put(symbol, current);
        return current;
    }
}
