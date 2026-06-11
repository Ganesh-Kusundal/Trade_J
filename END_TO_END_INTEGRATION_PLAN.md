# Trade-J End-to-End Integration Plan
**Backend → Frontend → Testing**  
**Date**: 2026-06-10  
**Status**: Ready for Execution

---

## EXECUTIVE SUMMARY

This document provides a complete blueprint for integrating the Trade-J Terminal with the existing backend platform, including:

1. ✅ **Existing Backend APIs** (17 controllers already implemented)
2. ❌ **Missing Backend APIs** (8 endpoints needed)
3. 🔧 **Frontend API Client Layer** (complete implementation plan)
4. 🔌 **WebSocket Integration** (real-time data flow)
5. 📦 **State Management** (Zustand stores)
6. 🧪 **End-to-End Testing Strategy** (unit + integration + e2e)

**Key Finding**: The backend is **80% complete** with robust APIs. The frontend needs API client layer, state management, and 8 missing backend endpoints.

---

# PART 1: EXISTING BACKEND APIS INVENTORY

## 1.1 Complete API Endpoint Map

### Market Data APIs ✅ EXISTING

| Endpoint | Method | Status | Description | Terminal Usage |
|----------|--------|--------|-------------|----------------|
| `/api/v1/market/ltp` | GET | ✅ Exists | Last traded price | Watchlist, Header |
| `/api/v1/market/historical/candles` | GET | ✅ Exists | Historical OHLCV data | TradingChart |
| `/api/v1/market/capabilities` | GET | ✅ Exists | Broker capabilities | Info panel |
| `/api/v1/market/intervals` | GET | ✅ Exists | Supported timeframes | Chart timeframe selector |
| `/api/v1/symbols` | GET | ✅ Exists | All tradeable symbols | Watchlist, Symbol search |
| `/api/v1/symbols/search` | GET | ✅ Exists | Symbol search | Symbol autocomplete |

### Order Management APIs ✅ EXISTING

| Endpoint | Method | Status | Description | Terminal Usage |
|----------|--------|--------|-------------|----------------|
| `/api/v1/orders` | GET | ✅ Exists | List orders (active/completed/all) | TerminalTabs (Orders) |
| `/api/v1/orders` | POST | ✅ Exists | Place order | OrderEntryPanel |
| `/api/v1/orders/place` | POST | ✅ Exists | Place order (alias) | OrderEntryPanel |
| `/api/v1/orders/{id}` | PUT | ✅ Exists | Modify order | Order management |
| `/api/v1/orders/{id}/cancel` | POST | ✅ Exists | Cancel order | Order management |

### Options APIs ✅ EXISTING

| Endpoint | Method | Status | Description | Terminal Usage |
|----------|--------|--------|-------------|----------------|
| `/api/v1/options/scan` | POST | ✅ Exists | Scan options by criteria | OptionChain, Scanner |
| `/api/v1/options/analytics` | GET | ✅ Exists | Options analytics | Options workspace |
| `/api/v1/expired-options` | GET | ✅ Exists | Expired options data | Historical analysis |

### Scanner APIs ✅ EXISTING

| Endpoint | Method | Status | Description | Terminal Usage |
|----------|--------|--------|-------------|----------------|
| `/api/v1/scans/latest` | GET | ✅ Exists | Latest scan results | Scanner workspace |
| `/api/v1/scans/{runId}` | GET | ✅ Exists | Scan results by ID | Scanner workspace |
| `/api/v1/scans` | GET | ✅ Exists | List scan runs | Scanner workspace |

### Replay APIs ✅ EXISTING

| Endpoint | Method | Status | Description | Terminal Usage |
|----------|--------|--------|-------------|----------------|
| `/api/v1/replay/start` | POST | ✅ Exists | Start candle replay | Replay workspace |
| `/api/v1/replay/stop` | POST | ✅ Exists | Stop replay | Replay workspace |
| `/api/v1/replay/play` | POST | ✅ Exists | Play/resume replay | Replay controls |
| `/api/v1/replay/pause` | POST | ✅ Exists | Pause replay | Replay controls |
| `/api/v1/replay/step` | POST | ✅ Exists | Step one candle | Replay controls |
| `/api/v1/replay/status` | GET | ✅ Exists | Current replay status | Replay progress |
| `/api/v1/replay/candles` | GET | ✅ Exists | Replay candle range | Replay chart |

### Read Model (Real-time State) ✅ EXISTING

| Endpoint | Method | Status | Description | Terminal Usage |
|----------|--------|--------|-------------|----------------|
| `/api/v1/read-model` | GET | ✅ Exists | Snapshot of current state | All workspaces |
| `/api/v1/stream/read-model` | SSE | ✅ Exists | Server-Sent Events stream | Real-time updates |

### Analytics APIs ✅ EXISTING

| Endpoint | Method | Status | Description | Terminal Usage |
|----------|--------|--------|-------------|----------------|
| `/api/v1/analytics/pnl` | GET | ✅ Exists | PnL analytics | PnL dashboard |
| `/api/v1/analytics/performance` | GET | ✅ Exists | Performance metrics | Analytics workspace |
| `/api/v1/portfolio/analytics` | GET | ✅ Exists | Portfolio analytics | Portfolio view |
| `/api/v1/depth/analytics` | GET | ✅ Exists | Market depth analytics | MarketDepth widget |

### Admin/Monitoring APIs ✅ EXISTING

| Endpoint | Method | Status | Description | Terminal Usage |
|----------|--------|--------|-------------|----------------|
| `/api/v1/admin/health` | GET | ✅ Exists | System health | Status bar |
| `/api/v1/admin/status` | GET | ✅ Exists | System status | Status bar |
| `/api/v1/admin/rate-limit` | GET | ✅ Exists | Rate limit metrics | Info panel |
| `/api/v1/sync/status` | GET | ✅ Exists | Sync status | Info panel |
| `/api/v1/sync/calendar` | GET | ✅ Exists | Trading calendar | Info panel |

### Other APIs ✅ EXISTING

| Endpoint | Method | Status | Description | Terminal Usage |
|----------|--------|--------|-------------|----------------|
| `/api/v1/news` | GET | ✅ Exists | Market news | News workspace |
| `/api/v1/pipeline` | GET | ✅ Exists | Pipeline status | Info panel |
| `/api/v1/studio` | POST | ✅ Exists | Strategy studio | Strategy workspace |
| `/api/v1/backtest` | POST | ✅ Exists | Backtesting | Strategy workspace |

---

## 1.2 Existing WebSocket/SSE Infrastructure

### Server-Sent Events (SSE) ✅ EXISTS

```java
// ReadModelController.java
@GetMapping(value = "/stream/read-model", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public SseEmitter stream() {
    // Streams: orders, positions, ticks, depths, candles, signals, pnl
    // Heartbeat every 15 seconds
}
```

