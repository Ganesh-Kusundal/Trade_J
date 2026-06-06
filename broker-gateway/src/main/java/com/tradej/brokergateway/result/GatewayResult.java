package com.tradej.brokergateway.result;

import java.time.Duration;
import java.time.Instant;

/**
 * Every gateway operation returns a {@code GatewayResult} wrapping the domain data
 * with broker source identification, latency measurement, and request correlation.
 *
 * @param data     the normalized domain result (Quote, OptionChainSnapshot, etc.)
 * @param source   which broker produced this result
 * @param metadata timing, correlation, and optional broker-specific metadata
 */
public record GatewayResult<T>(
        T data,
        BrokerSource source,
        ResultMetadata metadata
) {

    public Duration latency() {
        return metadata.latency();
    }

    public long latencyMs() {
        return metadata.latencyMs();
    }

    public Instant receivedAt() {
        return metadata.receivedAt();
    }

    public String requestId() {
        return metadata.requestId();
    }

    public boolean isSuccess() {
        return data != null;
    }
}
