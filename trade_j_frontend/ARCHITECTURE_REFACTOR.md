# Trade-J Frontend Architecture Refactoring

## Summary

Replaced a fragmented architecture where every component owned its own polling, state, and data-source logic with a centralized event-bus architecture where a single `MarketDataBus` owns all market data and React hooks subscribe to it.

## Violations Found & Fixed

| # | Violation | Fix |
|---|-----------|-----|
| 1 | **Duplicate Market Data Flows** — WatchlistPanel, MarketOverview, TerminalDataOrchestrator all polled `/api/v1/market/ltp` independently | Extracted polling into `useWatchlist` and `useMarketIndices` hooks. TerminalDataOrchestrator publishes through `MarketDataBus`. |
| 2 | **No Single Owner for DataMode** — App.tsx and TerminalDataOrchestrator both resolved data mode independently | Created `DataModeResolver.ts` (single pure function). Both App and Orchestrator import it. |
| 3 | **No Single Candle Pipeline** — TerminalDataOrchestrator fetched candles directly from REST | Created `CandleAggregator.ts` that wraps `fetchCandles` and publishes `CandleEvent` to the bus. |
| 4 | **Polling During Live Market** — `setInterval(loadLtp, 2000)` and `setInterval(loadDepth, 3000)` in Orchestrator | Extracted simulation polling into `SimulationFeed.ts`. Live mode uses Dhan WebSocket only. |
| 5 | **Symbol Bleed** — WatchlistPanel passed only `symbol` to App (no exchange); App would not clear exchange on instrument change | WatchlistPanel now emits `onSelectSymbol(symbol, exchange)`. App sets both. Bus clears before new data. |
| 6 | **State Duplication** — App.tsx had 20+ `useState` hooks for market data | Replaced with 8 typed hooks (`useLastTick`, `useDepth`, `useTrades`, `useCandles`, `useMarketState`, `useBrokerStatus`, `useFeedHealth`, `useDataMode`). |
| 7 | **No Canonical Contracts** — No shared event types | Created `marketContracts.ts` with 8 typed interfaces and a discriminated `MarketEvent` union. |
| 8 | **Synthetic Depth in OrderBook** — Random depth generated in HISTORICAL mode | Removed `Math.random()` depth generation. OrderBook now displays received snapshot from backend. |

## New Files Created

```
src/api/marketContracts.ts      — 8 canonical event types + MarketEvent union
src/api/MarketDataBus.ts         — Singleton event bus (subscribe/publish/getLast/clear)
src/api/DataModeResolver.ts      — Single pure function for data mode resolution
src/api/SimulationFeed.ts        — Encapsulated REST polling for SIMULATION mode
src/lib/CandleAggregator.ts      — REST candle fetch + CandleEvent publish
src/hooks/useMarketData.ts       — 8 React hooks consuming MarketDataBus
src/hooks/useWatchlist.ts        — Watchlist state + polling hook
src/hooks/useMarketIndices.ts    — Index data polling hook
src/tests/architecture-regression.test.ts — 38 regression tests for new architecture
```

## Files Modified

```
src/api/TerminalDataOrchestrator.ts — Publish to bus instead of callbacks;
                                      use SimulationFeed + CandleAggregator;
                                      use DataModeResolver
src/App.tsx                        — 548→437 lines; removed market data state,
                                      health/session polling, inline mode resolution;
                                      now uses hooks
src/components/WatchlistPanel.tsx   — Props-driven, no state/polling
src/components/MarketOverview.tsx   — Props-driven, no state/polling
src/components/OrderBook.tsx        — No synthetic depth generation
src/tests/orchestrator-integration.test.ts — Updated import for DataMode
```

## Data Flow (After)

```
Backend feeds (WS/REST)
  ↓
TerminalDataOrchestrator
  ├─ CandleAggregator → Bus CandleEvent
  ├─ SimulationFeed → Bus TickEvent / DepthEvent
  ├─ DhanFeedManager → Bus TickEvent / DepthEvent / FeedHealthEvent
  ├─ Session polling → Bus MarketStateEvent
  └─ Health polling → Bus BrokerStatusEvent
        ↓
   MarketDataBus (singleton)
        ↓
   React Hooks (useLastTick, useDepth, ...)
        ↓
   App.tsx (thin orchestrator layer)
        ↓
   Components (pure display, no data logic)
```

## Certification Tests (9/9 passing)

Defined in `src/tests/architecture-certification.test.ts`:

1. **Single Market Data Flow** — No component polls LTP directly
2. **Single Status Owner** — No inline brokerConnected/dataMode in App
3. **Single Mode Resolver** — DataModeResolver.ts exists, no private resolveMode
4. **Single Candle Pipeline** — CandleAggregator exists, no fetchCandles in Orchestrator
5. **No Polling During Live Market** — No setInterval(loadLtp/loadDepth) in Orchestrator
6. **No Symbol Bleed** — WatchlistPanel passes symbol+exchange, App sets both
7. **No State Duplication** — App has no useState for bars/bids/asks/trades/lastPrice
8. **No Invalid Index Values** — MarketOverview validates through sanitizeIndexValue
9. **Canonical Event Contracts** — marketContracts.ts + TICK/DEPTH/TRADE/CANDLE types used
