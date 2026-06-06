# P0 Test Coverage & Chaos Coverage Review
**Date:** 2026-06-06
**Scope:** Test surface, chaos coverage, opt-in test risk, live/sandbox boundary, mock vs real, and hidden coupling in the new Gateway/CLI/Market Utils architecture.
**Author persona:** Principal quant engineer — opinionated, file:line specific, brutally honest.
**Project:** Trade-J (24 Gradle subprojects, ~655 main classes, 213 tests)

---

## TL;DR (verdict)

You have **strong unit tests, nine real-thread stress tests, and ArchUnit enforcing module/Spring/profile boundaries** — that's the good news. The bad news is concentrated in the new architecture:

- The new `broker-gateway` module has **no tests for any of the actual hard problems** (routing, failover, route invariants, SPI discovery errors, result coercion). `BrokerGatewayTest` checks five trivial getters/setters.
- The new `gateway`, `market-utils`, and `cli` modules have **zero tests in their own modules** and are exercised only by `app`'s component tests — which means a breakage shows up as a Spring context failure, not a pointed diagnostic.
- Five of the seven P0 defects from `ARCHITECTURE_REVIEW_2026-06-06.md` (silent drops, dedup, kill-switch TOCTOU, cancel-before-guard, replay state reset) have **no regression test** at all.
- Chaos tests target the **old single-threaded surface** (handlers, registry, bus). The new fan-out paths (GatewayEventBridge, BrokerRouter, MarketGateway token bucket) have **no chaos tests**.
- `LiveDhanTestSupport` mixes LIVE and SANDBOX state in **JVM-wide static fields** — running these in parallel (`forkEvery 1`, CI matrix, or `--max-parallel-fork>1`) will cause real money-side state to leak into sandbox tests, including the order-cleanup list.

Fix order below. P0s first.

---

## P0 — Fix before any new broker traffic

### P0-1 — `LiveDhanTestSupport` is not parallel-safe and not test-isolated

**Where:** `app/src/test/java/com/tradej/app/integration/LiveDhanTestSupport.java:37-38, 159, 197-213`

Three JVM-wide static mutable fields:

```java
private static final Properties LIVE_PROPERTIES = loadProperties("config/dhan-local.properties");
private static final Properties SANDBOX_PROPERTIES = loadProperties("config/dhan-sandbox.properties");
private static final List<String> PENDING_SANDBOX_ORDER_IDS = Collections.synchronizedList(new ArrayList<>());
```

And a static method that calls the live network:

```java
static boolean preflightAuth(String clientId, String accessToken, String url) {
    ...
    HttpResponse<String> response = HttpClient.newHttpClient().send(request, ...);
    return response.statusCode() == 200 && response.body().contains("dhanClientId");
}
```

Problems:
1. `PENDING_SANDBOX_ORDER_IDS` is a **JVM-wide list of order IDs that the test process must cancel before exit**. If one test fails between `trackSandboxOrderForCleanup` and the cleanup hook, the next test inherits those IDs. Worse: in a `forkEvery 1` model, every test run starts fresh, but in `--max-parallel-fork>1` or CI matrix runs across modules, **the same JVM can host both LIVE and SANDBOX tests simultaneously**, and this list will try to cancel live orders when sandbox tests run with stale IDs.
2. `preflightAuth` does a real HTTP GET to `/fundlimit` on every connection setup. There's no caching, no recording, no per-test isolation. If the rate limiter fires, every test silently skips via `Assumptions.assumeTrue(false, ...)`, and you'll see "tests passed" in green with zero coverage.
3. `valueForProfile` reads `config/dhan-local.properties` and `config/dhan-sandbox.properties` from **whatever directory the test JVM was launched in**, walking up to find them (`LiveDhanTestSupport.java:237-247`). Running the same test from two different working directories loads different files, silently.
4. `LiveDhanAuthSession.resolve` (called at `LiveDhanTestSupport.java:89`) is described as a "JVM-wide session" — the cooldown is shared across all tests in the JVM. If the sandbox tests need a token mint and the live test just minted, the sandbox test skips.

