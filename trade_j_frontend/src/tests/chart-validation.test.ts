import { validateBar, validateSeries, validateOrderBook, validateTrade, computeMA, computeVWAP, filterValidBars } from "../domain/validators";
import type { OHLCVBar, OrderBookSnapshot, TradeTick, Instrument } from "../domain/instrument";
import { AssetClass, MarketState } from "../domain/instrument";

let passed = 0;
let failed = 0;

function assert(condition: boolean, name: string) {
  if (condition) { passed++; console.log(`  PASS: ${name}`); }
  else { failed++; console.error(`  FAIL: ${name}`); }
}

function makeBar(overrides: Partial<OHLCVBar> = {}): OHLCVBar {
  return { time: 1700000000, open: 100, high: 110, low: 90, close: 105, volume: 1000, ...overrides };
}

const EQUITY: Instrument = {
  symbol: "RELIANCE", exchange: "NSE", assetClass: AssetClass.EQUITY,
  currency: "INR", volumeUnit: "SHARES", tickSize: 0.05, lotSize: 1, qtyDecimals: 0,
};

console.log("\n=== OHLC Integrity Tests ===");
{
  const good = makeBar();
  assert(validateBar(good).valid, "Valid bar passes");

  const badHigh = makeBar({ high: 99 });
  assert(!validateBar(badHigh).valid, "Rejects high < max(open, close)");

  const badLow = makeBar({ low: 111 });
  assert(!validateBar(badLow).valid, "Rejects low > min(open, close)");

  const negVol = makeBar({ volume: -1 });
  assert(!validateBar(negVol).valid, "Rejects negative volume");

  const badTime = makeBar({ time: 0 });
  assert(!validateBar(badTime).valid, "Rejects zero timestamp");

  const zeroOpen = makeBar({ open: 0 });
  assert(!validateBar(zeroOpen).valid, "Rejects zero open");
}

console.log("\n=== Series Validation Tests ===");
{
  const good: OHLCVBar[] = [
    makeBar({ time: 100 }), makeBar({ time: 200 }), makeBar({ time: 300 }),
  ];
  assert(validateSeries(good).valid, "Valid ascending series passes");

  const dupes: OHLCVBar[] = [makeBar({ time: 100 }), makeBar({ time: 100 })];
  assert(!validateSeries(dupes).valid, "Rejects duplicate timestamps");

  const outOfOrder: OHLCVBar[] = [makeBar({ time: 200 }), makeBar({ time: 100 })];
  assert(!validateSeries(outOfOrder).valid, "Rejects out-of-order timestamps");
}

console.log("\n=== Order Book Validation Tests ===");
{
  const good: OrderBookSnapshot = {
    asks: [{ price: 101, quantity: 100, orders: 1 }, { price: 102, quantity: 200, orders: 2 }],
    bids: [{ price: 99, quantity: 100, orders: 1 }, { price: 98, quantity: 200, orders: 2 }],
    timestamp: Date.now(), ltp: 100, spread: 2,
  };
  assert(validateOrderBook(good).valid, "Valid order book passes");

  const badAsks: OrderBookSnapshot = {
    asks: [{ price: 102, quantity: 100, orders: 1 }, { price: 101, quantity: 200, orders: 2 }],
    bids: [{ price: 99, quantity: 100, orders: 1 }],
    timestamp: Date.now(), ltp: 100, spread: 3,
  };
  assert(!validateOrderBook(badAsks).valid, "Rejects non-ascending asks");

  const crossed: OrderBookSnapshot = {
    asks: [{ price: 99, quantity: 100, orders: 1 }],
    bids: [{ price: 100, quantity: 100, orders: 1 }],
    timestamp: Date.now(), ltp: 100, spread: -1,
  };
  assert(!validateOrderBook(crossed).valid, "Rejects crossed book");
}

console.log("\n=== Trade Validation Tests ===");
{
  const goodTrade: TradeTick = { time: Date.now() / 1000, price: 2500, quantity: 100, side: "BUY" };
  assert(validateTrade(goodTrade, EQUITY).valid, "Valid equity trade passes");

  const decimalQty: TradeTick = { time: Date.now() / 1000, price: 2500, quantity: 22.51, side: "BUY" };
  assert(!validateTrade(decimalQty, EQUITY).valid, "Rejects decimal qty for EQUITY");

  const badPrice: TradeTick = { time: Date.now() / 1000, price: 0, quantity: 100, side: "BUY" };
  assert(!validateTrade(badPrice, EQUITY).valid, "Rejects zero price");
}

console.log("\n=== MA Calculation Tests ===");
{
  const bars: OHLCVBar[] = [];
  for (let i = 0; i < 10; i++) bars.push(makeBar({ time: 100 + i, close: 10 + i }));

  const ma3 = computeMA(bars, 3);
  assert(ma3.length === 8, `MA(3) of 10 bars = 8 results (got ${ma3.length})`);
  assert(Math.abs(ma3[0].value - 11) < 0.01, `MA(3)[0] = avg(10,11,12) = 11 (got ${ma3[0].value})`);

  const ma20 = computeMA(bars, 20);
  assert(ma20.length === 0, "MA(20) of 10 bars returns empty (not N/A)");
}

console.log("\n=== VWAP Calculation Tests ===");
{
  const bars: OHLCVBar[] = [
    makeBar({ time: 100, high: 110, low: 90, close: 100, volume: 1000 }),
    makeBar({ time: 200, high: 120, low: 95, close: 115, volume: 2000 }),
  ];
  const vwap = computeVWAP(bars);
  assert(vwap.length === 2, "VWAP has 2 points");
  const tp0 = (110 + 90 + 100) / 3;
  assert(Math.abs(vwap[0].value - tp0) < 0.01, `VWAP[0] = TP0 = ${tp0.toFixed(2)} (got ${vwap[0].value.toFixed(2)})`);
  const tp1 = (120 + 95 + 115) / 3;
  const expectedVwap1 = (tp0 * 1000 + tp1 * 2000) / 3000;
  assert(Math.abs(vwap[1].value - expectedVwap1) < 0.01, `VWAP[1] = cumulative = ${expectedVwap1.toFixed(2)} (got ${vwap[1].value.toFixed(2)})`);
}

console.log("\n=== Filter Valid Bars Test ===");
{
  const mixed: OHLCVBar[] = [
    makeBar({ time: 100 }),
    makeBar({ time: 200, high: 50 }),
    makeBar({ time: 300 }),
  ];
  const valid = filterValidBars(mixed);
  assert(valid.length === 2, `Filters out 1 invalid bar from 3 (got ${valid.length})`);
}

console.log("\n=== Market State Tests ===");
{
  assert(MarketState.CLOSED === "CLOSED", "MarketState.CLOSED = 'CLOSED'");
  assert(MarketState.OPEN === "OPEN", "MarketState.OPEN = 'OPEN'");
  assert(MarketState.PREOPEN === "PREOPEN", "MarketState.PREOPEN = 'PREOPEN'");
}

console.log(`\n========================================`);
console.log(`Results: ${passed} passed, ${failed} failed out of ${passed + failed} tests`);
console.log(`========================================\n`);

if (failed > 0) process.exit(1);
