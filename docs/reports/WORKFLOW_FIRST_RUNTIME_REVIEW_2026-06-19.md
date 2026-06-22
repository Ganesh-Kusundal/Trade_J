# Workflow-First Runtime Review

Date: 2026-06-19

Scope: Trade_J runtime, scanner, execution, broker, reconciliation, and data flow.

Review stance: real-money trading safety. No patch-level fixes are proposed until the end-to-end flow is explicit and validated.

## 1. System Intent

The system should solve one visible trading problem:

```text
NIFTY500 data -> 09:45 scan -> Top 3 -> strategy decision -> orders -> portfolio state -> broker/review
```

The business workflow should be understandable without knowing the platform internals:

```text
candidates = scan(marketSnapshotAt0945)
selected = rank(candidates).top(3)
decisions = strategy.evaluate(selected, portfolioContext)
orders = execution.execute(decisions, readiness)
portfolio = portfolio.apply(fills)
review = reviewSession(portfolio, decisions, marketData)
```

### Expected Behavior Contract

Inputs:

- Canonical NIFTY500 instruments.
- A market snapshot or candle set at an explicit decision time, for example 09:45 IST.
- Strategy configuration and portfolio/risk context.
- Runtime source: historical, replay, paper, or live.
- Execution side-effect adapter: simulated or broker-backed.

Outputs:

- Ranked scan candidates.
- Explicit strategy decisions.
- Accepted, rejected, or suppressed orders.
- Fill-derived portfolio state.
- Reconciliation result.
- Persisted audit trail for scan, decision, order, fill, position, and review.

Timing guarantees:

- Scanner reads one consistent market state for the decision time.
- Strategy consumes only selected candidates.
- Execution happens only after readiness passes.
- Exit time is explicit, for example 15:15 IST.
- Replay and backtest advance by the same workflow clock, not by incidental wall-clock calls.

State transitions:

```text
universeReady
  -> snapshotReady
  -> candidatesProduced
  -> candidatesRanked
  -> strategyDecisionMade
  -> riskAccepted | signalSuppressed
  -> orderSubmitted
  -> orderAccepted | orderRejected
  -> fillReceived
  -> positionOpen
  -> exitDecisionMade
  -> positionClosed
  -> reconciled
  -> reviewed
```

Failure modes:

- Missing universe data: fail the scan, do not trade.
- Unresolved instrument identity: exclude the instrument and report it.
- Stale or mixed market data: fail the decision cycle.
- Unwired workflow: fail startup before broker transport connects.
- Broker unavailable or broker kill switch unknown: fail closed.
- Reconciliation mismatch: halt before next order when policy requires it.
- Queue overflow or event drop: surface as a trading decision failure, not just a log line.

## 2. Current Architecture Map

### Production Spring Runtime

Primary entry point:

- `app/src/main/java/com/tradej/app/TradingApplication.java`
- `app/src/main/java/com/tradej/app/config/StartupConfiguration.java`
- `app/src/main/java/com/tradej/app/startup/BrokerStartupOrchestrator.java`

Runtime wiring:

- `RuntimeConfiguration` creates the primary `EventBus` as `SimpleEventBus`.
- `MarketDataPipeline` and `OrderPipeline` publish broker WebSocket events into that bus.
- `BrokerStartupOrchestrator` wires persistence, audit, read models, position projection, reconciliation alerts, and DAG ingress.
- Startup starts the event bus, rebuilds OMS state, runs reconciliation, rebuilds position state, and then connects broker transport.

Observed production event flow:

```text
Broker WebSocket
  -> MarketDataPipeline / OrderPipeline
  -> SimpleEventBus
  -> persistence, audit, read model, net position, reconciliation alert, DAG ingress
```

This is not a complete trading workflow by itself. It persists and distributes events, but it does not prove that scan results become strategy decisions, that strategy decisions become risk-approved signals, or that signals reach the OMS.

### Certified Hot Path Runtime

Hot-path graph:

