import { fetchJson } from "./client";
import type {
  SymbolsResponse,
  LtpResponse,
  DepthResponse,
  CandleResponse,
} from "./backend-contracts";

export function fetchSymbols(exchangeSegment?: string, search?: string) {
  const params = new URLSearchParams();
  if (exchangeSegment) params.set("exchangeSegment", exchangeSegment);
  if (search) params.set("search", search);
  return fetchJson<SymbolsResponse>(`/symbols?${params}`);
}

export function fetchLtp(symbol: string, exchangeSegment: string) {
  return fetchJson<LtpResponse>(
    `/market/ltp?symbol=${encodeURIComponent(symbol)}&exchangeSegment=${exchangeSegment}`
  );
}

export function fetchDepth(symbol: string) {
  return fetchJson<DepthResponse>(`/market/depth/${encodeURIComponent(symbol)}`);
}

export function fetchCandles(
  symbol: string,
  exchangeSegment: string,
  interval: string,
  from: string,
  to: string,
  source = "broker"
) {
  const params = new URLSearchParams({ symbol, exchangeSegment, interval, from, to, source });
  return fetchJson<CandleResponse>(`/market/historical/candles?${params}`);
}
