/**
 * Typed contracts for the SSE read-model stream.
 *
 * These interfaces mirror the backend `ReadModelStore.ReadModelSnapshot`
 * and its nested view records, replacing the previous `unknown[]` / `Record<string, unknown>`
 * types with compile-time-safe structures.
 */

/** Backend OrderView — one entry per live order. */
export interface ReadModelOrder {
  orderId: string;
  symbol: string;
  status: string;
  quantity: number;
  side: string;
  pricePaisa: number;
  orderType: string;
  filledQuantity: number;
}

/** Backend PositionView — one entry per symbol with open position. */
export interface ReadModelPosition {
  symbol: string;
  netQuantity: number;
  avgPricePaisa: number;
}

/** Backend TickView — latest tick per symbol. */
export interface ReadModelTick {
  symbol: string;
  ltpPaisa: number;
  exchangeTimestampMs: number;
}

/** Backend DepthLevel inside a DepthView. */
export interface ReadModelDepthLevel {
  pricePaisa: number;
  quantity: number;
  orders: number;
}

/** Backend DepthView — latest depth snapshot per symbol. */
export interface ReadModelDepth {
  symbol: string;
  bids: ReadModelDepthLevel[];
  asks: ReadModelDepthLevel[];
  exchangeTimestampMs: number;
}

/** Backend CandleView — latest candle per symbol+interval. */
export interface ReadModelCandle {
  symbol: string;
  interval: string;
  closePaisa: number;
  volume: number;
  closed: boolean;
}

/** Backend SignalView — one entry per generated signal. */
export interface ReadModelSignal {
  signalId: string;
  symbol: string;
  side: string;
  strategy: string;
}

/** Backend PnlView — aggregate PnL. */
export interface ReadModelPnl {
  realizedPnlPaisa: number;
  unrealizedPnlPaisa: number;
  netExposurePaisa: number;
}

/** Backend ScanHitView — one hit within a scan result. */
export interface ReadModelScanHit {
  symbol: string;
  exchangeSegment: string;
  score: number;
  reasons: string[];
}

/** Backend ScanResultView — latest scan run. */
export interface ReadModelScanResult {
  runId: string;
  profileId: string;
  hitCount: number;
  hits: ReadModelScanHit[];
}

/** Full snapshot pushed via SSE `read-model` events. */
export interface ReadModelSnapshot {
  version: number;
  orders: ReadModelOrder[];
  positions: ReadModelPosition[];
  ticks: ReadModelTick[];
  depths: ReadModelDepth[];
  candles: ReadModelCandle[];
  signals: ReadModelSignal[];
  pnl: ReadModelPnl;
  latestScan: ReadModelScanResult | null;
}
