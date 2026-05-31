# Historical Bar Contract

## Canonical storage

- Equity bars are stored at **1-minute** granularity only under `data/historical-equity/bars/interval=1m/`.
- Hive partitions: `symbol={SYMBOL}/part-hive-YYYY-MM.parquet`.

## Port semantics (`HistoricalBarRepository`)

| Method | Output | Notes |
|--------|--------|-------|
| `queryCandles(instrument, interval, from, to)` | OHLCV at **requested interval** | Reads 1m parquet, resamples via `CandleResampler` + `CandleBucketPolicy` |
| `queryIntradayBars(symbols, date)` | Raw **1m** bars for scan input | No resampling |
| `queryBenchmarkBars(date, symbol)` | Raw **1m** benchmark bars | Falls back across NIFTY aliases |
| `querySymbolsWithDataOn(date, limit)` | Symbols with bars on `date` | Uses hive partition for that calendar month |
| `latestAvailableTradingDay(lookbackDays)` | Latest IST trading date in warehouse | Fast path: latest hive month only |

## Bucketing (zero-parity rule)

- **Anchor:** NSE session open **09:15 IST** (`CandleBucketPolicy.SESSION_OPEN`).
- **Intraday intervals** (`1m`, `3m`, `5m`, `15m`, `30m`, `1h`, `4h`): bucket start = sessionOpen + n × interval.
- **Daily (`1d`):** one bucket per IST session date (open 09:15 → close 15:30).
- **Sub-second (`1s`):** epoch-aligned seconds (live ticks via `CandleAggregationService`). ICICI Breeze `historicalcharts` v1 supports only `minute`, `5minute`, `30minute`, and `day`; sub-second historical is not available via broker REST.
- Historical (`CandleResampler`) and live (`CandleAggregationService`) **must** use the same `CandleBucketPolicy`.

## Scan cutoff

- Institutional scan default cutoff: **09:45 IST**.
- Selection order: exact bar → latest bar at/before cutoff → first bar after cutoff.
- Provenance records `requestedScanTime`, `fallbackReason`, and effective `scanTime`.

## Failure modes

| Condition | Behavior |
|-----------|----------|
| No warehouse at configured root | `IllegalStateException` on query |
| Range spans months without partition files | Falls back to full `equity_bars_1m` view |
| Empty symbol set on scan date | `IllegalStateException: No symbols with parquet data on {date}` |
| Unknown interval string | Parsed as `1m` via `CandleIntervalSpec` default |

## Workspace paths

- All relative paths resolve from workspace root (`trade.workspace.root` or process CWD).
- Gradle `:app:bootRun` sets `workingDir = rootProject.projectDir`.

## Federated analytics

Equity parquet and rolling options DuckDB are federated by `DuckDbAnalyticsEngine` (`:data-analytics`). Product code should use:

- `HistoricalBarRepository` / `HistoricalAnalyticsService.queryEquityCandles` for equity
- `RollingOptionHistoricalRepository` / `HistoricalAnalyticsService.queryOptionBars` for options

Catalog views, REST endpoints, and SQL guard rules: see [ANALYTICS_CATALOG.md](ANALYTICS_CATALOG.md).

Direct imports of `EquityHistoricalQuery` or `HistoricalWarehouseQuery` from application code are deprecated — use the ports above.