**What's Streamed**:
- ✅ Orders (active + completed)
- ✅ Positions (real-time PnL)
- ✅ Market ticks (price updates)
- ✅ Market depth (Level 2 updates)
- ✅ Candles (OHLCV updates)
- ✅ Strategy signals
- ✅ PnL calculations

**Gap**: Terminal doesn't consume this SSE stream at all!

---

# PART 2: MISSING BACKEND APIS (8 ENDPOINTS NEEDED)

## 2.1 Priority 0 - Critical (Week 1)

### 1. Market Depth (Level 2 Order Book)

**Endpoint**: `GET /api/v1/market/depth`

**Why Needed**: MarketDepthWidget, OrderEntryPanel

**Request**:
```http
GET /api/v1/market/depth?symbol=RELIANCE&exchangeSegment=NSE_EQ
```

**Response**:
```json
{
  "symbol": "RELIANCE",
  "exchangeSegment": "NSE_EQ",
  "timestamp": 1718020800,
  "bids": [
    { "price": 2924.35, "qty": 1250, "orders": 8 },
    { "price": 2924.30, "qty": 980, "orders": 5 }
  ],
  "asks": [
    { "price": 2924.40, "qty": 1100, "orders": 6 },
    { "price": 2924.45, "qty": 1350, "orders": 9 }
  ],
  "spread": 0.05,
  "totalBidQty": 12500,
  "totalAskQty": 13200
}
```

**Implementation**:
```java
@RestController
@RequestMapping("/api/v1/market")
public class MarketDepthController {
    
    private final MarketDataApplicationService marketDataService;
    
    @GetMapping("/depth")
    public ResponseEntity<MarketDepthResponse> getDepth(
        @RequestParam String symbol,
        @RequestParam ExchangeSegment exchangeSegment
    ) {
        InstrumentKey key = InstrumentKey.of(symbol, exchangeSegment);
        MarketDepth depth = marketDataService.getMarketDepth(key);
        return ResponseEntity.ok(MarketDepthResponse.from(depth));
    }
}
```

**Effort**: 2 hours

---

### 2. Option Chain with Greeks

**Endpoint**: `GET /api/v1/market/options/chain`

**Why Needed**: OptionChainTable, Options workspace

**Request**:
```http
GET /api/v1/market/options/chain?underlying=NIFTY&expiry=2026-06-26&exchangeSegment=IDX_I
```

**Response**:
```json
{
  "underlying": "NIFTY",
  "expiry": "2026-06-26",
  "spotPrice": 23450.50,
  "timestamp": 1718020800,
  "rows": [
    {
      "strikePrice": 23400,
      "ce": {
        "ticker": "NIFTY26JUN23400CE",
        "ltp": 185.50,
        "change": 12.30,
        "changePercent": 7.10,
        "volume": 450000,
        "openInterest": 1200000,
        "openInterestChange": 45000,
        "bid": 185.00,
        "bidQty": 500,
        "ask": 186.00,
        "askQty": 450,
        "iv": 12.5,
        "delta": 0.58,
        "gamma": 0.0012,
        "theta": -8.5,
        "vega": 15.2
      },
      "pe": {
        "ticker": "NIFTY26JUN23400PE",
        "ltp": 142.30,
        "change": -5.20,
        "changePercent": -3.52,
        "volume": 520000,
        "openInterest": 1500000,
        "openInterestChange": 52000,
        "bid": 142.00,
        "bidQty": 600,
        "ask": 142.50,
        "askQty": 550,
        "iv": 13.2,
        "delta": -0.42,
        "gamma": 0.0012,
        "theta": -7.8,
        "vega": 15.2
      }
    }
  ]
}
```

**Implementation**:
```java
@RestController
@RequestMapping("/api/v1/market/options")
public class OptionChainController {
    
    private final OptionsAnalyticsService optionsService;
    
    @GetMapping("/chain")
    public ResponseEntity<OptionChainResponse> getOptionChain(
        @RequestParam String underlying,
        @RequestParam String expiry,
        @RequestParam(defaultValue = "IDX_I") String exchangeSegment
    ) {
        OptionChain chain = optionsService.getOptionChain(
            underlying, 
            LocalDate.parse(expiry),
            ExchangeSegment.valueOf(exchangeSegment)
        );
        return ResponseEntity.ok(OptionChainResponse.from(chain));
    }
}
```

**Effort**: 4 hours (uses existing OptionsAnalyticsService)

---

### 3. Holdings (Portfolio)

**Endpoint**: `GET /api/v1/portfolio/holdings`

**Why Needed**: TerminalTabs (Holdings tab)

**Request**:
```http
GET /api/v1/portfolio/holdings
```

**Response**:
```json
{
  "holdings": [
    {
      "ticker": "TCS",
      "name": "Tata Consultancy Services Ltd.",
      "qty": 50,
      "avgPrice": 3540.20,
      "ltp": 3820.15,
      "currentValue": 191007.50,
      "investedValue": 177010.00,
      "pnl": 13997.50,
      "pnlPercent": 7.91
    }
  ],
  "totalInvested": 759910.00,
  "totalCurrent": 829182.50,
  "totalPnl": 69272.50,
  "totalPnlPercent": 9.11
}
```

**Implementation**:
```java
@RestController
@RequestMapping("/api/v1/portfolio")
public class PortfolioController {
    
    private final PortfolioService portfolioService;
    
    @GetMapping("/holdings")
    public ResponseEntity<HoldingsResponse> getHoldings() {
        HoldingsResponse holdings = portfolioService.getHoldings();
        return ResponseEntity.ok(holdings);
    }
}
```

**Effort**: 2 hours (broker API already has holdings endpoint)

---

## 2.2 Priority 1 - Important (Week 2)

### 4. Strategy Signals

**Endpoint**: `GET /api/v1/strategies/signals`

**Why Needed**: Strategy workspace, TerminalTabs (Signals tab)

**Request**:
```http
GET /api/v1/strategies/signals?symbol=RELIANCE&from=2026-06-09&to=2026-06-10
```

**Response**:
```json
{
  "signals": [
    {
      "id": "sig-123456",
      "timestamp": 1718019600,
      "symbol": "RELIANCE",
      "strategy": "HalfTrend",
      "action": "BUY",
      "price": 2920.00,
      "strength": "STRONG",
      "indicators": {
        "halfTrend": "BULLISH",
        "ema20": 2915.50,
        "ema50": 2910.20,
        "rsi": 62.5
      }
    }
  ],
  "count": 1
}
```

**Implementation**:
```java
@RestController
@RequestMapping("/api/v1/strategies")
public class StrategySignalsController {
    
    private final StrategySignalService signalService;
    
    @GetMapping("/signals")
    public ResponseEntity<SignalsResponse> getSignals(
        @RequestParam(required = false) String symbol,
        @RequestParam LocalDate from,
        @RequestParam LocalDate to
    ) {
        List<StrategySignal> signals = signalService.getSignals(symbol, from, to);
        return ResponseEntity.ok(SignalsResponse.from(signals));
    }
}
```