- `pipeline/runtime/src/main/java/com/tradej/pipeline/service/PipelineRuntimeService.java`
- Default graph: `risk-1 -> candle-1 -> feature-1 -> strategy-1 -> execution-1 -> reactor-1`

Compiled hot path:

- `runtime/disruptor/src/main/java/com/tradej/disruptor/DisruptorEventBus.java`
- `DisruptorEventBus` calls `compileHotPath(...)` when configured to compile on init.
- `DisruptorEventBus.start()` starts `ExecutionHandler`.

Observed hot-path flow:

```text
DomainEvent
  -> Disruptor ring
  -> compiled graph runtime
  -> risk
  -> candle/features
  -> strategy
  -> OMS
  -> async dispatch
```

The hot path has the shape of a trading runtime, but the Spring production runtime does not use it as the primary event bus.

### Scanner And Data Flow

Data sources:

- Equity bars: parquet under historical equity paths.
- Rolling options: DuckDB warehouse.
- Analytics: DuckDB federation over parquet and options.
- Live snapshots and option chains: broker adapters.

Scanner paths:

- `ScanService` routes between REST snapshot scan, option liquidity scan, and parquet institutional scan.
- `ScanEngine` evaluates broker snapshots and criteria.
- `InstitutionalScanEngine` computes historical parquet features and candidate selection.
- `OptionLiquidityScanner` ranks option contracts.
- DAG scanner nodes emit streaming scan events.
- `DuckDbScanStore` persists imperative scan results.

Decision representation:

- `ScanHit` is the closest current domain decision object.
- `ScanResult` and `ScanRun` represent a run and ordered hits.
- Pipeline scanner events and REST scan persistence are not a single handoff into strategy.

### Execution And Safety

Execution:

- `PositionRiskHandler` converts `SignalGenerated` into `SignalPendingExecution` when invoked through a graph or direct call.
- `ExecutionHandler` consumes `SignalPendingExecution`, queues commands, places orders through `OrderPlacementUseCase`, and emits order outcomes.
- `OrderManagementService` gates simulated vs broker placement by `RuntimeMode`.

Risk and kill switch:

- `PositionRiskHandler` tracks local kill switch and reconciliation halt state.
- `KillSwitchCoordinator` tries to synchronize OMS/broker kill switch.
- Admin/CLI and circuit-breaker paths can bypass the same state transition contract.

Reconciliation:

- `ReconciliationUseCase` invokes expected-vs-broker and OMS-vs-broker reconciliation.
- `OrderReconciler` emits `PositionMismatch`.
- `ReconciliationAlertLogger` evaluates policy and publishes `ReconciliationHaltRequired`.
- `TradingConfiguration` subscribes `ReconciliationHaltRequired` to `PositionRiskHandler`.

## 3. End-To-End Execution Flow

### Expected 09:45-to-Exit Cycle

Assume stored or live market data exists for NIFTY500 and the trader wants top 3 candidates.

1. Startup loads canonical instruments and validates the active universe.

State:

```text
universeReady = true
canonicalIdentity = StandardInstrumentIdentityService
clock = MarketTime.IST backed TradingClock
positions = rebuilt from fills
killSwitch = verified platform and broker state
```

2. At 09:45, the runtime source produces one decision snapshot.

State:

```text
snapshot.time = 09:45 IST
snapshot.symbols = NIFTY500 canonical symbols
snapshot.completeness = complete or explicitly failed
snapshot.staleness = within policy
```

3. Scanner computes candidates.

Expected:

```text
ScanDecision(symbol, score, reasons, featureValues, snapshotTime)
```

Actual:

- REST scans produce `ScanHit` from broker snapshots.
- Parquet scans produce institutional `ScoredBar`, then map it to `ScanHit`.
- Option liquidity scans produce `OptionContractHit`, then map to `ScanHit`.
- Streaming DAG scans can publish scanner events.

Mismatch:

