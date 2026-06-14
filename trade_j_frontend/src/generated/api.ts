/**
 * AUTO-GENERATED from docs/openapi.yaml — DO NOT EDIT MANUALLY.
 * Regenerate with: scripts/generate-api-client.sh
 *
 * Typed API client for Trade-J REST endpoints.
 */

import type {
  AnalyticsCatalogSnapshot,
  AnalyticsQueryResult,
  BrokerDescriptorView,
  CandleResponse,
  DepthResponse,
  DiscoveryResponse,
  ErrorResponse,
  EventCatalogView,
  FeatureView,
  HealthResponse,
  LtpResponse,
  OrderProjectionResponse,
  OrderResponse,
  PlaceOrderRequest,
  ReadModelSnapshot,
  Symbol,
  SymbolsResponse,
} from "./models";

const BASE_URL = "/api/v1";

async function request<T>(path: string, options?: RequestInit): Promise<T> {
  const res = await fetch(`${BASE_URL}${path}`, {
    headers: { "Content-Type": "application/json" },
    ...options,
  });
  if (!res.ok) {
    let errorBody: ErrorResponse | null = null;
    try { errorBody = await res.json(); } catch { /* ignore */ }
    throw new ApiError(res.status, res.statusText, errorBody);
  }
  return res.json();
}

export class ApiError extends Error {
  constructor(
    public readonly status: number,
    public readonly statusText: string,
    public readonly body: ErrorResponse | null,
  ) {
    super(`HTTP ${status}: ${statusText}${body?.message ? ` — ${body.message}` : ""}`);
  }
}

// ── Market Data ────────────────────────────────────────

export const marketApi = {
  ltp: (symbol: string, exchangeSegment: string) =>
    request<LtpResponse>(`/market/ltp?symbol=${encodeURIComponent(symbol)}&exchangeSegment=${exchangeSegment}`),

  candles: (params: { symbol: string; exchangeSegment: string; interval?: string; from: string; to: string; source?: string }) =>
    request<CandleResponse>(
      `/market/historical/candles?symbol=${encodeURIComponent(params.symbol)}&exchangeSegment=${params.exchangeSegment}&interval=${params.interval ?? "1d"}&from=${params.from}&to=${params.to}&source=${params.source ?? "broker"}`
    ),

  session: (exchange = "NSE") =>
    request<{ state: string; dataSource: string }>(`/market/session?exchange=${exchange}`),

  symbols: (refresh = false) =>
    request<SymbolsResponse>(`/symbols${refresh ? "?refresh=true" : ""}`),

  depth: (symbol: string) =>
    request<DepthResponse>(`/market/depth/${encodeURIComponent(symbol)}`),
};

// ── Broker Registry ────────────────────────────────────

export const brokerApi = {
  list: () =>
    request<BrokerDescriptorView[]>("/brokers"),
};

// ── Orders ─────────────────────────────────────────────

export const orderApi = {
  list: (status: "active" | "all" = "active") =>
    request<OrderProjectionResponse[]>(`/orders?status=${status}`),

  place: (order: PlaceOrderRequest) =>
    request<OrderResponse>("/orders", { method: "POST", body: JSON.stringify(order) }),

  cancel: (orderId: string) =>
    request<OrderResponse>(`/orders/${orderId}/cancel`, { method: "POST" }),
};

// ── Events ─────────────────────────────────────────────

export const eventApi = {
  catalog: () =>
    request<EventCatalogView[]>("/events"),

  categories: () =>
    request<string[]>("/events/categories"),

  byCategory: (category: string) =>
    request<EventCatalogView[]>(`/events/category/${encodeURIComponent(category)}`),
};

// ── Features ───────────────────────────────────────────

export const featureApi = {
  list: () =>
    request<FeatureView[]>("/features"),
};

// ── Discovery ──────────────────────────────────────────

export const discoveryApi = {
  all: () =>
    request<DiscoveryResponse>("/discovery"),
};

// ── Analytics ──────────────────────────────────────────

export const analyticsApi = {
  catalog: () =>
    request<AnalyticsCatalogSnapshot>("/analytics/catalog"),

  sql: (sql: string, limit = 100) =>
    request<AnalyticsQueryResult>("/analytics/sql", {
      method: "POST",
      body: JSON.stringify({ sql, limit }),
    }),
};

// ── Read Model ─────────────────────────────────────────

export const readModelApi = {
  snapshot: () =>
    request<ReadModelSnapshot>("/read-model"),
};

// ── Health ─────────────────────────────────────────────

export const healthApi = {
  check: () =>
    fetch("/actuator/health").then(r => r.json() as Promise<HealthResponse>),
};

// ── Option Chain ───────────────────────────────────────

export const optionsApi = {
  chain: (underlying: string, exchangeSegment: string, expiry: string) =>
    request<unknown>(`/market/options/chain?underlying=${encodeURIComponent(underlying)}&exchangeSegment=${exchangeSegment}&expiry=${encodeURIComponent(expiry)}`),
};

// ── Scanner ────────────────────────────────────────────

export const scannerApi = {
  run: (profile?: string) =>
    request<unknown>(`/scans/run${profile ? `?profile=${profile}` : ""}`, { method: "POST" }),

  latest: (profile: string) =>
    request<unknown>(`/scans/latest?profile=${profile}`),
};

// ── Replay ─────────────────────────────────────────────

export const replayApi = {
  candles: (symbol: string, interval: string, from: number, to: number) =>
    request<unknown>(`/admin/historical/replay/candles?symbol=${encodeURIComponent(symbol)}&interval=${interval}&from=${from}&to=${to}`, { method: "POST" }),

  ticks: (symbol: string, from: number, to: number, offset = 0, batchSize = 50000) =>
    request<unknown>(`/admin/historical/replay/ticks?symbol=${encodeURIComponent(symbol)}&from=${from}&to=${to}&offset=${offset}&batchSize=${batchSize}`, { method: "POST" }),

  runtimeMode: () =>
    request<{ mode: string }>("/admin/runtime/mode"),
};
