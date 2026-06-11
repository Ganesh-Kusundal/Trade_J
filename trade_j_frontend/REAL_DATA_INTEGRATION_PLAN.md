# 🔍 Trade-J Frontend - Real Data Integration Analysis

## 📊 Current State Analysis

### **trade_j_frontend** (THIS PROJECT)
**Location**: `/Users/apple/Downloads/Trade_J/trade_j_frontend/`  
**Purpose**: Professional trading terminal with real-time charts, order book, trades  
**Current Data Sources**:
- ✅ **Binance (Crypto)**: REAL WebSocket + REST API
- ❌ **Indian Markets (NSE/NFO/MCX)**: SIMULATED (random walk)

---

## 🎯 What trade_j_frontend Currently Does

### **For Binance (Crypto) - REAL DATA ✅**:

```typescript
// 1. Historical Candles - REAL Binance REST API
fetch(`https://api.binance.com/api/v3/klines?symbol=${symbol}&interval=${timeframe}&limit=120`)

// 2. Live Trades - REAL Binance WebSocket
new WebSocket(`wss://stream.binance.com:9443/ws/${bSymbol}@trade`)

// 3. Order Book Depth - REAL Binance WebSocket
new WebSocket(`wss://stream.binance.com:9443/ws/${bSymbol}@depth20@100ms`)

// 4. Live Candles - REAL Binance WebSocket
new WebSocket(`wss://stream.binance.com:9443/ws/${bSymbol}@kline_${timeframe}`)
```

**Result**: Full real-time trading terminal with live data! ✅

---

### **For Indian Markets (NSE/NFO/MCX) - SIMULATED ❌**:

```typescript
// Line 326-415: Indian Market high-fidelity simulation engine
const initialCandles = generateSimulatedHistory(symbol, timeframe); // ❌ FAKE

// Line 339: Random walk simulation
simulationIntervalRef.current = setInterval(() => {
  const tickDrift = (Math.random() - 0.5) * 0.0018; // ❌ FAKE
  const tickPrice = workingPrice * (1 + tickDrift);
  // ... generates fake trades, candles, depth
}, 550);
```

**Result**: Looks real but all data is simulated! ❌

---

## 🚀 What We Need: Replace Simulation with REAL Trade-J Backend

### **Architecture Target**:

```
┌─────────────────────────────────────────────────────────┐
│              trade_j_frontend (React + Vite)            │
│                                                         │
│  Components:                                            │
│  ├─ CandlestickChart (TradingView Lightweight Charts)  │
│  ├─ OrderBook (L2 depth visualization)                 │
│  └─ TradesList (Recent trades tape)                    │
│                                                         │
│  Data Sources (TARGET):                                 │
│  ├─ Candles → Trade-J Backend REST API                 │
│  ├─ Trades → Trade-J Backend SSE                       │
│  ├─ Order Book → Trade-J Backend SSE                   │
│  └─ Symbols → Trade-J Backend REST API                 │
└─────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────┐
│           Trade-J Backend (Spring Boot)                 │
│                                                         │
│  Market Data (from Dhan/Upstox broker):                │
│  ├─ REST: Historical candles                           │
│  ├─ SSE: Live ticks/trades                             │
│  ├─ SSE: Order book updates                            │
│  └─ REST: Symbol list                                  │
└─────────────────────────────────────────────────────────┘
```

---

## ✅ Existing Backend Endpoints (Ready to Use)

### **1. Historical Candles**:
```
GET /api/v1/market/historical/candles
  ?symbol=RELIANCE
  &exchangeSegment=NSE_EQ
  &interval=5m
  &from=2024-01-01
  &to=2024-12-31
  &source=duckdb|broker
```

**Response**:
```json
{
  "symbol": "RELIANCE",
  "exchangeSegment": "NSE_EQ",
  "interval": "5m",
  "count": 500,
  "candles": [{
    "symbol": "RELIANCE",
    "startTimeMs": 1700000000000,
    "endTimeMs": 1700000300000,
    "openPaisa": 292000,
    "highPaisa": 292500,
    "lowPaisa": 291800,
    "closePaisa": 292400,
    "volume": 123456
  }]
}
```

**Frontend Mapping**:
```typescript
// Backend sends:
{ startTimeMs: 1700000000000, openPaisa: 292000 }