- `ScanHit` is persisted and optionally promoted to subscriptions, but there is no mandatory handoff into a strategy decision step.
- `PARQUET_HISTORICAL` scan calls institutional scan over the latest available trading day and passes `null` as universe filter, so the configured profile universe is not enforced in that path.

4. Ranking selects top 3.

Expected:

```text
selected = rank(candidates).limit(3)
```

Actual:

- REST scan ranking is local to `ScanEngine` / ranker.
- Institutional scan ranking is handled by institutional engines.
- Option scan ranking is separate.

Mismatch:

- Ranking is not a single workflow step. Different engines own the score contract.

5. Strategy evaluates selected candidates.

Expected:

```text
decision = strategy.evaluate(selectedCandidate, portfolioContext)
```

Actual:

- Strategy execution is represented in pipeline graph nodes and `GraphStrategySandbox`.
- Production Spring startup does not assert that scan output flows into strategy.
- `PositionRiskHandler` can process `SignalGenerated`, but production subscriptions only wire it directly for trade lifecycle events and reconciliation halt. Signal handling depends on graph execution.

Mismatch:

- A scan can complete and publish without proving a trade decision was evaluated.

6. Risk readiness gates side effects.

Expected:

```text
readiness = {
  runtimeModeAllowsSideEffects,
  killSwitchDisengagedAndVerified,
  reconciliationHealthy,
  clockSelected,
  positionStateLoaded,
  orderIdentityReady,
  brokerPreflightPassed
}
```

Actual:

- Runtime mode is explicit through `ExecutionModePolicy`.
- Broker preflight and OMS replay occur before transport connect.
- No single readiness object verifies workflow wiring, kill-switch truth, clock authority, reconciliation health, and execution queue state before connecting.

Mismatch:

- Startup can complete while the complete business workflow is not deployable.

7. Execution places or simulates orders.

Expected:

```text
LIVE -> broker order adapter
REPLAY/BACKTEST/PAPER -> simulated adapter
same workflow, different side-effect adapter
```

Actual:

- `OrderManagementService.placeOrder()` branches on `RuntimeMode` for simulated execution.
- `PaperBrokerConnection` and `BacktestBrokerConnection` also exist as separate broker-style simulation paths.
- `OrderPlacementUseCase` places broker calls through `CompletableFuture.supplyAsync()` on the common pool and treats timeout as failure even if the broker side effect later completes.

Mismatch:

- Runtime parity is not guaranteed because source, event routing, and execution side effects can vary independently.

8. Portfolio state updates and reconciliation run.

Expected:

```text
fill -> portfolio state -> net position -> reconciliation -> halt or healthy
```

Actual:

- Net position updates from trade events.
- OMS state rebuilds from event-sourced order repository.
- Reconciliation compares expected/broker and OMS/broker states.

Mismatch:

- Reconciliation exceptions are logged and swallowed.
- OMS-vs-broker reconciliation compares each terminal order's filled quantity to aggregate broker position by symbol, which is structurally unsafe for multiple orders in the same symbol.

9. Exit at 15:15 and review.

Expected:

```text
exitDecision -> exitOrder -> fill -> positionClosed -> reviewed
```

Actual:

- The code has order lifecycle, fills, portfolio, PnL, and reconciliation components.
- The user workflow does not appear as one readable orchestration unit.

Mismatch:

- Review is an emergent set of persisted events and read models, not a first-class workflow outcome.

## 4. Invariant Checklist

Required invariants:

- One workflow controls scan, rank, strategy, execution, portfolio, exit, and review.
- Backtest, replay, paper, and live share workflow logic.
- Only runtime source and side-effect adapter vary by mode.
- Startup fails closed if workflow wiring is incomplete.
- Startup fails closed if kill switch state cannot be verified.
- Startup fails closed if reconciliation is unhealthy.
- Startup fails closed if the clock authority is ambiguous.
- Scanner output is a domain decision, not only persistence or subscription promotion.
- No broker order is possible without readiness.
- Event drops, queue overflow, stale data, and broker failures are visible to the workflow.
- Position state, order identity, and instrument identity each have one authoritative owner.

