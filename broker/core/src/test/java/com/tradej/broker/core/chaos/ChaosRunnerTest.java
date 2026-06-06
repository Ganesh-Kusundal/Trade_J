package com.tradej.broker.core.chaos;

import com.tradej.broker.api.IBrokerConnection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("unit")
class ChaosRunnerTest {

    private IBrokerConnection connection;

    @BeforeEach
    void setUp() {
        connection = mock(IBrokerConnection.class);
    }

    // ── ChaosMetrics tests ──────────────────────────────────────────────

    @Test
    void recordSuccessIncrementsCounters() {
        ChaosMetrics metrics = new ChaosMetrics();

        metrics.recordSuccess(100);
        metrics.recordSuccess(200);

        assertEquals(2, metrics.requestsAttempted());
        assertEquals(2, metrics.requestsSucceeded());
        assertEquals(0, metrics.requestsFailed());
    }

    @Test
    void recordFailureIncrementsFailureCounter() {
        ChaosMetrics metrics = new ChaosMetrics();

        metrics.recordFailure();
        metrics.recordFailure();
        metrics.recordFailure();

        assertEquals(3, metrics.requestsAttempted());
        assertEquals(0, metrics.requestsSucceeded());
        assertEquals(3, metrics.requestsFailed());
    }

    @Test
    void successRateCalculatesCorrectly() {
        ChaosMetrics metrics = new ChaosMetrics();

        // No requests: default rate is 1.0
        assertEquals(1.0, metrics.successRate(), 0.001);

        metrics.recordSuccess(50);
        metrics.recordSuccess(50);
        metrics.recordFailure();

        // 2 succeeded out of 3 attempted = 0.6667
        assertEquals(2.0 / 3.0, metrics.successRate(), 0.001);
    }

    @Test
    void averageLatencyMsCalculatesCorrectly() {
        ChaosMetrics metrics = new ChaosMetrics();

        // No successes: average is 0
        assertEquals(0, metrics.averageLatencyMs());

        metrics.recordSuccess(100);
        metrics.recordSuccess(200);
        metrics.recordSuccess(300);

        // (100 + 200 + 300) / 3 = 200
        assertEquals(200, metrics.averageLatencyMs());
    }

    @Test
    void maxLatencyMsTracksMaximum() {
        ChaosMetrics metrics = new ChaosMetrics();

        // No records: max is 0
        assertEquals(0, metrics.maxLatencyMs());

        metrics.recordSuccess(100);
        assertEquals(100, metrics.maxLatencyMs());

        metrics.recordSuccess(50);
        assertEquals(100, metrics.maxLatencyMs()); // max unchanged

        metrics.recordSuccess(500);
        assertEquals(500, metrics.maxLatencyMs()); // new max

        metrics.recordSuccess(200);
        assertEquals(500, metrics.maxLatencyMs()); // max unchanged
    }

    // ── ChaosRunner tests ───────────────────────────────────────────────

    @Test
    void runExecutesAllScenarios() {
        ChaosScenario scenario1 = mock(ChaosScenario.class);
        when(scenario1.name()).thenReturn("scenario-1");

        ChaosScenario scenario2 = mock(ChaosScenario.class);
        when(scenario2.name()).thenReturn("scenario-2");

        ChaosScenario scenario3 = mock(ChaosScenario.class);
        when(scenario3.name()).thenReturn("scenario-3");

        ChaosRunner runner = new ChaosRunner()
                .addScenario(scenario1)
                .addScenario(scenario2)
                .addScenario(scenario3)
                .logTo(msg -> {}); // silent

        List<ChaosRunner.ChaosResult> results = runner.run(connection);

        assertEquals(3, results.size());
        verify(scenario1).apply(eq(connection), any(ChaosScenario.ChaosContext.class));
        verify(scenario2).apply(eq(connection), any(ChaosScenario.ChaosContext.class));
        verify(scenario3).apply(eq(connection), any(ChaosScenario.ChaosContext.class));
    }

    @Test
    void runReturnsResultsWithCorrectScenarioNames() {
        ChaosScenario scenario1 = mock(ChaosScenario.class);
        when(scenario1.name()).thenReturn("latency-injection");

        ChaosScenario scenario2 = mock(ChaosScenario.class);
        when(scenario2.name()).thenReturn("circuit-breaker-test");

        ChaosRunner runner = new ChaosRunner()
                .addScenario(scenario1)
                .addScenario(scenario2)
                .logTo(msg -> {});

        List<ChaosRunner.ChaosResult> results = runner.run(connection);

        assertEquals(2, results.size());
        assertEquals("latency-injection", results.get(0).scenarioName());
        assertEquals("circuit-breaker-test", results.get(1).scenarioName());
        assertTrue(results.get(0).passed());
        assertTrue(results.get(1).passed());
    }

    @Test
    void runCapturesExceptionOnScenarioFailure() {
        RuntimeException failure = new RuntimeException("chaos explosion");
        ChaosScenario failingScenario = mock(ChaosScenario.class);
        when(failingScenario.name()).thenReturn("failing-scenario");
        doThrow(failure).when(failingScenario).apply(any(), any());

        ChaosRunner runner = new ChaosRunner()
                .addScenario(failingScenario)
                .logTo(msg -> {});

        List<ChaosRunner.ChaosResult> results = runner.run(connection);

        assertEquals(1, results.size());
        ChaosRunner.ChaosResult result = results.get(0);
        assertEquals("failing-scenario", result.scenarioName());
        assertFalse(result.passed());
        assertSame(failure, result.error());
        assertNotNull(result.metrics());
        assertTrue(result.durationMs() >= 0);
    }
}
