import type {
  LTPResponse,
  HistoricalCandlesResponse,
  SymbolsResponse,
  ScanResult,
  ScanRunsResponse,
  ReadModelSnapshot,
  RuntimeInfo,
  StrategiesResponse,
  StrategyPluginRow,
  KillSwitchResponse,
  StartupCandidatesResponse,
  StudioChartResponse,
} from '@/dto/types';

const BASE = import.meta.env.VITE_API_BASE ?? '';

async function request<T>(path: string, options?: RequestInit): Promise<T> {
  const res = await fetch(`${BASE}${path}`, {
    headers: {'Content-Type': 'application/json', ...options?.headers},
    ...options,
  });
  if (!res.ok) {
    const body = await res.text();
    throw new Error(`${res.status} ${res.statusText}: ${body}`);
  }
  return res.json() as Promise<T>;
}

// ── Market Data ──────────────────────────────────────────────────
export const marketApi = {
  ltp: (symbol: string, exchangeSegment: string) =>
    request<LTPResponse>(`/api/v1/market/ltp?symbol=${encodeURIComponent(symbol)}&exchangeSegment=${exchangeSegment}`),

  historicalCandles: (symbol: string, exchangeSegment: string, interval: string, from: string, to: string, source = 'parquet') =>
    request<HistoricalCandlesResponse>(
      `/api/v1/market/historical/candles?symbol=${encodeURIComponent(symbol)}&exchangeSegment=${exchangeSegment}&interval=${interval}&from=${from}&to=${to}&source=${source}`
    ),
};

// ── Studio ───────────────────────────────────────────────────────
export const studioApi = {
  startupCandidates: (topN = 3, date?: string) =>
    request<StartupCandidatesResponse>(
      `/api/v1/studio/startup-candidates?topN=${topN}${date ? `&date=${date}` : ''}`
    ),

  chart: (symbol: string, exchangeSegment: string, interval: string, from: string, to: string) =>
    request<StudioChartResponse>(
      `/api/v1/studio/chart?symbol=${encodeURIComponent(symbol)}&exchangeSegment=${exchangeSegment}&interval=${interval}&from=${from}&to=${to}`
    ),
};

// ── Symbols ──────────────────────────────────────────────────────
export const symbolApi = {
  list: (refresh = false) =>
    request<SymbolsResponse>(`/api/v1/symbols?refresh=${refresh}`),
};

// ── Scanner ──────────────────────────────────────────────────────
export const scanApi = {
  latest: (profile: string) =>
    request<ScanResult>(`/api/v1/scans/latest?profile=${encodeURIComponent(profile)}`),

  byRunId: (runId: string) =>
    request<ScanResult>(`/api/v1/scans/${encodeURIComponent(runId)}`),

  listRuns: (profile: string, limit = 10) =>
    request<ScanRunsResponse>(`/api/v1/scans?profile=${encodeURIComponent(profile)}&limit=${limit}`),

  run: (profile?: string) =>
    request<ScanResult>(`/api/v1/scans/run${profile ? `?profile=${encodeURIComponent(profile)}` : ''}`, {method: 'POST'}),
};

// ── Pipeline ─────────────────────────────────────────────────────
export const pipelineApi = {
  templates: () => request<Record<string, unknown>>('/api/v1/pipeline/templates'),

  nodeTypes: () => request<Record<string, unknown>>('/api/v1/pipeline/node-types'),

  nodeTypeCategories: () => request<string[]>('/api/v1/pipeline/node-types/categories'),

  nodeTypesByCategory: (category: string) =>
    request<unknown[]>(`/api/v1/pipeline/node-types/category/${encodeURIComponent(category)}`),

  activeGraph: () => request<unknown>('/api/v1/pipeline/graph'),

  activeDagGraphs: () => request<Record<string, unknown>>('/api/v1/pipeline/dag/graphs'),

  dagGraph: (graphId: string) =>
    request<unknown>(`/api/v1/pipeline/dag/graph/${encodeURIComponent(graphId)}`),

  history: (graphId: string) =>
    request<unknown[]>(`/api/v1/pipeline/history/${encodeURIComponent(graphId)}`),

  loadVersion: (graphId: string, version: number) =>
    request<unknown>(`/api/v1/pipeline/history/${encodeURIComponent(graphId)}/${version}`),

  persist: () =>
    request<{success: boolean; graphId: string; version: number; executionMode: string}>('/api/v1/pipeline/persist', {method: 'POST'}),

  compile: (graph: unknown) =>
    request<{success: boolean; message: string; version?: number}>('/api/v1/pipeline/compile', {
      method: 'POST',
      body: JSON.stringify(graph),
    }),

  restore: (graphId: string, version: number) =>
    request<{success: boolean; graphId: string; version: number; executionMode: string}>(
      `/api/v1/pipeline/restore/${encodeURIComponent(graphId)}/${version}`,
      {method: 'POST'}
    ),
};

// ── Read Model ───────────────────────────────────────────────────
export const readModelApi = {
  snapshot: () => request<ReadModelSnapshot>('/api/v1/read-model'),
};

// ── Admin ────────────────────────────────────────────────────────
export const adminApi = {
  runtime: () => request<RuntimeInfo>('/admin/runtime'),

  strategies: async (): Promise<StrategyPluginRow[]> => {
    const res = await request<StrategiesResponse>('/admin/strategies');
    return (res.plugins ?? []).map((name) => ({name}));
  },

  strategiesRaw: () => request<StrategiesResponse>('/admin/strategies'),

  summary: () => request<Record<string, unknown>>('/admin/summary'),

  killSwitch: (enabled: boolean) =>
    request<KillSwitchResponse>(`/admin/risk/kill-switch/${enabled}`, {method: 'POST'}),

  reconcile: () =>
    request<{success: boolean; message: string}>('/admin/reconcile', {method: 'POST'}),
};

// ── Health ───────────────────────────────────────────────────────
export const healthApi = {
  check: () => request<{status: string; [key: string]: unknown}>('/actuator/health'),
};
