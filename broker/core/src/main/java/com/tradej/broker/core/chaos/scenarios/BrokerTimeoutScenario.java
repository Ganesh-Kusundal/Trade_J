package com.tradej.broker.core.chaos.scenarios;

import com.tradej.broker.api.IBrokerConnection;
import com.tradej.broker.core.chaos.ChaosScenario;
import com.tradej.core.domain.model.InstrumentKey;
import com.tradej.core.domain.value.ExchangeSegment;

/**
 * Simulates broker timeouts by making rapid LTP requests and measuring latency.
 * Validates that the retry executor and circuit breaker handle slow responses.
 */
public final class BrokerTimeoutScenario implements ChaosScenario {

    private final int requestCount;
    private final long maxAcceptableLatencyMs;

    public BrokerTimeoutScenario(int requestCount, long maxAcceptableLatencyMs) {
        this.requestCount = requestCount;
        this.maxAcceptableLatencyMs = maxAcceptableLatencyMs;
    }

    @Override
    public String name() { return "BrokerTimeout"; }

    @Override
    public void apply(IBrokerConnection connection, ChaosContext context) {
        InstrumentKey key = InstrumentKey.of("RELIANCE", ExchangeSegment.NSE_EQ);
        for (int i = 0; i < requestCount; i++) {
            long start = System.currentTimeMillis();
            try {
                long ltp = connection.marketData().getLtpPaisa(key);
                long latency = System.currentTimeMillis() - start;
                context.metrics().recordSuccess(latency);
                context.log("Request " + (i + 1) + ": LTP=" + ltp + " latency=" + latency + "ms");
            } catch (Exception ex) {
                context.metrics().recordFailure();
                context.log("Request " + (i + 1) + ": FAILED — " + ex.getMessage());
            }
        }
    }
}
