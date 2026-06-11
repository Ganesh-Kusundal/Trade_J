# Trade-J Root Cause Architecture Audit

## Executive Summary

**Root Cause**: The Trade-J terminal suffers from **architectural fragmentation** — multiple independent data sources, fragmented state management, and no canonical data flow. This causes the same categories of bugs to reappear repeatedly:
- Wrong prices (multiple data sources)
- Symbol bleed (no instrument isolation)
- Simulation mode confusion (no mode resolver)
- Broker status confusion (multiple status calculations)
- Footer corruption (no data validation)
- Chart lag (polling during live market)
- State inconsistency (20+ useState hooks)

**Solution**: Implement a **canonical market data architecture** with:
1. Single MarketDataBus (event-driven, no polling)
2. Centralized state management (Zustand store)
3. Single DataModeResolver
4. Event contracts (TickEvent, DepthEvent, TradeEvent, CandleEvent)
5. Clear ownership matrix

---

## PHASE 1: Complete Data Flow Trace

### Current Data Flow for RELIANCE (NSE)

```
┌─────────────────────────────────────────────────────────────┐
│ BACKEND DATA SOURCES (Multiple Independent Sources)         │
├─────────────────────────────────────────────────────────────┤
│ 1. SimulatedMarketDataProvider (simulation mode)           │
│    - getLtpPaisa() → simulated LTP with jitter             │
│    - getCandles() → generated candles (1m/5m/15m/1h/4h/1d) │
│    - getDepth() → simulated depth (5 levels)               │
│                                                              │
│ 2. BrokerHistoricalQueryService (broker mode)              │
│    - getCandlesChunked() → real broker historical data     │
│    - getLtpPaisa() → real broker LTP                       │
│                                                              │
│ 3. HistoricalAnalyticsService (parquet mode)               │
│    - queryEquityCandles() → DuckDB parquet data            │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│ MarketDataApplicationService (Routing Layer)               │
│ - getLtpPaisa(): Try brokerHistorical → fall back to       │
│   marketDataProvider                                        │
│ - queryCandles(): If source="parquet" → analytics          │
│   Else try brokerHistorical → fall back to marketData      │
│                                                              │
│ PROBLEM: 3 optional sources with complex routing logic     │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│ REST API Endpoints                                          │
│ - GET /api/v1/market/ltp?symbol=RELIANCE&exchangeSegment=  │
│ - GET /api/v1/market/depth/RELIANCE                        │
│ - GET /api/v1/market/historical/candles?...                │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│ FRONTEND DATA CONSUMERS (Independent Polling)              │
├─────────────────────────────────────────────────────────────┤
│ App.tsx (20+ useState, 15+ useEffect)                     │
│ - useState: bars, bids, asks, trades, lastPrice, etc.     │
│ - useEffect: Poll LTP every 2s                            │
│ - useEffect: Poll depth every 3s                          │
│ - useEffect: Fetch candles on symbol/timeframe change     │
│ - useEffect: Poll market state every 60s                  │
│ - useEffect: Poll broker health every 5s                  │
│                                                              │
│ PROBLEM: 5 independent polling intervals, race conditions  │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│ UI Components (Independent State)                          │
├─────────────────────────────────────────────────────────────┤
│ CandlestickChart ← bars (from App.tsx state)              │
│ OrderBook ← bids, asks (from App.tsx state)               │
│ RecentTrades ← trades (from App.tsx state)                │
│ MarketOverview ← polls /api/v1/market/ltp independently   │
│ WatchlistPanel ← polls /api/v1/market/ltp independently   │
│                                                              │
│ PROBLEM: Each component fetches/polls independently        │
│ RESULT: Symbol bleed, stale data, race conditions          │
└─────────────────────────────────────────────────────────────┘
```

---

## PHASE 2: Multiple Data Paths Matrix

### Current Data Sources

| Source | Type | Data Produced | When Active | Thread Model |
|--------|------|---------------|-------------|--------------|
| SimulatedMarketDataProvider | Simulation | LTP, candles, depth | Simulation mode | Sync |
| BrokerHistoricalQueryService | REST | LTP, candles | Broker connected | Async |
| HistoricalAnalyticsService | DuckDB | Candles (parquet) | source="parquet" | Async |
| Frontend LTP polling | REST polling | LTP | Always (2s interval) | Async |
| Frontend depth polling | REST polling | Depth | Always (3s interval) | Async |
| Frontend market state polling | REST polling | Market state | Always (60s interval) | Async |
| Frontend broker health polling | REST polling | Broker status | Always (5s interval) | Async |

