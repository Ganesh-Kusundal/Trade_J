# Depth-of-Market Analytics & Trading Screen — Implementation Plan

**Status**: READY FOR EXECUTION
**Created**: 2026-06-06
**Owner**: trade-j
**Pre-req cleanup**: this plan supersedes `1780685428747-curious-island.md` (PLANNING) and includes the OrderBookEngine pre-req from the broker-gateway standardization work

---

## 1. Goal

Build a production-grade DOM (Depth-of-Market) analytics pipeline + read-only trading screen for the React terminal. Reuses the WebSocket path that already carries `DepthUpdateEvent` end-to-end, and adds 3 new analytics engines, a heatmap recorder, 6 new WebSocket topics, REST hydration, and a Canvas-based frontend.

**Strict architectural constraints** (enforced by review before merge):
- All new types live in `core` (canonical), not in any broker module
- `IndexSymbols`, `InstrumentKey`, `ExchangeSegment` are the only instrument types on public surfaces
- New broker-internal SPI methods get `@BrokerInternal` if needed
- All Dhan wire codes route through `DhanSegmentMapper.toWireValue()` (the seam established in RP-099)
- The `BrokerHandle.invoke()` dynamic-dispatch pattern is the public way to reach new analytics
- No new module-level state outside the existing `broker-core` depth package

---

## 2. Critical Pre-Req — Fix `OrderBookEngine` (blocks all downstream work)

**Problem**: `broker/core/.../OrderBookEngine.java:27` calls non-existent `book.apply(4 args)`. `OrderBook` only has `update(2 args)`. This is blocking all `:broker-*` test compilation since my standardization work.

**Two equally-valid fix paths** (pick one, both keep the public API of `OrderBook` clean):

### Option A — Add `apply(...)` to `OrderBook` (preferred)
- Add `public synchronized void apply(List<DepthLevel> bids, List<DepthLevel> asks, int levels, long exchangeTimestampMs)` to `OrderBook.java`
- It calls `update(bids, asks)` and stores `levels` + `exchangeTimestampMs` in two new `volatile` fields
- Add `levels()` and `exchangeTimestampMs()` accessors
- Add `OrderBookSnapshot` fields for both new values
- Keeps `OrderBookEngine.java` working as-is (intent was clear: persist all 4 args)

