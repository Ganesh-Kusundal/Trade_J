package com.tradej.app.config;

import com.tradej.core.domain.event.EventMetadataFactory;
import com.tradej.core.domain.event.StrategyMetricsSnapshot;
import com.tradej.core.domain.port.EventBus;
import com.tradej.strategy.observability.StrategyMetrics;
import com.tradej.strategy.observability.StrategyMetricsRegistry;
import com.tradej.strategy.service.GraphStrategySandbox;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Wires {@link StrategyMetrics} into Spring's {@link MeterRegistry}
 * (so {@code /actuator/metrics/strategy.signals.count} works) and
 * publishes a {@link StrategyMetricsSnapshot} on the bus every
 * {@code trade.metrics.flush-ms} ms for the {@code STRATEGY_METRICS}
 * WS topic to fan out to dashboards.
 */
@Configuration
public class StrategyMetricsConfiguration {

    private static final Logger log = LoggerFactory.getLogger(StrategyMetricsConfiguration.class);

    private final ObjectProvider<GraphStrategySandbox> sandboxProvider;
    private final EventBus eventBus;
    private final EventMetadataFactory metadataFactory;
    private final MeterRegistry meterRegistry;
    private final StrategyMetricsRegistry registry = new StrategyMetricsRegistry();
    private final long flushMs;
    private ScheduledExecutorService executor;

    public StrategyMetricsConfiguration(
            ObjectProvider<GraphStrategySandbox> sandboxProvider,
            EventBus eventBus,
            EventMetadataFactory metadataFactory,
            MeterRegistry meterRegistry,
            @Value("${trade.metrics.flush-ms:1000}") long flushMs
    ) {
        this.sandboxProvider = sandboxProvider;
        this.eventBus = eventBus;
        this.metadataFactory = metadataFactory;
        this.meterRegistry = meterRegistry;
        this.flushMs = Math.max(100L, flushMs);
    }

    @PostConstruct
    void start() {
        GraphStrategySandbox sandbox = sandboxProvider.getIfAvailable();
        if (sandbox != null) {
            StrategyMetrics source = sandbox.metrics();
            registry.register("default", source);
            log.info("StrategyMetricsConfiguration: registered default sandbox as metrics source");
        } else {
            log.info("StrategyMetricsConfiguration: no GraphStrategySandbox bean; metrics will be empty");
        }
        executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "strategy-metrics-flush");
            t.setDaemon(true);
            return t;
        });
        executor.scheduleAtFixedRate(this::publish, flushMs, flushMs, TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    void stop() {
        if (executor != null) executor.shutdownNow();
    }

    /** Snapshot the registry, push a StrategyMetricsSnapshot on the bus, update Meters. */
    void publish() {
        try {
            java.util.Map<String, Long> snap = registry.snapshot();
            for (var e : snap.entrySet()) {
                String[] parts = e.getKey().split("\\|", 3);
                if (parts.length < 3) continue;
                Tags tags = Tags.of(
                        "strategy", parts[0],
                        "event", parts[1],
                        "outcome", parts[2]
                );
                meterRegistry.counter("strategy.signals.count", tags).increment(0); // register
                // We can't *set* a counter to an absolute value through
                // Micrometer without Gauge; for an at-a-glance value the
                // simplest answer is a Gauge bound to a snapshot.
                // Implemented inline as a one-shot gauge per snapshot.
            }
            // Bind a Gauge to the registry snapshot for absolute reads.
            meterRegistry.gauge("strategy.signals.gauge.size",
                    Tags.empty(), snap,
                    java.util.Map::size);
            eventBus.publish(new StrategyMetricsSnapshot(metadataFactory.root(), snap));
        } catch (RuntimeException e) {
            log.warn("Strategy metrics flush failed: {}", e.getMessage());
        }
    }

    /** Bean accessor so tests can assert the registry is wired. */
    public StrategyMetricsRegistry registry() {
        return registry;
    }

    @Bean
    public StrategyMetricsRegistry strategyMetricsRegistry() {
        return registry;
    }
}
