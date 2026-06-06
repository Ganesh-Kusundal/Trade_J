package com.tradej.broker.core.chaos;

import com.tradej.broker.api.IBrokerConnection;
import java.util.function.Consumer;

/**
 * A chaos scenario that injects failures into a broker connection
 * to validate resilience behavior.
 */
public interface ChaosScenario {
    String name();
    void apply(IBrokerConnection connection, ChaosContext context);

    record ChaosContext(
        long startTimeMs,
        ChaosMetrics metrics,
        Consumer<String> logSink
    ) {
        public void log(String message) {
            if (logSink != null) logSink.accept(message);
        }
    }
}