Current enforcement:

- Runtime side effects are partly enforced by `ExecutionModePolicy` and `OrderManagementService`.
- LIVE forbids NoOp fallback infrastructure in some paths.
- Startup phases are explicit.
- Reconciliation policy can emit a halt event.

Current gaps:

- Production Spring runtime does not prove that the hot path is compiled or that strategy-to-OMS is reachable.
- `ExecutionHandler.start()` is tied to `DisruptorEventBus.start()`, not to a generic execution-readiness contract.
- Scanner-to-strategy handoff is implicit or absent.
- Kill switch success semantics are ambiguous.
- Reconciliation can fail by logging only.
- Clock authority is split between `Clock`, `TradingClock`, `VirtualClock`, broker timestamps, and wall-clock calls.

## 5. Failure And Risk Points

### What Can Go Wrong Silently

1. Live ticks can be persisted and displayed without driving strategy or OMS.

`RuntimeConfiguration` makes `SimpleEventBus` primary. `DisruptorEventBus` is the component that compiles the hot-path graph. `BrokerStartupOrchestrator` subscribes DAG ingress for ticks and candles, but it does not compile or verify the default hot path.

2. A scan can produce top candidates without creating tradable decisions.

`ScanService.persistAndPromote()` saves hits, publishes gateway output, and may promote WebSocket subscriptions. It does not hand off top candidates into a strategy contract.

3. LIVE can pass startup without an execution path readiness proof.

Startup validates broker catalog, subscriptions, tokens, and preflight. It starts the event bus and reconnects transport. It does not prove that:

- `PipelineRuntimeService` has deployed a hot-path graph.
- `ExecutionHandler` is running.
- strategy decisions can reach `OrderManagementService`.
- scanner output is linked to strategy input.

4. Kill switch can diverge between platform and broker.

`KillSwitchCoordinator.engage()` logs broker API failures and does not throw. `OrderManagementService.activateKillSwitch()` is a broker-side call only. `PositionRiskHandler` has local state. The load-balanced broker gateway returns success if any broker accepts kill switch, not if all do.

5. Reconciliation can degrade to logs while trading continues.

`ReconciliationUseCase` catches reconciliation failures and logs them. `OrderReconciler.reconcileAll()` treats broker position fetch failure as all zeros, then continues.

6. OMS reconciliation can report false mismatches or miss real ones.

The current OMS-vs-broker logic compares each terminal order's filled quantity against aggregate broker position for that symbol. Multiple entries, exits, partial fills, and netting make that comparison unsafe.

7. Clock drift can alter live/replay behavior.

`MarketTime` defines IST, but Spring `Clock` uses the JVM default zone. `TradingClock`, `VirtualClock`, broker timestamps, and wall-clock calls coexist. Replay/backtest cannot guarantee identical timing decisions until one workflow clock owns the cycle.

8. Order placement timeout can create unknown broker state.

`OrderPlacementUseCase` cancels a future after timeout, but the broker call may already be in flight or completed. The local system records a failure while the broker may hold a live order.

9. Queue overflow and event dropping can be normal control flow.

`DisruptorEventBus` can dead-letter and drop downstream events. `ExecutionHandler` suppresses signals when full. Those may be acceptable, but the trading workflow must see them as explicit failed decisions.

10. Multi-broker order routing can split lifecycle ownership.

`LoadBalancedBrokerGateway` places, modifies, and cancels through failover. Without a broker-owned order identity invariant, an order can be placed on one connection while a later lifecycle action routes elsewhere.

### What Will Break Under Real-Time Conditions

- Synchronous `SimpleEventBus` subscribers can block broker WebSocket handling.
- Strategy certification on the Disruptor path will not prove production Spring behavior.
- High tick rate can surface event drops or stale scanner decisions without halting trading.
- Broker latency can create duplicate local/broker truth after timeout.
- Reconciliation intervals can leave a mismatched position tradable until the next scheduled run.
- Subscription promotion can increase market-data load without ensuring execution readiness.

