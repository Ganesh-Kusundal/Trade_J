# Trade-J — Remediation Tracking Document

> **Based on:** Production Readiness Certification Review (2026-06-15)
> **Status:** 5 blocking issues fixed ✅ | 5 should-fix items tracked below

---

## ✅ Blocking Issues — Fixed

| # | Issue | Fix | Status |
|---|-------|-----|--------|
| #1 | `runtime.bus` defaulted to SIMPLE | Changed to DISRUPTOR in all configs, RuntimeBus, RuntimeBusHolder | ✅ Fixed |
| #2 | Margin enforcement disabled | Set `enforce-margin: true` by default | ✅ Fixed |
| #3 | Two circuit breaker implementations | Verified: TradingCircuitBreaker (execution) and CircuitBreaker (broker HTTP) serve different layers. BrokerHealthIndicator correctly monitors execution breaker. | ✅ Documented |
| #4 | Reconciliation didn't halt trading | Set `auto-halt: true`. ReconciliationAlertLogger already wired to publish ReconciliationHaltRequired. KillSwitchRiskCheck already checks reconciliationHaltActive. | ✅ Fixed |
| #5 | No paging on queue saturation | Added setAlertCallback() to DisruptorEventBus. Wired AlertManager from ObservabilityConfiguration. Triggers on downstream queue full, re-entrant queue full, 75% capacity. | ✅ Fixed |

---

## 🟡 Should-Fix — Tracking

### #6: ICICI Breeze Browser Session Fragility

**Risk:** The headless browser session capture (`BreezeBrowserSessionCapture`) is inherently fragile. Browser automation can fail due to UI changes, network issues, or timeout.

**Detection:** `BreezeTokenManager` exceptions are caught and logged. Health check goes DOWN.

**Mitigation Plan:**
1. [ ] Add manual session capture fallback — document `BreezeApiSessionRedirectServer` usage
2. [ ] Add a health check that validates the session is usable (not just not-null) by calling a lightweight API endpoint
3. [ ] Add session expiry monitoring — log warning 10 minutes before expected expiry
4. [ ] Consider adding TOTP-based auth as primary, browser capture as fallback only

**Priority:** MEDIUM (ICICI is the least-used broker; Dhan and Upstox are primary)

---

### #7: Parallel Event Hierarchies (Order Events)

**Risk:** Two parallel event hierarchies exist for order lifecycle:
- `com.tradej.core.domain.event.OrderRejected` — published on the event bus
- `com.tradej.core.domain.oms.OrderRejected` — drives the OrderStateMachine

Both represent the same business concept but with different fields and semantics. This creates confusion about which event to publish/handle.

**Current State:**
- `OrderStateMachine.on()` accepts `core.domain.oms.OrderEvent` variants
- Event bus publishers use `core.domain.event.OrderRejected`, etc.
- `ExecutionHandler` maps between them (e.g., `rejected.toOsmEvent(orderId)`)

**Recommendation:**
1. [ ] Document the dual-hierarchy design decision in ADR format
2. [ ] Consider consolidating in a future major version (breaking change to OSM)
3. [ ] Add a `@Deprecated` annotation on the OMS variants with guidance on which to use

**Priority:** LOW (working correctly, but confusing for new developers)

---

### #8: Chaos & Resilience Test Plan

**Risk:** No chaos testing exists. The system runs as a single JVM with no clustering, but broker API calls and WebSocket connections are external network calls subject to failure.

**Priority Scenarios (ordered by risk):**

| # | Scenario | Risk | Status |
|---|----------|------|--------|
| 1 | Broker WebSocket disconnect during active order | Lost fills, stale positions | Add to soak test |
| 2 | Broker API 5xx storm during order placement | Circuit breaker should open | Unit-testable with mock |
| 3 | DuckDB file locked/corrupted | Analytics failure, not hot-path | Add integration test |
| 4 | Chronicle queue disk full | DLQ/Audit writes fail silently | Add integration test |
| 5 | Network partition between app and broker | Orders may be placed but fills lost | Add to soak test |

