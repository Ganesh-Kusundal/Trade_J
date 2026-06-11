import { fetchJson } from "./client";
import type { SessionResponse } from "../domain/instrument";
import { MarketState } from "../domain/instrument";

export function fetchSession(exchange: string) {
  return fetchJson<SessionResponse>(`/market/session?exchange=${exchange}`);
}

export function isMarketOpen(state: MarketState): boolean {
  return state === MarketState.OPEN;
}
