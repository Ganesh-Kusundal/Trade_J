# Trade-J — Remediation & Adoption Plan (June 2026)

> **Date:** 2026-06-06
> **Scope:** Cross-report adoption plan for the 7 reviews on 2026-06-06:
>
> | # | Report | File | Items |
> |---|--------|------|-------|
> | 1 | Runtime / Event Flow | `docs/reports/ARCHITECTURE_REVIEW_2026-06-06.md` | 5 P0 + 11 P1 + 1 P2 |
> | 2 | Reactive Adoption | `docs/reports/REACTIVE_ADOPTION_REVIEW_2026-06-06.md` | 3 R-PRs (R-1, R-2, R-3) |
> | 3 | Test Coverage & Chaos | `docs/reports/TEST_COVERAGE_CHAOS_REVIEW_2026-06-06.md` | 5 P0 + 6 P1 + 6 P2 |
> | 4 | Broker Gateway | `docs/reports/BROKER_GATEWAY_ARCHITECTURE_REVIEW_2026-06-06.md` | 5 P0 + 6 P1 + 9 P2 |
> | 5 | Simulation / Replay / Backtest | `docs/reports/SIMULATION_REPLAY_BACKTEST_REVIEW_2026-06-06.md` | 5 P0 + 7 P1 + 7 P2 |
> | 6 | Plugin & Market Utils | `docs/reports/PLUGIN_MARKET_UTILS_REVIEW_2026-06-06.md` | 6 P0 + 4 P1 + 9 P2 |
> | 7 | Terminal Scalability | `docs/reports/TERMINAL_SCALABILITY_REVIEW_2026-06-06.md` | 5 P0 + 7 P1 + 7 P2 |
> | 8 | Multi-Asset Class | `docs/reports/MULTI_ASSET_CLASS_REVIEW_2026-06-06.md` | 5 P0 + 7 P1 + 7 P2 |
>
> **Total:** **39 P0s** + **3 R-PRs** + **50 P1s** + **48 P2s** = **140 items** consolidated into a single dependency-ordered plan.
> **Author:** Principal quant engineer — opinionated, dependency-aware, brutally honest about cost.
> **Companion:** [`docs/BACKLOG.md`](BACKLOG.md) — canonical issue registry (now extended with `RP-*` IDs).

---

## 0. TL;DR (the three things to read first)

1. **The platform cannot accept live capital today.** The 5 P0 defects in the architecture review are the gate: silent OMS drops, dedup misdesign, kill-switch TOCTOU, cancel-before-state-guard, and 8 deprecated DisruptorEventBus constructors. None of them has a regression test. Close them as **Phase 0** before any broker traffic.
2. **The new `broker-gateway` layer is a step backward.** It is a pass-through that pretends to be an abstraction, runs alongside the real `LoadBalancedBrokerGateway`, and is the gateway that *most* new code is being written against. **Pick Path A (delete the new layer, keep the old one) or Path B (delete the old, finish the new) in week 1.** Doing nothing is the worst option.
3. **The terminal is single-user, MOCK-mode, single-threaded REPL.** Five P0s must land before a second user can connect. P0-1 (per-user session isolation) + P0-2 (per-user WS filter) is the gate; P0-3 (live data) is the visible win. The remaining 2 P0s are nice-to-haves.

### Headline numbers

| Metric | Value |
|---|---|
| Total P0s | **39** |
| Total estimated P0 effort | **~58 person-days** (~12 person-weeks at 5d/wk, ~24 calendar weeks at 1 dev) |
| Effort to reach live capital (Phase 0+1+2+3) | **~32 person-days** (~6.5 person-weeks) |
| Effort to reach multi-user terminal (Phase 7 included) | **~52 person-days** (~10.5 person-weeks) |
| Effort to reach multi-asset / multi-broker ready (Phase 5) | **+12 person-days** on top |
| Effort to land all R-PRs (Reactor) | **+12-18 person-days** on top |
| Effort to land all P1s | **+85 person-days** (~17 person-weeks) |
| Effort to land all P2s | **+45 person-days** (~9 person-weeks) |
| **Grand total (P0+P1+P2+R-PRs)** | **~210 person-days** (~42 person-weeks, **~10 calendar months at 1 dev**) |

**Realistic minimum** to get to "Bloomberg-style for one user, NSE F&O, with failover and chaos coverage" = **~6 calendar months at 1 dev**, or **~2 months with 3 senior engineers working in parallel on Phase 2, 3, 7**.

---

## 1. Methodology — how the plan was built

### 1.1 ID convention

New items get `RP-NNN` IDs (Remediation Plan) following the `N-`/`E-`/`RP-`/`NR-`/`PR-` convention in `BACKLOG.md`. Cross-references use `<ORIGIN>-<NN>` where ORIGIN is the 3-letter report prefix (e.g. `ARCH-01`, `BG-02`, `PLG-01`).

| Prefix | Report |
|---|---|
| `ARCH-NN` | Architecture Review (1) |
| `R-NN` | Reactive Adoption (2) — `R-1`, `R-2`, `R-3` |
| `TCOV-NN` | Test Coverage (3) |
| `BG-NN` | Broker Gateway (4) |
| `SIM-NN` | Simulation / Replay / Backtest (5) |
| `PLG-NN` | Plugin & Market Utils (6) |
| `TERM-NN` | Terminal Scalability (7) |
| `MA-NN` | Multi-Asset Class (8) |

### 1.2 Dependency analysis

Three forces drive the phase ordering:

1. **Live capital gate.** Anything that can lose money or data on a busy morning is P0 and goes first.
2. **Code-path blocking.** Some items block the start of other items (e.g. you cannot land `R-1` Reactor rewrite until the `GatewayTopicRouter` is multi-threaded; you cannot land a 2nd user until per-user WS filtering is in).
3. **Refactor risk.** Some items are surgical (one PR, < 1 day) and can land in parallel. Others are 1-2 week refactors that touch 5+ modules and need to land atomically (e.g. Phase 2 broker gateway unification).

I built the dependency graph by reading every "Required fix" section in all 7 reports. Where two reviews touch the same file (e.g. `GatewayEventBridge` appears in ARCH-01, BG-04, BG-05, R-1, TERM-01, TERM-02, TCOV-01, TCOV-02), I treated that as a **single node** and routed all paths through it.

### 1.3 The 4 hard cross-report dependencies

These dependencies are the ones the team will trip over if the phases land out of order:

```
TCOV-03 (regression tests for 5 P0 defects)  ──depends on──>  Phase 0 fixes (ARCH-01..05)
R-1   (Reactor rewrite of GatewayEventBridge) ──depends on──>  BG-04 (GatewayTopicRouter multi-thread)
TERM-01 (per-user session)  ──depends on──>  BG-02/03 (real GatewayResult.Failure)
TERM-04 (live data panels)  ──depends on──>  TERM-02 (per-user WS filter) + BG-04
MA-05 (lot size validator)  ──depends on──>  MA-01 (capability methods on ExchangeSegment) or PLG-01 fix
R-1   (Reactor)  ──depends on──>  BG-04 (GatewayTopicRouter multi-thread)
```

These are the **critical path** items. Everything else is parallelizable.

---

## 2. Phased plan

8 phases, ordered by criticality. Each phase has explicit **gates** (what must be true to move on) and **deliverables** (PRs that must land).

### Phase 0 — Capital-Ready Gate (the live-money block)

**Effort:** ~16 person-days (~3 weeks at 1 dev)
**Gate:** All 5 ARCH P0s closed with their tests; `fullRegressionTest` green for 3 consecutive runs; 1 trading day sandbox with no dropped events in `/actuator/prometheus`.