// Frontend needs:
{ time: 1700000000000, open: 2920.00 }  // paisa / 100
```

**Status**: ✅ EXISTS (just needs type conversion)

---

### **2. Live Ticks/Trades (SSE)**:
```
SSE /api/v1/stream/read-model
```

**Events**:
```json
{
  "version": 123,
  "ticks": [
    {
      "symbol": "RELIANCE",
      "ltpPaisa": 292440,
      "exchangeTimestampMs": 1700000000000
    }
  ],
  "depths": [
    {
      "symbol": "RELIANCE",
      "bids": [{ "price": 292400, "quantity": 500 }],
      "asks": [{ "price": 292500, "quantity": 450 }],
      "exchangeTimestampMs": 1700000000000
    }
  ]
}
```

**Frontend Mapping**:
```typescript
// Tick → Trade
{
  id: `${symbol}-${timestamp}`,
  time: exchangeTimestampMs,
  price: ltpPaisa / 100,
  amount: 1,  // Need to get from elsewhere
  type: "buy" // Need to determine from price movement
}

// Depth → Order Book
{
  bids: depths[0].bids.map(b => ({
    price: b.price / 100,
    amount: b.quantity,
    total: (b.price / 100) * b.quantity,
    cumulative: ...,
    depthPercent: ...
  })),
  asks: ...
}
```

**Status**: ✅ EXISTS (needs parsing + transformation)

---

### **3. Symbol List**:
```
GET /api/v1/symbols
```

**Response**:
```json
{
  "symbols": [{
    "symbol": "RELIANCE",
    "name": "Reliance Industries",
    "exchange": "NSE",
    "exchangeSegment": "NSE_EQ",
    "instrumentType": "EQUITY",
    "lotSize": 1,
    "tickSizePaisa": 5
  }],
  "count": 5000
}
```

**Status**: ✅ EXISTS

---

## 🔴 What's MISSING in Backend (Need to Create)

### **Gap 1: No Real-Time Trade Stream**

**Problem**: SSE provides `TickView` (just LTP), not actual trades with quantity

**Current SSE TickView**:
```java
record TickView(String symbol, long ltpPaisa, long exchangeTimestampMs)
```

**What Frontend Needs**:
```typescript
interface Trade {
  id: string;
  time: number;
  price: number;
  amount: number;    // ❌ Missing from TickView
  type: "buy" | "sell";  // ❌ Missing from TickView
}
```

**Solution Options**:

#### **Option A**: Enhance TickView to include trade details
```java
record TickView(
    String symbol,
    long ltpPaisa,
    long volume,           // ✅ Add
    long exchangeTimestampMs,
    boolean isBuy          // ✅ Add (from price movement)
)
```

**Effort**: 30 minutes  
**File**: `ReadModelStore.java`

---

#### **Option B**: Calculate trade amount from volume delta
```typescript
// Track cumulative volume in SSE ticks
const prevVolume = useRef(0);

