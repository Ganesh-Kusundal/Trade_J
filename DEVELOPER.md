# Trade-J Developer Guide

> Read this first. It is the only documentation a new developer needs to
> become productive in two days.

## 1. Five-minute quick start

```bash
# Backend
./gradlew :app:bootRun                   # Spring Boot backend on :8080

# Frontend (in a second terminal)
cd trade_j_frontend && pnpm install && pnpm dev    # Vite dev server on :5173

# Or both via the dev composition (one command)
./gradlew :app:runDevProfile              # Spring dev profile + UI

# Or the fastest path: a one-command local workflow
./gradlew :cli:run --args="dev"
#   → starts simulation broker, one strategy, one scanner, replay last 7d SBIN
```

The backend serves REST at `http://localhost:8080/api/v1/*` and a binary
WebSocket at `ws://localhost:8080/ws/gateway`.

## 2. Where do I add a new strategy?

Three steps, one file.

1. Implement [`GraphStrategyPlugin`](file:///Users/apple/Downloads/Trade_J/trading/strategy/src/main/java/com/tradej/strategy/api/GraphStrategyPlugin.java) in a new class under
   `trading-strategy/src/main/java/com/tradej/strategy/example/`.
2. Append the fully-qualified class name to
   [`META-INF/services/com.tradej.strategy.api.GraphStrategyPlugin`](file:///Users/apple/Downloads/Trade_J/trading/strategy/src/main/resources/META-INF/services/com.tradej.strategy.api.GraphStrategyPlugin).
3. Re-run `./gradlew :app:bootRun`. The strategy is auto-discovered, runs in
   a virtual-thread sandbox, and is observable in the Strategy Catalog page.

Do not touch the gateway, the broker, the event bus, or any composition
code. The SPI is the *only* contract.

## 3. Where do I add a new scanner?

Two steps, one file.

1. Implement [`ScannerProvider`](file:///Users/apple/Downloads/Trade_J/trading/scanner/src/main/java/com/tradej/scanner/spi/ScannerProvider.java) in a class under
   `trading-scanner/src/main/java/com/tradej/scanner/`.
2. Append the FQN to
   [`META-INF/services/com.tradej.scanner.spi.ScannerProvider`](file:///Users/apple/Downloads/Trade_J/trading/scanner/src/main/resources/META-INF/services/com.tradej.scanner.spi.ScannerProvider).
3. Add the scanner to a scan profile in
   [`config/scan-profiles.json`](file:///Users/apple/Downloads/Trade_J/config/scan-profiles.json) or in the Spring config
   `trade.scan.profiles`.
4. Re-run. The scanner is wired into the same `ScanService` and its hits
   land in the dashboard via the existing `ScanResultsPublished` event.

## 4. Where do I add a new dashboard?

Two steps, two files.

1. Add a `DashboardConfig` to
   [`trade_j_frontend/src/domain/dashboards.ts`](file:///Users/apple/Downloads/Trade_J/trade_j_frontend/src/domain/dashboards.ts). Pick widget
   types from the existing `WidgetType` union.
2. Register the layout id in
   [`trade_j_frontend/src/components/TerminalLayout.tsx`](file:///Users/apple/Downloads/Trade_J/trade_j_frontend/src/components/TerminalLayout.tsx) (`DASHBOARD_LAYOUTS`).

If you need a new widget type:

1. Create the React component (e.g. `src/components/MyNewWidget.tsx`).
2. Export it as `default` from the file.
3. Add the type to the `WidgetType` union in
   [`trade_j_frontend/src/domain/dashboard.ts`](file:///Users/apple/Downloads/Trade_J/trade_j_frontend/src/domain/dashboard.ts).
4. Register it in
   [`trade_j_frontend/src/domain/widgetRegistry.ts`](file:///Users/apple/Downloads/Trade_J/trade_j_frontend/src/domain/widgetRegistry.ts).

## 5. Where do I add a new event topic?

Two steps, one file each.

1. Add a constant to
   [`GatewayTopic`](file:///Users/apple/Downloads/Trade_J/gateway/src/main/java/com/tradej/gateway/protocol/GatewayTopic.java)
   (Java) and the mirrored enum in
   [`GatewayFeedManager.ts`](file:///Users/apple/Downloads/Trade_J/trade_j_frontend/src/api/GatewayFeedManager.ts)
   (TypeScript). The codegen task `:gateway:generateBridge` will eventually
   remove the duplication.
2. Subscribe the bridge to the new event class via
   [`GatewayEventBridge.buildSerializerMap()`](file:///Users/apple/Downloads/Trade_J/gateway/src/main/java/com/tradej/gateway/bridge/GatewayEventBridge.java).
   No edit is needed if the event is a domain event with a serializer.

The frontend subscribes to topics by enum name from
`MarketDataBus.subscribe`.

## 6. How do I test end-to-end?

Replay is the test bench. Every new workflow should ship with a replay
E2E test under
[`app/src/test/java/com/tradej/app/e2e/`](file:///Users/apple/Downloads/Trade_J/app/src/test/java/com/tradej/app/e2e/).

The pattern is:

1. Set up an `EventBus` and subscribe to the events the workflow emits.
2. Drive a fixed input (candles, ticks, or orders) through the
   [`ReplayController`](file:///Users/apple/Downloads/Trade_J/replay/engine/src/main/java/com/tradej/replay/engine/ReplayController.java)
   (or the composition [`ReplayService`](file:///Users/apple/Downloads/Trade_J/composition/src/main/java/com/tradej/composition/ReplayService.java)).
3. Assert the captured event sequence is deterministic.

The parametric pattern for *strategy* certification is in
[`ReplayParityEndToEndTest`](file:///Users/apple/Downloads/Trade_J/app/src/test/java/com/tradej/app/e2e/ReplayParityEndToEndTest.java):
the test iterates over `ServiceLoader.load(GraphStrategyPlugin.class)`
and asserts each strategy is replay-deterministic.

## 7. How do I run live?

The active profile controls broker selection:

| Profile | Broker | What it does |
| --- | --- | --- |
| `dev` | simulation | In-memory GBM price walks, no credentials needed |
| `dev-live` | dhan (sandbox or live) | Reads `dhan-local.properties` for token |
| `prod` | dhan (live) | Reads env vars; production-ready |
| `gateway` | multi-broker | Load-balanced across Dhan/Upstox/ICICI |
| `replay` | none | Offline replay only |
| `test` | simulation | CI integration tests |

The CLI accepts `--attach http://localhost:8080` to talk to a running app,
or runs standalone via `BrokerSessionFactory` + the `IBrokerConnection`
SPI. There is no separate "CLI broker" code path.

## 8. Where is the test config?

- Unit tests: `./gradlew test`
- Component tests: `./gradlew componentTest`
- Integration tests: `./gradlew integrationTest`
- Replay parity E2E: `./gradlew :app:test --tests *ReplayParityEndToEndTest`
- Architecture boundary tests: `./gradlew :architecture-test:architectureTest`
- All of the above: `./gradlew check`

## 9. Things to read on day one

1. [`TRADE_J_ARCHITECTURE_DIAGRAMS.md`](file:///Users/apple/Downloads/Trade_J/TRADE_J_ARCHITECTURE_DIAGRAMS.md) — the source of truth for modules, classes, and flows.
2. [`app/src/main/java/com/tradej/app/startup/BrokerStartupOrchestrator.java`](file:///Users/apple/Downloads/Trade_J/app/src/main/java/com/tradej/app/startup/BrokerStartupOrchestrator.java) — the platform's "main method" in the Spring path.
3. [`composition/src/main/java/com/tradej/composition/BrokerComposition.java`](file:///Users/apple/Downloads/Trade_J/composition/src/main/java/com/tradej/composition/BrokerComposition.java) — the head-less composition root.
4. [`runtime/disruptor/src/main/java/com/tradej/disruptor/DisruptorEventBus.java`](file:///Users/apple/Downloads/Trade_J/runtime/disruptor/src/main/java/com/tradej/disruptor/DisruptorEventBus.java) — the only place domain events are dispatched.
5. The 4 SPIs: `BrokerProvider`, `GraphStrategyPlugin`, `ScannerProvider`, `IndicatorProvider`. Everything plugs in via one of them.

## 10. Things you should *not* do

- Do not add a 4th broker. If you need a 4th, remove a broker port you don't actually use.
- Do not edit the bridge serializer table by hand; codegen it.
- Do not add new CLI root sub-commands. Add them under `tradej ops …`.
- Do not create a new dashboard framework. The widget registry is the source of truth.
- Do not create a new pipeline runtime. The `PipelineRuntimeService` is enough.
- Do not create a new event topic unless the producer and at least one consumer are wired in the same PR.