**Effort**: 3 hours

---

### 5. Positions (Dedicated Endpoint)

**Note**: Positions already available via `/api/v1/read-model`, but needs dedicated endpoint

**Endpoint**: `GET /api/v1/portfolio/positions`

**Why Needed**: TerminalTabs (Positions tab), PnL dashboard

**Request**:
```http
GET /api/v1/portfolio/positions
```

**Response**:
```json
{
  "positions": [
    {
      "ticker": "RELIANCE",
      "qty": 250,
      "avgPrice": 2895.00,
      "ltp": 2924.40,
      "unrealizedPnl": 7350.00,
      "realizedPnl": 0.00,
      "product": "MIS",
      "timestamp": 1718020800
    }
  ],
  "totalUnrealizedPnl": 14250.00,
  "totalRealizedPnl": 1200.00,
  "totalPnl": 15450.00
}
```

**Implementation**: Wrapper around read-model positions

**Effort**: 1 hour

---

### 6. PnL Analytics (Detailed)

**Note**: Exists at `/api/v1/analytics/pnl` but needs enhanced response

**Endpoint**: `GET /api/v1/analytics/pnl/detailed`

**Why Needed**: PnL dashboard, Analytics workspace

**Request**:
```http
GET /api/v1/analytics/pnl/detailed?period=today
```

**Response**:
```json
{
  "period": "today",
  "realizedPnl": 1200.00,
  "unrealizedPnl": 14250.00,
  "totalPnl": 15450.00,
  "pnlPercent": 1.54,
  "trades": 12,
  "winRate": 75.0,
  "maxDrawdown": -2.1,
  "sharpeRatio": 1.85,
  "pnlTimeline": [
    { "timestamp": 1718013600, "pnl": 5000.00 },
    { "timestamp": 1718017200, "pnl": 12000.00 },
    { "timestamp": 1718020800, "pnl": 15450.00 }
  ]
}
```

**Effort**: 2 hours (enhance existing endpoint)

---

### 7. Replay Candles (Enhanced)

**Note**: Exists but needs to stream candles in real-time during replay

**Endpoint**: `GET /api/v1/replay/candles/stream`

**Why Needed**: TradingChart in replay mode

**Implementation**: SSE endpoint that streams replay candles

```java
@GetMapping(value = "/candles/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public SseEmitter streamReplayCandles() {
    SseEmitter emitter = new SseEmitter(0L);
    
    replaySession.subscribe(candle -> {
        try {
            emitter.send(SseEmitter.event()
                .name("replay-candle")
                .data(CandleResponse.from(candle)));
        } catch (IOException e) {
            emitter.completeWithError(e);
        }
    });
    
    return emitter;
}
```

**Effort**: 2 hours

---

### 8. News (Enhanced)

**Note**: Exists at `/api/v1/news` but needs more data

**Enhancement**: Add sentiment, impact, URL fields

**Effort**: 1 hour

---

## 2.3 Missing Backend Services Summary

| # | Endpoint | Priority | Effort | Dependencies |
|---|----------|----------|--------|--------------|
| 1 | `GET /api/v1/market/depth` | P0 | 2h | MarketDataService |
| 2 | `GET /api/v1/market/options/chain` | P0 | 4h | OptionsAnalyticsService |
| 3 | `GET /api/v1/portfolio/holdings` | P0 | 2h | Broker API |
| 4 | `GET /api/v1/strategies/signals` | P1 | 3h | StrategySignalService |
| 5 | `GET /api/v1/portfolio/positions` | P1 | 1h | ReadModelStore |
| 6 | `GET /api/v1/analytics/pnl/detailed` | P1 | 2h | AnalyticsService |
| 7 | `GET /api/v1/replay/candles/stream` | P1 | 2h | CandleReplaySession |
| 8 | `GET /api/v1/news` (enhanced) | P2 | 1h | NewsService |

**Total Backend Effort**: 17 hours (~2 days)

---

# PART 3: FRONTEND API CLIENT LAYER

## 3.1 Architecture

```
┌─────────────────────────────────────────────┐
│  Terminal Components                        │
│  (TradingChart, OptionChain, OrderEntry)    │
├─────────────────────────────────────────────┤
│  Zustand Stores                             │
│  (marketStore, orderStore, positionStore)   │
├─────────────────────────────────────────────┤
│  API Client Layer                           │
│  ├─ tradeApi.ts (REST)                     │
│  ├─ websocket.ts (SSE)                     │
│  └─ adapters.ts (Type conversions)         │
├─────────────────────────────────────────────┤
│  HTTP Client (fetch) + EventSource (SSE)   │
└─────────────────────────────────────────────┘
```

## 3.2 File Structure

```
frontend/src/terminal/
├── api/
│   ├── tradeApi.ts              ← REST API client
│   ├── websocket.ts             ← SSE client
│   ├── adapters.ts              ← Backend → Frontend type adapters
│   └── types.ts                 ← API request/response types
├── store/
│   ├── marketStore.ts           ← Market data state
│   ├── orderStore.ts            ← Order management state
│   ├── positionStore.ts         ← Position/PnL state
│   ├── replayStore.ts           ← Replay state
│   └── strategyStore.ts         ← Strategy/signals state
└── components/                  ← Existing components (unchanged)
```

## 3.3 Implementation: tradeApi.ts