### Unsafe Assumptions

- If events are published, trading is happening.
- If a scan hit is promoted, it is connected to strategy.
- If the kill switch log says engaged, the broker is halted.
- If reconciliation logged an error, the system is safe.
- If tests pass on Disruptor, Spring LIVE follows the same path.
- If runtime mode is correct, all side effects are correctly gated.
- If a broker position exists by symbol, it can be compared to each terminal order.

### Implicit Instead Of Explicit Behavior

- Workflow wiring.
- Scanner-to-strategy handoff.
- Execution handler lifecycle.
- Kill switch truth.
- Reconciliation health.
- Clock authority.
- Runtime source ownership.
- Broker order identity ownership.

## 6. Proposed Correct Architecture

### Core Shape

Replace the platform-first mental model with a workflow-first core:

```text
RuntimeSource
  -> MarketSnapshot
  -> FeatureSet
  -> Scanner
  -> Ranking
  -> Strategy
  -> ReadinessGate
  -> ExecutionPort
  -> PortfolioState
  -> Reconciliation
  -> Review
```

### Business Contracts

`TradingWorkflow`:

```text
run(TradingSession session, RuntimeSource source, ExecutionPort execution)
  -> TradingSessionResult
```

Owns:

- decision schedule, such as 09:45 scan and 15:15 exit.
- top-N selection.
- strategy evaluation.
- readiness check before side effects.
- portfolio/reconciliation checkpoints.
- review output.

Does not own:

- broker adapter details.
- event-bus implementation.
- graph editing.
- persistence mechanics.

`RuntimeSource`:

```text
snapshotAt(time, universe) -> MarketSnapshot
stream(universe) -> MarketEventStream
clock() -> TradingClock
```

Implementations:

- `DuckDbHistoricalSource`
- `ReplayJournalSource`
- `LiveBrokerSource`
- `PaperSource`

The workflow does not change when the source changes.

`ExecutionPort`:

```text
execute(OrderIntent, ExecutionReadiness) -> ExecutionResult
cancel(OrderId) -> CancelResult
killSwitch(state) -> VerifiedKillSwitchState
```

Implementations:

- `BrokerExecutionPort`
- `SimulatedExecutionPort`

The workflow does not branch between live, replay, backtest, and paper. Only the port changes.

`ExecutionReadinessGate`:

```text
verify(session) -> Ready | NotReady(reason)
```

Checks:

- runtime mode and side-effect permissions.
- workflow is wired.
- execution worker is running.
- one clock is selected.
- instrument identity service is ready.
- position state is rebuilt.
- order identity registry is ready.
- reconciliation is healthy.
- kill switch state is known and disengaged.
- broker preflight passed if live.

`ScanDecision`:

```text
symbol
exchangeSegment
score
rank
reasons
features
snapshotTime
source
```

This can initially wrap or replace `ScanHit`. The key rule is that it is the single handoff to strategy, not only a persistence record.

### What To Keep

- `ExecutionModePolicy`: good explicit mode contract.
- `MarketTime`: good canonical IST market-time foundation.
- `StandardInstrumentIdentityService`: good canonical identity foundation.
- `ScanHit` / `ScanResult`: useful if promoted to workflow decisions.
- `OrderManagementService`: useful as order lifecycle owner if side effects are moved behind `ExecutionPort`.
- `ReconciliationPolicy`: useful if reconciliation failure becomes fail-closed.
- DuckDB analytics: useful for declarative historical snapshots, ranking, and review.

### What To Move Behind The Workflow

- Event bus choice.
- DAG and graph runtime mechanics.
- Scanner engine variants.
- Broker routing and capability lookup.
- Persistence writers.

### What To Remove Or Collapse After Validation

- Parallel runtime roots that assemble different execution truths.
- Scanner factory/manager/registry surfaces that do not support runtime invariants.
- Separate backtest/replay/paper/live control flows.
- Duplicate historical query stacks.
- Thin registries that are only maps and not lifecycle contracts.
- Kill switch paths that bypass a single state machine.

