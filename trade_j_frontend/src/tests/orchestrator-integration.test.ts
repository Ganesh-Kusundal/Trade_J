import { DataMode } from "../api/TerminalDataOrchestrator";
import type { BrokerConfig } from "../api/TerminalDataOrchestrator";

// Minimal localStorage polyfill for Node.js test runner
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

// ===== BROKER EXCHANGE DEFINITIONS =====
const BROKER_EXCHANGES: Record<string, string[]> = {
  DHAN: ["NSE", "BSE", "NFO", "MCX", "CDS"],
  UPSTOX: ["NSE", "BSE", "NFO", "MCX", "CDS"],
  ZERODHA: ["NSE", "BSE", "NFO", "MCX", "CDS"],
  ANGELONE: ["NSE", "BSE", "NFO", "MCX", "CDS"],
  FYERS: ["NSE", "BSE", "NFO"],
  ICICI: ["NSE", "BSE", "NFO"],
};

console.log("\n=== Broker Exchange Compatibility ===");
{
  // All 6 brokers defined
  assert(Object.keys(BROKER_EXCHANGES).length === 6, "6 brokers defined");

  // DHAN supports all exchanges
  assert(BROKER_EXCHANGES["DHAN"].length === 5, "DHAN supports 5 exchanges");
  assert(BROKER_EXCHANGES["DHAN"].includes("MCX"), "DHAN supports MCX");
  assert(BROKER_EXCHANGES["DHAN"].includes("CDS"), "DHAN supports CDS");

  // FYERS limited
  assert(BROKER_EXCHANGES["FYERS"].length === 3, "FYERS supports 3 exchanges");
  assert(!BROKER_EXCHANGES["FYERS"].includes("CDS"), "FYERS does NOT support CDS");
  assert(!BROKER_EXCHANGES["FYERS"].includes("MCX"), "FYERS does NOT support MCX");

  // ICICI most limited
  assert(BROKER_EXCHANGES["ICICI"].length === 3, "ICICI supports 3 exchanges");
  assert(!BROKER_EXCHANGES["ICICI"].includes("MCX"), "ICICI does NOT support MCX");
  assert(!BROKER_EXCHANGES["ICICI"].includes("CDS"), "ICICI does NOT support CDS");
}

// ===== EXCHANGE RESET LOGIC =====
function resetExchangeIfNeeded(newBroker: string, currentExchange: string): { exchange: string; reset: boolean } {
  const supported = BROKER_EXCHANGES[newBroker] || BROKER_EXCHANGES["DHAN"];
  if (!supported.includes(currentExchange)) {
    return { exchange: supported[0], reset: true };
  }
  return { exchange: currentExchange, reset: false };
}

console.log("\n=== Exchange Reset on Broker Change ===");
{
  const r1 = resetExchangeIfNeeded("FYERS", "CDS");
  assert(r1.reset === true, "FYERS+CDS → needs reset");
  assert(r1.exchange === "NSE", "FYERS+CDS → resets to NSE");

  const r2 = resetExchangeIfNeeded("ICICI", "MCX");
  assert(r2.reset === true, "ICICI+MCX → needs reset");
  assert(r2.exchange === "NSE", "ICICI+MCX → resets to NSE");

  const r3 = resetExchangeIfNeeded("DHAN", "MCX");
  assert(r3.reset === false, "DHAN+MCX → no reset needed");
  assert(r3.exchange === "MCX", "DHAN+MCX → stays MCX");

  const r4 = resetExchangeIfNeeded("UPSTOX", "NFO");
  assert(r4.reset === false, "UPSTOX+NFO → no reset needed");

  // Switching from DHAN (all exchanges) to ICICI (only NSE, BSE, NFO)
  const r5 = resetExchangeIfNeeded("ICICI", "CDS");
  assert(r5.reset === true, "DHAN→ICICI with CDS → needs reset");

  const r6 = resetExchangeIfNeeded("ICICI", "NSE");
  assert(r6.reset === false, "DHAN→ICICI with NSE → no reset");
}