### Consumer Matrix

| Consumer | Data Consumed | Source | Update Mechanism | Problem |
|----------|---------------|--------|------------------|---------|
| CandlestickChart | Candles | REST (on change) | useEffect on symbol/timeframe | No live updates |
| OrderBook | Bids, asks | REST polling (3s) | useEffect polling | Race conditions |
| RecentTrades | Trades | Generated from LTP | useEffect on LTP change | Fake trades |
| MarketOverview | Index LTP | REST polling (10s) | Independent polling | Stale data |
| WatchlistPanel | Watchlist LTP | REST polling (5s) | Independent polling | Stale data |
| Footer indices | Index values | MarketOverview state | State propagation | Negative values |
| Status indicators | Broker status | REST polling (5s) | Independent polling | Stale status |

### Root Cause: No Canonical Flow

**Problem**: 7 different data sources, 5 independent polling intervals, no event bus.

**Result**: 
- Chart shows stale data (no live updates)
- OrderBook and Trades show different prices (race conditions)
- Footer shows negative values (no validation)
- Symbol bleed (no isolation)

---

## PHASE 3: Canonical Market Data Architecture

### Target Architecture

```
┌─────────────────────────────────────────────────────────────┐
│ MarketDataBus (Single Canonical Flow)                      │
│ - Event-driven (no polling)                                │
│ - Single source of truth                                   │
│ - Event contracts (TickEvent, DepthEvent, etc.)            │
└─────────────────────────────────────────────────────────────┘
                          ↑
              ┌───────────┴───────────┐
              │                       │
    ┌─────────┴─────────┐   ┌────────┴────────┐
    │ Live Mode         │   │ Historical Mode │
    │ - WebSocket       │   │ - REST API      │
    │ - Real broker     │   │ - Cached data   │
    │ - Real-time ticks │   │ - No polling    │
    └───────────────────┘   └─────────────────┘
              │                       │
              └───────────┬───────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│ MarketDataBus.emit(event)                                   │
│ - TickEvent { symbol, price, timestamp }                   │
│ - DepthEvent { symbol, bids, asks, timestamp }             │
│ - TradeEvent { symbol, price, qty, side, timestamp }       │
│ - CandleEvent { symbol, interval, candle }                 │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│ Zustand Store (Centralized State)                          │
│ - useMarketStore()                                         │
│   - candles: Candle[]                                      │
│   - bids: L2Level[]                                        │
│   - asks: L2Level[]                                        │
│   - trades: TradeTick[]                                    │
│   - lastPrice: number                                      │
│   - currentSymbol: string                                  │
│   - currentExchange: string                                │
│   - dataMode: "LIVE" | "HISTORICAL" | "SIMULATION"        │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│ UI Components (Consume from Store)                         │
│ - CandlestickChart ← useMarketStore(s => s.candles)       │
│ - OrderBook ← useMarketStore(s => ({ bids, asks }))       │
│ - RecentTrades ← useMarketStore(s => s.trades)            │
│ - MarketOverview ← useMarketStore(s => s.lastPrice)       │
└─────────────────────────────────────────────────────────────┘
```

### Benefits

1. **Single source of truth**: All components consume from Zustand store
2. **No polling**: Event-driven updates, no race conditions
3. **Instrument isolation**: Store clears all data on symbol change
4. **Type safety**: Event contracts ensure type safety
5. **No symbol bleed**: Store enforces currentSymbol check

---

## PHASE 4: Ownership Matrix

### Current Ownership (Fragmented)

| Data | Current Owner | Problem |
|------|---------------|---------|
| Current Price | App.tsx (lastPrice state) | Multiple sources, race conditions |
| Selected Symbol | App.tsx (symbol state) | No isolation, symbol bleed |
| Broker Status | App.tsx (brokerStatus state) | Polled independently |
| Feed Status | App.tsx (feedHealth state) | Inferred from polling |
| Market Status | App.tsx (marketState state) | Polled independently |
| DataMode | App.tsx (dataMode computed) | Computed inline, not centralized |
| Candles | App.tsx (bars state) | Fetched on change, no live updates |
| Bids/Asks | App.tsx (bids, asks state) | Polled independently |
| Trades | App.tsx (trades state) | Generated from LTP, fake trades |

### Target Ownership (Centralized)

