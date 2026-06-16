# Trade-J Production Hardening — Phased Execution Plan

**Generated**: June 15, 2026 | **Source**: PRODUCTION_READINESS_CERTIFICATION.md
**State**: System running LIVE (Dhan gateway, 8 instruments, 8K+ ticks)

---

## Summary: 16 Work Items Across 4 Phases

| Phase | Items | Effort | Risk Reduction |
|-------|-------|--------|---------------|
| **P0: Critical Hardening** 🚨 | 5 | ~4h | Blocks deployment |
| **P1: Code Cleanup** | 4 | ~6h | Reduces future bugs |
| **P2: Observability** | 4 | ~4h | Enables SRE |
| **P3: Testing & Cover** | 3 | ~6h | Proves correctness |

**Total**: ~20h effort, 7-8 working days with review cycles.

---

## PHASE 0 — CRITICAL HARDENING (Deploy BlockerS) 🚨

Goal: Eliminate the 5 architectural defects that caused production failures today.
Success: Backend starts clean in gateway mode with no manual intervention.
Duration: ~4 hours.

### 0.1 Split BrokerConfiguration into focused config classes

- **What**: Break 550-line `BrokerConfiguration.java` into:
  - `IciciBrokerConfiguration.java` (ICICI adapter beans, conditionally active)
  - `SimulationBrokerConfiguration.java` (simulation adapter beans)
  - `GatewayBrokerConfiguration.java` (gateway, load balancer, WS config)
  - `BrokerAgnosticConfiguration.java` (idempotency cache, identity registry, capabilities)
- **Why**: Eliminates the god-class anti-pattern. Each file has one reason to change.
- **Test**: All existing broker-dhan tests (27), app tests (26 chaos-tagged) + run the live gateway profile
- **Coverage target**: All 13 `@Bean` methods from the extracted classes must have at least 1 ArchUnit test proving they're picked up

### 0.2 Extract InstrumentCatalogLoader SPI

- **What**: Add `InstrumentCatalogLoader` interface to `broker-api`:
  ```java
  public interface InstrumentCatalogLoader {
      Path loadDailyCatalog(Path cacheDirectory, boolean forceRefresh);
      boolean supports(IBrokerConnection connection);
  }
  ```
  Implement `DhanInstrumentCatalogLoader` in `broker-dhan`.
- **Why**: Removes `instanceof DhanBrokerConnection` from `GatewayStartupStrategy`. Gateway should not know about concrete brokers.
- **Test**: Unit test `DhanInstrumentCatalogLoader`, integration test `GatewayStartupStrategy` delegates correctly
- **Coverage target**: 100% branch coverage on `GatewayStartupStrategy.loadCatalog()`

### 0.3 Consolidate Dhan Configuration Types

- **What**: Merge `TradingProperties.DhanProperties` + `BrokerProfile.DhanConfig` + `DhanConnectionSettings` into a single `DhanConfig` record in `broker-dhan`.
- **Why**: Three representations of the same concept. Change the client secret in one place, not three.
- **Test**: All 27 broker-dhan tests + DhanBrokerConfiguration compiles
- **Coverage target**: Verify `DhanConfig` is the single import in all Dhan-related classes

### 0.4 Eliminate Adapter Bean Method Duplication

- **What**: Extract common adapter wiring into `BrokerAdapterWiring` base:
  ```java
  public class BrokerAdapterWiring {
      public static MarketDataProvider marketData(IBrokerConnection conn, String label, MeterRegistry r) {
          return new ObservableMarketDataProvider(label, conn.marketData(), r);
      }
      public static OrderCommand orderCommand(IBrokerConnection conn, String label, MeterRegistry r) { ... }
      // ... 12 more
  }
  ```
  Use in `DhanBrokerConfiguration`, `IciciBrokerConfiguration`, `SimulationBrokerConfiguration`.
- **Why**: 14 identical `@Bean` methods duplicated across 3 config classes. Change once, apply everywhere.
- **Test**: All existing tests + verify all 3 profiles (dhan, icici, simulation) compile and wire correctly
- **Coverage target**: ArchUnit test proving `BrokerAdapterWiring` is the only class calling `new ObservableMarketDataProvider`

### 0.5 Add Silent Failover Logging

- **What**: In `LoadBalancedBrokerGateway.FailoverOrderCommand.withFailover()`, add `log.warn("Broker node {} failed for {}, trying next", connection.source(), action)` before rotating.
- **Why**: Failovers are currently completely silent — operators have no idea one broker leg is degraded.
- **Test**: Unit test with mocked `IBrokerConnection` that throws, verify log output
- **Coverage target**: `BrokerResilienceMetrics` records a failover event for each rotation