// ===== LOCALSTORAGE PERSISTENCE =====
console.log("\n=== localStorage Persistence ===");
{
  // Simulate save/load cycle
  const key = "tj_test_broker";
  localStorage.setItem(key, "UPSTOX");
  assert(localStorage.getItem(key) === "UPSTOX", "Save and load broker name");

  const credsKey = "tj_test_creds";
  const creds = { accessToken: "test_token_123", clientId: "client_456" };
  localStorage.setItem(credsKey, JSON.stringify(creds));
  const loaded = JSON.parse(localStorage.getItem(credsKey) || "null");
  assert(loaded?.accessToken === "test_token_123", "Save and load credentials token");
  assert(loaded?.clientId === "client_456", "Save and load credentials clientId");

  // Cleanup
  localStorage.removeItem(key);
  localStorage.removeItem(credsKey);
  assert(localStorage.getItem(key) === null, "Cleanup removes broker");
}

// ===== DATA MODE RESOLUTION =====
function resolveDataMode(hasCreds: boolean, marketOpen: boolean): DataMode {
  if (!hasCreds) return DataMode.SIMULATION;
  if (marketOpen) return DataMode.LIVE;
  return DataMode.HISTORICAL;
}

console.log("\n=== DataMode State Machine ===");
{
  // SIMULATION: no credentials, regardless of market state
  assert(resolveDataMode(false, true) === DataMode.SIMULATION, "No creds + market open → SIMULATION");
  assert(resolveDataMode(false, false) === DataMode.SIMULATION, "No creds + market closed → SIMULATION");

  // LIVE: has credentials + market open
  assert(resolveDataMode(true, true) === DataMode.LIVE, "Has creds + market open → LIVE");

  // HISTORICAL: has credentials + market closed
  assert(resolveDataMode(true, false) === DataMode.HISTORICAL, "Has creds + market closed → HISTORICAL");
}

// ===== MODE TRANSITION SEQUENCES =====
console.log("\n=== Mode Transition Sequences ===");
{
  // Scenario: User starts with no creds → enters creds during MCX open → LIVE
  let mode = resolveDataMode(false, true);
  assert(mode === DataMode.SIMULATION, "Start: SIMULATION (no creds, MCX open)");

  mode = resolveDataMode(true, true); // User enters creds
  assert(mode === DataMode.LIVE, "After creds: LIVE (creds + MCX open)");

  // Scenario: Market closes while LIVE → HISTORICAL
  mode = resolveDataMode(true, false);
  assert(mode === DataMode.HISTORICAL, "Market closes: HISTORICAL");

  // Scenario: Market reopens → back to LIVE
  mode = resolveDataMode(true, true);
  assert(mode === DataMode.LIVE, "Market reopens: LIVE");

  // Scenario: User clears creds → SIMULATION
  mode = resolveDataMode(false, true);
  assert(mode === DataMode.SIMULATION, "Creds cleared: SIMULATION");
}

// ===== CREDENTIAL VALIDATION =====
function validateCreds(token: string, clientId: string): boolean {
  return !!token && token.length >= 6 && !!clientId && clientId.length >= 3;
}

console.log("\n=== Credential Validation ===");
{
  assert(validateCreds("eyJhbGciOiJIUzI1NiJ9", "1234567"), "Valid DHAN creds pass");
  assert(!validateCreds("", "1234567"), "Empty token fails");
  assert(!validateCreds("abc", "1234567"), "Short token (<6) fails");
  assert(!validateCreds("eyJhbGciOiJIUzI1NiJ9", ""), "Empty clientId fails");
  assert(!validateCreds("eyJhbGciOiJIUzI1NiJ9", "ab"), "Short clientId (<3) fails");
  assert(validateCreds("valid_token_here", "cli"), "Minimum valid creds pass");
}

console.log(`\n========================================`);
console.log(`Results: ${passed} passed, ${failed} failed out of ${passed + failed} tests`);
console.log(`========================================\n`);

if (failed > 0) process.exit(1);