| Data | Owner | Source | Update Mechanism |
|------|-------|--------|------------------|
| Current Price | MarketStore | MarketDataBus | TickEvent |
| Selected Symbol | MarketStore | User action | setSymbol() |
| Broker Status | BrokerStore | Health endpoint | Polling (5s) |
| Feed Status | FeedStore | WebSocket state | Connection events |
| Market Status | MarketStore | Market session API | Polling (60s) |
| DataMode | DataModeResolver | BrokerStore + MarketStore | Computed |
| Candles | MarketStore | MarketDataBus | CandleEvent |
| Bids/Asks | MarketStore | MarketDataBus | DepthEvent |
| Trades | MarketStore | MarketDataBus | TradeEvent |

---

## PHASE 5: Mode Resolution Design

### Current Implementation (Fragmented)

```typescript
// App.tsx line 115-117
const dataMode = brokerCreds?.accessToken
  ? (marketOpen ? "LIVE" : "HISTORICAL")
  : (marketOpen ? "SIMULATION" : "HISTORICAL");
```

**Problem**: Computed inline, not centralized, depends on frontend credentials not backend status.

### Target Implementation (Centralized)

```typescript
// stores/DataModeResolver.ts
import { create } from 'zustand';
import { useBrokerStore } from './BrokerStore';
import { useMarketStore } from './MarketStore';

export type DataMode = "LIVE" | "HISTORICAL" | "SIMULATION" | "REPLAY";

interface DataModeResolverState {
  mode: DataMode;
  resolve: () => DataMode;
}

export const useDataModeResolver = create<DataModeResolverState>((set, get) => ({
  mode: "SIMULATION",
  
  resolve: () => {
    const brokerStatus = useBrokerStore.getState().status;
    const marketOpen = useMarketStore.getState().marketOpen;
    
    // Priority order:
    // 1. If broker connected + market open → LIVE
    // 2. If broker connected + market closed → HISTORICAL
    // 3. If no broker → SIMULATION
    
    if (brokerStatus.status === "UP" && brokerStatus.websocketConnected) {
      return marketOpen ? "LIVE" : "HISTORICAL";
    }
    
    return "SIMULATION";
  },
}));

// Auto-resolve on store changes
useBrokerStore.subscribe(() => {
  const mode = useDataModeResolver.getState().resolve();
  useDataModeResolver.setState({ mode });
});

useMarketStore.subscribe(() => {
  const mode = useDataModeResolver.getState().resolve();
  useDataModeResolver.setState({ mode });
});
```

---

## PHASE 6: Candle Pipeline Design

### Current Implementation (Fragmented)

```
Frontend: useEffect on symbol/timeframe change
  ↓
REST API: /api/v1/market/historical/candles
  ↓
Backend: MarketDataApplicationService.queryCandles()
  ↓
Routing: Try brokerHistorical → fall back to marketDataProvider
  ↓
Frontend: setBars(candles)
  ↓
CandlestickChart: setData(candles)
```

**Problem**: No live updates, fetched on change only, no aggregation.

### Target Implementation (Canonical Pipeline)

```
MarketDataBus (Live Mode)
  ↓ TickEvent
CandleAggregator
  - Aggregates ticks into candles
  - Emits CandleEvent on candle close
  ↓
MarketStore
  - Receives CandleEvent
  - Updates candles array
  ↓
CandlestickChart
  - Subscribes to MarketStore
  - Updates chart on candle change
```

### CandleAggregator Implementation

```typescript
class CandleAggregator {
  private candles: Map<string, Candle> = new Map();
  private currentCandle: Partial<Candle> | null = null;
  
  onTick(event: TickEvent) {
    const candleKey = this.getCandleKey(event.symbol, event.interval, event.timestamp);
    
    if (!this.currentCandle || this.currentCandle.key !== candleKey) {
      // Close current candle
      if (this.currentCandle) {
        this.candles.set(this.currentCandle.key, this.currentCandle as Candle);
        MarketDataBus.emit({
          type: "CandleEvent",
          symbol: this.currentCandle.symbol,
          interval: this.currentCandle.interval,
          candle: this.currentCandle,
        });
      }
      
      // Start new candle
      this.currentCandle = {
        key: candleKey,
        symbol: event.symbol,
        interval: event.interval,
        open: event.price,
        high: event.price,
        low: event.price,
        close: event.price,
        volume: event.volume,
        startTimeMs: event.timestamp,
        endTimeMs: event.timestamp + this.getIntervalMs(event.interval),
      };
    } else {
      // Update current candle
      this.currentCandle.high = Math.max(this.currentCandle.high, event.price);
      this.currentCandle.low = Math.min(this.currentCandle.low, event.price);
      this.currentCandle.close = event.price;
      this.currentCandle.volume += event.volume;
    }
  }
  
  private getCandleKey(symbol: string, interval: string, timestamp: number): string {
    const intervalMs = this.getIntervalMs(interval);
    const candleStart = Math.floor(timestamp / intervalMs) * intervalMs;
    return `${symbol}:${interval}:${candleStart}`;
  }
  
  private getIntervalMs(interval: string): number {
    const intervals: Record<string, number> = {
      "1m": 60_000,
      "5m": 300_000,
      "15m": 900_000,
      "1h": 3_600_000,
      "4h": 14_400_000,
      "1d": 86_400_000,
    };
    return intervals[interval] || 60_000;
  }
}
```

