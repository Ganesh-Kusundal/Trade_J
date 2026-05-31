package com.tradej.strategy.service;

import com.tradej.core.domain.market.CandleBucketPolicy;
import com.tradej.core.domain.market.CandleIntervalSpec;
import com.tradej.core.domain.event.CandleClosed;
import com.tradej.core.domain.event.CandleDeveloping;
import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.event.EventMetadata;
import com.tradej.core.domain.event.MarketTickEvent;
import com.tradej.core.domain.event.TickReceived;
import com.tradej.core.domain.model.Candle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class CandleAggregationService {

    private static final Logger log = LoggerFactory.getLogger(CandleAggregationService.class);
    private static final Set<String> KNOWN_INTERVALS = Set.of("1s", "1m", "15m", "1h", "5m");

    private final List<String> intervals;
    private final Map<String, Candle> currentCandles = new ConcurrentHashMap<>();

    /** Returns the candle intervals configured for this service. */
    public List<String> intervals() {
        return intervals;
    }

    /**
     * Creates a service that builds candles at the given intervals from incoming ticks.
     * Each interval produces a separate candle series (e.g., "1s" candles and "5m" candles
     * can coexist). Emits {@link CandleDeveloping} on every tick and {@link CandleClosed}
     * on bucket rollover for each interval independently.
     *
     * @param intervals candle intervals to build (e.g., ["1s", "5m"])
     * @throws IllegalArgumentException if any interval is not recognized
     */
    public CandleAggregationService(List<String> intervals) {
        this.intervals = List.copyOf(intervals);
        for (String interval : intervals) {
            if (!KNOWN_INTERVALS.contains(interval)) {
                log.warn("Unknown candle interval '{}' — will default to 5-minute buckets. "
                        + "Supported intervals: {}", interval, KNOWN_INTERVALS);
            }
        }
    }

    /**
     * Creates a service with a single default interval of "5m" for backward compatibility.
     */
    public CandleAggregationService() {
        this(List.of("5m"));
    }

    public void onDomainEvent(DomainEvent event, Consumer<DomainEvent> downstream) {
        switch (event) {
            case TickReceived tick -> onTick(tick.symbol(), tick.ltpPaisa(), tick.lastTradeQuantity(),
                    tick.exchangeTimestampMs(), tick.sequenceId(), tick.correlationId(),
                    tick.metadata().timestampMs(), downstream);
            case MarketTickEvent tick -> onTick(tick.symbol(), tick.ltpPaisa(), tick.lastTradeQuantity(),
                    tick.exchangeTimestampEpochMs(), tick.sequenceId(), tick.correlationId(),
                    tick.metadata().timestampMs(), downstream);
            default -> { }
        }
    }

    // ── Replay state isolation (AD-02) ──

    /** Captures a snapshot of all open candle buckets for replay isolation. */
    public StateSnapshot snapshot() {
        return new StateSnapshot(new ConcurrentHashMap<>(currentCandles));
    }

    /** Restores candle bucket state from a previously captured snapshot. */
    public void restore(StateSnapshot snapshot) {
        currentCandles.clear();
        currentCandles.putAll(snapshot.currentCandles());
    }

    /** Immutable snapshot of all open candle buckets for replay isolation. */
    public record StateSnapshot(Map<String, Candle> currentCandles) {}

    // ── Core tick processing ──

    private void onTick(String symbol, long ltpPaisa, long lastTradeQuantity,
                        long exchangeTimestampMs, long sequenceId, String correlationId,
                        long metadataTimestampMs,
                        Consumer<DomainEvent> downstream) {
        long baseTs = exchangeTimestampMs == 0 ? metadataTimestampMs : exchangeTimestampMs;
        long seqId = sequenceId;
        String corrId = correlationId;

        for (String interval : intervals) {
            String key = symbol + "::" + interval;
            long bStart = bucketStart(baseTs, interval);
            long bEnd = bucketEnd(bStart, interval);

            currentCandles.compute(key, (k, current) -> {
                if (current == null || current.endTimeMs() != bEnd) {
                    // Bucket rollover — close the old candle
                    if (current != null) {
                        downstream.accept(new CandleClosed(
                                EventMetadata.correlated(corrId, seqId), close(current)));
                    }
                    Candle fresh = new Candle(
                            symbol, interval, bStart, bEnd,
                            ltpPaisa, ltpPaisa, ltpPaisa,
                            ltpPaisa, lastTradeQuantity, false);
                    downstream.accept(new CandleDeveloping(
                            EventMetadata.correlated(corrId, seqId), fresh));
                    return fresh;
                }

                // Same bucket — update OHLC
                Candle updated = new Candle(
                        current.symbol(), current.interval(),
                        current.startTimeMs(), current.endTimeMs(),
                        current.openPaisa(),
                        Math.max(current.highPaisa(), ltpPaisa),
                        Math.min(current.lowPaisa(), ltpPaisa),
                        ltpPaisa,
                        current.volume() + lastTradeQuantity,
                        false);
                downstream.accept(new CandleDeveloping(
                        EventMetadata.correlated(corrId, seqId), updated));
                return updated;
            });
        }
    }

    private Candle close(Candle candle) {
        return new Candle(
                candle.symbol(),
                candle.interval(),
                candle.startTimeMs(),
                candle.endTimeMs(),
                candle.openPaisa(),
                candle.highPaisa(),
                candle.lowPaisa(),
                candle.closePaisa(),
                candle.volume(),
                true
        );
    }

    private long bucketStart(long timestampMs, String interval) {
        long ts = timestampMs == 0 ? System.currentTimeMillis() : timestampMs;
        CandleIntervalSpec spec = CandleIntervalSpec.parse(interval);
        return CandleBucketPolicy.bucketStartMs(ts, spec);
    }

    private long bucketEnd(long bucketStart, String interval) {
        CandleIntervalSpec spec = CandleIntervalSpec.parse(interval);
        return CandleBucketPolicy.bucketEndMs(bucketStart, spec);
    }
}
