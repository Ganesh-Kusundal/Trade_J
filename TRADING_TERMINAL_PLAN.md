# Trade-J Trading Terminal Implementation Plan

**Phase**: Phase 1 - Market Terminal (Read-Only)  
**Status**: Planning Complete, Implementation Started  
**Date**: 2026-06-10

---

## 🎯 Overview

Build a professional Trading Terminal UI that provides complete observability into the Trade-J platform. Initially read-only (no order placement), focusing on market data visualization, strategy monitoring, and replay capabilities.

---

## 📊 Screen Architecture

### Screen 1: Market View ✅ (In Progress)

**Purpose**: Real-time/historical market data visualization

**Layout**:
```
┌─────────────────────────────────────────────────┐
│ [Symbol Search] [Timeframe] [Broker] [Replay]   │
├─────────────────────────────────────────────────┤
│                                                  │
│           TradingView Lightweight Charts         │
│           - Candlestick                          │
│           - Volume                               │
│           - EMA 20/50                            │
│           - VWAP                                 │
│           - HalfTrend                            │
│                                                  │
│           Crosshair, Zoom, Pan                   │
│                                                  │
└─────────────────────────────────────────────────┘
```

**Components**:
- `MarketChart.tsx` - Main chart with TradingView Lightweight Charts
- `SymbolSelector.tsx` - Symbol search and selection
- `TimeframeSelector.tsx` - Timeframe buttons (1m, 5m, 15m, 1h, 1D)
- `BrokerSelector.tsx` - Broker selection (Dhan, Upstox, ICICI)
- `ReplayControls.tsx` - Play/Pause/Speed controls

**Data Sources**:
1. Historical: REST API `/api/v1/market/historical/candles`
2. Live: WebSocket `/ws/gateway`
3. Replay: Replay engine stream

**Status**: 
- ✅ Type system created (`types/market.ts`)
- ✅ Store created (`store/marketViewStore.ts`)
- ⚠️ Chart component has TypeScript issues with v5 API
- ✅ Data adapters planned

---

### Screen 2: Strategy View

**Purpose**: See why strategy acted

**Layout**:
```
┌─────────────────────────────┬──────────────────┐
│                             │  Signal Timeline │
│     Chart with Signals      │                  │
│     - Candles               │  09:35 BUY       │
│     - HalfTrend             │  09:42 EXIT      │
│     - Entry/Exit markers    │  10:15 BUY       │
│                             │                  │
├─────────────────────────────┴──────────────────┤
│           Strategy Logs                         │
│           - Signal generated                    │
│           - Risk check passed                   │
│           - Order sent                          │
└─────────────────────────────────────────────────┘
```

**Components**:
- `StrategyChart.tsx` - Chart with signal markers
- `SignalTimeline.tsx` - Vertical timeline of signals
- `StrategyLogs.tsx` - Log viewer for strategy decisions

**Data Sources**:
- Strategy signals from replay engine
- Strategy logs from backend

---

### Screen 3: Scanner View

**Purpose**: Market scanner with live updates

**Layout**:
```
┌─────────────────────────────────────────────────┐
│ [Universe Selector] [Filters] [Refresh]         │
├─────────────────────────────────────────────────┤
│ Symbol  │ Price │ Volume │ Spike │ HalfTrend    │
├─────────────────────────────────────────────────┤
│ RELIANCE│ 2850  │ 1.2M   │ +45%  │ UPTREND      │
│ TCS     │ 3520  │ 800K   │ +12%  │ DOWNTREND    │
│ INFY    │ 1480  │ 600K   │ +28%  │ UPTREND      │
│ ...     │ ...   │ ...    │ ...   │ ...          │
└─────────────────────────────────────────────────┘
```

**Components**:
- `ScannerTable.tsx` - Live updating table (TanStack Table)
- `ScannerFilters.tsx` - Volume spike, trend filters
- `UniverseSelector.tsx` - NIFTY50, NIFTY100, etc.

**Data Sources**:
- Scanner results from backend
- Live updates via WebSocket

---

### Screen 4: Options View ⭐ (Most Important)

**Purpose**: Options chain with Greeks analysis

**Layout**:
```
┌─────────────────────────────────────────────────┐
│ [Underlying] [Expiry] [Filters]                 │
├────────────────┬─────────┬──────────────────────┤
│    CALLS       │  SPOT   │       PUTS           │
│ Strike │ OI   │  Price  │ OI │ Strike           │
├────────────────┼─────────┼──────────────────────┤
│ 22000  │ 5.2M  │  21850  │ 3.1M│ 21800           │
│ 22100  │ 4.8M  │         │ 3.5M│ 21700           │
│ 22200  │ 6.1M  │         │ 4.2M│ 21600           │
└────────────────┴─────────┴──────────────────────┘
```

**Components**:
- `OptionsChain.tsx` - Call/Put chain table
- `GreeksPanel.tsx` - Delta, Gamma, Theta, Vega
- `OptionsFilters.tsx` - Top OI, Top Volume filters
- `OptionsChart.tsx` - OI change chart

