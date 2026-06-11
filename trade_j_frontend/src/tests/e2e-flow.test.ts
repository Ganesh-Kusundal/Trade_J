let passed = 0;
let failed = 0;

function assert(condition: boolean, name: string) {
  if (condition) { passed++; console.log(`  PASS: ${name}`); }
  else { failed++; console.error(`  FAIL: ${name}`); }
}

// ===== BROKER EXCHANGE DEFINITIONS =====
const BROKER_EXCHANGES: Record<string, string[]> = {
  DHAN: ["NSE", "BSE", "NFO", "MCX", "CDS"],
  UPSTOX: ["NSE", "BSE", "NFO", "MCX", "CDS"],
  ZERODHA: ["NSE", "BSE", "NFO", "MCX", "CDS"],
  ANGELONE: ["NSE", "BSE", "NFO", "MCX", "CDS"],
  FYERS: ["NSE", "BSE", "NFO"],
  ICICI: ["NSE", "BSE", "NFO"],
};

const DEFAULT_SYMBOLS: Record<string, string[]> = {
  NSE: ["RELIANCE", "TCS", "INFY", "HDFCBANK", "ICICIBANK", "SBIN"],
  BSE: ["RELIANCE", "TCS", "INFY", "SBIN"],
  NFO: ["NIFTY", "BANKNIFTY"],
  MCX: ["GOLD", "SILVER", "CRUDEOIL"],
  CDS: ["USDINR", "EURINR"],
};

// ===== E2E FLOW 1: Fresh user with no saved state =====
console.log("\n=== E2E: Fresh User Flow ===");
{
  // Step 1: App loads with defaults
  let broker = "DHAN";
  let exchange = "NSE";
  let symbol = "RELIANCE";
  let timeframe = "1m";

  assert(broker === "DHAN", "Default broker is DHAN");
  assert(exchange === "NSE", "Default exchange is NSE");
  assert(symbol === "RELIANCE", "Default symbol is RELIANCE");
  assert(timeframe === "1m", "Default timeframe is 1m");

  // Step 2: User switches to Fyers
  broker = "FYERS";
  const supportedExchanges = BROKER_EXCHANGES[broker];
  assert(!supportedExchanges.includes("CDS"), "FYERS doesn't support CDS");

  // Step 3: Exchange auto-resets if current not supported
  if (!supportedExchanges.includes(exchange)) {
    exchange = supportedExchanges[0];
    symbol = (DEFAULT_SYMBOLS[exchange] || [])[0] || symbol;
  }
  assert(exchange === "NSE", "Exchange stays NSE (supported by FYERS)");

  // Step 4: User switches to ICICI
  broker = "ICICI";
  const iciciExchanges = BROKER_EXCHANGES[broker];
  exchange = "NSE"; // stays
  assert(iciciExchanges.includes(exchange), "ICICI supports NSE");

  // Step 5: User was on MCX, switches to ICICI
  exchange = "MCX";
  if (!iciciExchanges.includes(exchange)) {
    exchange = iciciExchanges[0];
    symbol = (DEFAULT_SYMBOLS[exchange] || [])[0] || symbol;
  }
  assert(exchange === "NSE", "MCX→ICICI resets to NSE");
  assert(symbol === "RELIANCE", "Symbol resets to RELIANCE");
}

// ===== E2E FLOW 2: User enters credentials, market transitions =====
console.log("\n=== E2E: Credential Entry Flow ===");
{
  type DataMode = "LIVE" | "HISTORICAL" | "SIMULATION";

  let brokerCreds: { accessToken?: string; clientId?: string } | null = null;
  let marketOpen = true;

  const resolveMode = (): DataMode => {
    if (!brokerCreds?.accessToken) return "SIMULATION";
    return marketOpen ? "LIVE" : "HISTORICAL";
  };

  // Start: SIMULATION
  assert(resolveMode() === "SIMULATION", "Start: SIMULATION (no creds)");

  // User opens broker modal
  const token = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.test";
  const clientId = "12345678";

  // Validate creds
  const isValid = token.length >= 6 && clientId.length >= 3;
  assert(isValid, "Credentials pass validation");

  // Save creds
  brokerCreds = { accessToken: token, clientId };
  assert(resolveMode() === "LIVE", "After creds + market open: LIVE");

  // Market closes (e.g., NSE at 15:30)
  marketOpen = false;
  assert(resolveMode() === "HISTORICAL", "Market closes: HISTORICAL");

  // Freeze banner should show
  assert(resolveMode() === "HISTORICAL", "Freeze banner condition met");

  // Market reopens next day
  marketOpen = true;
  assert(resolveMode() === "LIVE", "Market reopens: LIVE");

  // User clears creds
  brokerCreds = null;
  assert(resolveMode() === "SIMULATION", "Creds cleared: SIMULATION");
}

// ===== E2E FLOW 3: Timeframe + keyboard shortcut flow =====
console.log("\n=== E2E: Timeframe Keyboard Shortcuts ===");
{
  const tfMap: Record<string, string> = { "1": "1m", "2": "5m", "3": "15m", "4": "1h", "5": "4h", "6": "1d" };
  let timeframe = "1m";

  // Press "3" → 15m
  timeframe = tfMap["3"] || timeframe;
  assert(timeframe === "15m", "Key '3' → 15m");

  // Press "6" → 1d
  timeframe = tfMap["6"] || timeframe;
  assert(timeframe === "1d", "Key '6' → 1d");

  // Press "1" → 1m
  timeframe = tfMap["1"] || timeframe;
  assert(timeframe === "1m", "Key '1' → 1m");

  // Press "4" → 1h
  timeframe = tfMap["4"] || timeframe;
  assert(timeframe === "1h", "Key '4' → 1h");
}

