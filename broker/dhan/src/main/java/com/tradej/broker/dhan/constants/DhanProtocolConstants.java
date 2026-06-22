package com.tradej.broker.dhan.constants;

import com.tradej.broker.core.rate.MultiBucketRateLimiter;
import com.tradej.broker.core.rate.RateLimitConfig;

import java.util.Map;
import java.util.Set;

/**
 * Centralised Dhan protocol-level constants.
 *
 * <p>Consolidates hardcoded magic numbers, threshold values, interval mappings,
 * commodity identifiers, and strike-step tables that were previously scattered
 * across adapters and infrastructure classes.
 */
public final class DhanProtocolConstants {
    private DhanProtocolConstants() {
    }

    // ---- WebSocket constants (from DhanWebSocketMultiplexer) ----

    /** Dhan websocket disconnect code indicating an invalid/expired token. */
    public static final int INVALID_TOKEN_CODE = 806;

    /** Maximum idle time (ms) before a market feed is considered stale. */
    public static final long STALE_FEED_THRESHOLD_MS = 30_000L;

    /** Interval (ms) between periodic token state checks. */
    public static final long TOKEN_CHECK_INTERVAL_MS = 60_000L;

    /** Interval (ms) between feed liveness checks in the health monitor. */
    public static final long FEED_HEALTH_CHECK_INTERVAL_MS = 5_000L;

    /** Base delay (ms) for WebSocket reconnection backoff. */
    public static final long WS_RECONNECT_BASE_DELAY_MS = 1_000L;

    /** Maximum delay (ms) for WebSocket reconnection backoff. */
    public static final long WS_RECONNECT_MAX_DELAY_MS = 30_000L;

    /** Consecutive reconnection failures before the circuit opens. */
    public static final int WS_RECONNECT_FAILURE_THRESHOLD = 3;

    /** Duration (ms) the circuit stays open before allowing another reconnect. */
    public static final long WS_RECONNECT_CIRCUIT_OPEN_MS = 30_000L;

    // ---- Historical data constants (from DhanHistoricalDataClient) ----

    /** Time format used for intraday historical request time boundaries. */
    public static final String HISTORICAL_TIME_FORMAT = "HH:mm:ss";

    /** Mapping from canonical interval strings to Dhan SDK interval codes. */
    public static final Map<String, String> INTERVAL_TO_DHAN_CODE = Map.of(
            "1m", "1",
            "5m", "5",
            "15m", "15",
            "25m", "25",
            "60m", "60",
            "1h", "60"
    );

    /** Set of daily-interval strings for historical data. */
    public static final Set<String> DAILY_INTERVALS = Set.of("1d", "d", "day");

    /**
     * Maximum per-call date span for intraday historical requests.
     * Long requests are split into multiple windows and merged.
     */
    public static final int HISTORICAL_INTRADAY_MAX_DAYS = 90;

    /** Maximum per-call date span for expired rolling option requests. */
    public static final int ROLLING_OPTION_MAX_DAYS = 30;

    /**
     * Maximum per-call date span for daily historical requests.
     * Allows large backfills while still bounding request payload size.
     */
    public static final int HISTORICAL_DAILY_MAX_DAYS = 3650;

    // ---- Futures commodity symbols (from DhanFuturesAdapter) ----

    /** Well-known commodity underlyings traded on MCX. */
    public static final Set<String> COMMON_COMMODITIES = Set.of(
            "GOLD", "SILVER", "CRUDEOIL", "NATURALGAS", "COPPER", "ZINC",
            "LEAD", "NICKEL", "ALUMINIUM", "CRUDEOILM", "GOLDM", "SILVERM"
    );

    // ---- Option chain constants (from DhanOptionsAdapter) ----

    /** Default strike price step (in paisa) per underlying for ATM selection. */
    public static final Map<String, Long> DEFAULT_STRIKE_STEPS_PAISA = Map.of(
            "NIFTY", 5_000L,
            "BANKNIFTY", 10_000L,
            "FINNIFTY", 5_000L,
            "MIDCPNIFTY", 2_500L,
            "SENSEX", 10_000L
    );

    /** Fallback strike step (in paisa) when the underlying has no specific mapping. */
    public static final long DEFAULT_STRIKE_STEP_PAISA = 5_000L;

    // ---- REST resilience constants (from DhanRetryExecutor) ----

    /** Base delay (ms) for REST retry exponential backoff. */
    public static final long RETRY_BASE_DELAY_MS = 100L;

    /** Maximum delay (ms) for REST retry exponential backoff. */
    public static final long RETRY_MAX_DELAY_MS = 2_000L;

    /** Consecutive failures before REST circuit breaker opens. */
    public static final int RETRY_FAILURE_THRESHOLD = 3;

    /** Duration (ms) the REST circuit stays open. */
    public static final long RETRY_CIRCUIT_OPEN_MS = 10_000L;

    // ---- Market feed WebSocket request/response codes ----

    public static final int FEED_DISCONNECT_REQUEST = 12;
    public static final int FEED_SUBSCRIBE_TICKER = 15;
    public static final int FEED_UNSUBSCRIBE_TICKER = 16;
    public static final int FEED_SUBSCRIBE_QUOTE = 17;
    public static final int FEED_UNSUBSCRIBE_QUOTE = 18;
    public static final int FEED_SUBSCRIBE_FULL = 21;
    public static final int FEED_UNSUBSCRIBE_FULL = 22;

