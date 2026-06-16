# Trade-J Agile Execution Plan
## Dr. Venkat Subramaniam — Architecture & Delivery Review

> *"Working software over comprehensive documentation. Feedback loops over big design up front. Simplicity over cleverness. Every sprint, something runs."*

---

## Read This First — How to Use This Plan

This is not a Gantt chart. This is a **learning map**.

Each phase is a hypothesis: *"If we build these things in this order, we'll learn what we need to know to build the next things correctly."* Every phase ends with working code and a review gate. No phase is approved by calendar; each is approved by demonstration.

The team owns the how. I own the what's-next and the *"does this still feel simple?"* question.

**Phases are sequential in concept but not in practice.** Where a task in Phase N has no dependency on Phase N's other tasks, pull it forward. Where a task can be done in parallel, do it in parallel. The phase boundaries exist to protect the team from building infrastructure they don't yet understand.

Let's begin.

---

## Phase 1 — Shared Kernel and Domain Foundation

**Objective:** A compile-safe, test-verified set of value types and domain primitives that every other module depends on, with zero infrastructure dependencies.

**Dr. Venkat's Framing:**  
You're building a house. Before you buy the plumbing, you need to know what a "pipe" is. That's this phase. Every price, every segment, every side of a trade, every order type — these are the atoms of your domain language. If two atoms in your codebase mean the same thing but are spelled differently, you have a bug that hasn't happened yet. We're going to make those atoms explicit, tested, and impossible to misuse. No Spring. No broker. No database. Just Java records, enums, and tests that prove they work.

### Engineering Tasks

**1.1 — Extract `DefaultSegments` constants class**
- **What:** Create `core/src/main/java/.../domain/config/DefaultSegments.java` with `DEFAULT_EQUITY_SEGMENT = "NSE_EQ"`, `DEFAULT_INDEX_SEGMENT = "IDX_I"`, `DEFAULT_FNO_SEGMENT = "NSE_FNO"` as `static final String` constants. Replace all ~40 hardcoded strings in controllers and CLI defaults.
- **Tests:** Unit test asserting each constant's value. No functional behavior changes — the test proves the constants match the previous literals.
- **Done when:** `grep -r '"NSE_EQ"' --include='*.java' src/` returns zero results in production code (default annotations excluded).

**1.2 — Standardize controller segment parameters to `ExchangeSegment` enum**
- **What:** Change all `@RequestParam(defaultValue = "NSE_EQ") String segment` to `@RequestParam ExchangeSegment segment` across controllers. Remove the `String → ExchangeSegment.valueOf()` manual conversion in request handlers.
- **Tests:** Existing integration tests already exercise these endpoints as HTTP calls. Those tests become the refactoring safety net. Add one `@WebMvcTest` per affected controller that proves string input is correctly deserialized to the enum.
- **Done when:** `grep 'String segment' src/main/java/.../api/*.java` returns zero matches for controller parameters.

**1.3 — Create `TestSymbols` constants in test-fixtures**
- **What:** Add `test-fixtures/src/main/java/.../testsupport/TestSymbols.java` with `RELIANCE`, `SBIN`, `TCS`, `NIFTY`, `BANKNIFTY` as `static final String`. Add `TestSegments` with `NSE_EQ`, `IDX_I`, etc.
- **Tests:** Unit test for the class itself. This is purely a refactoring target — migrate tests as you touch them.
- **Done when:** `TestSymbols` is published and at least the `core` module tests use it. (Full migration is incremental across phases.)

**1.4 — Consolidate alert channel timeouts**
- **What:** Create `AlertChannelDefaults` in `app/.../health/` with `DEFAULT_TIMEOUT = Duration.ofSeconds(5)`. Replace the duplicated constant in `SlackAlertChannel`, `PagerDutyAlertChannel`, `WebhookAlertChannel`.
- **Tests:** Unit test that all three channels use the same constant reference.
- **Done when:** Three `Duration.ofSeconds(5)` constants are replaced by one.

**1.5 — Create `ReconnectDefaults` in broker-core**
- **What:** `broker/core/src/.../reconnect/ReconnectDefaults.java` with `DEFAULT_MAX_ATTEMPTS = 8`, `DEFAULT_BASE_DELAY_MS = 1000`, `DEFAULT_MAX_DELAY_MS = 30000`. Replace the local constants in `UpstoxWebSocketMultiplexer` and `DhanTwentyDepthWebSocketClient`.
- **Tests:** Unit test for defaults. Test that `ReconnectManager` constructed with defaults behaves as expected (existing `ReconnectManagerTest` covers this).
- **Done when:** No broker has reconnect constants defined locally.

### Interfaces / Contracts Established This Phase

- `core.domain.config.DefaultSegments` — the single source of truth for segment string defaults
- `core.domain.value.ExchangeSegment` — already exists; this phase formalizes its role as *the only* segment type in controller APIs
- `test-fixtures.TestSymbols` — shared test data constants
- `broker-core.reconnect.ReconnectDefaults` — standard reconnect parameters

### What Must NOT Be Built Yet

- No event bus
- No pipeline graph
- No broker connection code
- No persistence
- No strategy engine
- No metrics or observability

If you find yourself writing a Spring bean in this phase, stop. You're in the wrong phase.

### Phase Gate — Dr. Venkat's Review Checklist

1. ☐ Can I compile `./gradlew :core:test` and get green, with no application context loading?
2. ☐ Can I grep `"NSE_EQ"` in production Java and find it only in `DefaultSegments` and enum definitions?
3. ☐ Does every controller that previously accepted `String segment` now accept `ExchangeSegment segment`?
4. ☐ Do three alert channels all reference the same timeout constant?
5. ☐ Is `TestSymbols` used by at least one test that proves it simplifies the test code?
6. ☐ Can I remove any broker's MAX_RECONNECT_ATTEMPTS constant and the build still compiles?

### Known Risks and Mitigation

| Risk | Detection | Mitigation |
|------|-----------|------------|
| Controllers break because Spring's `StringToEnumConverterFactory` doesn't handle `ExchangeSegment` | One `@WebMvcTest` will fail instantly | Confirm Spring Boot 3.4's converter factory handles enums by default; add a `@InitBinder` or `Converter<ExchangeSegment>` if needed |
| `ReconnectDefaults` values don't match existing broker behavior | ReconnectManager tests pass but brokers behave differently | Run the existing `ReconnectManagerTest` and `ReconnectCertificationTest` as regression |
| `TestSymbols` migration creates merge conflicts across branches | Multiple team members adding tests simultaneously | Agree the constant names in a quick sync; merge conflicts on imports are trivial to resolve |