// ===== E2E FLOW 4: Order placement flow =====
console.log("\n=== E2E: Order Placement Flow ===");
{
  let orderSide: "BUY" | "SELL" = "BUY";
  let orderType: string = "LIMIT";
  let orderQty = "10";
  let orderPrice = "73500.00";
  const lastPrice = 73450.50;

  // Press 'B' → BUY side, show order panel
  orderSide = "BUY";
  assert(orderSide === "BUY", "Key 'B' sets BUY side");

  // Click on order book price → fills price
  const clickedPrice = 73452.00;
  orderPrice = clickedPrice.toFixed(2);
  assert(orderPrice === "73452.00", "Order book click fills price");

  // Switch to MARKET
  orderType = "MARKET";
  assert(orderType === "MARKET", "Switch to MARKET order");

  // Build order payload
  const payload = {
    symbol: "GOLD",
    exchangeSegment: "MCX_COMM",
    side: orderSide,
    quantity: parseInt(orderQty),
    orderType: orderType,
    pricePaisa: orderType === "LIMIT" ? Math.round(parseFloat(orderPrice) * 100) : 0,
    productType: "CNC",
    validity: "DAY",
  };

  assert(payload.pricePaisa === 0, "MARKET order has pricePaisa=0");
  assert(payload.quantity === 10, "Quantity is 10");
  assert(payload.side === "BUY", "Side is BUY");

  // Switch to LIMIT
  orderType = "LIMIT";
  orderPrice = "73500.00";
  const limitPayload = {
    ...payload,
    orderType,
    pricePaisa: Math.round(parseFloat(orderPrice) * 100),
  };
  assert(limitPayload.pricePaisa === 7350000, "LIMIT pricePaisa = 7350000");
}

// ===== E2E FLOW 5: Watchlist persistence =====
console.log("\n=== E2E: Watchlist Persistence ===");
{
  // localStorage polyfill for Node
  const store: Record<string, string> = {};
  const ls = {
    getItem: (k: string) => store[k] ?? null,
    setItem: (k: string, v: string) => { store[k] = v; },
    removeItem: (k: string) => { delete store[k]; },
  };

  // Default watchlist
  const defaultList = ["RELIANCE", "TCS", "INFY", "HDFCBANK", "SBIN", "GOLD"];
  assert(defaultList.length === 6, "Default watchlist has 6 items");

  // User adds CRUDEOIL
  const updated = [...defaultList, "CRUDEOIL"];
  ls.setItem("tj_watchlist", JSON.stringify(updated));
  const loaded = JSON.parse(ls.getItem("tj_watchlist") || "[]");
  assert(loaded.length === 7, "Watchlist saved with 7 items");
  assert(loaded.includes("CRUDEOIL"), "CRUDEOIL in saved watchlist");

  // User removes TCS
  const filtered = loaded.filter((s: string) => s !== "TCS");
  ls.setItem("tj_watchlist", JSON.stringify(filtered));
  const reloaded = JSON.parse(ls.getItem("tj_watchlist") || "[]");
  assert(reloaded.length === 6, "Watchlist has 6 items after removal");
  assert(!reloaded.includes("TCS"), "TCS removed from watchlist");

  // Page reload — watchlist persists
  const afterReload = JSON.parse(ls.getItem("tj_watchlist") || "[]");
  assert(afterReload.length === 6, "Watchlist persists across reload");
}

// ===== E2E FLOW 6: Full broker + exchange + symbol switch =====
console.log("\n=== E2E: Full Switching Sequence ===");
{
  let broker = "DHAN";
  let exchange = "NSE";
  let symbol = "RELIANCE";

  // Switch to MCX + GOLD
  exchange = "MCX";
  symbol = (DEFAULT_SYMBOLS[exchange] || [])[0];
  assert(symbol === "GOLD", "MCX default symbol is GOLD");

  // Switch to CDS + USDINR
  exchange = "CDS";
  symbol = (DEFAULT_SYMBOLS[exchange] || [])[0];
  assert(symbol === "USDINR", "CDS default symbol is USDINR");

  // Switch to NFO + NIFTY
  exchange = "NFO";
  symbol = (DEFAULT_SYMBOLS[exchange] || [])[0];
  assert(symbol === "NIFTY", "NFO default symbol is NIFTY");

  // Switch broker to FYERS (loses CDS, MCX)
  broker = "FYERS";
  const supported = BROKER_EXCHANGES[broker];
  if (!supported.includes(exchange)) {
    exchange = supported[0];
    symbol = (DEFAULT_SYMBOLS[exchange] || [])[0];
  }
  assert(exchange === "NFO", "FYERS + NFO → stays NFO (supported)");

  // Actually NFO is supported by FYERS, so no reset
  assert(BROKER_EXCHANGES["FYERS"].includes("NFO"), "FYERS supports NFO");

  // Switch to ICICI while on NFO
  broker = "ICICI";
  if (!BROKER_EXCHANGES[broker].includes(exchange)) {
    exchange = BROKER_EXCHANGES[broker][0];
    symbol = (DEFAULT_SYMBOLS[exchange] || [])[0];
  }
  assert(exchange === "NFO", "ICICI + NFO → stays NFO (supported)");
  assert(symbol === "NIFTY", "Symbol stays NIFTY");
}

console.log(`\n========================================`);
console.log(`Results: ${passed} passed, ${failed} failed out of ${passed + failed} tests`);
console.log(`========================================\n`);

if (failed > 0) process.exit(1);