## 7. Migration Plan

### Slice 1: Workflow Contract And Readiness Report

Add a read-only architecture contract first:

- Define `TradingWorkflow` as the system boundary on paper and then in code.
- Map current classes into each workflow step.
- Add a startup diagnostic that reports whether the current app can execute the full workflow.
- Do not place orders from this diagnostic.

Integration validation:

- Boot with real app components and stored market data.
- Verify the readiness report fails when the hot path or workflow handoff is absent.

### Slice 2: Single Decision Object

Make scanner output a workflow decision:

- Wrap current `ScanHit` as `ScanDecision`.
- Enforce top-N selection as one workflow step.
- Preserve existing persistence by adapting `ScanDecision` back to `ScanHit` where needed.

Integration validation:

- Run the real configured NIFTY500 historical scan.
- Assert a decision set with rank, score, reasons, snapshot time, and source.

### Slice 3: Workflow Runner Without Broker Side Effects

Introduce a workflow runner using real DuckDB/parquet data and simulated execution port:

```text
DuckDbHistoricalSource -> TradingWorkflow -> SimulatedExecutionPort -> PortfolioState
```

Integration validation:

- Use real stored bars.
- No mocks or dummy data.
- Verify scan, rank, strategy decision, simulated fill, position state, and review output.

### Slice 4: Execution Readiness Gate

Add the fail-closed readiness gate before broker transport connect.

Checks:

- workflow route deployed.
- execution worker lifecycle known.
- clock authority selected.
- position state rebuilt.
- reconciliation health known.
- kill switch verified across platform and broker.
- broker preflight passed.

Integration validation:

- LIVE startup refuses to connect broker transport when any readiness item is unknown.
- REPLAY/BACKTEST can run with simulated port but still require clock and position readiness.

### Slice 5: Source Adapters

Collapse runtime differences behind `RuntimeSource`.

Adapters:

- historical source from DuckDB/parquet.
- replay source from journal.
- live source from broker market data.
- paper source reusing live market data with simulated execution.

Integration validation:

- Same workflow produces comparable decisions from historical and replay data for the same time window.
- Live source does not change strategy or execution workflow code.

### Slice 6: Execution Ports

Collapse side-effect differences behind `ExecutionPort`.

Adapters:

- broker execution.
- simulated execution.

Rules:

- Broker timeout creates `UnknownBrokerOrderState`, not ordinary suppression.
- Kill switch result must be verified or fail closed.
- Multi-broker order identity must pin lifecycle operations to the broker that accepted the order.

Integration validation:

- Simulated port uses real historical prices.
- Broker port can run a preflight-only check without placing orders.
- Kill switch failure prevents readiness.

### Slice 7: Remove Parallel Runtime Control Flow

After workflow parity is proven:

- Move Disruptor/DAG/event bus details behind workflow adapters.
- Retire separate backtest/replay/paper/live runners that duplicate control flow.
- Keep graph runtime only if it enforces latency or operational needs without hiding the workflow.

Integration validation:

- One workflow test matrix runs historical, replay, paper, and live-preflight with the same assertions.

### Slice 8: Collapse Infrastructure Abstractions

Delete or simplify abstractions only when each is proven nonessential:

- managers that only forward calls.
- registries that only hold local maps.
- factories that only switch on a type with no lifecycle.
- duplicate historical query stacks.
- no-op fallbacks in live paths.

Integration validation:

- Removing an abstraction must not change workflow outputs for the same real input data.

## Final Architectural Rule

The code should make this business sentence true:

```text
At 09:45, read one NIFTY500 market snapshot, rank the top 3 opportunities, evaluate the strategy, pass readiness, execute through the selected port, update portfolio state, exit explicitly, reconcile, and review.
```

If a future scanner requires touching managers, registries, factories, graph editors, startup orchestration, and broker wiring, the architecture has drifted away from the trading problem.