**Required fix:**
- Move all static state to a `@TempDir`-per-test or `@TestInstance(PER_CLASS)` broker factory.
- Wrap `preflightAuth` in a memoizing `ConcurrentHashMap<URL, Optional<Boolean>>` with a TTL.
- Replace the static `PENDING_SANDBOX_ORDER_IDS` with a `TestInstancePostProcessor` that registers a `try/finally` cleanup at the per-test level.
- Forbid `--max-parallel-fork>1` for any task that touches `LiveDhanTestSupport` (set `maxParallelForks = 1` in the relevant `Test` blocks).
- Add a `@Tag("live-broker")` exclusion to the default `test` task and require an explicit opt-in via `-PincludeLiveBrokerTests`.

### P0-2 — `BrokerGatewayTest` covers nothing that matters

**Where:** `broker-gateway/src/test/java/com/tradej/brokergateway/BrokerGatewayTest.java` (full file, 75 lines)

The five tests are:

| Test | Asserts |
|---|---|
| `createFromSingleBrokerComposition` | `gateway.availableBrokers().contains(DHAN)` |
| `brokerByNameReturnsCorrectHandle` | `gateway.broker("dhan")` returns non-null |
| `brokerByInvalidNameThrows` | `gateway.broker("upstox")` throws `IllegalArgumentException` |
| `hasBrokerReturnsTrueForAvailable` | `hasBroker(...)` boolean |
| `firstReturnsAvailableHandle` | `gateway.first()` returns non-null |

**What's missing — and these are the actual production risks:**

- **`BrokerRouter`** (the thing that decides which broker gets the order) has **zero tests**. The whole point of `BrokerGateway` is to hide broker selection; nothing verifies selection policy.
- **`DefaultBrokerGateway` failover** — does it actually pick the next broker when the primary 5xxs? No test.
- **`ServiceLoaderBrokerRegistry`** SPI discovery — what happens when a malformed provider is on the classpath? When two providers register the same `BrokerSource`? When `META-INF/services` is missing?
- **Result coercion in `result/` package** — `BrokerResult`, `BrokerSource`, error mapping. Untested.
- **Simulation backends in `simulation/`** — every simulated broker path is uncovered.
- **`MarketGateway`** — the market data entry point has no test at all in this module.
- **`HistoricalRequest`** — request lifecycle is untested.

**Required fix:** write these tests *now*, before adding any new broker. Priority order:
1. `ServiceLoaderBrokerRegistryTest` — duplicate registration, missing file, malformed provider, empty list
2. `DefaultBrokerGatewayTest` — primary 5xx → fallback path, all-brokers-down → error result, partial outage → degraded result
3. `BrokerRouterTest` — policy: explicit override, default by `BrokerSource`, unknown source → error
4. `MarketGatewayTest` — symbol resolution across NSE/BSE/MCX, cache invalidation, broker-down → empty result
5. `BrokerResultTest` — `success`/`failure`/`partial` invariants, exception unwrapping

If you can't write these in two days, the gateway module is not done.

### P0-3 — The five P0 defects from the Runtime/Event Flow Review have no regression test

**Where:** Defects identified in `docs/reports/ARCHITECTURE_REVIEW_2026-06-06.md`.

I grepped the test tree for each:

| P0 defect | Test exists? | What we need |
|---|---|---|
| `AsyncDispatchHandler` silent drop on full (`AsyncDispatchHandler.java:166-181`) | **No** | A test that fills the ring buffer and asserts (a) the drop counter increments, (b) a `LagEvent` is published, (c) a metric is emitted. Today: nothing. |
| EventId-only dedup in `DisruptorEventBus` (`DisruptorEventBus.java:368-388`) | **No** | A test that replays the same `eventId` with **different payloads** and asserts the second is dropped. The current dedup is vulnerable to a broker that re-uses an id after a re-send. |
| Kill-switch TOCTOU (`PositionRiskHandler.java:48, 279`) | **No** | `KillSwitchE2EComponentTest` (`app/src/test/java/com/tradej/app/integration/KillSwitchE2EComponentTest.java`) only tests the happy path: 2 losses, switch engages, next signal is suppressed. We need a multi-threaded test where one thread flips the switch while another is mid-`onDomainEvent` and asserts the publication count matches the loss budget. The current test would pass even if `killSwitch` was a non-volatile field. |
| `OrderManagementService.cancelOrder` calls broker before state guard (`OrderManagementService.java:131-149`) | **No** | A test that asserts: in `LifecycleState.SUBMITTING`, `cancelOrder` does **not** call the broker; in `SUBMITTED`, it does. Today: not asserted. |
| Replay state-reset between runs | **No** | `FillReplayIntegrationTest` (`app/src/test/java/com/tradej/app/integration/FillReplayIntegrationTest.java`) tests a single replay. There is no test that two consecutive `HistoricalRangeService.replayFillEvents` calls don't leak state — and the implementation reuses the same DuckDB connection. |

