package com.tradej.app.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class MetricsLoggerHarnessTest {

    private MeterRegistry meterRegistry;
    private List<String> logSink;
    private MetricsLoggerHarness harness;
    private Path tempLogFile;

    @BeforeEach
    void setUp() throws IOException {
        meterRegistry = new SimpleMeterRegistry();
        logSink = new ArrayList<>();
        tempLogFile = Files.createTempFile("soak-test-metrics", ".log");

        // Create the harness with simple log sink for testing
        harness = new MetricsLoggerHarness(meterRegistry, tempLogFile.toString(), true) {
            @Override
            protected void logMessage(String message) {
                logSink.add(message);
                super.logMessage(message);
            }
        };
    }

    @Test
    void pollsAndLogsMetricsWhenEnabled() throws IOException {
        // Register some test metrics to simple registry
        meterRegistry.gauge("disruptor.ring.buffer.remaining_capacity", 1024.0);
        meterRegistry.gauge("hotpath.ticks.total", 5000.0);

        harness.pollAndLog();

        assertFalse(logSink.isEmpty(), "Should have logged metrics");
        String logLine = logSink.get(0);
        assertTrue(logLine.contains("heap_used"), "Log should contain heap_used: " + logLine);
        assertTrue(logLine.contains("threads"), "Log should contain threads: " + logLine);
        assertTrue(logLine.contains("disruptor_ring_buffer_remaining_capacity"), "Log should contain ring buffer remaining capacity: " + logLine);
        assertTrue(logLine.contains("hotpath_ticks_total"), "Log should contain ticks total: " + logLine);

        // Verify file was written
        List<String> fileLines = Files.readAllLines(tempLogFile);
        assertFalse(fileLines.isEmpty(), "Log file should not be empty");
        assertTrue(fileLines.get(0).contains("heap_used"), "Log file line should contain heap_used");
    }

    @Test
    void doesNotLogWhenDisabled() {
        MetricsLoggerHarness disabledHarness = new MetricsLoggerHarness(meterRegistry, tempLogFile.toString(), false) {
            @Override
            protected void logMessage(String message) {
                logSink.add(message);
            }
        };

        disabledHarness.pollAndLog();

        assertTrue(logSink.isEmpty(), "Should not have logged when disabled");
    }
}
