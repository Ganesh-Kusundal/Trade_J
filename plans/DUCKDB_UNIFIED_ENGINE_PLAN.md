# DuckDB Unified Engine & Sync Architecture Plan

**Author:** Principal Engineer  
**Date:** 2026-06-06  
**Scope:** Consolidate 3 fragmented DuckDB databases → single unified engine with sync, dedup, loss protection  
**Estimated effort:** ~30 developer-days across 4 phases

---

## Executive Summary

Trade-J currently has **3 separate DuckDB databases** with no coordination between them:

| # | Database | Path | Purpose |
|---|----------|------|---------|
| 1 | Runtime | `runtime/tradej.duckdb` | Event store, OMS, scans, pipeline graphs |
| 2 | Analytics | `runtime-dev/trade.duckdb` | Analytics engine (in-memory, queries parquet directly) |
| 3 | Historical Warehouse | `runtime-dev/historical.duckdb` | Rolling option bars, download job tracking |

Additionally, there is a **parquet warehouse** (`data/historical-equity/`) with hive-partitioned equity bars that the analytics engine queries via `read_parquet()` — but there is no sync mechanism between the live event store and historical parquet, no deduplication protection for re-downloads, and no way to add new asset classes without code changes.

This plan proposes a **unified DuckDB engine** with:
1. **Single database file** with schema namespacing (eliminates fragmentation)
2. **Sync layer** between live events, DuckDB, and Parquet (auto + manual)
3. **Idempotent writes** with watermark-based dedup (prevents data loss on restart/replay)
4. **Asset registry** for adding new asset classes without code changes
5. **CLI commands** for sync operations and health monitoring

---

## Part 1: Current State Analysis

### 1.1 DuckDB Database Inventory

```
runtime/tradej.duckdb           ← DuckDbEventStore (candles, orders, fills, fill_events, trade_lifecycle)
                                  DuckDbScanStore (scan_results)
                                  DuckDbPipelineGraphStore (pipeline_graphs)
                                  DuckDbFeatureStore (feature_ticks, feature_candles)

runtime-dev/historical.duckdb   ← DuckDbHistoricalWarehouse (rolling_option_bars, download_jobs, download_tasks)

runtime-dev/trade.duckdb        ← DuckDbAnalyticsEngine (in-memory, no persistent file)
                                  Creates views over parquet files via read_parquet()
```

### 1.2 Data Flow Gaps

```
Live Feed ──→ EventBus ──┬──→ AsyncDuckDbWriter ──→ DuckDbFeatureStore (ticks+candles)
                          ├──→ AsyncDuckDbEventStore ──→ DuckDbEventStore (orders+fills)
                          ├──→ ChronicleAuditLogWriter ──→ Chronicle Queue
                          └──→ GatewayEventBridge ──→ WebSocket clients

Historical Download ──→ ParquetBarWriter ──→ Parquet files (no sync to DuckDB)
                                          
Analytics Engine ──→ read_parquet() ──→ queries parquet directly (no caching/materialization)
```

**Key problems:**
1. Two separate DuckDB files with no cross-querying
2. No sync from live DuckDB → parquet (historical gaps)
3. No sync from parquet → DuckDB analytics (queries are cold every restart)
4. No dedup protection for re-downloads (relies on file-exists checks)
5. No watermark tracking for incremental sync
6. No way to add new asset classes (e.g., commodities, currencies) without code changes

### 1.3 Existing Components to Preserve

| Component | Module | Keep/Refactor | Notes |
|-----------|--------|:---:|-------|
| `DuckDbEventStore` | `data-persistence` | **Refactor** | Merge into unified engine, keep table schemas |
| `AsyncDuckDbEventStore` | `data-persistence` | **Keep** | Async wrapper pattern is correct |
| `DuckDbFeatureStore` | `data-feature-store` | **Refactor** | Merge into unified engine |
| `AsyncDuckDbWriter` | `data-feature-store` | **Keep** | Async wrapper pattern is correct |
| `DuckDbScanStore` | `data-persistence` | **Refactor** | Merge into unified engine |
| `DuckDbPipelineGraphStore` | `data-persistence` | **Refactor** | Merge into unified engine |
| `DuckDbHistoricalWarehouse` | `data-historical-ingest` | **Refactor** | Merge option bars into unified, keep job tracking |
| `DuckDbAnalyticsEngine` | `data-analytics` | **Refactor** | Use unified DB with attached parquet views |
| `ParquetBarWriter` | `data-historical-ingest` | **Keep** | Parquet write is correct |
| `EquityParquetCompactor` | `data-historical-ingest` | **Keep** | Compaction logic is correct |
| `ParquetHistoricalBarRepository` | `data-historical-ingest` | **Keep** | Parquet read is correct |
| `FederatedHistoricalBarRepository` | `data-analytics` | **Refactor** | Point at unified engine |