| RP ID | Maps to | Title | Module | Effort | PRs |
|-------|---------|-------|--------|--------|-----|
| **RP-001** | ARCH-01 | Make `AsyncDispatchHandler` block on full; replay DLQ into bus; wire drop counters to `/actuator/prometheus` and `DisruptorReadinessHealthIndicator` | `runtime/disruptor` | 3d | 2 (impl + DLQ replay) |
| **RP-002** | ARCH-02 | Replace eventId dedup with `(brokerSource, exchangeSegment, instrumentId, sequenceNumber)` for market data; `(brokerOrderId, transition)` for OMS; persist dedup | `runtime/disruptor`, `data/persistence` | 3d | 2 (impl + DuckDB migration) |
| **RP-003** | ARCH-03 | Convert `killSwitch` to `AtomicBoolean` with CAS gate; implement unwind-on-halt (cancel-all + position-close fan-out) | `trading/execution` | 3d | 2 (gate + unwind) |
| **RP-004** | ARCH-04 | Never call broker cancel/modify without first persisting local `CancelRequested`/`OrderModified` and asserting state-machine guard | `trading/execution` | 2d | 1 |
| **RP-005** | ARCH-05 | Delete 8 deprecated `DisruptorEventBus` constructors; force `DisruptorPipelineConfig`-only path; recompile-test the whole tree | `runtime/disruptor` | 0.5d | 1 |
| **RP-006** | TCOV-03 | Add regression tests for the 5 ARCH P0 defects (drop counter, dedup race, kill-switch race, cancel-before-guard, replay state-reset) | `app` test, `runtime/disruptor` test | 2d | 5 (one per defect) |
| **RP-007** | ARCH-obs | Wire `dispatchDroppedEventCount`, `tickRateLimitedCount`, `dlqEnqueueCount`, `downstreamQueueDepth` to Micrometer; update `DisruptorReadinessHealthIndicator` to fail readiness on drop > 0 over 60s | `runtime/disruptor`, `app` | 1d | 1 |
| **RP-008** | ARCH-obs | Add OpenTelemetry spans around `processSignal` / `processFill` in `ExecutionHandler` | `trading/execution` | 0.5d | 1 |

**Verification:**
- `gradlew :runtime-disruptor:test` green (RP-001, RP-002, RP-005, RP-006)
- `gradlew :app:crossLayerRegressionTest` green (RP-003, RP-004, RP-006)
- `gradlew :app:runtimeE2eTest` green (RP-007)
- New `ArchUnit` rule: no `@Deprecated` on `DisruptorEventBus` constructors (RP-005)
- New `DisruptorEventBusDropTest` publishes 10k events to a subscriber that sleeps 100ms; asserts drop count > 0 and DLQ contains the dropped events (RP-001, RP-006)
- New `DisruptorDedupRaceTest` has 100 threads publishing the same `(source, segment, instrument, seq)` tuple; asserts exactly 1 publication (RP-002, RP-006)
- New `KillSwitchRaceTest` flips `killSwitch` while another thread publishes a `SignalPendingExecution`; asserts publication count matches loss budget (RP-003, RP-006)
- New `CancelOrderGuardTest` asserts that in `LifecycleState.SUBMITTING`, `cancelOrder` does NOT call the broker; in `SUBMITTED`, it does (RP-004, RP-006)
- New `ReplayStateResetTest` runs `HistoricalRangeService.replayFillEvents` twice consecutively; asserts no state leak (RP-006)

**Cross-references:**
- `ARCH-01` ↔ `TCOV-03` — RP-001 must land before RP-006 can have a target
- `ARCH-02` ↔ `SIM-P1-7` — both share the dedup misdesign; fix in one place only (RP-002 is the source of truth)
- `ARCH-04` ↔ `SIM-P1-4` — both touch OMS-first cancel/modify; same fix in same PR

---

### Phase 1 — Test Pyramid Closing (parallel to Phase 0-3, blocks all new work)

**Effort:** ~10 person-days (~2 weeks at 1 dev)
**Gate:** All 5 TCOV P0s closed; `LiveDhanTestSupport` is parallel-safe; `BrokerGatewayTest` covers SPI/routing/failover; new chaos tests for the 3 new fan-out surfaces.

| RP ID | Maps to | Title | Module | Effort |
|-------|---------|-------|--------|--------|
| **RP-009** | TCOV-01 | Make `LiveDhanTestSupport` parallel-safe and test-isolated (per-test `LiveDhanSessionFactory`, memoized `preflightAuth`, `try/finally` cleanup, `maxParallelForks=1` for relevant tasks) | `app` test | 1d |
| **RP-010** | TCOV-02 | Write the 7 missing `broker-gateway` tests: `ServiceLoaderBrokerRegistryTest`, `DefaultBrokerGatewayTest`, `BrokerRouterTest`, `MarketGatewayTest`, `BrokerResultTest`, `BrokerHandleTest`, `HistoricalRequestTest` | `broker-gateway` test | 2d |
| **RP-011** | TCOV-04 | Add WireMock broker + postback WS to `TradingHotPathE2EComponentTest`; tag `@Tag("component-broker")`; gate on `-PincludeBrokerMocks` | `app` test | 1d |
| **RP-012** | TCOV-05 | Add dedup-path stress tests to `DisruptorEventBusStressTest` (same eventId N threads, out-of-order dedup, wrap-around eviction) | `runtime/disruptor` test | 0.5d |
| **RP-013** | TCOV-P1-1 | Add 3 chaos tests for new fan-out surfaces: `GatewayEventBridgeChaosTest`, `BrokerRouterFailoverChaosTest`, `MarketDataPipelinePerSymbolBucketTest` | `gateway`, `broker-gateway`, `runtime/hotpath` test | 2d |
| **RP-014** | TCOV-P1-2 | Invert the ArchUnit whitelist: forbid `reactor.*` outside `com.tradej.pipeline.reactor.*`; same for Spring annotations | `architecture-test` | 0.25d |
| **RP-015** | TCOV-P1-3 | `RuntimeModeExtension` JUnit 5 extension that snapshots/restores `RuntimeModeHolder.mode()` around each test | `core` test | 0.25d |
| **RP-016** | TCOV-P1-4 | Add 4 jqwik property tests: `OrderProjectionPropertyTest`, `NetPositionPropertyTest`, `TokenBucketPropertyTest`, `PnLPropertyTest` | `core` test | 1d |
| **RP-017** | TCOV-P1-5/6 | Add `cli` test surface: picocli parse + exit-code tests for all 50+ subcommands; add `market-utils` tests: `InstrumentIdTest`, `ExchangeSegmentTest`, `SymbolNormalizerTest` | `cli`, `market-utils` test | 0.5d |
| **RP-018** | TCOV-P2-1 | Nightly flaky-test detector: 50× `unitTest` run with per-test pass-rate reporting; `@Tag("flaky")` quarantine; test-runner plugin that fails on retry | `buildSrc` | 1d |

**Verification:**
- `gradlew unitTest` green for 50 consecutive runs (RP-018)
- `LiveDhanTestSupport` under `maxParallelForks=4` and `--max-parallel-fork=2` does not bleed LIVE state into SANDBOX tests (RP-009)
- `BrokerRouterTest` asserts primary 5xx → fallback path; `ServiceLoaderBrokerRegistryTest` asserts duplicate-registration handling (RP-010)
- `GatewayEventBridgeChaosTest` simulates 50 transports at random 0-500ms latency; asserts p99 write < 100ms with `StepVerifier` + `VirtualTimeScheduler` (RP-013, also see R-1)
- `TokenBucketPropertyTest` asserts `consumed ≤ capacity ∧ tokens ≥ 0` over random sequences (RP-016)

**Cross-references:**
- `TCOV-02` ↔ `BG-01` — the 7 missing tests should be written **after** the broker-gateway unification path is chosen, so they exercise the final API
- `TCOV-04` ↔ `ARCH-04` — the WireMock broker test is what catches the cancel-before-guard defect end-to-end
- `TCOV-P1-1` ↔ `R-1` — the chaos test is the *verification gate* for the Reactor rewrite

