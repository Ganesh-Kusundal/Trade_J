package com.tradej.broker.core.chaos;

import com.tradej.broker.api.IBrokerConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Executes chaos scenarios against a broker connection and collects metrics.
 */
public final class ChaosRunner {

    private final List<ChaosScenario> scenarios = new ArrayList<>();
    private Consumer<String> logSink = System.out::println;

    public ChaosRunner addScenario(ChaosScenario scenario) {
        scenarios.add(scenario);
        return this;
    }

    public ChaosRunner logTo(Consumer<String> sink) {
        this.logSink = sink;
        return this;
    }

    public List<ChaosResult> run(IBrokerConnection connection) {
        List<ChaosResult> results = new ArrayList<>();
        for (ChaosScenario scenario : scenarios) {
            ChaosMetrics metrics = new ChaosMetrics();
            ChaosScenario.ChaosContext context = new ChaosScenario.ChaosContext(
                    System.currentTimeMillis(), metrics, logSink);
            logSink.accept("=== Starting chaos scenario: " + scenario.name() + " ===");
            long start = System.currentTimeMillis();
            try {
                scenario.apply(connection, context);
                long duration = System.currentTimeMillis() - start;
                logSink.accept("=== Completed: " + scenario.name() + " (" + duration + "ms) ===");
                logSink.accept(metrics.toString());
                results.add(new ChaosResult(scenario.name(), true, duration, metrics, null));
            } catch (Exception ex) {
                long duration = System.currentTimeMillis() - start;
                logSink.accept("=== FAILED: " + scenario.name() + " — " + ex.getMessage() + " ===");
                results.add(new ChaosResult(scenario.name(), false, duration, metrics, ex));
            }
        }
        return results;
    }

    public record ChaosResult(
            String scenarioName,
            boolean passed,
            long durationMs,
            ChaosMetrics metrics,
            Exception error
    ) {}
}