onTick: (tick) => {
  const tradeAmount = tick.volume - prevVolume.current;
  prevVolume.current = tick.volume;
  
  const trade: Trade = {
    id: `${tick.symbol}-${tick.exchangeTimestampMs}`,
    time: tick.exchangeTimestampMs,
    price: tick.ltpPaisa / 100,
    amount: tradeAmount,
    type: tick.ltpPaisa > prevPrice ? "buy" : "sell"
  };
}
```

**Effort**: 15 minutes (frontend only)  
**No backend changes needed!**

---

### **Gap 2: Depth Format Mismatch**

**Problem**: Backend `DepthView` uses `List<DepthLevel>`, frontend needs specific format

**Current Backend DepthView**:
```java
record DepthView(
    String symbol,
    List<DepthLevel> bids,
    List<DepthLevel> asks,
    long exchangeTimestampMs
)
```

**What DepthLevel Contains** (need to verify):
```java
// Expected:
class DepthLevel {
    long pricePaisa;
    long quantity;
    int orders;
}
```

**Frontend Needs**:
```typescript
interface BookItem {
  price: number;          // ✅ pricePaisa / 100
  amount: number;         // ✅ quantity
  total: number;          // ✅ price * amount (calculate)
  cumulative: number;     // ✅ Running total (calculate)
  depthPercent: number;   // ✅ Calculate from max depth
}
```

**Solution**: Transform client-side (no backend changes!)

**Effort**: 20 minutes (frontend transformation)

---

### **Gap 3: Candle Developing Updates**

**Problem**: Backend has both `CandleDeveloping` and `CandleClosed` events, but CandleView only shows close price

**Current SSE CandleView**:
```java
record CandleView(
    String symbol,
    String interval,
    long closePaisa,      // Only close price
    long volume,
    boolean closed
)
```

**Frontend Needs Full OHLC**:
```typescript
interface Candle {
  time: number;
  open: number;    // ❌ Missing
  high: number;    // ❌ Missing
  low: number;     // ❌ Missing
  close: number;   // ✅ closePaisa
  volume: number;  // ✅ volume
}
```

**Solution Options**:

#### **Option A**: Enhance CandleView with full OHLC
```java
record CandleView(
    String symbol,
    String interval,
    long openPaisa,      // ✅ Add
    long highPaisa,      // ✅ Add
    long lowPaisa,       // ✅ Add
    long closePaisa,
    long volume,
    long startTimeMs,    // ✅ Add (for time)
    boolean closed
)
```

**Effort**: 30 minutes  
**File**: `ReadModelStore.java`

---

#### **Option B**: Use historical candles + SSE updates
```typescript
// 1. Load historical candles from REST API
const candles = await fetch('/api/v1/market/historical/candles');

// 2. Update last candle from SSE developing events
onCandle: (candleView) => {
  if (!candleView.closed) {
    // Update last candle's close price
    setCandles(prev => {
      const updated = [...prev];
      const lastIdx = updated.length - 1;
      updated[lastIdx].close = candleView.closePaisa / 100;
      return updated;
    });
  }
}
```

**Limitation**: Can't update high/low without backend changes  
**Effort**: 15 minutes (partial solution)

---

## 📋 Backend Services Needed

### **Service 1: Market Data Streaming Service**

**Purpose**: Stream real-time market data to frontend via SSE

**Current State**: ✅ EXISTS (`ReadModelStore` + `ReadModelController`)

**Enhancements Needed**:

| Enhancement | File | Effort | Priority |
|-------------|------|--------|----------|
| Add OHLC to CandleView | `ReadModelStore.java` | 30 min | 🔴 P0 |
| Add volume delta to TickView | `ReadModelStore.java` | 20 min | 🟡 P1 |
| Verify DepthLevel format | `DepthUpdateEvent.java` | 10 min | 🔴 P0 |

**Total Backend Effort**: 1 hour

---

### **Service 2: Symbol Catalog Service**

**Purpose**: Provide list of tradeable symbols

**Current State**: ✅ EXISTS (`SymbolController`)

**Enhancements Needed**: None! Already perfect.

---

### **Service 3: Historical Data Service**

**Purpose**: Provide historical candle data

**Current State**: ✅ EXISTS (`MarketDataController`)

**Enhancements Needed**: None! Already supports DuckDB + broker.

---

## 🎯 Implementation Plan

### **Phase 1: Verify Backend Data Formats (30 min)**

1. **Start backend**:
```bash
cd /Users/apple/Downloads/Trade_J
./gradlew :app:bootRun
```

2. **Test endpoints**:
```bash
# Test symbols
curl http://localhost:8080/api/v1/symbols | jq '.symbols[0]'

# Test historical candles
curl "http://localhost:8080/api/v1/market/historical/candles?symbol=RELIANCE&exchangeSegment=NSE_EQ&interval=5m&from=2024-06-01&to=2024-06-10&source=duckdb" | jq '.candles[0]'

