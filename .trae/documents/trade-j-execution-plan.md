# Trade-J Platform — Execution Plan

> Reviewed by Dr. Venkat Subramaniam (Agile Developer, Inc. / Java Champion / FP & design).
> Audience: engineering team. This plan prioritises *deleting and finishing* over adding.

## 0. North-Star Question

> "Can Trade-J help us rapidly build and operate a professional quant trading platform with minimal complexity?"

**Answer after this plan: yes.** We are not adding capability; we are **finishing, surfacing, and deleting** so the capability that already exists becomes usable. The plan runs 12 weeks and is measured in *lines deleted + workflows completed*, not lines added.

## 1. Plan Principles (the "Venkat guardrails")

1. **Complete over comprehensive.** A finished small thing beats a half-finished big thing. Sprint goals are workflow completions, not feature PRs.
2. **Delete before build.** Every phase lists deletions *first*. If a deletion is contested, the *new* code is the one that loses.
3. **One source of truth per concept.** No two implementations of the same idea. Codegen the bridge table. Render the widget registry. One replay facade.
4. **Spring hosts the platform; the platform is not Spring.** The composition layer is sacred. Anything that forces Spring-only ownership is reworked.
5. **Replay is the test bench.** Every workflow acceptance test is a replay-driven end-to-end test, not a unit test. Acceptance = "replay produces the same PnL, signals, and risk events as live".
6. **Real data only.** No mocks, no local-random-walk, no synthetic-but-not-replicating-realism. Frontend paper-trading is removed.
7. **Production-ready means observable.** Every component exports the same three things: `metrics()`, `health()`, `lastEventAt()`.

## 2. The 5 Phases (12 weeks)

