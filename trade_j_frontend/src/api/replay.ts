import { replayApi as genReplayApi } from "../generated/api";

export interface ReplayResult {
  mode: string;
  symbol: string;
  interval?: string;
  from: number;
  to: number;
  totalRead: number;
  replayed: number;
  failed: number;
  complete: boolean;
}

export function replayCandles(
  symbol: string,
  interval: string,
  from: number,
  to: number
): Promise<ReplayResult> {
  return genReplayApi.candles(symbol, interval, from, to) as Promise<ReplayResult>;
}

export function replayTicks(
  symbol: string,
  from: number,
  to: number,
  offset = 0,
  batchSize = 50000
): Promise<ReplayResult> {
  return genReplayApi.ticks(symbol, from, to, offset, batchSize) as Promise<ReplayResult>;
}

export function checkRuntimeMode(): Promise<{ mode: string }> {
  return genReplayApi.runtimeMode();
}