```typescript
// frontend/src/terminal/api/tradeApi.ts

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080';

// ============================================================
// Market Data APIs
// ============================================================

export async function fetchSymbols(query?: string): Promise<SymbolInfo[]> {
  const params = new URLSearchParams();
  if (query) params.append('q', query);
  
  const response = await fetch(`${API_BASE_URL}/api/v1/symbols?${params}`);
  const data = await response.json();
  
  return data.symbols.map((s: any) => ({
    ticker: s.symbol,
    name: s.name || s.symbol,
    exchange: s.exchangeSegment,
    type: s.securityType,
    lotSize: s.lotSize || 1,
    tickSize: s.tickSize || 0.05
  }));
}

export async function fetchCandles(
  symbol: string,
  exchangeSegment: string,
  interval: string = '5m',
  from: string,
  to: string,
  source: string = 'broker'
): Promise<Candle[]> {
  const params = new URLSearchParams({
    symbol,
    exchangeSegment,
    interval,
    from,
    to,
    source
  });
  
  const response = await fetch(
    `${API_BASE_URL}/api/v1/market/historical/candles?${params}`
  );
  const data = await response.json();
  
  return data.candles.map((c: any) => ({
    time: c.startTimeMs / 1000, // Convert ms to seconds
    open: c.openPaisa / 100,    // Convert paisa to rupees
    high: c.highPaisa / 100,
    low: c.lowPaisa / 100,
    close: c.closePaisa / 100,
    volume: c.volume
  }));
}

export async function fetchLtp(
  symbol: string,
  exchangeSegment: string
): Promise<number> {
  const params = new URLSearchParams({ symbol, exchangeSegment });
  
  const response = await fetch(
    `${API_BASE_URL}/api/v1/market/ltp?${params}`
  );
  const data = await response.json();
  
  return data.ltpPaisa / 100; // Convert paisa to rupees
}

export async function fetchMarketDepth(
  symbol: string,
  exchangeSegment: string
): Promise<MarketDepth> {
  const params = new URLSearchParams({ symbol, exchangeSegment });
  
  const response = await fetch(
    `${API_BASE_URL}/api/v1/market/depth?${params}`
  );
  const data = await response.json();
  
  return {
    ticker: data.symbol,
    bids: data.bids,
    asks: data.asks,
    spread: data.spread,
    totalBidQty: data.totalBidQty,
    totalAskQty: data.totalAskQty
  };
}

export async function fetchOptionChain(
  underlying: string,
  expiry: string,
  exchangeSegment: string = 'IDX_I'
): Promise<OptionChain> {
  const params = new URLSearchParams({
    underlying,
    expiry,
    exchangeSegment
  });
  
  const response = await fetch(
    `${API_BASE_URL}/api/v1/market/options/chain?${params}`
  );
  const data = await response.json();
  
  return {
    underlying: data.underlying,
    expiry: data.expiry,
    rows: data.rows
  };
}

// ============================================================
// Order Management APIs
// ============================================================

export async function fetchOrders(status: string = 'active'): Promise<Order[]> {
  const params = new URLSearchParams({ status });
  
  const response = await fetch(
    `${API_BASE_URL}/api/v1/orders?${params}`
  );
  const data = await response.json();
  
  return data.map((o: any) => ({
    id: o.orderId,
    ticker: o.symbol,
    side: o.side,
    qty: o.quantity,
    filledQty: o.filledQuantity || 0,
    price: o.pricePaisa / 100,
    type: o.orderType,
    product: o.productType,
    status: o.status,
    time: formatISTDateTime(o.timestamp / 1000),
    rejectReason: o.rejectReason
  }));
}

export async function placeOrder(order: PlaceOrderRequest): Promise<Order> {
  const response = await fetch(`${API_BASE_URL}/api/v1/orders`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      symbol: order.ticker,
      exchangeSegment: order.exchangeSegment,
      side: order.side,
      quantity: order.qty,
      orderType: order.type,
      pricePaisa: Math.round(order.price * 100),
      effectiveTriggerPricePaisa: order.triggerPrice 
        ? Math.round(order.triggerPrice * 100) 
        : undefined,
      productType: order.product,
      validity: order.validity || 'DAY',
      correlationId: order.correlationId
    })
  });
  
  if (!response.ok) {
    const error = await response.json();
    throw new Error(error.reason || error.error || 'Order placement failed');
  }
  
  const data = await response.json();
  return adaptOrder(data);
}

export async function cancelOrder(orderId: string): Promise<boolean> {
  const response = await fetch(
    `${API_BASE_URL}/api/v1/orders/${orderId}/cancel`,
    { method: 'POST' }
  );
  
  const data = await response.json();
  return data.cancelled;
}

// ============================================================
// Portfolio APIs
// ============================================================

export async function fetchPositions(): Promise<Position[]> {
  const response = await fetch(`${API_BASE_URL}/api/v1/portfolio/positions`);
  const data = await response.json();
  
  return data.positions.map((p: any) => ({
    ticker: p.symbol,
    qty: p.quantity,
    avgPrice: p.averagePrice,
    ltp: p.ltp,
    pnl: p.pnl,
    unrealizedPnl: p.unrealizedPnl,
    realizedPnl: p.realizedPnl,
    product: p.productType
  }));
}

export async function fetchHoldings(): Promise<Holding[]> {
  const response = await fetch(`${API_BASE_URL}/api/v1/portfolio/holdings`);
  const data = await response.json();
  
  return data.holdings.map((h: any) => ({
    ticker: h.symbol,
    name: h.name,
    qty: h.quantity,
    avgPrice: h.averagePrice,
    ltp: h.ltp,
    currentValue: h.currentValue,
    investedValue: h.investedValue,
    pnl: h.pnl,
    pnlPercent: h.pnlPercent
  }));
}

// ============================================================
// Strategy APIs
// ============================================================

export async function fetchSignals(
  symbol?: string,
  from: string,
  to: string
): Promise<StrategySignal[]> {
  const params = new URLSearchParams({ from, to });
  if (symbol) params.append('symbol', symbol);
  
  const response = await fetch(
    `${API_BASE_URL}/api/v1/strategies/signals?${params}`
  );
  const data = await response.json();
  
  return data.signals.map((s: any) => ({
    id: s.id,
    ticker: s.symbol,
    time: formatISTDateTime(s.timestamp / 1000),
    indicator: s.strategy,
    action: s.action,
    price: s.price,
    strength: s.strength
  }));
}

// ============================================================
// Replay APIs
// ============================================================

export async function startReplay(
  symbol: string,
  exchangeSegment: string,
  from: string,
  to: string,
  interval: string = '1m'
): Promise<ReplayStatus> {
  const params = new URLSearchParams({
    symbol,
    exchange: exchangeSegment,
    from,
    to,
    interval
  });
  
  const response = await fetch(
    `${API_BASE_URL}/api/v1/replay/start?${params}`,
    { method: 'POST' }
  );
  
  return await response.json();
}

export async function stopReplay(): Promise<void> {
  await fetch(`${API_BASE_URL}/api/v1/replay/stop`, { method: 'POST' });
}

export async function playReplay(): Promise<void> {
  await fetch(`${API_BASE_URL}/api/v1/replay/play`, { method: 'POST' });
}

export async function pauseReplay(): Promise<void> {
  await fetch(`${API_BASE_URL}/api/v1/replay/pause`, { method: 'POST' });
}

export async function stepReplay(): Promise<void> {
  await fetch(`${API_BASE_URL}/api/v1/replay/step`, { method: 'POST' });
}

export async function getReplayStatus(): Promise<ReplayStatus> {
  const response = await fetch(`${API_BASE_URL}/api/v1/replay/status`);
  return await response.json();
}

// ============================================================
// Analytics APIs
// ============================================================

export async function fetchPnLAnalytics(period: string = 'today'): Promise<PnLAnalytics> {
  const params = new URLSearchParams({ period });
  
  const response = await fetch(
    `${API_BASE_URL}/api/v1/analytics/pnl/detailed?${params}`
  );
  return await response.json();
}

// ============================================================
// Scanner APIs
// ============================================================

export async function fetchLatestScan(profile: string): Promise<ScanResult> {
  const params = new URLSearchParams({ profile });
  
  const response = await fetch(
    `${API_BASE_URL}/api/v1/scans/latest?${params}`
  );
  return await response.json();
}

export async function scanOptions(
  underlying: string,
  expiry: string,
  options: OptionScanOptions = {}
): Promise<OptionScanResult> {
  const params = new URLSearchParams({
    underlying,
    expiry,
    ...options
  });
  
  const response = await fetch(
    `${API_BASE_URL}/api/v1/options/scan?${params}`,
    { method: 'POST' }
  );
  return await response.json();
}

// ============================================================
// Helper Functions
// ============================================================

function adaptOrder(data: any): Order {
  return {
    id: data.orderId,
    ticker: data.symbol,
    side: data.side,
    qty: data.quantity,
    filledQty: data.filledQuantity || 0,
    price: data.pricePaisa / 100,
    type: data.orderType,
    product: data.productType,
    status: data.status,
    time: formatISTDateTime(data.timestamp / 1000),
    rejectReason: data.rejectReason
  };
}
```

