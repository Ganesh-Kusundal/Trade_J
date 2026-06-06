package com.tradej.app.health;

import com.tradej.core.domain.event.EventMetadata;
import com.tradej.core.domain.event.MarketTickEvent;
import com.tradej.broker.api.model.BrokerTransportCapabilities;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.FeedMode;
import com.tradej.hotpath.MarketDataPipeline;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class MarketDataHealthIndicatorTest {

    private MarketDataPipeline pipeline;
    private MarketDataHealthIndicator indicator;

    @BeforeEach
    void setUp() {
        pipeline = new MarketDataPipeline(e -> { });
        indicator = new MarketDataHealthIndicator(pipeline, Duration.ofSeconds(30));
    }

    @Test
    void healthIsDownWhenNoTicksReceived() {
        var health = indicator.health();
        assertEquals("DOWN", health.getStatus().getCode());
    }

    @Test
    void healthShowsNoTicksYetStatusWhenEmpty() {
        var health = indicator.health();
        assertEquals("NO_TICKS_YET", health.getDetails().get("status"));
        assertEquals(0L, health.getDetails().get("totalTicks"));
    }

    @Test
    void healthIsUpAfterReceivingTicks() {
        var tick = new MarketTickEvent(
                EventMetadata.root(), 0L, "SBIN",
                ExchangeSegment.NSE_EQ, FeedMode.TICKER,
                750_00L, 10L, 1_000L,
                System.currentTimeMillis(), Optional.empty(), 0L, 0L
        );
        pipeline.onMarketTickEvent(tick);

        var health = indicator.health();
        assertEquals("UP", health.getStatus().getCode());
    }

    @Test
    void healthReportsActiveAfterRecentTick() {
        var tick = new MarketTickEvent(
                EventMetadata.root(), 0L, "SBIN",
                ExchangeSegment.NSE_EQ, FeedMode.TICKER,
                750_00L, 10L, 1_000L,
                System.currentTimeMillis(), Optional.empty(), 0L, 0L
        );
        pipeline.onMarketTickEvent(tick);

        var health = indicator.health();
        assertEquals("ACTIVE", health.getDetails().get("status"));
        assertEquals(1L, health.getDetails().get("totalTicks"));
        assertTrue((Double) health.getDetails().get("tickRate") >= 0.0);
    }

    @Test
    void healthIsDownWhenTickIsStale() {
        Instant baseTime = Instant.parse("2026-01-01T00:00:00Z");
        long tickTimestampMs = baseTime.toEpochMilli();

        pipeline.onMarketTickEvent(tickAt(tickTimestampMs));

        // Initially active — clock matches tick timestamp
        var activeIndicator = new MarketDataHealthIndicator(
                pipeline, Duration.ofMillis(200), null, Clock.fixed(baseTime, ZoneOffset.UTC));
        assertEquals("ACTIVE", activeIndicator.health().getDetails().get("status"));

        // Advance clock past the stale threshold (250ms > 200ms)
        var staleIndicator = new MarketDataHealthIndicator(
                pipeline, Duration.ofMillis(200), null, Clock.fixed(baseTime.plusMillis(250), ZoneOffset.UTC));

        var health = staleIndicator.health();
        assertEquals("DOWN", health.getStatus().getCode());
        assertEquals("STALLED", health.getDetails().get("status"));
    }

    @Test
    void healthDetailsIncludeTimestampAndThreshold() {
        var tick = new MarketTickEvent(
                EventMetadata.root(), 0L, "SBIN",
                ExchangeSegment.NSE_EQ, FeedMode.TICKER,
                750_00L, 10L, 1_000L,
                System.currentTimeMillis(), Optional.empty(), 0L, 0L
        );
        pipeline.onMarketTickEvent(tick);

        var health = indicator.health();
        assertEquals(tick.metadata().timestampMs(), health.getDetails().get("lastTickTimestampMs"));
        assertEquals(Duration.ofSeconds(30).toMillis(),
                health.getDetails().get("staleThresholdMs"));
        assertNotNull(health.getDetails().get("lastTickTimestampIso"));
    }

    @Test
    void healthUsesDefaultThreshold() {
        var defaultIndicator = new MarketDataHealthIndicator(pipeline, Duration.ofSeconds(30));
        var health = defaultIndicator.health();
        assertEquals(Duration.ofSeconds(30).toMillis(),
                health.getDetails().get("staleThresholdMs"));
    }

    @Test
    void healthIsDownWhenTicksStopAfterBeingActive() {
        Instant baseTime = Instant.parse("2026-01-01T00:00:00Z");

        // Send 3 ticks at baseTime+0ms, baseTime+5ms, baseTime+10ms
        for (int i = 0; i < 3; i++) {
            pipeline.onMarketTickEvent(tickAt(baseTime.toEpochMilli() + (i * 5)));
        }

        // Still active — last tick was 10ms ago, threshold is 200ms
        var activeIndicator = new MarketDataHealthIndicator(
                pipeline, Duration.ofMillis(200), null, Clock.fixed(baseTime, ZoneOffset.UTC));
        assertEquals("ACTIVE", activeIndicator.health().getDetails().get("status"));

        // Advance clock 260ms past the last tick (10 + 260 = 270 > 200ms threshold)
        var staleIndicator = new MarketDataHealthIndicator(
                pipeline, Duration.ofMillis(200), null, Clock.fixed(baseTime.plusMillis(260), ZoneOffset.UTC));

        var health = staleIndicator.health();
        assertEquals("DOWN", health.getStatus().getCode());
        assertEquals("STALLED", health.getDetails().get("status"));
        assertEquals(3L, health.getDetails().get("totalTicks"));
    }

    /** Creates a MarketTickEvent with a controlled metadata timestamp for deterministic time tests. */
    private static MarketTickEvent tickAt(long timestampMs) {
        var metadata = new EventMetadata(
                UUID.randomUUID().toString(),
                timestampMs, System.nanoTime(), 0L, "", 1);
        return new MarketTickEvent(
                metadata, 0L, "SBIN",
                ExchangeSegment.NSE_EQ, FeedMode.TICKER,
                750_00L, 10L, 1_000L,
                timestampMs, Optional.empty(), 0L, 0L);
    }

    @Test
    void healthIsUpForRestOnlyTransportWithoutTicks() {
        var restOnly = new MarketDataHealthIndicator(
                pipeline,
                Duration.ofSeconds(30),
                BrokerTransportCapabilities.upstoxAnalytics());
        var health = restOnly.health();
        assertEquals("UP", health.getStatus().getCode());
        assertEquals("REST_ONLY", health.getDetails().get("status"));
    }
}
