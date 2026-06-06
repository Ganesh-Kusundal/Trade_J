package com.tradej.brokergateway.result;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * Metadata attached to every gateway result.
 *
 * @param latency          time between request dispatch and response receipt
 * @param receivedAt       wall-clock instant when the response was received
 * @param requestId        unique identifier for this request (correlation / dedup)
 * @param brokerHeaders    optional broker-specific response headers or metadata
 * @param rawResponseBody  optional raw JSON response body from the broker API
 */
public record ResultMetadata(
        Duration latency,
        Instant receivedAt,
        String requestId,
        Map<String, String> brokerHeaders,
        String rawResponseBody
) {
    /**
     * Convenience constructor without raw response body.
     */
    public ResultMetadata(Duration latency, Instant receivedAt, String requestId, Map<String, String> brokerHeaders) {
        this(latency, receivedAt, requestId, brokerHeaders, null);
    }

    public ResultMetadata {
        brokerHeaders = brokerHeaders == null ? Map.of() : Map.copyOf(brokerHeaders);
    }

    public long latencyMs() {
        return latency.toMillis();
    }

    /**
     * Whether a raw response body is available.
     */
    public boolean hasRawResponse() {
        return rawResponseBody != null && !rawResponseBody.isBlank();
    }
}