### Phase 0 Validation Gate

- [ ] `./gradlew :app:bootRun --args='--spring.profiles.active=gateway'` starts clean
- [ ] `./gradlew broker-dhan:test` — 27 tests, 0 failures
- [ ] `./gradlew :app:test` — all chaos-tagged tests pass
- [ ] Health endpoint shows `websocketConnected: true`, 8 subscriptions
- [ ] MCX commodity LTPs flowing (GOLD, CRUDEOIL, SILVER, NATURALGAS)

---

## PHASE 1 — CODE CLEANUP (Bug Prevention)

Goal: Eliminate domain duplication and DTO proliferation.
Success: Single representation for every domain concept.
Duration: ~6 hours.

### 1.1 Merge Order DTOs → Core Model

- **What**: Delete `api.dto.PlaceOrderRequest`, `api.dto.OrderResponse`, `api.dto.OrderProjectionResponse`. Have `OrderController` accept/return `core.OrderRequest`/`core.Order` directly. Use Jackson annotations or mixins for serialization control.
- **Why**: Three representations of the same business concept cause mapping drift and bugs.
- **Test**: `OrderController` integration test — place, read, cancel an order via REST, verify round-trip
- **Coverage target**: All 3 deleted DTOs pass `grep -r "PlaceOrderRequest\|OrderResponse\|OrderProjectionResponse" --include="*.java"` returns zero results

### 1.2 Deduplicate Configuration Sources

- **What**: Move all defaults from `TradingProperties.java` hardcoded values into `application.yml`. Document override precedence: `env var > config/*.properties > application-{profile}.yml > application.yml`.
- **Why**: Operators shouldn't need to search Java source code to find default values.
- **Test**: `TradingProperties` test — verify each property resolves through the documented precedence chain
- **Coverage target**: `grep -r "= .*// default" TradingProperties.java` returns zero hardcoded defaults

### 1.3 Remove archive/ Directory

- **What**: Delete `archive/` — old frontend code replaced by `trade_j_frontend`.
- **Why**: Dead code is a maintenance tax. Every file read during searches, every import considered during refactoring.
- **Test**: `grep -r "archive/" --include="*.gradle" --include="*.yml"` returns zero references
- **Coverage target**: `ls archive/` returns "No such file"

### 1.4 Clean Frontend Pre-existing TypeScript Errors

- **What**: Fix `LiveTerminal.tsx` ExchangeSegment type narrowing (use enum values not string literals), fix `DashboardRenderer.tsx` missing export and WidgetProps.key
- **Why**: TypeScript errors mask real bugs. Clean compilation = clean baseline.
- **Test**: `npx tsc --noEmit` returns zero errors
- **Note**: `orchestrator-integration.test.ts` was fixed today. LiveTerminal and DashboardRenderer remain.

### Phase 1 Validation Gate

- [ ] `find . -name "PlaceOrderRequest.java" -o -name "OrderResponse.java" | wc -l` = 0
- [ ] `grep -r "hardcoded\|default\|magic" TradingProperties.java` = 0 matches
- [ ] `ls archive/` = empty
- [ ] `npx tsc --noEmit` in trade_j_frontend = clean

---

## PHASE 2 — OBSERVABILITY (SRE Enablement)

Goal: Operators can detect, diagnose, and respond to production issues.
Success: Every integration boundary has a metric, every failure mode has an alert.
Duration: ~4 hours.

### 2.1 Per-Broker Order Latency Metrics

- **What**: Add Micrometer `Timer` around `DhanOrderCommandAdapter.placeOrder()`, `modifyOrder()`, `cancelOrder()`. Tag by broker source and operation.
- **Why**: Cannot detect broker degradation without latency baselines.
- **Test**: Unit test verifies Timer is recorded; integration test confirms metric appears in `/actuator/prometheus`
- **Coverage target**: `broker_order_latency_seconds{broker="dhan",operation="place"}` appears in metrics

### 2.2 Circuit Breaker State Metrics

- **What**: Expose current circuit breaker state per operation via Micrometer `Gauge`. Use existing `BrokerResilienceMetrics.circuitBreakerTrips` and add state gauge.
- **Why**: Operators need to know when a circuit opens — before it causes user-visible failures.
- **Test**: Unit test — force circuit open, verify gauge reads 1; force close, verify 0
- **Coverage target**: `broker_circuit_breaker_open{broker="dhan",operation="place-order"}` metric

### 2.3 Production Alert Definitions

