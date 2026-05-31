package com.tradej.broker.api.resilience;

/**
 * Broker-agnostic error classification.
 * <p>
 * Every broker adapter maps HTTP status codes and response bodies to one of these categories.
 * The core retry/circuit-breaker infrastructure uses this classification to decide:
 * <ul>
 *   <li>Should the operation be retried?</li>
 *   <li>Should the circuit be opened?</li>
 *   <li>Should the token be refreshed?</li>
 *   <li>Should the error be escalated?</li>
 * </ul>
 */
public final class BrokerErrorCategory {

    private final String name;

    private BrokerErrorCategory(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return name;
    }

    // ─── Predefined categories ─────────────────────────────────────────────────

    /** Token has expired — trigger refresh silently, then retry. */
    public static final BrokerErrorCategory AUTH_EXPIRED    = new BrokerErrorCategory("AUTH_EXPIRED");

    /** Token has been revoked by the broker — full re-authentication required. */
    public static final BrokerErrorCategory AUTH_REVOKED    = new BrokerErrorCategory("AUTH_REVOKED");

    /** HTTP 429 or broker-level rate-limit — apply backoff, then retry. */
    public static final BrokerErrorCategory RATE_LIMITED    = new BrokerErrorCategory("RATE_LIMITED");

    /** Order rejected by exchange (e.g. insufficient margin, invalid price) — map to OrderRejectedEvent, do NOT retry. */
    public static final BrokerErrorCategory EXCHANGE_REJECTED = new BrokerErrorCategory("EXCHANGE_REJECTED");

    /** Exchange or broker unavailable (HTTP 5xx, connection refused) — circuit break, retry after backoff. */
    public static final BrokerErrorCategory SERVICE_DOWN    = new BrokerErrorCategory("SERVICE_DOWN");

    /** Transient network error (timeout, connection reset, DNS resolution failure) — retry with backoff. */
    public static final BrokerErrorCategory NETWORK_TRANSIENT = new BrokerErrorCategory("NETWORK_TRANSIENT");

    /** Permanent network failure (unknown host, SSL handshake failure) — do NOT retry, escalate. */
    public static final BrokerErrorCategory NETWORK_PERMANENT = new BrokerErrorCategory("NETWORK_PERMANENT");

    /** Invalid request (HTTP 4xx except 401/403/429) — fix payload, may retry once. */
    public static final BrokerErrorCategory VALIDATION_ERROR = new BrokerErrorCategory("VALIDATION_ERROR");

    /** Broker under scheduled maintenance — wait and retry. */
    public static final BrokerErrorCategory MAINTENANCE     = new BrokerErrorCategory("MAINTENANCE");

    /** Unclassified — log, escalate, retry once. */
    public static final BrokerErrorCategory UNKNOWN         = new BrokerErrorCategory("UNKNOWN");

    // ─── Retry decision helper ─────────────────────────────────────────────────

    /**
     * Returns {@code true} if operations in this category should be retried automatically.
     */
    public static boolean isRetryable(BrokerErrorCategory category) {
        return category == AUTH_EXPIRED
                || category == RATE_LIMITED
                || category == SERVICE_DOWN
                || category == NETWORK_TRANSIENT
                || category == MAINTENANCE
                || category == UNKNOWN;
    }

    /**
     * Returns {@code true} if this error category indicates the circuit should be opened.
     */
    public static boolean isCircuitBreaking(BrokerErrorCategory category) {
        return category == SERVICE_DOWN
                || category == NETWORK_PERMANENT
                || category == MAINTENANCE;
    }
}