| Phase | Weeks | Theme | Definition of Done |
| --- | --- | --- | --- |
| **P1** | 1–2 | Foundation: delete duplicates, codegen the bridge, render the registry | All deletions in §3 merged; `./gradlew check` green; default UI renders the full widget registry |
| **P2** | 3–4 | One replay facade + WS control plane | Frontend [ReplayControls](file:///Users/apple/Downloads/Trade_J/trade_j_frontend/src/components/ReplayControls.tsx) drives [ReplayController](file:///Users/apple/Downloads/Trade_J/replay/engine/src/main/java/com/tradej/replay/engine/ReplayController.java) end-to-end; live-vs-replay parity test green |
| **P3** | 5–6 | Options analytics producer + 3 widgets | 4 events produced; 3 new widgets render; 2 scanners consume |
| **P4** | 7–9 | Risk/PnL/Strategy observability + per-strategy replay-parity certification | Composite Risk & PnL widget; per-strategy parity report; replay-as-test pattern documented |
| **P5** | 10–12 | Developer experience + production hardening | [DEVELOPER.md](file:///Users/apple/Downloads/Trade_J/) committed; `tradej dev` command works; codegen in Gradle; one-click kill switch; production readiness checklist signed |

The phases are **sequential**; reviewers must not allow new work to slip into a closed phase. If a phase slips, we cut scope, never time.

## 3. Phase 1 — Foundation (Weeks 1–2)

### Goal
Eliminate the four highest-friction duplications; make the widget registry the source of truth for the UI; codegen the gateway bridge serializer table; collapse 4 MCP tool classes to 1.

### Deletions (must land first)

1. **Delete** [trade_j_frontend/src/api/PaperTradingFeed.ts](file:///Users/apple/Downloads/Trade_J/trade_j_frontend/src/api/PaperTradingFeed.ts) (and its references in [TerminalDataOrchestrator.ts](file:///Users/apple/Downloads/Trade_J/trade_j_frontend/src/api/TerminalDataOrchestrator.ts#L226-L238)).
2. **Delete** the *REST-polling* branch of [trade_j_frontend/src/api/SimulationFeed.ts](file:///Users/apple/Downloads/Trade_J/trade_j_frontend/src/api/SimulationFeed.ts); keep only the *backend-simulation* path that talks to the broker gateway.
3. **Delete** the unused method `startPaperFeed` in [TerminalDataOrchestrator](file:///Users/apple/Downloads/Trade_J/trade_j_frontend/src/api/TerminalDataOrchestrator.ts) once [PaperTradingFeed](file:///Users/apple/Downloads/Trade_J/trade_j_frontend/src/api/PaperTradingFeed.ts) is removed.
4. **Delete** the `classify` duplication in [MarketDataApplicationService](file:///Users/apple/Downloads/Trade_J/app/src/main/java/com/tradej/app/service/MarketDataApplicationService.java) by routing through the bus uniformly (audit-only if no other code path remains).
5. **Delete** the second replay entry [HistoricalEventReplayService](file:///Users/apple/Downloads/Trade_J/data/persistence/src/main/java/com/tradej/persistence/replay/HistoricalEventReplayService.java) *behind the new facade* — keep the class but make it `package-private` and the only caller is [ReplayService](file:///Users/apple/Downloads/Trade_J/composition/src/main/java/com/tradej/composition/FullComposition.java).
6. **Delete** 4 of the 5 health indicators — keep only [OrderPipelineHealthIndicator](file:///Users/apple/Downloads/Trade_J/app/src/main/java/com/tradej/app/health/OrderPipelineHealthIndicator.java) and refactor it to delegate sub-checks.
7. **Delete** the `research-api` Gradle module; move its 2 controllers into `app`. (Remove from [settings.gradle](file:///Users/apple/Downloads/Trade_J/settings.gradle).)
8. **Delete** [MultiTimeframeContext](file:///Users/apple/Downloads/Trade_J/replay/engine/src/main/java/com/tradej/replay/engine/MultiTimeframeContext.java) (inline into [CandleReplaySession](file:///Users/apple/Downloads/Trade_J/replay/engine/src/main/java/com/tradej/replay/engine/CandleReplaySession.java)).
9. **Delete** the four MCP tool classes, replaced by one consolidated class.
10. **Delete** [TradeVisualization.tsx](file:///Users/apple/Downloads/Trade_J/trade_j_frontend/src/components/StrategyVisualization.tsx) if it is not wired into any default layout (audit first).

### Builds (small, single-purpose)

#### B1.1 — Make the widget registry render the default layout

- **File**: [trade_j_frontend/src/App.tsx](file:///Users/apple/Downloads/Trade_J/trade_j_frontend/src/App.tsx).
- **What**: Replace the hard-coded chart/orderbook/trades/watchlist block with a `<DashboardRenderer config={DEFAULT_TRADING_DASHBOARD}>` (already in [DashboardRenderer.tsx](file:///Users/apple/Downloads/Trade_J/trade_j_frontend/src/components/DashboardRenderer.tsx) and [DEFAULT_TRADING_DASHBOARD](file:///Users/apple/Downloads/Trade_J/trade_j_frontend/src/domain/dashboard.ts#L41-L76)).
- **Why**: [widgetRegistry.ts](file:///Users/apple/Downloads/Trade_J/trade_j_frontend/src/domain/widgetRegistry.ts) declares 16 widgets; only 5 render. Rendering 16 is the *single* highest-perceived-value change we can make.
- **How**:
  1. Extract the chart + order book + trades + watchlist block from [App.tsx](file:///Users/apple/Downloads/Trade_J/trade_j_frontend/src/App.tsx) into a new file `src/components/TerminalLayout.tsx` that consumes a `DashboardConfig`.
  2. Add a "Layout" dropdown to the status bar that switches between `DEFAULT_TRADING_DASHBOARD`, `BOTTOM_DASHBOARD_CONFIG` (existing), and a new `RESEARCH_DASHBOARD_CONFIG` (see B1.2).
  3. Save the active layout id in `localStorage` keyed by `STORAGE_KEYS.layout`; load on mount.
- **Acceptance**: All 16 widgets render in at least one of the three layouts. `widgetRegistry.ts` is the only place components are wired.

#### B1.2 — Add `RESEARCH_DASHBOARD_CONFIG` and `SCANNER_DASHBOARD_CONFIG`

- **File**: [trade_j_frontend/src/domain/dashboards.ts](file:///Users/apple/Downloads/Trade_J/trade_j_frontend/src/domain/dashboards.ts).
- **What**: Two new exports:
  - `RESEARCH_DASHBOARD_CONFIG`: 6 widgets — chart, watchlist, scanner, portfolio, news, alerts.
  - `SCANNER_DASHBOARD_CONFIG`: 4 widgets — scanner (large), watchlist, optionchain, market-overview.
- **Why**: The "use the registry" pattern is meaningless if the registry is only the chart+orderbook+trades layout. Other layouts *exist* in the user's mental model from day one.
- **Acceptance**: `RESEARCH_DASHBOARD_CONFIG` renders without errors; same for `SCANNER_DASHBOARD_CONFIG`.

#### B1.3 — Codegen the [GatewayEventBridge](file:///Users/apple/Downloads/Trade_J/gateway/src/main/java/com/tradej/gateway/bridge/GatewayEventBridge.java) serializer table

- **Why**: The hand-written `Map.ofEntries(...)` block at [GatewayEventBridge.java#L71-L96](file:///Users/apple/Downloads/Trade_J/gateway/src/main/java/com/tradej/gateway/bridge/GatewayEventBridge.java#L71-L96) is the platform's biggest "edit this when adding an event" trap. Codegen it.
- **How**:
  1. Create `:gateway:generateBridge` Gradle task that:
     - Scans `com.tradej.core.domain.event.*Event` for classes annotated `@BridgedTopic(GatewayTopic topic, String jsonProperty)`.
     - Emits a Java file `src/generated/java/com/tradej/gateway/bridge/BridgeSerializers.java` containing the `Map<Class<? extends DomainEvent>, SerializerEntry>`.
  2. Add `@BridgedTopic` to each event class that should be bridged (start with the 14 already in the table).
  3. Replace the hand-written `buildSerializerMap` with `return BridgeSerializers.MAP;`.
  4. Add an `assert BridgeSerializers.MAP.size() == bridgedEventCount` at boot in test scope.
- **Acceptance**: Adding a 15th event + `@BridgedTopic` annotation requires *zero* edits to the bridge.

#### B1.4 — One consolidated MCP tool class

- **What**: Replace [MarketDataTools](file:///Users/apple/Downloads/Trade_J/mcp-server/src/main/java/com/tradej/mcp/tools/MarketDataTools.java), [SyncTools](file:///Users/apple/Downloads/Trade_J/mcp-server/src/main/java/com/tradej/mcp/tools/SyncTools.java), [EquityAnalyticsTools](file:///Users/apple/Downloads/Trade_J/mcp-server/src/main/java/com/tradej/mcp/tools/EquityAnalyticsTools.java), [OptionsAnalyticsTools](file:///Users/apple/Downloads/Trade_J/mcp-server/src/main/java/com/tradej/mcp/tools/OptionsAnalyticsTools.java) with one `AnalyticsTools.java` whose methods are `listEquitySymbols`, `equityCandles`, `equityRange`, `optionsChain`, `optionsMaxPain`, `optionsGreeks`, `optionsSurface`, `syncStatus`, `syncTrigger`, `marketLtp`, `marketQuote`, `marketDepth`.
- **Why**: 4 classes for 12 methods is a SPI mistake. One class, 12 `@McpTool` methods, one place to read.
- **Acceptance**: All 12 tools still discoverable; `tools/list` returns same names; existing MCP tests pass.

### Verifications for Phase 1

- `./gradlew check` (SpotBugs + Checkstyle + all unit tests) — green.
- `./gradlew :app:bootRun` then open `/` — all 16 widgets reachable.
- `./gradlew :gateway:generateBridge` regenerates `BridgeSerializers.java` deterministically.
- MCP `tools/list` returns the 12 tools.
- Frontend test: `pnpm test` for `e2e-flow.test.ts`, `widget-registry.test.ts`, `architecture-regression.test.ts` — all green.

### Reviewer (Dr. Venkat) checks

- [ ] Are the deletions *actually* dead? (Re-grep the codebase for references after each delete.)
- [ ] Is the codegen purely additive? (No runtime reflection of "what events exist".)
- [ ] Does the registry render with zero custom wiring in [App.tsx](file:///Users/apple/Downloads/Trade_J/trade_j_frontend/src/App.tsx)?
- [ ] Did the team avoid the temptation to "just add a widget" without deleting one?

---

## 4. Phase 2 — One Replay Facade + WS Control Plane (Weeks 3–4)

### Goal
A single `ReplayService` is the only public entry point. The frontend [ReplayControls](file:///Users/apple/Downloads/Trade_J/trade_j_frontend/src/components/ReplayControls.tsx) drives it through the WebSocket control plane. The `TODO` at [App.tsx#L261-L279](file:///Users/apple/Downloads/Trade_J/trade_j_frontend/src/App.tsx#L261-L279) is gone.

### Deletions

11. **Delete** the `replayCandles` HTTP path used by the frontend (kept only as a server-side test helper). Frontend goes through WS.
12. **Delete** the three REST endpoints in [ReplayStudioController](file:///Users/apple/Downloads/Trade_J/app/src/main/java/com/tradej/app/api/ReplayStudioController.java) (kept for backward compat, marked deprecated; remove in 1 release).
13. **Delete** the now-redundant `ReplayableRegistry` if its only consumer is [ReplayOrchestrator](file:///Users/apple/Downloads/Trade_J/replay/engine/src/main/java/com/tradej/replay/engine/ReplayOrchestrator.java).
14. **Delete** the local `start/stop/pause` handlers in [App.tsx](file:///Users/apple/Downloads/Trade_J/trade_j_frontend/src/App.tsx#L227-L280) and the [ReplayControls](file:///Users/apple/Downloads/Trade_J/trade_j_frontend/src/components/ReplayControls.tsx) `onReplayPause/onReplayResume/onReplayStop` props that are no longer needed.

### Builds

#### B2.1 — `ReplayService` facade in composition

- **New file**: `composition/src/main/java/com/tradej/composition/ReplayService.java`.
- **Surface** (single interface, no overloads):
  ```java
  ReplaySessionHandle start(String symbol, String interval, long fromMs, long toMs, double speed);
  void pause(String sessionId);
  void resume(String sessionId);
  void step(String sessionId, int n);
  void stop(String sessionId);
  ```
- **Internals**: delegates to [ReplayOrchestrator](file:///Users/apple/Downloads/Trade_J/replay/engine/src/main/java/com/tradej/replay/engine/ReplayOrchestrator.java) (ticks + fills) and [CandleReplaySession](file:///Users/apple/Downloads/Trade_J/replay/engine/src/main/java/com/tradej/replay/engine/CandleReplaySession.java) (candles). One state machine, one clock.
- **Wire in** [FullComposition](file:///Users/apple/Downloads/Trade_J/composition/src/main/java/com/tradej/composition/FullComposition.java): add `replay()` accessor.

#### B2.2 — WS control plane wiring

- **File**: [GatewayWebSocketHandler.java](file:///Users/apple/Downloads/Trade_J/gateway/src/main/java/com/tradej/gateway/websocket/GatewayWebSocketHandler.java) + [GatewayReplayCommandProcessor](file:///Users/apple/Downloads/Trade_J/gateway/src/main/java/com/tradej/gateway/websocket/GatewayReplayCommandProcessor.java).
- **What**: When a client sends `REPLAY {"op":"start",...}` over WS, dispatch to `ReplayService.start(...)`. Responses go back as `GatewayTopic.REPLAY_CONTROL`.
- **Already present**: the WebSocket control-frame plumbing. Need to:
  1. Replace the no-op default with a `DefaultReplayCommandProcessor` (in `composition` or `replay-engine`) that takes a `ReplayService`.
  2. Wire it in [GatewayAutoConfiguration](file:///Users/apple/Downloads/Trade_J/gateway/src/main/java/com/tradej/gateway/config/GatewayAutoConfiguration.java) as a default `GatewayReplayCommandProcessor` bean.

#### B2.3 — Frontend WS replay client

- **New file**: `trade_j_frontend/src/api/replayControl.ts` exporting `sendReplayCommand(op, params)`.
- **Modify**: [ReplayControls](file:///Users/apple/Downloads/Trade_J/trade_j_frontend/src/components/ReplayControls.tsx) to call `sendReplayCommand('start' | 'pause' | 'resume' | 'step' | 'stop', ...)` instead of local state.
- **Modify**: [App.tsx](file:///Users/apple/Downloads/Trade_J/trade_j_frontend/src/App.tsx) to subscribe to the `REPLAY_CONTROL` topic on [GatewayFeedManager](file:///Users/apple/Downloads/Trade_J/trade_j_frontend/src/api/GatewayFeedManager.ts) and update the UI from real events.

#### B2.4 — Live-vs-replay parity test

- **New file**: `app/src/test/java/com/tradej/app/e2e/ReplayParityEndToEndTest.java` (next to the existing [ReplayEngineEndToEndTest](file:///Users/apple/Downloads/Trade_J/app/src/test/java/com/tradej/app/e2e/ReplayEngineEndToEndTest.java)).
- **What**: For each registered [GraphStrategyPlugin](file:///Users/apple/Downloads/Trade_J/trading/strategy/src/main/java/com/tradej/strategy/api/GraphStrategyPlugin.java):
  1. Record the sequence of `SignalGenerated` events for symbol `SBIN` 09:30–10:00 today.
  2. Replay the same window.
  3. Assert the two sequences are *equal* (deduplicated by `correlationId`).
- **Why**: This test pattern is the *standard* for "did the live change break the replay" and is reusable for every strategy certification.

### Verifications for Phase 2

- `pnpm dev` → click ▶ on [ReplayControls](file:///Users/apple/Downloads/Trade_J/trade_j_frontend/src/components/ReplayControls.tsx) → backend [ReplayController](file:///Users/apple/Downloads/Trade_J/replay/engine/src/main/java/com/tradej/replay/engine/ReplayController.java) state becomes `PLAYING` (visible via `/actuator/metrics`).
- `./gradlew :app:test --tests *ReplayParityEndToEndTest` green.
- New `ReplayService` Javadoc; the class is the *only* thing new joiners read about replay.

### Reviewer (Dr. Venkat) checks

- [ ] Is `ReplayService.start` truly the *only* public entry point? (`grep "replay" composition` should not find anything else.)
- [ ] Does the WS control plane handle disconnects mid-replay? (Yes — `pause` on close.)
- [ ] Is the parity test data-driven? (It loops over `ServiceLoader.load(GraphStrategyPlugin.class)`, not a hard-coded list.)

---

## 5. Phase 3 — Options Analytics Producer + Widgets (Weeks 5–6)

### Goal
The four `OptionChainUpdated`/`GreeksComputed`/`MaxPainComputed`/`GammaExposureComputed` events are produced every `N` seconds. Three widgets render. Two scanners consume.

### Deletions

15. **Delete** the four `entry(GatewayTopic.STRATEGY_SIGNAL, ...)` mappings at [GatewayEventBridge.java#L91-L94](file:///Users/apple/Downloads/Trade_J/gateway/src/main/java/com/tradej/gateway/bridge/GatewayEventBridge.java#L91-L94) — replaced by per-event topics.
16. **Delete** the `STRATEGY_SIGNAL` topic *only if* no remaining event maps to it (audit first). Otherwise keep it for actual strategy signals.
17. **Delete** the duplicated `getVolatilitySurface` body from [OptionsAnalyticsApplicationService](file:///Users/apple/Downloads/Trade_J/app/src/main/java/com/tradej/app/service/OptionsAnalyticsApplicationService.java) once the producer exists — the controller will be a thin pass-through.

### Builds

#### B3.1 — New topics

- **File**: [GatewayTopic.java](file:///Users/apple/Downloads/Trade_J/gateway/src/main/java/com/tradej/gateway/protocol/GatewayTopic.java).
- **What**: Add `MAX_PAIN_UPDATE(17, 1)`, `GREEKS_UPDATE(18, 1)`, `OI_UPDATE(19, 1)`, `GAMMA_EXPOSURE_UPDATE(20, 1)`.
- **Mirror in frontend** [GatewayFeedManager.ts](file:///Users/apple/Downloads/Trade_J/trade_j_frontend/src/api/GatewayFeedManager.ts) (the enum is duplicated; codegen it later, mirror by hand now).

#### B3.2 — `OptionsAnalyticsProducer` service

- **New file**: `trading-options-analytics/src/main/java/com/tradej/options/producer/OptionsAnalyticsProducer.java`.
- **Surface**:
  ```java
  void start();   // @PostConstruct
  void stop();    // @PreDestroy
  ```
- **Behaviour**:
  - Reads the configured underlying list from `trade.options.analytics-enabled` and `trade.options.underlyings` in [application.yml](file:///Users/apple/Downloads/Trade_J/app/src/main/resources/application.yml#L67-L69).
  - Every `trade.options.chain-poll-interval-ms` (already in config) fetches the option chain via [OptionsProvider](file:///Users/apple/Downloads/Trade_J/broker-gateway/src/main/java/com/tradej/brokergateway/query/OptionAnalytics.java), computes MaxPain/PCR/Gamma, publishes the 4 events via the bus.
  - Reuses [MaxPainCalculator](file:///Users/apple/Downloads/Trade_J/app/src/main/java/com/tradej/app/service/OptionsAnalyticsApplicationService.java) and [VolatilitySurfaceBuilder](file:///Users/apple/Downloads/Trade_J/app/src/main/java/com/tradej/app/service/OptionsAnalyticsApplicationService.java) — do not re-implement.
- **Wire** in [OptionsAnalyticsController](file:///Users/apple/Downloads/Trade_J/app/src/main/java/com/tradej/app/api/OptionsAnalyticsController.java) — controller becomes read-only; producer handles publish.

#### B3.3 — Three new widgets

- **New file**: `trade_j_frontend/src/components/MaxPainChart.tsx`. Subscribes to `MAX_PAIN_UPDATE`. Plots strikes × total OI with vertical line at max-pain strike.
- **New file**: `trade_j_frontend/src/components/PcrTimeseries.tsx`. Subscribes to `OI_UPDATE`. PCR = put OI / call OI. Last 60 polls.
- **New file**: `trade_j_frontend/src/components/GammaHeatmap.tsx`. Subscribes to `GAMMA_EXPOSURE_UPDATE`. Strike × expiry heatmap.
- **Register** all three in [widgetRegistry.ts](file:///Users/apple/Downloads/Trade_J/trade_j_frontend/src/domain/widgetRegistry.ts) and add to the `OPTIONS` category.
- **Add to layouts**: [dashboards.ts](file:///Users/apple/Downloads/Trade_J/trade_j_frontend/src/domain/dashboards.ts) — add to `RESEARCH_DASHBOARD_CONFIG` and to a new `OPTIONS_DASHBOARD_CONFIG`.

#### B3.4 — Two new scanners

- **New file**: `trading-scanner/src/main/java/com/tradej/scanner/option/PcrScanner.java`. Subscribes to `OI_UPDATE`; emits `ScanResultsPublished` when PCR crosses 1.2 or 0.8.
- **New file**: `trading-scanner/src/main/java/com/tradej/scanner/option/MaxPainProximityScanner.java`. Subscribes to `MAX_PAIN_UPDATE`; emits when spot is within 0.5 % of max-pain.
- **Register** both as [ScannerProvider](file:///Users/apple/Downloads/Trade_J/trading/scanner/src/main/resources/META-INF/services/com.tradej.scanner.spi.ScannerProvider).

### Verifications for Phase 3

- Boot the app in `dev` profile with `trade.options.analytics-enabled=true`.
- Within 60 s, `gateway-events.ts` shows new events on the new topics.
- The 3 widgets render the data.
- Two scanners emit hits when synthetic chain data crosses thresholds.
- `./gradlew :trading-options-analytics:test` green.

### Reviewer (Dr. Venkat) checks

- [ ] Is the producer's interval *one* config knob, not 4?
- [ ] Do the new widgets share a chart primitive? (Yes — reuse existing `lightweight-charts` instance from [CandlestickChart](file:///Users/apple/Downloads/Trade_J/trade_j_frontend/src/components/CandlestickChart.tsx) if possible.)
- [ ] Do the scanners have a unit test that replays synthetic `OI_UPDATE` events? (Yes — that is the pattern.)

---

## 6. Phase 4 — Risk, PnL, Strategy Observability (Weeks 7–9)

### Goal
A composite "Risk & PnL" widget that updates in real time. A per-strategy replay-parity certification report. A `useRisk()` hook that any widget can consume.

### Deletions

18. **Delete** [RiskCalculator](file:///Users/apple/Downloads/Trade_J/trade_j_frontend/src/components/RiskCalculator.tsx) as a "calculator" — replace it with `RiskMonitor` widget (same name, new behaviour).
19. **Delete** the 4 separate REST endpoints under `/api/v1/portfolio/...` if they overlap with [PortfolioAnalyticsController](file:///Users/apple/Downloads/Trade_J/app/src/main/java/com/tradej/app/api/PortfolioAnalyticsController.java); audit and merge.
20. **Delete** any "second" kill-switch entry point in the UI (the `killswitch` CLI sub-command is the canonical one; the UI just calls it).

### Builds

#### B4.1 — `RiskMonitor` widget + `useRisk()` hook

- **New file**: `trade_j_frontend/src/hooks/useRisk.ts`.
- **What**: Subscribes to `PNL_UPDATE` and `BROKER_STATUS`; exposes `{netExposurePaisa, dailyPnlPaisa, drawdownPaisa, openPositions, killSwitchArmed}`.
- **New file**: `trade_j_frontend/src/components/RiskMonitor.tsx`. Renders 4 numbers + 1 chart (equity curve from the analytics endpoint) + 1 kill-switch button.
- **Replace** [RiskCalculator](file:///Users/apple/Downloads/Trade_J/trade_j_frontend/src/components/RiskCalculator.tsx) in [widgetRegistry.ts](file:///Users/apple/Downloads/Trade_J/trade_j_frontend/src/domain/widgetRegistry.ts).

#### B4.2 — Equity-curve chart

- **New file**: `trade_j_frontend/src/components/EquityCurve.tsx`.
- **What**: Fetches `/api/v1/portfolio/analytics/equity-curve?from=...&to=...` (use the existing [PerformanceAnalytics](file:///Users/apple/Downloads/Trade_J/pipeline/analytics/trade-analytics/src/main/java/com/tradej/analytics/PerformanceAnalytics.java) endpoint, expose it via [PortfolioAnalyticsController](file:///Users/apple/Downloads/Trade_J/app/src/main/java/com/tradej/app/api/PortfolioAnalyticsController.java) if not already).
- **Register** in registry under `analysis`.

#### B4.3 — Per-strategy replay-parity certification

- **New file**: `trading-strategy/src/main/java/com/tradej/strategy/certification/StrategyReplayParityReporter.java`.
- **What**: For a given strategy id and date range, replay the period and produce a JSON report: signals emitted live vs replay, PnL live vs replay, drawdown live vs replay.
- **Expose** as a CLI sub-command: `tradej strategies parity <plugin-id> --from ... --to ...`.
- **Test**: parametric over all `ServiceLoader.load(GraphStrategyPlugin.class)`.

#### B4.4 — Per-strategy observability

- **New file**: `trading-strategy/src/main/java/com/tradej/strategy/observability/StrategyMetrics.java`.
- **What**: A simple counter per (strategy, event type, outcome).
- **Wire** in [GraphStrategySandbox](file:///Users/apple/Downloads/Trade_J/trading/strategy/src/main/java/com/tradej/strategy/service/GraphStrategySandbox.java) — already has a `runPlugin` seam.
- **Expose** via `/actuator/metrics` and via WS topic `STRATEGY_METRICS`.

#### B4.5 — Strategy catalog page

- **New file**: `trade_j_frontend/src/pages/StrategyCatalogPage.tsx`.
- **What**: Lists loaded plugins, last signal time, last error, total signals, parity status.
- **Add to layouts**.

### Verifications for Phase 4

- `pnpm dev` → open `RESEARCH_DASHBOARD_CONFIG` → see EquityCurve + RiskMonitor + StrategyCatalog.
- `tradej strategies parity tick-price-change --from today-7d --to today` exits 0 and prints a JSON.
- `/actuator/metrics/strategy.signals.count` increments on each signal.

### Reviewer (Dr. Venkat) checks

- [ ] Are the metric names stable across releases? (Yes — versioned.)
- [ ] Does the parity reporter fail loudly if replay diverges by more than 1 %? (Yes — exit code 1.)
- [ ] Is `RiskMonitor` only a *view* — does it not own any state? (Yes — driven by the bus + REST.)

---

## 7. Phase 5 — Developer Experience + Production Hardening (Weeks 10–12)

### Goal
A new dev is productive in 2 days. Production deployment is one command. There is a documented `tradej dev` workflow. The codegen is in Gradle.

### Deletions

21. **Delete** the `tradej dev` placeholder if it exists; rebuild.
22. **Delete** the legacy `app` startup log lines that print "Loading class X" — replace with a single one-line "Composition ready in N ms" log.
23. **Delete** `RateLimitFilter` if [GatewayBackpressureHandler](file:///Users/apple/Downloads/Trade_J/gateway/src/main/java/com/tradej/gateway/resilience/GatewayBackpressureHandler.java) covers it (audit first).
24. **Delete** [BrokerInspectionReport](file:///Users/apple/Downloads/Trade_J/broker-gateway/src/main/java/com/tradej/brokergateway/explorer/BrokerInspectionReport.java) + 4 satellite classes; collapse to one `BrokerInspection` record.

### Builds

#### B5.1 — `DEVELOPER.md`

- **New file**: [DEVELOPER.md](file:///Users/apple/Downloads/Trade_J/) at the project root.
- **Contents**:
  1. 5-minute quick start (`./gradlew :app:bootRun` + `pnpm dev`).
  2. "Where do I add a new strategy?" — 1 section, 3 steps, 1 file (`META-INF/services`).
  3. "Where do I add a new scanner?" — same shape.
  4. "Where do I add a new dashboard?" — registry + layouts.
  5. "Where do I add a new event topic?" — annotate + codegen.
  6. "How do I test end-to-end?" — replay parity.
  7. "How do I run live?" — profile table.
- **Acceptance**: A new dev can complete each of the 7 sections without opening any other doc.

#### B5.2 — `tradej dev` command

- **New file**: `cli/src/main/java/com/tradej/cli/command/CliDevCommand.java`.
- **Behaviour**:
  - Boots [FullComposition](file:///Users/apple/Downloads/Trade_J/composition/src/main/java/com/tradej/composition/FullComposition.java) in `simulation` mode.
  - Loads the [SampleStrategy](file:///Users/apple/Downloads/Trade_J/trading/strategy/src/main/java/com/tradej/strategy/example/TickPriceChangeStrategy.java) + [SampleScanner](file:///Users/apple/Downloads/Trade_J/trading/scanner/src/main/java/com/tradej/scanner/option/OptionLiquidityScanner.java).
  - Starts a [ReplayController](file:///Users/apple/Downloads/Trade_J/replay/engine/src/main/java/com/tradej/replay/engine/ReplayController.java) for `SBIN` last 7 days.
  - Prints the URL of the local web UI.
  - Exits when the user presses Ctrl-C.
- **Acceptance**: One command, one URL, full workflow running locally.

#### B5.3 — Codegen in Gradle

- **New Gradle task**: `:frontend:generateApi` that runs [generate-api-client.sh](file:///Users/apple/Downloads/Trade_J/scripts/generate-api-client.sh) + [generate-gateway-types.sh](file:///Users/apple/Downloads/Trade_J/scripts/generate-gateway-types.sh).
- **Wire into `:app:check`** so a stale generated client fails CI.
- **Acceptance**: `./gradlew check` regenerates clients and fails if they were stale.

#### B5.4 — Production readiness checklist

- **New file**: `docs/PRODUCTION_READINESS.md`.
- **Sections**: tokens, secrets, kill switch test, OMS recovery test, gateway load test, broker failover, replay parity, health endpoint behaviour, observability (metrics, traces, logs), graceful shutdown.
- **Test harness**: a `./gradlew :app:productionSmokeTest` task that runs the checklist end-to-end against a fresh `prod` profile boot.

#### B5.5 — One-click kill switch in UI

- **Modify**: [RiskMonitor](#) (added in Phase 4) — a single button calls `POST /api/v1/risk/kill-switch` which delegates to [KillSwitchCoordinator](file:///Users/apple/Downloads/Trade_J/composition/src/main/java/com/tradej/composition/ExecutionComposition.java).
- **New endpoint**: [RiskController.java](file:///Users/apple/Downloads/Trade_J/app/src/main/java/com/tradej/app/api/) (new file, thin).
- **Acceptance**: Press the button → [KillSwitchCoordinator](file:///Users/apple/Downloads/Trade_J/composition/src/main/java/com/tradej/composition/ExecutionComposition.java) fires; OMS stops accepting new orders; existing orders cancel per policy.

### Verifications for Phase 5

- New dev on-ramp: 2 days from `git clone` to first PR.
- `tradej dev` boots and serves a URL within 10 s.
- `./gradlew check` is green; codegen is deterministic.
- `./gradlew :app:productionSmokeTest` is green.

### Reviewer (Dr. Venkat) checks

- [ ] Is `DEVELOPER.md` written for a *new* dev, not for the team that already knows? (Yes — assume zero prior context.)
- [ ] Does `tradej dev` boot in *one* command, not "first do X, then Y"?
- [ ] Is the production checklist a *script* (assertions, not prose)?
- [ ] Is the kill switch button labelled unambiguously? (Yes — "STOP ALL TRADING", not "Halt".)

---

## 8. Cross-Phase Workstreams

These run *in parallel* across all 5 phases and must not be deferred:

- **A. Documentation debt**: every deleted class is mentioned in `CHANGELOG.md`. Every new public class has a 1-paragraph Javadoc. Every new public widget has a 1-paragraph header comment.
- **B. Test discipline**: every phase's "Acceptance" becomes a test in the next phase. *Tests are not optional.*
- **C. Observability**: every new component exports the same triple (`metrics()`, `health()`, `lastEventAt()`). Reviewers reject PRs that don't.
- **D. Performance budget**: hot-path PRs must include a JMH microbench or a number from the existing [`runtime/hotpath` tests](file:///Users/apple/Downloads/Trade_J/runtime/hotpath/src/test/java/com/tradej/hotpath/DisruptorHighThroughputStressTest.java).

## 9. Risks & Mitigations

| Risk | Mitigation |
| --- | --- |
| Phase 1 deletions break hidden consumers | Mandatory `grep` per delete; run `./gradlew check` after each delete |
| Codegen for the bridge is too clever | Keep it dumb: read annotations, emit a `Map.entry` block, never runtime reflection |
| Frontend widget registry renders an empty widget | Every widget must have a "happy path" data assertion in [marketContracts](file:///Users/apple/Downloads/Trade_J/trade_j_frontend/src/api/marketContracts.ts) before render |
| Replay parity is non-deterministic (e.g. parallel tests) | Use a single-threaded bus for parity tests, document in `DEVELOPER.md` |
| Options producer overwhelms the bus | Default interval is 60 s; rate-limit configurable; the existing `trade.options.chain-poll-interval-ms` config knob is reused |
| `tradej dev` command hides real wiring | `tradej dev` uses [FullComposition](file:///Users/apple/Downloads/Trade_J/composition/src/main/java/com/tradej/composition/FullComposition.java) — the same root as production; no shadow path |
| Per-strategy observability introduces a new SPI | Don't. Reuse the existing `DomainEventHandler` mechanism; subscribe a `StrategyMetricsHandler` in the composition root |

## 10. What is *not* in this plan

- A 4th broker.
- A new MCP tool beyond the 12.
- A new dashboard framework.
- A new pipeline runtime.
- A new event topic beyond the 4 added in Phase 3.
- A new strategy SPI surface.

If anyone proposes these mid-plan, the answer is "no, finish what we have".

## 11. Weekly Cadence

- **Monday**: phase stand-up, list the deletions landed + the builds done.
- **Wednesday**: mid-week reviewer check (Dr. Venkat reviews the PRs that landed).
- **Friday**: end-of-week demo of one user-visible thing (one widget, one command, one event topic).
- **End of phase**: reviewer sign-off against the phase's "Acceptance" list. No phase closes without it.

## 12. Exit Criteria for the Whole Plan

Trade-J is a *professional quant platform* when:

- [ ] A new dev is productive in 2 days (verified by an external hire or contractor).
- [ ] All 16 widgets render by default.
- [ ] Replay control plane is driven end-to-end from the UI.
- [ ] Options analytics events are produced and consumed.
- [ ] Risk & PnL composite widget updates in real time.
- [ ] Per-strategy replay-parity is a one-command check.
- [ ] `tradej dev` boots a full workflow locally in one command.
- [ ] `./gradlew check` is green and includes the parity tests.
- [ ] Production readiness checklist is a script, not prose.
- [ ] No deleted file from §3 is referenced anywhere.

## 13. Final Word

This plan is **measured in lines deleted and workflows completed, not lines added**. The 24 deletions in §3 are non-negotiable; the 17 builds are sequenced so each one is *the smallest possible thing that delivers value*. If a build grows beyond its listed size, cut it and re-scope — the plan protects against scope creep by making every phase *close on a user-visible artifact*, not a code review.

Reviewers, remember: every commit must answer "did this delete at least as much as it added?" If the answer is no, the commit waits.
