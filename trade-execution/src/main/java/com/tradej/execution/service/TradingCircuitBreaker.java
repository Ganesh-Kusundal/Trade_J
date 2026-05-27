package com.tradej.execution.service;

import org.springframework.stereotype.Service;

@Service
public final class TradingCircuitBreaker {
    private final int failureThreshold;
    private final long openDurationMs;
    private int consecutiveFailures;
    private long openUntilMs;

    public TradingCircuitBreaker() {
        this(5, 30_000L);
    }

    public TradingCircuitBreaker(int failureThreshold, long openDurationMs) {
        this.failureThreshold = failureThreshold;
        this.openDurationMs = openDurationMs;
    }

    public synchronized boolean allowsRequest() {
        return System.currentTimeMillis() >= openUntilMs;
    }

    public synchronized void recordSuccess() {
        consecutiveFailures = 0;
        openUntilMs = 0L;
    }

    public synchronized void recordFailure() {
        consecutiveFailures++;
        if (consecutiveFailures >= failureThreshold) {
            openUntilMs = System.currentTimeMillis() + openDurationMs;
        }
    }

    public synchronized boolean isOpen() {
        return !allowsRequest();
    }
}
