package com.tradej.historical.ingest.resample;

import com.tradej.core.domain.market.CandleBucketPolicy;
import com.tradej.core.domain.market.CandleIntervalSpec;
import com.tradej.core.domain.model.Candle;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CandleResampler {

    private CandleResampler() {
    }

    public static List<Candle> resample(List<Candle> candles, String targetInterval) {
        if (candles == null || candles.isEmpty()) {
            return List.of();
        }
        CandleIntervalSpec spec = CandleIntervalSpec.parse(targetInterval);
        if (spec.isOneMinuteOrFiner()) {
            return List.copyOf(candles);
        }

        Map<Long, Candle> buckets = new LinkedHashMap<>();
        for (Candle candle : candles) {
            long bucketStart = CandleBucketPolicy.bucketStartMs(candle.startTimeMs(), spec);
            long bucketEnd = CandleBucketPolicy.bucketEndMs(bucketStart, spec);
            Candle existing = buckets.get(bucketStart);
            if (existing == null) {
                buckets.put(bucketStart, new Candle(
                        candle.symbol(),
                        spec.code(),
                        bucketStart,
                        bucketEnd,
                        candle.openPaisa(),
                        candle.highPaisa(),
                        candle.lowPaisa(),
                        candle.closePaisa(),
                        candle.volume(),
                        true
                ));
            } else {
                buckets.put(bucketStart, new Candle(
                        existing.symbol(),
                        spec.code(),
                        existing.startTimeMs(),
                        bucketEnd,
                        existing.openPaisa(),
                        Math.max(existing.highPaisa(), candle.highPaisa()),
                        Math.min(existing.lowPaisa(), candle.lowPaisa()),
                        candle.closePaisa(),
                        existing.volume() + candle.volume(),
                        true
                ));
            }
        }
        return new ArrayList<>(buckets.values());
    }

    /**
     * @deprecated use {@link CandleIntervalSpec#parse(String)}
     */
    @Deprecated
    public static int parseIntervalMinutes(String interval) {
        CandleIntervalSpec spec = CandleIntervalSpec.parse(interval);
        if (spec.mode() == CandleIntervalSpec.AggregationMode.DAILY_SESSION) {
            return 1440;
        }
        return (int) (spec.durationMs() / 60_000L);
    }
}
