package com.tradej.app.integration;

import com.tradej.core.domain.event.CandleClosed;
import com.tradej.core.domain.event.EventMetadataFactory;
import com.tradej.core.domain.event.TickReceived;
import com.tradej.core.domain.model.Candle;
import com.tradej.core.domain.time.LiveTradingClock;
import com.tradej.core.domain.time.TradingClock;
import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.port.DomainEventHandler;
import com.tradej.core.domain.port.EventBus;
import com.tradej.persistence.chronicle.ChronicleAuditLogWriter;
import com.tradej.persistence.replay.ReplayRunner;
import com.tradej.strategy.service.CandleAggregationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies Chronicle audit replay reproduces the same candle closes as live aggregation.
 */
@Tag("component")
class ChronicleReplayParityIntegrationTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-05-27T10:00:00Z"), ZoneId.of("UTC"));

    private Path chroniclePath;

    @AfterEach
    void tearDown() throws Exception {
        if (chroniclePath == null) {
            return;
        }
        try (var paths = Files.walk(chroniclePath.getParent())) {
            paths.filter(p -> p.getFileName().toString().contains("chronicle-parity-"))
                    .sorted(Comparator.reverseOrder())
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (Exception ignored) {
                        }
                    });
        } catch (Exception ignored) {
        }
    }

    @Test
    void chronicleReplayPreservesCandleCloseHash() throws Exception {
        chroniclePath = Files.createTempDirectory("chronicle-parity-");
        TradingClock tradingClock = new LiveTradingClock(FIXED_CLOCK);
        EventMetadataFactory metadata = new EventMetadataFactory(tradingClock);
        CandleAggregationService aggregation = new CandleAggregationService(List.of("1s"));
        List<Candle> liveCloses = new ArrayList<>();

        try (ChronicleAuditLogWriter writer = new ChronicleAuditLogWriter(chroniclePath)) {
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
                aggregation.onDomainEvent(tick, event -> {
                    if (event instanceof CandleClosed closed) {
                        liveCloses.add(closed.candle());
                        writer.onEvent(closed);
                    }
                });
            }
        }

        List<Candle> replayedCloses = new CopyOnWriteArrayList<>();
        EventBus collectingBus = new CollectingEventBus(replayedCloses);

        try (ReplayRunner runner = new ReplayRunner(chroniclePath, collectingBus)) {
            var result = runner.replayAll(CandleClosed.class);
            assertTrue(result.isComplete(), result.summary());
            assertEquals(liveCloses.size(), result.replayed());
        }

        assertEquals(liveCloses.size(), replayedCloses.size(), "candle count must match");
        assertEquals(hashCloses(liveCloses), hashCloses(replayedCloses), "candle hash must match after chronicle replay");
    }

    private static final class CollectingEventBus implements EventBus {
        private final List<Candle> sink;

        CollectingEventBus(List<Candle> sink) {
            this.sink = sink;
        }

        @Override
        public <T extends DomainEvent> void subscribe(Class<T> eventType, DomainEventHandler<T> handler) {
        }

        @Override
        public <T extends DomainEvent> void unsubscribe(Class<T> eventType, DomainEventHandler<T> handler) {
        }

        @Override
        public void start() {
        }

        @Override
        public void stop() {
        }

        @Override
        public void publish(DomainEvent event) {
            if (event instanceof CandleClosed closed) {
                sink.add(closed.candle());
            }
        }
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
