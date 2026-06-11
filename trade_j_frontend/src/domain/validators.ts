import type { OHLCVBar, OrderBookSnapshot, TradeTick, Instrument } from "./instrument";
import { AssetClass } from "./instrument";

export interface ValidationResult {
  valid: boolean;
  errors: string[];
}

export function validateBar(bar: OHLCVBar): ValidationResult {
  const errors: string[] = [];
  if (bar.high < Math.max(bar.open, bar.close))
    errors.push(`OHLC_INTEGRITY: high(${bar.high}) < max(open(${bar.open}),close(${bar.close}))`);
  if (bar.low > Math.min(bar.open, bar.close))
    errors.push(`OHLC_INTEGRITY: low(${bar.low}) > min(open(${bar.open}),close(${bar.close}))`);
  if (bar.open <= 0) errors.push(`INVALID_OPEN: ${bar.open}`);
  if (bar.close <= 0) errors.push(`INVALID_CLOSE: ${bar.close}`);
  if (bar.high <= 0) errors.push(`INVALID_HIGH: ${bar.high}`);
  if (bar.low <= 0) errors.push(`INVALID_LOW: ${bar.low}`);
  if (bar.volume < 0) errors.push(`NEGATIVE_VOLUME: ${bar.volume}`);
  if (bar.time <= 0) errors.push(`INVALID_TIMESTAMP: ${bar.time}`);
  return { valid: errors.length === 0, errors };
}

export function validateSeries(bars: OHLCVBar[]): ValidationResult {
  const errors: string[] = [];
  const timestamps = new Set<number>();
  for (let i = 0; i < bars.length; i++) {
    const v = validateBar(bars[i]);
    if (!v.valid) errors.push(...v.errors.map(e => `[${i}] ${e}`));
    if (timestamps.has(bars[i].time))
      errors.push(`[${i}] DUPLICATE_TIMESTAMP: ${bars[i].time}`);
    timestamps.add(bars[i].time);
    if (i > 0 && bars[i].time <= bars[i - 1].time)
      errors.push(`[${i}] OUT_OF_ORDER: ${bars[i].time} <= ${bars[i - 1].time}`);
  }
  return { valid: errors.length === 0, errors };
}

export function validateOrderBook(book: OrderBookSnapshot): ValidationResult {
  const errors: string[] = [];
  for (let i = 1; i < book.asks.length; i++) {
    if (book.asks[i].price <= book.asks[i - 1].price)
      errors.push(`ASK_NOT_ASCENDING at level ${i}: ${book.asks[i].price} <= ${book.asks[i - 1].price}`);
  }
  for (let i = 1; i < book.bids.length; i++) {
    if (book.bids[i].price >= book.bids[i - 1].price)
      errors.push(`BID_NOT_DESCENDING at level ${i}: ${book.bids[i].price} >= ${book.bids[i - 1].price}`);
  }
  if (book.asks.length > 0 && book.bids.length > 0 && book.asks[0].price <= book.bids[0].price)
    errors.push(`CROSSED_BOOK: bestAsk(${book.asks[0].price}) <= bestBid(${book.bids[0].price})`);
  return { valid: errors.length === 0, errors };
}

export function validateTrade(trade: TradeTick, instrument: Instrument): ValidationResult {
  const errors: string[] = [];
  if (instrument.assetClass === AssetClass.EQUITY && !Number.isInteger(trade.quantity))
    errors.push(`NON_INTEGER_QUANTITY: ${trade.quantity} for EQUITY ${instrument.symbol}`);
  if (trade.price <= 0) errors.push(`INVALID_PRICE: ${trade.price}`);
  if (trade.quantity <= 0) errors.push(`INVALID_QUANTITY: ${trade.quantity}`);
  return { valid: errors.length === 0, errors };
}

export function computeMA(bars: OHLCVBar[], period: number): { time: number; value: number }[] {
  if (bars.length < period) return [];
  const result: { time: number; value: number }[] = [];
  let sum = 0;
  for (let i = 0; i < bars.length; i++) {
    sum += bars[i].close;
    if (i >= period) sum -= bars[i - period].close;
    if (i >= period - 1) result.push({ time: bars[i].time, value: sum / period });
  }
  return result;
}

export function computeVWAP(bars: OHLCVBar[]): { time: number; value: number }[] {
  let cumTPV = 0;
  let cumVol = 0;
  return bars.map(bar => {
    const tp = (bar.high + bar.low + bar.close) / 3;
    cumTPV += tp * bar.volume;
    cumVol += bar.volume;
    return { time: bar.time, value: cumVol > 0 ? cumTPV / cumVol : tp };
  });
}

export function filterValidBars(bars: OHLCVBar[]): OHLCVBar[] {
  return bars.filter(b => {
    const v = validateBar(b);
    return v.valid;
  });
}
