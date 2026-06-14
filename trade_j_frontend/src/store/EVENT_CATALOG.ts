// ============================================================================
// Event Contract (Frontend Mirror of Backend DomainEvent Catalog)
// ============================================================================
//
// The backend publishes 31 typed events in
// core/src/main/java/com/tradej/core/domain/event/. They are immutable
// records that all implement com.tradej.core.domain.event.DomainEvent,
// which exposes:
//
//   - eventId():          String  — unique per event
//   - timestampMs():      long    — wall clock
//   - timestampMonotonic: long    — for ordering
//   - sequenceId():       long    — for cross-replay determinism
//   - correlationId():    String  — to trace a user action end-to-end
//   - priority():         EventPriority
//   - schemaVersion():    int     — for backward compat
//
// The frontend never sees these records directly. It receives the
// fold of the event stream (the read-model snapshot) over SSE at
// /api/v1/stream/read-model. The snapshot is:
//
//   ReadModelSnapshot {
//     version:      long      // increments on every fold change
//     orders:       Order[]
//     positions:    Position[]
//     ticks:        Tick[]
//     depths:       Depth[]
//     candles:      Candle[]
//     signals:      Signal[]
//     pnl:          PnL
//   }
//
// Each tick/depth/candle/order/position MAY carry an `origin` field.
// The four honoured values are:
//
//   BROKER_LIVE    — came from a real broker feed (Dhan, Upstox, ICICI)
//   REPLAY_VIRTUAL — came from the historical replay engine
//   PAPER_MATCHED  — came from the paper matcher (no real fill)
//   SIMULATED      — came from the backend SimulatedMarketDataProvider
//
// The frontend renders the origin as a colored stripe. Components
// never fabricate data; they only render the snapshot.
//
// Event catalog (31 types, source: core/src/main/java/.../domain/event/):
//   MarketTickEvent           — the canonical tick
//   DepthUpdateEvent          — L2 book update
//   OrderUpdateEvent          — order accepted/modified/cancelled
//   OrderAccepted             — paper or live order accepted
//   OrderModified             — order modified
//   OrderCancelled            — order cancelled
//   OrderRejected             — order rejected
//   OrderFilled               — alias for OrderFullyFilled
//   OrderFullyFilled          — fill to completion
//   OrderPartiallyFilled      — partial fill
//   TradeOpened               — trade lifecycle
//   TradeUpdated              — trade state update
//   TradeClosed               — trade closed
//   TradeExecutionEvent       — trade-level execution
//   PositionUpdateEvent       — position changed
//   PositionMismatch          — reconciliation
//   PnlUpdatedEvent           — realized/unrealized
//   UnrealizedPnLUpdated      — unrealized tick
//   SignalGenerated           — strategy emitted a signal
//   SignalPendingExecution    — awaiting order manager
//   SignalSuppressed          — risk or mode blocked the signal
//   StrategyError             — strategy threw
//   ScanHitProduced           — scanner found a hit
//   ScanResultsPublished      — scan run completed
//   CandleClosed              — candle closed (replay or live)
//   CandleDeveloping          — live in-progress candle
//   GreeksComputed            — option greeks recomputed
//   GammaExposureComputed     — GEX update
//   MaxPainComputed           — max pain updated
//   OptionChainUpdated        — option chain refresh
//   KillSwitchEngaged         — risk kill
//   UnifiedKillSwitchEngaged  — global kill
//   UnifiedKillSwitchDisengaged
//   ReconciliationHaltRequired
//   StreamHealthChanged       — broker feed health
//   BrokerAdapterError        — broker error
//   EventBusBackpressure      — bus back-pressure
//   ReplayTimeChangedEvent    — virtual clock update
//
// Origin-of-truth: backend `core` module. Do NOT duplicate event
// classes on the frontend. The frontend maps the read-model
// projection (see /store/readModelStream.ts) and renders it.

export const EVENT_CATALOG = {
  MARKET: [
    "MarketTickEvent",
    "DepthUpdateEvent",
    "CandleClosed",
    "CandleDeveloping",
    "StreamHealthChanged",
  ],
  ORDERS: [
    "OrderAccepted",
    "OrderModified",
    "OrderCancelled",
    "OrderRejected",
    "OrderFullyFilled",
    "OrderPartiallyFilled",
    "OrderUpdateEvent",
    "OrderFilled",
  ],
  TRADES: [
    "TradeOpened",
    "TradeUpdated",
    "TradeClosed",
    "TradeExecutionEvent",
  ],
  POSITIONS_PNL: [
    "PositionUpdateEvent",
    "PositionMismatch",
    "PnlUpdatedEvent",
    "UnrealizedPnLUpdated",
  ],
  STRATEGIES: [
    "SignalGenerated",
    "SignalPendingExecution",
    "SignalSuppressed",
    "StrategyError",
  ],
  SCANNERS: [
    "ScanHitProduced",
    "ScanResultsPublished",
  ],
  OPTIONS: [
    "GreeksComputed",
    "GammaExposureComputed",
    "MaxPainComputed",
    "OptionChainUpdated",
  ],
  RISK: [
    "KillSwitchEngaged",
    "UnifiedKillSwitchEngaged",
    "UnifiedKillSwitchDisengaged",
    "ReconciliationHaltRequired",
  ],
  REPLAY: [
    "ReplayTimeChangedEvent",
  ],
  INFRASTRUCTURE: [
    "EventBusBackpressure",
    "BrokerAdapterError",
  ],
} as const;

export const TOTAL_EVENT_TYPES = Object.values(EVENT_CATALOG).reduce(
  (acc, list) => acc + list.length,
  0,
);