---

## Phase 2 — Event Model and Core Domain Logic

**Objective:** A complete, test-verified domain event catalog and the core business logic (Order state machine, PriceMath, ExchangeCalendar) that operates on those events — all running in memory with no I/O.

**Dr. Venkat's Framing:**  
The foundation is laid. Now we teach the system what it *means* when something happens. A tick arrives. An order is placed. A candle closes. These are events — facts that have happened in the past. Your domain logic is a stateless function that transforms these facts into new facts. "When a fill arrives, update the position." "When an order is rejected, release the reserved margin." These rules are your competitive advantage. They must be pure, testable, and independent of any framework. If I can run your order state machine without starting Spring, you're on the right track.

### Engineering Tasks

**2.1 — Audit and simplify the domain event hierarchy**
- **What:** Review all 30+ domain event classes in `core/domain/event/`. Remove unused events. Ensure every event implements `DomainEvent` and carries `EventMetadata`. Write an ArchUnit test that enforces: "every class ending in 'Event' in `core.domain.event` must implement `DomainEvent`."
- **Tests:** ArchUnit test for event contract compliance. Remove dead code (events with zero consumers).
- **Done when:** `DomainEvent` hierarchy is minimal, every event is used, and the ArchUnit test passes.

**2.2 — Strengthen the Order state machine**
- **What:** Review `OrderStateMachine` and all `OrderEvent` subclasses in `core/domain/oms/`. The state machine should be a **pure function**: `(OrderState, OrderEvent) → OrderState`. Write a property-based test (use jqwik or similar) that generates random valid event sequences and asserts that the machine never enters an invalid state.
- **Tests:** 
  - Existing `OrderStateMachineTest` and `OrderStateMachinePropertyTest` — expand coverage.
  - Property test: "From any reachable state, applying any valid event produces a known next state; applying any invalid event throws `IllegalStateTransitionException`."
- **Done when:** A fuzzer can run 10,000 random event sequences against the state machine without producing an inconsistent state.

**2.3 — Extract `ExchangeCalendar` as a pure function**
- **What:** `ExchangeCalendar` currently uses time-based session definitions. Make it a pure function: `(ExchangeSegment, Instant) → boolean` for `isMarketOpen`. Remove any reliance on system clock — the caller passes the `Instant`.
- **Tests:** Parameterized test covering all segments at session-open, session-close, after-hours, and during lunch (if applicable).
- **Done when:** `ExchangeCalendar` has no reference to `System.currentTimeMillis()`, `Clock.systemDefaultZone()`, or any other implicit time source.

**2.4 — Formalize `EventBus` contract as a test double contract test**
- **What:** Write an `EventBusContractTest` in `test-fixtures` that any `EventBus` implementation must pass. The contract specifies: publish produces events to subscribers, backpressure behavior, ordering guarantees (or lack thereof), and error isolation (one subscriber's exception doesn't kill others).
- **Tests:** The contract test itself. Apply it to `SimpleEventBus` (in core) and `DisruptorEventBus` (in runtime-disruptor) as proof.
- **Done when:** Both bus implementations pass the same contract test suite.

### Interfaces / Contracts Established This Phase

- `EventBus` contract (formalized, tested)
- `OrderStateMachine` as a pure function: `(OrderState, OrderEvent) → OrderState`
- `ExchangeCalendar.isMarketOpen(ExchangeSegment, Instant)` — explicit time parameter
- `DomainEvent` interface contract (ArchUnit-enforced)

### What Must NOT Be Built Yet

- No persistence of events
- No serialization/deserialization
- No broker integration
- No WebSocket transport
- No pipeline graph execution
- No portfolio engine

### Phase Gate — Dr. Venkat's Review Checklist

1. ☐ Can I run `OrderStateMachine` as a standalone Java class with `main()` that processes 100 events without loading Spring?
2. ☐ Does the property test run on every build (not nightly)?
3. ☐ Can I switch from `SimpleEventBus` to `DisruptorEventBus` and all EventBusContractTest assertions still pass?
4. ☐ Is `ExchangeCalendar` free of implicit system clock usage?
5. ☐ Does `grep -r 'System.currentTimeMillis\|Clock.system' core/src/main/java/.../domain/` return nothing?
6. ☐ Does every event class that claims to be a DomainEvent actually implement the interface?

### Known Risks and Mitigation

| Risk | Detection | Mitigation |
|------|-----------|------------|
| Property tests become flaky or take too long | CI timeout or non-deterministic failures | Cap iterations at 10,000; use a fixed random seed for reproducibility |
| Removing "unused" events breaks something compiled but not tested | CI test failure | Run a full `./gradlew build` before merge |
| EventBusContractTest over-specifies ordering that Disruptor provides but Simple doesn't | Contract test fails on SimpleEventBus | Split the contract: `OrderedEventBusContractTest` vs `UnorderedEventBusContractTest` |

---

## Phase 3 — Backtesting Engine (Event-Driven Simulation)

**Objective:** An in-process, deterministic simulation engine that can ingest a sequence of market data events, apply trading rules, produce orders, match fills, and output a P&L — all in a single thread, with the same result every time.

**Dr. Venkat's Framing:**  
This is the most important phase in the entire plan. Backtesting is where you discover whether your trading logic actually works. If you cannot prove that your system produces correct results when you feed it yesterday's data, you have no business feeding it today's data. The simulation engine must be **deterministic**: same input, same output, every time, on every machine. No wall-clock dependencies. No thread scheduling dependencies. No random seeds. The replay engine in Phase 5 depends on this guarantee.

### Engineering Tasks

**3.1 — Complete the `MatchingEngine` fill model**
- **What:** The existing `MatchingEngine` in `trading-simulation` simulates order matching. Extend it to support: limit orders (price-time priority), market orders (immediate fill at best available), partial fills with remaining quantity tracking, and order cancellation.
- **Tests:** Unit test each matching rule in isolation: "limit order at price X fills when opposite side arrives at price ≥ X", "market order fills completely only if sufficient volume exists", "cancelled order does not fill". Use the `TestSymbols` constants from Phase 1.
- **Done when:** `MatchingEngine` passes 20+ parameterized scenarios covering edge cases.