### Option B — Downgrade `OrderBookEngine.java` to 2-arg `update`
- Change `book.apply(bids, asks, levels, ts)` → `book.update(bids, asks)`
- Drop `levels` and `exchangeTimestampMs` from the engine (they're already on `DepthUpdateEvent` upstream)
- Smaller diff, but loses per-book `levels()` accessors

**Tests** (must all pass before any other phase):
- Existing `OrderBookEngineTest` (5 tests at `broker/core/src/test/.../OrderBookEngineTest.java`) — must continue to pass
- New `OrderBookTest` unit tests (currently missing — see §10.1)

**Effort**: 1-2 hours
**RP**: RP-101 in `docs/BACKLOG.md`
**INV**: INV-47 in `REGRESSION_MANIFEST.md`

---

## 3. Phase 1 — Analytics Engines (3 new + 1 existing port)

**Location**: `trading/analytics/src/main/java/com/tradej/analytics/depth/`
(or `trading/strategy/.../analytics/depth/` if analytics is rolled into strategy; pick by what's already there)

### 3.1 `SupportResistanceAnalyzer` (S/R Levels)

**Purpose**: Detect price levels where buy/sell pressure clusters by finding local maxima in cumulative bid/ask quantities.

**Algorithm** (deterministic, no ML):
- For each price bucket of 5 paisa (configurable):
  - Compute rolling bid+ask quantity over a 100-tick window (configurable)
  - A level is "support" if bid quantity > 80th percentile AND no higher bid in ±3 buckets
  - A level is "resistance" if ask quantity > 80th percentile AND no higher ask in ±3 buckets
- Return top 5 support + top 5 resistance per symbol

**Public type** (in `core/.../analytics/`):
```java
public record SupportResistanceLevels(
    InstrumentKey key,
    List<PriceLevel> support,    // PriceLevel(pricePaisa, strength 0..1, lastSeenMs)
    List<PriceLevel> resistance,
    long computedAtMs
) {}

public record PriceLevel(long pricePaisa, double strength, long lastSeenMs) {}
```

**Output topic**: `GatewayTopic.SR_LEVELS_UPDATE(15)` — already declared in `GatewayTopic` enum, no wire change needed

**Tunables** (all in `application.yml`):
- `tradej.analytics.sr.bucketPaisa: 5`
- `tradej.analytics.sr.windowTicks: 100`
- `tradej.analytics.sr.topN: 5`

### 3.2 `IcebergDetector`

**Purpose**: Identify iceberg orders — same price level repeatedly replenished with small visible quantity.

**Algorithm**:
- For each (price, side) in current depth:
  - Look back through the last 60 ticks (configurable) at this price
  - Count "refresh events" (level disappeared then reappeared within 5 ticks)
  - If refresh count ≥ 3 AND average visible quantity within 50% of current → mark as iceberg
- Emit alert with: side, price, estimated hidden quantity = sum of all fills at this price since first refresh

**Public type**:
```java
public record IcebergAlert(
    InstrumentKey key,
    Side side,                // BID or ASK
    long pricePaisa,
    long estimatedHiddenQty,
    int refreshCount,
    long firstSeenMs,
    long lastRefreshMs
) {
    public enum Side { BID, ASK }
}
```

**Output topic**: `GatewayTopic.ICEBERG_ALERT(13)` — already declared

**Anti-spam**: emit at most 1 alert per (symbol, side, price) per 5 minutes. State in a `ConcurrentHashMap<AlertKey, Long>` keyed on `(symbol, side, price)`.

### 3.3 `AbsorptionDetector`

**Purpose**: Detect when one side is absorbing the other — large orders being filled without price movement (suggests institutional activity).

**Algorithm**:
- Track last 30 ticks per symbol (configurable)
- Compute "absorption score" = (cumulative quantity traded at best bid/ask) / (price range in ticks)
- High score + low price movement + high volume = absorption
- Threshold: score > 0.7 AND price range < 2 ticks AND volume > 50,000 qty (configurable)

**Public type**:
```java
public record AbsorptionAlert(
    InstrumentKey key,
    Side absorbingSide,         // who is absorbing the other side
    double absorptionScore,     // 0..1
    long priceRangePaisa,
    long volumeAtLevel,
    long startTickMs,
    long endTickMs
) {
    public enum Side { BID_ABSORBING_ASKS, ASK_ABSORBING_BIDS }
}
```

**Output topic**: `GatewayTopic.ABSORPTION_ALERT(14)` — already declared

### 3.4 `DepthImbalanceAnalyzer` (NEW — wraps existing strategy)

The existing `DepthImbalanceStrategy` at `trading/strategy/.../DepthImbalanceStrategy.java` computes ratio. Promote to a proper analyzer:
- Type: `ImbalanceSnapshot(InstrumentKey, double ratio, long topBidQty, long topAskQty, long timestampMs)`
- Output topic: `GatewayTopic.DEPTH_IMBALANCE(11)` — already declared
- Keep `DepthImbalanceStrategy` as a signal consumer of `ImbalanceSnapshot`

### 3.5 Common analyzer contract

All 4 analyzers implement:
```java
public interface DepthAnalyzer {
    String name();
    void onDepthUpdate(DepthUpdateEvent event);
    void onTick(MarketTickEvent tick);
    /** Clear per-symbol state for the given instrument (e.g. on unsubscribe). */
    void clear(InstrumentKey key);
}
```

**Threading**: analyzers run on a single `ExecutorService` (1 thread) consuming from the Disruptor event bus. Order-book engine runs on the same thread, so all `OrderBook` reads are serialized — no need for concurrent access from analyzers.

**Tests per analyzer** (per §10):
- Unit: synthetic depth events → expected alert/level
- Unit: edge cases (empty book, single-sided, rapid price change)
- Unit: tuning parameters affect output
- Contract: analyzer output types are wire-stable across rebuilds (JSON round-trip test)

---

## 4. Phase 2 — `HeatmapRecorder`

**Location**: `runtime/hotpath/src/main/java/com/tradej/hotpath/heatmap/HeatmapRecorder.java`

**Purpose**: Roll per-second depth snapshots into 5-minute chunks for the heatmap visualization. Per `curious-island` plan §1 "Decisions Confirmed": heatmap window = 5 min (300 snapshots at 1/sec).

**Algorithm**:
- On each `DepthUpdateEvent`:
  - Compute "imbalance cell" = `sign(cumulativeImbalance) * log10(abs(cumulativeImbalance) + 1)`
  - Bucket by (symbol, price-bucket-of-5-paisa, time-bucket-of-1-sec)
  - If time-bucket differs from last → emit "rotate" event
- On 5-minute boundary:
  - Emit `HeatmapChunk` (300 cells per symbol, oldest 60s discarded)
  - Reset accumulator for that symbol

**Public type** (in `core/.../analytics/`):
```java
public record HeatmapChunk(
    InstrumentKey key,
    long startMs,           // 5-min window start
    long endMs,             // 5-min window end
    List<HeatmapCell> cells,  // sorted by (timeSecOffset, pricePaisa)
    double minIntensity,    // for color scaling
    double maxIntensity
) {}

public record HeatmapCell(
    int timeSecOffset,      // 0..299
    long pricePaisa,
    double intensity,       // signed log-imbalance
    long volume
) {}
```

**Output topic**: `GatewayTopic.HEATMAP_CHUNK(12)` — already declared

**Memory bound**: 5 min × 1 Hz × 20 price levels × 8 bytes/cell = 48 KB per symbol. With 50 active symbols = 2.4 MB max. Use `ConcurrentHashMap<InstrumentKey, SymbolHeatmapState>`.

**Tests** (per §10):
- Unit: 300 ticks → exactly 1 `HeatmapChunk` emitted with 300 cells
- Unit: chunk rotation at 5-min boundary
- Unit: memory cap when many symbols active
- Unit: per-symbol isolation (no cross-contamination)

---

## 5. Phase 3 — `OrderBookSnapshotProvider` (REST hydration + DhanMarketDepthProvider)

### 5.1 `OrderBookSnapshotProvider` (read-only SPI in `broker-api`)

**Purpose**: Expose the current `OrderBookSnapshot` so:
1. Frontend can hydrate on screen load (REST)
2. `DhanMarketDepthProvider` (currently empty stub) can be populated

**Interface** (in `broker-api/src/main/.../port/`):
```java
public interface OrderBookSnapshotProvider {
    /**
     * @return the current top-N levels of both sides, or empty when no updates seen yet
     */
    Optional<OrderBookSnapshot> snapshot(InstrumentKey key, int levels);

    /** @return all instruments with at least one depth update in the last 5 minutes */
    List<InstrumentKey> activeKeys();
}
```

`OrderBook` is in `broker-core` and `broker-api` should not depend on it. Solution: **move `OrderBook` and `OrderBookEngine` from `broker-core` to `core`**, or expose them via a new abstraction. The cleanest is to keep `OrderBook` where it is and let `OrderBookSnapshotProvider` live in `broker-api` and use the `core` types only. The provider's impl is in `broker-core`.

**Concrete impl**: `OrderBookEngineSnapshotProvider implements OrderBookSnapshotProvider` in `broker-core` — wraps `OrderBookEngine` and converts `OrderBook.toSnapshot(int)` to the wire-stable `OrderBookSnapshot` record.

### 5.2 Populate `DhanMarketDepthProvider`

Currently a stub. Implementation:
- Implement `MarketDepthProvider` (existing port in `broker-api/port/`)
- Inject `OrderBookEngine` (which receives `DepthUpdateEvent` from the WebSocket path)
- `getDepth(InstrumentKey)` returns a `MarketDepth` built from the engine's `OrderBook` for that symbol
- Wired via Spring: `DhanMarketDepthProvider` constructor takes `OrderBookEngine` and `DhanBrokerConnection`

### 5.3 Surface to broker-gateway

Add 2 methods to `BrokerHandle`:
```java
public GatewayResult<OrderBookSnapshot> orderBookSnapshot(String symbol, ExchangeSegment segment, int levels);
public GatewayResult<List<InstrumentKey>> activeOrderBookKeys();
```

Both route through `connection.getCapability(OrderBookSnapshotProvider.class)`. Add to `invoke()` dispatch:
- `orderBookSnapshot` → parse symbol+segment+levels, delegate
- `activeOrderBookKeys` → delegate

---

## 6. Phase 4 — WebSocket topic wiring

`GatewayTopic` enum already has all 6 new topics declared (11-16). The plan is to extend `GatewayEventBridge` to publish to them.

**Files to modify**:
- `gateway/src/main/.../bridge/GatewayEventBridge.java` — add 6 new cases to `onDomainEvent()` switch
- `gateway/src/main/.../bridge/Payloads.java` (if split out) or inline in bridge — 6 new payload serializers

**New payload types** (in `core/.../analytics/` or `gateway/.../payload/`):
- `DepthImbalancePayload { InstrumentKey key, double ratio, long topBidQty, long topAskQty, long ts }`
- `HeatmapChunkPayload { InstrumentKey key, long startMs, long endMs, List<HeatmapCellDto> cells, double minIntensity, double maxIntensity }`
- `IcebergAlertPayload { InstrumentKey key, String side, long pricePaisa, long hiddenQty, int refreshCount, long firstSeenMs }`
- `AbsorptionAlertPayload { InstrumentKey key, String absorbingSide, double score, long priceRangePaisa, long volumeAtLevel }`
- `SupportResistancePayload { InstrumentKey key, List<PriceLevelDto> support, List<PriceLevelDto> resistance, long computedAtMs }`
- `OrderBookSnapshotPayload { String symbol, String segment, List<DepthLevelDto> bids, List<DepthLevelDto> asks, long midPricePaisa, long spreadPaisa, long cumulativeBidVol, long cumulativeAskVol, double imbalance, long timestampMs }`

**Wire format**: JSON via Jackson, same as existing `depthPayload()` (line 257-265 in `GatewayEventBridge.java`).

**Subscription**: existing `MarketSubscriptionRequest` + per-topic client filter. Reuse current pattern — no protocol change.

**Tests** (per §10):
- Unit: each `Payloads.toJson(payload)` is parseable and round-trips
- Component: end-to-end Disruptor event → `GatewayEventBridge` → captured output JSON contains expected fields
- Live: connect WebSocket, subscribe to topic 11, observe imbalance updates from real Dhan depth stream

---

## 7. Phase 5 — REST hydration endpoints

**Location**: `app/src/main/java/com/tradej/app/api/`

**Endpoints** (all GET, all under `/api/dom/`):
```
GET /api/dom/snapshot/{symbol}?segment=NSE_EQ&levels=10
  → 200 OrderBookSnapshotPayload
  → 204 when no depth updates seen yet for this symbol

GET /api/dom/active-keys
  → 200 List<InstrumentKey>

GET /api/dom/sr-levels/{symbol}?segment=NSE_EQ
  → 200 SupportResistancePayload
  → 404 when no S/R data computed yet
```

**Implementation**:
- `DomController` annotated `@RestController @RequestMapping("/api/dom")`
- Injects `OrderBookSnapshotProvider` (for `/snapshot` + `/active-keys`)
- Injects `SupportResistanceAnalyzer` directly (for `/sr-levels`)

**Security**: per RP-098 (terminal scalability review), apply per-user session filter + WS topic filter BEFORE any second user. For now: all endpoints public with TODO + RP-098 cross-link.

**Tests** (per §10):
- Component: in-process test client hits endpoints, asserts 200 + correct shape
- Live: hit `/api/dom/snapshot/NIFTY?segment=IDX_I&levels=5` against real Dhan depth stream, assert non-empty bids+asks

---

## 8. Phase 6 — Frontend DOM screen + heatmap

**Location**: `frontend/src/ui/screens/DepthOfMarketScreen.tsx` + `frontend/src/ui/components/dom/`

**Components** (all new):
- `DepthOfMarketScreen.tsx` — top-level screen, subscribes to `MARKET_DEPTH` + new topics 11-16 via `gatewaySocket`
- `dom/OrderBookTable.tsx` — bids (left, red→green by size) + asks (right) + spread + cumulative imbalance bar
- `dom/HeatmapCanvas.tsx` — Canvas-based heatmap using `HEATMAP_CHUNK` topic, 5-min rolling window, color scale = `interpolateRdBu` from d3-scale-chromatic
- `dom/IcebergList.tsx` — list of `ICEBERG_ALERT` events, click → highlight on `OrderBookTable`
- `dom/AbsorptionList.tsx` — list of `ABSORPTION_ALERT` events
- `dom/SupportResistanceOverlay.tsx` — overlays S/R price levels on the `OrderBookTable`

**State** (extend `frontend/src/state/terminalStore.ts`):
- `depthBySymbol: Record<string, MarketDepth>` — already exists
- `orderBookSnapshot: Record<string, OrderBookSnapshotPayload>` — new
- `heatmapBySymbol: Record<string, HeatmapChunkPayload[]>` — new (rolling 5-min = ~6 chunks max)
- `icebergAlerts: IcebergAlertPayload[]` — new (capped at 50)
- `absorptionAlerts: AbsorptionAlertPayload[]` — new
- `srLevels: Record<string, SupportResistancePayload>` — new

**Hydration**: on screen mount, fetch `GET /api/dom/active-keys` then per-key `GET /api/dom/snapshot/{symbol}?levels=10` in parallel. After first WS update arrives, drop hydration state.

**Tests** (per §10):
- Component (Vitest): each component renders with mock data, asserts DOM structure
- Component: store updates trigger re-renders
- E2E (Playwright optional): open DOM screen, see bids/asks, see heatmap populate

**Accessibility**:
- All colors paired with text labels (color is not the only signal)
- Keyboard nav: arrow keys to move row focus, Enter to select
- Screen-reader-friendly: `aria-label` on each level cell describing price + qty + side

---

## 9. Architectural consistency checks (review before merge)

Enforced by `architecture-test` module + manual code review:

| Rule | Mechanism |
|---|---|
| No `BANKNIFTY` literal outside `IndexSymbols.ALIAS_TO_CANONICAL` | `ArchUnit` rule in `architecture-test` |
| No direct `DhanSegmentMapper.name()` for wire codes — must go through `DhanSegmentMapper.toWireValue()` | `ArchUnit` rule |
| No `OrderBook` (broker-core type) referenced from `core` or `app` | `ArchUnit` rule (or move to `core` — see §5.1) |
| All new broker-internal SPIs tagged `@BrokerInternal` | `ArchUnit` rule + grep in CI |
| All new `GatewayResult`-returning methods on `BrokerHandle` follow pattern `(args) → GatewayResult<T>` | review checklist |
| All new WebSocket payloads have a wire-version constant in `GatewayTopic` | review checklist |
| All new frontend state lives in `terminalStore.ts` (no per-component fetch) | review checklist |

---

## 10. Test plan (per test pyramid level)

### 10.1 Unit (`./gradlew unitTest`)

| Test | File | Count |
|---|---|---|
| OrderBook basic operations | `broker/core/.../OrderBookTest.java` | 8 (currently MISSING) |
| OrderBook thread safety | same | 3 |
| OrderBookEngine | already exists | 5 (currently failing — see §2) |
| SupportResistanceAnalyzer | `trading/analytics/.../SupportResistanceAnalyzerTest.java` | 12 (3 per algorithm + 3 edge + 3 tunables + 3 wire) |
| IcebergDetector | `.../IcebergDetectorTest.java` | 10 |
| AbsorptionDetector | `.../AbsorptionDetectorTest.java` | 8 |
| DepthImbalanceAnalyzer | `.../DepthImbalanceAnalyzerTest.java` | 6 |
| HeatmapRecorder | `runtime/hotpath/.../HeatmapRecorderTest.java` | 6 |
| OrderBookSnapshotProvider | `broker/core/.../OrderBookSnapshotProviderTest.java` | 4 |
| DomController (REST) | `app/.../api/DomControllerTest.java` | 4 |
| Payload JSON round-trip | `gateway/.../bridge/PayloadsTest.java` | 6 (one per new topic) |
| IndexSymbols/DhanSegmentMapper additions (if any) | existing | — |

**Total new unit tests**: ~72
**Total post-phase unit tests in `trading/analytics`**: 36
**Total post-phase unit tests in `runtime/hotpath`**: 6
**Total post-phase unit tests in `broker-core`**: 16 (8 + 3 + 5)
**Total post-phase unit tests in `app`**: 4
**Total post-phase unit tests in `gateway`**: 6

### 10.2 Contract (`./gradlew contractTest`)

| Test | File |
|---|---|
| All 4 analyzers implement `DepthAnalyzer` correctly | `DepthAnalyzerContractTest.java` |
| `OrderBookSnapshotProvider` returns same shape across 3 brokers (Dhan/Upstox/Icici) | `OrderBookSnapshotProviderContractTest.java` |
| All 6 new payloads are wire-compatible with `GatewayTopic.fromWireId()` | `GatewayTopicWireContractTest.java` |

### 10.3 Component (`./gradlew componentTest`)

| Test | File |
|---|---|
| `DepthUpdateEvent` → 4 analyzers + HeatmapRecorder all run, expected output | `DepthAnalyticsPipelineComponentTest.java` |
| `GatewayEventBridge` produces expected JSON for each of 6 new topics | `GatewayEventBridgeComponentTest.java` |
| `DomController` endpoints return correct shape with mock providers | `DomControllerComponentTest.java` |

### 10.4 Live (`./gradlew brokerRestTest` / `./gradlew brokerWsTest`)

| Test | File | Evidence |
|---|---|---|
| Real Dhan depth WS → S/R + Iceberg + Absorption + Heatmap emit at least 1 alert in 5 min | `DomAnalyticsLiveTest.java` | tags: integration + broker-ws |
| `/api/dom/snapshot/NIFTY?segment=IDX_I&levels=5` returns populated snapshot during market hours | `DomRestLiveTest.java` | tags: integration |
| Frontend DOM screen receives WS updates | manual smoke (not automated) | — |

### 10.5 Architecture test (`./gradlew architectureTest`)

| Rule | File |
|---|---|
| No broker-alias literals outside `IndexSymbols` | `ArchitectureConsistencyTest.java` |
| All Dhan wire codes via `toWireValue()` | same |
| `OrderBook` not referenced from `core` or `app` | same |
| All new SPI methods tagged `@BrokerInternal` | same |

---

## 11. Backlog + regression manifest updates

**Add to `docs/BACKLOG.md`**:
- RP-101: Fix `OrderBookEngine` pre-req (§2)
- RP-102: 3 new analyzers + imbalance promotion (§3)
- RP-103: `HeatmapRecorder` (§4)
- RP-104: `OrderBookSnapshotProvider` SPI + populate `DhanMarketDepthProvider` (§5)
- RP-105: WebSocket topic 11-16 wiring (§6)
- RP-106: REST hydration endpoints (§7)
- RP-107: Frontend DOM screen + heatmap (§8)

**Add to `REGRESSION_MANIFEST.md`**:
- INV-47: `OrderBookEngine` fix (§2)
- INV-48: 4 analyzers — output types stable, tunables honored (§3)
- INV-49: `HeatmapRecorder` — 5-min rotation, memory cap (§4)
- INV-50: `OrderBookSnapshotProvider` SPI + `DhanMarketDepthProvider` populated (§5)
- INV-51: WebSocket topics 11-16 wired and live-tested (§6)
- INV-52: DOM REST endpoints (§7)
- INV-53: Frontend DOM screen renders + hydrates (§8)

---

## 12. Execution order & binary gates

**Order** (each phase must pass its binary gate before next starts):
1. **§2** OrderBookEngine fix → gate: `OrderBookEngineTest` 5/5 + new `OrderBookTest` 8/8 + all `:broker-*` tests compile
2. **§3** Analyzers → gate: 36 new unit tests + 4 `DepthAnalyzerContractTest` + 1 `DepthAnalyticsPipelineComponentTest`
3. **§4** HeatmapRecorder → gate: 6 unit tests + integrated into §3 component test
4. **§5** SnapshotProvider + DhanMarketDepthProvider → gate: 4 unit + 1 contract + 2 `BrokerHandle.invoke` tests
5. **§6** WebSocket topic wiring → gate: 6 payload unit + 1 component + 1 live
6. **§7** REST endpoints → gate: 4 controller unit + 1 component + 1 live
7. **§8** Frontend DOM screen → gate: 4 Vitest component + manual smoke

**Cross-phase dependencies**:
- §3-§6 all depend on §2 (the engine must work)
- §5-§7 depend on §4 (analyzer outputs)
- §6-§7 depend on §5 (REST surfaces the provider)
- §8 depends on §5-§7 (consumes REST + WS)

**Parallelism opportunities**:
- §3.1, §3.2, §3.3, §3.4 can be implemented in parallel (different files)
- §6 payload serializers can be written in parallel with §3-§5 if types are agreed upfront
- §7 can be implemented in parallel with §8 once the REST contract is fixed

**Estimated effort**:
- §2: 0.25 day
- §3: 3 days
- §4: 1 day
- §5: 1.5 days
- §6: 1 day
- §7: 0.5 day
- §8: 2.5 days
- Architecture review + INV/RP updates: 0.5 day
- **Total**: ~10 working days

---

## 13. Risks & mitigations

| Risk | Severity | Mitigation |
|---|---|---|
| `OrderBookEngine` design is wrong (new `apply` method semantically differs from `update`) | M | Add `OrderBookTest` unit tests covering both methods before any consumer uses them |
| HeatmapRecorder memory growth under load | M | Hard cap at 50 active symbols + per-symbol 5-min rolling window + 60s eviction after last update |
| IcebergDetector false-positive spam | M | 5-min per-(symbol,side,price) cooldown; threshold tuning parameter |
| AbsorptionDetector false-positive during volatility | M | Require both `volumeAtLevel > 50000` AND `priceRange < 2 ticks` to fire |
| Real-Dhan heatmap chunk rates differ from 1 Hz assumption | L | Log actual rate during live test; tune bucket size if off by >2x |
| Frontend canvas re-render cost on rapid updates | M | Throttle to 10 fps; use `requestAnimationFrame`; only redraw deltas |
| WebSocket topic 11-16 not in `MarketSubscriptionRequest` whitelist | M | Add all 6 to whitelist as part of §6 |

---

## 14. Files created / modified

**New files** (~20):
- `core/src/main/java/com/tradej/core/analytics/depth/SupportResistanceLevels.java`
- `core/src/main/java/com/tradej/core/analytics/depth/IcebergAlert.java`
- `core/src/main/java/com/tradej/core/analytics/depth/AbsorptionAlert.java`
- `core/src/main/java/com/tradej/core/analytics/depth/ImbalanceSnapshot.java`
- `core/src/main/java/com/tradej/core/analytics/depth/HeatmapChunk.java`
- `core/src/main/java/com/tradej/core/analytics/depth/DepthAnalyzer.java`
- `trading/analytics/src/main/java/com/tradej/trading/analytics/depth/SupportResistanceAnalyzer.java`
- `trading/analytics/src/main/java/com/tradej/trading/analytics/depth/IcebergDetector.java`
- `trading/analytics/src/main/java/com/tradej/trading/analytics/depth/AbsorptionDetector.java`
- `trading/analytics/src/main/java/com/tradej/trading/analytics/depth/DepthImbalanceAnalyzer.java`
- `runtime/hotpath/src/main/java/com/tradej/hotpath/heatmap/HeatmapRecorder.java`
- `broker-api/src/main/java/com/tradej/broker/api/port/OrderBookSnapshotProvider.java`
- `broker/core/src/main/java/com/tradej/broker/core/depth/OrderBookSnapshotProviderImpl.java`
- `app/src/main/java/com/tradej/app/api/DomController.java`
- `frontend/src/ui/screens/DepthOfMarketScreen.tsx`
- `frontend/src/ui/components/dom/{OrderBookTable,HeatmapCanvas,IcebergList,AbsorptionList,SupportResistanceOverlay}.tsx`
- Plus ~20 test files

**Modified files** (~10):
- `broker/core/src/main/java/com/tradej/broker/core/depth/OrderBook.java` (add `apply` + new fields — Option A)
- `broker/dhan/src/main/java/com/tradej/broker/dhan/adapter/DhanMarketDepthProvider.java` (populate from `OrderBookEngine`)
- `gateway/src/main/java/com/tradej/gateway/bridge/GatewayEventBridge.java` (6 new topic publishers)
- `broker-gateway/src/main/java/com/tradej/brokergateway/BrokerHandle.java` (2 new methods + 2 invoke cases)
- `frontend/src/state/terminalStore.ts` (5 new state slices)
- `app/src/main/resources/application.yml` (analytics tunables)
- `docs/BACKLOG.md` (7 RP entries)
- `REGRESSION_MANIFEST.md` (7 INV entries)

---

## 15. Done definition

- All 72+ new unit tests pass
- All 3 new contract tests pass
- All 3 new component tests pass
- All 2 new live tests pass
- Architecture test rules pass
- 6 new WebSocket topics observable in browser dev tools (ws://localhost:8080/ws/gateway + filter by wireId 11-16)
- Manual smoke: open DOM screen, see bids/asks populate within 5s of subscribe, see heatmap chunks streaming, see at least 1 alert fire during a 5-min observation window
- INV-47..53 all in `REGRESSION_MANIFEST.md` and `gradle fullRegressionTest` includes them
- RP-101..107 all marked **Fixed** in `docs/BACKLOG.md`
- Architecture review (this file's §9) passes