---

## Part 2: Unified DuckDB Engine Architecture

### 2.1 Target Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    Unified DuckDB Engine                      │
│                    (single file: tradej.duckdb)               │
├─────────────────────────────────────────────────────────────┤
│                                                               │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐       │
│  │ runtime.*    │  │ features.*   │  │ analytics.*  │       │
│  │ (live events)│  │ (tick/candle)│  │ (views)      │       │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘       │
│         │                  │                  │               │
│  ┌──────┴──────────────────┴──────────────────┴───────┐     │
│  │              SyncCoordinator                        │     │
│  │  ┌─────────────┐  ┌──────────────┐  ┌──────────┐  │     │
│  │  │ Watermark   │  │ Idempotency  │  │ Parquet  │  │     │
│  │  │ Tracker     │  │ Guard        │  │ Bridge   │  │     │
│  │  └─────────────┘  └──────────────┘  └──────────┘  │     │
│  └────────────────────────────────────────────────────┘     │
│                                                               │
│  ┌──────────────────────────────────────────────────────┐   │
│  │              Asset Registry                           │   │
│  │  equity · options · commodities · currencies · ...    │   │
│  └──────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
         │                    │                    │
         ▼                    ▼                    ▼
   Parquet Warehouse    EventBus Subscribers   REST/CLI APIs
   (hive-partitioned)   (auto-sync triggers)   (manual sync)
```

### 2.2 Schema Namespacing

**Note:** DuckDB schemas are naming conventions, not true isolation like PostgreSQL. We use table prefixes for clarity and simplicity, matching the existing pattern in `DuckDbAnalyticsEngine` (which uses `ATTACH ... (read_only)` for true isolation of external databases).

```sql
-- Unified tradej.duckdb table layout:

-- === Runtime tables (live events) ===
CREATE TABLE rt_candles (...);
CREATE TABLE rt_orders (...);
CREATE TABLE rt_fills (...);
CREATE TABLE rt_fill_events (...);
CREATE TABLE rt_trade_lifecycle (...);
CREATE TABLE rt_scan_results (...);
CREATE TABLE rt_pipeline_graphs (...);

-- === Feature tables (time-series features) ===
CREATE TABLE feat_ticks (...);
CREATE TABLE feat_candles (...);

-- === Warehouse tables (historical data) ===
CREATE TABLE wh_rolling_option_bars (...);
CREATE TABLE wh_download_jobs (...);
CREATE TABLE wh_download_tasks (...);

-- === Sync metadata (watermark-based, NOT per-event) ===
CREATE TABLE sync_watermarks (
    asset_class VARCHAR,
    symbol VARCHAR,
    interval VARCHAR,           -- '1m', '5m', '15m', '1d' — per-interval watermark
    last_synced_event_time_ms BIGINT,
    last_synced_ingest_time_ms BIGINT,
    row_count BIGINT,
    updated_at_ms BIGINT,
    PRIMARY KEY (asset_class, symbol, interval)
);

