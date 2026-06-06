export type Quote = {
  symbol: string;
  exchange: string;
  ltp: number; // price in INR (or normalized)
  changePct: number;
  volume: number;
  timestampMs: number;
};

export type Candle = {
  timeMs: number; // epoch ms
  open: number;
  high: number;
  low: number;
  close: number;
  volume: number;
};

export type OptionSide = 'CE' | 'PE';

export type OptionChainRow = {
  strike: number;
  side: OptionSide;
  oi: number;
  oiChange: number; // delta
  volume: number;
  iv: number; // implied vol
};

export type OptionChain = {
  symbol: string;
  exchange: string;
  expiry: string; // ISO date string (or broker-agnostic)
  rows: OptionChainRow[];
};

export type MarketDepthLevel = {
  bidPrice: number;
  bidQty: number;
  askPrice: number;
  askQty: number;
};

export type MarketDepth = {
  symbol: string;
  exchange: string;
  levels: MarketDepthLevel[];
  timestampMs: number;
};

export type Position = {
  symbol: string;
  exchange: string;
  qty: number;
  avgPrice: number;
  pnl: number;
};

export type Holding = {
  asset: string;
  qty: number;
  avgPrice: number;
  value: number;
};

export type Order = {
  id: string;
  symbol: string;
  exchange: string;
  side: 'BUY' | 'SELL';
  type: 'MARKET' | 'LIMIT';
  qty: number;
  price?: number;
  status: 'NEW' | 'PENDING' | 'FILLED' | 'REJECTED' | 'CANCELLED';
  createdAtMs: number;
};

export type NewsItem = {
  id: string;
  headline: string;
  source: string;
  publishedAtMs: number;
  url?: string;
};

export type StrategySignal = {
  symbol: string;
  strategyId: string;
  signal: 'BUY' | 'SELL' | 'HOLD';
  confidence: number; // 0..1
  createdAtMs: number;
};

// ── DOM Analytics Types ──

export type DepthImbalance = {
  symbol: string;
  segment: string;
  topOfBookImbalance: number;
  cumulativeImbalance: number;
  trend: 'STRONG_BID' | 'STRONG_ASK' | 'NEUTRAL' | 'MIXED';
  timestampMs: number;
};

export type HeatmapPriceBucket = {pricePaisa: number; bidVol: number; askVol: number};
export type HeatmapChunk = {symbol: string; segment: string; priceBuckets: HeatmapPriceBucket[]; windowStartMs: number};

export type IcebergAlert = {
  symbol: string; segment: string; pricePaisa: number; side: 'BID' | 'ASK';
  confidence: number; estimatedTotalQty: number; visibleQty: number; refreshCount: number; timestampMs: number;
};

export type AbsorptionAlert = {
  symbol: string; segment: string; pricePaisa: number; side: 'BID' | 'ASK';
  erosionRate: number; breakoutProbability: number; isCancellation: boolean; timestampMs: number;
};

export type SRLevel = {pricePaisa: number; pricePaisaHigh: number; side: string; totalVolume: number; persistenceScore: number};
export type SRLevelsUpdate = {symbol: string; segment: string; levels: SRLevel[]; timestampMs: number};

export type OrderBookSnapshot = {
  symbol: string; segment: string;
  bids: {pricePaisa: number; quantity: number; orderCount: number}[];
  asks: {pricePaisa: number; quantity: number; orderCount: number}[];
  midPricePaisa: number; spreadPaisa: number;
  cumulativeBidVol: number; cumulativeAskVol: number; imbalance: number; timestampMs: number;
};