**3.2 — Build the `SimulatedOrderService` application service**
- **What:** The existing `SimulatedOrderService` wraps `MatchingEngine` in a service that accepts `OrderRequest`, validates it, calls `placeOrder`, returns the filled order, and emits `OrderFilled` / `OrderRejected` events. Add the "place" and "cancel" paths.
- **Tests:** Component test that sends an order, verifies the matched fill, verifies the emitted event matches the fill. Use `CollectingEventBus` from core testFixtures.
- **Done when:** A test can place a market order for 100 shares, receive 100 filled, and verify the P&L delta in the matching engine.

**3.3 — Build the `BacktestService` orchestrator**
- **What:** `BacktestService` in `core/service/` coordinates: load historical bars → for each bar, compute indicators → evaluate strategy rules → produce orders → simulate fills → update P&L. The key: this is a **synchronous, single-threaded loop**. No threads, no timers, no real-time.
- **Tests:** End-to-end backtest with a fixed dataset (10 bars of synthetic data). Assert: initial position is empty, strategy generates correct signal, order is placed and filled, final P&L matches expected.
- **Done when:** `BacktestService.run(syntheticBars).finalPnL()` returns a known value with no randomness.

**3.4 — Implement `DefaultBacktestFillModel`**
- **What:** The fill model determines *how* an order gets filled in simulation. The default model: immediate fill at order price for market orders, fill at limit price (or better) for limit orders if market crosses the limit. Add a configurable slippage model.
- **Tests:** Parameterized: slippage of 0 → fill at order price; slippage of 0.1% → fill at price × 1.001 for buys.
- **Done when:** Two fill models (immediate and slippage) are available and tested.

### Interfaces / Contracts Established This Phase

- `FillModel` interface: `fill(Order, MarketState) → FillResult` — pluggable fill logic
- `BacktestService.run(List<Candle>) → BacktestResult` — synchronous backtest API
- `MatchingEngine` contract: all matching rules are deterministic

### What Must NOT Be Built Yet

- No live market data connection
- No broker API integration
- No real-time execution
- No portfolio-level capital allocation
- No multi-strategy coordination
- No persistence of backtest results

### Phase Gate — Dr. Venkat's Review Checklist

1. ☐ Can I run the same backtest twice and get byte-identical output?
2. ☐ Can I explain in one sentence how fill price is determined for a limit order?
3. ☐ Does `BacktestService.run()` complete without creating any threads?
4. ☐ Is there at least one test that uses real (recorded) market data, not synthetic?
5. ☐ Can I run the backtest on my laptop without network access?
6. ☐ Does a partial fill leave the remaining quantity in the order book correctly?

### Known Risks and Mitigation

| Risk | Detection | Mitigation |
|------|-----------|------------|
| Slippage model does not match real broker fill behavior | Backtest P&L diverges from paper trading P&L in Phase 6 | Make `FillModel` pluggable from day one; the realbroker-calibrated model comes later |
| Synthetic test data is too clean — no missing bars, no gaps | Backtest succeeds but crashes on real data | Add a test that includes gaps, late data, and duplicate timestamps |
| Single-threaded backtest is too slow for 5 years of tick data | Phase 3 demos are sluggish but pass | Do not optimize yet. Correctness first. We'll parallelize in Phase 8 if needed. |

---

## Phase 4 — Portfolio, Risk, and Order Decision Engine

**Objective:** The layer that decides *whether* to execute an order, *how much* to allocate, and *what* the resulting portfolio exposure looks like — all decoupled from the market data and execution layers.

**Dr. Venkat's Framing:**  
A strategy generates signals. A trading system generates orders. The gap between them is where risk management lives. "Should I take this trade?" is a fundamentally different question from "Can I execute this trade?" The portfolio engine owns the first question. The risk handler owns the second. Your backtester from Phase 3 calls into these engines. Your live system will too. If the same code path is used for both, you can prove that your backtest reflects your live risk rules.

### Engineering Tasks

**4.1 — Strengthen `PositionRiskHandler` as a pure pre-trade check**
- **What:** `PositionRiskHandler` in `trading-execution` currently qualifies signals. Refactor it into a pure function: `(Signal, PortfolioState) → RiskVerdict`. `RiskVerdict` is either `APPROVED` or `REJECTED` with a reason. Rules: max daily loss, max consecutive losses, max open positions, kill switch.
- **Tests:** For each risk rule, a test that proves: "when position state violates rule X, signal is REJECTED; when it does not, signal is APPROVED."
- **Done when:** `PositionRiskHandler` has zero side effects — no I/O, no database queries, no broker calls.

**4.2 — Build the `PortfolioEngine` position tracker**
- **What:** `PortfolioEngine` in `trading-strategy` tracks: open positions (symbol, quantity, entry price, unrealized P&L), available capital, net exposure. It processes `PositionUpdateEvent` and `PnLUpdatedEvent` to maintain state. Make it an in-memory aggregate — no database.
- **Tests:** "Place a buy order for 100 shares at ₹150 → position shows 100 shares, entry ₹150, unrealized P&L computed from current price." "Sell 50 shares → position shows 50 shares, realized P&L recorded."
- **Done when:** `PortfolioEngine` can answer "can I afford this order?" by checking available capital against order value.

**4.3 — Wire `PortfolioEngine` + `PositionRiskHandler` into `BacktestService`**
- **What:** Before each backtest order is sent to the `MatchingEngine`, run it through `PositionRiskHandler`. Rejected signals are recorded as `SignalSuppressed` events. The backtest output includes both executed trades and suppressed signals.
- **Tests:** Backtest with a kill switch engaged → all signals suppressed. Backtest with max-daily-loss = 0 → first trade executes, second is suppressed.
- **Done when:** Backtest output includes a `suppressedSignals` list alongside `executedTrades`.

**4.4 — Implement `PositionSizer` interface**
- **What:** `PositionSizer` determines order quantity from a signal and current portfolio state. Implement `DefaultPositionSizer` (fixed quantity), `PercentRiskPositionSizer` (position size = fixed percentage of capital at risk), and `VolatilityPositionSizer` (position size inversely proportional to ATR).
- **Tests:** "100 shares of ₹1000 stock with 1% risk → position value = 1% of ₹10,00,000 = ₹10,000 → 10 shares." "ATR of ₹50 on ₹1000 stock → smaller position than ATR of ₹10."
- **Done when:** Three sizers exist, each with 3+ edge-case tests.

### Interfaces / Contracts Established This Phase