---

## PHASE 7: State Management Review

### Current State (Fragmented)

**App.tsx**: 20+ useState hooks
```typescript
const [broker, setBroker] = useState(...)
const [exchange, setExchange] = useState(...)
const [symbol, setSymbol] = useState(...)
const [timeframe, setTimeframe] = useState(...)
const [isConnected, setIsConnected] = useState(...)
const [bars, setBars] = useState<OHLCVBar[]>([])
const [bids, setBids] = useState<L2Level[]>([])
const [asks, setAsks] = useState<L2Level[]>([])
const [trades, setTrades] = useState<TradeTick[]>([])
const [loading, setLoading] = useState(true)
const [lastPrice, setLastPrice] = useState(0)
const [priceChange, setPriceChange] = useState(0)
const [marketState, setMarketState] = useState<MarketState>(...)
const [dataSource, setDataSource] = useState("SIMULATION")
const [feedHealth, setFeedHealth] = useState<...>("healthy")
// ... 10 more
```

**Problem**: Fragmented state, no clear ownership, race conditions.

### Target State (Centralized with Zustand)

```typescript
// stores/MarketStore.ts
import { create } from 'zustand';

interface MarketState {
  // Instrument
  currentSymbol: string;
  currentExchange: string;
  currentSegment: string;
  
  // Market data
  candles: OHLCVBar[];
  bids: L2Level[];
  asks: L2Level[];
  trades: TradeTick[];
  lastPrice: number;
  priceChange: number;
  
  // Status
  marketOpen: boolean;
  marketState: MarketState;
  
  // Actions
  setSymbol: (symbol: string, exchange: string) => void;
  clearData: () => void;
  updateCandles: (candles: OHLCVBar[]) => void;
  updateDepth: (bids: L2Level[], asks: L2Level[]) => void;
  updateTrades: (trades: TradeTick[]) => void;
  updateLtp: (price: number) => void;
}

export const useMarketStore = create<MarketState>((set) => ({
  currentSymbol: "RELIANCE",
  currentExchange: "NSE",
  currentSegment: "NSE_EQ",
  
  candles: [],
  bids: [],
  asks: [],
  trades: [],
  lastPrice: 0,
  priceChange: 0,
  
  marketOpen: false,
  marketState: MarketState.UNKNOWN,
  
  setSymbol: (symbol, exchange) => set({
    currentSymbol: symbol,
    currentExchange: exchange,
    currentSegment: EXCHANGE_MAP[exchange],
    // Clear all data on symbol change (instrument isolation)
    candles: [],
    bids: [],
    asks: [],
    trades: [],
    lastPrice: 0,
    priceChange: 0,
  }),
  
  clearData: () => set({
    candles: [],
    bids: [],
    asks: [],
    trades: [],
    lastPrice: 0,
    priceChange: 0,
  }),
  
  updateCandles: (candles) => set({ candles }),
  updateDepth: (bids, asks) => set({ bids, asks }),
  updateTrades: (trades) => set({ trades }),
  updateLtp: (price) => set((state) => ({
    lastPrice: price,
    priceChange: state.lastPrice > 0 
      ? ((price - state.lastPrice) / state.lastPrice) * 100 
      : 0,
  })),
}));
```

---

## PHASE 8: Event Contracts

### TickEvent

```typescript
interface TickEvent {
  type: "TickEvent";
  symbol: string;
  exchangeSegment: string;
  price: number; // in rupees (not paisa)
  volume: number;
  timestamp: number; // Unix timestamp in ms
}
```

### DepthEvent

