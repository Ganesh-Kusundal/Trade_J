package com.tradej.app.metrics;

import com.tradej.core.domain.event.EventMetadata;
import com.tradej.core.domain.event.MarketTickEvent;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.FeedMode;
import com.tradej.hotpath.MarketDataPipeline;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@Tag("component")
class McxFullSessionSoakTest {

    private MeterRegistry meterRegistry;
    private MarketDataPipeline pipeline;
    private MetricsLoggerHarness harness;
    private Path soakLogPath;

    @BeforeEach
    void setUp() throws IOException {
        meterRegistry = new SimpleMeterRegistry();
        soakLogPath = Paths.get("logs/soak/mcx-soak.log");
        Files.deleteIfExists(soakLogPath);
        Files.createDirectories(soakLogPath.getParent());

        harness = new MetricsLoggerHarness(meterRegistry, soakLogPath.toString(), true);
        pipeline = new MarketDataPipeline(event -> {
            // Downstream handler
        });
    }

    @Test
    void executeMcxFullSessionSoakTest() throws IOException {
        System.out.println("Starting simulated MCX Full Session Soak Test...");

        // Register metrics corresponding to MicrometerConfiguration
        meterRegistry.gauge("disruptor.ring.buffer.remaining_capacity", 1024.0);
        meterRegistry.gauge("disruptor.ring.buffer.size", 1024.0);
        meterRegistry.gauge("execution.queue.depth", 0.0);
        meterRegistry.gauge("dhan.websocket.connected", 1.0);
        meterRegistry.gauge("dhan.websocket.subscriptions", 5.0);

        // Simulate high-throughput tick stream (e.g. 5000 ticks) for MCX
        long start = System.currentTimeMillis();
        int tickCount = 5000;
        for (int i = 0; i < tickCount; i++) {
            MarketTickEvent tick = new MarketTickEvent(
                    EventMetadata.root(),
                    i,
                    "GOLD26JUN",
                    ExchangeSegment.MCX_COMM,
                    FeedMode.TICKER,
                    65000_00L + (i % 20),
                    1L,
                    5000L + i,
                    System.currentTimeMillis(),
                    Optional.empty(),
                    0L,
                    0L
            );
            pipeline.onMarketTickEvent(tick);
        }
        long duration = System.currentTimeMillis() - start;

        // Register pipeline metrics
        meterRegistry.gauge("hotpath.ticks.total", pipeline.totalTicksProcessed());
        meterRegistry.gauge("hotpath.ticks.rate", pipeline.tickRate());

        // Trigger metrics polling and logging
        harness.pollAndLog();

        // Verify successful execution and tick processing
        assertEquals(tickCount, pipeline.totalTicksProcessed(), "All ticks should be processed with zero loss");
        assertTrue(duration < 2000, "High-throughput processing should be extremely fast (stable latency)");

        // Verify log file was written and archived successfully
        assertTrue(Files.exists(soakLogPath), "MCX soak log file should exist");
        String logContent = Files.readString(soakLogPath);
        assertTrue(logContent.contains("hotpath_ticks_total"), "Log should contain processed tick count");
        assertTrue(logContent.contains("timestamp"), "Log should contain timestamp");

        System.out.println("Simulated MCX Full Session Soak Test completed successfully. Duration: " + duration + "ms");
    }
}
