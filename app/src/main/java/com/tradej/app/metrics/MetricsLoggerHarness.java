package com.tradej.app.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.ThreadMXBean;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * A harness to poll and log JMX and Micrometer metrics (heap, GC, threads, ring buffer depth)
 * during live trading sessions.
 *
 * <p>Logs are written both to standard SLF4J logger and to a dedicated soak test log file.
 */
@Component
public class MetricsLoggerHarness {

    private static final Logger log = LoggerFactory.getLogger(MetricsLoggerHarness.class);

    private final MeterRegistry meterRegistry;
    private final String logFilePath;
    private final boolean enabled;

    public MetricsLoggerHarness(
            MeterRegistry meterRegistry,
            @Value("${trade.metrics.harness.log-file:build/soak-test-metrics.log}") String logFilePath,
            @Value("${trade.metrics.harness.enabled:true}") boolean enabled
    ) {
        this.meterRegistry = meterRegistry;
        this.logFilePath = logFilePath;
        this.enabled = enabled;

        if (enabled) {
            log.info("MetricsLoggerHarness initialized. Logging to file={}", logFilePath);
            // Ensure parent directories exist
            try {
                Path path = Paths.get(logFilePath);
                if (path.getParent() != null) {
                    Files.createDirectories(path.getParent());
                }
            } catch (IOException e) {
                log.error("Failed to create metrics log directory", e);
            }
        }
    }

    /**
     * Scheduled polling task. Runs every 5 seconds.
     */
    @Scheduled(fixedDelayString = "${trade.metrics.harness.interval-ms:5000}")
    public void pollAndLog() {
        if (!enabled) {
            return;
        }

        try {
            Map<String, Object> metrics = collectMetrics();
            String formattedMessage = formatMetrics(metrics);
            logMessage(formattedMessage);
        } catch (Exception e) {
            log.error("Error polling and logging metrics in harness", e);
        }
    }

    /**
     * Collects all JMX and Micrometer metrics.
     */
    private Map<String, Object> collectMetrics() {
        Map<String, Object> metrics = new LinkedHashMap<>();

        metrics.put("timestamp", Instant.now().toString());

        // ── JVM JMX Metrics ──
        MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
        long heapUsed = memoryMXBean.getHeapMemoryUsage().getUsed();
        long heapCommitted = memoryMXBean.getHeapMemoryUsage().getCommitted();
        long heapMax = memoryMXBean.getHeapMemoryUsage().getMax();

        metrics.put("heap_used_bytes", heapUsed);
        metrics.put("heap_used_mb", heapUsed / (1024 * 1024));
        metrics.put("heap_committed_mb", heapCommitted / (1024 * 1024));
        metrics.put("heap_max_mb", heapMax / (1024 * 1024));

        ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
        metrics.put("threads_live", threadMXBean.getThreadCount());
        metrics.put("threads_peak", threadMXBean.getPeakThreadCount());

        long gcCount = 0;
        long gcTimeMs = 0;
        for (var gcBean : ManagementFactory.getGarbageCollectorMXBeans()) {
            long count = gcBean.getCollectionCount();
            long time = gcBean.getCollectionTime();
            if (count != -1) gcCount += count;
            if (time != -1) gcTimeMs += time;
        }
        metrics.put("gc_collection_count", gcCount);
        metrics.put("gc_collection_time_ms", gcTimeMs);

        // ── Custom Micrometer Metrics ──
        metrics.put("disruptor_ring_buffer_remaining_capacity", getMetricValue("disruptor.ring.buffer.remaining_capacity", -1.0));
        metrics.put("disruptor_ring_buffer_size", getMetricValue("disruptor.ring.buffer.size", -1.0));
        metrics.put("hotpath_ticks_total", getMetricValue("hotpath.ticks.total", 0.0));
        metrics.put("hotpath_ticks_rate", getMetricValue("hotpath.ticks.rate", 0.0));
        metrics.put("execution_queue_depth", getMetricValue("execution.queue.depth", 0.0));
        metrics.put("dhan_websocket_connected", getMetricValue("dhan.websocket.connected", 0.0));
        metrics.put("dhan_websocket_subscriptions", getMetricValue("dhan.websocket.subscriptions", 0.0));

        return metrics;
    }

    /**
     * Safely reads a metric value from the MeterRegistry.
     */
    private double getMetricValue(String name, double defaultValue) {
        try {
            var search = meterRegistry.find(name);
            var gauge = search.gauge();
            if (gauge != null) {
                return gauge.value();
            }
            var counter = search.counter();
            if (counter != null) {
                return counter.count();
            }
            var timer = search.timer();
            if (timer != null) {
                return timer.totalTime(java.util.concurrent.TimeUnit.MILLISECONDS);
            }
            var meter = search.meter();
            if (meter != null) {
                for (var measurement : meter.measure()) {
                    return measurement.getValue();
                }
            }
        } catch (Exception ignored) {
        }
        return defaultValue;
    }

    /**
     * Formats metrics map as a JSON-like structured line.
     */
    private String formatMetrics(Map<String, Object> metrics) {
        return "{" + metrics.entrySet().stream()
                .map(e -> "\"" + e.getKey() + "\":" + (e.getValue() instanceof String ? "\"" + e.getValue() + "\"" : e.getValue()))
                .collect(Collectors.joining(",")) + "}";
    }

    /**
     * Logs the formatted metrics message.
     */
    protected void logMessage(String message) {
        // Log to standard SLF4J logger at INFO level
        log.info("SOAK_TEST_METRICS: {}", message);

        // Append to the soak test metrics log file
        try {
            Path path = Paths.get(logFilePath);
            Files.writeString(path, message + System.lineSeparator(),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            log.error("Failed to write metrics to log file={}", logFilePath, e);
        }
    }
}