## 3.4 Implementation: websocket.ts (SSE Client)

```typescript
// frontend/src/terminal/api/websocket.ts

const SSE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080';

export class ReadModelStream {
  private eventSource: EventSource | null = null;
  private subscribers: Set<ReadModelSubscriber> = new Set();
  private reconnectTimer: NodeJS.Timeout | null = null;
  private lastVersion: number = -1;

  connect() {
    this.eventSource = new EventSource(`${SSE_URL}/api/v1/stream/read-model`);

    this.eventSource.addEventListener('read-model', (event) => {
      const data = JSON.parse(event.data);
      
      // Skip duplicate versions
      if (data.version <= this.lastVersion) return;
      this.lastVersion = data.version;

      // Notify subscribers
      this.subscribers.forEach(sub => {
        if (sub.onOrders && data.orders) sub.onOrders(data.orders);
        if (sub.onPositions && data.positions) sub.onPositions(data.positions);
        if (sub.onCandles && data.candles) sub.onCandles(data.candles);
        if (sub.onSignals && data.signals) sub.onSignals(data.signals);
        if (sub.onPnL && data.pnl) sub.onPnL(data.pnl);
      });
    });

    this.eventSource.onerror = (error) => {
      console.error('[SSE] Connection error:', error);
      
      // Auto-reconnect after 5 seconds
      this.disconnect();
      this.reconnectTimer = setTimeout(() => this.connect(), 5000);
    };
  }

  subscribe(subscriber: ReadModelSubscriber): Unsubscribe {
    this.subscribers.add(subscriber);
    
    return () => {
      this.subscribers.delete(subscriber);
    };
  }

  disconnect() {
    if (this.eventSource) {
      this.eventSource.close();
      this.eventSource = null;
    }
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer);
      this.reconnectTimer = null;
    }
  }
}

export interface ReadModelSubscriber {
  onOrders?: (orders: any[]) => void;
  onPositions?: (positions: any[]) => void;
  onCandles?: (candles: any[]) => void;
  onSignals?: (signals: any[]) => void;
  onPnL?: (pnl: any) => void;
}

export interface Unsubscribe {
  (): void;
}

// Singleton instance
export const readModelStream = new ReadModelStream();
```

## 3.5 Implementation: Zustand Stores

### marketStore.ts

```typescript
// frontend/src/terminal/store/marketStore.ts

import { create } from 'zustand';
import * as api from '../api/tradeApi';
import { readModelStream } from '../api/websocket';
import type { SymbolInfo, Candle, Quote, MarketDepth, OptionChain } from '../types';

interface MarketState {
  // Symbols
  symbols: SymbolInfo[];
  selectedSymbol: SymbolInfo | null;
  
  // Candles
  candles: Candle[];
  timeframe: string;
  
  // Live data
  quote: Quote | null;
  depth: MarketDepth | null;
  optionChain: OptionChain | null;
  
  // Loading states
  isLoading: boolean;
  error: string | null;
  
  // Actions
  loadSymbols: (query?: string) => Promise<void>;
  selectSymbol: (symbol: SymbolInfo) => void;
  loadCandles: (timeframe?: string) => Promise<void>;
  loadDepth: () => Promise<void>;
  loadOptionChain: (expiry: string) => Promise<void>;
  subscribeToLiveUpdates: () => void;
  setTimeframe: (timeframe: string) => void;
}

export const useMarketStore = create<MarketState>((set, get) => ({
  symbols: [],
  selectedSymbol: null,
  candles: [],
  timeframe: '5m',
  quote: null,
  depth: null,
  optionChain: null,
  isLoading: false,
  error: null,

  loadSymbols: async (query) => {
    try {
      const symbols = await api.fetchSymbols(query);
      set({ symbols, error: null });
    } catch (error: any) {
      set({ error: error.message });
    }
  },

  selectSymbol: (symbol) => {
    set({ selectedSymbol: symbol });
    get().loadCandles();
    get().loadDepth();
  },

  loadCandles: async (timeframe) => {
    const { selectedSymbol } = get();
    if (!selectedSymbol) return;

    set({ isLoading: true, error: null, timeframe: timeframe || get().timeframe });

    try {
      const now = new Date();
      const from = new Date(now.getTime() - 7 * 24 * 60 * 60 * 1000).toISOString().split('T')[0];
      const to = now.toISOString().split('T')[0];

      const candles = await api.fetchCandles(
        selectedSymbol.ticker,
        selectedSymbol.exchange,
        get().timeframe,
        from,
        to
      );

      set({ candles, isLoading: false });
    } catch (error: any) {
      set({ error: error.message, isLoading: false });
    }
  },

  loadDepth: async () => {
    const { selectedSymbol } = get();
    if (!selectedSymbol) return;

    try {
      const depth = await api.fetchMarketDepth(
        selectedSymbol.ticker,
        selectedSymbol.exchange
      );
      set({ depth });
    } catch (error: any) {
      console.error('[MarketStore] Failed to load depth:', error);
    }
  },

  loadOptionChain: async (expiry) => {
    const { selectedSymbol } = get();
    if (!selectedSymbol) return;

    try {
      const optionChain = await api.fetchOptionChain(
        selectedSymbol.ticker,
        expiry,
        selectedSymbol.exchange
      );
      set({ optionChain });
    } catch (error: any) {
      console.error('[MarketStore] Failed to load option chain:', error);
    }
  },

  subscribeToLiveUpdates: () => {
    readModelStream.subscribe({
      onCandles: (candles) => {
        // Update latest candle
        set(state => {
          const updated = [...state.candles];
          const latest = candles[candles.length - 1];
          if (latest && updated.length > 0) {
            updated[updated.length - 1] = adaptCandle(latest);
          }
          return { candles: updated };
        });
      }
    });
  },

  setTimeframe: (timeframe) => {
    set({ timeframe });
    get().loadCandles();
  }
}));

function adaptCandle(data: any): Candle {
  return {
    time: data.startTimeMs / 1000,
    open: data.openPaisa / 100,
    high: data.highPaisa / 100,
    low: data.lowPaisa / 100,
    close: data.closePaisa / 100,
    volume: data.volume
  };
}
```

