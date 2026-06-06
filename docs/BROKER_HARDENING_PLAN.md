# Trade-J Broker Implementation & Hardening Plan

**Role**: Principal Broker Architect / Staff QA Engineer / Production Readiness Auditor
**Date**: 2026-06-06
**Scope**: Broker Gateway, Market Gateway, CLI Workbench, Replay, Simulation, Paper Trading, Production Trading

---

## PHASE 1: BROKER COVERAGE ANALYSIS

### Comprehensive Capability Matrix

| Feature | Dhan | Upstox | ICICI | Coverage % | Prod Ready? | Missing | Risk |
|---------|:---:|:---:|:---:|:---:|:---:|---------|:---:|
| **Quotes (LTP/Quote/OHLC)** | YES | YES | YES | 100% | YES | — | Low |
| **Market Depth (5-level)** | YES | YES | YES | 100% | YES | — | Low |
| **Market Depth (20-level)** | YES | NO | NO | 33% | PARTIAL | Upstox/ICICI depth | Medium |
| **Batch LTP/Quote** | YES | YES | YES | 100% | YES | — | Low |
| **Historical Candles** | YES | YES | YES | 100% | YES | — | Low |
| **Instrument Catalog** | YES | YES | YES | 100% | YES | — | Low |
| **Instrument Resolution** | YES | YES | YES | 100% | YES | — | Low |
| **Option Chain** | YES | YES | YES | 100% | YES | — | Low |
| **Option Expiries** | YES | YES | YES | 100% | YES | — | Low |
| **Option Greeks** | YES | YES | NO | 67% | PARTIAL | ICICI greeks | Medium |
| **Strike Selection** | YES | YES | YES | 100% | YES | — | Low |
| **Rolling Options** | YES | NO | NO | 33% | DHAN ONLY | Upstox/ICICI rolling | Low |
| **Futures Contracts** | YES | YES | YES | 100% | YES | — | Low |
| **Place Order** | YES | YES | YES | 100% | YES | — | Low |
| **Modify Order** | YES | YES | YES | 100% | YES | — | Low |
| **Cancel Order** | YES | YES | YES | 100% | YES | — | Low |
| **Cancel All Orders** | YES | YES | YES | 100% | YES | — | Low |
| **Kill Switch** | YES | YES | NO | 67% | PARTIAL | ICICI kill switch | Medium |
| **Square Off Intraday** | YES | NO | NO | 33% | DHAN ONLY | Upstox/ICICI square-off | Medium |
| **Order Preview** | YES | STUB | YES | 67% | PARTIAL | Upstox real preview | Low |
| **Bracket Orders** | YES | NO | NO | 33% | DHAN ONLY | Upstox/ICICI brackets | High |
| **Cover Orders** | NO | NO | NO | 0% | NO | All brokers | High |
| **GTT Orders** | YES | YES | NO | 67% | PARTIAL | ICICI GTT | Medium |
| **Slice Orders** | YES | YES | NO | 67% | PARTIAL | ICICI slice | Medium |
| **Order Book** | YES | YES | YES | 100% | YES | — | Low |
| **Trade Book** | YES | YES | YES | 100% | YES | — | Low |
| **Order Status** | YES | YES | YES | 100% | YES | — | Low |
| **Balance/Funds** | YES | YES | YES | 100% | YES | — | Low |
| **Positions** | YES | YES | YES | 100% | YES | — | Low |
| **Holdings** | YES | YES | YES | 100% | YES | — | Low |
| **Margin Estimate** | YES | YES | YES | 100% | YES | — | Low |
| **WebSocket Market Feed** | YES | YES | YES | 100% | YES | — | Low |
| **WebSocket Order Updates** | YES | YES | YES | 100% | YES | — | Low |
| **WebSocket 20-Depth** | YES | NO | NO | 33% | DHAN ONLY | Upstox/ICICI WS depth | Low |
| **News Provider** | NO | YES | NO | 33% | UPSTOX ONLY | Dhan/ICICI news | Low |
| **Session Risk** | YES | NO | NO | 33% | DHAN ONLY | Upstox/ICICI risk | Medium |
| **Conditional Alerts** | YES | YES | NO | 67% | PARTIAL | ICICI alerts | Low |
| **Token Auto-Refresh** | YES | YES | NO | 67% | PARTIAL | ICICI manual refresh | High |
| **Market Status** | NO | NO | NO | 0% | NO | All brokers | Medium |