---

### Phase 2 — Broker Gateway Unification (the biggest refactor of the cycle)

**Effort:** ~10 person-days (~2 weeks at 1 dev) for Path A; ~25 person-days for Path B
**Gate:** Pick Path A or B in week 1, document the decision in `docs/ADRS/0007-broker-gateway-unification.md`, and **delete the loser's classes atomically**. No half-state.

**Decision required from team lead.** Both paths are valid. Path A is faster, lower risk, smaller blast radius. Path B is more architecturally honest, more future-proof, but takes 2-3× longer.

#### Path A (recommended): delete the new `broker-gateway` layer, keep `LoadBalancedBrokerGateway`

| RP ID | Maps to | Title | Module | Effort |
|-------|---------|-------|--------|--------|
| **RP-019a** | BG-01 path A | Delete `BrokerRouter`, `MarketGateway`, `BrokerHandle`, `GatewayResult` (revert to `IBrokerConnection` direct usage with capability checks) | `broker-gateway` | 2d |
| **RP-020a** | BG-02 path A | Replace `MarketGateway`/`BrokerHandle` with `GatewayOps` static helpers in `broker-gateway/result/` (wrap, requireCapability, getCapability) | `broker-gateway` | 1d |
| **RP-021a** | BG-03 path A | Adopt `LoadBalancedBrokerGateway` as the single gateway surface; deprecate `BrokerGateway.dhan(config)` factory | `broker/core`, `broker-gateway` | 1d |
| **RP-022a** | BG-04 path A | Replace single `gateway-publisher` thread with 4-thread `ExecutorService`; per-transport MPSC queue; remove `synchronized (session)` in `SpringWebSocketTransport`; apply `ConcurrentWebSocketSessionDecorator` | `gateway` | 2d |
| **RP-023a** | BG-05 path A | Share `ObjectMapper` from Spring context to `BrokerHandle` (now `GatewayOps`); pre-allocate `Map.of(...)` payloads where ≤10 entries; introduce flat `byte[]` codec for hot path | `gateway` | 1d |
| **RP-024a** | BG-P1-1 path A | `DefaultBrokerGateway.fromRegistry` (or its replacement in Path A) throws on dropped profiles; logs the dropped list at WARN | `broker-gateway` | 0.25d |
| **RP-025a** | BG-P1-2 path A | Document the 12-of-16-port single-broker decision in `broker/core/.../routing/` Javadoc; add a "degraded mode" signal to health indicator | `broker/core` | 0.5d |
| **RP-026a** | BG-P1-3 path A | `ServiceLoaderBrokerRegistry` (if kept) threads classloader explicitly; test in fat-jar build | `broker-gateway` | 0.5d |
| **RP-027a** | BG-P1-4 path A | Already covered by RP-022a | — | — |
| **RP-028a** | BG-P1-5 path A | `GatewayEventBridge.close()` calls `eventBus.unsubscribe` for all 17 event types (requires adding `unsubscribe` to `EventBus`) | `gateway`, `core` | 0.5d |
| **RP-029a** | BG-P1-6 path A | `BrokerRouter` (or replacement) caches `BrokerHandle` at `setActive` time | `broker-gateway` | 0.25d |

**Total Path A: ~9 person-days.**

#### Path B: keep the new `broker-gateway` layer, port failover from the old

| RP ID | Maps to | Title | Module | Effort |
|-------|---------|-------|--------|--------|
| **RP-019b** | BG-01 path B | Delete `LoadBalancedBrokerGateway` and the four `Failover*` classes in `broker/core/routing/` | `broker/core` | 2d |
| **RP-020b** | BG-02 path B | Add real behavior to `MarketGateway` and `BrokerHandle`: per-broker circuit breaker, per-symbol timeout, per-port rate limit | `broker-gateway` | 5d |
| **RP-021b** | BG-03 path B | Rewrite `GatewayResult` as sealed `Success`/`Failure`; rewrite `BrokerHandle.timed` to catch exceptions and produce `Failure`; rewire all 22 call sites | `broker-gateway` | 4d |
| **RP-022b** | BG-04 path B | Same as RP-022a | `gateway` | 2d |
| **RP-023b** | BG-05 path B | Same as RP-023a | `gateway` | 1d |
| **RP-024b** | BG-P1-1..6 path B | Same as RP-024a..029a | `broker-gateway` | 2d |

**Total Path B: ~18 person-days.**

**My recommendation: Path A.** The new layer is a pass-through that adds cost without behavior. The old layer has real failover. Time saved on Path A is invested in the multi-user / live-data work that ships visible product value.

**Verification (both paths):**
- All 7 new `broker-gateway` tests from RP-010 pass against the new shape
- `TradingHotPathE2EComponentTest` runs the full OMS-exec-broker round-trip; assert OMS + event store + DuckDB are consistent
- A 10-minute stress test with one broker simulated as 5xx: assert the gateway rotates to the secondary within 1s and back when the primary recovers
- Fat-jar build (`gradlew bootJar` + run) — `ServiceLoaderBrokerRegistry` discovers all 3 remaining providers (RP-026)

**Cross-references:**
- `BG-04` ↔ `R-1` — the multi-thread gateway-publisher is the **prerequisite** for the Reactor rewrite in R-1
- `BG-04` ↔ `TERM-01/02` — single-thread publisher blocks per-user isolation
- `BG-05` ↔ `R-1` — the per-event `LinkedHashMap` allocation is what R-1 deletes by switching to `Flux`

---

### Phase 3 — Plugin & Market Utils Correctness (the silent real-money bugs)

**Effort:** ~6 person-days (~1.5 weeks at 1 dev)
**Gate:** `Instruments.bankNifty()` returns the right symbol; no `IciciBrokerProvider` in SPI; all order-entry paths validate lot size; the broker SPI is no longer coupled to the network call.

| RP ID | Maps to | Title | Module | Effort |
|-------|---------|-------|--------|--------|
| **RP-030** | PLG-01 | Fix `Instruments.bankNifty()` → `"BANKNIFTY"`, `finNifty()` → `"FINNIFTY"`, `midcpNifty()` → `"MIDCPNIFTY"`, `bankNiftyFuture()` → `"BANKNIFTY"`. Add `WellKnownIndices` enum + per-broker symbol resolution | `core/domain/instrument` | 0.5d |
| **RP-031** | PLG-02 | `ContractSymbolNormalizer.tryNormalize` (returns `Optional<String>`); `normalize` logs WARN on no-match; all order-entry call sites use `tryNormalize` | `core/domain/instrument` | 0.5d |
| **RP-032** | PLG-03 | Delete `IciciBrokerProvider` + SPI entry; add runtime config flag `-Dtradej.broker.providers=dhan,upstox,simulation`; log loaded providers at INFO | `broker-gateway` | 0.25d |
| **RP-033** | PLG-04 | Make `BrokerProvider.connect(BrokerProfile)` lazy (return a `IBrokerConnection` proxy that defers network call) or two-phase (`createProfile` + `connect()` explicit) | `broker-gateway` | 1d |
| **RP-034** | PLG-05 | Wrap `MarketDatasource.register` in BEGIN/COMMIT/ROLLBACK; typed `DatasourceRegistrationException`; connection cleaned on partial init | `broker-gateway` | 0.5d |
| **RP-035** | PLG-06 | Split `BrokerInspectionReport.capabilities` into `portCapabilities` + `markerCapabilities`; `formatReport` iterates each | `broker-gateway` | 0.25d |
| **RP-036** | PLG-P1-1 | Per-exchange `ContractSymbolParser` (NseContractSymbolParser, BseContractSymbolParser, McxContractSymbolParser); dispatcher in `ContractSymbolNormalizer`; fold `BrokerHandle.defaultSegment` into the same logic | `core/domain/instrument`, `broker-gateway` | 2d |
| **RP-037** | PLG-P1-2 | `BrokerInspector.inspectAsync()` returning `CompletableFuture<BrokerInspectionReport>`; bounded by `Semaphore(N)`; per-probe timeout; LTP-based strike selection | `broker-gateway` | 2d |
| **RP-038** | PLG-P1-3 | `probeOptionGreeks` checks both `call().greeks()` and `put().greeks()` | `broker-gateway` | 0.1d |
| **RP-039** | PLG-P1-4 | Delete `BrokerCertification`; certification CLI calls `DefaultBrokerInspector.inspect(...)` with a `(symbol, segment)` arg | `broker-gateway` | 0.5d |

