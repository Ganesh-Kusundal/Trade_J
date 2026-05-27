package com.tradej.broker.dhan.constants;

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

    // ---- REST resilience constants (from DhanResilienceExecutor) ----

    /** Base delay (ms) for REST retry exponential backoff. */
    public static final long RETRY_BASE_DELAY_MS = 100L;

    /** Maximum delay (ms) for REST retry exponential backoff. */
    public static final long RETRY_MAX_DELAY_MS = 2_000L;

    /** Consecutive failures before REST circuit breaker opens. */
    public static final int RETRY_FAILURE_THRESHOLD = 3;

    /** Duration (ms) the REST circuit stays open. */
    public static final long RETRY_CIRCUIT_OPEN_MS = 10_000L;

    /** Retry count for ORDER-category operations. */
    public static final int RETRY_COUNT_ORDER = 2;

    /** Retry count for DATA-category operations. */
    public static final int RETRY_COUNT_DATA = 3;

    /** Retry count for QUOTE-category operations. */
    public static final int RETRY_COUNT_QUOTE = 3;

    /** Retry count for OPTION_CHAIN-category operations. */
    public static final int RETRY_COUNT_OPTION_CHAIN = 3;

    /** Retry count for NON_TRADING-category operations. */
    public static final int RETRY_COUNT_NON_TRADING = 3;

    // ---- Rate limit constants (from MultiBucketRateLimiter) ----

    /** Token-bucket fill rate (tokens/s) for ORDER-category operations. */
    public static final double RATE_LIMIT_ORDER_RATE = 7.0d;

    /** Token-bucket capacity for ORDER-category operations. */
    public static final int RATE_LIMIT_ORDER_CAPACITY = 10;

    /** Token-bucket fill rate (tokens/s) for DATA-category operations. */
    public static final double RATE_LIMIT_DATA_RATE = 2.0d;

    /** Token-bucket capacity for DATA-category operations. */
    public static final int RATE_LIMIT_DATA_CAPACITY = 1;

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
}
