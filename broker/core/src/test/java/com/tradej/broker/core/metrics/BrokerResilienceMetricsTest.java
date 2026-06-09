package com.tradej.broker.core.metrics;

import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class BrokerResilienceMetricsTest {

    @Test
    void recordStateChange_incrementsCounter() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        BrokerResilienceMetrics metrics = new BrokerResilienceMetrics(registry);

        metrics.recordStateChange("dhan", "CLOSED", "OPEN");

        double count = registry.counter("broker.circuit_breaker.state_change",
                Tags.of("broker", "dhan", "from_state", "CLOSED", "to_state", "OPEN")).count();
        assertEquals(1.0, count);
    }

    @Test
    void recordStateChange_multipleTransitions() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        BrokerResilienceMetrics metrics = new BrokerResilienceMetrics(registry);

        metrics.recordStateChange("dhan", "CLOSED", "OPEN");
        metrics.recordStateChange("dhan", "OPEN", "CLOSED");
        metrics.recordStateChange("dhan", "CLOSED", "OPEN");

        assertEquals(2.0, registry.counter("broker.circuit_breaker.state_change",
                Tags.of("broker", "dhan", "from_state", "CLOSED", "to_state", "OPEN")).count());
        assertEquals(1.0, registry.counter("broker.circuit_breaker.state_change",
                Tags.of("broker", "dhan", "from_state", "OPEN", "to_state", "CLOSED")).count());
    }

    @Test
    void recordStateChange_updatesStateGauge() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        BrokerResilienceMetrics metrics = new BrokerResilienceMetrics(registry);

        metrics.recordStateChange("dhan", "CLOSED", "OPEN");
        var gauge = registry.find("broker.circuit_breaker.state").tag("broker", "dhan").gauge();
        assertNotNull(gauge);
        assertEquals(2.0, gauge.value());

        metrics.recordStateChange("dhan", "OPEN", "CLOSED");
        assertEquals(0.0, gauge.value());
    }

    @Test
    void recordExecution_recordsTimer() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        BrokerResilienceMetrics metrics = new BrokerResilienceMetrics(registry);

        metrics.recordExecution("dhan", "place_order", 42L, true);

        Timer timer = registry.find("broker.circuit_breaker.execution.time")
                .tag("broker", "dhan")
                .tag("operation", "place_order")
                .timer();
        assertNotNull(timer);
        assertEquals(1, timer.count());
        assertEquals(42.0, timer.totalTime(java.util.concurrent.TimeUnit.MILLISECONDS));
    }

    @Test
    void recordExecution_failed() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        BrokerResilienceMetrics metrics = new BrokerResilienceMetrics(registry);

        metrics.recordExecution("upstox", "cancel_order", 15L, false);

        Timer timer = registry.find("broker.circuit_breaker.execution.time")
                .tag("broker", "upstox")
                .tag("operation", "cancel_order")
                .timer();
        assertNotNull(timer);
        assertEquals(1, timer.count());
    }

    @Test
    void recordFailover_incrementsCounter() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        BrokerResilienceMetrics metrics = new BrokerResilienceMetrics(registry);

        metrics.recordFailover("dhan", "upstox", "connection_timeout");

        double count = registry.counter("broker.failover.execution",
                Tags.of("from_broker", "dhan", "to_broker", "upstox", "reason", "connection_timeout")).count();
        assertEquals(1.0, count);
    }

    @Test
    void recordRetryAttempt_incrementsCounter() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        BrokerResilienceMetrics metrics = new BrokerResilienceMetrics(registry);

        metrics.recordRetryAttempt("dhan", true);
        metrics.recordRetryAttempt("dhan", false);

        assertEquals(1.0, registry.counter("broker.retry.attempt",
                Tags.of("broker", "dhan", "success", "true")).count());
        assertEquals(1.0, registry.counter("broker.retry.attempt",
                Tags.of("broker", "dhan", "success", "false")).count());
    }

    @Test
    void recordRetryExecutionTime_recordsTimer() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        BrokerResilienceMetrics metrics = new BrokerResilienceMetrics(registry);

        metrics.recordRetryExecutionTime("dhan", 120L);

        Timer timer = registry.find("broker.retry.execution.time").tag("broker", "dhan").timer();
        assertNotNull(timer);
        assertEquals(1, timer.count());
        assertEquals(120.0, timer.totalTime(java.util.concurrent.TimeUnit.MILLISECONDS));
    }

    @Test
    void recordFailureCount_updatesGauge() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        BrokerResilienceMetrics metrics = new BrokerResilienceMetrics(registry);

        metrics.recordFailureCount("dhan", 3);
        var gauge = registry.find("broker.circuit_breaker.failure_count").tag("broker", "dhan").gauge();
        assertNotNull(gauge);
        assertEquals(3.0, gauge.value());

        metrics.recordFailureCount("dhan", 5);
        assertEquals(5.0, gauge.value());
    }

    @Test
    void multipleBrokers_areIsolated() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        BrokerResilienceMetrics metrics = new BrokerResilienceMetrics(registry);

        metrics.recordFailover("dhan", "upstox", "timeout");
        metrics.recordFailover("upstox", "icici", "rate_limit");

        assertEquals(1.0, registry.counter("broker.failover.execution",
                Tags.of("from_broker", "dhan", "to_broker", "upstox", "reason", "timeout")).count());
        assertEquals(1.0, registry.counter("broker.failover.execution",
                Tags.of("from_broker", "upstox", "to_broker", "icici", "reason", "rate_limit")).count());
    }
}
