import { readFileSync, existsSync } from "fs";
import { resolve, dirname } from "path";
import { fileURLToPath } from "url";

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);
const root = resolve(__dirname, "../..");

type Check = {
  name: string;
  pass: boolean;
  details: string[];
};

const read = (file: string): string => readFileSync(resolve(root, file), "utf8");
const exists = (file: string): boolean => existsSync(resolve(root, file));

const files = {
  app: "src/App.tsx",
  orchestrator: "src/api/TerminalDataOrchestrator.ts",
  orderBook: "src/components/OrderBook.tsx",
  watchlist: "src/components/WatchlistPanel.tsx",
  marketOverview: "src/components/MarketOverview.tsx",
  chart: "src/components/CandlestickChart.tsx",
};

const checks: Check[] = [];

function check(name: string, pass: boolean, details: string[]) {
  checks.push({ name, pass, details });
}

// 1. No direct REST calls in components (must use generated API client via hooks or dynamic import)
check(
  "No Direct REST in Components",
  !read(files.watchlist).includes('await fetch(') &&
    !read(files.marketOverview).includes('await fetch('),
  [
    "WatchlistPanel must not call fetch() directly",
    "MarketOverview must not call fetch() directly",
  ]
);

// 2. No Dhan-specific imports anywhere
check(
  "No Broker-Specific Imports",
  !exists("src/api/DhanFeedManager.ts") &&
    !exists("src/api/dhanSecurityIds.ts") &&
    !exists("src/api/dhanPacketParser.ts") &&
    !read(files.orchestrator).includes("DhanFeedManager") &&
    !read(files.orchestrator).includes("dhanSecurityIds"),
  [
    "DhanFeedManager.ts must not exist",
    "dhanSecurityIds.ts must not exist",
    "dhanPacketParser.ts must not exist",
    "Orchestrator must not reference Dhan modules",
  ]
);

// 3. Generated API client is used (no client.ts imports in API files)
check(
  "Generated Client Usage",
  !read("src/api/marketData.ts").includes('from "./client"') &&
    !read("src/api/orders.ts").includes('from "./client"') &&
    !read("src/api/marketSession.ts").includes('from "./client"') &&
    read("src/api/marketData.ts").includes("generated/api"),
  [
    "marketData.ts must use generated API client",
    "orders.ts must use generated API client",
    "marketSession.ts must use generated API client",
  ]
);

// 4. Gateway WebSocket for live data
check(
  "Gateway WebSocket for Live Data",
  read(files.orchestrator).includes("GatewayFeedManager") &&
    read(files.orchestrator).includes("GatewayTopic") &&
    read(files.orchestrator).includes("marketBus"),
  [
    "Orchestrator must use GatewayFeedManager for live data",
    "Orchestrator must subscribe to GatewayTopic subscriptions",
    "Live data must flow through MarketDataBus",
  ]
);

// 5. Core infrastructure files exist
check(
  "Core Infrastructure Files",
  exists("src/api/DataModeResolver.ts") &&
    exists("src/lib/CandleAggregator.ts") &&
    exists("src/api/marketContracts.ts") &&
    exists("src/api/GatewayFeedManager.ts") &&
    exists("src/api/MarketDataBus.ts"),
  [
    "DataModeResolver.ts must exist",
    "CandleAggregator.ts must exist",
    "marketContracts.ts must exist",
    "GatewayFeedManager.ts must exist",
    "MarketDataBus.ts must exist",
  ]
);

// 6. Components don't duplicate parent state (props-driven, not internally fetched)
check(
  "Components Are Props-Driven",
  !read("src/components/CandlestickChart.tsx").includes("useState<OHLCVBar[]>") &&
    !read("src/components/OrderBook.tsx").includes("useState<L2Level[]>") &&
    !read("src/components/TradesList.tsx").includes("useState<TradeTick[]>"),
  [
    "CandlestickChart must receive bars via props, not own state",
    "OrderBook must receive depth via props, not own state",
    "TradesList must receive trades via props, not own state",
  ]
);

// 7. Index value sanitization
check(
  "Index Value Sanitization",
  read(files.marketOverview).includes("sanitizeIndexValue"),
  [
    "MarketOverview must sanitize index values",
  ]
);

// 8. Canonical event contracts
function fileContains(file: string, pattern: string): boolean {
  try { return read(file).includes(pattern); } catch { return false; }
}

check(
  "Canonical Event Contracts",
  exists("src/api/marketContracts.ts") &&
    (fileContains(files.orchestrator, '"TICK"') || fileContains("src/api/GatewayFeedManager.ts", '"TICK"')) &&
    (fileContains(files.orchestrator, '"DEPTH"') || fileContains("src/api/GatewayFeedManager.ts", '"DEPTH"')) &&
    (fileContains(files.orchestrator, '"CANDLE"') || fileContains("src/api/GatewayFeedManager.ts", '"CANDLE"')),
  [
    "marketContracts.ts must exist",
    "GatewayFeedManager must handle TICK events",
    "GatewayFeedManager must handle DEPTH events",
    "GatewayFeedManager must handle CANDLE events",
  ]
);

// 9. Props-driven components (no internal data fetching in reusable components)
check(
  "Props-Driven Components",
  !read("src/components/PortfolioPanel.tsx").includes('await fetch(') &&
    !read("src/components/ScannerResults.tsx").includes('await fetch(') &&
    !read("src/components/OptionChain.tsx").includes('await fetch('),
  [
    "PortfolioPanel must not fetch data internally",
    "ScannerResults must not fetch data internally",
    "OptionChain must not fetch data internally",
  ]
);

const failed = checks.filter(c => !c.pass);

console.log("\n=== Trade-J Architecture Certification ===");
for (const c of checks) {
  console.log(`${c.pass ? "PASS" : "FAIL"} ${c.name}`);
  if (!c.pass) {
    for (const d of c.details) console.log(`  - ${d}`);
  }
}

console.log(`\nResults: ${checks.length - failed.length}/${checks.length} passed\n`);

if (failed.length > 0) {
  console.error("Architecture certification failed. Refactor required before these checks can pass.");
  process.exit(1);
}
