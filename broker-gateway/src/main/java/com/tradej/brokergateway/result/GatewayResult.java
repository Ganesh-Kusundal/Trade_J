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
        ResultMetadata metadata,
        boolean success
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
        return success;
    }

    public static <T> GatewayResult<T> success(T data, BrokerSource source, ResultMetadata metadata) {
        return new GatewayResult<>(data, source, metadata, true);
    }

    public static <T> GatewayResult<T> failed(BrokerSource source, ResultMetadata metadata) {
        return new GatewayResult<>(null, source, metadata, false);
    }
}