**Required fix:** add one regression test per defect, all tagged `@Tag("regression-preflight")` so they run on every CI build. The full list of 32 invariants in `REGRESSION_MANIFEST.md` should map 1:1 to a test class with a `// invariant: N` comment.

### P0-4 — `TradingHotPathE2EComponentTest` is incremental but does not include a real broker

**Where:** `app/src/test/java/com/tradej/app/integration/TradingHotPathE2EComponentTest.java:52-205`

This is the closest thing to a true E2E test in the repo. It wires:

- `OrderPipeline` (hot path)
- `ExecutionHandler`
- `OrderManagementService`
- `EventSourcedOrderRepository`
- `DuckDbEventStore`
- `HistoricalRangeService`
- `PositionRiskHandler`
- `PortfolioEngine`
- `TradingCircuitBreaker`

…with a real DuckDB, real Disruptor pipeline, real OMS, real replay. **But the broker is still a mock or a no-op.** This means the test verifies that signals flow through to OMS state, but it does not verify the broker round-trip — and the broker round-trip is where the cancel-before-guard defect lives.

**Required fix:**
- Add a `WireMock` (or in-process Jetty + custom dispatcher) that speaks Dhan's REST surface for the orders endpoint and replays canned `order.postback` payloads on the WS side. Tag `@Tag("component-broker")` and gate on `-PincludeBrokerMocks`.
- For one weekly cron in CI, run the same test against `LiveDhanTestSupport` with a **single instrument, single qty, single order, sandbox profile**. The test should: submit, cancel, fill, and assert OMS + event store + DuckDB are consistent. This catches broker-API drift.

### P0-5 — `DisruptorEventBusStressTest` does not exercise the dedup path

**Where:** `runtime/disruptor/src/test/java/com/tradej/disruptor/DisruptorEventBusStressTest.java`

I read it: 9 stress tests reuse `ConcurrentStressTester` (`core/src/testFixtures/java/com/tradej/core/testing/ConcurrentStressTester.java`). The patterns are solid: deterministic latch, mixed producers, thread-pool drain, no shared mutable assertions.

What's missing:
- **Dedup contention**: thousands of threads publishing the *same* `eventId` — the dedup map should not allow more than one downstream publication, but a concurrent put could allow two through if the implementation uses a non-atomic `Map.containsKey` + `put` (and `DisruptorEventBus.java:368-388` does). The current stress tests publish *different* events.
- **Out-of-order dedup**: thread A publishes event 5, thread B publishes event 3; both reach the consumer. The dedup map clears by id, not by sequence. Worth testing.
- **Wrap-around**: the dedup map (likely a `LinkedHashMap` or ring buffer — needs reading) is bounded; when full, oldest is evicted. Test that an evicted id can be re-published without false-positive dedup.

---

## P1 — Fix in the next two sprints

### P1-1 — Chaos coverage is concentrated in the old surface

The 9 stress tests (using `ConcurrentStressTester`) cover:
- `DisruptorEventBusStressTest`
- `DisruptorHighThroughputStressTest`
- `EventSourcedOrderRepositoryStressTest`
- `OrderIdentityRegistryStressTest`
- `ExecutionHandlerStressTest`
- `TradingCircuitBreakerStressTest`
- `PositionRiskHandlerStressTest`
- `PortfolioEngineStressTest`
- `CandleAggregationServiceStressTest`

All of these are handlers, registries, and the bus. The new architecture adds **three fan-out surfaces with no chaos tests**:

1. **`gateway/src/main/java/com/tradej/gateway/bridge/GatewayEventBridge.java`** — the bridge between broker WS streams and the Disruptor hot path. A slow consumer here will cascade into the broker's TCP backpressure. No test simulates: (a) Disruptor full → does the bridge buffer or drop? (b) broker WS disconnect mid-event-stream → does the bridge re-seek the sequence? (c) two consumers racing on the same event.
2. **`broker-gateway` routing and failover** — `BrokerRouter` + `DefaultBrokerGateway` have zero chaos tests. The whole point of having a router is to absorb broker outages; we don't test the absorption.
3. **`runtime/hotpath/MarketDataPipeline` token bucket** (`MarketDataPipeline.java:117-122`) — the global token bucket is shared across all symbols. A burst on one symbol starves the others. No chaos test simulates (a) one symbol at 10x rate while others are normal, (b) bucket exhaustion and recovery, (c) the order in which starved symbols resume.

