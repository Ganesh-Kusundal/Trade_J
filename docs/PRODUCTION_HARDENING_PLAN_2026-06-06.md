# Production-Grade Hardening Plan

**Scope:** Single-user production readiness. All P0 fixes + full regression suite + real data validation. Multi-user isolation excluded.

**Total P0s to fix:** 22 (24 minus 2 multi-user items)  
**Estimated effort:** ~65 developer-days  
**Organized into:** 6 workstreams, parallelizable via multi-agent teams

---

## WORKSTREAM 1: Runtime / Disruptor Critical Fixes (10 P0s, ~22d)

These are the money-losing bugs. Fix first.

### W1-1: AsyncDispatchHandler — block-on-full instead of silent drop (3d)
**File:** `runtime/disruptor/src/main/java/com/tradej/disruptor/config/AsyncDispatchHandler.java:166`
**Fix:** Replace `dispatchQueue.offer()` with bounded `offer(timeout)` + backpressure signal. Wire `droppedEventCount` to `/actuator/prometheus` and `DisruptorReadinessHealthIndicator`. Add DLQ replay path.
**Test:** `AsyncDispatchHandlerBackpressureTest` — fill queue, verify blocking, verify DLQ, verify health alarm.

### W1-2: Dedup — source/sequence keyed, persistence-backed (3d)
**File:** `runtime/disruptor/src/main/java/com/tradej/disruptor/DisruptorEventBus.java:368`
**Fix:** Replace `eventId` UUID dedup with `(brokerSource, exchangeSegment, instrumentKey, sequenceNumber)` for market data and `(brokerOrderId, transition)` for OMS. Back with DuckDB `seen_events` table.
**Test:** `DedupPersistenceTest` — restart process, verify broker replay events are caught.

### W1-3: KillSwitch — atomic guard with coordinated unwind (2d)
**File:** `trading/execution/src/main/java/com/tradej/execution/risk/PositionRiskHandler.java:279`
**Fix:** Replace `volatile boolean` with `AtomicBoolean` + `ReentrantLock` for activate/deactivate. Add coordinated unwind: cancel open orders → halt signals → drain pipeline → confirm.
**Test:** `KillSwitchAtomicityTest` — concurrent activate + signal processing, verify no race.

### W1-4: cancelOrder — state check before broker call (1d)
**File:** `trading/execution/src/main/java/com/tradej/execution/service/OrderManagementService.java:131`
**Fix:** Check `OrderStateMachine` state BEFORE calling `brokerConnection.orders().cancelOrder()`. Reject if already CANCELLED/FILLED.
**Test:** `CancelOrderStateGuardTest` — verify double-cancel rejected, cancel-after-fill rejected.

### W1-5: ExecutionHandler — partition by symbol (5d)
**File:** `trading/execution/src/main/java/com/tradej/execution/service/ExecutionHandler.java`
**Fix:** Partition by `symbol.hashCode() % N` into N independent worker threads each with its own `ArrayBlockingQueue`. N=4 default, configurable.
**Test:** `ExecutionHandlerPartitioningTest` — verify concurrent symbol processing, verify ordering within symbol.

### W1-6: ExecutionHandler — immutable downstream capture (1d)
**File:** `trading/execution/src/main/java/com/tradej/execution/service/ExecutionHandler.java`
**Fix:** Replace mutable `currentDownstream` field with constructor-injected immutable reference.
**Test:** Covered by existing ExecutionHandler tests.

### W1-7: TradeUpdated — remove zero-value placeholders (1d)
**File:** `trading/execution/src/main/java/com/tradej/execution/service/ExecutionHandler.java`
**Fix:** Only emit `TradeUpdated` when quantity > 0 and pnl != 0. Skip placeholder emissions.
**Test:** `TradeUpdatedEmissionTest` — verify no zero-value events.

### W1-8: PortfolioEngine — move off ring thread (3d)
**File:** `trading/strategy/src/main/java/com/tradej/strategy/service/PortfolioEngine.java`
**Fix:** Run PortfolioEngine on a dedicated thread with its own queue. Decouple from ring buffer thread.
**Test:** `PortfolioEngineIsolationTest` — verify portfolio computation doesn't block strategy/risk.

### W1-9: MarketDataPipeline — tick vs order-book distinction (2d)
**File:** `runtime/hotpath/src/main/java/com/tradej/hotpath/MarketDataPipeline.java`
**Fix:** Separate tick processing path from depth processing path. Rate-limit ticks independently. Don't drop depth updates on tick rate-limit.
**Test:** `MarketDataPipelineSeparationTest` — verify depth updates flow even when ticks are rate-limited.

