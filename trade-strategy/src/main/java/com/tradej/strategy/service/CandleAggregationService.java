package com.tradej.strategy.service;

import org.springframework.stereotype.Service;
import com.tradej.core.domain.event.CandleClosed;
import com.tradej.core.domain.event.CandleDeveloping;
import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.event.EventMetadata;
import com.tradej.core.domain.event.TickReceived;
import com.tradej.core.domain.model.Candle;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

@Service
public final class CandleAggregationService {
    private final Map<String, Candle> currentCandles = new ConcurrentHashMap<>();

    public void onDomainEvent(DomainEvent event, Consumer<DomainEvent> downstream) {
        if (!(event instanceof TickReceived tick)) {
            return;
        }

        String key = tick.symbol() + "::" + tick.interval();
        Candle current = currentCandles.get(key);
        long bucketStart = bucketStart(tick.exchangeTimestampMs() == 0 ? tick.timestampMs() : tick.exchangeTimestampMs(), tick.interval());
        long bucketEnd = bucketEnd(bucketStart, tick.interval());

        if (current == null || current.endTimeMs() != bucketEnd) {
            if (current != null) {
                downstream.accept(new CandleClosed(EventMetadata.correlated(tick.correlationId(), tick.sequenceId()), close(current)));
            }
            Candle newCandle = new Candle(
                    tick.symbol(),
                    tick.interval(),
                    bucketStart,
                    bucketEnd,
                    tick.ltpPaisa(),
                    tick.ltpPaisa(),
                    tick.ltpPaisa(),
                    tick.ltpPaisa(),
                    tick.lastTradeQuantity(),
                    false
            );
            currentCandles.put(key, newCandle);
            downstream.accept(new CandleDeveloping(EventMetadata.correlated(tick.correlationId(), tick.sequenceId()), newCandle));
            return;
        }

        Candle updated = new Candle(
                current.symbol(),
                current.interval(),
                current.startTimeMs(),
                current.endTimeMs(),
                current.openPaisa(),
                Math.max(current.highPaisa(), tick.ltpPaisa()),
                Math.min(current.lowPaisa(), tick.ltpPaisa()),
                tick.ltpPaisa(),
                current.volume() + tick.lastTradeQuantity(),
                false
        );
        currentCandles.put(key, updated);
        downstream.accept(new CandleDeveloping(EventMetadata.correlated(tick.correlationId(), tick.sequenceId()), updated));
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
        Instant instant = Instant.ofEpochMilli(timestampMs == 0 ? System.currentTimeMillis() : timestampMs);
        return switch (interval) {
            case "1m" -> instant.truncatedTo(ChronoUnit.MINUTES).toEpochMilli();
            case "15m" -> instant.atZone(ZoneOffset.UTC).withMinute((instant.atZone(ZoneOffset.UTC).getMinute() / 15) * 15)
                    .withSecond(0).withNano(0).toInstant().toEpochMilli();
            case "1h" -> instant.truncatedTo(ChronoUnit.HOURS).toEpochMilli();
            default -> instant.atZone(ZoneOffset.UTC).withMinute((instant.atZone(ZoneOffset.UTC).getMinute() / 5) * 5)
                    .withSecond(0).withNano(0).toInstant().toEpochMilli();
        };
    }

    private long bucketEnd(long bucketStart, String interval) {
        return switch (interval) {
            case "1m" -> bucketStart + 60_000L;
            case "15m" -> bucketStart + 900_000L;
            case "1h" -> bucketStart + 3_600_000L;
            default -> bucketStart + 300_000L;
        };
    }
}