- **What**: Define alert rules (Prometheus/AlertManager format):
  ```yaml
  - alert: BrokerCircuitOpen
    expr: broker_circuit_breaker_open > 0
    for: 60s
    severity: P1
    annotations:
      summary: "Circuit breaker open for {{ $labels.broker }}/{{ $labels.operation }}"
  ```
  Repeat for: WS disconnected > 120s, order rejection > 5%, heap > 80%, Chronicle queue > 10K, zero ticks > 30s.
- **Why**: Without alert definitions, metrics are collected but never acted upon.
- **Test**: Alert rule syntax validation (promtool)
- **Coverage target**: 6 alert rules, all validate with `promtool check rules`

### 2.4 SSE Client Connection Count

- **What**: Track active SSE connections in `ReadModelController.stream()` with an `AtomicInteger`. Expose via health endpoint or metric.
- **Why**: Operators should know if the trading terminal is connected.
- **Test**: Open SSE connection, verify count increments; close, verify decrements
- **Coverage target**: `sse_connections_active` gauge in `/actuator/prometheus`

### Phase 2 Validation Gate

- [ ] `/actuator/prometheus` returns `broker_order_latency_seconds`, `broker_circuit_breaker_open`, `sse_connections_active`
- [ ] `promtool check rules alerts.yml` passes
- [ ] Health endpoint shows circuit breaker states

---

## PHASE 3 — TESTING & COVERAGE (Correctness Proof)

Goal: Prove the system can survive production conditions.
Success: All critical paths have deterministic, repeatable tests.
Duration: ~6 hours.

### 3.1 Dhan Load Test

- **What**: JUnit test that exercises all 5 rate-limit buckets concurrently:
  - 10 orders/sec for 5 seconds (ORDER bucket)
  - 10 historical requests (DATA bucket)
  - 5 quote requests (QUOTE bucket)
  - 3 option chain requests (OPTION_CHAIN bucket)
  - 20 funds/positions requests (NON_TRADING bucket)
- **Why**: Verify rate limiter prevents 429 responses; verify no circuit breaker trips under normal load.
- **Coverage target**: 0 `DhanHttpException` with status 429, 0 circuit breaker opens

### 3.2 WebSocket Reconnect Chaos Test

- **What**: Run existing `RapidReconnectScenario` against live Dhan:
  - Connect → subscribe → disconnect → reconnect → verify subscriptions restored
  - Disconnect during active tick flow → verify no tick loss > 2s after reconnect
  - Kill token → verify 806 code → token rotation → auto-reconnect
- **Why**: Reconnection is the most complex state machine; it must work under chaos.
- **Coverage target**: 10 reconnect cycles, 0 subscription leaks, 0 zombie connections

### 3.3 End-to-End Paper Trade Flow

- **What**: REST-driven end-to-end test:
  1. Place BUY order via `POST /api/v1/orders`
  2. Poll `/api/v1/orders?status=active` until filled or cancelled
  3. Verify SSE stream emits order update
  4. Cancel order via `DELETE /api/v1/orders/{id}`
  5. Verify SSE emits cancellation
- **Why**: The full OMS flow (REST → broker → event bus → SSE → frontend) has no automated test.
- **Coverage target**: 1 successful round-trip for each order type (LIMIT, MARKET)

### Phase 3 Validation Gate

- [ ] Load test: 50 concurrent requests, 0 rate-limit errors
- [ ] Chaos test: 10 reconnect cycles, 0 failures
- [ ] E2E paper trade: BUY → FILL → CANCEL all visible in SSE stream
- [ ] `./gradlew test` — all tests pass, including new additions

---

## EXECUTION ORDER

```
P0.1 → P0.2 → P0.3 → P0.4 → P0.5   (sequential — each depends on previous)
         ↓
P1.1 → P1.2                           (parallel after P0 done)
P1.3 → P1.4                           (parallel after P0 done)
         ↓
P2.1 → P2.2 → P2.3                   (parallel, independent of P2.4)
P2.4                                  (parallel)
         ↓
P3.1 → P3.2 → P3.3                   (parallel after P2 done)
```

## FINAL CERTIFICATION GATE (Before Every Deployment)

- [ ] `./gradlew test` — all tests pass
- [ ] `./gradlew :app:bootRun --args='--spring.profiles.active=gateway'` starts clean
- [ ] Health: `websocketConnected: true`, `marketData: UP`, subscriptions > 0
- [ ] MCX + NSE LTPs flowing
- [ ] Frontend `npx tsc --noEmit` = clean
- [ ] `promtool check rules alerts.yml` = valid
- [ ] Load test: 50 requests, 0 429s
- [ ] Chaos test: 10 reconnect cycles, 0 failures
