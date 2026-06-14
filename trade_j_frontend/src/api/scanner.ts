import { scannerApi } from "../generated/api";

export interface ScanHit {
  symbol: string;
  exchangeSegment: string;
  assetClass: string;
  underlying: string;
  score: number;
  reasons: string[];
  snapshot: Record<string, any>;
  promoted: boolean;
}

export interface ScanRun {
  runId: string;
  profileId: string;
  startedAtMs: number;
  finishedAtMs: number;
  status: string;
  universeSize: number;
  hitCount: number;
  partialFailureCount: number;
  errorMessage?: string;
}

export interface ScanResult {
  run: ScanRun;
  hits: ScanHit[];
}

export function runScan(profile?: string): Promise<ScanResult> {
  return scannerApi.run(profile) as Promise<ScanResult>;
}

export function getLatestScan(profile: string): Promise<ScanResult> {
  return scannerApi.latest(profile) as Promise<ScanResult>;
}