- `PositionRiskHandler.evaluate(Signal, PortfolioState) → RiskVerdict` — pure pre-trade check
- `PortfolioEngine` — in-memory portfolio state machine (event-sourced)
- `PositionSizer` interface — pluggable sizing strategies
- `PositionUpdateEvent` and `PnLUpdatedEvent` — portfolio state change events (already exist, now formalized)

### What Must NOT Be Built Yet

- No live order execution
- No broker-specific risk rules (broker margin, broker position limits)
- No WebSocket streaming of portfolio state
- No persistence of portfolio state

### Phase Gate — Dr. Venkat's Review Checklist

1. ☐ Can I run `PositionRiskHandler` without starting Spring or accessing a database?
2. ☐ Does the kill switch in `PositionRiskHandler` match the kill switch semantics defined in `UnifiedKillSwitchEngaged`/`Disengaged` events?
3. ☐ Can I backtest with risk rules enabled and get a different result than without risk rules?
4. ☐ Does `PortfolioEngine` correctly compute unrealized P&L when given a current market price?
5. ☐ Are there edge-case tests for: zero capital, negative capital, order value exceeding capital?
6. ☐ Does a rejected signal produce an event that can be consumed by the caller?

### Known Risks and Mitigation

| Risk | Detection | Mitigation |
|------|-----------|------------|
| PositionSizer uses current market price but backtest has no "current price" in that bar | NPE or stale price used | Make current price explicit in the `PositionSizer` signature: `size(Signal, PortfolioState, BigDecimal currentPrice)` |
| Max-consecutive-losses rule resets on winning trade, but team disagrees on "consecutive" definition | Argument in code review | Write a test that explicitly defines the reset behavior and agree as a team |
| PortfolioEngine in-memory state is lost on restart | No test for this | Expected at this phase. Persistence comes in Phase 6. |

---

## Phase 5 — Replay Engine and Parity Validation

**Objective:** A replay engine that can ingest recorded event streams (from backtest, from live capture, from historical data) and reproduce the exact same system state as the original run — and a test suite that *proves* replay matches backtest.

**Dr. Venkat's Framing:**  
This is where we earn trust. A backtest says "you would have made this much money." Replay says "watch me run that same scenario again, in slow motion, and produce the exact same result." If replay and backtest disagree, one of them is wrong — and you cannot ship until you know which. The replay engine must consume the *same events*, process them through the *same logic*, and produce the *same output*, regardless of whether the clock is wall time or a file. This is your zero-discrepancy guarantee.

### Engineering Tasks

**5.1 — Complete the `ReplayTradingClock`**
- **What:** `ReplayTradingClock` advances time based on event timestamps, not wall clock. It must: allow time to be set to a specific instant, advance to the next event's timestamp, and provide "current time" as seen by domain logic. Replace any `System.currentTimeMillis()` in domain logic with calls through the clock interface.
- **Tests:** "Set clock to T=0 → advance to T=10000 → current time is 10000." "Two back-to-back advances without events → clock stays at last set time."
- **Done when:** All domain code that needs current time uses `TradingClock.now()`, not system calls.

**5.2 — Build the `TickReplaySession` event loop**
- **What:** The existing `TickReplaySession` reads events from a source (file, database, in-memory list) and publishes them through the event bus. Complete it to: replay at configurable speed (1×, 10×, 100×, instant), pause/resume, and emit "replay complete" event.
- **Tests:** Replay 100 synthetic ticks at instant speed → all 100 arrive on the event bus. Verify count and ordering.
- **Done when:** `TickReplaySession` can replay any `List<DomainEvent>` and deliver them all to subscribers.

**5.3 — Write the Parity Test: Backtest × Replay**
- **This is the most important test in the system.**
- **What:** Take a recorded scenario (e.g., 100 ticks of NIFTY market data with 2 order events). Run it through `BacktestService` and record all output events. Replay the same scenario through `TickReplaySession` and record all output events. Assert that the two event sequences are byte-identical.
- **Tests:** The parity test itself. Start with a synthetic scenario of 10 events. Gradually increase to recorded market data.
- **Done when:** A test asserts `assertEquals(backtestEvents, replayEvents)` and passes on every build.

**5.4 — Build the `ScenarioRunner` for parameterized replay**
- **What:** `ScenarioRunner` accepts a `Scenario` (a named set of initial state + event sequence + expected outcome). It runs the scenario through both backtest and replay, and asserts parity. Multiple scenarios form a regression suite.
- **Tests:** Create 5 scenarios: "flat market — no trades", "gap up — limit order fills", "gap down — stop loss triggers", "kill switch during replay", "partial fill sequence".
- **Done when:** `./gradlew :replay-engine:test` runs 5+ scenarios and all assert PASS.

### Interfaces / Contracts Established This Phase

- `TradingClock` interface — domain code depends on this, never on system clock
- `TickReplaySession` — standard replay loop
- `Scenario` — portable, self-contained test scenario definition
- Parity invariant: `BacktestService(sameEvents).output == ReplayEngine(sameEvents).output`

### What Must NOT Be Built Yet

- No broker connection during replay
- No live data capture for replay
- No persistence of replay results
- No UI for replay visualization

### Phase Gate — Dr. Venkat's Review Checklist

1. ☐ Can I replay a 1000-event sequence and get identical output to backtest?
2. ☐ Can I run replay at instant speed and 100× speed and get the same result?
3. ☐ Does any domain class still reference `System.currentTimeMillis()` or `Instant.now()`?
4. ☐ Can I add a new Scenario by writing only a data file and a test method?
5. ☐ Does the parity test run as part of `./gradlew check`?
6. ☐ If I add a new event type, does the replay engine handle it without modification?

### Known Risks and Mitigation

| Risk | Detection | Mitigation |
|------|-----------|------------|
| Replay and backtest use different code paths (one goes through Disruptor, the other is direct call) | Parity test fails | Both should use the same domain logic. If Disruptor adds non-determinism, replay must bypass it. |
| Event timestamps have insufficient precision for ordering | Two events at same timestamp arrive in different order | Use a sequence counter as tiebreaker. `EventMetadata` already has one? Audit and standardize. |
| Scenario data files become large | Git repo bloats | Keep scenarios ≤100 events for the parity test suite. Full backtest datasets live outside the repo. |

---

## Phase 6 — Broker Abstraction and Paper Trading

**Objective:** A clean, tested broker abstraction where a simulated broker (paper) and real brokers (Dhan, Upstox, ICICI) are interchangeable behind the same interface, and paper trading is proven correct by parity with backtest output.

