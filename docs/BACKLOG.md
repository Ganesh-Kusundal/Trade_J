# Architecture backlog

Extracted from archived reviews (`docs/archive/`). Track fixes as GitHub issues or PRs; IDs match the original review symbols.

## P0 — fix before live capital

| ID | Module | Issue | Status |
|----|--------|-------|--------|
| N-01 | trade-execution | `EventSourcedNetPositionProvider` removes symbol on close — wrong for multi-trade symbols | **Fixed** — `tradeContributions` map; verify via regression |
| E-01 | trade-execution | Execution queue capacity 50 is too small — increase and make configurable | **Fixed** — capacity 1000; verify via `ExecutionHandlerUnitTest` / full regression |

## P1 — high / medium

| ID | Module | Issue |
|----|--------|-------|
| RP-01 | trade-persistence | Chronicle replay deserializes all events as same type |
| AD-02 | trade-app | Replay does not reset state — mixed live/replay corruption risk |
| PE-02 | trade-strategy | Fragile correlationId coupling between risk handler and portfolio engine |
| ST-02 | trade-app | Broad `DomainEvent` subscriptions — unnecessary dispatch overhead |
| UB-03 | trade-broker-upstox | Unchecked cast to `UpstoxInstrumentResolver` |
| DW-03 | trade-broker-dhan | Order stream drops `OrderPartiallyFilled` |
| GB-01 | trade-gateway | WebSocket bridge broadcasts all ticks to all clients |
| FS-01 | trade-feature-store | JDBC connection check on every event |
| SC-01 | trade-scanner | Batch-only scan — no incremental evaluation |

## P2 — lower priority

| ID | Module | Issue |
|----|--------|-------|
| ME-01 | trade-simulation | No slippage model |
| ME-02 | trade-simulation | No partial fills / queue position |
| UB-01 | trade-broker-upstox | Unsupported port sentinels untested |
| GC-01 | trade-hotpath | High `CandleDeveloping` allocation rate |
| ST-01 | trade-app | Large startup ApplicationRunner / orchestrator (partially extracted) |

See archived [CODE_LEVEL_REVIEW.md](archive/CODE_LEVEL_REVIEW.md) for full context.

See [CODE_EXTRACTION.md](CODE_EXTRACTION.md) for post-move code placement follow-ups.

See [ARCHITECTURE_REPORT.md](ARCHITECTURE_REPORT.md) for flows, test pyramid, and module map.
