import { createStore, useStore } from "./createStore";
import type { Instrument, Candle, L2Level, Fill, Mode, DataOrigin } from "./types";

interface MarketState {
  symbol: string | null;
  exchange: string | null;
  segment: string | null;
  mode: Mode;
  ltp: number;
  priceChangePct: number;
  bids: L2Level[];
  asks: L2Level[];
  candles: Record<string, Candle[]>;
  activeInterval: string;
  fills: Fill[];
  lastTickOrigin: DataOrigin;
  lastTickTs: number;
}

const EMPTY: MarketState = {
  symbol: null,
  exchange: null,
  segment: null,
  mode: "PAPER",
  ltp: 0,
  priceChangePct: 0,
  bids: [],
  asks: [],
  candles: {},
  activeInterval: "1m",
  fills: [],
  lastTickOrigin: "UNKNOWN",
  lastTickTs: 0,
};

export const marketStore = createStore<MarketState>(EMPTY);

export function setSymbol(symbol: string, exchange: string, segment: string): void {
  marketStore.setState((s) => ({
    ...s,
    symbol,
    exchange,
    segment,
    ltp: 0,
    priceChangePct: 0,
    bids: [],
    asks: [],
    candles: {},
    fills: [],
    lastTickOrigin: "UNKNOWN",
  }));
}

export function applyTick(price: number, ts: number, origin: DataOrigin): void {
  marketStore.setState((s) => {
    const change = s.ltp > 0 ? ((price - s.ltp) / s.ltp) * 100 : 0;
    return { ...s, ltp: price, priceChangePct: change, lastTickTs: ts, lastTickOrigin: origin };
  });
}

export function applyDepth(bids: L2Level[], asks: L2Level[]): void {
  marketStore.setState({ bids, asks });
}

export function applyCandle(interval: string, candle: Candle): void {
  marketStore.setState((s) => {
    const existing = s.candles[interval] ?? [];
    const next = existing.length > 0 && existing[existing.length - 1].time === candle.time
      ? [...existing.slice(0, -1), candle]
      : [...existing, candle].slice(-1500);
    return { ...s, candles: { ...s.candles, [interval]: next } };
  });
}

export function setCandles(interval: string, candles: Candle[]): void {
  marketStore.setState((s) => ({ ...s, candles: { ...s.candles, [interval]: candles } }));
}

export function applyFill(fill: Fill): void {
  marketStore.setState((s) => ({ ...s, fills: [fill, ...s.fills].slice(0, 100) }));
}

export function setMode(mode: Mode): void {
  marketStore.setState({ mode });
}

export const useMarket = <S,>(selector: (s: MarketState) => S) => useStore(marketStore, selector);
