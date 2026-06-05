# End-to-End Verification (incremental)

Last run: `./gradlew test componentTest :architecture-test:architectureTest` — **BUILD SUCCESSFUL**.

CI (`.github/workflows/ci.yml`) runs `test`, `componentTest`, and `:architecture-test:architectureTest` on every push/PR.

## Tier 1 — Unit (default `test`)

| Area | Representative tests |
|------|----------------------|
| OMS / VWAP | `OrderStateMachineUnitTest`, `OrderStateMachinePropertyTest` |
| Execution timeout | `ExecutionHandlerUnitTest.orderPlacementTimeoutEmitsSignalSuppressed` |
| Net position | `EventSourcedNetPositionProviderTest` |
| Replay clock | `ClockDivergenceTest` |
| Gateway routing | `LoadBalancedBrokerGatewayTest` |

## Tier 2 — Component (`./gradlew componentTest`)

| Flow | Test |
|------|------|
| Signal → simulated order | `DisruptorSignalToExecutionComponentTest` |
| Partial fill → OMS | `OrderPartiallyFilledHotPathComponentTest` |
| Risk forward | `PositionRiskHandlerComponentTest` |
| **Full hot path + DuckDB** | `TradingHotPathE2EComponentTest` |
| Replay ticks | `ReplayMarketTickParityTest`, `MarketDataReplayParityComponentTest` |
| Subscription recovery | `SubscriptionCoordinatorTest`, `SubscriptionRecoveryReconnectComponentTest` |
| NR-02 startup order | `RuntimeModeStartupOrderComponentTest` |
| Gateway resolver | `BrokerRuntimeModeResolverComponentTest`, `GatewayProfileContextComponentTest` |
| Fill replay | `FillReplayIntegrationTest` |

## Tier 3 — Integration (credentials / live)

Tagged `@Tag("integration")` — excluded from default CI `test`:

- `GatewayLiveBenchmark` (multi-broker gateway profile)
- Broker live suites under `app/.../integration/`

Run locally: `./gradlew :app:integrationTest` (requires config under `config/`).

## Production paths verified (no mocks)

1. `SignalGenerated` → `PositionRiskHandler` → `SignalPendingExecution` → `ExecutionHandler`
2. REPLAY mode → `OrderAccepted` (simulated broker)
3. `OrderPipeline` → `OrderPartiallyFilled` → OMS `PARTIALLY_FILLED`
4. `DuckDbEventStore` persists with `event_time_ms`
5. `HistoricalRangeService.queryFillEvents` filters by virtual time window
6. Reconnect → `ReconnectListenerRegistry.notifyReconnect()` → `SubscriptionRecoveryManager.recoverAfterReconnect()` → `SubscriptionCoordinator.reconcileAfterReconnect()`

## Gateway profile

- `trade.broker-type=gateway` activates Dhan + ICICI + Upstox configs and `@Primary` bean `loadBalancedBrokerGateway`.
- `LoadBalancedBrokerGateway` round-robins LTP and failovers orders/REST.
- `ReconnectListenerRegistry` wired through `LoadBalancedBrokerGateway` → `FailoverWebSocketMultiplexer`.
- `SubscriptionCoordinator` resolves multiplexer via `IBrokerConnection.websocket()` — uses `FailoverWebSocketMultiplexer` in gateway mode.
- Spring wiring (no full app boot): `GatewayProfileContextComponentTest`

## Not in build

- `trading/options-analytics` — see `trading/options-analytics/README.md`
