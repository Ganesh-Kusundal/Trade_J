import type { MarketState } from "../domain/instrument";

export interface TickEvent {
  type: "TICK";
  symbol: string;
  exchange: string;
  ltp: number;
  change: number;
  changePercent: number;
  timestamp: number;
}

export interface DepthLevel {
  price: number;
  quantity: number;
  orders: number;
}

export interface DepthEvent {
  type: "DEPTH";
  symbol: string;
  exchange: string;
  bids: DepthLevel[];
  asks: DepthLevel[];
  ltp: number;
  spread: number;
  timestamp: number;
}

export interface TradeTickData {
  time: number;
  price: number;
  quantity: number;
  side: "BUY" | "SELL";
}

export interface TradeEvent {
  type: "TRADE";
  symbol: string;
  exchange: string;
  trades: TradeTickData[];
}

export interface OHLCVBarData {
  time: number;
  open: number;
  high: number;
  low: number;
  close: number;
  volume: number;
}

export interface CandleEvent {
  type: "CANDLE";
  symbol: string;
  exchange: string;
  candles: OHLCVBarData[];
  isPartial?: boolean;
}

export interface MarketStateEvent {
  type: "MARKET_STATE";
  state: MarketState;
  exchange: string;
  dataSource: string;
}

export interface BrokerStatusEvent {
  type: "BROKER_STATUS";
  connected: boolean;
  websocketConnected: boolean;
  broker: string;
}

export interface PnlSnapshot {
  realizedPnlPaisa: number;
  unrealizedPnlPaisa: number;
  netExposurePaisa: number;
}

export interface PnlEvent {
  type: "PNL_UPDATE";
  symbol?: string;
  realizedPnlPaisa: number;
  unrealizedPnlPaisa: number;
  netExposurePaisa: number;
  timestamp: number;
}

export interface FeedHealthEvent {
  type: "FEED_HEALTH";
  health: "healthy" | "delayed" | "stale" | "disconnected";
}

export interface DataModeEvent {
  type: "DATA_MODE";
  mode: "LIVE" | "SIMULATION" | "HISTORICAL" | "PAPER" | "REPLAY";
  marketOpen: boolean;
  brokerConnected: boolean;
}

export interface ReplayControlState {
  sessionId: string;
  state: "PLAYING" | "PAUSED" | "STOPPED" | "UNKNOWN";
  symbol: string;
  interval: string;
  fromMs: number;
  toMs: number;
  speed: number;
}

export interface ReplayControlEvent {
  type: "REPLAY_CONTROL";
  state: ReplayControlState;
}

export type MarketEvent =
  | TickEvent
  | DepthEvent
  | TradeEvent
  | CandleEvent
  | MarketStateEvent
  | BrokerStatusEvent
  | FeedHealthEvent
  | DataModeEvent
  | ReplayControlEvent
  | PnlEvent;