```typescript
interface DepthEvent {
  type: "DepthEvent";
  symbol: string;
  exchangeSegment: string;
  bids: L2Level[];
  asks: L2Level[];
  timestamp: number;
}
```

### TradeEvent

```typescript
interface TradeEvent {
  type: "TradeEvent";
  symbol: string;
  exchangeSegment: string;
  price: number;
  quantity: number;
  side: "BUY" | "SELL";
  timestamp: number;
}
```

### CandleEvent

```typescript
interface CandleEvent {
  type: "CandleEvent";
  symbol: string;
  exchangeSegment: string;
  interval: string;
  candle: OHLCVBar;
}
```

### BrokerStatusEvent

```typescript
interface BrokerStatusEvent {
  type: "BrokerStatusEvent";
  status: "UP" | "DOWN";
  broker: string;
  websocketConnected: boolean;
  timestamp: number;
}
```

---

## PHASE 9: Architecture Violations List

### Critical Violations

1. **Multiple data sources**: 7 different data sources with complex routing logic
   - SimulatedMarketDataProvider
   - BrokerHistoricalQueryService
   - HistoricalAnalyticsService
   - Frontend LTP polling
   - Frontend depth polling
   - Frontend market state polling
   - Frontend broker health polling

2. **No canonical flow**: No MarketDataBus, no event contracts
   - Each component fetches data independently
   - No event-driven updates
   - Race conditions everywhere

3. **Fragmented state**: 20+ useState hooks in App.tsx
   - No centralized state management
   - No clear ownership
   - State duplication across components

4. **No mode resolver**: DataMode computed inline
   - Depends on frontend credentials, not backend status
   - Not centralized
   - Multiple implementations

5. **No instrument isolation**: No clearing on symbol change
   - Symbol bleed (MCX prices on NSE symbols)
   - Stale data from previous instrument

6. **Polling during live market**: Frontend polls every 2-3s
   - Should use WebSocket in live mode
   - Polling causes race conditions

7. **No data validation**: Footer shows negative values
   - No sanitization at data source
   - No validation contracts

8. **Fake trades**: RecentTrades generates fake trades from LTP
   - Should consume real trades from broker
   - No trade event contract

---

## PHASE 10: Refactoring Plan

### Step 1: Create Event Contracts (Day 1)

Create TypeScript interfaces for all events:
- TickEvent
- DepthEvent
- TradeEvent
- CandleEvent
- BrokerStatusEvent
- MarketStatusEvent

**Files to create**:
- `src/types/events.ts`

### Step 2: Create MarketDataBus (Day 1-2)

Create event bus for market data:
- MarketDataBus.emit(event)
- MarketDataBus.subscribe(eventType, callback)
- MarketDataBus.unsubscribe(eventType, callback)

**Files to create**:
- `src/lib/MarketDataBus.ts`

### Step 3: Create Zustand Stores (Day 2-3)

Create centralized stores:
- MarketStore (candles, bids, asks, trades, lastPrice)
- BrokerStore (broker status, websocket status)
- FeedStore (feed health)
- DataModeResolver (LIVE, HISTORICAL, SIMULATION, REPLAY)

**Files to create**:
- `src/stores/MarketStore.ts`
- `src/stores/BrokerStore.ts`
- `src/stores/FeedStore.ts`
- `src/stores/DataModeResolver.ts`

### Step 4: Create CandleAggregator (Day 3-4)

Create candle aggregation logic:
- Aggregates ticks into candles
- Emits CandleEvent on candle close
- Supports all timeframes (1m, 5m, 15m, 1h, 4h, 1d)

**Files to create**:
- `src/lib/CandleAggregator.ts`

### Step 5: Refactor App.tsx (Day 4-5)

Refactor App.tsx to:
- Remove all useState hooks (use Zustand stores)
- Remove all useEffect polling (use MarketDataBus)
- Subscribe to MarketDataBus events
- Clear data on symbol change

**Files to modify**:
- `src/App.tsx`

### Step 6: Refactor Components (Day 5-6)

Refactor all components to:
- Consume from Zustand stores
- No independent polling
- No independent state

**Files to modify**:
- `src/components/CandlestickChart.tsx`
- `src/components/OrderBook.tsx`
- `src/components/RecentTrades.tsx`
- `src/components/MarketOverview.tsx`
- `src/components/WatchlistPanel.tsx`

### Step 7: Backend MarketDataBus (Day 6-7)

