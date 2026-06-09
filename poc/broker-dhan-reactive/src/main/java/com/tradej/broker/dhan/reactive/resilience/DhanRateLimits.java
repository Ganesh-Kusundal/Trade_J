package com.tradej.broker.dhan.reactive.resilience;

import java.util.Map;

/**
 * Dhan-specific rate limit configuration and constants.
 * 
 * Ported from old broker's DhanProtocolConstants with proven production values.
 */
public final class DhanRateLimits {
    
    private DhanRateLimits() {
        // Utility class
    }
    
    // ---- Rate limits (tokens/second, capacity) ----
    
    /** Historical data, LTP - allows bursting up to 5 requests */
    public static final double DATA_RATE = 5.0;
    public static final int DATA_CAPACITY = 5;
    
    /** Quote endpoint - very restrictive (1 request every 2 seconds) */
    public static final double QUOTE_RATE = 0.5;
    public static final int QUOTE_CAPACITY = 1;
    
    /** Options/futures chain - restrictive (1 request every 3 seconds) */
    public static final double OPTION_CHAIN_RATE = 0.34;
    public static final int OPTION_CHAIN_CAPACITY = 1;
    
    /** Order operations - higher rate for trading */
    public static final double ORDER_RATE = 7.0;
    public static final int ORDER_CAPACITY = 10;
    
    /** Auth, portfolio, fund limits - high rate for non-trading ops */
    public static final double NON_TRADING_RATE = 15.0;
    public static final int NON_TRADING_CAPACITY = 20;
    
    // ---- WebSocket limits ----
    
    /** Maximum instruments per subscription batch */
    public static final int WS_MAX_INSTRUMENTS_PER_SUBSCRIPTION = 100;
    
    /** Maximum instruments per WebSocket connection */
    public static final int WS_MAX_INSTRUMENTS_PER_CONNECTION = 5000;
    
    /** Delay between subscription batches (milliseconds) */
    public static final long WS_BATCH_DELAY_MS = 50;
    
    // ---- Historical data limits ----
    
    /** Maximum days per intraday historical request */
    public static final int HISTORICAL_INTRADAY_MAX_DAYS = 90;
    
    /** Maximum days per rolling option/futures request */
    public static final int ROLLING_OPTION_MAX_DAYS = 30;
    
    /** Maximum days per daily historical request */
    public static final int HISTORICAL_DAILY_MAX_DAYS = 3650;
    
    // ---- Retry configuration ----
    
    /** Base delay for retry backoff (milliseconds) */
    public static final long RETRY_BASE_DELAY_MS = 1_000L;
    
    /** Maximum delay for retry backoff (milliseconds) */
    public static final long RETRY_MAX_DELAY_MS = 10_000L;
    
    /** Maximum retry attempts */
    public static final int MAX_RETRIES = 3;
    
    // ---- Factory methods ----
    
    /**
     * Create default rate limiter with Dhan API limits.
     */
    public static MultiBucketRateLimiter createDefault() {
        return new MultiBucketRateLimiter(Map.of(
            "DATA", new RateLimitConfig("DATA", DATA_RATE, DATA_CAPACITY),
            "QUOTE", new RateLimitConfig("QUOTE", QUOTE_RATE, QUOTE_CAPACITY),
            "OPTION_CHAIN", new RateLimitConfig("OPTION_CHAIN", OPTION_CHAIN_RATE, OPTION_CHAIN_CAPACITY),
            "ORDER", new RateLimitConfig("ORDER", ORDER_RATE, ORDER_CAPACITY),
            "NON_TRADING", new RateLimitConfig("NON_TRADING", NON_TRADING_RATE, NON_TRADING_CAPACITY)
        ));
    }
    
    /**
     * Create rate limiter for testing (unlimited).
     */
    public static MultiBucketRateLimiter createUnlimited() {
        return new MultiBucketRateLimiter(Map.of(
            "DATA", new RateLimitConfig("DATA", 1000.0, 1000),
            "QUOTE", new RateLimitConfig("QUOTE", 1000.0, 1000),
            "OPTION_CHAIN", new RateLimitConfig("OPTION_CHAIN", 1000.0, 1000),
            "ORDER", new RateLimitConfig("ORDER", 1000.0, 1000),
            "NON_TRADING", new RateLimitConfig("NON_TRADING", 1000.0, 1000)
        ));
    }
}
