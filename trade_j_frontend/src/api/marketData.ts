import { marketApi } from "../generated/api";
import type {
  SymbolsResponse,
  LtpResponse,
  DepthResponse,
  CandleResponse,
} from "../generated/models";

export function fetchSymbols(exchangeSegment?: string, search?: string) {
  return marketApi.symbols(false) as Promise<SymbolsResponse>;
}

export function fetchLtp(symbol: string, exchangeSegment: string) {
  return marketApi.ltp(symbol, exchangeSegment) as Promise<LtpResponse>;
}

export function fetchDepth(symbol: string) {
  return marketApi.depth(symbol) as Promise<DepthResponse>;
}

export function fetchCandles(
  symbol: string,
  exchangeSegment: string,
  interval: string,
  from: string,
  to: string,
  source = "broker"
) {
  return marketApi.candles({ symbol, exchangeSegment, interval, from, to, source }) as Promise<CandleResponse>;
}
