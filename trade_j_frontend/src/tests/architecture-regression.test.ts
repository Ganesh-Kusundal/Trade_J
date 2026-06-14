/**
 * Architecture Regression Test Suite
 * Verifies the canonical data pipeline: MarketDataBus → Hooks → Components
 */

import { marketBus, MarketDataBus } from "../api/MarketDataBus";
import { resolveDataMode, DataModes } from "../api/DataModeResolver";
import type { DataMode } from "../api/DataModeResolver";
import { SimulationFeed } from "../api/SimulationFeed";
import type { TickEvent, DepthEvent, TradeEvent, CandleEvent, MarketStateEvent, BrokerStatusEvent, FeedHealthEvent, DataModeEvent } from "../api/marketContracts";
import { readFileSync, readdirSync } from "fs";
import { resolve, dirname, basename } from "path";
import { fileURLToPath } from "url";

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);

// ── Polyfills ──
if (typeof globalThis.localStorage === "undefined") {
  const store = new Map<string, string>();
  (globalThis as any).localStorage = {
    getItem: (key: string) => store.has(key) ? store.get(key)! : null,
    setItem: (key: string, value: string) => { store.set(key, String(value)); },
    removeItem: (key: string) => { store.delete(key); },
    clear: () => { store.clear(); },
    get length() { return store.size; },
    key: (i: number) => [...store.keys()][i] ?? null,
  };
}

let passed = 0;
let failed = 0;

function assert(condition: boolean, name: string) {
  if (condition) { passed++; console.log(`  PASS: ${name}`); }
  else { failed++; console.error(`  FAIL: ${name}`); }
}

// ==========================================
// MarketDataBus Tests
// ==========================================
console.log("\n=== MarketDataBus ===");
{
  const bus = new MarketDataBus();

  // Subscribe and publish
  const received: string[] = [];
  const unsub = bus.subscribe((e) => { received.push(e.type); });
  bus.publish({ type: "TICK", symbol: "RELIANCE", exchange: "NSE", ltp: 2500, change: 10, changePercent: 0.4, timestamp: Date.now() });
  assert(received.length === 1, "Subscriber receives event");
  assert(received[0] === "TICK", "Event type is TICK");

  // getLast
  const last = bus.getLast<TickEvent>("TICK");
  assert(last?.ltp === 2500, "getLast returns latest TICK");
  assert(last?.symbol === "RELIANCE", "getLast symbol is RELIANCE");

  // Unsubscribe
  unsub();
  bus.publish({ type: "TICK", symbol: "HDFCBANK", exchange: "NSE", ltp: 1600, change: 5, changePercent: 0.3, timestamp: Date.now() });
  assert(received.length === 1, "Unsubscribed does not receive events");

  // Multiple event types
  const all: string[] = [];
  bus.subscribe((e) => { all.push(e.type); });
  bus.publish({ type: "DEPTH", symbol: "RELIANCE", exchange: "NSE", bids: [], asks: [], ltp: 2500, spread: 0.5, timestamp: Date.now() });
  bus.publish({ type: "TRADE", symbol: "RELIANCE", exchange: "NSE", trades: [] });
  bus.publish({ type: "CANDLE", symbol: "RELIANCE", exchange: "NSE", candles: [] });
  assert(all.length === 3, "Multiple event types received");
  assert(all[0] === "DEPTH" && all[1] === "TRADE" && all[2] === "CANDLE", "Event types in order");

  // Clear
  bus.clear();
  assert(bus.getLast<TickEvent>("TICK") === undefined, "clear() removes stored events");

  // Listener count
  assert(bus.listenerCount === 1, "listenerCount tracks active subscribers");
}

// ==========================================
// DataModeResolver Tests
// ==========================================
console.log("\n=== DataModeResolver ===");
{
  assert(resolveDataMode(false, true, false) === DataModes.SIMULATION, "No creds + market open → SIMULATION");
  assert(resolveDataMode(false, false, false) === DataModes.HISTORICAL, "No creds + market closed → HISTORICAL");
  assert(resolveDataMode(true, true, true) === DataModes.LIVE, "Connected + market open → LIVE");
  assert(resolveDataMode(true, false, true) === DataModes.HISTORICAL, "Connected + market closed → HISTORICAL");
  assert(resolveDataMode(false, true, true) === DataModes.SIMULATION, "Not connected + market open + creds → SIMULATION");
  assert(resolveDataMode(false, false, true) === DataModes.HISTORICAL, "Not connected + market closed + creds → HISTORICAL");
}

