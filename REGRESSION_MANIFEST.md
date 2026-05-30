# Regression Manifest

Maps each verification test to Gradle task, environment, opt-in flags, and architecture invariants.

## Gradle tasks

| Task | Scope | Purpose |
|------|-------|---------|
| `unitTest` | All subprojects | Fast deterministic logic |
| `componentTest` | All subprojects | In-process multi-module wiring |
| `regressionPreflightTest` | trade-app | Validates live + sandbox credentials before long runs |
| `brokerRestTest` | trade-app | Live read-only REST (data, options, quotes) |
| `brokerWsTest` | trade-app | Live market feed WebSocket |
| `brokerOrderTest` | trade-app | Sandbox order mutations |
| `runtimeE2eTest` | trade-app | Spring runtime smoke + reconciliation |
| `crossLayerRegressionTest` | trade-app | OMS + execution + sandbox broker |
| `fullRegressionTest` | Root | Runs all layers above |

## Required vs optional (full regression)

| Required | Optional (skip OK with message) |
|----------|----------------------------------|
| All `unitTest` / `componentTest` | `DhanMarketFeedIntegrationTest` (needs `DHAN_TEST_*` symbol env) |
| `RegressionPreflightIntegrationTest` | `DhanSessionRiskIntegrationTest` if sandbox lacks `/pnlExit` |
| `brokerRestTest` tests without `*_TEST_ENABLED` | `DhanSuperOrderIntegrationTest` / `DhanForeverOrderIntegrationTest` if sandbox 404 |
| `brokerWsTest` when live WS creds valid | `DhanRuntimeSmokeIntegrationTest` candle assert outside market hours |
| `brokerOrderTest` with all `*_TEST_ENABLED=true` | |
| `runtimeE2eTest` | |
| `crossLayerRegressionTest` with `DHAN_CROSS_LAYER_TEST_ENABLED=true` | |

## Test inventory

| Test class | Task | Env | Opt-in flag | Invariant |
|------------|------|-----|-------------|-----------|
| `PriceMathUnitTest` | unitTest | none | — | INV-01 price math |
| `OrderStateMachineUnitTest` | unitTest | none | — | INV-02 OMS transitions |
| `ExecutionHandlerUnitTest` | unitTest | none | — | INV-03 execution flow |
| `DhanApiUrlResolverUnitTest` | unitTest | none | — | INV-04 dual base URL |
| `DhanConnectionSettingsUnitTest` | unitTest | none | — | INV-04 env defaults |
| `DhanRestOrderClientUnitTest` | unitTest | none | — | INV-05 sandbox payload |
| `DhanRestOrderClientFixtureTest` | unitTest | none | — | INV-05 JSON mapping |
| `CandleAggregationServiceComponentTest` | componentTest | none | — | INV-06 candles |
| `DisruptorTickToCandleComponentTest` | componentTest | none | — | INV-07 disruptor path |
| `ReconciliationSchedulerComponentTest` | componentTest | none | — | INV-08 reconcile |
| `PositionRiskHandlerComponentTest` | componentTest | none | — | INV-09 risk qualify |
| `RegressionPreflightIntegrationTest` | regressionPreflightTest | live+sandbox | — | INV-10 creds |
| `DhanHistoricalDataIntegrationTest` | brokerRestTest | live | — | INV-11 historical |
| `DhanDerivativesIntegrationTest` | brokerRestTest | live | — | INV-12 options |
| `DhanBatchQuoteIntegrationTest` | brokerRestTest | live | — | INV-13 quotes |
| `LivePnlIntegrationTest` | brokerRestTest | live | — | INV-14 PnL |
| `HistoricalRangeIntegrationTest` | brokerRestTest | live | — | INV-15 range |
| `DhanRollingOptionIntegrationTest` | brokerRestTest | live | `DHAN_ROLLING_OPTION_TEST_ENABLED` | INV-16 rolling opt |
| `DhanMarginIntegrationTest` | brokerRestTest | live | `DHAN_MARGIN_TEST_ENABLED` | INV-17 margin |
| `DhanTokenLifecycleIntegrationTest` | brokerRestTest | live | TOTP config | INV-18 token |
| `DhanMarketFeedIntegrationTest` | brokerWsTest | live | symbol env | INV-19 WS feed |
| `DhanOrderLifecycleIntegrationTest` | brokerOrderTest | sandbox | `DHAN_ORDER_TEST_ENABLED` | INV-20 place/cancel |
| `DhanSliceOrderIntegrationTest` | brokerOrderTest | sandbox | `DHAN_SLICE_ORDER_TEST_ENABLED` | INV-21 slice |
| `DhanSuperOrderIntegrationTest` | brokerOrderTest | sandbox | `DHAN_SUPER_ORDER_TEST_ENABLED` | INV-22 super |
| `DhanForeverOrderIntegrationTest` | brokerOrderTest | sandbox | `DHAN_FOREVER_ORDER_TEST_ENABLED` | INV-23 forever |
| `DhanSquareOffIntegrationTest` | brokerOrderTest | sandbox | `DHAN_SQUAREOFF_TEST_ENABLED` | INV-24 squareoff |
| `DhanAlertIntegrationTest` | brokerOrderTest | sandbox | `DHAN_ALERT_TEST_ENABLED` | INV-25 alerts |
| `DhanSessionRiskIntegrationTest` | brokerOrderTest | sandbox | `DHAN_PNL_EXIT_TEST_ENABLED` | INV-26 pnl exit |
| `ExecutionToSandboxBrokerIntegrationTest` | crossLayerRegressionTest | sandbox | `DHAN_CROSS_LAYER_TEST_ENABLED` | INV-27 exec→broker |
| `OmsToExecutionSandboxIntegrationTest` | crossLayerRegressionTest | sandbox | `DHAN_CROSS_LAYER_TEST_ENABLED` | INV-28 OMS+exec |
| `DhanRuntimeSmokeIntegrationTest` | runtimeE2eTest | live | — | INV-29 runtime boot |
| `TradingRuntimeReconciliationIntegrationTest` | runtimeE2eTest | live | — | INV-30 reconcile bean |
| `PipelineDefinitionUnitTest` | unitTest | none | — | INV-31 immutability |
| `PipelineVersionUnitTest` | unitTest | none | — | INV-32 version correctness |
| `PipelineSnapshotUnitTest` | unitTest | none | — | INV-31 immutability |
| `PipelineGraphComponentTest` | componentTest | none | — | INV-31 immutability |
| `ModuleDependencyTest` | unitTest | none | — | INV-31 immutability |

## Architecture phase mapping

| Phase (roadmap) | Invariants |
|-----------------|------------|
| A — Foundation | INV-04, INV-10 |
| B — OMS | INV-02, INV-03, INV-27, INV-28 |
| C — Risk | INV-09 |
| D — Replay / pipeline | INV-06, INV-07, INV-29, INV-31, INV-32 |
| Broker parity | INV-11–INV-26 |
