package com.tradej.app.integration;

import com.tradej.core.domain.event.CandleClosed;
import com.tradej.core.domain.event.EventMetadataFactory;
import com.tradej.core.domain.event.TickReceived;
import com.tradej.core.domain.model.Candle;
import com.tradej.strategy.service.CandleAggregationService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies that the same tick stream produces identical candle closes on replay
 * (zero-parity for market aggregation logic).
 */
@Tag("component")
class ReplayParityHashTest {

  private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-05-27T10:00:00Z"), ZoneId.of("UTC"));

  @Test
  void liveAndReplayProduceIdenticalCandleCloseHash() {
    EventMetadataFactory metadata = new EventMetadataFactory(FIXED_CLOCK);
    CandleAggregationService live = new CandleAggregationService(List.of("1s"));
    CandleAggregationService replay = new CandleAggregationService(List.of("1s"));

    List<Candle> liveCloses = new ArrayList<>();
    List<Candle> replayCloses = new ArrayList<>();

    long baseMs = FIXED_CLOCK.millis();
    for (int i = 0; i < 120; i++) {
      TickReceived tick = new TickReceived(
          metadata.root(),
          "SBIN",
          "1s",
          100_000L + i,
          10L,
          1_000L + i,
          baseMs + (i * 1_000L),
          null
      );
      live.onDomainEvent(tick, event -> {
        if (event instanceof CandleClosed closed) {
          liveCloses.add(closed.candle());
        }
      });
      replay.onDomainEvent(tick, event -> {
        if (event instanceof CandleClosed closed) {
          replayCloses.add(closed.candle());
        }
      });
    }

    assertEquals(liveCloses.size(), replayCloses.size(), "candle count must match");
    assertEquals(hashCloses(liveCloses), hashCloses(replayCloses), "candle close hash must match on replay");
  }

  private static long hashCloses(List<Candle> candles) {
    AtomicLong hash = new AtomicLong(17L);
    for (Candle candle : candles) {
      hash.updateAndGet(h -> h * 31L + candle.startTimeMs());
      hash.updateAndGet(h -> h * 31L + candle.closePaisa());
      hash.updateAndGet(h -> h * 31L + candle.volume());
    }
    return hash.get();
  }
}