### W1-10: ScanEngine — wire StreamingScanCriterion into hot loop (3d)
**File:** `trading/scanner/src/main/java/com/tradej/scanner/engine/ScanEngine.java`
**Fix:** Wire `StreamingScanCriterion` implementations into the Disruptor tick processing path. Enable real-time scan evaluation on each tick.
**Test:** `StreamingScanIntegrationTest` — verify scan fires on live tick.

---

## WORKSTREAM 2: Gateway P0s (2 P0s, ~7d)

### W2-1: GatewayTopicRouter — per-client isolation (5d)
**File:** `gateway/src/main/java/com/tradej/gateway/router/GatewayTopicRouter.java`
**Fix:** Replace single-thread publish with per-transport write queue. Each WebSocket transport gets its own `ArrayBlockingQueue<byte[]>` with configurable capacity (default 1024). Slow clients fill their queue; upstream applies backpressure. Drop policy: oldest-first eviction with drop counter per transport.
**Test:** `GatewayTopicRouterIsolationTest` — one slow client, verify other clients unaffected.

### W2-2: GatewayEventBridge — eliminate per-event allocation (2d)
**File:** `gateway/src/main/java/com/tradej/gateway/bridge/GatewayEventBridge.java`
**Fix:** Replace `new LinkedHashMap()` per event with `ThreadLocal<ObjectNode>` or pre-allocated `ObjectNode` pool. Reuse `ObjectMapper` (already shared). Use `ObjectNode.put()` instead of Map construction.
**Test:** `GatewayEventBridgeAllocationTest` — JMH benchmark, verify zero-allocation hot path.

---

## WORKSTREAM 3: Simulation / Backtest P0s (3 P0s, ~13d)

### W3-1: BacktestServiceImpl — real implementation (5d)
**File:** `trading/simulation/src/main/java/com/tradej/simulation/service/BacktestServiceImpl.java`
**Fix:** Replace mock results with real backtest execution: load historical candles → replay through strategy → simulate fills via MatchingEngine → compute P&L → return real BacktestResult.
**Test:** `BacktestServiceImplIntegrationTest` — run known strategy on known data, verify P&L matches expected.

### W3-2: Unify BacktestResult schemas (3d)
**Files:** Three `BacktestResult` records across modules.
**Fix:** Create single canonical `BacktestResult` in `core/` module. Migrate all three implementations to use it. Add adapter methods for backward compatibility.
**Test:** `BacktestResultSchemaTest` — verify all three code paths produce same schema.

### W3-3: HistoricalRangeService — decompose god class (5d)
**File:** `data/persistence/src/main/java/com/tradej/persistence/replay/HistoricalRangeService.java`
**Fix:** Split into: `HistoricalCandleLoader` (read), `HistoricalRangeValidator` (validation), `HistoricalFillStrategy` (gap filling), `HistoricalRangeQuery` (query builder). Each < 200 lines.
**Test:** Existing tests refactored to target each new class.

---

## WORKSTREAM 4: Multi-Asset P0s (4 P0s, ~10d)

### W4-1: MatchingEngine — exchange-specific tick sizes (2d)
**File:** `trading/simulation/src/main/java/com/tradej/simulation/MatchingEngine.java`
**Fix:** Replace hard-coded 5 paisa with `ExchangeTickSizeRegistry` that maps `(exchange, segment, instrumentType)` → tick size. Pre-populate: NSE_EQ=5p, NSE_FNO=5p, MCX=1p, CDS=0.25p.
**Test:** `MatchingEngineTickSizeTest` — verify correct tick size per exchange.

### W4-2: SessionSchedule — multi-exchange calendar (2d)
**File:** `trading/simulation/` (wherever SessionSchedule lives)
**Fix:** Replace binary MCX-vs-rest with `ExchangeCalendar` registry. Each exchange has its own open/close/holiday schedule. Pre-populate NSE, BSE, MCX, CDS.
**Test:** `ExchangeCalendarTest` — verify market hours per exchange.

### W4-3: ProductType — abstract to exchange-agnostic enum (3d)
**File:** `core/src/main/java/com/tradej/core/domain/value/ProductType.java`
**Fix:** Rename Indian-specific values: `CNC` → `DELIVERY`, `MIS` → `INTRADAY_MARGIN`, `MTF` → `MARGIN_FUNDING`. Add `@Deprecated` aliases for backward compat. Update all broker adapters.
**Test:** `ProductTypeMappingTest` — verify all brokers map correctly.

