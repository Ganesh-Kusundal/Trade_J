export type Mode = "LIVE" | "PAPER" | "REPLAY";
export type DataOrigin = "BROKER_LIVE" | "REPLAY_VIRTUAL" | "PAPER_MATCHED" | "SIMULATED" | "UNKNOWN";

export interface Instrument {
  symbol: string;
  exchange: string;
  segment: string;
  tickSize: number;
  qtyDecimals: number;
  volumeUnit: string;
  currency: string;
}

export interface L2Level {
  price: number;
  quantity: number;
  orders: number;
}

export interface Candle {
  time: number;
  open: number;
  high: number;
  low: number;
  close: number;
  volume: number;
}

export interface Fill {
  time: number;
  symbol: string;
  side: "BUY" | "SELL";
  price: number;
  quantity: number;
  orderId: string;
  origin: DataOrigin;
}

export interface Position {
  symbol: string;
  exchangeSegment: string;
  netQuantity: number;
  averagePricePaisa: number;
  currentPricePaisa: number;
  realizedPnlPaisa: number;
  unrealizedPnlPaisa: number;
}

export interface Order {
  orderId: string;
  symbol: string;
  side: string;
  quantity: number;
  filledQuantity: number;
  pricePaisa: number;
  status: string;
  orderType: string;
  correlationId?: string;
  rejectionReason?: string | null;
}

export interface Signal {
  strategyId: string;
  symbol: string;
  side: "BUY" | "SELL";
  strength: number;
  reason: string;
  ts: number;
}

export interface HealthSnapshot {
  brokerUp: boolean;
  websocketConnected: boolean;
  broker: string;
  marketDataUp: boolean;
  lastTickMs: number;
}