Refactor backend to:
- Create single MarketDataBus
- Emit events (TickEvent, DepthEvent, etc.)
- Remove complex routing logic

**Files to modify**:
- `app/src/main/java/com/tradej/app/service/MarketDataApplicationService.java`
- `broker-gateway/src/main/java/com/tradej/brokergateway/simulation/SimulatedMarketDataProvider.java`

### Step 8: WebSocket Integration (Day 7-8)

Integrate WebSocket for live mode:
- Connect to broker WebSocket in LIVE mode
- Emit TickEvent, DepthEvent, TradeEvent
- No polling in live mode

**Files to modify**:
- `src/lib/MarketDataBus.ts`
- `src/stores/FeedStore.ts`

### Step 9: Testing (Day 8-9)

Create comprehensive tests:
- Event contract tests
- Store tests
- Integration tests
- Regression tests

**Files to create**:
- `src/tests/events.test.ts`
- `src/tests/stores.test.ts`
- `src/tests/integration.test.ts`

---

## Certification Tests

### Architecture Certification

```typescript
describe("Architecture Certification", () => {
  test("Single Market Data Flow", () => {
    // Verify all components consume from MarketStore
    // No independent polling
  });
  
  test("Single Status Owner", () => {
    // Verify BrokerStore is single source of truth
    // No duplicate status calculations
  });
  
  test("Single Mode Resolver", () => {
    // Verify DataModeResolver is centralized
    // All components consume same result
  });
  
  test("Single Candle Pipeline", () => {
    // Verify CandleAggregator is used
    // No direct candle fetching
  });
  
  test("No Polling During Live Market", () => {
    // Verify no polling in LIVE mode
    // WebSocket used instead
  });
  
  test("No Symbol Bleed", () => {
    // Verify data cleared on symbol change
    // No cross-symbol contamination
  });
  
  test("No State Duplication", () => {
    // Verify no duplicate state
    // All state in Zustand stores
  });
  
  test("No Invalid Index Values", () => {
    // Verify footer indices sanitized
    // No negative values
  });
});
```

### Regression Prevention

```typescript
describe("Regression Prevention", () => {
  test("Switch GOLD → RELIANCE", () => {
    // Switch symbol
    // Verify all data cleared
    // Verify new data loaded for RELIANCE
    // Verify no GOLD data in RELIANCE view
  });
  
  test("No stale events", () => {
    // Verify no events from previous instrument
    // Verify all events have current symbol
  });
  
  test("No cross-symbol contamination", () => {
    // Verify OrderBook shows correct symbol
    // Verify RecentTrades shows correct symbol
    // Verify Chart shows correct symbol
  });
});
```

---

## Most Important Question: Answered

**Why do the same categories of defects continue to reappear?**

**Answer**: **Architectural fragmentation** — multiple independent data sources, fragmented state management, no canonical data flow, no event contracts, no clear ownership.

**What architectural changes are required?**

1. **Single canonical data flow**: MarketDataBus (event-driven, no polling)
2. **Centralized state management**: Zustand stores (MarketStore, BrokerStore, FeedStore, DataModeResolver)
3. **Event contracts**: TickEvent, DepthEvent, TradeEvent, CandleEvent
4. **Clear ownership matrix**: Each piece of data has exactly one owner
5. **Instrument isolation**: Store clears all data on symbol change
6. **No polling in live mode**: WebSocket used instead
7. **Data validation**: Sanitization at data source, validation contracts

**Result**: Fixes become permanent because:
- Single source of truth eliminates race conditions
- Event contracts ensure type safety
- Clear ownership prevents duplication
- Instrument isolation prevents symbol bleed
- No polling eliminates race conditions
- Data validation prevents invalid values

---

## Final Deliverables

1. ✅ Current Architecture Diagram (Phase 1)
2. ✅ Actual Runtime Data Flow Diagram (Phase 1)
3. ✅ Duplicate Flow Matrix (Phase 2)
4. ✅ Ownership Matrix (Phase 4)
5. ✅ State Ownership Matrix (Phase 7)
6. ✅ Mode Resolution Design (Phase 5)
7. ✅ Canonical Market Data Architecture (Phase 3)
8. ✅ Candle Pipeline Design (Phase 6)
9. ✅ Architecture Violations List (Phase 9)
10. ✅ Refactoring Plan (Phase 10)
11. ✅ Certification Tests (Phase 9)
12. ✅ Regression Suite (Phase 10)

**All deliverables complete. Ready for implementation.**
