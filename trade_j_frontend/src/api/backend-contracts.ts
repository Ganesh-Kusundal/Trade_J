export enum Side {
  BUY = "BUY",
  SELL = "SELL",
  LONG = "LONG",
  SHORT = "SHORT",
}

export enum OrderType {
  MARKET = "MARKET",
  LIMIT = "LIMIT",
  STOP_LOSS = "STOP_LOSS",
  STOP_LOSS_MARKET = "STOP_LOSS_MARKET",
}

export enum ProductType {
  INTRADAY = "INTRADAY",
  CNC = "CNC",
  MARGIN = "MARGIN",
  CARRY_FORWARD = "CARRY_FORWARD",
}

export enum Validity {
  DAY = "DAY",
  IOC = "IOC",
}

export enum ExchangeSegment {
  NSE_EQ = "NSE_EQ",
  NSE_FNO = "NSE_FNO",
  BSE_EQ = "BSE_EQ",
  BSE_FNO = "BSE_FNO",
  IDX_I = "IDX_I",
  MCX_COMM = "MCX_COMM",
  NSE_CURRENCY = "NSE_CURRENCY",
  BSE_CURRENCY = "BSE_CURRENCY",
}

export interface PlaceOrderRequest {
  symbol: string;
  exchangeSegment: ExchangeSegment;
  side: Side;
  quantity: number;
  orderType: OrderType;
  pricePaisa: number;
  triggerPricePaisa: number | null;
  productType: ProductType;
  validity: Validity;
  correlationId?: string;
}

export interface OrderResponse {
  orderId: string;
  correlationId: string;
  symbol: string;
  exchangeSegment: ExchangeSegment;
  side: Side;
  productType: ProductType;
  orderType: OrderType;
  status: string;
  quantity: number;
  filledQuantity: number;
  pricePaisa: number;
  triggerPricePaisa: number;
  exchangeTimeMs: number;
  rejectionReason: string | null;
}

export interface OrderProjectionResponse {
  orderId: string;
  symbol: string;
  totalQuantity: number;
  filledQuantity: number;
  averagePricePaisa: number;
  status: string;
}

export interface Symbol {
  symbol: string;
  canonicalSymbol: string;
  name: string;
  exchange: string;
  exchangeSegment: string;
  instrumentType: string;
  lotSize: number;
  tickSizePaisa: number;
  active: boolean;
  segment: string;
}

export interface SymbolsResponse {
  symbols: Symbol[];
}

export interface LtpResponse {
  symbol: string;
  canonicalSymbol: string;
  exchangeSegment: string;
  ltpPaisa: number;
}

export interface DepthResponse {
  symbol: string;
  segment: string;
  bids: DepthLevel[];
  asks: DepthLevel[];
  midPricePaisa: number;
  spreadPaisa: number;
  cumulativeBidVol: number;
  cumulativeAskVol: number;
  imbalance: number;
  timestampMs: number;
}

export interface DepthLevel {
  pricePaisa?: number;
  quantity?: number;
  orders?: number;
  price?: number;
  amount?: number;
}

export interface CandleResponse {
  symbol: string;
  canonicalSymbol: string;
  exchangeSegment: string;
  interval: string;
  from: string;
  to: string;
  source: string;
  count: number;
  candles: CandleData[];
}

export interface CandleData {
  symbol: string;
  canonicalSymbol: string;
  startTimeMs: number;
  endTimeMs: number;
  openPaisa: number;
  highPaisa: number;
  lowPaisa: number;
  closePaisa: number;
  volume: number;
}

export interface ReadModelSnapshot {
  signals: unknown[];
  positions: unknown[];
  version: number;
  candles: unknown[];
  ticks: unknown[];
  orders: unknown[];
  depths: unknown[];
  pnl: {
    realizedPnlPaisa: number;
    unrealizedPnlPaisa: number;
    netExposurePaisa: number;
  };
}

export interface HealthResponse {
  status: "UP" | "DOWN";
  groups: string[];
  components: Record<string, { status: string; details?: Record<string, unknown> }>;
}
