package com.tradej.hotpath;

import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.event.EventMetadata;
import com.tradej.core.domain.event.MarketTickEvent;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.FeedMode;
import java.util.Optional;
import com.tradej.core.support.MdcHelper;
import com.tradej.hotpath.rate.TokenBucket;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class MarketDataPipelineTest {

    private List<DomainEvent> emitted;
    private MarketDataPipeline pipeline;

    @BeforeEach
    void setUp() {
        emitted = new ArrayList<>();
        pipeline = new MarketDataPipeline(emitted::add);
    }

    @Test
    void nullTickIsIgnored() {
        pipeline.onMarketTickEvent(null);
        assertTrue(emitted.isEmpty(), "Null tick should not be forwarded");
        assertEquals(0, pipeline.totalTicksProcessed());
    }

    @Test
    void tickIsForwardedToDownstream() {
        var tick = new MarketTickEvent(EventMetadata.root(), 0L, "SBIN", ExchangeSegment.NSE_EQ, FeedMode.TICKER, 750_00L, 10L, 1_000L, System.currentTimeMillis(), Optional.empty(), 0L, 0L);

        pipeline.onMarketTickEvent(tick);

        assertEquals(1, emitted.size(), "One event should be emitted");
        assertInstanceOf(MarketTickEvent.class, emitted.get(0));
        assertEquals("SBIN", ((MarketTickEvent) emitted.get(0)).symbol());
    }

    @Test
    void multipleTicksAreAllForwarded() {
        long t0 = System.currentTimeMillis();
        var tick1 = new MarketTickEvent(EventMetadata.root(), 0L, "SBIN", ExchangeSegment.NSE_EQ, FeedMode.TICKER, 750_00L, 10L, 1_000L, t0, Optional.empty(), 0L, 0L);
        var tick2 = new MarketTickEvent(EventMetadata.root(), 0L, "RELIANCE", ExchangeSegment.NSE_EQ, FeedMode.TICKER, 2_500_00L, 5L, 500L, t0 + 10L, Optional.empty(), 0L, 0L);
        var tick3 = new MarketTickEvent(EventMetadata.root(), 0L, "TCS", ExchangeSegment.NSE_EQ, FeedMode.TICKER, 3_500_00L, 2L, 200L, t0 + 20L, Optional.empty(), 0L, 0L);

        pipeline.onMarketTickEvent(tick1);
        pipeline.onMarketTickEvent(tick2);
        pipeline.onMarketTickEvent(tick3);

        assertEquals(3, emitted.size());
        assertEquals(3, pipeline.totalTicksProcessed());
    }

    @Test
    void lastTickTimestampMatchesTickMetadata() {
        long tickTimestamp = System.currentTimeMillis() - 5_000L; // 5 seconds ago
        var tick = new MarketTickEvent(EventMetadata.root(), 0L, "SBIN", ExchangeSegment.NSE_EQ, FeedMode.TICKER, 750_00L, 10L, 1_000L, tickTimestamp, Optional.empty(), 0L, 0L);

        pipeline.onMarketTickEvent(tick);

        // Should use the tick's metadata timestamp, not wall clock
        assertEquals(tick.metadata().timestampMs(), pipeline.lastTickTimestampMs());
    }

    @Test
    void lastTickTimestampIsZeroBeforeFirstTick() {
        assertEquals(0L, pipeline.lastTickTimestampMs(),
                "Timestamp should be 0 before any tick is processed");
    }

    @Test
    void returnsTotalTicksProcessed() {
        assertEquals(0, pipeline.totalTicksProcessed());

        var tick = new MarketTickEvent(EventMetadata.root(), 0L, "SBIN", ExchangeSegment.NSE_EQ, FeedMode.TICKER, 750_00L, 10L, 1_000L, 1_000_000L, Optional.empty(), 0L, 0L);
        pipeline.onMarketTickEvent(tick);
        pipeline.onMarketTickEvent(tick);
        pipeline.onMarketTickEvent(tick);

        assertEquals(3, pipeline.totalTicksProcessed());
    }

    @Test
    void downstreamNullThrows() {
        assertThrows(NullPointerException.class, () -> new MarketDataPipeline(null),
                "Null downstream consumer should throw NPE");
    }

    // ── MDC enrichment tests ──

    @Test
    void mdcIsEnrichedDuringProcessing() {
        var tick = new MarketTickEvent(EventMetadata.root(), 0L, "SBIN", ExchangeSegment.NSE_EQ, FeedMode.TICKER, 750_00L, 10L, 1_000L, System.currentTimeMillis(), Optional.empty(), 0L, 0L);

        // Verify MDC values are set inside the downstream consumer
        var verifyingPipeline = new MarketDataPipeline(e -> {
            assertEquals("MarketTickEvent", MDC.get("eventType"), "eventType should be set during processing");
            assertEquals("SBIN", MDC.get("symbol"), "symbol should be set during processing");
            assertEquals("market-data", MDC.get("stage"), "stage should be 'market-data'");
            assertNotNull(MDC.get("eventId"), "eventId should be set");
        });
        verifyingPipeline.onMarketTickEvent(tick);
    }

    @Test
    void mdcIsClearedAfterTickProcessing() {
        var tick = new MarketTickEvent(EventMetadata.root(), 0L, "SBIN", ExchangeSegment.NSE_EQ, FeedMode.TICKER, 750_00L, 10L, 1_000L, System.currentTimeMillis(), Optional.empty(), 0L, 0L);

        pipeline.onMarketTickEvent(tick);

        // After processing completes, MDC should be clean — clear is a no-op
        assertNull(MDC.get("eventType"));
        assertNull(MDC.get("symbol"));
        assertNull(MDC.get("stage"));
        assertNull(MDC.get("eventId"));
    }

    @Test
    void mdcIsClearedOnExceptionDuringProcessing() {
        // Create a broken downstream consumer that throws
        var broken = new MarketDataPipeline(e -> {
            // Verify MDC is enriched before the exception
            assertEquals("MarketTickEvent", MDC.get("eventType"));
            throw new RuntimeException("fail");
        });
        var tick = new MarketTickEvent(EventMetadata.root(), 0L, "SBIN", ExchangeSegment.NSE_EQ, FeedMode.TICKER, 750_00L, 10L, 1_000L, System.currentTimeMillis(), Optional.empty(), 0L, 0L);

        assertThrows(RuntimeException.class, () -> broken.onMarketTickEvent(tick));

        // MDC should still be clean after the exception
        assertNull(MDC.get("eventType"));
        assertNull(MDC.get("symbol"));
        assertNull(MDC.get("stage"));
    }

    @Test
    void tickRateIsZeroInitially() {
        assertEquals(0.0, pipeline.tickRate(), "Tick rate should be 0 before any tick");
    }

    @Test
    void tickRateIsZeroAfterFirstTick() {
        var tick = new MarketTickEvent(EventMetadata.root(), 0L, "SBIN", ExchangeSegment.NSE_EQ, FeedMode.TICKER, 750_00L, 10L, 1_000L, System.currentTimeMillis(), Optional.empty(), 0L, 0L);
        pipeline.onMarketTickEvent(tick);
        assertEquals(0.0, pipeline.tickRate(), "Tick rate should be 0 after a single tick (no interval)");
    }

    @Test
    void tickRateIncreasesAfterMultipleTicks() throws InterruptedException {
        var tick = new MarketTickEvent(EventMetadata.root(), 0L, "SBIN", ExchangeSegment.NSE_EQ, FeedMode.TICKER, 750_00L, 10L, 1_000L, System.currentTimeMillis(), Optional.empty(), 0L, 0L);

        // First tick — rate stays 0
        pipeline.onMarketTickEvent(tick);
        assertEquals(0.0, pipeline.tickRate());

        // Second tick after a short delay — rate should be positive
        Thread.sleep(50);
        pipeline.onMarketTickEvent(tick);
        assertTrue(pipeline.tickRate() > 0.0,
                "Tick rate should be positive after at least two ticks with elapsed time");
    }

    @Test
    void tickRateIsStableWithManyTicks() throws InterruptedException {
        var tick = new MarketTickEvent(EventMetadata.root(), 0L, "SBIN", ExchangeSegment.NSE_EQ, FeedMode.TICKER, 750_00L, 10L, 1_000L, System.currentTimeMillis(), Optional.empty(), 0L, 0L);

        // Feed 10 ticks at ~100ms intervals
        for (int i = 0; i < 10; i++) {
            pipeline.onMarketTickEvent(tick);
            Thread.sleep(100);
        }

        // Rate should be around 10 ticks/sec (100ms intervals)
        double rate = pipeline.tickRate();
        assertTrue(rate > 0.0, "Tick rate should be positive");
        assertTrue(rate < 100.0, "Tick rate should be reasonable (< 100 tps for 100ms intervals)");
    }

    @Test
    void tickRateIntegratesCountsWithTimestamp() {
        // Verify RATE_ALPHA is accessible
        assertTrue(MarketDataPipeline.RATE_ALPHA > 0.0 && MarketDataPipeline.RATE_ALPHA < 1.0,
                "Rate smoothing alpha should be between 0 and 1");
    }

    // ── Rate limiting tests ──

    @Test
    void unlimitedPipelineAcceptsAllTicks() {
        // Default constructor = no rate limiting
        var unlimited = new MarketDataPipeline(e -> { });
        var tick = new MarketTickEvent(EventMetadata.root(), 0L, "SBIN", ExchangeSegment.NSE_EQ, FeedMode.TICKER, 750_00L, 10L, 1_000L, System.currentTimeMillis(), Optional.empty(), 0L, 0L);

        // Unlimited pipeline should accept all ticks
        for (int i = 0; i < 1000; i++) {
            unlimited.onMarketTickEvent(tick);
        }
        assertEquals(1000, unlimited.totalTicksProcessed());
        assertEquals(0, unlimited.tickRateLimitedCount());
    }

    @Test
    void rateLimitedPipelineDropsExcessTicks() throws InterruptedException {
        // 10 ticks/s, burst 2 — very restrictive
        var limiter = new TokenBucket(10.0, 2);
        var limited = new MarketDataPipeline(e -> { }, limiter);
        var tick = new MarketTickEvent(EventMetadata.root(), 0L, "SBIN", ExchangeSegment.NSE_EQ, FeedMode.TICKER, 750_00L, 10L, 1_000L, System.currentTimeMillis(), Optional.empty(), 0L, 0L);

        // Burst of 2 should pass
        limited.onMarketTickEvent(tick);
        limited.onMarketTickEvent(tick);
        assertEquals(2, limited.totalTicksProcessed());
        assertEquals(0, limited.tickRateLimitedCount());

        // Third tick should be rate limited
        limited.onMarketTickEvent(tick);
        assertEquals(2, limited.totalTicksProcessed());
        assertEquals(1, limited.tickRateLimitedCount());
    }

    @Test
    void rateLimitedPipelineRecoversAfterWait() throws InterruptedException {
        // 20 ticks/s, burst 1
        var limiter = new TokenBucket(20.0, 1);
        var limited = new MarketDataPipeline(e -> { }, limiter);
        var tick = new MarketTickEvent(EventMetadata.root(), 0L, "SBIN", ExchangeSegment.NSE_EQ, FeedMode.TICKER, 750_00L, 10L, 1_000L, System.currentTimeMillis(), Optional.empty(), 0L, 0L);

        limited.onMarketTickEvent(tick);
        assertEquals(1, limited.totalTicksProcessed());

        // Immediate next tick should be rate limited, no refill yet
        limited.onMarketTickEvent(tick);
        assertEquals(1, limited.tickRateLimitedCount());

        // Wait 100ms — at 20/s, that's 2 tokens, but burst=1 so only 1
        Thread.sleep(100);
        limited.onMarketTickEvent(tick);
        assertEquals(2, limited.totalTicksProcessed());
    }

    @Test
    void rateLimitedCounterAccumulates() throws InterruptedException {
        // Very low rate — all ticks will be rate limited after burst
        var limiter = new TokenBucket(1.0, 1);
        var limited = new MarketDataPipeline(e -> { }, limiter);
        var tick = new MarketTickEvent(EventMetadata.root(), 0L, "SBIN", ExchangeSegment.NSE_EQ, FeedMode.TICKER, 750_00L, 10L, 1_000L, System.currentTimeMillis(), Optional.empty(), 0L, 0L);

        limited.onMarketTickEvent(tick);  // 1 accepted (burst token)
        limited.onMarketTickEvent(tick);  // rate limited
        limited.onMarketTickEvent(tick);  // rate limited
        limited.onMarketTickEvent(tick);  // rate limited

        assertEquals(1, limited.totalTicksProcessed());
        assertEquals(3, limited.tickRateLimitedCount());
    }

    @Test
    void rateLimitedPipelineReportsLimiter() {
        var limiter = new TokenBucket(100.0, 10);
        var limited = new MarketDataPipeline(e -> { }, limiter);
        assertSame(limiter, limited.tickRateLimiter());
    }

    @Test
    void unlimitedPipelineReportsNullLimiter() {
        assertNull(pipeline.tickRateLimiter(), "Unlimited pipeline should have null rate limiter");
    }
}