**Required fix:** add three more `ConcurrentStressTester`-based chaos tests, one per surface above.

### P1-2 — Architecture tests whitelist `com.tradej.pipeline.reactor` but don't enforce the rule for new code

**Where:** `architecture-test/src/test/java/com/tradej/architecture/SpringFreeArchitectureTest.java`

This test whitelists the existing reactive packages but a new package added tomorrow that uses Reactor will silently pass. Same problem with `ModuleBoundaryArchitectureTest` — a new top-level package under `com.tradej` won't fail the rule.

**Required fix:** invert the rule. Test that **only** packages matching `com\.tradej\.pipeline\.reactor(\..*)?` may import `reactor.*`; same for Spring annotations. That way a new package fails loudly.

### P1-3 — `RuntimeModeHolder` is static and tests must remember to reset it

**Where:** `core/src/main/java/com/tradej/core/domain/runtime/RuntimeModeHolder.java` (used by `app/src/test/java/com/tradej/app/integration/TradingHotPathE2EComponentTest.java:62, 79` — see the `setUp`/`tearDown` shape in `FillReplayIntegrationTest` at `:54-74`).

Any test that touches the runtime mode has to set it in `@BeforeEach` and reset in `@AfterEach`. Skipping the reset leaks mode into the next test. The blast radius is low today (most tests are `@Tag("component")` and the test classes set it explicitly), but as the gateway layer grows, more tests will need the mode flag.

**Required fix:** introduce a JUnit 5 `@ExtendWith(RuntimeModeExtension.class)` that snapshots and restores the mode around each test. Removes the foot-gun.

### P1-4 — No property-based testing despite jqwik being on the classpath

`grep -r "net.jqwik" --include="*.gradle"` should return something. I didn't see any `@Property` or `@ForAll` annotations in test sources. For a trading system, property tests are the right tool to cover:

- Order projection: any valid `Order` produces an `OrderProjection` whose `signedQuantity` is zero-sum over SELL/BUY.
- Position state machine: any sequence of fills, valid in qty, produces a `NetPosition` whose `abs(qty) ≤ |sum(fills)|`.
- Token bucket: any sequence of N acquires and M refills obeys `consumed ≤ capacity` and `tokens ≥ 0`.
- PnL: any sequence of trades with consistent timestamps is non-decreasing in the trade count if the PnL is monotonic.

**Required fix:** add a `property-test` source set under `core/` (or extend `unitTest`) and write at least 4 property tests for the four invariants above. Each must be reproducible with a fixed seed (`@Seed(42)`) for CI.

### P1-5 — `cli` module has no tests

`ls cli/src/test/` (if it exists) — most likely empty. The CLI is the user-facing entry point and the most likely surface for **silent regressions** (e.g. `--broker` flag typo, missing arg, wrong exit code). With picocli, the test surface is small: parse args → assert exit code → assert system out. Do this.

### P1-6 — `market-utils` module has no tests

Same story. The market utils are the data normalization layer (instrument IDs, exchange segment, symbol parsing). One wrong mapping and every order routes to the wrong exchange. The cost of tests is trivial; the cost of an untested mapping bug is a regulatory issue.

**Required fix:** add `InstrumentIdTest`, `ExchangeSegmentTest`, `SymbolNormalizerTest` with table-driven cases for NSE/BSE/MCX.

---

## P2 — Fix in the next quarter

### P2-1 — No flaky-test detection

You have 9 stress tests that do real-thread racing. If one fails once in 200 runs, the team will ignore it. Add:

- A nightly run that executes `unitTest` 50x and reports per-test pass-rate.
- A flaky-test quarantine tag (`@Tag("flaky")`) that gates the test out of merge-to-main but keeps it in nightly.
- A test-runner plugin that fails the build on any test that has been retried in the last 7 days.

### P2-2 — No test coverage of the `Result` algebra

The `result/` package under `broker-gateway` defines a `BrokerResult` sum type (success/failure/partial). If the algebra is used in 20 places and the coercion rules are in one place, that one place needs:

- Exhaustive enum coverage in tests.
- A property test: `result.flatMap(result.map(...))` is associative, `result.map(f).map(g) == result.map(f.andThen(g))`.
- Serialization round-trip if it ever crosses a network boundary.