CREATE TABLE sync_parquet_manifest (
    asset_class VARCHAR,
    symbol VARCHAR,
    partition_file VARCHAR,
    row_count BIGINT,
    checksum VARCHAR,
    synced_at_ms BIGINT,
    PRIMARY KEY (asset_class, symbol, partition_file)
);
```

**Key design decisions:**
- **No `sync.event_log` table** — storing every tick UUID at 1000+ events/sec is too expensive. Hot-path dedup stays in-memory (existing `DisruptorEventBus.seenEvents` pattern). Persistence-level dedup uses watermarks + idempotent UPSERTs only.
- **Watermarks include interval** — a 1m candle sync doesn't imply 5m is synced. PK is `(asset_class, symbol, interval)`.
- **Asset config in YAML, not a table** — no over-engineered registry table. New assets = new YAML entries.

### 2.3 Module Structure

**Decision: Package within `data/persistence`, NOT a new module.** The existing `data/persistence` module already owns DuckDB stores. Creating a third module adds unnecessary dependency complexity across 24 Gradle modules. `data/feature-store` depends on `data/persistence` for the unified engine.

```
data/persistence/
  src/main/java/com/tradej/persistence/engine/     ← NEW package
    UnifiedDuckDbEngine.java         ← Single connection manager, schema bootstrap
    SchemaBootstrapper.java          ← Creates all tables with prefixes
    SyncCoordinator.java             ← Orchestrates sync between live/parquet/warehouse
    WatermarkTracker.java            ← Per-symbol+interval sync watermarks
    IdempotencyGuard.java            ← Watermark-based write dedup (NOT per-event)
    ParquetBridge.java               ← DuckDB ↔ Parquet export/import (read-only snapshot)
    SyncMode.java                    ← AUTO, MANUAL, DISABLED enum
    SyncResult.java                  ← Sync operation result record
    SyncSchedule.java                ← Timer-based sync scheduling
  
  src/test/java/com/tradej/persistence/engine/
    UnifiedDuckDbEngineTest.java
    SyncCoordinatorTest.java
    WatermarkTrackerTest.java
    IdempotencyGuardTest.java
    ParquetBridgeTest.java
```

---

## Part 3: Sync Mechanism Design

### 3.1 Sync Modes

| Mode | Trigger | Use Case |
|------|---------|----------|
| **AUTO** | EventBus subscription + scheduled timer | Production live trading |
| **MANUAL** | CLI command or REST endpoint | Data backfill, repair |
| **DISABLED** | No sync operations | Testing, isolation |

### 3.2 Sync Flow: Live → DuckDB → Parquet

```
                    ┌─────────────────────────────────┐
                    │         EventBus                  │
                    │   (MarketTick, CandleClosed,     │
                    │    OrderFilled, TradeOpened, ...) │
                    └──────────────┬──────────────────┘
                                   │
                    ┌──────────────▼──────────────────┐
                    │    SyncCoordinator.onEvent()     │
                    │  1. Write to unified DuckDB       │
                    │     (UPSERT with ON CONFLICT)     │
                    │  2. Update sync_watermarks        │
                    └──────────────┬──────────────────┘
                                   │
                    ┌──────────────▼──────────────────┐
                    │    ParquetBridge (scheduled)      │
                    │  1. Read sync_watermarks           │
                    │  2. Open READ_ONLY snapshot via    │
                    │     ATTACH ... (READ_ONLY)        │
                    │  3. Export changed symbols to     │
                    │     parquet (COPY ... FORMAT)     │
                    │  4. Update sync_parquet_manifest  │
                    │  5. Compact partitions            │
                    │  6. Retry with backoff on failure │
                    └─────────────────────────────────┘
```

**Concurrent safety:** Parquet export uses a separate `ATTACH ... (READ_ONLY)` connection (same pattern as `DuckDbAnalyticsEngine.attachOptionsWarehouse()`). This avoids blocking live writes. DuckDB serializes writes, so the export query reads from a consistent snapshot without locking the write path.

### 3.3 Sync Flow: Parquet → DuckDB (Analytics)

```
    Parquet files (data/historical-equity/)
           │
    ┌──────▼──────────────────────────┐
    │  DuckDbAnalyticsEngine          │
    │  (already does this via         │
    │   read_parquet() views)         │
    │                                 │
    │  Enhancement:                   │
    │  - Cache view metadata in       │
    │    sync.parquet_manifest        │
    │  - Incremental view refresh     │
    │    on parquet manifest change   │
    └─────────────────────────────────┘