**Dr. Venkat's Framing:**  
"Should I go live?" The answer is "not until your paper trades match your backtest." The broker abstraction is not a leaky Java interface — it's a contract. Paper broker says "I'll fill your order at the market price." Live broker says "I'll try to fill your order at the market price." If those two diverge, you need to know why. The abstraction must be so clean that you can swap a broker implementation and the only difference is latency and fill probability — never correctness.

### Engineering Tasks

**6.1 — Create `AbstractBrokerConnection` base class**
- **What:** Extract the duplicated `putIfNotNull` capability map building, `getCapability`, `requireCapability`, and `putIfNotNull` pattern from all 4 broker connections into `broker-core`. Each broker's `BrokerConnection` extends `AbstractBrokerConnection` and implements only business methods.
- **Tests:** `BrokerPluginContractTest` (already exists in broker-api) passes without changes. The contract proves the abstraction is correct.
- **Done when:** `DhanBrokerConnection`, `UpstoxBrokerConnection`, `IciciBrokerConnection` all extend `AbstractBrokerConnection` and their existing tests pass.

**6.2 — Create `UnsupportedPortProvider` to eliminate ICICI boilerplate**
- **What:** Replace 5 "not supported" adapters (BracketOrder, CoverOrder, GttOrder, SliceOrder, ConditionalAlert) with a single `UnsupportedPortProvider` factory. It uses `java.lang.reflect.Proxy` to create an implementation of any port interface that throws `UnsupportedOperationException` with a consistent message.
- **Tests:** "Create an unsupported `BracketOrderProvider` → calling any method throws `UnsupportedOperationException` with message containing 'ICICI' and the interface name."
- **Done when:** `IciciBracketOrderAdapter`, `IciciCoverOrderAdapter`, `IciciGttOrderAdapter`, `IciciSliceOrderAdapter` are deleted.

**6.3 — Wire `PaperBrokerConnection` through the full stack**
- **What:** The existing `PaperBrokerConnection` in `broker-gateway` uses `SimulatedOrderService` and `SimulatedWebSocketMultiplexer`. Wire it so that `trade.broker-type=paper` produces a fully functional paper trading environment that uses the Phase 3 `MatchingEngine`.
- **Tests:** Integration test: set broker type to `paper`, place an order through the OrderController, verify the fill event appears on the event bus.
- **Done when:** `curl localhost:8080/api/v1/orders/place` with paper mode returns a filled order.

**6.4 — Fix the layer violation in `data-historical-ingest`**
- **What:** Remove the `implementation project(':broker-dhan')` dependency from `data/historical-ingest/build.gradle`. Extract any Dhan-specific logic into `broker-dhan` and have it call into `data-historical-ingest` through an injected interface.
- **Tests:** Existing integration tests pass. ArchUnit test in `architecture-test` enforces: "no data module depends on any broker implementation."
- **Done when:** `grep 'broker-dhan' data/historical-ingest/build.gradle` returns nothing.

### Interfaces / Contracts Established This Phase

- `AbstractBrokerConnection` — reusable base for all broker connections
- `PaperBrokerConnection` — full paper trading stack
- Broker-type configuration: `trade.broker-type=paper` works end-to-end
- `UnsupportedPortProvider` — standard NotSupported fallback

### What Must NOT Be Built Yet

- No live trading with real money
- No broker failover (broker-gateway multi-broker orchestration)
- No broker certification framework
- No production monitoring for broker connections

### Phase Gate — Dr. Venkat's Review Checklist

1. ☐ Can I start the app with `trade.broker-type=paper` and place an order via curl?
2. ☐ Does the paper trade's P&L match what the backtester would have produced for the same market data?
3. ☐ Can I add a new broker with ~100 lines or fewer (provider + connection + tests)?
4. ☐ Are the ICICI "not supported" adapters deleted?
5. ☐ Does `data-historical-ingest` compile without `broker-dhan` on the classpath?
6. ☐ Do all 4 connection implementations share the `AbstractBrokerConnection` base?

### Known Risks and Mitigation

| Risk | Detection | Mitigation |
|------|-----------|------------|
| `AbstractBrokerConnection` becomes a God class | It has more than 10 methods or starts doing I/O | Keep it focused on capability wiring only. Reconnect, auth, and lifecycle stay in separate classes. |
| Paper broker fills don't match real broker fills | Systematic price difference in parity test | Make fill model configurable per broker (separate task, not this phase) |
| Removing `broker-dhan` from data-historical-ingest breaks the build | Compilation error | Run `./gradlew :data-historical-ingest:build` before merge |

---

## Phase 7 — Screening Engine

**Objective:** A declarative, profile-driven screening engine that scans instruments against configurable criteria (volume spikes, price breakouts, option liquidity) and produces ranked signals — usable both standalone and as a strategy input.

**Dr. Venkat's Framing:**  
Not every trade comes from a strategy. Sometimes you want to answer: "show me the 10 most liquid option contracts right now." Or "which stocks just broke out on volume?" That's screening — a different beast from strategy execution. Screeners are declarative: "I want instruments where X > Y, ranked by Z." The engine evaluates these rules against market data and returns a sorted list. Keep screening separate from execution. A screener produces ideas. A strategy produces orders. They talk through signals, not shared classes.

### Engineering Tasks

**7.1 — Refactor `ScanEngine` to pure evaluation**
- **What:** `ScanEngine` currently mixes profile loading, instrument iteration, and criterion evaluation. Split it: `ScanProfile` is data (YAML/JSON → POJO), `ScanCriterion` evaluates a single rule, `ScanEngine` orchestrates. Make `ScanEngine.accept(ScanProfile, UniverseState) → ScanResult` a pure function.
- **Tests:** "Scan profile with `volume-spike` criterion at 1.5× multiplier → instruments with volume < 1.5× average are excluded."
- **Done when:** `ScanEngine` is stateless; all state lives in `ScanProfile` and `UniverseState`.

**7.2 — Expand the `ScanCriterion` library**
- **What:** Implement: `PriceChangeFromOpenCriterion`, `VolumeSpikeCriterion`, `RSIIndicatorCriterion`, `OptionLiquidityCriterion` (ranking by OI × volume ÷ spread). Each implements `ScanCriterion` and is testable in isolation.
- **Tests:** For each criterion: "candle with 2× average volume → score = 100; candle with 0.5× average volume → score = 0."
- **Done when:** 4+ criteria exist, each with 3+ edge-case tests.