    public static final int FEED_RESPONSE_INDEX = 1;
    public static final int FEED_RESPONSE_TICKER = 2;
    public static final int FEED_RESPONSE_QUOTE = 4;
    public static final int FEED_RESPONSE_OI = 5;
    public static final int FEED_RESPONSE_PREV_CLOSE = 6;
    public static final int FEED_RESPONSE_MARKET_STATUS = 7;
    public static final int FEED_RESPONSE_FULL = 8;
    public static final int FEED_RESPONSE_DISCONNECT = 50;
    public static final int FEED_RESPONSE_HEARTBEAT = 100;

    public static final int ORDER_UPDATE_MSG_CODE = 42;
    public static final int FEED_MAX_INSTRUMENTS_PER_SUBSCRIPTION = 100;
    public static final int FEED_MAX_INSTRUMENTS_PER_CONNECTION = 5000;

    /** Default retry count for most Dhan REST categories. */
    public static final int RETRY_COUNT_DEFAULT = 3;

    /** Retry count for ORDER-category operations (lower than default to fail fast). */
    public static final int RETRY_COUNT_ORDER = 2;

    /** Retry count for AUTH token minting. Dhan auth rejects must not be retried in a burst. */
    public static final int RETRY_COUNT_AUTH = 1;

    // ---- Rate limit constants (from MultiBucketRateLimiter) ----

    /** Token-bucket fill rate (tokens/s) for ORDER-category operations. */
    public static final double RATE_LIMIT_ORDER_RATE = 7.0d;

    /** Dhan allows token generation only at a very low frequency. */
    public static final long TOKEN_ACQUISITION_COOLDOWN_MS = 130_000L;

    /** Token-bucket fill rate (tokens/s) for AUTH token minting. */
    public static final double RATE_LIMIT_AUTH_RATE = 1.0d / (TOKEN_ACQUISITION_COOLDOWN_MS / 1_000.0d);

    /** Token-bucket capacity for AUTH token minting. */
    public static final int RATE_LIMIT_AUTH_CAPACITY = 1;

    /** Token-bucket capacity for ORDER-category operations. */
    public static final int RATE_LIMIT_ORDER_CAPACITY = 10;

    /** Token-bucket fill rate (tokens/s) for DATA-category operations. */
    public static final double RATE_LIMIT_DATA_RATE = 5.0d;

    /** Token-bucket capacity for DATA-category operations. Allows bursting up to 5. */
    public static final int RATE_LIMIT_DATA_CAPACITY = 5;

    /** Token-bucket fill rate (tokens/s) for QUOTE-category operations. */
    public static final double RATE_LIMIT_QUOTE_RATE = 0.5d;

    /** Token-bucket capacity for QUOTE-category operations. */
    public static final int RATE_LIMIT_QUOTE_CAPACITY = 1;

    /** Token-bucket fill rate (tokens/s) for OPTION_CHAIN-category operations. */
    public static final double RATE_LIMIT_OPTION_CHAIN_RATE = 0.34d;

    /** Token-bucket capacity for OPTION_CHAIN-category operations. */
    public static final int RATE_LIMIT_OPTION_CHAIN_CAPACITY = 1;

    /** Token-bucket fill rate (tokens/s) for NON_TRADING-category operations. */
    public static final double RATE_LIMIT_NON_TRADING_RATE = 15.0d;

    /** Token-bucket capacity for NON_TRADING-category operations. */
    public static final int RATE_LIMIT_NON_TRADING_CAPACITY = 20;

    // ---- Safety validation constants ----

    /** Maximum notional value (in paisa) allowed without warning. Default: ₹50,000 = 5,000,000 paisa. */
    public static final long MAX_NOTIONAL_PAISA = 5_000_000L;

    /**
     * Creates a {@link MultiBucketRateLimiter} configured with the Dhan-specific
     * rate limits defined above. Used by both Spring DI and the legacy
     * {@link com.tradej.broker.dhan.DhanBrokerConnection} factory.
     */
    public static MultiBucketRateLimiter defaultRateLimiter() {
        return new MultiBucketRateLimiter(Map.of(
                "AUTH", new RateLimitConfig("AUTH", RATE_LIMIT_AUTH_RATE, RATE_LIMIT_AUTH_CAPACITY),
                "ORDER", new RateLimitConfig("ORDER", RATE_LIMIT_ORDER_RATE, RATE_LIMIT_ORDER_CAPACITY),
                "DATA", new RateLimitConfig("DATA", RATE_LIMIT_DATA_RATE, RATE_LIMIT_DATA_CAPACITY),
                "QUOTE", new RateLimitConfig("QUOTE", RATE_LIMIT_QUOTE_RATE, RATE_LIMIT_QUOTE_CAPACITY),
                "OPTION_CHAIN", new RateLimitConfig("OPTION_CHAIN", RATE_LIMIT_OPTION_CHAIN_RATE, RATE_LIMIT_OPTION_CHAIN_CAPACITY),
                "NON_TRADING", new RateLimitConfig("NON_TRADING", RATE_LIMIT_NON_TRADING_RATE, RATE_LIMIT_NON_TRADING_CAPACITY)
        ));
    }
}
