# Analytics Catalog

Federated read-only views exposed by `DuckDbAnalyticsEngine` (`:data-analytics`).

## Configuration

```yaml
trade:
  analytics:
    equity-root: data/historical-equity
    options-warehouse: runtime-dev/historical.duckdb
    runtime-db-path: runtime-dev/trade.duckdb
    attach-runtime-db: false
    sql-enabled: true
    sql-max-rows: 10000
    sql-max-runtime-ms: 30000
```

Environment overrides: `TRADE_HISTORICAL_EQUITY_ROOT`, `TRADE_HISTORICAL_WAREHOUSE`, `TRADE_STORAGE_DUCKDB`, `TRADE_ANALYTICS_ATTACH_RUNTIME`, `TRADE_ANALYTICS_SQL_ENABLED`.

## Catalog views

| View | Source | Description |
|------|--------|-------------|
| `equity_bars_1m` | Parquet hive lake | Nifty 500 1m OHLCV; `interval` normalized to `1m` |
| `equity_universe` | Universe parquet or distinct symbols | Symbol metadata for scans |
| `rolling_option_bars` | Attached `historical.duckdb` | Dhan rolling option warehouse bars |

When `attach-runtime-db: true`, the runtime events DB is attached as `runtime_wh` (read-only). Cross-join manually in SQL, e.g. replay events with equity bars.

## REST API

| Method | Path | Purpose |
|--------|------|---------|
| GET | `/api/v1/analytics/catalog` | Dataset freshness and row counts |
| GET | `/api/v1/analytics/equity/candles` | Resampled equity candles |
| GET | `/api/v1/analytics/equity/universe` | Nifty 500 universe |
| GET | `/api/v1/analytics/options/bars` | Rolling option series |
| POST | `/api/v1/analytics/sql` | Guarded read-only SQL (`{"sql":"select ...","limit":1000}`) |

Admin ingest endpoints remain under `/admin/download/*` and `/admin/historical/equity/import-hive`. Deprecated read routes on `/admin/historical/*` delegate to the same repositories; prefer `/api/v1/analytics/*`.

## CLI

```bash
# Catalog snapshot
./gradlew :cli:run --args="analytics catalog"

# Typed queries
./gradlew :cli:run --args="analytics query-equity --symbol SBIN --from 2026-05-01 --to 2026-05-15"
./gradlew :cli:run --args="analytics query-options --underlying NIFTY --from-ms 1740000000000 --to-ms 1741000000000"

# Ad-hoc SQL (views above)
./gradlew :cli:run --args="analytics sql 'select count(*) from equity_bars_1m'"

# Download job registry
./gradlew :cli:run --args="download jobs list --source ROLLING_OPTION"

# Phase 0 hygiene
./gradlew :cli:run --args="equity compact-partitions --root data/historical-equity"
./gradlew :cli:run --args="universe refresh-from-disk --root data/historical-equity"
```

## IST semantics

- Equity bar times are epoch milliseconds in IST session context (see `HISTORICAL_BAR_CONTRACT.md`).
- Product APIs resample through `CandleResampler` + `CandleBucketPolicy` (09:15 IST anchor). Do not resample in ad-hoc SQL for product parity.

## Cross-asset example

```sql
SELECT e.symbol, e.bar_time_ms, e.close_paisa AS equity_close, o.close_paisa AS nifty_call
FROM equity_bars_1m e
JOIN rolling_option_bars o
  ON o.underlying = 'NIFTY'
 AND o.bar_time_ms = e.bar_time_ms
WHERE e.symbol = 'RELIANCE'
  AND o.option_type = 'CALL'
  AND o.strike_offset = 0
LIMIT 100
```

## MCP / external tools

Prefer `POST /api/v1/analytics/sql` over direct file paths so workspace root resolution stays centralized. Document queries against view names in this catalog, not raw parquet paths.