### Broker Coverage Summary

| Broker | Ports Implemented | Ports Missing | Coverage |
|--------|:---:|:---:|:---:|
| **Dhan** | 14/15 | NewsProvider | 93% |
| **Upstox** | 12/15 | BracketOrderProvider, SessionRiskProvider | 80% |
| **ICICI** | 9/15 | BracketOrder, GTT, Slice, SessionRisk, Alerts, News | 60% |

---

## PHASE 2: TEST COVERAGE ANALYSIS

### Test Classification Matrix

| Category | Count | Dhan | Upstox | ICICI | Broker-Core | Gateway | App |
|----------|:---:|:---:|:---:|:---:|:---:|:---:|:---:|
| **Unit Tests** | 69 | 28 | 21 | 15 | 5 | — | — |
| **Contract Tests** | 4 | 3 | — | 1 | — | — | — |
| **Integration Tests** | 62 | 27 | 11 | 11 | — | 2 | 11 |
| **Smoke Tests** | 2 | 2 | — | — | — | — | — |
| **Component Tests** | 3 | 1 | — | — | — | 2 | — |
| **Performance Tests** | 1 | — | — | — | 1 (JMH) | — | — |
| **Concurrency Tests** | 1 | — | — | — | — | 1 | — |
| **Chaos Tests** | 0 | — | — | — | — | — | — |
| **Recovery Tests** | 0 | — | — | — | — | — | — |
| **Replay Tests** | 1 | — | — | — | — | — | 1 |
| **Simulation Tests** | 1 | — | — | — | — | 1 | — |
| **TOTAL** | **144** | | | | | | |

### Coverage by Capability

| Capability | Unit | Contract | Integration | Coverage % | Gap |
|-----------|:---:|:---:|:---:|:---:|------|
| **Authentication** | 8 | — | 4 | 90% | ICICI session refresh |
| **Market Data (REST)** | 4 | — | 6 | 85% | Batch edge cases |
| **Market Depth** | 2 | — | 3 | 80% | 20-level Upstox/ICICI |
| **Historical Data** | 4 | — | 5 | 90% | Large pagination stress |
| **Option Chain** | 3 | — | 3 | 85% | Partial chain handling |
| **Orders (place/modify/cancel)** | 5 | 1 | 8 | 90% | Partial fill scenarios |
| **Order Query** | 2 | — | 3 | 80% | Status edge cases |
| **Portfolio** | 2 | — | 3 | 80% | Empty portfolio |
| **Margin** | 1 | — | 2 | 75% | Multi-leg margin |
| **WebSocket** | 2 | — | 6 | 70% | Reconnect, duplicate events |
| **Bracket/GTT/Slice** | 3 | — | 4 | 80% | Upstox/ICICI missing |
| **News** | — | — | 2 | 50% | Upstox only |
| **Instrument Catalog** | 3 | 2 | 1 | 85% | Large catalog performance |
| **Rate Limiting** | 1 | — | — | 40% | No integration tests |
| **Retry/Resilience** | 3 | — | — | 50% | No chaos/recovery tests |
| **Token Lifecycle** | 3 | — | 3 | 80% | Expired token recovery |
| **Circuit Breaker** | 1 | — | — | 30% | Not wired to brokers |
| **Reconciliation** | 3 | — | 2 | 70% | Partial fill reconciliation |
| **Replay** | 1 | — | — | 20% | Tick replay missing |
| **Simulation/Paper** | 1 | — | — | 30% | End-to-end flow missing |
| **Gateway Failover** | 1 | — | — | 25% | No integration test |

### Coverage Gaps (Critical)

| Gap | Severity | Impact |
|-----|:---:|--------|
| No chaos/recovery tests | **HIGH** | Unknown behavior under broker failures |
| WebSocket reconnect not tested | **HIGH** | Production disconnections unhandled |
| Rate limit integration tests missing | **HIGH** | Rate limiter may block legitimate requests |
| Circuit breaker not wired to brokers | **HIGH** | Cascading failures possible |
| Cover orders not implemented | **MEDIUM** | Feature gap for all brokers |
| Market status not implemented | **MEDIUM** | Cannot detect market hours programmatically |
| Duplicate event handling untested | **MEDIUM** | Duplicate ticks/orders may cause issues |
| Partial fill handling untested | **MEDIUM** | Order lifecycle gaps |

