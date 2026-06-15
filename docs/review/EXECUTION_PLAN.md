# Trade-J Venkat Review — Execution Plan

## Overview
**Source:** `docs/review/VENKAT_ARCHITECTURAL_REVIEW.md`  
**Objective:** Fix all critical, high, and simplification findings from the Venkat review  
**Method:** Multi-agent parallel execution with Venkat-style code review at each gate  
**Target state:** Production-safe, simplified, deterministic, observable

---

## Phase 1: Critical Fixes
*Priority: P0 — Blocking production issues*

### 1.1 CandleAggregationService — Shared State Race
**Risk:** `currentCandles` ConcurrentHashMap accessed by ring buffer thread AND downstream drainer thread concurrently  
**Fix:** 
- Add re-entrancy guard: skip processing if the event is a CandleDeveloping/CandleClosed (these come from the drainer and should not re-enter candle aggregation)
- OR: use a single-threaded executor for candle aggregation
**Files:**
- `trading/strategy/src/main/java/com/tradej/strategy/service/CandleAggregationService.java`
- `runtime/disruptor/src/main/java/com/tradej/disruptor/DisruptorEventBus.java` (add guard)
**Tests:**
- `app/src/test/java/com/tradej/app/integration/TripleModePNLParityTest.java` (already tests determinism)
- `trading/strategy/src/test/java/com/tradej/strategy/service/CandleAggregationServiceComponentTest.java`

### 1.2 PortfolioEngine — Queue Drops Trading Events
**Risk:** `offer()` drops TradeOpened/TradeClosed silently when queue full → PnL divergence  
**Fix:**
- Already correct for ledger events: `put()` is used for TradeOpened/TradeClosed
- Increase `DEFAULT_QUEUE_CAPACITY` from 1024 to 4096
- Use `put()` for ALL event types (not just ledger events)  
  OR: increase queue size and add monitoring
**Files:**
- `trading/strategy/src/main/java/com/tradej/strategy/portfolio/PortfolioEngine.java`
**Tests:**
- `trading/strategy/src/test/java/com/tradej/strategy/portfolio/PortfolioEngineTest.java`
- `trading/strategy/src/test/java/com/tradej/strategy/portfolio/PortfolioEngineStressTest.java`

---

## Phase 2: High Priority Fixes
*Priority: P1 — Can cause silent data loss*

### 2.1 CandleDeveloping Should Bypass Dispatch
**Risk:** Downstream queue overflow from 3600 CandleDeveloping events/second  
**Fix:**
- `CandleDeveloping` events should NOT go through the subscriber dispatch path  
- They are intermediate events consumed by the graph runtime only  
- Add a filter in `AsyncDispatchHandler` that drops `CandleDeveloping` from dispatch  
  OR: Don't publish CandleDeveloping to the hotPath at all — only publish via graph node adjacency
**Files:**
- `runtime/disruptor/src/main/java/com/tradej/disruptor/config/AsyncDispatchHandler.java`
- `runtime/disruptor/src/main/java/com/tradej/disruptor/DisruptorEventBus.java`
**Tests:**
- `runtime/disruptor/src/test/java/com/tradej/disruptor/DisruptorGraphReplayParityTest.java`

### 2.2 Overflow Alerts
**Risk:** Queue overflows are silent (logged to dead letter queue, no alert)  
**Fix:**
- Wire `droppedEventCount` from downstream queue, dispatch queue, and portfolio queue into metrics (Micrometer)  
- Add `Hystrix`-style circuit breaker or WARN-level logs that are PAGER-worthy
**Files:**
- `runtime/disruptor/src/main/java/com/tradej/disruptor/DisruptorEventBus.java`
- `runtime/disruptor/src/main/java/com/tradej/disruptor/config/AsyncDispatchHandler.java`
- `app/src/main/java/com/tradej/app/config/RuntimeAndStartupConfiguration.java`

---

## Phase 3: Simplifications
*Priority: P2 — Reduce architecture complexity*