**Verification:**
- `InstrumentsTest` asserts `bankNifty().symbol() == "BANKNIFTY"` and that the symbol resolves to a Dhan security ID (RP-030)
- `ContractSymbolNormalizerTest` table-driven: 50 valid + 50 invalid inputs; `tryNormalize` returns `Optional.empty()` for invalid (RP-031)
- `BrokerGatewayIntegrationTest` constructs `BrokerGateway.dhan(profile)` without making a network call; `connect()` is called explicitly later (RP-033)
- `BrokerDescriptorProviderListTest` asserts the runtime flag filters SPI providers (RP-032)
- `DefaultBrokerInspectorAsyncTest` asserts the async report has per-probe latency and the per-probe timeout fires correctly (RP-037)

**Cross-references:**
- `PLG-01` ↔ `MA-04` — the `WellKnownIndices` enum dovetails with the `InstrumentType` enum from MA-04; both should land in the same PR
- `PLG-04` ↔ `TCOV-01` — making `connect` lazy is what makes `LiveDhanTestSupport` parallel-safe (no network in constructor)
- `PLG-P1-1` ↔ `MA-P1-7` — the per-exchange parser is what makes `ExchangeSegment.supportsOptions()` correct

---

### Phase 4 — Simulation / Replay / Backtest Unification

**Effort:** ~10 person-days (~2 weeks at 1 dev)
**Gate:** Only one backtest implementation exists (`BacktestExecutionService`); all callers use one `BacktestResult` record; `IsolatedReplayStateManager.afterReplay` is strict; `ReplayClock` enforces monotonicity; `HistoricalRangeService` is split into query + replay interfaces.

| RP ID | Maps to | Title | Module | Effort |
|-------|---------|-------|--------|--------|
| **RP-040** | SIM-01 | Delete `BacktestServiceImpl` and `BacktestService` interface; `BacktestExecutionService` is the unified facade | `trading/simulation`, `replay/engine`, `cli`, `app` | 2d |
| **RP-041** | SIM-02 | Move `BacktestResult` to `core/domain/model/BacktestResult.java`; one canonical 14-field record (runId, strategy, symbol, trades, wins, losses, pnlPaisa, maxDrawdownPaisa, sharpeRatio, initialCapitalPaisa, finalCapitalPaisa, tradeLog, orders, status); CLI/REST/simulation all use it | `core`, `trading/simulation`, `cli` | 1d |
| **RP-042** | SIM-04 | `IsolatedReplayStateManager.afterReplay` renamed `tryRestore`; throws `IllegalStateException` if no matching `beforeReplay`; `beforeReplay` reuses the prior snapshot if not restored | `replay/engine` | 0.5d |
| **RP-043** | SIM-05 | `ReplayClock.advanceTo` throws `IllegalArgumentException` if `timestampMs < currentTimeMs.get()`; add a `VirtualClock` delegation option | `data/persistence` | 0.25d |
| **RP-044** | SIM-03 | Split `HistoricalRangeService` into `HistoricalDataQuery` (read-only, 7 query methods) + `HistoricalReplayService` (7 replay methods) + a `DuckDbConnectionFactory`; 10k-row cap returned via `ReplayResult`; `replayFillEvents` no longer fabricates `qty/2` | `data/persistence` | 3d |
| **RP-045** | SIM-P1-2 | Inject `Clock` into `MatchingEngine`, `PnLLedger`, `SimulatedOrderService`; `ReplayClock` is the impl in REPLAY mode | `trading/simulation` | 1d |
| **RP-046** | SIM-P1-4 | `BacktestExecutionService.runBacktest` returns `BacktestRunHandle` (`CompletableFuture<BacktestResult>`); `BacktestRunStore` for progress; `status(runId)` polls progress | `replay/engine` | 1d |
| **RP-047** | SIM-P1-5 | `CliBacktestCommands` uses `HistoricalRangeService.queryCandles` as primary, falling back to broker; offline backtest is a first-class path | `cli`, `data/persistence` | 0.5d |
| **RP-048** | SIM-P1-6 | Replace EWMA-of-`|Δprice|` with Welford's online variance; rename `lastPriceVarianceBySymbol` → `realizedVarianceBySymbol`; update slippage formula to `vol * sqrt(T) * z` | `trading/simulation` | 0.5d |
| **RP-049** | SIM-P1-7 | `HistoricalRangeService.replayOrders` / `replayFillEvents` use `UUID.randomUUID()` for `eventId`; carry `sourceEventId` on `EventMetadata` | `data/persistence`, `core` | 0.5d |

**Verification:**
- `BacktestServiceImplTest` is removed from the test tree; `BacktestService` is no longer in the dependency graph (RP-040)
- `BacktestResultTest` asserts JSON round-trip preserves all 14 fields and rejects `wins + losses > trades` (RP-041)
- `IsolatedReplayStateManagerTest` calls `tryRestore` twice; second call throws (RP-042)
- `ReplayClockTest` calls `advanceTo(500)` then `advanceTo(1000)` then `advanceTo(800)`; third call throws (RP-043)
- `HistoricalDataQueryTest` mocks the new interface; existing `FillReplayIntegrationTest` rewritten against the new shape; second-run test asserts no state leak (RP-044)
- `MatchingEngineClockTest` with virtual clock: 1 year of data in 1 second wall time; trade timestamps match the virtual clock (RP-045)
- `BacktestRunHandleTest` runs a 100k-event backtest; `status()` returns `progress=0.42` mid-run; cancel via `handle.cancel()` (RP-046)

**Cross-references:**
- `SIM-02` ↔ `PLG-P1-7` (terminal) — the backtest run-handle is what the terminal's `Run` button polls for progress
- `SIM-03` ↔ `R-3` — once `HistoricalDataQuery` is an interface, the token-refresh / session-risk polling can use it as a polling target
- `SIM-05` ↔ `ARCH-02` — both share the clock-source-of-truth pattern; fix once in `VirtualClock`, point everything at it
- `SIM-P1-4` ↔ `R-2` — `BacktestRunHandle` is a candidate for `orTimeout`-based progress polling

---

### Phase 5 — Multi-Asset Class (India-first, multi-broker-ready)

**Effort:** ~12 person-days (~2.5 weeks at 1 dev)
**Gate:** `MatchingEngine` tick comes from the instrument; `SessionSchedule` is per-segment; `InstrumentType` is an enum; `OrderRequest` validates lot size; `ExchangeSegment` has capability methods; `ProductClass` is the canonical category.