---

## PHASE 3: REAL ENDPOINT CERTIFICATION

### Endpoint Validation Matrix

| Endpoint | Dhan | Upstox | ICICI | Validation Method | Market Required? |
|----------|:---:|:---:|:---:|-------------------|:---:|
| **Auth/Token** | REAL | REAL | REAL | Integration tests | No |
| **LTP** | REAL | REAL | REAL | brokerRestTest | Yes (stale) |
| **Quote** | REAL | REAL | REAL | brokerRestTest | Yes (stale) |
| **Depth (5-level)** | REAL | REAL | REAL | brokerRestTest | Yes (stale) |
| **Depth (20-level)** | REAL | MOCK | MOCK | Dhan only | Yes (stale) |
| **OHLC** | REAL | REAL | REAL | brokerRestTest | Yes (stale) |
| **Batch LTP/Quote** | REAL | REAL | REAL | Unit tests | Yes (stale) |
| **Historical Candles** | REAL | REAL | REAL | Integration tests | **No** |
| **Option Expiries** | REAL | REAL | REAL | Integration tests | **No** |
| **Option Chain** | REAL | REAL | REAL | Integration tests | **No** |
| **Greeks** | REAL | REAL | MOCK | Unit + Integration | **No** |
| **Strike Selection** | REAL | REAL | REAL | Integration tests | **No** |
| **Rolling Options** | REAL | N/A | N/A | Integration tests | **No** |
| **Place Order** | REAL | REAL | REAL | Integration tests | Yes |
| **Modify Order** | REAL | REAL | REAL | Integration tests | Yes |
| **Cancel Order** | REAL | REAL | REAL | Integration tests | Yes |
| **Kill Switch** | REAL | REAL | N/A | Integration tests | **No** |
| **Bracket Order** | REAL | N/A | N/A | Integration tests | Yes |
| **GTT Order** | REAL | REAL | N/A | Integration tests | Yes |
| **Slice Order** | REAL | REAL | N/A | Integration tests | Yes |
| **Square Off** | REAL | N/A | N/A | Integration tests | Yes |
| **Order Book** | REAL | REAL | REAL | Integration tests | **No** |
| **Trade Book** | REAL | REAL | REAL | Integration tests | **No** |
| **Balance** | REAL | REAL | REAL | brokerRestTest | **No** |
| **Positions** | REAL | REAL | REAL | brokerRestTest | **No** |
| **Holdings** | REAL | REAL | REAL | brokerRestTest | **No** |
| **Margin Estimate** | REAL | REAL | REAL | Integration tests | **No** |
| **News** | N/A | REAL | N/A | Integration tests | **No** |
| **WebSocket Connect** | REAL | REAL | REAL | brokerWsTest | **No** |
| **WebSocket Market Feed** | REAL | REAL | REAL | brokerWsTest | Yes (no ticks) |
| **WebSocket Order Stream** | REAL | REAL | REAL | Integration tests | Yes |
| **Session Risk** | REAL | N/A | N/A | Integration tests | **No** |
| **Alerts** | REAL | REAL | N/A | Integration tests | **No** |
| **Instrument Catalog** | REAL | REAL | REAL | Contract tests | **No** |

### Validation Summary

| Validation Type | Dhan | Upstox | ICICI |
|----------------|:---:|:---:|:---:|
| **Real Broker APIs** | 28 endpoints | 22 endpoints | 16 endpoints |
| **Sandbox APIs** | 0 | 0 | 0 |
| **Mock/Unit** | 28 tests | 21 tests | 15 tests |
| **Never Tested Live** | Cover orders, Market status | Cover orders, Market status | Cover orders, Market status, GTT, Slice, Alerts |
| **Market Hours Required** | LTP, Quote, Depth, Live WS ticks, Orders | Same | Same |
| **Safe While Closed** | Historical, Options, Portfolio, Orders (read), Auth, Catalog | Same | Same |

---

## PHASE 4: BROKER GATEWAY READINESS

### Assessment

