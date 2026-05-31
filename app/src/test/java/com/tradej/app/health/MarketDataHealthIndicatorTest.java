package com.tradej.app.health;

import com.tradej.core.domain.event.EventMetadata;
import com.tradej.core.domain.event.TickReceived;
import com.tradej.broker.api.model.BrokerTransportCapabilities;
import com.tradej.hotpath.MarketDataPipeline;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;

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
        var tick = new TickReceived(
                EventMetadata.root(), "SBIN", "5m", 750_00L, 10L, 1_000L,
                System.currentTimeMillis(), null
        );
        pipeline.onTickReceived(tick);

        var health = indicator.health();
        assertEquals("UP", health.getStatus().getCode());
    }

    @Test
    void healthReportsActiveAfterRecentTick() {
        var tick = new TickReceived(
                EventMetadata.root(), "SBIN", "5m", 750_00L, 10L, 1_000L,
                System.currentTimeMillis(), null
        );
        pipeline.onTickReceived(tick);

        var health = indicator.health();
        assertEquals("ACTIVE", health.getDetails().get("status"));
        assertEquals(1L, health.getDetails().get("totalTicks"));
        assertTrue((Double) health.getDetails().get("tickRate") >= 0.0);
    }

    @Test
    void healthIsDownWhenTickIsStale() throws InterruptedException {
        // Use a very short stale threshold
        var shortThreshold = new MarketDataHealthIndicator(pipeline, Duration.ofMillis(10));

        var tick = new TickReceived(
                EventMetadata.root(), "SBIN", "5m", 750_00L, 10L, 1_000L,
                System.currentTimeMillis(), null
        );
        pipeline.onTickReceived(tick);

        // Initially active
        assertEquals("ACTIVE", shortThreshold.health().getDetails().get("status"));

        // Wait for threshold to expire
        Thread.sleep(30);

        var health = shortThreshold.health();
        assertEquals("DOWN", health.getStatus().getCode());
        assertEquals("STALLED", health.getDetails().get("status"));
    }

    @Test
    void healthDetailsIncludeTimestampAndThreshold() {
        var tick = new TickReceived(
                EventMetadata.root(), "SBIN", "5m", 750_00L, 10L, 1_000L,
                System.currentTimeMillis(), null
        );
        pipeline.onTickReceived(tick);

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
    void healthIsDownWhenTicksStopAfterBeingActive() throws InterruptedException {
        var shortThreshold = new MarketDataHealthIndicator(pipeline, Duration.ofMillis(20));

        // Send a few ticks
        for (int i = 0; i < 3; i++) {
            var tick = new TickReceived(
                    EventMetadata.root(), "SBIN", "5m", 750_00L, 10L, 1_000L,
                    System.currentTimeMillis(), null
            );
            pipeline.onTickReceived(tick);
            Thread.sleep(5);
        }

        // Was active
        assertEquals("ACTIVE", shortThreshold.health().getDetails().get("status"));

        // Wait for stall
        Thread.sleep(30);

                var health = shortThreshold.health();
        assertEquals("DOWN", health.getStatus().getCode());
        assertEquals("STALLED", health.getDetails().get("status"));
        assertEquals(3L, health.getDetails().get("totalTicks"));
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
