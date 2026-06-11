export interface Candle {
  time: number; // timestamp
  open: number;
  high: number;
  low: number;
  close: number;
  volume: number;
}

export interface BookItem {
  price: number;
  amount: number;
  total: number;
  cumulative: number;
  depthPercent: number;
}

export interface Trade {
  id: string;
  time: number;
  price: number;
  amount: number;
  type: "buy" | "sell";
}

export interface Order {
  id: string;
  time: number;
  symbol: string;
  type: "limit" | "market";
  side: "buy" | "sell";
  price: number;
  amount: number;
  filled: number;
  status: "pending" | "filled" | "cancelled";
}

export interface AssetBalance {
  asset: string;
  free: number;
  locked: number;
}

export interface WatchlistItem {
  symbol: string;
  name: string;
  price: number;
  change: number;
  volume: number;
  sparkline?: number[];
}