| Criterion | Status | Detail |
|-----------|:---:|--------|
| **Port completeness** | PASS | 15 port interfaces defined in broker-api |
| **Capability exposure** | PASS | All brokers implement `getCapability(Class)` |
| **Capability discovery** | PASS | `BrokerExplorer.inspect()` + `BrokerDescriptor` |
| **Extension support** | PASS | `BrokerExtras` escape hatch (DhanExtras, UpstoxExtras) |
| **SPI registration** | PASS | ServiceLoader discovers all 4 providers |
| **Gateway interface** | PASS | `BrokerGateway` interface + `DefaultBrokerGateway` |
| **Market Gateway** | PASS | `MarketGateway` wraps `BrokerRouter` |
| **Raw response capture** | PASS | `ResultMetadata.rawResponseBody` via Jackson |
| **Broker Inspector** | PASS | 20 live probes with latency measurement |
| **Certification** | PASS | `BrokerCertification.runFull()` with 25 checks |

### Refactoring Required Before Gateway Implementation

| Item | Effort | Risk | Dependency |
|------|:---:|:---:|------------|
| **Cover Order port interface** | 2d | Low | Broker API design |
| **Market Status port interface** | 1d | Low | Broker API design |
| **Upstox real order preview** | 2d | Medium | Upstox API docs |
| **ICICI auto token refresh** | 3d | High | TOTP automation |
| **Unified batch depth interface** | 1d | Low | 20-level depth for Upstox/ICICI |

### Gateway Compatibility Score: **90%**

The gateway layer is production-ready for the implemented capabilities. The 10% gap is in unimplemented port interfaces (Cover Orders, Market Status) and ICICI's manual token refresh.

---

## PHASE 5: SIMULATION READINESS

### Current State

| Component | Status | Detail |
|-----------|:---:|--------|
| **PaperBrokerConnection** | PARTIAL | New: 8 base prices, simulated orders, portfolio |
| **SimulationBrokerProvider** | PASS | SPI-registered, creates PaperBrokerConnection |
| **CandleReplaySession** | PARTIAL | Candle-only replay, no tick replay |
| **SimulatedOrderService** | PASS | MatchingEngine + PnLLedger for backtest |
| **TradingSimulation module** | EXIST | MatchingEngine, PnLLedger |

### Abstractions Missing

| Missing Abstraction | Impact | Effort |
|---------------------|--------|:---:|
| **TickReplaySession** | Cannot replay tick-level data for HFT strategies | 5d |
| **SimulatedMarketDataProvider** | PaperBrokerConnection uses random jitter, not historical data | 3d |
| **SimulatedWebSocketMultiplexer** | Paper WS is no-op; cannot subscribe to simulated ticks | 3d |
| **BacktestBrokerConnection** | No abstraction bridging SimulatedOrderService to IBrokerConnection | 5d |
| **Clock abstraction** | CandleReplaySession uses wall clock, not virtual clock | 2d |
| **ReplayInstrumentCatalog** | Replay needs time-aware instrument resolution (expiry-aware) | 3d |
| **SimulationPortfolioProvider** | Paper broker has static balance; no P&L tracking during simulation | 2d |

### Contracts to Extract

| Contract | From | To |
|----------|------|------|
| **BrokerClock** | CandleReplaySession | broker-api |
| **SimulatedExecution** | SimulatedOrderService | broker-api |
| **ReplayDataSource** | CandleReplaySession | replay-engine |
| **PaperTradingSession** | PaperBrokerConnection | broker-gateway/simulation |

### Broker Assumptions That Block Simulation

| Assumption | Location | Fix |
|-----------|----------|-----|
| Market data comes from HTTP REST | All broker adapters | Inject MarketDataProvider interface |
| WebSocket requires real connection | All WS multiplexers | SimulatedWebSocketMultiplexer |
| Token auth is required | Dhan/Upstox/ICICI connections | PaperBrokerConnection bypasses auth |
| Instrument catalog is loaded from broker | All InstrumentResolvers | Static catalog for simulation |
| Time is wall-clock | CandleReplaySession | VirtualClock injection |

### Simulation Readiness Score: **45%**

Major gaps exist in tick replay, backtest broker abstraction, virtual clock, and simulated WebSocket. The PaperBrokerConnection is a good start but only covers CLI/gateway scenarios, not full pipeline simulation.

---

## PHASE 6: PRODUCTION HARDENING

### Current State