# Test SSE (will stream)
curl -N http://localhost:8080/api/v1/stream/read-model
```

3. **Document actual response formats**

---

### **Phase 2: Enhance Backend (If Needed) (1 hour)**

**If CandleView lacks OHLC**:
```java
// File: ReadModelStore.java:47
public record CandleView(
    String symbol,
    String interval,
    long openPaisa,      // ADD
    long highPaisa,      // ADD
    long lowPaisa,       // ADD
    long closePaisa,
    long volume,
    long startTimeMs,    // ADD
    boolean closed
) {}
```

**Update event handlers** (lines 146-155):
```java
private void putCandle(Candle candle, boolean closed) {
    String key = candle.symbol() + ":" + candle.interval();
    candles.put(key, new CandleView(
        candle.symbol(),
        candle.interval(),
        candle.openPaisa(),      // ADD
        candle.highPaisa(),      // ADD
        candle.lowPaisa(),       // ADD
        candle.closePaisa(),
        candle.volume(),
        candle.startTimeMs(),    // ADD
        closed
    ));
}
```

---

### **Phase 3: Create Frontend API Client (1.5 hours)**

**File**: `trade_j_frontend/src/api/tradeApi.ts`

```typescript
const API_BASE = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080';

// Fetch historical candles
export async function fetchCandles(
  symbol: string,
  exchangeSegment: string,
  interval: string,
  from: string,
  to: string
): Promise<Candle[]> {
  const res = await fetch(
    `${API_BASE}/api/v1/market/historical/candles` +
    `?symbol=${symbol}` +
    `&exchangeSegment=${exchangeSegment}` +
    `&interval=${interval}` +
    `&from=${from}` +
    `&to=${to}` +
    `&source=duckdb`
  );
  
  const data = await res.json();
  
  return data.candles.map((c: any) => ({
    time: c.startTimeMs,
    open: c.openPaisa / 100,
    high: c.highPaisa / 100,
    low: c.lowPaisa / 100,
    close: c.closePaisa / 100,
    volume: c.volume
  }));
}

// Fetch symbols
export async function fetchSymbols(): Promise<any[]> {
  const res = await fetch(`${API_BASE}/api/v1/symbols`);
  const data = await res.json();
  return data.symbols;
}
```

---

### **Phase 4: Create SSE Client (1 hour)**

**File**: `trade_j_frontend/src/api/sseClient.ts`

```typescript
const SSE_URL = import.meta.env.VITE_SSE_URL || 'http://localhost:8080';

type Callbacks = {
  onTick?: (tick: any) => void;
  onDepth?: (depth: any) => void;
  onCandle?: (candle: any) => void;
};

class SSEClient {
  private eventSource: EventSource | null = null;
  private callbacks: Callbacks | null = null;

  connect(callbacks: Callbacks) {
    this.callbacks = callbacks;
    this.eventSource = new EventSource(`${SSE_URL}/api/v1/stream/read-model`);
    
    this.eventSource.addEventListener('read-model', (event) => {
      const data = JSON.parse(event.data);
      
      if (data.ticks && callbacks.onTick) {
        data.ticks.forEach((tick: any) => callbacks.onTick!(tick));
      }
      
      if (data.depths && callbacks.onDepth) {
        data.depths.forEach((depth: any) => callbacks.onDepth!(depth));
      }
      
      if (data.candles && callbacks.onCandle) {
        data.candles.forEach((candle: any) => callbacks.onCandle!(candle));
      }
    });
  }

  disconnect() {
    if (this.eventSource) {
      this.eventSource.close();
      this.eventSource = null;
    }
  }
}

export const sseClient = new SSEClient();
```

---

### **Phase 5: Integrate into App.tsx (2 hours)**

**Replace Binance/Indian simulation with Trade-J backend**:

```typescript
// OLD: Binance WebSocket (line 209-325)
// NEW: Trade-J Backend SSE