```

### 3.4 Deduplication & Loss Protection

**Two-level dedup design:**

#### Level 1: Hot-Path In-Memory Dedup (unchanged)
- `DisruptorEventBus.seenEvents` — ConcurrentHashMap with TTL eviction (existing, don't change)
- `GatewayEventBridge.seenEventIds` — ConcurrentHashMap with periodic pruning (existing, don't change)
- These catch broker retransmissions and duplicate EventBus publishes

#### Level 2: Persistence-Level Watermark Dedup

Only tracks the **last synced event time per symbol+interval**, not every event:

```java
public class IdempotencyGuard {
    /**
     * Returns true if this event's time is at or before the last synced watermark.
     * This catches replay-of-already-synced-data without storing every event ID.
     */
    public boolean isAlreadySynced(String assetClass, String symbol, String interval,
                                    long eventTimeMs) {
        long watermark = watermarkTracker.getWatermark(assetClass, symbol, interval);
        return eventTimeMs <= watermark;
    }
    
    /**
     * Called after successful write batch to advance the watermark.
     * Uses MAX(event_time_ms) from the batch, not individual events.
     */
    public void advanceWatermark(String assetClass, String symbol, String interval,
                                  long maxEventTimeMs, long rowCount) {
        // UPSERT into sync_watermarks (asset_class, symbol, interval)
    }
}
```

#### Crash Recovery

On startup, the engine:
1. Reads the last watermark per symbol+interval from `sync_watermarks`
2. For live mode: resumes from watermark timestamp (no data loss)
3. For replay mode: replays from watermark timestamp
4. Logs any gaps detected between watermark and current time
5. Verifies row counts match between watermark and actual table

#### Write-Ahead Pattern

```
1. BEGIN TRANSACTION
2. INSERT INTO rt_candles / feat_ticks / etc. (actual data, UPSERT with ON CONFLICT)
3. UPDATE sync_watermarks (advance watermark to MAX event_time_ms)
4. COMMIT
```

If any step fails, the entire transaction rolls back — no partial writes. **No event_log table** — watermarks + UPSERTs provide sufficient idempotency.

### 3.5 Parquet Sync (Export)

```java
public class ParquetBridge {
    /**
     * Export live DuckDB data to parquet for long-term storage.
     * Uses DuckDB COPY command with hive partitioning.
     */
    public SyncResult exportToParquet(String assetClass, List<String> symbols, 
                                       LocalDate fromDate, LocalDate toDate) {
        // 1. Read watermarks for each symbol
        // 2. Query DuckDB for rows since last export watermark
        // 3. COPY to parquet with hive partitioning:
        //    COPY (SELECT ... FROM runtime.candles 
        //          WHERE symbol = ? AND bar_time_ms >= ?
        //          ORDER BY bar_time_ms)
        //    TO 'data/historical-equity/bars/interval=1m/symbol=X/part-export-YYYY-MM.parquet'
        //    (FORMAT PARQUET)
        // 4. Update sync.parquet_manifest
        // 5. Run EquityParquetCompactor to merge into hive partitions
    }
    
    /**
     * Import parquet data into DuckDB for analytics.
     * Creates read-only views over parquet files.
     */
    public void refreshAnalyticsViews() {
        // Re-read parquet manifest
        // Recreate read_parquet() views in analytics schema
    }
}
```

---

## Part 4: Asset Registry

### 4.1 Purpose

Allow adding new asset classes (commodities, currencies, crypto) by adding YAML configuration entries, **not** by modifying Java code. No registry table needed — asset schemas are defined in `TradingProperties` and auto-created on startup.

### 4.2 Asset Configuration (YAML)

```yaml
trade:
  engine:
    assets:
      equity:
        display-name: "Equity"
        exchange-segments: [NSE_EQ, BSE_EQ]
        intervals: [1m, 5m, 15m, 1d]
        parquet-root: data/historical-equity
        enabled: true
      options:
        display-name: "Options (F&O)"
        exchange-segments: [NSE_FNO]
        intervals: [5m, 15m]
        warehouse-path: runtime-dev/historical.duckdb
        enabled: true
      commodity:
        display-name: "Commodity"
        exchange-segments: [MCX]
        intervals: [1m, 5m, 15m]
        enabled: false  # Enable when ready