**Data Sources**:
- Options chain from broker API
- Greeks calculated backend or frontend

---

### Screen 5: Replay View ⭐ (Architecture Showcase)

**Purpose**: Replay historical market data with strategy

**Layout**:
```
┌─────────────────────────────────────────────────┐
│ ▶ Play  ⏸ Pause  ⏪ Back  ⏩ Forward            │
│ Speed: [1x] [5x] [10x] [50x] [100x]            │
│ [────────── Progress Bar ──────────] 45%        │
├─────────────────────────────────────────────────┤
│                                                  │
│        Chart (updates like live market)          │
│        Strategy signals appear                   │
│        Orders appear                             │
│        PnL updates                               │
│                                                  │
└─────────────────────────────────────────────────┘
```

**Components**:
- `ReplayControls.tsx` - Play/Pause/Speed/Seek
- `ReplayChart.tsx` - Same as MarketChart but with replay stream
- `ReplayProgressBar.tsx` - Progress and time display
- `ReplayStats.tsx` - Events processed, elapsed time

**Data Sources**:
- Replay engine (deterministic)
- Same data as live but controlled timing

---

### Screen 6: Execution Monitor

**Purpose**: Track order lifecycle

**Layout**:
```
┌─────────────────────────────────────────────────┐
│ Signal → Risk → Order → Position                │
├─────────────────────────────────────────────────┤
│ Timeline:                                        │
│ 09:35:12  Signal Generated (HalfTrend BUY)      │
│ 09:35:12  Risk Check Passed                     │
│ 09:35:13  Order Sent (Dhan)                     │
│ 09:35:14  Order Filled @ 21850                  │
│ 09:35:14  Position Open (Qty: 50)               │
├─────────────────────────────────────────────────┤
│ Active Orders:                                   │
│ Order ID │ Status │ Symbol │ Qty │ Price        │
├─────────────────────────────────────────────────┤
│ Positions:                                       │
│ Symbol │ Qty │ Avg Price │ Current │ PnL        │
└─────────────────────────────────────────────────┘
```

**Components**:
- `ExecutionTimeline.tsx` - Order lifecycle visualization
- `ActiveOrdersTable.tsx` - Pending orders
- `PositionsTable.tsx` - Open positions with PnL

**Data Sources**:
- Order events from execution engine
- Position updates from OMS

---

### Screen 7: Event Flow Monitor

**Purpose**: Visualize event-driven architecture

**Layout**:
```
┌─────────────────────────────────────────────────┐
│ Event Flow Visualization (React Flow)           │
│                                                  │
│ MarketTick ──→ Strategy ──→ Signal              │
│                                    ↓             │
│ Position ←── Order ←── Risk                     │
│                                                  │
│ Like Kafka UI but for Trade-J                   │
└─────────────────────────────────────────────────┘
```

**Components**:
- `EventFlowGraph.tsx` - React Flow visualization
- `EventLog.tsx` - Real-time event log
- `EventStats.tsx` - Events/sec, latency

**Data Sources**:
- Event stream from backend
- Disruptor metrics

---

## 🛠️ Technology Stack

### Core
- **React 19.2.6** - UI framework
- **TypeScript** - Type safety
- **Vite** - Build tool
- **Tailwind CSS** - Styling

### State Management
- **Zustand** - Global state (already installed)
- **TanStack Query** - Server state (to install)

### UI Libraries
- **TradingView Lightweight Charts** - Charts (already installed v5.2.0)
- **TanStack Table** - Data tables (to install)
- **React Flow** - Event flow graphs (to install)
- **Lucide React** - Icons (already installed)

### Data Flow
```
REST API ──→ Historical Data
     ↓
WebSocket ──→ Live Updates
     ↓
Replay Stream ──→ Replay Events
     ↓
Normalized to MarketEvent interface
```

---

## 📋 Implementation Priority

### Priority 1: Market View (Current)
- [x] Type system
- [x] Zustand store
- [ ] Fix TradingView chart TypeScript issues
- [ ] Symbol selector with search
- [ ] Timeframe selector
- [ ] Indicator overlays (EMA, VWAP, HalfTrend)
- [ ] Volume chart
- [ ] WebSocket integration
- [ ] Replay integration

**Estimated Time**: 2-3 days

### Priority 2: Options View
- Options chain table
- Greeks display
- OI change visualization
- Filters (Top OI, Top Volume)

**Estimated Time**: 2-3 days

### Priority 3: Scanner View
- Live scanner table
- Filters
- Click to open chart

**Estimated Time**: 1-2 days

### Priority 4: Replay View
- Replay controls
- Progress bar
- Speed selection
- Integration with replay engine

**Estimated Time**: 2 days

### Priority 5: Strategy View
- Chart with signal markers
- Signal timeline
- Strategy logs

**Estimated Time**: 2 days

### Priority 6: Execution Monitor
- Order lifecycle timeline
- Active orders table
- Positions table with PnL

**Estimated Time**: 2 days