| RP ID | Maps to | Title | Module | Effort |
|-------|---------|-------|--------|--------|
| **RP-050** | MA-01 | `MatchingEngine.match` uses `instrument.tickSizePaisa()`; throws if instrument is null in non-backtest; expose `tickSizePaisa` on `OrderRequest` for backtests | `trading/simulation`, `core` | 0.5d |
| **RP-051** | MA-02 | `SessionSchedule` becomes `Map<ExchangeSegment, SessionHours>` where `SessionHours = (open, close, eveningClose?)`; `isPastClosingWindow` uses the per-segment hours | `core` | 0.5d |
| **RP-052** | MA-04 | `InstrumentType` enum: `EQUITY, FUTURE, OPTION, CURRENCY_FUTURE, CURRENCY_OPTION, COMMODITY_FUTURE, COMMODITY_OPTION, INDEX, BOND, MUTUAL_FUND, UNKNOWN`; `Instrument.instrumentType` becomes `InstrumentType`; catalog loaders map raw strings to the enum | `core` | 0.5d |
| **RP-053** | MA-05 | `LotSizeValidator` in `core/.../validation/`: looks up instrument by `(symbol, exchangeSegment)`, asserts `quantity % instrument.lotSize() == 0`; returns `ValidationIssue(ERROR, "lot-size mismatch")`; wired into `OrderRequest` validation chain | `core` | 0.5d |
| **RP-054** | MA-P1-7 | Add capability methods to `ExchangeSegment`: `supportsOptions()`, `supportsFutures()`, `isIndex()`, `isCashEquity()`, `isCurrency()`, `isCommodity()`, `requiresLotSize()`, `defaultTickSizePaisa()`; replace 4-5 hard-coded segment checks across the codebase | `core`, `trading/simulation`, `broker-dhan`, `gateway` | 0.5d |
| **RP-055** | MA-03 | Introduce `ProductClass { LEVERAGED, DELIVERY, FUTURES, OPTIONS, CARRY }` as canonical; `ProductType` stays per-broker; `BrokerProductMapper` interface + per-broker impl; `OrderRequest` carries `ProductClass` | `core`, `broker-dhan`, `broker-upstox` | 5d |
| **RP-056** | MA-P1-1 | Move exchange-mandated product matrix from `DhanOrderValidator` to `core/.../validation/ExchangeProductMatrix.java`; Dhan-specific extensions stay in `DhanOrderValidator` | `core`, `broker-dhan` | 0.5d |
| **RP-057** | MA-P1-2 | `NSE_CURRENCY.venueExchange() = NCDS`, `BSE_CURRENCY.venueExchange() = BCDS`; `Exchange` enum becomes macro category | `core` | 0.25d |
| **RP-058** | MA-P1-3 | `Instruments.commodity("GOLD")` contract test against the catalog | `core` | 0.25d |
| **RP-059** | MA-P1-5 | `DhanProtocolConstants.SEGMENT_CODES` single source of truth; parser does map lookup; unit test asserts every `ExchangeSegment` has a Dhan code | `broker-dhan` | 0.5d |
| **RP-060** | MA-P1-6 | Delete `Exchange.IDX`; update all references to `INDEX` | `core` | 0.05d |
| **RP-061** | MA-P1-4 | `BrokerHandle.defaultSegment` is replaced by a `Broker.instrumentResolver(symbol).lookup()` call; the string-prefix match is removed | `broker-gateway` | 1d |

**Verification:**
- `MatchingEngineTickTest` for MCX (1 rupee tick), NSE F&O (5 paisa), NSE equity (1 paisa): all produce correctly rounded fill prices (RP-050)
- `SessionScheduleTest` for `NSE_CURRENCY` (09:00-17:00), `MCX_COMM` (09:00-23:30 with 17:00-17:30 break), `NSE_FNO` (09:15-15:30): all return correct `isPastClosingWindow` (RP-051)
- `InstrumentTypeTest` asserts the enum covers all known catalog-loader outputs (RP-052)
- `LotSizeValidatorTest` for NIFTY (lot=25, qty=13 → reject; qty=25 → accept), BANKNIFTY (lot=15, qty=15 → accept; qty=14 → reject) (RP-053)
- `ExchangeSegmentCapabilityTest` asserts the new methods; `MatchingEngineTickTest` uses `segment.defaultTickSizePaisa()` for backtests without a catalog (RP-054)
- `BrokerProductMapperTest` for Dhan (`INTRADAY → LEVERAGED`, `CNC → DELIVERY`, `MARGIN → FUTURES?` — TBD per Dhan docs) and Upstox (`I → LEVERAGED`, `D → DELIVERY`, `M → FUTURES`) (RP-055)
- `ExchangeProductMatrixTest` asserts Dhan's segment × product matrix matches the union of the core matrix and Dhan's extensions (RP-056)

**Cross-references:**
- `MA-04` ↔ `PLG-01` — both are "well-known asset class identities"; land in the same PR
- `MA-05` ↔ `PLG-01` — lot-size validation depends on the symbol being right
- `MA-P1-7` ↔ `MA-01` and `MA-02` — capability methods make the data sources in P0-1 and P0-2 a single line each
- `MA-03` ↔ `BG-01` — the per-broker product mapper is the second reason to land broker gateway unification first

---

### Phase 6 — Reactive Adoption (R-1, R-2, R-3 from `REACTIVE_ADOPTION_REVIEW_2026-06-06.md`)

**Effort:** ~12-18 person-days (~2.5-3.5 weeks at 1 dev). R-2 and R-3 are small (≤ 1 day each); R-1 is the big one.
**Gate:** R-2 and R-3 land first (zero risk). R-1 lands **after** Phase 2 BG-04 is done (multi-thread gateway-publisher is its prerequisite).

| RP ID | Maps to | Title | Module | Effort | Prerequisite |
|-------|---------|-------|--------|--------|--------------|
| **RP-062** | R-2 | Replace `CompletableFuture.supplyAsync(commonPool())` with `Executors.newFixedThreadPool(8)` + `orTimeout` in `ExecutionHandler.placeOrderWithTimeout`; add `reactorThreadAudit` Gradle task that fails the build on `supplyAsync` without executor | `trading/execution`, `buildSrc` | 1d | None |
| **RP-063** | R-3 | `Flux.interval`-based token refresh (`DefaultTokenLifecycleService`), session-risk polling, expired-options refresh (`BrokerExpiredOptionQueryService`); `Schedulers.boundedElastic()` for blocking I/O; `StepVerifier` + `VirtualTimeScheduler` tests | `broker/core`, `broker-dhan`, `data/historical-ingest` | 1d | None |
| **RP-064** | R-1 | Replace `GatewayEventBridge` (455 lines) with a `Sinks.Many<DomainEvent>` + per-topic `groupBy` + per-transport `Flux`; delete the 200k dedup cache and `dedupPruner` daemon; per-client backpressure via `Schedulers.boundedElastic()` | `gateway` | 1-2w | RP-022a/b (BG-04) |
| **RP-065** | R-1 test | R-1 chaos test: 50 virtual WebSocket transports at random 0-500ms latency; feed 10k `MarketTickEvent`; assert no transport drops > 5% and p99 client-write latency < 100ms with `StepVerifier` + `VirtualTimeScheduler` | `gateway` test | 1d | RP-064 |
| **RP-066** | R-1 test | R-1 backpressure test: force one transport's `write` to block 30s; assert the source `Sinks` returns `FAIL_OVERFLOW` within 1s and the DLQ receives the dropped events | `gateway` test | 0.5d | RP-064 |

**Verification:**
- `reactorThreadAudit` task green (RP-062)
- `R-1` chaos test green (RP-065)
- `R-1` backpressure test green — this is the test that would have caught ARCH-01 (RP-066)
- `StepVerifier` virtual-time tests for token refresh, session-risk polling, expired-options refresh (RP-063)
- 2-day live sandbox with 10k ticks/sec; `sinks.fail-overflow-count` ≤ 0; per-client p99 write latency ≤ 50ms (RP-064)