**Implementation Plan:**
1. [ ] Add `BrokerApiChaosTest` — inject latency/failure into broker mock
2. [ ] Add `ChronicleFullDiskTest` — fill disk, verify graceful degradation
3. [ ] Add `WebSocketKillMidTradeTest` — disconnect WS, verify reconciliation catches up
4. [ ] Run 8-hour broker sandbox soak with periodic fault injection

**Priority:** HIGH (required for production certification sign-off)

---

### #9: Chronicle Queue Retention

**Risk:** Chronicle audit logs and dead letter queues grow unbounded. Disk exhaustion is a real risk for long-running instances.

**Current State:**
- `ChronicleAuditLogWriter` writes all events — no cleanup
- `ChronicleDeadLetterQueue` captures dropped events — `appendCount` tracked but no cleanup

**Fix Implemented:**
- ✅ Added `cleanupOldFiles(retentionDays)` to both `ChronicleAuditLogWriter` and `ChronicleDeadLetterQueue`
- ✅ Added `@Scheduled` cleanup task in `DataConfiguration` (daily at 03:00)
- ✅ Configurable retention via `CHRONICLE_RETENTION_DAYS` env var (default: 30 days)

**Remaining:**
- [ ] Add Prometheus gauge for `ChronicleDeadLetterQueue.appendCount()`
- [ ] Add alert on DLQ growth rate exceeding threshold
- [ ] Consider Chronicle Queue's built-in `StoreFileListener` for automatic roll-based cleanup

**Priority:** MEDIUM (fixed with scheduled cleanup, but needs monitoring)

---

### #10: Frontend Architecture Status

**Risk:** The frontend is archived/removed. The ARCHITECTURE_AUDIT.md documents significant architectural issues in the original React frontend (20+ useState hooks, 5 independent polling intervals, no canonical data flow).

**Current State:**
- Frontend source files in `docs/archive/frontend/`
- `frontend/` directory exists but only contains `.vite/` cache
- WebSocket gateway (`/ws/gateway`) is still active for any connected clients
- Console assets are synced into `:app` via `syncFrontend` Gradle task

**Target Architecture (from ARCHITECTURE_AUDIT.md):**
1. Single `MarketDataBus` (event-driven, no polling)
2. Centralized Zustand store (MarketStore, BrokerStore, FeedStore)
3. Single `DataModeResolver`
4. Event contracts (TickEvent, DepthEvent, TradeEvent, CandleEvent)
5. Clear ownership matrix — each piece of data has exactly one owner

**Decision:**
- [ ] If frontend is revived, implement the canonical architecture from ARCHITECTURE_AUDIT.md
- [ ] If frontend remains archived, remove `syncFrontend` task and `frontend/` directory
- [ ] Document that the WebSocket gateway remains for programmatic/CLI consumers

**Priority:** LOW (frontend is not currently in use)

---

## Additional Improvements Implemented

### Observability Gaps Closed

| Gap | Fix | Status |
|-----|-----|--------|
| No downstream queue depth metric | Added `downstreamQueueDepth()` to `DisruptorBusMetrics` + Prometheus gauge | ✅ Fixed |
| No execution dropped fill metric | Added `execution.dropped.fill.count` Prometheus gauge | ✅ Fixed |
| Misleading bus mode metric | Changed `RuntimeBus` default to DISRUPTOR, added startup validation warning | ✅ Fixed |

---

## Deployment Checklist (Pre-Production)

- [ ] All 5 blocking issues verified fixed
- [ ] Chronicle retention tested (simulate old files, verify cleanup)
- [ ] Chaos test plan executed (at minimum: WebSocket kill + broker 5xx scenarios)
- [ ] 8-hour broker sandbox soak test passed
- [ ] AlertManager channels verified (Slack/PagerDuty webhook URLs configured)
- [ ] `CHRONICLE_RETENTION_DAYS` env var set in production
- [ ] Margin enforcement verified with broker sandbox
- [ ] Reconciliation auto-halt verified (simulate mismatch, verify halt)
