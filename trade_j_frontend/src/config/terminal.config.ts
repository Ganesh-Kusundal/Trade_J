/**
 * Trade-J Terminal Configuration
 * All hardcoded values moved here for easy management
 */

import type { ExchangeSegment } from "../generated/models";

// Exchange segment mapping
export const EXCHANGE_MAP: Record<string, ExchangeSegment> = {
  NSE: "NSE_EQ" as ExchangeSegment,
  BSE: "BSE_EQ" as ExchangeSegment,
  NFO: "NSE_FNO" as ExchangeSegment,
  MCX: "MCX_COMM" as ExchangeSegment,
  CDS: "NSE_CURRENCY" as ExchangeSegment,
};

// Default symbols per exchange (can be overridden by API)
export const DEFAULT_SYMBOLS: Record<string, string[]> = {
  NSE: ["RELIANCE", "TCS", "INFY", "HDFCBANK", "ICICIBANK", "SBIN"],
  BSE: ["RELIANCE", "TCS", "INFY", "SBIN"],
  NFO: ["NIFTY", "BANKNIFTY"],
  MCX: ["GOLD", "SILVER", "CRUDEOIL"],
  CDS: ["USDINR", "EURINR"],
};

// Broker exchange support matrix
export const BROKER_EXCHANGES: Record<string, string[]> = {
  DHAN: ["NSE", "BSE", "NFO", "MCX", "CDS"],
  UPSTOX: ["NSE", "BSE", "NFO", "MCX", "CDS"],
  ICICI: ["NSE", "BSE", "NFO"],
  SIMULATION: ["NSE", "BSE", "NFO", "MCX", "CDS"],
};

// Timeframe configuration
export const TIMEFRAME_CONFIG: Record<string, { interval: string; days: number }> = {
  "1m":  { interval: "1m",  days: 5 },
  "5m":  { interval: "5m",  days: 15 },
  "15m": { interval: "15m", days: 30 },
  "1h":  { interval: "1h",  days: 90 },
  "4h":  { interval: "4h",  days: 180 },
  "1d":  { interval: "1d",  days: 750 },
};

// Index configuration
// REMOVED: fallback, min, max values to prevent fake data display
// Now uses null when API unavailable - displays "N/A" in UI
export const INDEX_CONFIG = [
  { name: "NIFTY 50", apiSymbol: "NIFTY 50", exchange: "NSE", segment: "IDX_I" },
  { name: "BANK NIFTY", apiSymbol: "NIFTY BANK", exchange: "NSE", segment: "IDX_I" },
  { name: "INDIA VIX", apiSymbol: "INDIA VIX", exchange: "NSE", segment: "IDX_I" },
];

// Feed polling intervals (milliseconds)
export const FEED_CONFIG = {
  GATEWAY_WS_URL: typeof window !== "undefined"
    ? `${window.location.protocol === "https:" ? "wss:" : "ws:"}//${window.location.host}/ws/gateway`
    : "ws://localhost:8080/ws/gateway",
  LTP_INTERVAL_MS: 2000,
  DEPTH_INTERVAL_MS: 3000,
  CANDLE_REFRESH_MS: 60000,
  SESSION_INTERVAL_MS: 60000,
  HEALTH_INTERVAL_MS: 5000,
  STALE_AFTER_MS: 30000,
  DELAYED_AFTER_MS: 5000,
  STALE_CHECK_MS: 2000,
  WATCHDOG_INTERVAL_MS: 15000,
  WATCHDOG_TIMEOUT_MS: 30000,
};

export function intervalToMs(interval: string): number {
  const map: Record<string, number> = {
    "1m": 60000,
    "3m": 180000,
    "5m": 300000,
    "15m": 900000,
    "1h": 3600000,
    "4h": 14400000,
    "1d": 86400000,
  };
  return map[interval] ?? 60000;
}

// Default UI settings
export const UI_DEFAULTS = {
  defaultBroker: "DHAN",
  defaultExchange: "NSE",
  defaultTimeframe: "1m",
  maxTradeHistory: 50,
  maxCandleDataPoints: 1000,
};

// Local storage keys
export const STORAGE_KEYS = {
  broker: "tj_broker",
  exchange: "tj_exchange",
  symbol: "tj_symbol",
  timeframe: "tj_timeframe",
  credentials: "tj_creds",
};