// ==========================================
// SimulationFeed Tests
// ==========================================
console.log("\n=== SimulationFeed ===");
{
  const feed = new SimulationFeed();

  // Start and stop without errors
  try {
    feed.start("RELIANCE", "NSE", "NSE_EQ");
    feed.stop();
    assert(true, "SimulationFeed start/stop succeeds");
  } catch (e) {
    assert(false, `SimulationFeed start/stop threw: ${e}`);
  }

  // Double stop is safe
  try {
    feed.stop();
    assert(true, "SimulationFeed double stop is safe");
  } catch (e) {
    assert(false, `Double stop threw: ${e}`);
  }
}

// ==========================================
// Mode Transition Logic Tests
// ==========================================
console.log("\n=== Mode Transition Sequences ===");
{
  const states: DataMode[] = [];

  // Sequence: SIMULATION → LIVE → HISTORICAL → LIVE → SIMULATION
  states.push(resolveDataMode(false, true, false)); // SIMULATION
  assert(states[0] === DataModes.SIMULATION, "Start: SIMULATION");

  states.push(resolveDataMode(true, true, true)); // LIVE
  assert(states[1] === DataModes.LIVE, "Creds + open: LIVE");

  states.push(resolveDataMode(true, false, true)); // HISTORICAL
  assert(states[2] === DataModes.HISTORICAL, "Market closes: HISTORICAL");

  states.push(resolveDataMode(true, true, true)); // LIVE
  assert(states[3] === DataModes.LIVE, "Market reopens: LIVE");

  states.push(resolveDataMode(false, true, false)); // SIMULATION
  assert(states[4] === DataModes.SIMULATION, "Creds cleared: SIMULATION");
}

// ==========================================
// MarketDataBus Event Shape Tests
// ==========================================
console.log("\n=== MarketDataBus Event Shapes ===");
{
  const bus = new MarketDataBus();
  const events: string[] = [];
  bus.subscribe((e) => {
    const label = (e as any).symbol || (e as any).exchange || "none";
    events.push(`${e.type}:${label}`);
  });

  // TICK event
  bus.publish({ type: "TICK", symbol: "TCS", exchange: "NSE", ltp: 4000, change: 20, changePercent: 0.5, timestamp: 100 });
  assert(events[0] === "TICK:TCS", "TICK event has correct shape");

  // DEPTH event
  bus.publish({
    type: "DEPTH", symbol: "TCS", exchange: "NSE",
    bids: [{ price: 3999, quantity: 100, orders: 2 }],
    asks: [{ price: 4001, quantity: 200, orders: 3 }],
    ltp: 4000, spread: 2, timestamp: 101,
  });
  assert(events[1] === "DEPTH:TCS", "DEPTH event has correct shape");

  // TRADE event
  bus.publish({ type: "TRADE", symbol: "TCS", exchange: "NSE", trades: [{ time: 101, price: 4000, quantity: 100, side: "BUY" }] });
  assert(events[2] === "TRADE:TCS", "TRADE event has correct shape");

  // CANDLE event
  bus.publish({ type: "CANDLE", symbol: "TCS", exchange: "NSE", candles: [{ time: 100, open: 3990, high: 4010, low: 3985, close: 4000, volume: 10000 }] });
  assert(events[3] === "CANDLE:TCS", "CANDLE event has correct shape");

  // MARKET_STATE event
  const msEvent: MarketStateEvent = { type: "MARKET_STATE", state: "OPEN" as any, exchange: "NSE", dataSource: "LIVE" };
  bus.publish(msEvent);
  assert(events[4] === "MARKET_STATE:NSE", "MARKET_STATE event has correct shape");

  // BROKER_STATUS event
  const bsEvent: BrokerStatusEvent = { type: "BROKER_STATUS", connected: true, websocketConnected: true, broker: "DHAN" };
  bus.publish(bsEvent);
  assert(events[5] === "BROKER_STATUS:none", "BROKER_STATUS event has correct shape");

  // FEED_HEALTH event
  const fhEvent: FeedHealthEvent = { type: "FEED_HEALTH", health: "healthy" };
  bus.publish(fhEvent);
  assert(events[6] === "FEED_HEALTH:none", "FEED_HEALTH event has correct shape");

  // DATA_MODE event
  const dmEvent: DataModeEvent = { type: "DATA_MODE", mode: "LIVE", marketOpen: true, brokerConnected: true };
  bus.publish(dmEvent);
  assert(events[7] === "DATA_MODE:none", "DATA_MODE event has correct shape");
}

