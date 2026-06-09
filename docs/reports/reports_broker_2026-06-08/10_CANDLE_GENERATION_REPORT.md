# Candle Generation Report
**Generated:** 2026-06-08  
**Source:** Code analysis — no dedicated candle generator found in broker modules; analysis of tick event structure

## Current Candle Generation State

### Implementation Location
- Candle aggregation exists in `runtime/disruptor` and `runtime/hotpath` (referred to by `MarketDataPipeline`)
- NOT in any broker adapter — broker adapters produce `MarketTickEvent`; aggregation is downstream
- `core/domain/market/CandleIntervalSpec` defines canonical intervals

### Required Fields for Candle Generation
| Field | Source in `MarketTickEvent` | Present |
|-------|----------------------------|---------|
| Open | First `ltpPaisa` in interval | ✅ |
| High | Max `ltpPaisa` in interval | ✅ |
| Low | Min `ltpPaisa` in interval | ✅ |
| Close | Last `ltpPaisa` in interval | ✅ |
| Volume | `volume()` field | ✅ (Dhan/Upstox) |
| Timestamp | `exchangeTimestampMs()` | ✅ |

### Dhan Candle Support
**PASS** — Ticker sends `ltt` (last trade time) which maps to `exchangeTimestampMs` via `toEpochMs()`. Full sends `ltt` for OHLC validity.

### Upstox Candle Support
**PARTIAL** — `exchangeTimestampMs` is broker-provided. However, OI-only frames (type 5) carry `exchangeTimestampMs` but no price — can be used for candle volume confirmation only.

### ICICI Candle Support
**PARTIAL** — `System.currentTimeMillis()` used as timestamp. Risk of clock drift if client and exchange clocks differ.

### Out-of-Order Tick Handling
**NOT ADDRESSED IN CODE** — No explicit timestamp-based ordering in broker adapters. Downstream aggregation (in disruptor/hotpath) would need to handle out-of-order ticks.

### Gap Handling
**NOT ADDRESSED IN CODE** — No gap detection in broker adapters. Gaps detected downstream by `CandleAggregationService` (not in scope).

## Verdict

| Candle Interval | Feasible | Gaps |
|-----------------|---------|------|
| 1 Second | YES | Out-of-order handling |
| 5 Second | YES | — |
| 15 Second | YES | — |
| 1 Minute | YES | — |
| 3 Minute | YES | — |
| 5 Minute | YES | — |
| 15 Minute | YES | — |
| 30 Minute | YES | — |
| 1 Hour | YES | — |

Brokers provide sufficient data for candle generation at all intervals. Out-of-order handling and gap detection are downstream concerns.