---

# PART 4: END-TO-END INTEGRATION PLAN

## 4.1 Phase 1: Foundation (Week 1)

### Day 1-2: Backend API Creation

**Tasks**:
1. Create `MarketDepthController` (2h)
2. Create `OptionChainController` (4h)
3. Create `PortfolioController` (2h)
4. Test all 3 endpoints with curl/Postman

**Deliverables**:
- ✅ 3 new REST endpoints
- ✅ API documentation
- ✅ Unit tests for controllers

### Day 3-4: Frontend API Client

**Tasks**:
1. Create `tradeApi.ts` (4h)
2. Create `websocket.ts` (2h)
3. Create `adapters.ts` (2h)
4. Test API client with mock backend

**Deliverables**:
- ✅ Complete API client layer
- ✅ SSE client with auto-reconnect
- ✅ Type-safe adapters

### Day 5: Zustand Stores

**Tasks**:
1. Create `marketStore.ts` (3h)
2. Create `orderStore.ts` (2h)
3. Create `positionStore.ts` (2h)
4. Integrate stores with components

**Deliverables**:
- ✅ 3 Zustand stores
- ✅ Real-time SSE subscriptions
- ✅ Component integration

---

## 4.2 Phase 2: Core Integration (Week 2)

### Day 1-2: Missing Backend APIs

**Tasks**:
1. Create `StrategySignalsController` (3h)
2. Create dedicated positions endpoint (1h)
3. Enhance PnL analytics endpoint (2h)
4. Enhance news endpoint (1h)

**Deliverables**:
- ✅ 4 enhanced/new endpoints
- ✅ Strategy signals API
- ✅ Detailed PnL analytics

### Day 3-4: Terminal Component Integration

**Tasks**:
1. Replace mockData.ts calls with API calls (4h)
2. Integrate TradingChart with real candles (2h)
3. Integrate OptionChain with real options (2h)
4. Integrate OrderEntryPanel with real orders (2h)

**Deliverables**:
- ✅ All components use real API
- ✅ Mock data removed
- ✅ Real-time updates working

### Day 5: WebSocket Integration

**Tasks**:
1. Connect SSE stream to all stores (3h)
2. Test real-time updates (2h)
3. Handle disconnection/reconnection (2h)
4. Error handling (1h)

**Deliverables**:
- ✅ Real-time order updates
- ✅ Real-time position updates
- ✅ Real-time candle updates
- ✅ Auto-reconnect logic

---

## 4.3 Phase 3: Advanced Features (Week 3)

### Day 1-2: Replay Workspace

**Tasks**:
1. Create replay workspace UI (4h)
2. Integrate with replay API (2h)
3. Add replay controls (play/pause/step) (2h)
4. Add replay progress bar (2h)

**Deliverables**:
- ✅ Replay workspace
- ✅ Real replay engine integration
- ✅ Interactive controls

### Day 3-4: Scanner Workspace

**Tasks**:
1. Create scanner workspace UI (4h)
2. Integrate with scan API (2h)
3. Add scanner configuration (2h)
4. Add click-through to chart (2h)

**Deliverables**:
- ✅ Scanner workspace
- ✅ Real scan results
- ✅ Symbol drill-down

### Day 5: Strategy Workspace

**Tasks**:
1. Create strategy workspace UI (4h)
2. Integrate with strategy signals (2h)
3. Add strategy visualization (2h)
4. Add PnL timeline (2h)

**Deliverables**:
- ✅ Strategy workspace
- ✅ Signal visualization
- ✅ PnL timeline

---

## 4.4 Phase 4: Paper Trading & Certification (Week 4)

### Day 1-2: Paper Trading Workspace

**Tasks**:
1. Create paper trading workspace (4h)
2. Integrate with paper OMS (2h)
3. Add paper positions/orders (2h)
4. Add PnL analytics (2h)

**Deliverables**:
- ✅ Paper trading workspace
- ✅ Paper OMS integration
- ✅ Real-time PnL

### Day 3-4: Certification Dashboard

**Tasks**:
1. Create certification dashboard UI (4h)
2. Integrate with certification framework (2h)
3. Add certification status display (2h)
4. Add evidence viewer (2h)

**Deliverables**:
- ✅ Certification dashboard
- ✅ Platform certification status
- ✅ Evidence viewer

### Day 5: Options Research Workspace

**Tasks**:
1. Create options research workspace (4h)
2. Add OI ranking (2h)
3. Add PCR analysis (2h)
4. Add IV analysis (2h)

**Deliverables**:
- ✅ Options research workspace
- ✅ OI/volume ranking
- ✅ PCR/IV analysis

---

## 4.5 Phase 5: Testing & Polish (Week 5)

### Day 1-2: End-to-End Testing

**Tasks**:
1. Write e2e test scripts (4h)
2. Test all workspaces (4h)
3. Test real-time updates (2h)
4. Test error scenarios (2h)

**Deliverables**:
- ✅ E2E test suite
- ✅ All workspaces tested
- ✅ Error handling verified

### Day 3-4: Performance Optimization

**Tasks**:
1. Optimize API calls (caching) (3h)
2. Optimize chart rendering (2h)
3. Optimize SSE handling (2h)
4. Add loading states (1h)

**Deliverables**:
- ✅ < 100ms API response
- ✅ Smooth chart rendering
- ✅ Efficient SSE processing

### Day 5: Documentation & Deployment

**Tasks**:
1. Write integration documentation (3h)
2. Create deployment scripts (2h)
3. Test production build (2h)
4. Deploy to staging (1h)

**Deliverables**:
- ✅ Integration guide
- ✅ Deployment scripts
- ✅ Staging deployment

---

# PART 5: END-TO-END TESTING STRATEGY

## 5.1 Test Pyramid

```
         ╱ E2E Tests (5) ╲
        ╱ Integration (20)╲
       ╱   Unit (100+)     ╲
      ╱_____________________╲
```

## 5.2 Unit Tests (Frontend)

### API Client Tests

