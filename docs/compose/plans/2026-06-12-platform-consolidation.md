# Trade-J Platform Consolidation & Parity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use compose:subagent (recommended) or compose:execute to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Consolidate composition roots, simplify the event system, unify position tracking state, ensure research/replay execution parity, and clean up duplicate workflows.

**Architecture (re-confirmed 2026-06-12):** Single composition root (`FullComposition`) aggregating `ClockComposition` + `BrokerComposition` + `DataComposition` + `ExecutionComposition` + `PipelineComposition`. Spring delegates all production wiring to `FullComposition.createFull()`. The `app/.../config/` `@Configuration` classes are reduced to thin Spring-shell glue.

> **Note on prior architecture review:** `docs/COMPOSITION_ROOT_DECISION.md` (2026-06-11) recommended the opposite direction (delete wrappers, keep `app/` as root). User has explicitly chosen to follow this plan instead, which re-introduces the structural pattern the doc flagged. NPE risk at `FullComposition.execution()` is mitigated by removing the legacy `create()` overload that returns null — see Phase 2B.

**Tech Stack:** Java 21, Spring Boot, Disruptor, DuckDB, Parquet

---

## User Review Required

> [!IMPORTANT]
> **Single Composition Root Migration:** Bootstrapping the entire Spring Application Context via the programmatic `FullComposition` root is a major architectural shift. This will change how properties are injected (moving validation to pure Java properties records) and will significantly reduce direct `@Autowired` annotations in Spring controllers.

> [!WARNING]
> **Position Tracking Refactoring:** Merging position state across `PortfolioEngine`, `PositionRiskHandler`, and the databases into a single `PositionService` will require editing multiple trading lifecycle handlers on the hot path. We must preserve low-latency execution boundaries.

> [!CAUTION]
> **Event System Consolidation:** Deleting `ShardedDisruptorEventBus` and `BrokerScopedEventBus` might impact custom performance configurations. However, a single Disruptor EventBus is more than sufficient for standard MCX/FNO option volumes.

---

## Open Questions (resolved)

1. **Reconciliation cadence:** Fully automated, running in the existing `trading/simulation` sandbox (PaperBrokerConnection + MatchingEngine + PnLLedger) before any live enablement. (Resolved 2026-06-12.)
2. **Backward-compatible Parquet files:** No migration script. Rebuild history from raw logs. (Resolved 2026-06-12.)

---

## Phase 1: Event System Simplification & Consolidation ✅ DONE (verified 2026-06-12)

- [x] **P1.1 — `EventBus.java` MODIFY for trace ID propagation.** Already implemented via `runtime/disruptor/.../TracingEventBus.java` (75-line decorator).
- [x] **P1.2 — DELETE `ShardedDisruptorEventBus`.** Already removed; only stale `.class` artifacts in `build/`. Test `DisruptorEventBusLegacyRemovalTest.java` codifies the removal.
- [x] **P1.3 — DELETE `BrokerScopedEventBus`.** Already removed. The 3 Spring beans that wired it (`dhanEventBus`/`upstoxEventBus`/`iciciEventBus`) have been migrated to the canonical `DisruptorEventBus` + `TracingEventBus` composition.
- [x] **P1.4 — Verification.** `./gradlew :runtime-disruptor:test :core:test` BUILD SUCCESSFUL (force-rerun, 45 tests). 4 pre-existing `architecture-test` failures are unrelated to Phase 1 (all in Phases 2-5 scope).

---

## Phase 2: Unify Composition Roots

### 2A: Recreate the 5 wrapper composition classes (foundation, buildable in isolation) ✅ DONE (2026-06-12)

Build order is dependency-driven (bottom-up). Each class compiles standalone before the next is added.