| Hardening Area | Dhan | Upstox | ICICI | Core | Status |
|---------------|:---:|:---:|:---:|:---:|:---:|
| **Retry Logic** | DhanRetryExecutor (3 retries, exp backoff) | UpstoxRetryExecutor | IciciResilienceExecutor | RetryExecutor | **PASS** |
| **Timeouts** | HTTP client timeouts configured | HTTP client timeouts | HTTP client timeouts | — | **PASS** |
| **Circuit Breaker** | Available in core | Available in core | Available in core | CircuitBreaker class | **NOT WIRED** |
| **Rate Limiting** | MultiBucketRateLimiter (4 buckets) | Server-side + headers | Breeze limits | TokenBucketRateLimiter | **PASS** |
| **Auth Refresh** | DhanTokenManager (auto) | UpstoxTokenManager (auto) | Manual session key | TokenLifecycleService | **PARTIAL** |
| **WS Recovery** | DhanWebSocketMultiplexer reconnect | UpstoxWS reconnect | BreezeWS reconnect | ReconnectManager | **PASS** |
| **Session Recovery** | Auto-reconnect on drop | Auto-reconnect | Manual re-auth | BrokerLifecycleManager | **PARTIAL** |
| **Duplicate Events** | NOT HANDLED | NOT HANDLED | NOT HANDLED | — | **FAIL** |
| **Idempotency** | CaffeineIdempotencyCache | CaffeineIdempotencyCache | CaffeineIdempotencyCache | IdempotencyCachePort | **PASS** |
| **Order Lifecycle Recovery** | OrderReconciler | OrderReconciler | OrderReconciler | — | **PASS** |
| **Reconciliation** | OrderReconciler + TickReconciler | OrderReconciler | OrderReconciler | ReconciliationScheduler | **PASS** |
| **Audit Logging** | ChronicleAuditLogWriter | ChronicleAuditLogWriter | ChronicleAuditLogWriter | — | **PASS** |
| **Metrics** | ObservableOrderCommand + ObservableMarketDataProvider | Same | Same | Micrometer | **PASS** |
| **Tracing** | Correlation IDs in orders | Correlation IDs | Correlation IDs | — | **PASS** |
| **Health Checks** | BrokerStartupValidator | BrokerStartupValidator | BrokerStartupValidator | BrokerLifecycleManager | **PASS** |
| **Trading Circuit Breaker** | TradingCircuitBreaker | TradingCircuitBreaker | TradingCircuitBreaker | — | **PASS** |

### Weaknesses Identified

| # | Weakness | Severity | Broker | Fix |
|---|---------|:---:|--------|-----|
| 1 | Circuit breaker not wired to individual broker adapters | **HIGH** | All | Wire `CircuitBreaker` into each adapter's execute path |
| 2 | Duplicate event handling missing | **HIGH** | All | Add dedup layer in WebSocket multiplexer |
| 3 | ICICI manual token refresh | **HIGH** | ICICI | Implement TOTP-based auto-refresh |
| 4 | No reconnect storm protection | **MEDIUM** | All | Add exponential backoff to WS reconnect |
| 5 | Rate limiter not integration-tested | **MEDIUM** | All | Add rate limit integration tests |
| 6 | No broker-level circuit breaker config | **MEDIUM** | All | Per-broker circuit breaker thresholds |
| 7 | Partial fill reconciliation untested | **MEDIUM** | All | Add partial fill test scenarios |
| 8 | No graceful degradation on broker failure | **MEDIUM** | All | FailoverWebSocketMultiplexer needs testing |

---

## PHASE 7: MISSING TESTS

### Critical Missing Test Cases

#### Authentication & Session
| # | Test Case | Broker | Type | Priority |
|---|----------|--------|------|:---:|
| 1 | Invalid credentials returns descriptive error | All | Unit | P0 |
| 2 | Expired token triggers automatic refresh | Dhan, Upstox | Integration | P0 |
| 3 | ICICI session expiry triggers re-authentication | ICICI | Integration | P0 |
| 4 | Token refresh during active WebSocket connection | Dhan, Upstox | Integration | P1 |
| 5 | Concurrent token refresh (race condition) | All | Concurrency | P1 |
| 6 | TOTP generation produces valid 6-digit codes | Dhan, ICICI | Unit | P2 |