### Priority 7: Event Flow Monitor
- React Flow graph
- Event log
- Event statistics

**Estimated Time**: 2 days

**Total Estimated Time**: 13-17 days (~3 weeks)

---

## 🚀 Getting Started

### Current Status

**Completed**:
- ✅ Frontend project setup (Vite + React + TypeScript)
- ✅ TradingView Lightweight Charts installed (v5.2.0)
- ✅ Zustand state management installed
- ✅ Type system created
- ✅ Zustand store with data adapters
- ✅ WebSocket proxy configured in Vite

**In Progress**:
- ⚠️ MarketChart component (TypeScript issues with v5 API)

**Next Steps**:
1. Fix MarketChart TypeScript issues
2. Test with mock data
3. Connect to backend REST API
4. Add WebSocket live updates
5. Integrate with replay engine

### Quick Start

```bash
cd /Users/apple/Downloads/Trade_J/frontend

# Install missing dependencies
npm install @tanstack/react-query @tanstack/react-table reactflow

# Start development server
npm run dev

# Open http://localhost:5173
```

---

## 📊 Data Architecture

### Unified MarketEvent Interface

All data sources (live, replay, historical) normalize to:

```typescript
interface MarketEvent {
  symbol: string;
  exchangeSegment: string;
  timestamp: number; // Unix seconds
  open: number;
  high: number;
  low: number;
  close: number;
  volume: number;
  vwap?: number;
  source: 'live' | 'replay' | 'historical';
}
```

This allows charts to work identically regardless of data source.

### State Management

```
Zustand Store
    ↓
┌─────────────────┐
│ MarketViewStore │
└─────────────────┘
    ↓
┌──────────┬──────────┬──────────┐
│ Historical│   Live   │  Replay  │
│  Adapter  │  Adapter │  Adapter │
└──────────┴──────────┴──────────┘
    ↓           ↓          ↓
  REST API   WebSocket  Replay Engine
```

---

## 🎨 Design Principles

1. **Dark Theme First** - Professional trading terminal aesthetic
2. **Real-Time Updates** - No page refreshes, WebSocket-driven
3. **Keyboard Shortcuts** - Power user friendly
4. **Responsive** - Works on different screen sizes
5. **Performance** - 60fps charts, minimal re-renders
6. **Type Safety** - Full TypeScript coverage

---

## 📝 Backend API Requirements

### Existing APIs (Already Available)

```
GET  /api/v1/market/historical/candles
GET  /api/v1/market/ltp
GET  /api/v1/market/options/chain
GET  /api/v1/scanner/results
GET  /api/v1/replay/events
WS   /ws/gateway
```

### New APIs Needed (For Terminal)

```
GET  /api/v1/market/symbols              # Symbol search
GET  /api/v1/strategies/signals          # Strategy signals
GET  /api/v1/execution/orders            # Active orders
GET  /api/v1/execution/positions         # Open positions
GET  /api/v1/events/stream               # Event stream (SSE)
GET  /api/v1/health/brokers              # Broker health
```

---

## 🔄 Development Workflow

### Daily Process

1. **Morning**: Check backend is running
2. **Develop**: Build UI components with mock data
3. **Integrate**: Connect to real backend APIs
4. **Test**: Verify with replay engine
5. **Commit**: Push to feature branch

### Testing Strategy

1. **Unit Tests**: Component rendering
2. **Integration Tests**: API calls
3. **E2E Tests**: Full user workflows
4. **Replay Tests**: Deterministic validation

---

## 📈 Success Metrics

- [ ] Market View displays live candles in < 100ms
- [ ] Options chain loads in < 500ms
- [ ] Scanner updates in real-time (< 1s latency)
- [ ] Replay runs deterministically (identical results)
- [ ] All charts render at 60fps
- [ ] Zero TypeScript errors
- [ ] Full type coverage

---

## 🚨 Known Issues

1. **TradingView v5 TypeScript API**: Changes from v4 to v5 require adaptation
   - VolumeSeries → HistogramSeries
   - Time type changes (UnixTimestamp vs UTCTimestamp)
   - Solution: Use type assertions or adapt to v5 API

2. **Backend CORS**: May need to enable CORS for localhost:5173
   - Solution: Add CORS configuration in Spring Boot

3. **WebSocket Reconnection**: Need to handle disconnects
   - Solution: Implement reconnection logic with exponential backoff

---

## 📅 Timeline

**Week 1**: Market View + Options View  
**Week 2**: Scanner View + Replay View  
**Week 3**: Strategy View + Execution Monitor + Event Flow  

**Total**: 3 weeks to complete Phase 1

---

## 🎯 Phase 2 & 3 (Future)

### Phase 2: Live Observability
- Broker health dashboard
- Data health metrics (events/sec, ticks/sec)
- Replay health monitoring
- Alert system

### Phase 3: Strategy Research UI
- Strategy runner interface
- Parameter optimization
- Backtest results visualization
- Walk-forward analysis
- Monte Carlo simulation

---

**Next Action**: Fix MarketChart TypeScript issues and complete Market View implementation.