### W4-4: Instrument.instrumentType — proper enum (3d)
**File:** `core/src/main/java/com/tradej/core/domain/model/Instrument.java`
**Fix:** Replace `String instrumentType` with `InstrumentType` enum: `EQUITY, FUTURE, OPTION_CALL, OPTION_PUT, INDEX, CURRENCY_FUTURE, CURRENCY_OPTION, COMMODITY_FUTURE, COMMODITY_OPTION`. Add parser from string.
**Test:** `InstrumentTypeParserTest` — verify all broker instrument types parse correctly.

---

## WORKSTREAM 5: Plugin / Utils P0s (2 P0s, ~3d)

### W5-1: ContractSymbolNormalizer — fail on unrecognized (1d)
**File:** `core/src/main/java/com/tradej/core/domain/instrument/ContractSymbolNormalizer.java`
**Fix:** Instead of returning uppercase input unchanged, throw `UnrecognizedContractSymbolException` when no pattern matches. Add `normalizeOrNull()` for callers that want lenient behavior.
**Test:** `ContractSymbolNormalizerFailureTest` — verify exception on garbage input.

### W5-2: DuckDbQueryEngine — transactional registration (2d)
**File:** `broker-gateway/src/main/java/com/tradej/brokergateway/query/DuckDbQueryEngine.java`
**Fix:** Wrap `registerDatasource` in a savepoint. If `ds.register()` throws, rollback the savepoint. Engine remains in consistent state.
**Test:** `DuckDbQueryEngineTransactionalTest` — verify half-init doesn't corrupt engine.

---

## WORKSTREAM 6: Test P0s + Regression Suite (3 P0s, ~10d)

### W6-1: LiveDhanTestSupport — parallel-safe isolation (2d)
**File:** `broker/dhan/src/testFixtures/` or wherever LiveDhanTestSupport lives
**Fix:** Replace shared mutable state with `@TempDir` per test. Each test gets its own token state file, catalog cache, and settings. Add `@Execution(CONCURRENT)` annotation.
**Test:** Verify `./gradlew broker-dhan:test --parallel` passes.

### W6-2: TradingHotPathE2E — real broker test (3d)
**File:** `app/src/test/java/`
**Fix:** Add `@Tag("live")` E2E test that connects to Dhan sandbox, places a real order, verifies fill, cancels, verifies cancel. Uses `DhanConnectionSettings.sandboxWithDefaults()`.
**Test:** `TradingHotPathLiveTest` — full order lifecycle against Dhan sandbox.

### W6-3: Real Data Validation Suite (5d)
**New file:** `app/src/test/java/com/tradej/app/validation/RealDataValidationSuite.java`

Create a comprehensive validation suite that runs against live broker data:

```
RealDataValidationSuite/
├── MarketDataValidationTest        — LTP, quote, depth, OHLC vs known values
├── OptionChainValidationTest       — chain completeness, greeks sanity, strike spacing
├── HistoricalDataValidationTest    — candle integrity, gap detection, OHLC invariant
├── OrderLifecycleValidationTest    — place → acknowledge → fill → settle
├── PortfolioConsistencyTest        — balance = holdings_value + positions_margin + cash
├── InstrumentCatalogValidationTest — symbol resolution, segment mapping, lot sizes
├── WebSocketStreamValidationTest   — tick rate, depth update rate, no duplicates
├── MarginEstimateValidationTest    — estimate vs actual margin on placement
└── ReconciliationValidationTest    — broker positions match local positions
```

Each test:
- Connects to Dhan sandbox (or live if credentials available)
- Fetches real data
- Validates invariants (OHLC: high >= open,close >= low; quantity > 0; price > 0)
- Cross-validates between endpoints (LTP within quote's bid-ask spread)
- Reports pass/fail with evidence

---

## EXECUTION ORDER

| Phase | Workstreams | Duration | Agents |
|-------|------------|----------|:---:|
| **Phase 1** | W1 (Runtime) + W2 (Gateway) | 2 weeks | 3 agents |
| **Phase 2** | W3 (Simulation) + W4 (Multi-Asset) | 2 weeks | 3 agents |
| **Phase 3** | W5 (Plugin) + W6 (Tests) | 1 week | 2 agents |
| **Phase 4** | Integration + Regression run | 3 days | 1 agent |

**Total: ~6 weeks with multi-agent parallelism**

---

## SUCCESS CRITERIA

1. All 22 P0 findings verified FIXED with evidence
2. Full `./gradlew unitTest` passes with 0 failures
3. Real Data Validation Suite passes against Dhan sandbox
4. No `@Deprecated` constructor foot-guns in DisruptorEventBus
5. Dedup survives process restart (persistence-backed)
6. KillSwitch is atomic and coordinated
7. ExecutionHandler partitions by symbol (4 threads)
8. Gateway router isolates slow clients
9. BacktestServiceImpl produces real P&L
10. All instrument types use proper enums, not strings
