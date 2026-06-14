# Trade-J Refactor — Multi-Agent Execution Plan (No-Regression)

> **Purpose:** Adopt the structural-assessment recommendations (Phases 1-8 of the prior analysis) using a **multi-agent team** so the work runs in parallel with **zero behaviour regression** and a **kill-switch for every phase**.
>
> **Scope:** 4 modules to refactor, 1 module to guard. 8 sequential phases, 4 of them safely parallelisable across agents.
>
> **Constraint honoured:** No code changes during planning. Plan file is the only file written.

---

## 1. Summary

- **Why:** The composition root and broker adapters have drifted from the otherwise-clean Hexagonal core. The smells are concentrated in 4 files (`BrokerConfiguration.java`, `BrokerStartupOrchestrator.java`, `PipelineConfiguration.java`, `DhanBrokerConnection.java`, `OrderStateMachine.java`).
- **What:** 8 behaviour-preserving refactors + 1 ArchUnit guard-rail suite. Each phase ships independently, behind a Spring-profile feature flag where applicable.
- **Who:** A 5-agent team (1 architect/coordinator + 4 executor agents) plus a parallel `search` subagent for impact analysis.
- **How:** Every phase is gated by a 4-layer safety net (targeted unit tests → ArchUnit → component tests → runtime certification scripts). Each phase has a documented `git revert` rollback.
- **Outcome:** ~700 lines of duplicated composition-root config collapse, 5 `ObjectProvider` Service-Locator fields removed, 1 hard-coded `LiveTradingClock` leak fixed, 3 redundant OMS test classes consolidated, and 4 new ArchUnit rules preventing recurrence.

---

## 2. Current State Analysis (verified during exploration)