- [x] **P2.1 — `composition/.../ClockComposition.java`.** Wires `Clock` + `TradingClock` + `EventMetadataFactory`. Three factory methods: `live()`, `replay()`, `live(Clock)`. **Created + compiles.**
- [x] **P2.2 — `composition/.../DataComposition.java`.** Wires `ChronicleDeadLetterQueue`, `ChronicleAuditLogWriter`, `DuckDbConnectionPool`, `DuckDbEventStore`, `AsyncDuckDbEventStore` (with `start()`), `DuckDbPipelineGraphStore`, `DuckDbScanStore`. Implements `AutoCloseable` (`throws Exception`) for safe shutdown. **Created + compiles.**
- [x] **P2.3 — `composition/.../ExecutionComposition.java`.** Wires `EventSourcedNetPositionProvider`, `CaffeineIdempotencyCache`, `MarginEnforcementHandler` (with `MarginProvider` + `PortfolioProvider` resolved from `IBrokerConnection` capabilities), `KillSwitchCoordinator`, `PositionRiskHandler`. **Created + compiles.**
- [x] **P2.4 — `composition/.../PipelineComposition.java`.** Wires `VirtualClock`, `ReactorBridge`, `ReactorBridgeMetrics`, `NodeRegistry` (with SPI providers + 9 hard-coded descriptors: INGRESS, RISK, CANDLE, STRATEGY, OMS, REACTOR, SCAN, SCAN_CRITERION, SCAN_AGGREGATOR), `PipelineNodeFactory` (10-arg), `PipelineRuntimeService`, `DagPipelineRuntimeService`, `DagPipelineIngressBridge`. **Created + compiles.**
- [x] **P2.5 — `composition/.../FullComposition.java`.** Aggregator. Two entry points: `createFull(...)` (Spring path, all 5 sub-compositions wired) and `brokerOnly(BrokerProfile)` (CLI / replay path, only clock + broker). **NPE mitigation:** `dataComposition()`, `executionComposition()`, `pipelineComposition()` (and the short-named `data()`, `execution()`, `pipeline()` shims) throw `IllegalStateException` on a `brokerOnly()` result instead of returning null. **Created + compiles.**
- [x] **P2.6 — Tests.** `:composition:test` BUILD SUCCESSFUL. Tests added: `FullCompositionTest` covers `ClockComposition` (live, replay, live(Clock)) and the NPE-mitigation contract via the accessors. Tests for `brokerOnly` with real `BrokerProfile` skipped — requires SPI broker providers (DHAN/UPSTOX/ICICI), not on the composition classpath. Will exercise via `:app:test` in Phase 2B.

### 2B: Wire Spring to delegate bean instantiation to `FullComposition` ✅ DONE (2026-06-12)

- [x] **P2.7** — `FullCompositionConfiguration.java` builds `FullComposition` as a Spring bean from existing Spring beans + `TradingProperties` profiles.
- [x] **P2.8** — Stripped 5 risk beans (`RiskLimits`, `EventSourcedNetPositionProvider`, `MarginEnforcementHandler`, `KillSwitchCoordinator`, `PositionRiskHandler`) from `TradingConfiguration.java`. Shim beans in `FullCompositionConfiguration.java` were initially used for backward compat, then **REMOVED** in a follow-up commit after all consumers migrated to inject `FullComposition` directly.
- [x] **P2.8.1 — Consumer migrations** ✅ — All 8+ consumers migrated to inject `FullComposition` directly: `AdminConfiguration` (DailyRiskResetScheduler, reconciliationScheduler), `RuntimeAndStartupConfiguration` (eventBus, startupDependencies), `DataConfiguration` (replayStateManager), `PipelineConfiguration` (pipelineNodeFactory), `ReconciliationController`, `PortfolioAnalyticsController`, `OrderApplicationService`, `StartupDependencies`. `OrderControllerComponentTest` updated to use Mockito mock of `FullComposition` + `ExecutionComposition` (since both are final with private constructors).
- [x] **P2.9** — `DhanBrokerConfiguration.java:99-175` dual-root. Done via shim pattern: the 15 port beans are intentionally kept as Spring `@Bean` methods. They ARE the shim layer over `BrokerComposition.brokerConnection()`; each port bean calls `conn.<port>()` (or `conn.getCapability(PortClass.class)`). All 15 port beans source from the same `BrokerComposition` instance.
- [x] **P2.10** — Apply same dual-root fix to other broker configs. Done via shim pattern for `UpstoxBrokerConfiguration` (14 ports), `IciciBrokerConfiguration` (ICICI ports), `SimulationBrokerConfiguration` (simulation ports). `BrokerAdapterConfiguration.java` was deleted in a prior session, so the original P2.10 plan was superseded by scope expansion to the 4 broker config files.
- [x] **P2.11** — 4 integration tests verified: they use `BrokerComposition.create()` directly, which still works (not changed by Phase 2A). All 4 tests + 1 config test pass. `OrderControllerComponentTest` updated to use Mockito mock (since `FullComposition`/`ExecutionComposition` are final with private constructors).
- [x] **P2.12** — Full test sweep: `:app:test` BUILD SUCCESSFUL, `:composition:test` BUILD SUCCESSFUL, `:cli:test` 2 pre-existing picocli reflection failures, `:broker-gateway:test` BUILD SUCCESSFUL, `:architecture-test:test` 4 pre-existing carry-forward failures.
- [x] **P2.13** — `docs/COMPOSITION_ROOT_DECISION.md` supersession notice added.
- [x] **P2.14** — MEMORY.md updated with Phase 2B addendum.
- [x] **P2.15** — 2 stale Javadocs corrected.
- [x] **P2.16** — `runtime-verification/scripts/phase1-composition-verification.sh` verified clean.