### P2-3 — No contract tests for the `ServiceLoaderBrokerRegistry` SPI

The SPI is the extension point. Third parties (or your future self) will write providers. The contract is implicit. Make it explicit with a contract test that any `BrokerProvider` must pass to be registered. This is what OSGi did 20 years ago and we still haven't internalized it.

### P2-4 — `app/src/test/java/com/tradej/app/integration/` is the only integration test directory

All integration tests live in `app/`. If a future module wants to add an integration test, it has to depend on `app`. That's an inverted dependency for tests. Either:

- Promote `app/src/test/java/com/tradej/app/integration/` to a new `integration-test/` Gradle subproject, OR
- Add a `src/integrationTest` source set to each module that needs one (Gradle supports this since 7.x).

The latter is cleaner and matches the standard `src/main`, `src/test`, `src/integrationTest` layout.

### P2-5 — No coverage for the `certification/` and `explorer/` packages in `broker-gateway`

I saw these packages in the directory listing but didn't open them. If they are user-facing (certification = broker compliance check, explorer = broker capability discovery), they need a test each. Add this to the P0-2 list.

### P2-6 — No test for the `mavis` (or whatever the new CLI runtime is) interactive shell

If the CLI is REPL-style (which the `app` module name suggests), there should be a test that drives a scripted session: type commands, assert stdout, assert exit on EOF. Today, REPL bugs only surface in manual testing.

---

## Test pyramid shape (current vs target)

**Current:**
```
        /\
       /  \         E2E (live broker)
      /    \        1 test, opt-in, requires real money account
     /------\
    /        \      Component (in-process, no broker)
   /          \     ~15 tests, all in `app/`
  /------------\
 /              \   Unit + stress
/                \  ~200 tests, 9 stress tests using ConcurrentStressTester
```

**Target:**
```
        /\
       /  \         E2E live broker (nightly, sandbox)
      /    \        + E2E live broker (weekly, real account, $1 budget)
     /------\
    /        \      Component
   /          \     15+ tests per module, including broker-gateway, gateway, market-utils
  /------------\
 /              \   Unit + stress
/                \  200+ tests, 15+ stress tests, 4+ property tests
```

---

## Recommendations (concrete, ordered)

| # | Action | Owner guess | Effort | Impact |
|---|---|---|---|---|
| 1 | Make `LiveDhanTestSupport` parallel-safe and test-isolated (P0-1) | platform | 1d | High — unblocks CI matrix |
| 2 | Write the 7 missing `broker-gateway` tests (P0-2) | gateway | 2d | High — currently the new module is untested |
| 3 | Add regression tests for the 5 P0 defects (P0-3) | execution + disruptor | 1d | High — prevents regression of known bugs |
| 4 | Add broker-mock to `TradingHotPathE2EComponentTest` (P0-4) | execution | 1d | Medium — catches broker API drift |
| 5 | Add dedup-path stress tests (P0-5) | disruptor | 0.5d | Medium |
| 6 | Chaos tests for the 3 new fan-out surfaces (P1-1) | gateway | 2d | High — new architecture is unchaos-tested |
| 7 | Invert the architecture-test whitelist (P1-2) | architecture | 0.25d | Low cost, high value |
| 8 | `RuntimeModeExtension` (P1-3) | core | 0.25d | Low cost |
| 9 | Add 4 property tests (P1-4) | core | 1d | Medium — catches algebraic bugs |
| 10 | Test `cli` and `market-utils` (P1-5, P1-6) | cli + market-utils | 0.5d | High — these are user-facing |

Total estimated effort: **~10 person-days** to go from "strong test foundation" to "test pyramid in balance with the architecture."

---

## Closing thought

The team has clearly invested in testing — 9 stress tests, ArchUnit, profile isolation, and a regression manifest. But the new Gateway/CLI/Market Utils architecture was shipped without the same discipline. The risk is that the **next incident will be in the new code, and we will not have a test that fails fast**. Fix the five P0s and the gap closes by 80%.

See also:
- `docs/reports/ARCHITECTURE_REVIEW_2026-06-06.md` — the defects being regression-tested
- `docs/reports/REACTIVE_ADOPTION_REVIEW_2026-06-06.md` — R-1 (GatewayEventBridge) is the highest-leverage chaos target
- `REGRESSION_MANIFEST.md` — the 32 invariants that should map 1:1 to test classes
