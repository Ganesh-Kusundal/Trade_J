import type { DataOrigin } from "../store/types";

export interface FeedTick {
  symbol: string;
  exchangeSegment: string;
  ltp: number;
  bid?: number;
  ask?: number;
  qty?: number;
  ts: number;
  origin: DataOrigin;
}

export interface FeedDepth {
  symbol: string;
  exchangeSegment: string;
  bids: Array<{ price: number; quantity: number; orders: number }>;
  asks: Array<{ price: number; quantity: number; orders: number }>;
  ts: number;
}

export interface FeedFill {
  symbol: string;
  exchangeSegment: string;
  side: "BUY" | "SELL";
  price: number;
  quantity: number;
  orderId: string;
  ts: number;
  origin: DataOrigin;
}

export type FeedEvent =
  | { type: "tick"; data: FeedTick }
  | { type: "depth"; data: FeedDepth }
  | { type: "fill"; data: FeedFill }
  | { type: "status"; data: { connected: boolean; reason?: string } };

export interface BrokerFeedClient {
  readonly broker: string;
  connect(symbols: string[]): Promise<void>;
  disconnect(): void;
  subscribe(listener: (e: FeedEvent) => void): () => void;
  isConnected(): boolean;
}
