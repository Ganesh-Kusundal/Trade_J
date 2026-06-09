package com.tradej.broker.core.metrics;

import com.tradej.broker.core.resilience.CircuitBreakerMetrics;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Micrometer-backed metrics for broker resilience operations:
 * circuit breaker state changes, failover events, and retry attempts.
 */
public final class BrokerResilienceMetrics implements CircuitBreakerMetrics {

    private final MeterRegistry registry;
    private final ConcurrentHashMap<String, AtomicInteger> failureCounts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicInteger> stateGauges = new ConcurrentHashMap<>();

    public BrokerResilienceMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    // ── Circuit Breaker ──

    @Override
    public void recordStateChange(String broker, String fromState, String toState) {
        Counter.builder("broker.circuit_breaker.state_change")
                .tag("broker", broker)
                .tag("from_state", fromState)
                .tag("to_state", toState)
                .description("Circuit breaker state transitions")
                .register(registry)
                .increment();
        stateGauges.computeIfAbsent(broker, k -> {
            AtomicInteger gauge = new AtomicInteger(0);
            registry.gauge("broker.circuit_breaker.state",
                    io.micrometer.core.instrument.Tags.of("broker", broker),
                    gauge);
            return gauge;
        }).set(stateOrdinal(toState));
    }

    @Override
    public void recordExecution(String broker, String operation, long durationMs, boolean success) {
        Timer.builder("broker.circuit_breaker.execution.time")
                .tag("broker", broker)
                .tag("operation", operation)
                .description("Circuit breaker execution time")
                .register(registry)
                .record(durationMs, TimeUnit.MILLISECONDS);
    }

    public void recordFailureCount(String broker, int count) {
        failureCounts.computeIfAbsent(broker, k -> {
            AtomicInteger gauge = new AtomicInteger(0);
            registry.gauge("broker.circuit_breaker.failure_count",
                    io.micrometer.core.instrument.Tags.of("broker", broker),
                    gauge);
            return gauge;
        }).set(count);
    }

    // ── Failover ──

    public void recordFailover(String fromBroker, String toBroker, String reason) {
        Counter.builder("broker.failover.execution")
                .tag("from_broker", fromBroker)
                .tag("to_broker", toBroker)
                .tag("reason", reason)
                .description("Broker failover events")
                .register(registry)
                .increment();
    }

    // ── Retry ──

    public void recordRetryAttempt(String broker, boolean success) {
        Counter.builder("broker.retry.attempt")
                .tag("broker", broker)
                .tag("success", String.valueOf(success))
                .description("Retry attempts")
                .register(registry)
                .increment();
    }

    public void recordRetryExecutionTime(String broker, long durationMs) {
        Timer.builder("broker.retry.execution.time")
                .tag("broker", broker)
                .description("Total retry execution time including backoffs")
                .register(registry)
                .record(durationMs, TimeUnit.MILLISECONDS);
    }

    private static int stateOrdinal(String state) {
        return switch (state) {
            case "CLOSED" -> 0;
            case "HALF_OPEN" -> 1;
            case "OPEN" -> 2;
            default -> -1;
        };
    }
}
