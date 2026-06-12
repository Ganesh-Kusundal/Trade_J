import { GatewayFeedManager } from "../api/GatewayFeedManager";
import { marketBus } from "../api/MarketDataBus";
import type { MarketEvent, TickEvent, CandleEvent } from "../api/marketContracts";

let passed = 0;
let failed = 0;

function assert(condition: boolean, name: string) {
  if (condition) { passed++; console.log(`  PASS: ${name}`); }
  else { failed++; console.error(`  FAIL: ${name}`); }
}

/** Build a binary frame: [1 byte topic][8 bytes big-endian seq][N bytes JSON] */
function buildFrame(topicId: number, sequence: bigint, payload: string): ArrayBuffer {
  const payloadBytes = new TextEncoder().encode(payload);
  const buf = new ArrayBuffer(9 + payloadBytes.length);
  const view = new DataView(buf);
  view.setUint8(0, topicId);
  view.setBigUint64(1, sequence, false);
  new Uint8Array(buf, 9).set(payloadBytes);
  return buf;
}

function makeManager(): GatewayFeedManager {
  return new GatewayFeedManager({
    gatewayUrl: "ws://unused",
    symbol: "RELIANCE",
    exchange: "NSE",
  });
}

// ── Test 1: MARKET_TICK ──────────────────────────────────────────
console.log("\n=== Gateway Feed: MARKET_TICK ===");
{
  const mgr = makeManager();
  const events: MarketEvent[] = [];
  const unsub = marketBus.subscribe(e => events.push(e));

  const frame = buildFrame(0, 42n, JSON.stringify({ symbol: "RELIANCE", ltpPaisa: 250000 }));
  (mgr as any).handleFrame(frame);

  const tick = events.find(e => e.type === "TICK") as TickEvent | undefined;
  assert(tick !== undefined, "TICK event published");
  assert(tick?.symbol === "RELIANCE", "symbol is RELIANCE");
  assert(tick?.ltp === 2500, "ltp is 250000/100 = 2500");
  assert(tick?.exchange === "NSE", "exchange defaults to config");

  unsub();
  marketBus.clear();
}

// ── Test 2: CANDLE_CLOSED ───────────────────────────────────────
console.log("\n=== Gateway Feed: CANDLE_CLOSED ===");
{
  const mgr = makeManager();
  const events: MarketEvent[] = [];
  const unsub = marketBus.subscribe(e => events.push(e));

  const payload = JSON.stringify({
    candle: {
      symbol: "RELIANCE",
      startTimeMs: 1700000000000,
      openPaisa: 250000,
      highPaisa: 255000,
      lowPaisa: 248000,
      closePaisa: 253000,
      volume: 12345,
    },
  });
  const frame = buildFrame(3, 100n, payload);
  (mgr as any).handleFrame(frame);

  const candle = events.find(e => e.type === "CANDLE") as CandleEvent | undefined;
  assert(candle !== undefined, "CANDLE event published");
  assert(candle?.isPartial === false, "isPartial is false for CANDLE_CLOSED");
  assert(candle?.candles[0]?.open === 2500, "open = 250000/100");
  assert(candle?.candles[0]?.high === 2550, "high = 255000/100");
  assert(candle?.candles[0]?.volume === 12345, "volume preserved");

  unsub();
  marketBus.clear();
}

// ── Test 3: Short frame (< 9 bytes) ─────────────────────────────
console.log("\n=== Gateway Feed: Short Frame ===");
{
  const mgr = makeManager();
  const events: MarketEvent[] = [];
  const unsub = marketBus.subscribe(e => events.push(e));

  const short = new ArrayBuffer(5);
  let crashed = false;
  try {
    (mgr as any).handleFrame(short);
  } catch {
    crashed = true;
  }

  assert(!crashed, "no crash on short frame");
  assert(events.length === 0, "no events published for short frame");

  unsub();
  marketBus.clear();
}

// ── Test 4: Malformed JSON ──────────────────────────────────────
console.log("\n=== Gateway Feed: Malformed JSON ===");
{
  const mgr = makeManager();
  const events: MarketEvent[] = [];
  const unsub = marketBus.subscribe(e => events.push(e));

  const frame = buildFrame(0, 1n, "{not valid json!!!");
  let crashed = false;
  try {
    (mgr as any).handleFrame(frame);
  } catch {
    crashed = true;
  }

  assert(!crashed, "no crash on malformed JSON");
  assert(events.length === 0, "no events published for malformed JSON");

  unsub();
  marketBus.clear();
}

// ── Summary ──────────────────────────────────────────────────────
console.log(`\n=== Results: ${passed} passed, ${failed} failed ===\n`);
if (failed > 0) process.exit(1);