**7.3 — Build `ScannerStrategyBridge`**
- **What:** `ScannerStrategyBridge` converts top-ranked scan hits into `SignalGenerated` events that feed into the execution pipeline. The bridge filters by minimum score, deduplicates across scan cycles, and throttles signal frequency.
- **Tests:** "Top 3 scan hits → 3 signals emitted." "Same hit in consecutive scans → only 1 signal emitted (deduplication)."
- **Done when:** A scan profile can produce signals that flow into `PositionRiskHandler` and result in paper orders.

**7.4 — Expose scan profiles as configuration**
- **What:** `config/scan-profiles.json` already exists. Formalize the schema. Add a `ScanProfileValidator` that rejects invalid profiles at startup. Document the criteria DSL.
- **Tests:** Profile with unknown criterion → validation fails. Profile with valid criteria → passes.
- **Done when:** Startup reports "scan profile 'intraday-hybrid' loaded with 4 criteria" or "scan profile 'bad-profile' rejected: unknown criterion 'magic-algorithm'."

### Interfaces / Contracts Established This Phase

- `ScanCriterion` interface — single-criterion evaluation
- `ScanEngine.evaluate(ScanProfile, UniverseState) → ScanResult` — pure function
- `ScannerStrategyBridge` — scan hit → trading signal bridge
- `ScanProfile` schema — JSON/YAML definition format

### What Must NOT Be Built Yet

