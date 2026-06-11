export enum AssetClass { EQUITY = "EQUITY", FUTURES = "FUTURES", OPTIONS = "OPTIONS", CRYPTO = "CRYPTO", FOREX = "FOREX", COMMODITY = "COMMODITY" }
export enum MarketState { PREOPEN = "PREOPEN", OPEN = "OPEN", CLOSED = "CLOSED", HOLIDAY = "HOLIDAY", AUCTION = "AUCTION", UNKNOWN = "UNKNOWN" }

export interface Instrument {
  symbol: string;
  exchange: "NSE" | "BSE" | "NFO" | "MCX" | "CDS" | "BINANCE";
  assetClass: AssetClass;
  currency: "INR" | "USD" | "USDT";
  volumeUnit: "SHARES" | "CONTRACTS" | "BTC" | "ETH" | "LOTS";
  tickSize: number;
  lotSize: number;
  qtyDecimals: number;
}

export interface OHLCVBar {
  time: number;
  open: number;
  high: number;
  low: number;
  close: number;
  volume: number;
}

export interface L2Level {
  price: number;
  quantity: number;
  orders: number;
}

export interface OrderBookSnapshot {
  asks: L2Level[];
  bids: L2Level[];
  timestamp: number;
  ltp: number;
  spread: number;
}

export interface TradeTick {
  time: number;
  price: number;
  quantity: number;
  side: "BUY" | "SELL";
}

export interface SessionResponse {
  state: MarketState;
  exchange: string;
  currentTime: string;
  timezone: string;
  sessionHours: Record<string, string>;
  nextTransition: string;
  dataSource: string;
}

const EQUITY_DEFAULTS: Partial<Instrument> = {
  assetClass: AssetClass.EQUITY, currency: "INR", volumeUnit: "SHARES",
  tickSize: 0.05, lotSize: 1, qtyDecimals: 0,
};

export const INSTRUMENTS: Record<string, Instrument> = {
  RELIANCE: { symbol: "RELIANCE", exchange: "NSE", ...EQUITY_DEFAULTS } as Instrument,
  TCS: { symbol: "TCS", exchange: "NSE", ...EQUITY_DEFAULTS } as Instrument,
  INFY: { symbol: "INFY", exchange: "NSE", ...EQUITY_DEFAULTS } as Instrument,
  HDFCBANK: { symbol: "HDFCBANK", exchange: "NSE", ...EQUITY_DEFAULTS } as Instrument,
  ICICIBANK: { symbol: "ICICIBANK", exchange: "NSE", ...EQUITY_DEFAULTS } as Instrument,
  SBIN: { symbol: "SBIN", exchange: "NSE", ...EQUITY_DEFAULTS } as Instrument,
  NIFTY: { symbol: "NIFTY", exchange: "NFO", assetClass: AssetClass.FUTURES, currency: "INR", volumeUnit: "CONTRACTS", tickSize: 0.05, lotSize: 25, qtyDecimals: 0 },
  BANKNIFTY: { symbol: "BANKNIFTY", exchange: "NFO", assetClass: AssetClass.FUTURES, currency: "INR", volumeUnit: "CONTRACTS", tickSize: 0.05, lotSize: 15, qtyDecimals: 0 },
  GOLD: { symbol: "GOLD", exchange: "MCX", assetClass: AssetClass.COMMODITY, currency: "INR", volumeUnit: "LOTS", tickSize: 1, lotSize: 1, qtyDecimals: 0 },
  SILVER: { symbol: "SILVER", exchange: "MCX", assetClass: AssetClass.COMMODITY, currency: "INR", volumeUnit: "LOTS", tickSize: 1, lotSize: 1, qtyDecimals: 0 },
  CRUDEOIL: { symbol: "CRUDEOIL", exchange: "MCX", assetClass: AssetClass.COMMODITY, currency: "INR", volumeUnit: "LOTS", tickSize: 1, lotSize: 1, qtyDecimals: 0 },
  USDINR: { symbol: "USDINR", exchange: "CDS", assetClass: AssetClass.FOREX, currency: "INR", volumeUnit: "CONTRACTS", tickSize: 0.0025, lotSize: 1000, qtyDecimals: 0 },
  EURINR: { symbol: "EURINR", exchange: "CDS", assetClass: AssetClass.FOREX, currency: "INR", volumeUnit: "CONTRACTS", tickSize: 0.0025, lotSize: 1000, qtyDecimals: 0 },
};

export function resolveInstrument(symbol: string, exchange: string): Instrument {
  const key = symbol.toUpperCase();
  if (INSTRUMENTS[key]) return INSTRUMENTS[key];
  const ex = exchange.toUpperCase();
  if (ex === "BINANCE") {
    const base = key.replace("USDT", "");
    return { symbol: key, exchange: "BINANCE", assetClass: AssetClass.CRYPTO, currency: "USDT", volumeUnit: base as any, tickSize: 0.01, lotSize: 1, qtyDecimals: 3 };
  }
  return { symbol: key, exchange: ex as any, ...EQUITY_DEFAULTS } as Instrument;
}

export function resolveVolumeUnit(inst: Instrument): string {
  return inst.volumeUnit;
}

export function resolveQtyDecimals(inst: Instrument): number {
  return inst.qtyDecimals;
}

export const MARKET_STATE_COLORS: Record<MarketState, string> = {
  [MarketState.OPEN]: "#26a69a",
  [MarketState.CLOSED]: "#ef5350",
  [MarketState.PREOPEN]: "#f0b429",
  [MarketState.HOLIDAY]: "#6e7681",
  [MarketState.AUCTION]: "#f0b429",
  [MarketState.UNKNOWN]: "#6e7681",
};
