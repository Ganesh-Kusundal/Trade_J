# Trade-J Institutional Trading Platform — Implementation Roadmap

> **Date**: 2026-06-05  
> **Status**: Phase 1 — Foundation  
> **Principle**: Build a tradeable core first. UI comes last.

---

## Current State Assessment

### What Exists (Strong Foundation)
- ✅ Java 21 + Spring Boot 3.4.13 multi-module Gradle project (24 subprojects)
- ✅ Domain model: Instrument, Candle, Order, Position, Trade (Java records)
- ✅ Event-driven architecture: 30+ domain events, Disruptor event bus
- ✅ OMS: Event-sourced OrderStateMachine with full lifecycle
- ✅ Broker abstraction: Ports (MarketDataProvider, OrderCommand, etc.)
- ✅ Dhan + Upstox broker adapters
- ✅ Strategy plugin system: GraphStrategyPlugin with sandboxed execution
- ✅ MatchingEngine with slippage model for simulation
- ✅ DuckDB persistence layer
- ✅ React 19 frontend shell
- ✅ WebSocket gateway
- ✅ CLI tool

### What's Missing (Gaps to Fill)
- ❌ Unified execution engine (live/replay/backtest share more code)
- ❌ Feature engine with incremental indicator calculations
- ❌ Risk engine (position sizing, capital limits, circuit breakers)
- ❌ Portfolio engine (multi-strategy, multi-account)
- ❌ Replay engine (deterministic historical playback)
- ❌ Backtest engine (parameter optimization, walk-forward)
- ❌ Instrument master / symbol mapping
- ❌ Corporate actions handling
- ❌ Frontend trading terminal (watchlist, charts, OMS panel)
- ❌ Comprehensive test coverage for new modules

---

## Phase 1: Tradeable Core (Weeks 1-4)
**Goal**: One strategy → One broker → One account → Live trading

### 1.1 Unified Execution Engine
- Create `UnifiedExecutionEngine` that routes through same pipeline regardless of mode
- Implement `IDataSource` interface: `LiveDataSource`, `HistoricalDataSource`, `ReplayDataSource`
- Wire `RuntimeModeHolder` to switch data sources without touching strategy code

### 1.2 Feature Engine
- Create `FeatureEngine` with incremental indicator calculations
- Implement: EMA, SMA, RSI, ATR, Supertrend, HalfTrend, VWAP, Bollinger Bands
- Multi-timeframe support (tick → 1m → 5m → 15m → 1h → daily)
- Market breadth, relative strength, order flow features

### 1.3 Risk Engine
- Create `RiskEngine` with mandatory checks before every order
- Position sizing (fixed fractional, ATR-based, Kelly criterion)
- Capital allocation per strategy/instrument
- Daily loss limits, max drawdown circuit breakers
- Exposure limits (gross/net, sector, instrument)

### 1.4 HalfTrend Strategy Implementation
- Implement HalfTrend as `GraphStrategyPlugin`
- Parameters: Amplitude, ATR Period, SL multiplier, TP multiplier
- Generate signals with entry, stop-loss, take-profit

### 1.5 Portfolio Engine
- Track positions, realized/unrealized PnL, drawdown
- Single-strategy → multi-strategy support
- Capital reservation and margin tracking

---

## Phase 2: Replay & Backtesting (Weeks 5-8)
**Goal**: Live = Backtest = Replay with identical strategy code

### 2.1 Replay Engine
- Deterministic historical event playback
- Simulate tick stream, candle stream, order execution
- Slippage and latency modeling
- Event inspection and speed controls

### 2.2 Backtest Engine
- Walk-forward optimization
- Parameter sweep with DuckDB result storage
- Performance analytics: Sharpe, Sortino, max drawdown, win rate
- Equity curve generation

### 2.3 Historical Data Lake
- Parquet storage for ticks and candles
- DuckDB analytical queries
- Instrument master with corporate actions
- Data ingestion from Dhan/Upstox historical APIs

---

## Phase 3: Trading Terminal (Weeks 9-12)
**Goal**: Lightweight Bloomberg-style workstation

### 3.1 Frontend Modules
- **Dashboard**: Portfolio status, daily PnL, exposure, risk
- **Trading Terminal**: Watchlists, orders, positions, trades, executions
- **Charts**: Candles, indicators, drawing tools, strategy overlays
- **Strategy Studio**: Configuration, parameters, performance
- **System Log**: Real-time event feed

### 3.2 Backend APIs
- REST endpoints for all frontend modules
- WebSocket streaming for real-time updates
- CQRS read models for fast queries

---

## Phase 4: Multi-Broker & Advanced Features (Weeks 13-16)
**Goal**: Broker independence + advanced capabilities

### 4.1 Broker Abstraction Hardening
- Complete `IBroker` interface
- Add Zerodha, Angel One adapters
- Broker health monitoring and failover

### 4.2 Advanced Order Types
- Bracket orders, cover orders, GTT orders
- Iceberg orders, TWAP/VWAP execution

### 4.3 Strategy Studio
- Parameter editing without restart
- Strategy switching on-the-fly
- Signal inspection and explainability

### 4.4 Risk Dashboard
- Real-time exposure monitoring
- Margin utilization
- Risk alerts and circuit breaker status

---

## Phase 5: Institutional Features (Weeks 17-24)
**Grade**: Production-grade institutional platform

### 5.1 Multi-Strategy Portfolio
- Strategy correlation analysis
- Capital allocation optimization
- Cross-strategy risk management

### 5.2 Analytics & Reporting
- Trade replay (candle-by-candle)
- Strategy explainability
- Performance attribution
- Regulatory reporting

### 5.3 Observability
- Structured logging (JSON)
- Metrics (Micrometer + Prometheus)
- Distributed tracing
- Alerting

### 5.4 Deployment
- Docker containers
- Kubernetes manifests
- CI/CD pipeline
- Blue-green deployment

---

## Design Principles

1. **Correctness over convenience** — Every order must be traceable
2. **Simplicity over cleverness** — Prefer explicit over implicit
3. **Maintainability over short-term speed** — Clean architecture always
4. **Single source of truth** — Event-sourced where possible
5. **Strategy isolation** — No strategy knows broker, OMS, or UI
6. **Risk is mandatory** — No strategy can bypass risk checks
7. **Unified engine** — Same code path for live/replay/backtest
