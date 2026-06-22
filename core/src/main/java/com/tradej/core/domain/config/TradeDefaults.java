package com.tradej.core.domain.config;

import com.tradej.core.domain.runtime.RuntimeMode;

/**
 * Shared platform defaults that are part of Trade-J's runtime contract.
 */
public final class TradeDefaults {

    public static final RuntimeMode RUNTIME_MODE = RuntimeMode.LIVE;
    public static final int HOT_PATH_SHARD_COUNT_AUTO = 0;
    public static final int EVENT_DOWNSTREAM_QUEUE_CAPACITY = 4096;
    public static final int EVENT_DISPATCH_QUEUE_CAPACITY = 4096;
    public static final int UNIVERSE_MAX_SUBSCRIPTIONS_PER_BATCH_AUTO = 0;
    public static final String CANDLE_INTERVAL_1S = "1s";
    public static final String CANDLE_INTERVAL_5M = "5m";
    public static final long DOWNLOAD_DELAY_MS = 0L;
    public static final int DOWNLOAD_WORKERS = 2;

    public static final String HISTORICAL_EQUITY_ROOT = "data/historical-equity";
    public static final String HISTORICAL_WAREHOUSE_PATH = "runtime-dev/historical.duckdb";
    public static final String RUNTIME_ANALYTICS_DB_PATH = "runtime-dev/trade.duckdb";
    public static final String HISTORICAL_UNIVERSE_URL =
            "https://www.niftyindices.com/IndexConstituent/ind_nifty500list.csv";
    public static final String HIVE_CACHE_IMPORT_FROM_MONTH = "2020-01";

    public static final String ANALYTICS_ENGINE_DUCKDB = "duckdb";
    public static final int ANALYTICS_SQL_MAX_ROWS = 10_000;
    public static final long ANALYTICS_SQL_MAX_RUNTIME_MS = 30_000L;

    public static final String SYNC_CRON_AFTER_MARKET_CLOSE = "0 0 16 * * MON-FRI";
    public static final int SYNC_LOOKBACK_MONTHS = 3;
    public static final String SYNC_SEGMENT = "NSE_EQ";
    public static final int SYNC_BATCH_SIZE = 50;
    public static final long SYNC_DELAY_MS = 500L;

    private TradeDefaults() {
    }
}