#### Rate Limiting & Resilience
| # | Test Case | Broker | Type | Priority |
|---|----------|--------|------|:---:|
| 7 | Rate limit 429 response triggers backoff | All | Integration | P0 |
| 8 | Rate limiter permits after cooldown period | All | Unit | P1 |
| 9 | Multi-bucket limiter isolates categories | Dhan | Unit | P1 |
| 10 | Circuit breaker opens after N failures | All | Unit | P0 |
| 11 | Circuit breaker half-open allows probe request | All | Unit | P1 |
| 12 | Broker downtime triggers graceful degradation | All | Chaos | P1 |
| 13 | Network timeout returns meaningful error | All | Unit | P1 |
| 14 | DNS resolution failure handled gracefully | All | Chaos | P2 |
| 15 | Connection refused triggers retry with backoff | All | Integration | P1 |

#### WebSocket
| # | Test Case | Broker | Type | Priority |
|---|----------|--------|------|:---:|
| 16 | WebSocket disconnect triggers automatic reconnect | All | Integration | P0 |
| 17 | Reconnect restores all subscriptions | All | Integration | P0 |
| 18 | Reconnect storm (rapid disconnect/reconnect) throttled | All | Concurrency | P1 |
| 19 | Duplicate tick event is deduplicated | All | Unit | P1 |
| 20 | Duplicate order update is deduplicated | All | Unit | P1 |
| 21 | Heartbeat timeout triggers reconnect | Dhan | Integration | P1 |
| 22 | Binary frame parsing handles malformed data | All | Unit | P1 |
| 23 | Large binary frame (20-depth) parsed correctly | Dhan | Unit | P2 |
| 24 | WebSocket subscription manager tracks state | Dhan | Unit | P2 |

#### Order Lifecycle
| # | Test Case | Broker | Type | Priority |
|---|----------|--------|------|:---:|
| 25 | Partial fill updates position correctly | All | Integration | P0 |
| 26 | Order rejection returns error with reason | All | Unit | P0 |
| 27 | Modify order after partial fill | All | Integration | P1 |
| 28 | Cancel order after partial fill | All | Integration | P1 |
| 29 | Order status transitions (PENDING→OPEN→TRADED) | All | Unit | P1 |
| 30 | Kill switch prevents new orders | Dhan, Upstox | Integration | P1 |
| 31 | Bracket order target/SL triggers | Dhan | Integration | P1 |
| 32 | GTT order triggers on price condition | Dhan, Upstox | Integration | P2 |

#### Data Integrity
| # | Test Case | Broker | Type | Priority |
|---|----------|--------|------|:---:|
| 33 | Historical pagination returns complete dataset | All | Integration | P0 |
| 34 | Large option chain (500+ strikes) parsed correctly | All | Performance | P1 |
| 35 | Large portfolio (100+ holdings) returned completely | All | Performance | P1 |
| 36 | Empty response (null/empty arrays) handled | All | Unit | P1 |
| 37 | Malformed JSON response handled gracefully | All | Unit | P1 |
| 38 | OHLC integrity (high >= open,close >= low) | All | Unit | P2 |
| 39 | Candle timestamps are contiguous (no gaps) | All | Unit | P2 |
| 40 | Paisa precision maintained through mapping | All | Unit | P1 |

#### Gateway & Failover
| # | Test Case | Broker | Type | Priority |
|---|----------|--------|------|:---:|
| 41 | Gateway failover switches to backup broker | All | Integration | P0 |
| 42 | Capability discovery returns correct matrix | All | Unit | P1 |
| 43 | Load-balanced gateway distributes requests | All | Performance | P1 |
| 44 | Broker extras accessible via gateway handle | Dhan, Upstox | Unit | P2 |
| 45 | Raw response capture serializes correctly | All | Unit | P2 |

#### Simulation & Replay
| # | Test Case | Broker | Type | Priority |
|---|----------|--------|------|:---:|
| 46 | PaperBrokerConnection returns realistic quotes | Sim | Unit | P1 |
| 47 | Paper order placement and cancellation | Sim | Unit | P1 |
| 48 | Candle replay session play/pause/step | Sim | Unit | P1 |
| 49 | Backtest with simulated broker produces P&L | Sim | Integration | P1 |
| 50 | Replay speed multiplier affects timing | Sim | Unit | P2 |

---

## PHASE 8: GO-LIVE READINESS

### Dhan Production — **GO WITH CONDITIONS**