// ==========================================
// MarketDataBus Singleton Tests
// ==========================================
console.log("\n=== MarketDataBus Singleton ===");
{
  // marketBus is a singleton
  assert(marketBus.listenerCount >= 0, "Singleton marketBus exists");

  // Can subscribe/unsubscribe
  const unsub = marketBus.subscribe(() => {});
  assert(marketBus.listenerCount > 0, "Singleton subscriber added");
  unsub();

  // Clear does not throw
  marketBus.clear();
  assert(true, "Singleton clear() does not throw");
}

// ==========================================
// No Direct REST in Components
// ==========================================
console.log("\n=== No Direct REST in Components ===");
{
  const root = resolve(__dirname, "../..");
  const componentFiles = [
    "src/components/WatchlistPanel.tsx",
    "src/components/MarketOverview.tsx",
    "src/components/OrderBook.tsx",
    "src/components/CandlestickChart.tsx",
    "src/components/TradesList.tsx",
  ];
  for (const file of componentFiles) {
    const content = readFileSync(resolve(root, file), "utf8");
    const hasDirectFetch = content.includes("fetch(");
    assert(!hasDirectFetch, `${file} has no direct fetch calls`);
  }
}

// ==========================================
// Broker Isolation — No Direct Broker Imports
// ==========================================
console.log("\n=== Broker Isolation ===");
{
  const root = resolve(__dirname, "../..");
  const brokerPatterns = [
    "DhanFeedManager",
    "dhanSecurityIds",
    "dhanPacketParser",
    "api-feed.dhan.co",
    "wss://api.upstox.com",
    "wss://ws.icicidirect.com",
  ];

  const apiDir = resolve(root, "src/api");
  const apiFiles = readdirSync(apiDir)
    .filter(f => f.endsWith(".ts") || f.endsWith(".tsx"))
    .map(f => resolve(apiDir, f));

  const componentDir = resolve(root, "src/components");
  const componentFiles = readdirSync(componentDir)
    .filter(f => f.endsWith(".ts") || f.endsWith(".tsx"))
    .map(f => resolve(componentDir, f));

  for (const file of [...apiFiles, ...componentFiles]) {
    const content = readFileSync(file, "utf8");
    for (const pattern of brokerPatterns) {
      const hasPattern = content.includes(pattern);
      assert(!hasPattern, `${basename(file)} does not import or reference broker-specific module '${pattern}'`);
    }
  }
}

// ==========================================
// Generated Client Enforcement
// ==========================================
console.log("\n=== Generated Client Enforcement ===");
{
  const root = resolve(__dirname, "../..");
  const apiDir = resolve(root, "src/api");
  const apiFiles = readdirSync(apiDir)
    .filter(f => f.endsWith(".ts") && f !== "client.ts")
    .map(f => resolve(apiDir, f));

  for (const file of apiFiles) {
    const content = readFileSync(file, "utf8");
    const importsClient = content.includes('from "./client"') || content.includes("from './client'");
    assert(!importsClient, `${basename(file)} does not import from client.ts (use generated/api.ts)`);
  }
}

// ==========================================
// No Direct Fetch in Components
// ==========================================
console.log("\n=== No Direct Fetch in Components ===");
{
  const root = resolve(__dirname, "../..");
  const componentDir = resolve(root, "src/components");
  const componentFiles = readdirSync(componentDir)
    .filter(f => f.endsWith(".tsx") || f.endsWith(".ts"))
    .map(f => resolve(componentDir, f));

  for (const file of componentFiles) {
    const content = readFileSync(file, "utf8");
    const hasFetch = /\bfetch\s*\(/.test(content);
    assert(!hasFetch, `${basename(file)} does not call fetch() directly (use hooks or generated API client)`);
  }
}

console.log(`\n========================================`);
console.log(`Results: ${passed} passed, ${failed} failed out of ${passed + failed} tests`);
console.log(`========================================\n`);

if (failed > 0) process.exit(1);