- No scan persistence (history of scan results)
- No scheduled scan execution (that's Phase 8)
- No institutional screener variants
- No scan result visualization

### Phase Gate — Dr. Venkat's Review Checklist

1. ☐ Can I define a new scan criterion without touching `ScanEngine`?
2. ☐ Can I write a scan profile in JSON and have the engine accept it without recompiling?
3. ☐ Is `ScanEngine` stateless — no mutable fields, no database calls?
4. ☐ Can a scan produce a signal that results in a paper trade (end-to-end demo)?
5. ☐ Does the `OptionLiquidityCriterion` correctly handle zero-OI or zero-volume instruments?
6. ☐ Are invalid profiles rejected at startup with a clear error message?

### Known Risks and Mitigation

| Risk | Detection | Mitigation |
|------|-----------|------------|
| Scan criteria require live market data but screener is tested in isolation | Criteria test with mock data passes, but real data has different shape | Add a contract test that uses recorded real market data as input |
| ScannerStrategyBridge creates too many signals | Event bus overload | Throttle is built in from day one; verify with a test that sends 100 scan results in 1 second |
| Option liquidity criterion is slow because it queries the option chain for each underlying | Scan takes >30 seconds for NIFTY + BANKNIFTY | The criterion can cache the option chain during a single scan cycle; benchmark test warns if >5 seconds |

---

## Phase 8 — Strategy Optimization and Research Workflows

**Objective:** A research environment where an analyst can parameterize a strategy, run it across historical data, compare results, and select the best parameter set — without writing Java code or touching the production codebase.

**Dr. Venkat's Framing:**  
A strategy is a hypothesis. "If price crosses above the 20-day moving average, buy." That hypothesis has parameters: which moving average period? 20 days? 15? 30? What's the stop loss? 2%? 5%? The optimizer asks: which combination of parameters would have performed best over the last 3 years? This is not a production feature — it's a research tool. It must be isolated from the production code. The last thing you want is an optimizer accidentally sending real orders because you reused the wrong connection.

### Engineering Tasks

**8.1 — Build the parameter sweep harness**
- **What:** A `ParameterSweep` that takes a strategy, a set of parameter ranges (e.g., `maPeriod: [10, 20, 30]`, `stopLoss: [0.02, 0.05]`), and historical data. It runs the backtester for each parameter combination and returns all results for comparison.
- **Tests:** "Strategy with 2 parameters × 3 values each → 9 backtest runs." "Results are comparable (same input data, same metric calculation)."
- **Done when:** `ParameterSweep.run(strategy, params, data).results()` returns 9 entries with Sharpe, max drawdown, total return.

**8.2 — Implement optimization metrics**
- **What:** Compute: total return, Sharpe ratio, max drawdown, win rate, average win/loss, profit factor, Calmar ratio. Each is a pure function: `(List<Trade>, PortfolioState) → Metric`.
- **Tests:** "10 trades, 6 winning (₹100 avg), 4 losing (₹50 avg) → win rate 60%, profit factor (600/200) = 3.0."
- **Done when:** 7+ metrics exist, all mathematically verified against known inputs.

**8.3 — Build the `StrategyRegistry` YAML-based plugin system**
- **What:** The existing `StrategyRegistry` discovers strategies from YAML files. Complete it: strategies can be loaded from `config/strategies/*.yaml` without recompiling. Each YAML specifies: class name, parameters with defaults, required indicators.
- **Tests:** "Load strategy from YAML → instance created with correct parameters." "YAML references unknown class → descriptive error."
- **Done when:** A strategy can be deployed by adding a YAML file, without redeploying the app.

**8.4 — Implement walk-forward optimization**
- **What:** Walk-forward optimization splits historical data into training and testing windows. Optimize on training, validate on test. Implement fixed-window and expanding-window modes.
- **Tests:** "3-year data, 2-year train, 1-year test → optimal params from train period, performance reported on test period."
- **Done when:** Walk-forward test produces out-of-sample metrics that differ from in-sample (demonstrating overfitting detection).

### Interfaces / Contracts Established This Phase

- `ParameterSweep` — parallel parameter search harness
- `StrategyPlugin` — YAML-loadable strategy contract
- `StrategyRegistry` — YAML-based strategy discovery
- Optimization metrics — 7+ well-defined metric functions

### What Must NOT Be Built Yet

- No ML-based optimization (genetic algorithms, Bayesian optimization — that's research-lab scope)
- No live parameter updates (changing strategy params while running)
- No visual charting in the CLI

### Phase Gate — Dr. Venkat's Review Checklist

1. ☐ Can a non-developer (analyst) add a strategy by writing a YAML file?
2. ☐ Is the optimizer guaranteed to never send a real order?
3. ☐ Can I run 100 backtests and get all results without manual intervention?
4. ☐ Are out-of-sample results reported separately from in-sample?
5. ☐ Does `ParameterSweep` respect the same risk rules as the live system (from Phase 4)?
6. ☐ Does the Calmar ratio match the formula: annualized return / max drawdown?

### Known Risks and Mitigation

| Risk | Detection | Mitigation |
|------|-----------|------------|
| Optimization overfits to historical data | Out-of-sample metrics are much worse than in-sample | Walk-forward is mandatory; flag strategies where gap exceeds 20% |
| Parameter sweep takes too long (1000+ combinations) | CI timeout | Cap parallel runs at CPU core count; optimize by caching bar data across runs |
| YAML strategy loading creates security risk (arbitrary class instantiation) | RCE vulnerability | Whitelist allowed packages in `StrategyRegistry`; validate class names against allowed list |

---

## Phase 9 — Live Execution Hardening and Operational Readiness

**Objective:** A production-hardened live execution environment with circuit breakers, kill switches, rate limiting, observability, and safety guarantees — ready to risk capital but still paper-only by default.

**Dr. Venkat's Framing:**  
This is the boring phase. The most important one. No new features. No new strategies. Just resilience, observability, and failure modes. A trading platform that goes down during market hours costs money. A trading platform that *doesn't detect* it went down costs more. Every circuit breaker, every rate limiter, every health check, every alert — these are not nice-to-haves. They are the difference between "we had a technical issue" and "we had a financial loss." Test every failure mode. Then test it again.

### Engineering Tasks

**9.1 — Formalize the kill switch with dual confirmation**
- **What:** The kill switch must: stop ALL order placement in under 100ms, persist its state (so it survives restart), support both manual (API) and automatic (risk rule breach) activation, and require explicit confirmation to disengage. Wire `UnifiedKillSwitchEngaged` and `UnifiedKillSwitchDisengaged` events through the entire pipeline.
- **Tests:** "Kill switch engaged → order placement returns 503." "Kill switch engaged → in-flight orders are NOT cancelled (they complete normally)." "Kill switch survives app restart (persisted state)."
- **Done when:** Kill switch state is persisted, events are emitted on toggle, and confirmations are required.

**9.2 — Complete the circuit breaker integration**
- **What:** The existing `CircuitBreaker` in `broker-core` is implemented. Wire it into all broker calls (REST + WebSocket). Circuit opens after N consecutive failures, stays open for M milliseconds, then half-opens. Test: broker API starts returning errors → circuit opens → no broker calls made → circuit half-opens → one call succeeds → circuit closes.
- **Tests:** "After 5 consecutive failures, circuit is OPEN." "After 30s, circuit is HALF_OPEN." "After half-open success, circuit is CLOSED."
- **Done when:** Every external broker call passes through a circuit breaker. Metrics are exposed via Micrometer.

**9.3 — Implement rate limiting at the broker call level**
- **What:** The existing token bucket rate limiters in `broker-core` need to be wired per-broker, per-API-category. Each broker declares its rate limits (e.g., Dhan: 50 requests/second, 1/minute for option chain). Exceeded calls are queued or rejected with a `429 Too Many Requests`.
- **Tests:** "Send 60 requests in 1 second with 50/sec limit → 50 succeed, 10 are rate-limited." "Rate-limited call returns 429 after configured timeout."
- **Done when:** Each broker category has a rate limiter; rate limit metrics are exposed.

**9.4 — Add structured alerting for production incidents**
- **What:** Extend the existing alert channels (Slack, PagerDuty, Webhook) to fire on: broker disconnect, circuit breaker open, rate limit threshold breach, kill switch activation, abnormal P&L change (>5% in 1 minute). Each alert includes: timestamp, severity, affected component, correlation ID, suggested action.
- **Tests:** "Alert configuration with invalid webhook URL → startup warning, not failure." "Simulate broker disconnect → Slack notification contains expected fields."
- **Done when:** Every critical state change in the broker layer produces an alert. Alerts are structured (JSON) and actionable.

### Interfaces / Contracts Established This Phase

- Kill switch — state machine with persistence
- Circuit breaker — per-broker, per-API-category
- Rate limiter — per-broker, per-category
- Alerting — structured, actionable notifications

### What Must NOT Be Built Yet

- No actual real-money trading (still paper)
- No broker failover (broker-gateway multi-broker will be Phase 10)
- No ML model serving
- No analytics dashboards

### Phase Gate — Dr. Venkat's Review Checklist

1. ☐ Can I simulate a broker outage and verify the system degrades gracefully (stops trading, alerts, does not crash)?
2. ☐ Does the kill switch survive a restart?
3. ☐ Are all broker calls protected by circuit breakers and rate limiters?
4. ☐ Can I trigger an alert and verify it arrives in Slack within 5 seconds?
5. ☐ Does the system recover automatically when the broker comes back online?
6. ☐ Are rate limit metrics visible via `/actuator/prometheus`?
7. ☐ Can I identify what *specific* API call failed from the alert message alone?

### Known Risks and Mitigation

| Risk | Detection | Mitigation |
|------|-----------|------------|
| Circuit breaker resets too aggressively, causing cascading failures | Metrics show repeated open/close cycling | Use the existing "storm cooldown" in ReconnectManager; tune per broker |
| Rate limiter queue grows unbounded during broker slowdown | OOM | Queue must have bounded capacity; reject with 429 when full |
| Alert fatigue from noisy circuit breaker transitions | Alerts ignored | Only alert on state transitions (OPEN → CLOSED, OPEN → HALF_OPEN is informational) |

---

## Phase 10 — Continuous Quality and Architectural Governance

**Objective:** An automated governance layer — ArchUnit rules, dependency checks, code style enforcement, and a living architectural fitness function — that prevents the codebase from regressing into the shotgun surgery state we found in Phase 1.

**Dr. Venkat's Framing:**  
Every codebase starts clean. Every codebase decays. The question is not whether yours will decay — it's whether you'll notice. Architectural governance is not bureaucracy. It's a early-warning system. When someone accidentally adds a dependency from `data` to `broker-dhan`, the build should fail with a message that says "Data modules must not depend on broker implementations, and here's why." When someone creates a class that makes PriceMath lookups bypass the standard conversion, a test should catch it. The rule is simple: *if it matters, automate it.* If you're enforcing something in code review manually, you're wasting human attention on something a machine can check in 10 milliseconds.

### Engineering Tasks

**10.1 — Complete the ArchUnit module dependency rules**
- **What:** The existing `ModuleDependencyTest` in `architecture-test` must be extended to cover all boundary rules from Phase 5 of the audit: domain → no infra imports, data → no broker imports, broker-api → no broker implementation imports, no static mutable holders. Each rule produces a clear, actionable failure message.
- **Tests:** The ArchUnit tests themselves. Add a "known violation" list for existing violations that are not yet fixed — failing tests for these would be wrong; we want them passing with exceptions listed.
- **Done when:** `./gradlew :architecture-test:test` passes and enforces all boundary rules with custom failure messages.

**10.2 — Add Checkstyle rules for naming and structure**
- **What:** Checkstyle rules: constant naming (`^DEFAULT_.*$` for shared defaults), controller parameter types (`ExchangeSegment` required where applicable), method length limit (30 lines), class responsibility (no class > 500 lines). Keep the bar lower than ideal and tighten over time.
- **Tests:** Checkstyle violations are surfaced as test failures in CI.
- **Done when:** `./gradlew checkstyleMain` passes with no violations.

**10.3 — Add SpotBugs rules for mutable statics**
- **What:** SpotBugs detection for `ST_WRITE_TO_STATIC_FROM_INSTANCE_METHOD`, `MS_MUTABLE_COLLECTION`, `MS_MUTABLE_ARRAY`. Configured in `config/spotbugs/exclude.xml` with exclusions for known-safe patterns.
- **Tests:** SpotBugs violations are surfaced as test failures (or warnings, with trend tracking).
- **Done when:** Static analysis runs as part of `./gradlew build` and the team reviews any new violations within 1 week.

**10.4 — Create the ADR (Architecture Decision Record) template and process**
- **What:** An ADR template in `docs/adr/TEMPLATE.md`. The template has 5 sections: Context, Decision, Consequences, Alternatives Considered, References. Require an ADR for: new module creation, new inter-module dependency, new external service integration, new event type in core.
- **Tests:** There is no automated test for ADRs. The review process enforces it.
- **Done when:** The `docs/adr/` directory exists with the template and 2 completed ADRs (retroactively for existing decisions).

**10.5 — Build the "fragmentation dashboard"**
- **What:** A simple script (`scripts/architecture-fitness.sh`) that reports: number of files touched by the last commit, number of modules touched, number of hardcoded `"NSE_EQ"` strings, number of `Thread.sleep()` calls in production code. Trend the numbers over time.
- **Tests:** The script runs without errors and produces a machine-readable output.
- **Done when:** The dashboard is part of CI and the team reviews trends weekly.

### Interfaces / Contracts Established This Phase

- ArchUnit boundary rules — automated layer enforcement
- Checkstyle rules — automated code style enforcement
- SpotBugs rules — automated bug pattern detection
- ADR process — architectural decision documentation
- Fragmentation dashboard — trend-based quality metric

### What Must NOT Be Built Yet

- No SonarQube (standalone tools are lighter)
- No full-time "architecture team" (governance is everyone's job)
- No rigid framework (ArchUnit rules can be relaxed by team consensus + ADR)

### Phase Gate — Dr. Venkat's Review Checklist

1. ☐ Does `./gradlew build` fail if someone adds a prohibited dependency?
2. ☐ Does `./gradlew build` fail if someone adds a mutable static collection?
3. ☐ Can I add a new ArchUnit rule in under 10 minutes?
4. ☐ Does the fragmentation dashboard produce a clear red/green signal?
5. ☐ Can I find the ADR for every module in the project within 2 minutes?
6. ☐ Are Checkstyle violations visible in CI output, not buried in a report?
7. ☐ If I join the team tomorrow, can I learn the architecture rules in 10 minutes by reading the ArchUnit test?

### Known Risks and Mitigation

| Risk | Detection | Mitigation |
|------|-----------|------------|
| ArchUnit tests become a maintenance burden (too many, too slow) | Build time increases | Categorize rules: "fast" (pure structural, run every build), "slow" (full classpath scan, run in nightly CI) |
| Team ignores ADRs as paperwork | ADR directory is empty after 1 month | Only require ADRs for changes that touch >1 module; write the first 3 ADRs as a team exercise |
| Fragmentation dashboard numbers trend upward but no one acts | Metrics are reported but ignored | Set thresholds: ">5 files touched per commit triggers mandatory review step"; review at sprint retrospectives |

---

## "What I Will Hold The Team To"
### — Dr. Venkat's Non-Negotiable Principles

**1. No test, no merge.**
If the test wasn't written with or before the production code, it doesn't exist. "I tested it manually" is not a test. A test is an automated assertion you can run 100 times in 1 second.

**2. If a class has more than one reason to change, split it.**
Before you merge, ask: "If I change X, does this class need to change? If I change Y, does it also need to change?" If the answer is yes to both, the class has multiple responsibilities.

**3. Domain code must compile without Spring, without a database, and without a broker.**
If I run `javac` on your domain package and it fails because some broker class is missing, you've coupled the wrong things. Domain depends on nothing.

**4. Same path, backtest and live.**
If the code path that executes a trade in backtest is different from the path that executes it live, you are not testing what you think you're testing. The strategy, risk, and sizing logic must be identical. Only the "fill model" and "order transport" differ.

**5. Every public interface must have a contract test.**
A contract test is one that any implementation of the interface can pass. It proves the interface contract is well-defined. If you can't write a contract test for an interface, the interface is too vague or too coupled.

**6. Broker types must never appear in domain code.**
If I see `DhanBrokerConnection`, `UpstoxSubscriptionRequest`, or any broker-specific import inside `core/`, `trading/`, or `pipeline/`, the PR is rejected. The broker abstraction is a one-way door: domain code calls into broker-api ports; broker implementations implement those ports. That is all.

**7. Static mutable state is technical debt from day one.**
`RuntimeModeHolder`. `RuntimeBusHolder`. These are globals with convenient names. They make testing non-deterministic and force test order dependencies. Every sprint, eliminate at least one static holder. By Phase 5, there should be zero.

**8. If you cannot name the single responsibility of a module in one sentence, you have too many modules.**
"`trading-execution` handles order execution, risk, and subscription management" means it needs to be split. One sentence, one responsibility.

**9. A refactoring that leaves the code worse than it found it is not a refactoring.**
Martin Fowler's rule: a refactoring is a behavior-preserving transformation that improves the design. If you're changing behavior while restructuring, you're doing two things at once. Stop. Do one at a time.

**10. The parity test is sacred.**
The test that asserts backtest output equals replay output is the most important test in the system. If it breaks, everything stops. You fix it, you understand why, and you add a test that would have caught the break. No exceptions.

---

*"A plan is nothing. Planning is everything." — Dwight D. Eisenhower*

This plan will change the moment you start implementing. The phase boundaries are rubber, not steel. The principles above are steel. Hold to those, and the codebase will tell you when it's ready for the next step.

— Dr. Venkat