useEffect(() => {
  if (broker !== "Trade-J") return;
  
  // 1. Fetch historical candles
  fetchCandles(symbol, "NSE_EQ", timeframe, "2024-06-01", "2024-06-10")
    .then(candles => setCandles(candles));
  
  // 2. Connect to SSE for live updates
  sseClient.connect({
    onTick: (tick) => {
      if (tick.symbol !== symbol) return;
      
      // Create trade from tick
      const trade: Trade = {
        id: `${tick.symbol}-${tick.exchangeTimestampMs}`,
        time: tick.exchangeTimestampMs,
        price: tick.ltpPaisa / 100,
        amount: 1, // Calculate from volume delta
        type: "buy"
      };
      
      setTrades(prev => [trade, ...prev.slice(0, 40)]);
      setLastPrice(tick.ltpPaisa / 100);
    },
    
    onDepth: (depth) => {
      if (depth.symbol !== symbol) return;
      
      // Transform to BookItem format
      const bids = depth.bids.map((b: any, idx: number) => ({
        price: b.pricePaisa / 100,
        amount: b.quantity,
        total: (b.pricePaisa / 100) * b.quantity,
        cumulative: ...,
        depthPercent: ...
      }));
      
      setBids(bids);
      setAsks(...);
    },
    
    onCandle: (candle) => {
      if (candle.symbol !== symbol || candle.interval !== timeframe) return;
      
      // Update last candle or add new one
      setCandles(prev => {
        const updated = [...prev];
        const lastIdx = updated.length - 1;
        
        if (lastIdx >= 0 && updated[lastIdx].time === candle.startTimeMs) {
          // Update developing candle
          updated[lastIdx] = {
            time: candle.startTimeMs,
            open: candle.openPaisa / 100,
            high: candle.highPaisa / 100,
            low: candle.lowPaisa / 100,
            close: candle.closePaisa / 100,
            volume: candle.volume
          };
        } else {
          // Add new candle
          updated.push({
            time: candle.startTimeMs,
            open: candle.openPaisa / 100,
            high: candle.highPaisa / 100,
            low: candle.lowPaisa / 100,
            close: candle.closePaisa / 100,
            volume: candle.volume
          });
        }
        
        return updated;
      });
    }
  });
  
  return () => sseClient.disconnect();
}, [broker, symbol, timeframe]);
```

---

## 📊 Summary

### **Backend Status**:

| Service | Status | Changes Needed | Effort |
|---------|--------|----------------|--------|
| Historical Candles | ✅ EXISTS | None | 0 min |
| Symbol List | ✅ EXISTS | None | 0 min |
| SSE Stream | ✅ EXISTS | Add OHLC to CandleView | 30 min |
| Order Book | ✅ EXISTS | Verify format | 10 min |
| **Total Backend** | **80% Ready** | **Minor enhancements** | **40 min** |

---

### **Frontend Status**:

| Component | Status | Changes Needed | Effort |
|-----------|--------|----------------|--------|
| CandlestickChart | ✅ EXISTS | Connect to Trade-J API | 1 hour |
| OrderBook | ✅ EXISTS | Transform SSE depth | 30 min |
| TradesList | ✅ EXISTS | Create from SSE ticks | 30 min |
| API Client | ❌ MISSING | Create tradeApi.ts | 1.5 hours |
| SSE Client | ❌ MISSING | Create sseClient.ts | 1 hour |
| App.tsx Integration | ❌ MISSING | Replace Binance/simulation | 2 hours |
| **Total Frontend** | **50% Ready** | **API + SSE + Integration** | **6 hours** |

---

## 🚀 Next Steps

**What would you like to do?**

1. **"Test backend endpoints first"** - Verify data formats with curl
2. **"Enhance backend CandleView"** - Add OHLC fields (30 min)
3. **"Create frontend API client"** - tradeApi.ts + sseClient.ts (2.5 hours)
4. **"Full integration"** - Replace Binance/simulation with Trade-J backend (8 hours total)

**Tell me your preference!**
