package com.tradej.broker.core.reconnect;

/**
 * Shared default reconnect configuration for all broker implementations.
 *
 * <p>Broker WebSocket multiplexers and depth feed clients should reference
 * these constants instead of defining their own local values. This ensures
 * consistent reconnect behavior across brokers and allows centralised tuning.
 */
public final class ReconnectDefaults {

    private ReconnectDefaults() {
    }

    /** Default maximum number of consecutive reconnect attempts before giving up. */
    public static final int DEFAULT_MAX_ATTEMPTS = 8;

    /** Base delay (ms) for exponential backoff — first retry waits this long. */
    public static final long DEFAULT_BASE_DELAY_MS = 1_000L;

    /** Maximum delay (ms) for exponential backoff — cap at 30 seconds. */
    public static final long DEFAULT_MAX_DELAY_MS = 30_000L;

    /** Storm cooldown (ms) between reconnect cycles after all attempts exhausted. */
    public static final long DEFAULT_STORM_COOLDOWN_MS = 60_000L;
}