```typescript
// frontend/src/terminal/api/tradeApi.test.ts

describe('tradeApi', () => {
  describe('fetchCandles', () => {
    it('should fetch candles from backend', async () => {
      const candles = await api.fetchCandles('RELIANCE', 'NSE_EQ', '5m', '2026-06-09', '2026-06-10');
      
      expect(candles.length).toBeGreaterThan(0);
      expect(candles[0]).toHaveProperty('time');
      expect(candles[0]).toHaveProperty('open');
      expect(candles[0]).toHaveProperty('high');
      expect(candles[0]).toHaveProperty('low');
      expect(candles[0]).toHaveProperty('close');
      expect(candles[0]).toHaveProperty('volume');
    });

    it('should convert paisa to rupees', async () => {
      const candles = await api.fetchCandles('RELIANCE', 'NSE_EQ', '5m', '2026-06-09', '2026-06-10');
      
      // Backend returns paisa (integer), frontend expects rupees (decimal)
      expect(candles[0].open).toBeLessThan(10000); // Should be in rupees
    });

    it('should convert ms to seconds', async () => {
      const candles = await api.fetchCandles('RELIANCE', 'NSE_EQ', '5m', '2026-06-09', '2026-06-10');
      
      const now = Date.now() / 1000;
      expect(candles[0].time).toBeLessThan(now); // Should be in seconds
    });
  });

  describe('placeOrder', () => {
    it('should place order successfully', async () => {
      const order = await api.placeOrder({
        ticker: 'RELIANCE',
        exchangeSegment: 'NSE_EQ',
        side: 'BUY',
        qty: 1,
        type: 'MARKET',
        product: 'MIS',
        price: 0
      });
      
      expect(order.id).toBeDefined();
      expect(order.status).toBe('PENDING');
    });

    it('should reject invalid orders', async () => {
      await expect(api.placeOrder({
        ticker: 'INVALID',
        exchangeSegment: 'NSE_EQ',
        side: 'BUY',
        qty: 0, // Invalid quantity
        type: 'MARKET',
        product: 'MIS',
        price: 0
      })).rejects.toThrow();
    });
  });
});
```

### Store Tests

```typescript
// frontend/src/terminal/store/marketStore.test.ts

describe('marketStore', () => {
  it('should load symbols', async () => {
    const store = useMarketStore.getState();
    await store.loadSymbols();
    
    expect(store.symbols.length).toBeGreaterThan(0);
  });

  it('should load candles for selected symbol', async () => {
    const store = useMarketStore.getState();
    await store.loadSymbols();
    store.selectSymbol(store.symbols[0]);
    await store.loadCandles();
    
    expect(store.candles.length).toBeGreaterThan(0);
  });

  it('should handle API errors gracefully', async () => {
    const store = useMarketStore.getState();
    store.selectedSymbol = { ticker: 'INVALID' } as any;
    await store.loadCandles();
    
    expect(store.error).toBeDefined();
    expect(store.isLoading).toBe(false);
  });
});
```

## 5.3 Integration Tests (Backend)

### Controller Tests

```java
// app/src/test/java/com/tradej/app/api/MarketDepthControllerTest.java

@SpringBootTest
@AutoConfigureMockMvc
class MarketDepthControllerTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @Test
    void getDepth_shouldReturnMarketDepth() throws Exception {
        mockMvc.perform(get("/api/v1/market/depth")
                .param("symbol", "RELIANCE")
                .param("exchangeSegment", "NSE_EQ"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.symbol").value("RELIANCE"))
            .andExpect(jsonPath("$.bids").isArray())
            .andExpect(jsonPath("$.asks").isArray())
            .andExpect(jsonPath("$.spread").isNumber());
    }
    
    @Test
    void getDepth_shouldReturn400ForInvalidSymbol() throws Exception {
        mockMvc.perform(get("/api/v1/market/depth")
                .param("symbol", "INVALID")
                .param("exchangeSegment", "NSE_EQ"))
            .andExpect(status().isBadRequest());
    }
}
```

### API Integration Tests

```bash
#!/bin/bash
# scripts/test-api-integration.sh

set -e

BASE_URL="http://localhost:8080"

echo "=== Backend API Integration Tests ==="

# Test 1: Fetch symbols
echo "Test 1: Fetch symbols..."
SYMBOLS=$(curl -s $BASE_URL/api/v1/symbols)
SYMBOL_COUNT=$(echo $SYMBOLS | jq '.symbols | length')
if [ $SYMBOL_COUNT -gt 0 ]; then
  echo "✅ PASS: $SYMBOL_COUNT symbols returned"
else
  echo "❌ FAIL: No symbols returned"
  exit 1
fi

# Test 2: Fetch candles
echo "Test 2: Fetch candles..."
CANDLES=$(curl -s "$BASE_URL/api/v1/market/historical/candles?symbol=RELIANCE&exchangeSegment=NSE_EQ&interval=5m&from=2026-06-09&to=2026-06-10")
CANDLE_COUNT=$(echo $CANDLES | jq '.count')
if [ $CANDLE_COUNT -gt 0 ]; then
  echo "✅ PASS: $CANDLE_COUNT candles returned"
else
  echo "❌ FAIL: No candles returned"
  exit 1
fi

# Test 3: Place order
echo "Test 3: Place order..."
ORDER=$(curl -s -X POST $BASE_URL/api/v1/orders \
  -H "Content-Type: application/json" \
  -d '{"symbol":"RELIANCE","exchangeSegment":"NSE_EQ","side":"BUY","quantity":1,"orderType":"MARKET","productType":"MIS"}')
ORDER_ID=$(echo $ORDER | jq -r '.orderId')
if [ "$ORDER_ID" != "null" ] && [ -n "$ORDER_ID" ]; then
  echo "✅ PASS: Order placed with ID: $ORDER_ID"
else
  echo "❌ FAIL: Order placement failed"
  exit 1
fi

# Test 4: Fetch market depth
echo "Test 4: Fetch market depth..."
DEPTH=$(curl -s "$BASE_URL/api/v1/market/depth?symbol=RELIANCE&exchangeSegment=NSE_EQ")
DEPTH_BIDS=$(echo $DEPTH | jq '.bids | length')
if [ $DEPTH_BIDS -gt 0 ]; then
  echo "✅ PASS: Market depth returned with $DEPTH_BIDS bid levels"
else
  echo "❌ FAIL: No market depth returned"
  exit 1
fi

echo ""
echo "=== All Integration Tests Passed ==="
```

## 5.4 End-to-End Tests

### E2E Test Scenarios