**Cross-references:**
- `R-1` ↔ `BG-04` (RP-022a/b) — single-thread publisher is the bottleneck
- `R-1` ↔ `ARCH-01` — Reactor backpressure is the *real fix* for silent drops; RP-064 + RP-066 enshrine the test
- `R-1` ↔ `TCOV-P1-1` (RP-013) — the chaos tests in RP-013 are the pre-R-1 baseline; the post-R-1 chaos tests in RP-065/066 are the regression
- `R-2` ↔ `ARCH-04` (RP-004) — `orTimeout` + `cancel(true)` ensures broker cancel-on-timeout actually works

---

### Phase 7 — Terminal: Multi-User + Live Data (the visible product win)

**Effort:** ~12 person-days (~2.5 weeks at 1 dev)
**Gate:** Per-user session isolation (P0-1) + per-user WS filter (P0-2) land first; they are the gate for any second user. P0-3 (live data wiring) and P0-4 (resizable panels) are the visible wins. P0-5 (async REPL) is a CLI-only refactor.

| RP ID | Maps to | Title | Module | Effort | Prerequisite |
|-------|---------|-------|--------|--------|--------------|
| **RP-067** | TERM-01 | Add Spring Security (or lightweight auth filter); `UserPrincipal` from JWT or session token; `BrokerSessionRegistry` resolves per-user broker connection; all REST controllers accept `@AuthenticationPrincipal`; the WebSocket handshake resolves `UserPrincipal` from the session token | `app`, `gateway` | 3w | RP-020/021 (BG-02/03) |
| **RP-068** | TERM-02 | `GatewayTopicRouter.subscribe(transport, topic, userId)`; `SendTask` carries `userId`; `dispatchToTransports` skips non-matching transports; `publishFiltered` becomes the default path; `GatewayEventBridge` stamps `userId` on every event | `gateway` | 1w | RP-022a/b (BG-04) |
| **RP-069** | TERM-03 | Add `react-resizable-panels`; replace the fixed `grid-cols-[280px_1fr]` / `grid-rows-[1fr_220px]` with `<PanelGroup>` / `<Panel>` / `<PanelResizeHandle>`; persist layout to `localStorage` via `zustand/middleware/persist` | `frontend` | 0.5w | None |
| **RP-070** | TERM-04 | `useWebSocket(url, topics)` hook; wire each panel: `WatchlistPanel` ← `MARKET_TICK`, `ChartPanel` ← `CANDLE_CLOSED`+`CANDLE_DEVELOPING`, `OptionChainPanel` ← `OPTION_CHAIN_UPDATED`, `OrdersPanel` ← `ORDER_UPDATE`, `PositionsPanel` ← `POSITION_UPDATE`+`PNL_UPDATE`, `LogsPanel` ← `SCAN_COMPLETED`+`REPLAY_CONTROL`; reconnection with exponential backoff; per-topic status dot | `frontend` | 1w | RP-068 (TERM-02) |
| **RP-071** | TERM-05 | Split `InteractiveShell.run()` into: (a) command-execution thread reading user input, (b) background subscription thread pushing live updates via `terminal.writer().println(...)` on a status line; JLine `MaskingCallback` / `ParameterCompleter` for async updates; distinct `Ctrl-C` (cancel current) from `Ctrl-D` (exit) | `cli` | 1-2w | None |
| **RP-072** | TERM-P1-3 | Structured `TerminalException` sealed class with subclasses per error type; exception handler in `InteractiveShell` dispatches on type | `cli`, `core` | 0.5w | None |
| **RP-073** | TERM-P1-4 | Add `@tanstack/react-virtual`; use `useVirtualizer` for `WatchlistPanel`, `PositionsPanel`, `OptionChainPanel` (Greeks table) | `frontend` | 0.5w | None |
| **RP-074** | TERM-P1-5 | `MultiChartContainer` wrapping `lightweight-charts`; supports 1×1, 2×1, 2×2, 4×4 grids; each chart a separate WS subscription | `frontend` | 0.5w | None |
| **RP-075** | TERM-P1-6 | Command palette in `InteractiveShell`: `StringsCompleter` over picocli's command tree; fuzzy match (Levenshtein) | `cli` | 0.5w | None |
| **RP-076** | TERM-P1-7 | Wire `WS:` and `MODE:` badges to real connection state and `RuntimeModeHolder.mode()`; color-code green/yellow/red/gray | `frontend`, `app` | 0.25w | None |
| **RP-077** | TERM-P1-1 | Cache `HttpClient` and `AttachClient` in a process-wide singleton keyed by `--attach` URL | `cli` | 0.25w | None |
| **RP-078** | TERM-P2-3 | `~/.tradej/config.toml` loader; precedence CLI > env > config > default | `cli` | 0.5w | None |
| **RP-079** | TERM-P2-5 | Call `AttachClient.isReachable()` at CLI startup; clear error and exit on failure (or degraded mode in REPL) | `cli` | 0.25w | None |
| **RP-080** | TERM-P2-7 | Add `react-router-dom`; top-level `<Router>` with `<Routes>` for `/terminal`, `/settings`, `/history`, `/help` | `frontend` | 0.5w | None |

**Verification:**
- Two CLI / browser sessions connect to the same `app` instance with different `UserPrincipal`s; each sees only its own orders / positions / P&L; `GatewayTopicRouter` logs the userId match (RP-067, RP-068)
- `WatchlistPanel` updates within 100ms of a `MARKET_TICK` event for a symbol in the watchlist; `ChartPanel` updates within 200ms of `CANDLE_CLOSED`; `OrdersPanel` updates within 50ms of `ORDER_UPDATE` (RP-070)
- `InteractiveShell` REPL stays responsive while a `backtest run` is in progress; `Ctrl-C` cancels the backtest; `Ctrl-D` exits cleanly with WS unsubscribe + `AttachClient.close()` (RP-071)
- `WatchlistPanel` with 5000 symbols and 10k ticks/sec: scroll is smooth (≥ 50 fps), p99 input latency < 50ms (RP-073)
- `MultiChartContainer` shows 4 NIFTY/BANKNIFTY/FINNIFTY/MIDCPNIFTY charts updating concurrently (RP-074)

**Cross-references:**
- `TERM-01` ↔ `BG-02/03` (RP-020/021) — real `GatewayResult.Failure` is what per-user errors need
- `TERM-02` ↔ `R-1` (RP-064) — the per-user filter is much easier to express on a `Flux<GatewayMessage>` than on a hand-rolled dispatcher
- `TERM-04` ↔ `SIM-P1-4` (RP-046) — the `BacktestRunHandle.status()` is what the terminal's `Run` button polls
- `TERM-05` ↔ `BG-04` (RP-022a/b) — the async REPL is built on the multi-thread gateway

---

### Phase 8 — Long-Term / P1 / P2 (continuous, parallel to other phases)

**Effort:** ~130 person-days (~26 person-weeks). Run as capacity allows; the items below are the highest-impact P1s and P2s.

