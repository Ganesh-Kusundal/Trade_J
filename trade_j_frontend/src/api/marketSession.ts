import { marketApi } from "../generated/api";
import type { SessionResponse } from "../domain/instrument";
import { MarketState } from "../domain/instrument";

export function fetchSession(exchange: string) {
  return marketApi.session(exchange) as Promise<SessionResponse>;
}

export function isMarketOpen(state: MarketState): boolean {
  return state === MarketState.OPEN;
}
