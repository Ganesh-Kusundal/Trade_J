# Architecture (current)

Living overview of the Trade-J codebase. Historical AI reviews live in [archive/](archive/).

**Full report (flows, disruptor configs, test pyramid, type registry):** [ARCHITECTURE_REPORT.md](ARCHITECTURE_REPORT.md) — start at **§0** for diagram index and capabilities.

**Interactive diagrams (browser):** [visuals/Trade-J-Architecture-Visual.html](visuals/Trade-J-Architecture-Visual.html) — architecture, components, flows, modules.

**Leaf file index:** [CODEBASE_LEAF_INDEX.md](CODEBASE_LEAF_INDEX.md) — update manually when module layout changes.

## Runtime modes

See [runtime-mode-audit.md](runtime-mode-audit.md). Modes: `LIVE`, `REPLAY`, `BACKTEST` (`RuntimeMode` in `:core`).

## Broker selection

| `trade.broker-type` | Profiles | Adapter modules |
|---------------------|----------|-----------------|
| `dhan` (default) | `dev`, `dev-live`, `prod` | `:broker-dhan` |
| `upstox` | `upstox-dev`, `upstox-prod`, `upstox-analytics` | `:broker-upstox` |

Spring wiring for brokers lives in `app` (`BrokerConfiguration`, `UpstoxConfiguration`). Analytics-only Upstox skips WebSocket connect; REST market data via `/api/v1/market/*`.

## Module map

Gradle project IDs match physical paths (e.g. `:broker-api` → `broker/api/`).

```
core/                               Domain events, ports, pipeline graph types
broker/
  api/                              IBrokerConnection, broker ports
  core/                             Shared auth, resilience
  dhan/                             Dhan adapter
  upstox/                           Upstox adapter
runtime/
  disruptor/                        LMAX Disruptor event bus
  hotpath/                          MarketDataPipeline, OrderPipeline, PipelineConfig
trading/
  strategy/                         Candles, plugins, portfolio engine
  execution/                        OMS, risk, execution handlers
  scanner/                          Scan engine
  simulation/                       Matching engine
data/
  persistence/                      Chronicle, DuckDB, replay
  feature-store/                    DuckDB / in-memory features
app/                                Spring Boot composition root
gateway/                            WebSocket UI bridge
cli/                                Operator CLI
```

## Dependency flow (simplified)

```
:core
  → :broker-api → :broker-{core,dhan,upstox}
  → :runtime-disruptor → :runtime-hotpath
  → :trading-{strategy,execution,scanner,simulation}
  → :data-{persistence,feature-store}
  → :app (depends on all)
```

Known coupling: `:data-persistence` → `:trading-scanner` (scan store models); `:runtime-disruptor` → `:trading-execution` + `:trading-strategy` (stage list).

## Dual pipeline (important)

Two assembly paths coexist:

1. **Legacy hot path** — `EventBusConfiguration` → `PipelineConfig.create()` → `DisruptorEventBus` with fixed stage order (risk → candle → strategy → execution). Production path for live ticks today.
2. **Graph runtime** — `PipelineRuntimeService` / `GraphRuntime` in `:core`, wired from `:app`. Target for composable DAG; not fully retired legacy path yet.

Do not assume graph runtime parity with Disruptor until explicitly tested ([BACKLOG.md](BACKLOG.md)).

## Historical data APIs

| Endpoint | Source |
|----------|--------|
| `/api/v1/market/ltp`, `/api/v1/market/historical/candles` | Broker REST (`MarketDataProvider`) |
| `/admin/historical/*` | Local DuckDB (`localHistoricalRangeService`) |

## Startup

`BrokerStartupOrchestrator` (in `app`) drives catalog load, preflight, WebSocket connect policy via `BrokerRuntimeMode`.

## Options liquidity scan

Rank individual CE/PE contracts by open interest, volume, and bid-ask spread.

| Surface | Entry |
|---------|-------|
| CLI | `./scripts/tradej options-scan NIFTY IDX_I --top 10` |
| REST | `POST /api/v1/options/scan?underlying=NIFTY&segment=IDX_I&top=5` |
| Profile | `scan run --profile option-liquidity` (requires `trade.scan.enabled=true`) |

Enable scheduled/profile scans in `application-*.yml`:

```yaml
trade:
  scan:
    enabled: true
    default-profile: intraday-hybrid
```

Profile definitions: [config/scan-profiles.json](config/scan-profiles.json) (CLI standalone) and `trade.scan.profiles` in YAML (app). The `option-liquidity` profile uses `optionScan` thresholds instead of coarse universe criteria.

## Open work

See [BACKLOG.md](BACKLOG.md). Post–physical-move code extraction targets:

- Broker Spring config → `broker/` ([CODE_EXTRACTION.md](CODE_EXTRACTION.md))
- App-local broker services → `trading/` / `runtime/`
- Scan DTOs shared via `:core` to break persistence ↔ scanner coupling
