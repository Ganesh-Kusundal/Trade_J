# Broker Coverage & Code Review Action Plan

## Phase 1 — Infrastructure (Immediate)

| # | Task | Module | Impact |
|---|------|--------|--------|
| 1.1 | **Add JaCoCo plugin** to root `build.gradle` with per-subproject HTML/XML reports | root | 🔴 Stop guessing coverage |
| 1.2 | **Add broker-api TCK fixtures** (`AbstractBrokerConnectionTest`) so every broker proves contract compliance | `broker-api` | 🔴 Contract enforcement |

## Phase 2 — Critical Bugs (Memory Leaks & Silent Failures)

| # | Task | Module | Impact |
|---|------|--------|--------|
| 2.1 | **Fix `DhanOrderCommandAdapter.correlationLocks` leak** — remove entries after `placeOrder` completes | `broker-dhan` | 🔴 OOM risk |
| 2.2 | **Fix `IciciOrderCommandAdapter.orderExchangeCache` leak** — TTL eviction or remove on terminal states | `broker-icici` | 🔴 OOM risk |
| 2.3 | **Fix `IciciOrderCommandAdapter.cancelAllOpenOrders()` no-op** — either implement or throw `UnsupportedOperationException` | `broker-icici` | 🔴 Silent safety failure |
| 2.4 | **Refactor `UpstoxTokenManager.performInteractiveOAuth`** — inject `BrowserLauncher` instead of `System.out` + `Desktop` | `broker-upstox` | 🔴 Headless env failure |

## Phase 3 — Facade & Adapter Tests

| # | Task | Module | Impact |
|---|------|--------|--------|
| 3.1 | **Add `DhanBrokerConnectionTest`** — constructor null-guard, capability discovery, `loadInstrumentCatalog` delegation | `broker-dhan` | 🟡 Facade coverage |
| 3.2 | **Add `IciciBrokerConnectionTest`** — constructor null-guard, capability discovery, `getCapability` exhaustive checks | `broker-icici` | 🟡 Facade coverage |
| 3.3 | **Add `UpstoxBrokerConnectionTest`** — constructor null-guard, capability discovery, null futuresProvider handling | `broker-upstox` | 🟡 Facade coverage |
| 3.4 | **Add `DefaultTokenLifecycleService` unit tests** — happy-path acquire, refresh, expiry boundary, store failure | `broker-core` | 🔴 Auth correctness |

## Phase 4 — Error-Path & Integration Tests

| # | Task | Module | Impact |
|---|------|--------|--------|
| 4.1 | **Add HTTP-failure fixtures** for each broker's REST client (401, 500, malformed JSON) | all brokers | 🟡 Resilience |
| 4.2 | **Add WebSocket disconnect tests** without reflection (use package-visible seams or test doubles) | all brokers | 🟡 Reconnect confidence |
| 4.3 | **Move benchmarks** out of `src/test/java` into `src/jmh/java` or dedicated source set | `broker-core` | 🟢 CI speed |

## Phase 5 — Cleanup

| # | Task | Module | Impact |
|---|------|--------|--------|
| 5.1 | **Delete or populate `broker-common`** | `broker-common` | 🟢 Build hygiene |
| 5.2 | **Reduce reflection in tests** — convert private-field pokes to constructor injection or package-visible accessors | all brokers | 🟢 Maintainability |
| 5.3 | **Enable SpotBugs + Checkstyle in CI** — ensure reports are generated and archived | root | 🟢 Static analysis |

---

## Success Criteria
- JaCoCo reports show >60% line coverage for each broker module
- Zero memory-leak warnings in broker adapter code
- Each broker connection facade has ≥1 dedicated test class
- `broker-api` has TCK fixtures that each broker extends
