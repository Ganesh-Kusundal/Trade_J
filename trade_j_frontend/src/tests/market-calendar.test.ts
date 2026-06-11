import { MarketCalendarService } from "../domain/MarketCalendarService";
import { MarketState } from "../domain/instrument";
import type { Instrument } from "../domain/instrument";
import { AssetClass } from "../domain/instrument";

let passed = 0;
let failed = 0;

function assert(condition: boolean, name: string) {
  if (condition) { passed++; console.log(`  PASS: ${name}`); }
  else { failed++; console.error(`  FAIL: ${name}`); }
}

const GOLD_MCX: Instrument = {
  symbol: "GOLD", exchange: "MCX", assetClass: AssetClass.COMMODITY,
  currency: "INR", volumeUnit: "LOTS", tickSize: 1, lotSize: 1, qtyDecimals: 0,
};

const RELIANCE_NSE: Instrument = {
  symbol: "RELIANCE", exchange: "NSE", assetClass: AssetClass.EQUITY,
  currency: "INR", volumeUnit: "SHARES", tickSize: 0.05, lotSize: 1, qtyDecimals: 0,
};

// Helper to create a Date at a specific IST time
// IST = UTC + 5:30, so subtract 5:30 from IST to get UTC
function istDate(dateStr: string): Date {
  // dateStr format: "2026-06-10T21:46:00"
  const [date, time] = dateStr.split("T");
  const [year, month, day] = date.split("-").map(Number);
  const [h, m, s] = time.split(":").map(Number);
  // Convert IST to UTC: subtract 5 hours 30 minutes
  const totalMinutes = h * 60 + m - 330; // 330 = 5*60 + 30
  const utcH = Math.floor(totalMinutes / 60);
  const utcM = totalMinutes % 60;
  return new Date(Date.UTC(year, month - 1, day, (utcH + 24) % 24, (utcM + 60) % 60, s || 0));
}

console.log("\n=== Market Calendar Tests ===");
{
  // MCX at 21:46 IST should be OPEN (DST active in June)
  const t1 = istDate("2026-06-10T21:46:00");
  assert(MarketCalendarService.getMarketState("MCX", GOLD_MCX, t1) === MarketState.OPEN,
    "MCX GOLD at 21:46 IST → OPEN");

  // MCX at 23:35 IST should be CLOSED
  const t2 = istDate("2026-06-10T23:35:00");
  assert(MarketCalendarService.getMarketState("MCX", GOLD_MCX, t2) === MarketState.CLOSED,
    "MCX GOLD at 23:35 IST → CLOSED");

  // MCX at 08:30 IST should be CLOSED (before open)
  const t3 = istDate("2026-06-10T08:30:00");
  assert(MarketCalendarService.getMarketState("MCX", GOLD_MCX, t3) === MarketState.CLOSED,
    "MCX GOLD at 08:30 IST → CLOSED");

  // MCX at 10:00 IST should be OPEN
  const t4 = istDate("2026-06-10T10:00:00");
  assert(MarketCalendarService.getMarketState("MCX", GOLD_MCX, t4) === MarketState.OPEN,
    "MCX GOLD at 10:00 IST → OPEN");

  // NSE at 21:46 IST should be CLOSED
  assert(MarketCalendarService.getMarketState("NSE", RELIANCE_NSE, t1) === MarketState.CLOSED,
    "NSE RELIANCE at 21:46 IST → CLOSED");

  // NSE at 10:00 IST should be OPEN
  assert(MarketCalendarService.getMarketState("NSE", RELIANCE_NSE, t4) === MarketState.OPEN,
    "NSE RELIANCE at 10:00 IST → OPEN");

  // NSE at 09:05 IST should be PREOPEN
  const t5 = istDate("2026-06-10T09:05:00");
  assert(MarketCalendarService.getMarketState("NSE", RELIANCE_NSE, t5) === MarketState.PREOPEN,
    "NSE RELIANCE at 09:05 IST → PREOPEN");

  // NSE at 15:45 IST should be AUCTION
  const t6 = istDate("2026-06-10T15:45:00");
  assert(MarketCalendarService.getMarketState("NSE", RELIANCE_NSE, t6) === MarketState.AUCTION,
    "NSE RELIANCE at 15:45 IST → AUCTION");

  // Weekend test (Saturday)
  const sat = new Date("2026-06-13T10:00:00Z"); // June 13 2026 is Saturday
  assert(MarketCalendarService.getMarketState("NSE", RELIANCE_NSE, sat) === MarketState.CLOSED,
    "NSE on Saturday → CLOSED");
  assert(MarketCalendarService.getMarketState("MCX", GOLD_MCX, sat) === MarketState.CLOSED,
    "MCX on Saturday → CLOSED");
}

console.log("\n=== US DST Calculation Tests ===");
{
  // June 2026 should be DST
  const june = new Date("2026-06-15T12:00:00Z");
  assert(MarketCalendarService.isUSDST(june) === true, "June 2026 → DST active");

  // January 2026 should NOT be DST
  const jan = new Date("2026-01-15T12:00:00Z");
  assert(MarketCalendarService.isUSDST(jan) === false, "January 2026 → DST inactive");

  // December 2026 should NOT be DST
  const dec = new Date("2026-12-15T12:00:00Z");
  assert(MarketCalendarService.isUSDST(dec) === false, "December 2026 → DST inactive");
}

console.log("\n=== Next Transition Tests ===");
{
  assert(MarketCalendarService.getNextTransition("MCX", MarketState.OPEN) === "CLOSE at 23:30 IST",
    "MCX OPEN → next: CLOSE at 23:30 IST");

  assert(MarketCalendarService.getNextTransition("NSE", MarketState.OPEN) === "CLOSE at 15:30 IST",
    "NSE OPEN → next: CLOSE at 15:30 IST");

  assert(MarketCalendarService.getNextTransition("CDS", MarketState.OPEN) === "CLOSE at 17:00 IST",
    "CDS OPEN → next: CLOSE at 17:00 IST");

  assert(MarketCalendarService.getNextTransition("NSE", MarketState.CLOSED) === "PREOPEN tomorrow 09:00 IST",
    "NSE CLOSED → next: PREOPEN tomorrow 09:00 IST");
}

console.log(`\n========================================`);
console.log(`Results: ${passed} passed, ${failed} failed out of ${passed + failed} tests`);
console.log(`========================================\n`);

if (failed > 0) process.exit(1);