### 3.1 Delete `composition` Module — ✅ **COMPLETED**
**Reason:** Duplicates Spring wiring; CLI and tests use separate paths → configuration drift  
**Completed:**
- Moved `BrokerProfile`, `StorageProfile`, `RiskProfile`, `ScanProperties`, `ConfigLoader` to appropriate modules
- Moved `BrokerComposition` to `broker-gateway/wiring/`
- Deleted `FullComposition`, `PipelineComposition`, `DataComposition`, `ExecutionComposition`, `ClockComposition`
- Removed `composition/` directory and all build references

### 3.2 Delete `SimpleEventBus`
**Reason:** Not thread-safe, no ordering guarantees, drops events silently  
**Action:**
1. Find all references to `SimpleEventBus`  
2. Migrate to `DisruptorEventBus` with appropriate config (or use it only in tests)  
3. Delete `SimpleEventBus.java`
**Files:**
- `core/src/main/java/com/tradej/core/domain/event/SimpleEventBus.java`

### 3.3 Delete `ReplayClock`
**Reason:** Already delegates to `VirtualClock` (P1 fix); dead code  
**Action:**
1. Find all callers of `ReplayClock`  
2. Migrate to `VirtualClock`  
3. Delete `ReplayClock.java`
**Files:**
- `data/persistence/src/main/java/com/tradej/persistence/replay/ReplayClock.java`
- `app/src/main/java/com/tradej/app/config/DataConfiguration.java`
- `app/src/test/java/com/tradej/app/e2e/ReplayEngineEndToEndTest.java`

### 3.4 Merge `OrderEvent` Hierarchy
**Reason:** Order events split across `core.domain.oms` and `core.domain.event`  
**Action:**
1. Identify all event types in both packages  
2. Merge into single canonical hierarchy in `core.domain.oms`  
3. Update all references
**Files:** TBD — requires inventory of all OrderEvent types

### 3.5 Consolidate Experimental Modules

**Modules reviewed and deleted:**
- `nodes/trade-node-library` — ✅ **DELETED** (no active node implementations; zero external code references)
- NoOp classes — ✅ **DELETED** (NoOpBeans, NoOpGatewayTopicRouter, NoOpHistoricalBarRepository, NoOpInstitutionalScanEngine)

**Modules retained:**
- `pipeline/analytics/trade-analytics` — **KEPT** (has active code)
- `pipeline/platform/trade-pipeline-platform` — **KEPT** (has active code)
- `mcp-server` — **KEPT** (experimental, still under development)
- `broker-gateway` — **KEPT** (unified broker facade with active SPI)

---

## Phase 4: Validation & Review
*Priority: Required before merge*

### 4.1 Full Regression Suite
- `./gradlew :app:test` — all tests
- `TripleModePNLParityTest` — determinism certification
- `ReplayDeterminismCertificationTest` — replay determinism
- Architecture tests — module dependency enforcement

### 4.2 Venkat-Style Code Review
- Every change reviewed for:
  - Race conditions
  - Thread safety
  - Event ordering
  - Backward compatibility
  - Test coverage

---

## Agent Assignment Plan

| Workstream | Lead Agent | Review Agent | Parallelizable |
|-----------|------------|--------------|----------------|
| 1.1 CandleAggregation race | code-searcher + basher | code-reviewer-deepseek | Yes (with 1.2) |
| 1.2 PortfolioEngine queue | code-searcher + basher | code-reviewer-deepseek | Yes (with 1.1) |
| 2.1 CandleDeveloping dispatch | code-searcher + basher | code-reviewer-deepseek | After 1.1 |
| 2.2 Overflow alerts | code-searcher + basher | code-reviewer-deepseek | After 2.1 |
| 3.1 Delete composition | file-picker + basher | code-reviewer-deepseek | After Phase 2 |
| 3.2 Delete SimpleEventBus | file-picker + basher | code-reviewer-deepseek | Parallel with 3.1 |
| 3.3 Delete ReplayClock | file-picker + basher | code-reviewer-deepseek | Parallel with 3.1 |
| 3.5 Consolidate modules | file-picker + basher | code-reviewer-deepseek | After Phase 2 |
| 4.1 Full regression | basher | — | After all fixes |
| 4.2 Venkat review | code-reviewer-deepseek | — | After all fixes |