| Criterion | Status | Evidence |
|-----------|:---:|----------|
| Port coverage (14/15) | PASS | All port interfaces except NewsProvider |
| Unit tests (28/28 pass) | PASS | All broker module tests green |
| Integration tests (27 pass) | PASS | Real endpoint validation complete |
| Auth auto-refresh | PASS | DhanTokenManager with TOTP |
| Rate limiting | PASS | MultiBucketRateLimiter (4 buckets) |
| Retry logic | PASS | DhanRetryExecutor (3 retries, exp backoff) |
| WebSocket (3 channels) | PASS | Market feed + order stream + 20-depth |
| Order lifecycle | PASS | Place, modify, cancel, kill switch, bracket, GTT, slice |
| Reconciliation | PASS | OrderReconciler + TickReconciler |
| Audit logging | PASS | ChronicleAuditLogWriter |
| Metrics | PASS | Observable wrappers on all adapters |

**Conditions**:
1. Wire CircuitBreaker into broker adapters before go-live
2. Add duplicate event handling in WebSocket multiplexer
3. Add rate limit integration tests

### Upstox Production — **GO WITH CONDITIONS**

| Criterion | Status | Evidence |
|-----------|:---:|----------|
| Port coverage (12/15) | PASS | Missing bracket, session risk |
| Unit tests (21/21 pass) | PASS | All broker module tests green |
| Integration tests (11 pass) | PASS | Real endpoint validation |
| Auth (OAuth PKCE) | PASS | UpstoxTokenManager with auto-refresh |
| WebSocket | PASS | Market feed multiplexer |
| News provider | PASS | Unique capability |
| Order lifecycle | PASS | Place, modify, cancel, GTT, slice |
| Margin | PASS | UpstoxMarginProvider |

**Conditions**:
1. Wire CircuitBreaker into broker adapters
2. Add duplicate event handling
3. Implement real order preview (currently stub)
4. Add WebSocket reconnect integration test

### ICICI Production — **NO GO**

| Criterion | Status | Evidence |
|-----------|:---:|----------|
| Port coverage (9/15) | PARTIAL | Missing 6 port interfaces |
| Unit tests (15/15 pass) | PASS | All broker module tests green |
| Integration tests (11 pass) | PASS | Real endpoint validation |
| Auth auto-refresh | **FAIL** | Manual session key required |
| Advanced orders | **FAIL** | No bracket, GTT, slice orders |
| News | **FAIL** | No news provider |
| Session risk | **FAIL** | No session risk provider |

**Blocking Issues**:
1. Manual token refresh is unacceptable for production (session expires, trading stops)
2. Missing advanced order types limits strategy execution
3. 6/15 port interfaces not implemented

**Path to GO**:
1. Implement TOTP-based auto-refresh (P0, 3 days)
2. Add GTT order support (P1, 3 days)
3. Add bracket order support (P1, 3 days)

---

## PHASE 9: PRIORITIZED ROADMAP

### P0 — Must Fix Before Go-Live

| # | Item | Effort | Risk | Dependencies | Impact |
|---|------|:---:|:---:|------------|--------|
| 1 | Wire CircuitBreaker into all broker adapters | 3d | Low | CircuitBreaker class exists | Prevents cascading failures |
| 2 | Add duplicate event handling in WS multiplexers | 3d | Medium | Dedup by event ID + timestamp | Prevents duplicate orders/ticks |
| 3 | ICICI auto token refresh via TOTP | 3d | High | BreezeTotpGenerator exists | Enables ICICI production |
| 4 | Rate limit integration tests | 2d | Low | Rate limiter exists | Validates rate limit behavior |
| 5 | WebSocket reconnect integration tests | 2d | Low | Reconnect logic exists | Validates production recovery |
| 6 | Invalid credentials test cases | 1d | Low | None | Security validation |
| 7 | Expired token auto-refresh tests | 2d | Low | Token manager exists | Auth resilience |

**Total P0 effort: 16 days**

### P1 — Must Fix Before Gateway Launch