| RP ID | Maps to | Title | Module | Effort | Notes |
|-------|---------|-------|--------|--------|-------|
| **RP-081** | ARCH-P1-6 | Partition `ExecutionHandler` by `symbol` into 4-8 worker threads, each with its own `ArrayBlockingQueue`; per-partition queue-depth metrics; reject at strategy layer, not execution | `trading/execution` | 3d | Hard dependency on `ConcurrentHashMap` state machine — already safe |
| **RP-082** | ARCH-P1-7 | Move `PortfolioEngine` out of the ring (no I/O) to the `AsyncDispatchHandler` ring side; stopwatch metric per `case`; alarm at p99 > 50µs | `trading/strategy`, `runtime/disruptor` | 2d | Watch out for `PortfolioEngine.onDomainEvent` capital reservation race |
| **RP-083** | ARCH-P1-8 | Collapse `LoadBalancedBrokerGateway` to single broker (if Path B chosen); per-port `health()` returns 0 if last WS heartbeat > N seconds; gateway picks first `IBrokerConnection.health() > 0` | `broker/core` | 1d | Already covered by RP-019b/021b |
| **RP-084** | ARCH-P1-9 | Block `DagPipelineIngressBridge` in `RuntimeMode.LIVE` (mode guard); cold path only in REPLAY / BACKTEST | `app` | 0.5d | Single-line mode check; add ArchUnit rule |
| **RP-085** | ARCH-P1-10 | Per-`(ExchangeSegment, instrumentKey)` token bucket in `MarketDataPipeline`; `trade.hot-path.symbol-burst-permits` config; 95th-percentile shed-rate metric | `runtime/hotpath` | 2d | |
| **RP-086** | ARCH-P1-11 | Drive `StreamingScanCriterion` from a `ScanNode` in the DAG runtime; cron becomes universe refresh only | `trading/scanner` | 5d | New DAG node type + wiring |
| **RP-087** | ARCH-P1-12 | `ThreadLocal` (or visitor-return) for `currentDownstream` in `ExecutionHandler`; remove the shared mutable field | `trading/execution` | 0.5d | |
| **RP-088** | ARCH-P1-13 | Carry `cumulativeQuantity`, `incrementalQuantity`, `realizedPnlPaisa` on `TradeUpdated`; populate in `ExecutionHandler` | `core/.../event/TradeUpdated.java` + caller | 0.5d | |
| **RP-089** | SIM-P1-3 | Already covered by RP-040 (delete `BacktestService`) | — | — | |
| **RP-090** | TCOV-P2-2..6 | Property tests on `Result` algebra, contract tests on SPI, integration-test module split, `certification/`+`explorer/` test coverage, `mavis` REPL test | `broker-gateway` test, `app` test | 3d | |
| **RP-091** | PLG-P2-1..9 | `ExchangeSegment.fromCode` map lookup; `Symbol` uppercase normalization; `Instruments` config-driven registry; `BrokerCapability` enum; `DuckDbConnectionFactory`; `DhanProtocolConstants.SEGMENT_CODES` already in RP-059; `MarketDatasource` SQL injection guard; remove `BrokerProvider.isEnabled` (replaced by RP-032) | various | 5d | |
| **RP-092** | TERM-P2-1/2/4/6 | `MenuEntry` record + `List<MenuEntry>` per menu; `Ctrl-C` cancels current, `Ctrl-D` exits; virtualize `WatchlistPanel`; wire remaining panels to WebSocket | `cli`, `frontend` | 2d | |
| **RP-093** | MA-P2-1..7 | Per-broker `BrokerProductMapper` (covered in RP-055); `LotSizeRegistry` + `TickSizeRegistry` in `core/.../marketdata/`; `DhanBinaryParser` segment codes single source (covered in RP-059); `Order`/`Trade` carry `lotSize`; bond + mutual fund support (separate project) | various | 5d | |
| **RP-094** | ARCH-P2-13/14/15/16 | Move `Broker*Configuration` into broker modules as auto-config; reject orders outside session with `session_risk`; `NetPositionProvider` state in `PositionRiskHandler.StateSnapshot`; OpenTelemetry spans on `processSignal`/`processFill` (covered in RP-008) | `app`, `core` | 5d | |
| **RP-095** | MDC + config | MDC helper `MdcHelper.withContext(DomainEvent, Runnable)`; apply to `AsyncDispatchHandler`, `AsyncDuckDbEventStore`, `AsyncDuckDbWriter`, `GatewayTopicRouter`, `DagPipelineIngressBridge`; config placement rule (delete `ExecutionConfiguration` deprecated marker) | `app`, `core`, `gateway` | 2d | |
| **RP-096** | Frontend types | Frontend DTO type generation from Java records (Pkl or `jsonschema2ts`); CI step | `buildSrc`, `frontend` | 3d | |
| **RP-097** | Stress + flaky | `reactorThreadAudit` Gradle task (covered in RP-062); flaky-test detector (covered in RP-018); additional stress tests on `TradingCircuitBreaker`, `OrderStateMachine`, `PriceMath` | `buildSrc`, `core` test | 2d | |

**Verification:** as capacity allows. The P0 + P1 set is the critical path; the P2 set is opportunistic.

---

## 3. Cross-cutting improvements (apply across phases)

### 3.1 MDC propagation (ARCH-P1-15, RP-095)

Already flagged in `IMPROVEMENT_PLAN.md` §5.1. Land `MdcHelper` in the same PR as Phase 0 RP-001/002. Six async paths: `AsyncDispatchHandler`, `AsyncDuckDbEventStore`, `AsyncDuckDbWriter`, `GatewayTopicRouter`, `DagPipelineIngressBridge`, `ReactorBridge` (after R-1).

### 3.2 Error-handling philosophy (RP-097 doc, no code)

Document the convention in `core/src/main/java/com/tradej/core/package-info.java`:
- **Fail fast** for invariant violations (`IllegalStateException`).
- **Degrade** at trust boundaries (broker I/O, persistence) via typed exceptions.
- **Never** `catch (Exception ignored)`. Always log + counter.

### 3.3 Frontend↔backend contract (RP-096)

Pick Pkl or `jsonschema2ts` in week 1; land the generator in the same PR as the first Phase 7 (TERM) work.

### 3.4 Configuration sprawl (RP-097)

One-sentence rule per config class:
- `Broker*Configuration` — broker client wiring (separate files per broker)
- `RuntimeConfiguration` — ONE file, owns `RuntimeModeHolder` + starter + health
- `Persistence*Configuration`, `FeatureStore*Configuration` — storage wiring
- `Pipeline*Configuration` — pipeline runtime wiring
- `Studio*Configuration`, `Admin*Configuration` — feature flags

Delete `ExecutionConfiguration` (deprecated marker). Audit all 29 config files for compliance. Land in RP-095.

### 3.5 Architectural boundary test (RP-097)

Extend `architecture-test`:
- `core` has zero Spring imports (already true; lock it)
- `trading-execution` does not import `runtime-disruptor` directly (currently goes through `EventBus`)
- `broker-*` does not import `trading-*` (currently true; lock it)
- `runtime-disruptor` does not import specific handler types — push to a `PipelineStage` list
- Invert the reactive whitelist (covered in RP-014)

Land in the same PR as RP-014.

---

## 4. Phased execution Gantt (text)

This is the dependency-ordered critical path. **Bold** = blocking; *italic* = parallelizable.

```
Week 1-3:   **Phase 0** (RP-001..008)               *Phase 1 setup* (RP-014, RP-015, RP-018)
Week 3-4:   **Phase 1** (RP-009..013)               *Phase 3* (RP-030, RP-032, RP-033, RP-036)
Week 4-5:   **Phase 2 Path A** (RP-019a..029a)      *Phase 4* (RP-040, RP-041, RP-042, RP-043, RP-044)
Week 5-7:   *Phase 5* (RP-050..054)                 *Phase 6 R-2/R-3* (RP-062, RP-063)
Week 7-9:   *Phase 5* (RP-055..061)                 **Phase 6 R-1** (RP-064..066)  [depends on Phase 2 BG-04]
Week 9-12:  **Phase 7** (RP-067, RP-068)            *Phase 8 P1 set* (RP-081..088)
Week 12-14: *Phase 7* (RP-069..076)                 *Phase 8 P1 set* (RP-090..097)
Week 14-24: *Phase 8 P2 set* (RP-091..097 + cleanup)
```

**Critical path (longest dependency chain):**
Phase 0 (RP-001..008) → Phase 2 BG-04 (RP-022a/b) → Phase 6 R-1 (RP-064..066) → Phase 7 (RP-067..080)
**= ~20 person-weeks = ~4 calendar months at 1 dev, ~6-7 weeks at 3 senior engineers in parallel**

---

## 5. Acceptance criteria

### 5.1 "Live capital ready" (end of Phase 0 + 1 + 2 + 3)

