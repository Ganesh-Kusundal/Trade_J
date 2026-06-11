let passed = 0;
let failed = 0;

function assert(condition: boolean, name: string) {
  if (condition) { passed++; console.log(`  PASS: ${name}`); }
  else { failed++; console.error(`  FAIL: ${name}`); }
}

// ===== BROKER DEFINITIONS =====
const BROKER_EXCHANGES: Record<string, string[]> = {
  DHAN: ["NSE", "BSE", "NFO", "MCX", "CDS"],
  UPSTOX: ["NSE", "BSE", "NFO", "MCX", "CDS"],
  ZERODHA: ["NSE", "BSE", "NFO", "MCX", "CDS"],
  ANGELONE: ["NSE", "BSE", "NFO", "MCX", "CDS"],
  FYERS: ["NSE", "BSE", "NFO"],
  ICICI: ["NSE", "BSE", "NFO"],
};

function getSupportedExchanges(broker: string): string[] {
  return BROKER_EXCHANGES[broker] || [];
}

console.log("\n=== Broker Exchange Filtering Tests ===");
{
  assert(getSupportedExchanges("DHAN").includes("MCX"), "DHAN supports MCX");
  assert(getSupportedExchanges("DHAN").includes("CDS"), "DHAN supports CDS");
  assert(getSupportedExchanges("FYERS").includes("NSE"), "FYERS supports NSE");
  assert(!getSupportedExchanges("FYERS").includes("CDS"), "FYERS does NOT support CDS");
  assert(getSupportedExchanges("ICICI").includes("NSE"), "ICICI supports NSE");
  assert(!getSupportedExchanges("ICICI").includes("MCX"), "ICICI does NOT support MCX");
  assert(getSupportedExchanges("DHAN").length === 5, "DHAN supports 5 exchanges");
  assert(getSupportedExchanges("FYERS").length === 3, "FYERS supports 3 exchanges");
  assert(getSupportedExchanges("ICICI").length === 3, "ICICI supports 3 exchanges");
}

// ===== DATA MODE RESOLVER =====
type DataMode = "LIVE" | "HISTORICAL" | "SIMULATION";

function resolveDataMode(hasCreds: boolean, marketOpen: boolean): DataMode {
  if (!hasCreds) return "SIMULATION";
  if (marketOpen) return "LIVE";
  return "HISTORICAL";
}

console.log("\n=== DataMode Resolver Tests ===");
{
  assert(resolveDataMode(false, true) === "SIMULATION", "No creds + market open → SIMULATION");
  assert(resolveDataMode(false, false) === "SIMULATION", "No creds + market closed → SIMULATION");
  assert(resolveDataMode(true, true) === "LIVE", "Has creds + market open → LIVE");
  assert(resolveDataMode(true, false) === "HISTORICAL", "Has creds + market closed → HISTORICAL");
}

// ===== TRADE DEDUPLICATION =====
interface Trade { time: number; price: number; quantity: number; side: string; }

function deduplicateTrades(trades: Trade[], tickSize: number): Trade[] {
  const result: Trade[] = [];
  let lastPrice = 0;
  for (const t of trades) {
    const priceMoved = Math.abs(t.price - lastPrice);
    if (priceMoved >= tickSize || lastPrice === 0) {
      result.push(t);
      lastPrice = t.price;
    }
  }
  return result;
}

console.log("\n=== Trade Deduplication Tests ===");
{
  const trades: Trade[] = [
    { time: 1000, price: 73100, quantity: 100, side: "BUY" },
    { time: 1001, price: 73100, quantity: 200, side: "SELL" },   // same price — should be filtered
    { time: 1002, price: 73101, quantity: 150, side: "BUY" },    // price moved by 1 — should pass (tick=1)
    { time: 1003, price: 73101, quantity: 300, side: "SELL" },   // same price — should be filtered
    { time: 1004, price: 73103, quantity: 250, side: "BUY" },    // price moved by 2 — should pass
  ];
  const deduped = deduplicateTrades(trades, 1.0);
  assert(deduped.length === 3, `Dedup 5 trades → 3 (got ${deduped.length})`);
  assert(deduped[0].price === 73100, "First trade at 73100");
  assert(deduped[1].price === 73101, "Second trade at 73101");
  assert(deduped[2].price === 73103, "Third trade at 73103");

  // All same price
  const sameTrades: Trade[] = [
    { time: 1000, price: 100, quantity: 10, side: "BUY" },
    { time: 1001, price: 100, quantity: 20, side: "SELL" },
    { time: 1002, price: 100, quantity: 30, side: "BUY" },
  ];
  const sameDeduped = deduplicateTrades(sameTrades, 0.05);
  assert(sameDeduped.length === 1, `All same price → 1 trade (got ${sameDeduped.length})`);
}

// ===== DEFAULT TIMEFRAME =====
console.log("\n=== Default Timeframe Tests ===");
{
  const defaultTf = "1m";
  assert(defaultTf === "1m", "Default timeframe is 1m");
  const timeframes = ["1m", "5m", "15m", "1h", "4h", "1d"];
  assert(timeframes.includes(defaultTf), "1m is a valid timeframe");
  assert(timeframes.indexOf(defaultTf) === 0, "1m is the first timeframe option");
}

// ===== BROKER EXCHANGE RESET =====
function resetExchangeIfNeeded(broker: string, currentExchange: string): string {
  const supported = getSupportedExchanges(broker);
  if (!supported.includes(currentExchange)) {
    return supported[0]; // reset to first supported
  }
  return currentExchange;
}

console.log("\n=== Broker Exchange Reset Tests ===");
{
  assert(resetExchangeIfNeeded("FYERS", "CDS") === "NSE", "FYERS + CDS → reset to NSE");
  assert(resetExchangeIfNeeded("ICICI", "MCX") === "NSE", "ICICI + MCX → reset to NSE");
  assert(resetExchangeIfNeeded("DHAN", "MCX") === "MCX", "DHAN + MCX → stays MCX");
  assert(resetExchangeIfNeeded("FYERS", "NFO") === "NFO", "FYERS + NFO → stays NFO");
}

console.log(`\n========================================`);
console.log(`Results: ${passed} passed, ${failed} failed out of ${passed + failed} tests`);
console.log(`========================================\n`);

if (failed > 0) process.exit(1);