| # | Item | Effort | Risk | Dependencies | Impact |
|---|------|:---:|:---:|------------|--------|
| 8 | Cover Order port interface + Dhan implementation | 3d | Low | Broker API design | Feature parity |
| 9 | Market Status port interface | 2d | Low | Broker API design | Market hours awareness |
| 10 | Upstox real order preview | 2d | Medium | Upstox API docs | Accurate margin estimates |
| 11 | Per-broker circuit breaker configuration | 2d | Low | CircuitBreaker wiring | Tuned resilience |
| 12 | Reconnect storm protection | 2d | Medium | WS reconnect logic | Prevents broker bans |
| 13 | Partial fill reconciliation tests | 2d | Low | OrderReconciler exists | Order accuracy |
| 14 | Gateway failover integration test | 2d | Medium | FailoverWebSocketMultiplexer | HA validation |
| 15 | Concurrent token refresh race condition test | 1d | Low | Token manager | Auth stability |
| 16 | Bracket order tests for Dhan | 1d | Low | DhanBracketOrderAdapter exists | Order validation |

**Total P1 effort: 17 days**

### P2 — Must Fix Before Replay/Simulation

| # | Item | Effort | Risk | Dependencies | Impact |
|---|------|:---:|:---:|------------|--------|
| 17 | TickReplaySession | 5d | Medium | CandleReplaySession exists | Tick-level backtesting |
| 18 | SimulatedMarketDataProvider (historical-backed) | 3d | Low | PaperBrokerConnection exists | Realistic paper trading |
| 19 | SimulatedWebSocketMultiplexer | 3d | Medium | PaperBrokerConnection exists | Full pipeline simulation |
| 20 | BacktestBrokerConnection abstraction | 5d | High | IBrokerConnection interface | Strategy backtesting |
| 21 | VirtualClock injection for replay | 2d | Low | VirtualClock exists | Time-travel debugging |
| 22 | ReplayInstrumentCatalog (expiry-aware) | 3d | Medium | Instrument catalog | Options replay |
| 23 | SimulationPortfolioProvider (P&L tracking) | 2d | Low | PaperBrokerConnection exists | Accurate simulation P&L |
| 24 | GTT order tests for Upstox | 1d | Low | UpstoxGttOrderAdapter exists | GTT validation |
| 25 | Large option chain performance test | 1d | Low | None | Performance baseline |

**Total P2 effort: 25 days**

### P3 — Future Improvements

| # | Item | Effort | Risk | Impact |
|---|------|:---:|:---:|--------|
| 26 | ICICI bracket order support | 3d | Medium | ICICI feature parity |
| 27 | ICICI GTT order support | 3d | Medium | ICICI feature parity |
| 28 | ICICI slice order support | 2d | Low | ICICI feature parity |
| 29 | ICICI news/alerts support | 2d | Low | ICICI feature parity |
| 30 | Dhan news provider | 2d | Low | Dhan feature parity |
| 31 | Chaos testing framework | 5d | Low | Production resilience |
| 32 | Performance benchmark suite (JMH) | 3d | Low | Latency baselines |
| 33 | Upstox 20-level depth | 3d | Medium | Depth parity |
| 34 | Sandbox environment support | 3d | Low | Safe testing |
| 35 | Broker health dashboard | 3d | Low | Operational visibility |

**Total P3 effort: 29 days**

---

## DELIVERABLE SUMMARY

| # | Deliverable | Location |
|---|-----------|----------|
| 1 | Broker Capability Matrix | Phase 1 above |
| 2 | Test Coverage Matrix | Phase 2 above |
| 3 | Real Endpoint Certification Matrix | Phase 3 above |
| 4 | Missing Coverage Report | Phase 2 gaps + Phase 7 |
| 5 | Missing Test Cases (50 cases) | Phase 7 above |
| 6 | Gateway Readiness Assessment | Phase 4 above |
| 7 | Simulation Readiness Assessment | Phase 5 above |
| 8 | Production Hardening Plan | Phase 6 above |
| 9 | Go-Live Recommendation | Phase 8 above |
| 10 | Prioritized Remediation Roadmap | Phase 9 above |

---

## TOTAL EFFORT ESTIMATE

| Priority | Effort | Timeline | Blocks |
|----------|:---:|:---:|--------|
| **P0** | 16 days | 2 weeks | Go-live |
| **P1** | 17 days | 2 weeks | Gateway launch |
| **P2** | 25 days | 3 weeks | Replay/Simulation |
| **P3** | 29 days | 4 weeks | Feature parity |
| **Total** | **87 days** | **~11 weeks** | Full production |