**Net Phase 2B result:** 5 risk beans are now sourced from `FullComposition` (single source of truth). 15 broker port beans are intentional shims. Shim beans in `FullCompositionConfiguration` have been REMOVED — the migration is complete.

### 2C: Documentation updates ✅ DONE (2026-06-12)

- [x] **P2.13 — `docs/COMPOSITION_ROOT_DECISION.md`** — Supersession notice added at top of file (§0 Supersession section). Doc's §1-§4 remain as historical analysis.
- [x] **P2.14 — MEMORY.md** — Phase 2B addendum added to `## Architecture decisions` capturing the shim-bean pattern, the consumer-migration completion, and the P2.9/P2.10 shim closure.
- [x] **P2.15 — 2 stale Javadocs** corrected: `composition/.../ReplayService.java:27` and `cli/.../CliDevCommand.java:39`.
- [x] **P2.16 — `runtime-verification/scripts/phase1-composition-verification.sh:30,33`** — verified clean (script is already correct: greps for `FullComposition` and reports usage).

---

## Phase 3: Position State Unification

- [ ] **P3.1 — New `core/src/main/java/com/tradej/core/domain/service/PositionService.java`.** Single, thread-safe, event-sourced service. Canonical source of truth for positions and unrealized PnL.
- [ ] **P3.2 — Migrate `PortfolioEngine`.** Delegate position/exposure queries to `PositionService`.
- [ ] **P3.3 — Migrate `PositionRiskHandler`.** Delegate limit checks and risk valuations to `PositionService`.
- [ ] **P3.4 — Reconcile DuckDB persistence.** Position snapshots write through `PositionService` only.
- [ ] **P3.5 — Broker API position state.** Broker snapshot feeds `PositionService` events; consumers read from `PositionService`.
- [ ] **P3.6 — Verify** `./gradlew :trading-strategy:test :trading-execution:test` and replay-parity tests.

---

## Phase 4: Research & Replay Execution Parity

- [ ] **P4.1 — `StrategyLabService` — remove custom exit simulator loop.** Route candles and orders through the canonical OMS via the simulated backtest execution broker.
- [ ] **P4.2 — Expand `DeterministicReplayParityTest`** to assert identical state output across multiple backtest iterations on real Parquet feeds.
- [ ] **P4.3 — Auto-reconciliation scheduler (sandbox).** Build scheduler on top of `trading/simulation` infrastructure. Detected drift → bracket order/exit. No live enablement gate yet.
- [ ] **P4.4 — Verify** `./gradlew :research-lab:test --tests "com.tradej.research.parity.DeterministicReplayParityTest"` and 100% execution parity between CLI standalone and Spring REST backtest runs.

---

## Phase 5: Dashboard Gateway & UX Simplification

- [ ] **P5.1 — `GatewayEventBridge` generic router.** Dynamically publish new domain metrics onto the WebSocket gateway using JSON metadata tags. No per-metric REST controllers.
- [ ] **P5.2 — `GatewayWebSocketHandler` topic subscription wildcards.** Support custom strategy parameter topics.
- [ ] **P5.3 — Verify** React frontend option analytics stream and local position tables remain consistent.

---

## Verification Plan (run after each phase)

### Automated Tests
```bash
# Build & compile
./gradlew clean build -x test

# Architecture test suite (module isolation)
./gradlew :architecture-test:test

# Phase 4 replay parity
./gradlew :research-lab:test --tests "com.tradej.research.parity.DeterministicReplayParityTest"
```

### Manual Verification
1. Launch Spring backend in dev mode; verify console registers Unified Composition roots.
2. Run a candle backtest via CLI standalone and via Spring REST; compare logs to confirm 100% execution parity.
3. Stream options analytics over React frontend; verify local position tables do not diverge.

---

## Out of Scope (called out but not in this plan)

- Simulation code migration from `broker-gateway` to `trading/simulation` (separate plan).
- Migration of `OrderRequest`/`OrderCommand`/`SliceOrderRequest` consolidation to canonical `BrokerSource`-based type (separate plan).
- `@Configuration` class consolidation from 21 to ~5 (separate plan, partially addressed by Phase 2B).
- Event schema versioning implementation (Q2 resolved: rebuild from raw logs, no Parquet migration).