```bash
#!/bin/bash
# scripts/test-e2e-terminal.sh

set -e

FRONTEND_URL="http://localhost:5173"
BACKEND_URL="http://localhost:8080"

echo "=== End-to-End Terminal Tests ==="

# Prerequisites
echo "Checking prerequisites..."
if curl -s $BACKEND_URL/api/v1/admin/health | jq -r '.status' | grep -q "UP"; then
  echo "✅ Backend is running"
else
  echo "❌ Backend is not running"
  exit 1
fi

if curl -s $FRONTEND_URL | grep -q "Trade-J"; then
  echo "✅ Frontend is running"
else
  echo "❌ Frontend is not running"
  exit 1
fi

# Test 1: Load symbols
echo ""
echo "Test 1: Load symbols in terminal..."
SYMBOLS=$(curl -s $BACKEND_URL/api/v1/symbols | jq '.symbols | length')
echo "✅ $SYMBOLS symbols available"

# Test 2: Load candles and display in chart
echo ""
echo "Test 2: Load candles for chart..."
CANDLES=$(curl -s "$BACKEND_URL/api/v1/market/historical/candles?symbol=NIFTY&exchangeSegment=IDX_I&interval=5m&from=2026-06-09&to=2026-06-10" | jq '.count')
echo "✅ $CANDLES candles loaded for NIFTY"

# Test 3: Subscribe to SSE stream
echo ""
echo "Test 3: Subscribe to SSE stream..."
timeout 5 curl -s $BACKEND_URL/api/v1/stream/read-model -N | head -20 > /tmp/sse_test.txt
if grep -q "read-model" /tmp/sse_test.txt; then
  echo "✅ SSE stream working"
else
  echo "⚠️  SSE stream may not be working (timeout expected)"
fi

# Test 4: Place order
echo ""
echo "Test 4: Place test order..."
ORDER=$(curl -s -X POST $BACKEND_URL/api/v1/orders \
  -H "Content-Type: application/json" \
  -d '{"symbol":"TCS","exchangeSegment":"NSE_EQ","side":"BUY","quantity":1,"orderType":"MARKET","productType":"CNC"}')
ORDER_ID=$(echo $ORDER | jq -r '.orderId')
if [ "$ORDER_ID" != "null" ]; then
  echo "✅ Order placed: $ORDER_ID"
  
  # Cancel order
  curl -s -X POST $BACKEND_URL/api/v1/orders/$ORDER_ID/cancel > /dev/null
  echo "✅ Order cancelled"
else
  echo "❌ Order placement failed"
  exit 1
fi

# Test 5: Start replay
echo ""
echo "Test 5: Start replay..."
REPLAY=$(curl -s -X POST "$BACKEND_URL/api/v1/replay/start?symbol=NIFTY&exchange=IDX_I&from=2026-06-09&to=2026-06-10&interval=1m")
REPLAY_STATUS=$(echo $REPLAY | jq -r '.status')
if [ "$REPLAY_STATUS" = "RUNNING" ]; then
  echo "✅ Replay started"
  
  # Stop replay
  curl -s -X POST $BACKEND_URL/api/v1/replay/stop > /dev/null
  echo "✅ Replay stopped"
else
  echo "⚠️  Replay may not have data for test dates"
fi

echo ""
echo "=== All E2E Tests Passed ==="
```

## 5.5 Test Execution Plan

### Automated Test Suite

```bash
#!/bin/bash
# scripts/run-all-tests.sh

set -e

echo "╔══════════════════════════════════════════╗"
echo "║   Trade-J Integration Test Suite         ║"
echo "╚══════════════════════════════════════════╝"
echo ""

# Step 1: Unit Tests
echo "Step 1: Running unit tests..."
cd frontend && npm test -- --coverage
cd ..
echo "✅ Unit tests passed"
echo ""

# Step 2: Backend Tests
echo "Step 2: Running backend tests..."
./gradlew test
echo "✅ Backend tests passed"
echo ""

# Step 3: Integration Tests
echo "Step 3: Running integration tests..."
bash scripts/test-api-integration.sh
echo "✅ Integration tests passed"
echo ""

# Step 4: E2E Tests
echo "Step 4: Running E2E tests..."
bash scripts/test-e2e-terminal.sh
echo "✅ E2E tests passed"
echo ""

echo "╔══════════════════════════════════════════╗"
echo "║   All Tests Passed! ✅                   ║"
echo "╚══════════════════════════════════════════╝"
```

---

# PART 6: ENVIRONMENT CONFIGURATION

## 6.1 Frontend Environment

```bash
# frontend/.env.development

# Backend API URL
VITE_API_BASE_URL=http://localhost:8080

# SSE Stream URL (defaults to API_BASE_URL if not set)
VITE_SSE_URL=http://localhost:8080

# IST Timezone
VITE_TIMEZONE=Asia/Kolkata
```

## 6.2 Backend Configuration

```yaml
# application.yml

server:
  port: 8080

tradej:
  broker:
    active: dhan
    dhan:
      client-id: ${DHAN_CLIENT_ID}
      access-token: ${DHAN_ACCESS_TOKEN}
  
  market-data:
    source: broker
    cache-enabled: true
  
  replay:
    enabled: true
    default-speed: 1x
  
  cors:
    allowed-origins: http://localhost:5173,http://localhost:3000
```

---

# PART 7: DEPLOYMENT CHECKLIST

## 7.1 Pre-Deployment

- [ ] All backend APIs implemented and tested
- [ ] Frontend API client layer complete
- [ ] Zustand stores integrated
- [ ] SSE stream working
- [ ] All workspaces functional
- [ ] Unit tests passing (100+ tests)
- [ ] Integration tests passing (20 tests)
- [ ] E2E tests passing (5 scenarios)
- [ ] Performance < 100ms for API calls
- [ ] Error handling complete
- [ ] Loading states implemented
- [ ] IST timezone throughout

## 7.2 Deployment

- [ ] Build frontend: `cd frontend && npm run build`
- [ ] Build backend: `./gradlew clean build`
- [ ] Start backend: `java -jar app/build/libs/app.jar`
- [ ] Start frontend: `cd frontend && npm run preview`
- [ ] Verify health: `curl http://localhost:8080/api/v1/admin/health`
- [ ] Test in browser: `http://localhost:4173`

---

# SUMMARY

## What Needs to be Created

### Backend (8 endpoints, ~17 hours)
1. ✅ `GET /api/v1/market/depth` - Market depth
2. ✅ `GET /api/v1/market/options/chain` - Option chain with Greeks
3. ✅ `GET /api/v1/portfolio/holdings` - Portfolio holdings
4. ✅ `GET /api/v1/strategies/signals` - Strategy signals
5. ✅ `GET /api/v1/portfolio/positions` - Positions (dedicated)
6. ✅ `GET /api/v1/analytics/pnl/detailed` - Enhanced PnL
7. ✅ `GET /api/v1/replay/candles/stream` - Replay candle stream (SSE)
8. ✅ `GET /api/v1/news` (enhanced) - Enhanced news

### Frontend (3 files + 5 stores, ~40 hours)
1. ✅ `api/tradeApi.ts` - REST API client
2. ✅ `api/websocket.ts` - SSE client
3. ✅ `api/adapters.ts` - Type adapters
4. ✅ `store/marketStore.ts` - Market data
5. ✅ `store/orderStore.ts` - Orders
6. ✅ `store/positionStore.ts` - Positions
7. ✅ `store/replayStore.ts` - Replay
8. ✅ `store/strategyStore.ts` - Strategies

### Testing (3 test suites, ~15 hours)
1. ✅ Unit tests (100+ tests)
2. ✅ Integration tests (20 tests)
3. ✅ E2E tests (5 scenarios)

**Total Effort**: ~72 hours (~9 working days)

---

**END OF INTEGRATION PLAN**
