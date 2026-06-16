package com.tradej.app.config;

import com.tradej.broker.core.metrics.BrokerResilienceMetrics;
import com.tradej.broker.core.resilience.CircuitBreaker;
import com.tradej.broker.core.resilience.CircuitBreakerMetrics;
import com.tradej.broker.dhan.auth.DhanTokenProvider;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.MultiGauge;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.binder.MeterBinder;
import io.micrometer.core.instrument.config.MeterFilter;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Metrics-domain configuration: Prometheus tags, order/signal counters, broker resilience
 * metrics, circuit-breaker gauges, and Dhan token-expiry gauge.
 *
 * <p>Consolidated from:
 * <ul>
 *   <li>{@code PrometheusConfiguration} — common tags, JVM GC timer, order/signal counters</li>
 *   <li>{@code CircuitBreakerMetricsConfiguration} — scheduled circuit-breaker gauges</li>
 *   <li>{@code TokenMetricsConfiguration} — Dhan token-expiry gauge</li>
 * </ul>
 *
 * <p><b>Why @PostConstruct instead of constructor for gauge registration:</b>
 * the original 3 files each had constructor-injected {@code MeterRegistry} and
 * worked fine in isolation. When merged into a single @Configuration, Spring's
 * metrics autoconfiguration (ObservationAutoConfiguration) creates a cycle:
 * autoconfig → simpleMeterRegistry → metricsConfiguration → simpleMeterRegistry.
 * Moving the gauge registration out of the constructor (to {@code @PostConstruct})
 * breaks the cycle by deferring the dependency lookup until after the bean
 * is fully constructed.
 */
@Configuration
@EnableScheduling
public class MetricsConfiguration {

    private static final Logger log = LoggerFactory.getLogger(MetricsConfiguration.class);

    private final MeterRegistry registry;
    private final ObjectProvider<DhanTokenProvider> tokenProvider;
    private MultiGauge circuitBreakerGauge;

    public MetricsConfiguration(MeterRegistry registry, ObjectProvider<DhanTokenProvider> tokenProvider) {
        this.registry = registry;
        this.tokenProvider = tokenProvider;
    }

    @PostConstruct
    void registerGauges() {
        // Register the circuit-breaker multi-gauge so it appears in /actuator/prometheus
        this.circuitBreakerGauge = MultiGauge.builder("broker.circuit.breaker.open")
                .description("Circuit breaker open state per operation. 1 = open, 0 = closed.")
                .baseUnit("boolean")
                .register(registry);
        log.info("Circuit breaker Micrometer gauge registered: broker.circuit.breaker.open");

        // Register the Dhan token-expiry gauge
        if (tokenProvider.getIfAvailable() == null) {
            log.info("DhanTokenProvider not available — broker.auth.token.remaining.seconds will report 0 (non-Dhan profile or token not configured)");
        }
        Gauge.builder("broker.auth.token.remaining.seconds", () -> {
                    DhanTokenProvider provider = tokenProvider.getIfAvailable();
                    if (provider == null) {
                        return 0L;
                    }
                    try {
                        return provider.tokenRemainingSeconds();
                    } catch (Exception e) {
                        return 0L;
                    }
                })
                .description("Seconds until the broker auth token expires. 0 = expired or unavailable.")
                .baseUnit("seconds")
                .register(registry);
        log.info("Dhan token expiry gauge registered: broker.auth.token.remaining.seconds");
    }

    // ── Prometheus common tags + order/signal counters + JVM GC binder ──

    @Bean
    MeterFilter commonTags() {
        return MeterFilter.commonTags(List.of(
                Tag.of("application", "trade-j"),
                Tag.of("region", "local")
        ));
    }

    @Bean
    MeterBinder jvmMetricsWithTags() {
        return registry -> {
            Timer.builder("jvm.gc.pause")
                    .description("JVM GC pause time")
                    .register(registry);
        };
    }

    @Bean
    Counter ordersPlacedTotal(MeterRegistry registry) {
        return Counter.builder("orders_placed_total")
                .description("Total number of orders placed successfully")
                .tag("application", "trade-j")
                .register(registry);
    }

    @Bean
    Counter ordersRejectedTotal(MeterRegistry registry) {
        return Counter.builder("orders_rejected_total")
                .description("Total number of orders rejected")
                .tag("application", "trade-j")
                .register(registry);
    }

    @Bean
    Counter signalsGeneratedTotal(MeterRegistry registry) {
        return Counter.builder("signals_generated_total")
                .description("Total number of trading signals generated")
                .tag("application", "trade-j")
                .register(registry);
    }

    // ── Broker resilience + circuit-breaker callback ──

    @Bean
    public BrokerResilienceMetrics brokerResilienceMetrics(MeterRegistry meterRegistry) {
        return new BrokerResilienceMetrics(meterRegistry);
    }

    @Bean
    public CircuitBreakerMetrics circuitBreakerMetrics(BrokerResilienceMetrics brokerResilienceMetrics) {
        return brokerResilienceMetrics;
    }

    // ── Scheduled circuit-breaker gauge refresh ──

    @Scheduled(fixedDelay = 30, initialDelay = 10, timeUnit = TimeUnit.SECONDS)
    void refreshCircuitBreakerGauges() {
        if (circuitBreakerGauge == null) {
            return; // @PostConstruct hasn't run yet (e.g., during context refresh)
        }
        try {
            Map<String, Boolean> states = CircuitBreaker.snapshotAllCircuitStates();
            if (states.isEmpty()) {
                return;
            }
            List<MultiGauge.Row<Number>> rows = states.entrySet().stream()
                    .map(e -> MultiGauge.Row.<Number>of(
                            Tags.of(Tag.of("operation", e.getKey())),
                            e.getValue() ? 1 : 0))
                    .toList();
            circuitBreakerGauge.register(rows, true);
        } catch (Exception e) {
            log.debug("Failed to refresh circuit breaker gauges: {}", e.getMessage());
        }
    }
}