1. All 5 ARCH P0s closed with their regression tests (RP-001..006)
2. `fullRegressionTest` green for **5 consecutive runs**
3. 2-day live Dhan sandbox with no dropped events in `/actuator/prometheus`
4. `DhanOrderLifecycleIntegrationTest` shows cancel-before-fill race correctly rejected (RP-004 + RP-006)
5. `LiveDhanTestSupport` passes under `maxParallelForks=4` (RP-009)
6. `BrokerGatewayTest` covers all 7 test classes in RP-010
7. The 6 PLG-01..06 P0s closed; `Instruments.bankNifty()` returns `"BANKNIFTY"`
8. The path-A or path-B broker gateway decision is documented in `docs/ADRS/0007-broker-gateway-unification.md` and the loser's classes are deleted

### 5.2 "Multi-user terminal" (end of Phase 7)

1. Two CLI/browser sessions with different `UserPrincipal`s see disjoint data (RP-067, RP-068)
2. `WS:` and `MODE:` badges reflect real connection state (RP-076)
3. Each panel updates from a live `WebSocket` topic (RP-070)
4. `InteractiveShell` REPL is responsive during a long command (RP-071)
5. 4 charts in a 2×2 grid updating concurrently with 1k ticks/sec total (RP-074)

### 5.3 "Multi-asset / multi-broker ready" (end of Phase 5)

1. `MatchingEngineTickTest` passes for MCX, NSE F&O, NSE equity (RP-050)
2. `SessionScheduleTest` passes for NSE Currency, MCX, NSE F&O (RP-051)
3. `LotSizeValidatorTest` passes for NIFTY, BANKNIFTY, MCX GOLD (RP-053)
4. `ExchangeSegmentCapabilityTest` passes; 4-5 hard-coded segment checks across the codebase are replaced (RP-054)
5. `BrokerProductMapperTest` passes for Dhan and Upstox (RP-055)

### 5.4 "Reactor adopted" (end of Phase 6)

1. `R-1` chaos test green: 50 transports at random latency, 10k events, p99 < 100ms (RP-065)
2. `R-1` backpressure test green: blocked transport causes `FAIL_OVERFLOW` + DLQ enqueue (RP-066)
3. `R-2` placement timeout test: `orderManagementService` sleeping 30s; placement returns at 10s with typed `OrderPlacementTimeoutException` (RP-062)
4. `R-3` token refresh / session-risk / expired-options tests use `StepVerifier` + `VirtualTimeScheduler` (RP-063)
5. `reactorThreadAudit` task green (RP-062)

---

## 6. Tracking

### 6.1 Backlog updates

- All 96 `RP-NNN` IDs are added to `docs/BACKLOG.md` alongside the existing items.
- Each PR must:
  - Land the fix
  - Land the new test (mapped to a `REGRESSION_MANIFEST.md` invariant)
  - Add a row to `REGRESSION_MANIFEST.md` referencing the new invariant
  - Update `BACKLOG.md` with the new status
- Each phase end is gated by `./gradlew fullRegressionTest` and the relevant `runtimeE2eTest` / `crossLayerRegressionTest`.

### 6.2 ADR additions

Three new ADRs required before the corresponding PRs:

1. **`docs/ADRS/0007-broker-gateway-unification.md`** — Path A or Path B decision (Phase 2).
2. **`docs/ADRS/0008-product-class-canonicalization.md`** — `ProductClass` as the canonical category, per-broker `ProductType` enum (Phase 5, MA-03 / RP-055).
3. **`docs/ADRS/0009-runtime-mode-security.md`** — Spring Security / `UserPrincipal` / per-user broker session registry (Phase 7, TERM-01 / RP-067).

### 6.3 Test manifest updates

For each P0 closed, add a row to `REGRESSION_MANIFEST.md`:

```
| `DisruptorEventBusDropTest` | regressionPreflightTest | none | — | INV-33 async-dispatch drop |
| `DisruptorDedupRaceTest` | unitTest | none | — | INV-34 dedup race |
| `KillSwitchRaceTest` | componentTest | none | — | INV-35 kill-switch TOCTOU |
| `CancelOrderGuardTest` | componentTest | none | — | INV-36 cancel-before-guard |
| `ReplayStateResetTest` | componentTest | none | — | INV-37 replay state reset |
| `InstrumentSymbolTest` | unitTest | none | — | INV-38 well-known indices |
| `MatchingEngineTickTest` | unitTest | none | — | INV-39 per-exchange tick |
| `SessionScheduleTest` | unitTest | none | — | INV-40 per-segment hours |
| `LotSizeValidatorTest` | unitTest | none | — | INV-41 lot-size validation |
| `PerUserWebSocketFilterTest` | componentTest | none | — | INV-42 per-user WS filter |
```

(And so on for the 39 P0s.)

---

## 7. Closing thought

The platform has the **right shape**: hexagonal broker boundary, event-sourced OMS, LMAX Disruptor hot path, DAG cold path, DLQ + audit, dual storage. The 7 reviews identified **39 P0 defects** that span 5 categories:

1. **Silent data loss** (ARCH-01, ARCH-02, BG-04, BG-05) — the system drops events and doesn't tell you
2. **Race conditions** (ARCH-03, ARCH-04, SIM-05, MA-P1-2) — the system has TOCTOU windows that can fire in production
3. **Misleading APIs** (BG-03, PLG-02, PLG-06, SIM-01, SIM-02, MA-04) — the API returns success / hardcoded data / type-unsafe strings
4. **Architectural rot** (BG-01, BG-02, ARCH-05) — duplicate abstractions, deprecated constructors, pass-throughs
5. **Single-user / single-broker** (TERM-01, TERM-02, MA-03, MA-P1-2) — the system is hard-coded to one user and one broker

**Phase 0 closes categories 1-4 enough to ship live capital.** **Phase 5-7 close category 5 enough to ship multi-user + multi-broker.** Everything else is debt you can service in parallel without taking risk.

The biggest risk to Trade-J is not that the 39 P0s are hard. They're not. They're 1-3 day items individually. The risk is that **the team is being asked to ship against a moving target**: a new gateway layer that was finished but never integrated, a terminal labeled "Phase 1" that has hard-coded `MODE: MOCK`, a backtest service that returns hardcoded prices, an `Instruments` helper that returns the wrong symbol. None of these are subtle. They're the kind of things a 5-line test catches if the test exists, and a $5M incident catches if it doesn't.

**The plan is not to "fix everything." It's to land 32 person-days of P0 work in 6.5 weeks that makes the platform safe to put money on, then continue the rest as capacity allows.**

See also:
- `docs/IMPROVEMENT_PLAN.md` — prior improvement plan (2026-06-01); this document supersedes the *new P0* section
- `docs/reports/ARCHITECTURE_REVIEW_2026-06-06.md` — the 5 P0s of Phase 0
- `docs/reports/REACTIVE_ADOPTION_REVIEW_2026-06-06.md` — R-1, R-2, R-3 (Phase 6)
- `docs/reports/TEST_COVERAGE_CHAOS_REVIEW_2026-06-06.md` — the 5 P0s of Phase 1
- `docs/reports/BROKER_GATEWAY_ARCHITECTURE_REVIEW_2026-06-06.md` — the 5 P0s of Phase 2
- `docs/reports/SIMULATION_REPLAY_BACKTEST_REVIEW_2026-06-06.md` — the 5 P0s of Phase 4
- `docs/reports/PLUGIN_MARKET_UTILS_REVIEW_2026-06-06.md` — the 6 P0s of Phase 3
- `docs/reports/TERMINAL_SCALABILITY_REVIEW_2026-06-06.md` — the 5 P0s of Phase 7
- `docs/reports/MULTI_ASSET_CLASS_REVIEW_2026-06-06.md` — the 5 P0s of Phase 5