```

### 4.3 Adding a New Asset Class

Add a YAML entry under `trade.engine.assets` and restart. The `SchemaBootstrapper` auto-creates the DuckDB table and `SyncCoordinator` auto-registers watermarks.

**No CLI command, no REST endpoint, no registry table needed** for this phase. If dynamic registration (without restart) is needed later, it can be added as a follow-up.

---

## Part 5: Implementation Phases

### Phase 1: Unified Engine Core (12 days)

**Goal:** Single DuckDB file, table prefixes, preserve all existing functionality

| # | Task | Effort | Files Affected |
|---|------|:---:|----------------|
| 1.1 | Create `UnifiedDuckDbEngine` in `data/persistence/engine/` | 2d | New package |
| 1.2 | Create `SchemaBootstrapper` — all tables with `rt_`/`feat_`/`wh_` prefixes | 1d | New file |
| 1.3 | Create `WatermarkTracker` — per-symbol+interval watermarks | 1d | New file |
| 1.4 | Create `IdempotencyGuard` — watermark-based write dedup | 1d | New file |
| 1.5 | Refactor `DuckDbEventStore` → delegate to unified engine | 1d | `data/persistence` |
| 1.6 | Refactor `DuckDbFeatureStore` → delegate to unified engine | 1d | `data/feature-store` |
| 1.7 | Refactor `DuckDbScanStore` → delegate to unified engine | 0.5d | `data/persistence` |
| 1.8 | Refactor `DuckDbPipelineGraphStore` → delegate to unified engine | 0.5d | `data/persistence` |
| 1.9 | Refactor `DuckDbHistoricalWarehouse` → delegate to unified engine | 1d | `data/historical-ingest` |
| 1.10 | Update `StorageProfile` and `TradingProperties` to single DB path | 0.5d | `composition`, `app` |
| 1.11 | Configure DuckDB WAL settings (`wal_autocheckpoint=1000`) | 0.5d | `UnifiedDuckDbEngine` |
| 1.12 | Write unit tests for all new components | 3d | New test files |

**Validation:**
- All existing `DuckDbEventStoreTest`, `DuckDbFeatureStoreTest`, `DuckDbScanStoreTest` pass
- Schema migration test: old databases migrate to new unified format
- `./gradlew :data-persistence:test :data-feature-store:test`

### Phase 2: Sync Mechanism (10 days)

**Goal:** Auto-sync live → DuckDB → Parquet, manual sync, crash recovery, retry/backoff

| # | Task | Effort | Files Affected |
|---|------|:---:|----------------|
| 2.1 | Create `SyncCoordinator` — orchestrates sync operations | 2d | New file |
| 2.2 | Create `ParquetBridge` — DuckDB ↔ Parquet using READ_ONLY snapshot | 2d | New file |
| 2.3 | Wire `SyncCoordinator` into `BrokerStartupOrchestrator` | 1d | `app` |
| 2.4 | Add scheduled parquet export (configurable interval) | 1d | `app` |
| 2.5 | Add retry/backoff for failed sync operations (3 retries, exponential) | 1d | `SyncCoordinator` |
| 2.6 | Add CLI commands: `tradej engine sync`, `tradej engine status` | 1d | `cli` |
| 2.7 | Add REST endpoints: `POST /api/v1/engine/sync`, `GET /api/v1/engine/status` | 1d | `app` |
| 2.8 | Write integration tests for sync flows | 2d | New test files |

**Validation:**
- Auto-sync: live ticks → DuckDB → parquet within configurable interval
- Manual sync: CLI command triggers immediate export
- Crash recovery: restart after partial write, watermark ensures no data loss
- Retry: failed export retries 3x with backoff, alerts after 3 consecutive failures
- Concurrent safety: live writes not blocked during parquet export
- `./gradlew :data-persistence:test :app:integrationTest`

### Phase 3: Asset Configuration (3 days)

**Goal:** YAML-driven asset schemas, new assets without code changes

| # | Task | Effort | Files Affected |
|---|------|:---:|----------------|
| 3.1 | Add `EngineAssetProperties` to `TradingProperties` | 0.5d | `app` |
| 3.2 | Update `SchemaBootstrapper` to create tables from YAML config | 1d | `data/persistence` |
| 3.3 | Seed with equity + options defaults (matching current schemas) | 0.5d | Config |
| 3.4 | Document how to add new asset class (YAML example in README) | 0.5d | Docs |
| 3.5 | Write tests: asset config → table creation → sync registration | 0.5d | New test files |

**Validation:**
- Add `commodity` YAML entry → restart → table exists, sync watermarks created
- Remove YAML entry → restart → table dropped
- `./gradlew :data-persistence:test`

### Phase 4: Migration & Cleanup (10 days)

**Goal:** Migrate existing data with rollback safety, clarify Chronicle scope, full regression

| # | Task | Effort | Files Affected |
|---|------|:---:|----------------|
| 4.1 | Create migration script with backup + transaction + row count verification | 3d | New script |
| 4.2 | Migrate `DuckDbAnalyticsEngine` to use unified DB (keep `read_parquet()` views) | 1d | `data/analytics` |
| 4.3 | Remove old standalone DB files (backup first) | 0.5d | File system |
| 4.4 | Update all configuration files (YAML, properties) | 0.5d | `app` |
| 4.5 | Document: Chronicle Queue stays for audit log + DLQ (not replaced by DuckDB) | 0.5d | Docs |
| 4.6 | Update ARCHITECTURE.md and docs | 0.5d | `docs/` |
| 4.7 | Full regression test run | 3d | All modules |
| 4.8 | Performance benchmark: tick-to-DB latency before/after | 1d | Benchmarks |

**Migration rollback strategy:**
1. Backup old `.duckdb` files before migration
2. Migration runs in a DuckDB transaction
3. Verification step compares row counts between old and new
4. If verification fails → rollback = restore from backup
5. Old files renamed to `*.duckdb.bak` (not deleted) for 30 days

**Chronicle Queue scope:** Chronicle remains for:
- `ChronicleAuditLogWriter` — append-only audit log
- `ChronicleDeadLetterQueue` — dead letter queue
- `EventSourcedOrderRepository` — OMS event sourcing

DuckDB replaces only: event store, feature store, scan store, pipeline graph store, historical warehouse.

**Validation:**
- All 214 test files pass
- No compilation errors across all 24 modules
- `./gradlew test :app:testFrontend`
- `./scripts/run-full-regression.sh`
- Tick-to-DB latency < 5ms (p99), same as current

---

## Part 6: Configuration

### 6.1 New TradingProperties

```yaml
trade:
  engine:
    unified-db-path: "runtime/tradej.duckdb"
    sync:
      mode: AUTO                    # AUTO | MANUAL | DISABLED
      interval-ms: 300000           # 5 minutes — parquet export interval
      batch-size: 1000              # Events per sync batch
      watermark-backup-interval: 60000  # 1 minute — watermark persistence
    assets:
      - name: equity
        enabled: true
        parquet-root: "data/historical-equity"
        intervals: ["1m"]
      - name: options
        enabled: true
        warehouse-path: "runtime-dev/historical.duckdb"
        intervals: ["5m"]
    dedup:
      enabled: true
      ttl-hours: 24                 # Event dedup window
      max-entries: 500000           # Max dedup cache size