| # | Smell | File | Lines | Verified? |
|---|---|---|---|---|
| S1 | Large Class, 6 inner `@Configuration`s | [BrokerConfiguration.java](file:///Users/apple/Downloads/Trade_J/app/src/main/java/com/tradej/app/config/BrokerConfiguration.java) | 738 | ✅ `wc -l` |
| S2 | Service Locator (`ObjectProvider`) | [BrokerStartupOrchestrator.java](file:///Users/apple/Downloads/Trade_J/app/src/main/java/com/tradej/app/startup/BrokerStartupOrchestrator.java) L84-89 | 5 fields | ✅ grep |
| S3 | Switch smell + dead cases | [OrderStateMachine.java](file:///Users/apple/Downloads/Trade_J/core/src/main/java/com/tradej/core/domain/oms/OrderStateMachine.java) L59-80 | 9 cases, 7 `ignored` | ✅ read |
| S4 | Hardcoded `LiveTradingClock` in adapter | [DhanBrokerConnection.java](file:///Users/apple/Downloads/Trade_J/broker/dhan/src/main/java/com/tradej/broker/dhan/DhanBrokerConnection.java) L250 | 1 leak | ✅ read |
| S5 | Long ctor (16 args) + legacy 3-arg ctor | [DhanBrokerConnection.java](file:///Users/apple/Downloads/Trade_J/broker/dhan/src/main/java/com/tradej/broker/dhan/DhanBrokerConnection.java) L99-135, L168-256 | 414 lines | ✅ read |
| S6 | Hardcoded node-type catalogue | [PipelineConfiguration.java](file:///Users/apple/Downloads/Trade_J/app/src/main/java/com/tradej/app/pipeline/PipelineConfiguration.java) L74-175 | 100 inline | ✅ read |
| S7 | 3 test classes for 1 SUT | [OrderStateMachinePropertyTest.java](file:///Users/apple/Downloads/Trade_J/core/src/test/java/com/tradej/core/domain/oms/OrderStateMachinePropertyTest.java), [OrderStateMachineTest.java](file:///Users/apple/Downloads/Trade_J/core/src/test/java/com/tradej/core/domain/oms/OrderStateMachineTest.java), [OrderStateMachineUnitTest.java](file:///Users/apple/Downloads/Trade_J/core/src/test/java/com/tradej/core/domain/oms/OrderStateMachineUnitTest.java) | 3 files | ✅ ls |
| S8 | `data-persistence` → `trading-scanner` leak | (known, in BACKLOG) | — | ✅ docs/ARCHITECTURE.md |

**Pre-existing safety net (confirmed by exploration):**

| Layer | File / Command | Status |
|---|---|---|
| Unit + component + frontend tests | [ci.yml L21](file:///Users/apple/Downloads/Trade_J/.github/workflows/ci.yml#L21) `./gradlew test componentTest :app:testFrontend` | ✅ live |
| ArchUnit suite (16 tests) | [architecture-test/](file:///Users/apple/Downloads/Trade_J/architecture-test/src/test/java/com/tradej/architecture/) + [ModuleDependencyTest.java](file:///Users/apple/Downloads/Trade_J/core/src/test/java/com/tradej/core/architecture/ModuleDependencyTest.java) | ✅ live |
| SpotBugs + Checkstyle | [ci.yml L24-35](file:///Users/apple/Downloads/Trade_J/.github/workflows/ci.yml#L24-L35) | ✅ live |
| Broker certification scripts (7 levels + 1 minus) | [scripts/certify-*.sh](file:///Users/apple/Downloads/Trade_J/scripts/) | ✅ available |
| Integration tests (conditional on parquet) | [ci.yml L41-50](file:///Users/apple/Downloads/Trade_J/.github/workflows/ci.yml#L41-L50) | ✅ live |
| `StartupDependencies` record (pre-existing) | [StartupDependencies.java](file:///Users/apple/Downloads/Trade_J/app/src/main/java/com/tradej/app/startup/StartupDependencies.java) | ✅ partially mitigates S2 |

---

## 3. Multi-Agent Team Structure

> All five agents work in **isolated git worktrees** so they cannot stomp on each other. The **Architect/Coordinator** owns the merge queue and CI gate. The `search` subagent is spawned on-demand for impact analysis.

### 3.1 Roster

| Role | Subagent Type | Owns | Authority |
|---|---|---|---|
| **A0 — Architect / Coordinator** | `trade-orchestrator` | Sequencing, gate decisions, inter-phase dependencies, final merge | Holds the **kill switch** (Spring profile `refactor.safety.use-legacy=true`) |
| **A1 — Composition Root Agent** | `principal-trading-engineer` | Phase 1 (Broker config), Phase 2 (Startup), Phase 3 (Pipeline node catalogue) | PRs in `app/`, `composition/`, `pipeline/` |
| **A2 — Broker Adapter Agent** | `broker-integration-engineer` | Phase 4 (legacy ctor), Phase 5 (clock leak) | PRs in `broker/dhan/`, `broker/api/` |
| **A3 — Domain / OMS Agent** | `principal-trading-engineer` | Phase 6 (state machine), Phase 7 (persistence ↔ scanner leak) | PRs in `core/`, `data/persistence/`, `trading/scanner/` |
| **A4 — Architecture Guard Agent** | `trading-systems-architect` | Phase 0 (baseline) + Phase 8 (ArchUnit) | PRs in `architecture-test/` only |
| **A5 — Search / Impact Subagent** | `search` (spawned on-demand) | Blast-radius analysis before each PR | Read-only |

### 3.2 Coordination Protocol

1. **Sequencer:** A0 publishes a per-week phase order (e.g. *"Week-1: A1→P1, A2→P4-prep, A3→P6-prep, A4→P0-baseline"*).
2. **Branch layout:** `refactor/phase-N-short-name` per phase. A1/A2/A3 each push to their own. A4's branch is the gating merge target.
3. **CI gate:** Every PR must pass `./gradlew test componentTest :app:testFrontend :architecture-test:architectureTest checkstyleMain spotbugsMain` before A0 will review. A0 then runs the 7 `certify-level-*.sh` scripts locally as a smoke test.
4. **Merge cadence:** Each phase = 1 PR. A0 merges in sequence (P0 → P1 → P2 → P3 → P4 → P5 → P6 → P7 → P8).
5. **Conflict resolution:** If two PRs touch the same file, A0 serialises them. Agents do not rebase each other's branches.
6. **Communication:** A0 issues a daily "Phase Status" report. Agents report blockers, not progress.

### 3.3 Worktree Setup (executed at start of execution phase)

```bash
# In the execution phase (not now — Plan Mode forbids it).
git worktree add ../trade-j-a1 -b refactor/phase-1-broker-config-collapse
git worktree add ../trade-j-a2 -b refactor/phase-4-broker-ctor
git worktree add ../trade-j-a3 -b refactor/phase-6-oms-refactor
git worktree add ../trade-j-a4 -b refactor/phase-8-archunit-rules
```

---

## 4. Phased Implementation Plan

### Phase 0 — Baseline Safety Net  *(A4, blocking prerequisite)*

**Why first:** Every subsequent phase needs a known-good CI state. A4 captures the "golden" output of every test, linter, and certification script. This becomes the comparator.

**Files touched (exhaustive):**
- [architecture-test/](file:///Users/apple/Downloads/Trade_J/architecture-test/) — add a `BaselineSnapshotTest` that records current ArchUnit findings count
- [docs/migration/PHASE0_BASELINE.md](file:///Users/apple/Downloads/Trade_J/docs/migration/) — new file with baseline numbers (test count, line counts, SpotBugs warnings, Checkstyle violations)

**Tasks (A4):**
1. Run `./gradlew test componentTest :app:testFrontend :architecture-test:architectureTest` on `main` and capture:
   - Total tests / pass / fail
   - Per-module test counts
   - ArchUnit rule count + finding count
   - SpotBugs warning count
   - Checkstyle violation count
2. Capture line counts of the 5 "hot" files.
3. Run all 7 `certify-level-*.sh` scripts (skipped if brokers not configured, but capture exit codes).
4. Save baseline to `docs/migration/PHASE0_BASELINE.md`.

**Acceptance criteria:**
- All tests pass on `main`.
- Baseline doc checked in.
- A0 signs off.

**No-regression gate:** Future phases must show **identical or better** numbers (test count, line counts).

---

### Phase 1 — Collapse Broker Configuration  *(A1, after P0)*

**Target:** [BrokerConfiguration.java](file:///Users/apple/Downloads/Trade_J/app/src/main/java/com/tradej/app/config/BrokerConfiguration.java) (738 lines).

**Why:** Three near-identical inner `@Configuration` classes (Dhan / Icici / Simulation) wire the same 9 port beans. Plus a `GatewayWebSocketConfiguration` that mixes WebSocket concerns.

**Tasks (A1):**
1. Create `app/src/main/java/com/tradej/app/config/broker/BrokerPortsRegistrar.java` — a single helper that takes `IBrokerConnection` and `MeterRegistry` + an observability tag and registers all 9 port beans.
2. Create `app/src/main/java/com/tradej/app/config/broker/DhanAdapterConfig.java`, `IciciAdapterConfig.java`, `SimulationAdapterConfig.java` — each ~40 lines, calling `BrokerPortsRegistrar`.
3. Create `app/src/main/java/com/tradej/app/config/broker/GatewayWebSocketConfig.java` — move `GatewayWebSocketConfiguration` out.
4. Delete the inner classes from `BrokerConfiguration.java`. Result: ~100 lines, only broker-agnostic + `GatewayConfig` (multi-broker) + `GatewayBeansConfig` (gateway beans).
5. **No bean name or type changes.** All existing injection points continue to resolve.

**Feature flag:** `trade.refactor.phase1.collapsed-broker-config=true` (default `true`). When `false`, the old inner classes are kept on the classpath for one release. The flag is read in `BrokerConfiguration` to switch which file's @Configuration gets loaded.

**Acceptance criteria:**
- `BrokerConfiguration.java` < 200 lines.
- All bean names preserved (verify with `grep` of `@Bean` annotations vs. consumer `@Qualifier` / `@Autowired`).
- `IBrokerConnectionContractTest`, `BrokerStartupOrchestrator` smoke test pass.
- `./scripts/certify-level-1.sh` and `certify-level-2.sh` still pass.

**Rollback:** Set flag to `false`. Single-line revert. Or `git revert <merge-sha>`.

**Effort:** 1 engineer-day.

---

### Phase 2 — Remove Service Locator from Startup  *(A1, after P1)*

**Target:** [BrokerStartupOrchestrator.java](file:///Users/apple/Downloads/Trade_J/app/src/main/java/com/tradej/app/startup/BrokerStartupOrchestrator.java) L84-89.

**Why:** 5 `ObjectProvider<T>` parameters = Service Locator. A clean `StartupContext` sealed interface removes the indirection. (`StartupDependencies` record already exists — this phase extends that pattern.)

**Tasks (A1):**
1. Add `StartupContext` sealed interface in `app/.../startup/`:
   ```text
   sealed interface StartupContext permits LiveContext, AnalyticsOnlyContext, GatewayContext {}
   record LiveContext(DhanTokenProvider, BreezeTokenProvider) implements StartupContext {}
   record AnalyticsOnlyContext() implements StartupContext {}
   record GatewayContext(RuntimeSubscriptionManager, SubscriptionCoordinator) implements StartupContext {}
   ```
2. Move the 5 `ObjectProvider` fields into the appropriate context variant. (Most call sites — Dhan, Icici — become `LiveContext`.)
3. Change `runStartup(StartupDependencies, 5×ObjectProvider)` → `runStartup(StartupDependencies, StartupContext)`.
4. Update the caller in the composition root to construct the right context based on `BrokerTransportProfile`.

**Feature flag:** `trade.refactor.phase2.typed-startup=true` (default `true`). When `false`, a shim wraps the old `ObjectProvider` calls.

**Acceptance criteria:**
- Zero `ObjectProvider` imports in `BrokerStartupOrchestrator.java`.
- `runStartup` has 2 parameters.
- `certify-level-0.sh` through `certify-level-3.sh` all pass.

**Rollback:** Flag = `false`.

**Effort:** 0.5 engineer-day.

---

### Phase 3 — SPI-ify the Pipeline Node Catalogue  *(A1, after P2)*

**Target:** [PipelineConfiguration.java](file:///Users/apple/Downloads/Trade_J/app/src/main/java/com/tradej/app/pipeline/PipelineConfiguration.java) L74-175.

**Why:** Adding a new node type requires editing the composition root. A SPI (`NodeTypeProvider`) lets plugins register node metadata without touching `app/`.

**Tasks (A1):**
1. Define `NodeTypeProvider` interface in `pipeline-core/src/main/java/com/tradej/pipeline/spi/NodeTypeProvider.java` — `void registerMetadata(NodeRegistry registry)`.
2. Create `DefaultNodeTypeCatalogue implements NodeTypeProvider` in `pipeline-core`, containing the 7 hardcoded descriptors from `PipelineConfiguration` (Ingress, Risk, Candle, Strategy, OMS, Reactor, Scan, ScanCriterion, ScanAggregator).
3. Register `META-INF/services/com.tradej.pipeline.spi.NodeTypeProvider` in `pipeline-core/src/main/resources/`.
4. Replace the 100-line inline catalog in `PipelineConfiguration.nodeRegistry()` with a 5-line SPI call.
5. The `descriptor(...)` helper moves to `DefaultNodeTypeCatalogue`.

**Feature flag:** None needed — pure SPI expansion. New nodes can be added via META-INF, existing code path unchanged.

**Acceptance criteria:**
- `PipelineConfiguration.nodeRegistry()` < 30 lines.
- All `pipeline-*` tests pass (especially `NodeRegistryTest` if present).
- Frontend palette still sees the same 9 node types (verify via `:app:testFrontend`).

**Rollback:** `git revert`. SPI is additive.

**Effort:** 1 engineer-day.

---

### Phase 4 — Retire `DhanBrokerConnection` Legacy Constructor  *(A2, parallelisable with P3)*

**Target:** [DhanBrokerConnection.java](file:///Users/apple/Downloads/Trade_J/broker/dhan/src/main/java/com/tradej/broker/dhan/DhanBrokerConnection.java) L168-256.

**Why:** The 3-arg legacy ctor builds 14 internal collaborators inline, leaks `LiveTradingClock` (S4), and is a footgun for test fixtures.

**Tasks (A2):**
1. **Pre-step (A5 search):** Enumerate every test file that calls the 3-arg ctor or `create(...)`. Output: list of files + test method names.
2. Create `broker/dhan/.../adapter/factory/DhanAdapterBundle.java` — a record that holds all 14 constructed clients.
3. Create `broker/dhan/.../adapter/factory/DhanAdapterBundleFactory.java` — the factory that builds the bundle from `DhanConnectionSettings`, `MultiBucketRateLimiter`, `IdempotencyCachePort`. This is the only place that imports `LiveTradingClock`.
4. Update `DhanBrokerConnection` to accept `DhanAdapterBundle` instead of `(settings, rateLimiter, idempotency)`.
5. Migrate each test identified in step 1 to the new constructor. Add a `@SuppressWarnings("deprecation")` if any legacy ctor must be temporarily retained.
6. **After all tests migrated,** delete the `@Deprecated` 3-arg ctor and the `create(...)` static factory.

**Feature flag:** `trade.refactor.phase4.dhan-bundle=true` (default `true`).

**Acceptance criteria:**
- Zero `@Deprecated` constructors in `DhanBrokerConnection`.
- All `DhanBrokerConnection*Test*` files pass.
- `certify-level-0.sh` (which exercises Dhan auth) passes.
- Diff: `DhanBrokerConnection.java` < 250 lines.

**Rollback:** Flag = `false` retains the old path. Or revert the merge.

**Effort:** 2 engineer-days (one for migration, one for ctor deletion).

---

### Phase 5 — Stop Domain-Clock Leak in Dhan Adapter  *(A2, after P4)*

**Target:** [DhanBrokerConnection.java L250](file:///Users/apple/Downloads/Trade_J/broker/dhan/src/main/java/com/tradej/broker/dhan/DhanBrokerConnection.java#L246-L253).

**Why:** Adapter hardcodes `new LiveTradingClock()`. Clock should be a port.

**Tasks (A2):**
1. Define `Clock` port in `broker/api/.../port/BrokerClock.java` (or reuse `com.tradej.core.domain.time.TradingClock` if ArchUnit allows the dependency — see Phase 8).
2. Add `BrokerClock` parameter to `DhanWebSocketMultiplexer` constructor.
3. Inject `BrokerClock` from the composition root (use the existing `LiveTradingClock` bean).
4. Delete the `new LiveTradingClock()` call from `DhanBrokerConnection`.

**Note:** This phase may merge into Phase 4's `DhanAdapterBundleFactory` to avoid touching the ctor twice.

**Feature flag:** None.

**Acceptance criteria:**
- Zero references to `LiveTradingClock` from `broker/dhan/`.
- ArchUnit rule (added in Phase 8) passes.

**Rollback:** Single commit revert.

**Effort:** 0.5 engineer-day (often folded into P4).

---

### Phase 6 — Refactor `OrderStateMachine`  *(A3, parallelisable with P3)*

**Target:** [OrderStateMachine.java L58-80](file:///Users/apple/Downloads/Trade_J/core/src/main/java/com/tradej/core/domain/oms/OrderStateMachine.java#L59-L80).

**Why:** Switch with 7 `ignored` cases is a smell. Fill-accumulation logic should be a strategy.

**Tasks (A3):**
1. Create `core/.../oms/FillAccumulator.java` — a record that takes `OrderPartiallyFilled`/`OrderFullyFilled` and returns `(filledQuantity, accumulatedValuePaisa)`.
2. Replace the inline switch in `OrderStateMachine.on()` with a call to `FillAccumulator`.
3. Delete the 7 `ignored -> {}` cases; pattern match exhaustiveness is enforced by the compiler.
4. Consolidate the 3 test classes:
   - Keep [OrderStateMachineTest.java](file:///Users/apple/Downloads/Trade_J/core/src/test/java/com/tradej/core/domain/oms/OrderStateMachineTest.java) (most comprehensive).
   - Keep [OrderStateMachinePropertyTest.java](file:///Users/apple/Downloads/Trade_J/core/src/test/java/com/tradej/core/domain/oms/OrderStateMachinePropertyTest.java) (property-based, high value).
   - Delete [OrderStateMachineUnitTest.java](file:///Users/apple/Downloads/Trade_J/core/src/test/java/com/tradej/core/domain/oms/OrderStateMachineUnitTest.java) (lowest coverage, redundant).
5. Add `FillAccumulatorTest`.

**Feature flag:** None — pure internal refactor, no behaviour change.

**Acceptance criteria:**
- State-transition table is byte-identical (diff `buildTransitions()` before/after).
- `OrderStateMachinePropertyTest` (the strongest) still passes 100% of generated cases.
- 2 test files instead of 3.

**Rollback:** `git revert`.

**Effort:** 0.5 engineer-day.

---

### Phase 7 — Decouple `data-persistence` from `trading-scanner`  *(A3, after P6)*

**Target:** Known dependency leak in [docs/ARCHITECTURE.md](file:///Users/apple/Downloads/Trade_J/docs/ARCHITECTURE.md) (line 62: "Known coupling: `:data-persistence` → `:trading-scanner`").

**Why:** Persistence should not depend on scanner (the *consumer* of scan results).

**Tasks (A3):**
1. **A5 search:** Enumerate all uses of `com.tradej.scanner.*` from `data/persistence/`.
2. Move shared DTOs (e.g. `ScanRun`, `ScanHit`, `ScanResult`) into `core/.../scan/`.
3. Update `data/persistence/` imports to use `com.tradej.core.scan.*`.
4. Confirm `trading-scanner` still re-exports or imports from the new path.

**Feature flag:** None.

**Acceptance criteria:**
- Zero imports of `com.tradej.scanner.*` in `data/persistence/`.
- `:data-persistence:test` passes.
- ArchUnit rule (Phase 8) passes.

**Rollback:** `git revert`.

**Effort:** 1 engineer-day.

---

### Phase 8 — ArchUnit Guard Rails  *(A4, final phase)*

**Target:** [architecture-test/](file:///Users/apple/Downloads/Trade_J/architecture-test/src/test/java/com/tradej/architecture/) — add 4 new test classes.

**Why:** Without enforcement, the smells will reappear in 6 months.

**Tasks (A4):**
1. **Rule 1 — No Domain Clocks in Adapters:**
   - `noClasses().that().resideInAPackage("com.tradej.broker.dhan..").should().dependOnClassesThat().haveFullyQualifiedName("com.tradej.core.domain.time.LiveTradingClock")`
2. **Rule 2 — No `ObjectProvider` in Composition Root Orchestrators:**
   - `noClasses().that().resideInAPackage("com.tradej.app.startup..").should().dependOnClassesThat().haveFullyQualifiedName("org.springframework.beans.factory.ObjectProvider")`
3. **Rule 3 — No `trading-scanner` import from `data-persistence`:**
   - `noClasses().that().resideInAPackage("com.tradej.persistence..").should().dependOnClassesThat().resideInAPackage("com.tradej.scanner..")`
4. **Rule 4 — `app/config` may not contain broker-specific @Configuration classes:**
   - Restrict `com.tradej.app.config..` to not have classes with `@Bean` methods that return `com.tradej.broker.dhan.*` types. (Use `@AnalyzeClasses` packages + `DescribedPredicate` on class name + member annotation scanning.)

Add to [ModuleDependencyTest.java](file:///Users/apple/Downloads/Trade_J/core/src/test/java/com/tradej/core/architecture/ModuleDependencyTest.java) or a new file under `architecture-test/`.

**Feature flag:** None — these tests are always-on in CI.

**Acceptance criteria:**
- 4 new ArchUnit tests pass on `main` after all prior phases.
- All 7 phases stay green with the new rules active.
- `:architecture-test:architectureTest` runs in <30s.

**Rollback:** `git revert` the test class. Rules are additive.

**Effort:** 0.5 engineer-day.

---

## 5. Anti-Regression Strategy (4 Layers)

The CI pipeline already provides layers 1 and 2. We extend layers 3-4.

| Layer | Tool | Owner | What it catches |
|---|---|---|---|
| **L1 — Targeted unit tests** | `./gradlew :<module>:test` (run by A1/A2/A3 in their worktree) | Each agent | Functional regressions in the changed module |
| **L2 — Full test pyramid + frontend** | `./gradlew test componentTest :app:testFrontend` ([ci.yml L21](file:///Users/apple/Downloads/Trade_J/.github/workflows/ci.yml#L21)) | CI | Cross-module regression |
| **L3 — ArchUnit guard rails** | `./gradlew :architecture-test:architectureTest` ([ci.yml L23](file:///Users/apple/Downloads/Trade_J/.github/workflows/ci.yml#L23)) + new rules from P8 | A4 / CI | Boundary violations, dependency leaks |
| **L4 — Runtime certification** | `./scripts/certify-level-{0,1,2,3,4,5,6,minus1}.sh` (run by A0 before merge) | A0 | End-to-end broker wiring, OMS recovery, scanner activation |

**Per-phase gating rules (enforced by A0 before merge):**
- L1: All targeted tests in the changed module green.
- L2: Full `./gradlew test componentTest :app:testFrontend` green.
- L3: ArchUnit count delta from P0 baseline = 0 (or negative for the 4 new rules).
- L4: At least the certification scripts relevant to the phase pass. (P1→1,2; P2→0,1,2,3; P3→frontend only; P4→0; P5→0; P6→none; P7→none; P8→none.)
- **SpotBugs + Checkstyle:** No new warnings introduced.
- **Line counts:** Reduced or equal (never increased for the 5 hot files).

**Pre-merge checklist (A0 fills in for every PR):**
- [ ] Baseline numbers compared (test count, line count, ArchUnit count, SpotBugs, Checkstyle).
- [ ] Certify scripts run, output attached to PR.
- [ ] Feature flag exists and defaults to "old behaviour" until P0 baseline phase ships.
- [ ] Rollback command documented in PR description.
- [ ] A4 signs off on ArchUnit impact.

---

## 6. Risk Register

| ID | Risk | Likelihood | Impact | Mitigation | Owner |
|---|---|---|---|---|---|
| R1 | Spring bean name collision after broker config collapse | Medium | High (boot failure) | A5 search for all `@Qualifier` references; A0 manually verifies | A1 |
| R2 | `ObjectProvider` removal breaks a profile that the test suite doesn't cover (e.g. `upstox-analytics`) | Medium | High (boot failure) | A5 search for profile names; run with `-Dspring.profiles.active=upstox-analytics`; feature flag retains old path | A1 |
| R3 | `DhanAdapterBundle` migration misses a test that constructs via the 3-arg ctor | High | Medium (test failure) | A5 enumerates ALL usages in pre-step; A0 verifies zero `@Deprecated` ctor calls before deletion | A2 |
| R4 | ArchUnit rule 4 (no Dhan beans in `app.config`) blocks legitimate use | Low | Medium (false positive) | Scope rule to specific class names; whitelist if needed | A4 |
| R5 | Agent A1 and A2 race on `broker/api/.../port/BrokerClock` if A2 starts before A1's `BrokerPortsRegistrar` is merged | Low | Low (compile error) | A0 serialises: A1 P1 → A1 P2 → A2 P4 (with P5 folded in) | A0 |
| R6 | `OrderStateMachine` refactor changes VWAP rounding | Low | High (financial) | Property-based test in `OrderStateMachinePropertyTest` is the safety net; A0 runs it 100× locally before merge | A3 |
| R7 | Persistence → scanner refactor breaks runtime scan storage | Low | High (production) | A0 runs `certify-level-2.sh` and `certify-level-3.sh` before merge | A3 |
| R8 | CI environment differs from agent worktree (Java 21 vs local) | Medium | Medium | All agents use the same `gradle-wrapper`; A0 verifies CI green on PR before merge | A0 |

---

## 7. Sequencing & Timeline

| Week | A0 (Coordinator) | A1 (Composition) | A2 (Broker) | A3 (Domain) | A4 (Guard) |
|---|---|---|---|---|---|
| W1 | Publish P0/P1/P2 brief; set up worktrees | (idle) | (idle) | (idle) | **P0 — baseline** |
| W2 | Gate P0 merge; sign off | **P1 — broker config** | (idle) | (idle) | (review ArchUnit) |
| W3 | Gate P1 merge | **P2 — startup** | **P4 prep — A5 search** | **P6 prep — write FillAccumulator in worktree** | (review) |
| W4 | Gate P2, P3 merge | **P3 — SPI node catalogue** | **P4 — Dhan legacy ctor** | **P6 — state machine** | (review) |
| W5 | Gate P4, P5, P6 merge | (idle) | **P5 — clock leak** | **P7 — persistence/scanner** | (review) |
| W6 | Gate P7 merge | (idle) | (idle) | (idle) | **P8 — ArchUnit rules** |
| W7 | Final cert sweep; sign off | — | — | — | (review) |

**Critical path:** A0 → A1 → A1 → A1 → A4 (6 weeks end-to-end).
**Parallelism:** A2 (W3-W5) and A3 (W3-W5) run in parallel with A1.

---

## 8. Assumptions & Decisions

| # | Assumption / Decision | Rationale | Reversibility |
|---|---|---|---|
| D1 | The `StartupDependencies` record is the right shape; we extend, not replace | It's already in production and removes 16 of 21 fields | Reversible |
| D2 | `LiveTradingClock` is the only domain clock; we add a `BrokerClock` port to `broker/api` | Cleanest port boundary; ArchUnit will enforce it | Reversible |
| D3 | The 3 `OrderStateMachine*Test*` files are redundant; we keep 2 | Inspection shows Test+PropertyTest are comprehensive; UnitTest is subset | Reversible (re-add the file) |
| D4 | `certify-level-*.sh` scripts are a sufficient runtime smoke test | They exist, are shell-runnable, and exercise the real broker paths | N/A (existing) |
| D5 | Spring feature flags (`trade.refactor.phaseN.*`) are the kill switch, not git branches | Lets us ship "both paths" for one release; instant rollback in production | Reversible |
| D6 | A0 (Architect) is a single human, not a subagent, during merge windows | Architectural judgement needed at gate decisions; subagents execute, humans decide | N/A |
| D7 | No backend protocol/wire changes — this is **internal refactor only** | All API DTOs, event schemas, OMS state codes are preserved byte-for-byte | N/A |
| D8 | Frontend is unaffected (no breaking DTO changes) | Confirmed by inspection of `app/.../api/dto/*Response.java` | N/A |

---

## 9. Verification Steps (post-implementation)

Run these commands in order from the repository root. Each must succeed.

```bash
# 1. Compile and unit/component tests
./gradlew clean test componentTest :app:testFrontend --no-daemon

# 2. ArchUnit (including the 4 new rules from P8)
./gradlew :architecture-test:architectureTest --no-daemon

# 3. Static analysis (no new warnings)
./gradlew checkstyleMain spotbugsMain --no-daemon

# 4. Build the boot jar to confirm wiring
./gradlew :app:bootJar --no-daemon

# 5. Runtime certification (in order)
for level in minus1 0 1 2 3 4 5 6; do
  ./scripts/certify-level-${level}.sh || echo "FAIL: level ${level}"
done

# 6. Smoke test broker endpoints
./scripts/dhan-smoke.sh || echo "Dhan smoke not configured (expected in CI)"
./scripts/upstox-smoke.sh || echo "Upstox smoke not configured (expected in CI)"
./scripts/console-smoke.sh
```

**Success criteria (compared to P0 baseline):**
- Total test count: **≥** baseline
- ArchUnit finding count: **≤** baseline (minus 0 from P8's 4 new rules = 4 new tests, 0 new violations)
- SpotBugs warnings: **≤** baseline
- Checkstyle violations: **≤** baseline
- `BrokerConfiguration.java` line count: **< 200** (from 738)
- `DhanBrokerConnection.java` line count: **< 250** (from 414)
- `PipelineConfiguration.nodeRegistry()` method: **< 30** lines (from ~100)
- `BrokerStartupOrchestrator.runStartup()` parameters: **2** (from 6)
- `OrderStateMachineTest*` file count: **2** (from 3)

---

## 10. Appendix — Multi-Agent Invocation Templates

When execution begins, A0 will issue the following prompts to each agent. These are intentionally concise — the plan above is the spec.

### 10.1 To A4 (Phase 0)

> *"Read `docs/migration/PHASE0_BASELINE.md` (to be created). Run the four gradle commands listed in §9 step 1-3 against the `main` branch. Capture test counts, ArchUnit findings, SpotBugs warnings, Checkstyle violations, and the 5 hot-file line counts. Commit the baseline doc. Do not modify any production code."*

### 10.2 To A1 (Phase 1)

> *"Read §Phase-1 of the plan. Refactor `BrokerConfiguration.java` so the 6 inner @Configuration classes become top-level classes in `app/.../config/broker/`. Add a `BrokerPortsRegistrar` helper. Preserve every bean name and type. Add the `trade.refactor.phase1.collapsed-broker-config` flag, default `true`. Run `./gradlew :app:test :architecture-test:architectureTest` and `./scripts/certify-level-1.sh`. Report line counts and any test failures."*

### 10.3 To A2 (Phase 4)

> *"Read §Phase-4 of the plan. First, run `search` for every call site of `DhanBrokerConnection(...)` with 3 args or `DhanBrokerConnection.create(...)`. List the files. Then create `DhanAdapterBundle` + `DhanAdapterBundleFactory`. Migrate each test. Delete the `@Deprecated` ctor. Run `./gradlew :broker-dhan:test`. Report new line count."*

### 10.4 To A3 (Phase 6)

> *"Read §Phase-6 of the plan. Extract `FillAccumulator` from `OrderStateMachine.on()`. Keep the transition table byte-identical. Delete `OrderStateMachineUnitTest.java`. Run `./gradlew :core:test --tests '*OrderStateMachine*' --rerun-tasks` 100 times (loop in a shell) to confirm property-based stability. Report any failure."*

### 10.5 To A4 (Phase 8)

> *"Read §Phase-8 of the plan. Add 4 ArchUnit rules to `architecture-test/`. Verify all 4 pass on `main` (the smell may already be present — if so, your new rule WILL fail; that's expected; the previous phases will have already removed the smell, and the rule is forward-looking). Confirm `./gradlew :architecture-test:architectureTest` runs in under 30s."*

---

## 11. Sign-Off

This plan is **decision-complete** for the executor. The only remaining decisions are:
- Whether to fold Phase 5 into Phase 4 (recommended) — defer to A0.
- Whether to delete `OrderStateMachineUnitTest.java` outright or `@Disabled` it for one release — defer to A3 (default: delete).

**Ready for A0 to begin Phase 0 on user approval.**