```

### 6.2 Backward Compatibility

The unified engine reads existing database files and migrates them on first startup:

```java
public class SchemaMigrator {
    /**
     * Detects old schema layout and migrates to unified format.
     * Preserves all existing data.
     */
    public void migrateIfNeeded(Path unifiedDbPath, 
                                 Path oldEventDbPath,
                                 Path oldHistoricalDbPath) {
        // 1. Check if unified DB already has data
        // 2. If not, attach old DBs and COPY data into unified schemas
        // 3. Verify row counts match
        // 4. Rename old files as backup
    }
}
```

---

## Part 7: Risk Mitigation

| Risk | Mitigation |
|------|------------|
| Data loss during migration | Transactional migration with backup of old files |
| Performance regression from unified DB | DuckDB handles multiple schemas efficiently; benchmark before/after |
| Sync lag during high volatility | Configurable batch size and interval; drop policy with DLQ |
| Schema evolution conflicts | Schema versioning in `sync.schema_versions` table |
| Concurrent writes from multiple threads | DuckDB WAL mode + `synchronized` on connection (existing pattern) |
| Parquet export blocking live writes | Export runs on dedicated thread, uses `read_only` snapshot |

---

## Part 8: Success Criteria

| Criterion | Measurement |
|-----------|-------------|
| **Single DB file** | Only `runtime/tradej.duckdb` exists (no `trade.duckdb` or `historical.duckdb`) |
| **Zero data loss** | Watermark-based crash recovery verified in integration test |
| **Dedup works** | Re-downloading same data produces identical parquet (checksum match) |
| **Auto-sync** | Live ticks appear in parquet within 5 minutes |
| **Manual sync** | CLI `tradej engine sync` completes within 30 seconds |
| **New asset class** | Adding "commodity" via CLI takes < 10 seconds, no code changes |
| **All tests pass** | `./gradlew test` — 0 failures across 214 test files |
| **No performance regression** | Tick-to-DB latency < 5ms (p99), same as current |

---

## Appendix A: File Change Summary

### New Files (Phase 1-3)
```
data/engine/build.gradle
data/engine/src/main/java/com/tradej/engine/UnifiedDuckDbEngine.java
data/engine/src/main/java/com/tradej/engine/SchemaBootstrapper.java
data/engine/src/main/java/com/tradej/engine/SyncCoordinator.java
data/engine/src/main/java/com/tradej/engine/WatermarkTracker.java
data/engine/src/main/java/com/tradej/engine/IdempotencyGuard.java
data/engine/src/main/java/com/tradej/engine/ParquetBridge.java
data/engine/src/main/java/com/tradej/engine/AssetRegistry.java
data/engine/src/main/java/com/tradej/engine/SyncMode.java
data/engine/src/main/java/com/tradej/engine/SyncResult.java
data/engine/src/main/java/com/tradej/engine/SyncSchedule.java
data/engine/src/test/java/... (6 test files)
```

### Modified Files
```
settings.gradle                    ← Add :data-engine module
app/build.gradle                   ← Add :data-engine dependency
composition/build.gradle           ← Add :data-engine dependency
app/src/main/java/.../TradingProperties.java  ← Add engine.* properties
app/src/main/java/.../PersistenceConfiguration.java  ← Use unified engine
app/src/main/java/.../FeatureStoreConfiguration.java  ← Use unified engine
app/src/main/java/.../ScanConfiguration.java  ← Use unified engine
app/src/main/java/.../AnalyticsConfiguration.java  ← Use unified engine
app/src/main/java/.../BrokerStartupOrchestrator.java  ← Wire sync coordinator
composition/src/main/java/.../StorageProfile.java  ← Single DB path
composition/src/main/java/.../DataComposition.java  ← Use unified engine
data/persistence/src/main/java/.../DuckDbEventStore.java  ← Delegate to unified
data/feature-store/src/main/java/.../DuckDbFeatureStore.java  ← Delegate to unified
data/persistence/src/main/java/.../DuckDbScanStore.java  ← Delegate to unified
data/persistence/src/main/java/.../DuckDbPipelineGraphStore.java  ← Delegate to unified
data/historical-ingest/src/main/java/.../DuckDbHistoricalWarehouse.java  ← Delegate to unified
data/analytics/src/main/java/.../DuckDbAnalyticsEngine.java  ← Use unified DB
cli/src/main/java/.../TradeCli.java  ← Add engine subcommands
```

### Removed Files (Phase 4)
```
None — old classes are refactored, not deleted.
Their logic moves into the unified engine module.
```

---

## Appendix B: DuckDB SQL Patterns

### Watermark Query
```sql
-- Get last synced event time for a symbol
SELECT last_synced_event_time_ms 
FROM sync.watermarks 
WHERE asset_class = 'equity' AND symbol = 'RELIANCE';
```

### Incremental Export
```sql
-- Export only new rows since last sync
COPY (
    SELECT * FROM runtime.candles 
    WHERE symbol = 'RELIANCE' 
      AND start_time_ms > (SELECT last_synced_event_time_ms 
                            FROM sync.watermarks 
                            WHERE symbol = 'RELIANCE')
    ORDER BY start_time_ms
) TO 'data/historical-equity/bars/interval=1m/symbol=RELIANCE/part-export.parquet' 
(FORMAT PARQUET);
```

### Dedup Check
```sql
-- Check if event already exists
SELECT COUNT(*) FROM sync.event_log 
WHERE event_id = 'evt-12345';
```

### Analytics View Refresh
```sql
-- Refresh analytics view over new parquet files
CREATE OR REPLACE VIEW analytics.equity_bars_1m AS
SELECT symbol, interval, bar_time_ms, open_paisa, high_paisa, low_paisa, close_paisa, volume
FROM read_parquet('data/historical-equity/bars/interval=1m/symbol=*/part-hive-*.parquet', 
                  hive_partitioning=true);
```

---

*End of Plan*
