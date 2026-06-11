let passed = 0;
let failed = 0;

function assert(condition: boolean, name: string) {
  if (condition) { passed++; console.log(`  PASS: ${name}`); }
  else { failed++; console.error(`  FAIL: ${name}`); }
}

// ===== RISK CALCULATOR LOGIC =====
function calculateRisk(capital: number, riskPercent: number, stopLossDistance: number, marginPercent: number, price: number) {
  const riskAmount = capital * (riskPercent / 100);
  const positionSize = stopLossDistance > 0 ? Math.floor(riskAmount / stopLossDistance) : 0;
  const positionValue = positionSize * price;
  const marginRequired = positionValue * (marginPercent / 100);
  const maxQtyByCapital = price > 0 ? Math.floor(capital / (price * (marginPercent / 100))) : 0;
  return { riskAmount, positionSize, positionValue, marginRequired, maxQtyByCapital };
}

console.log("\n=== Risk Calculator Tests ===");
{
  // Basic calculation: 1L capital, 2% risk, SL distance 50, 20% margin, price 2500
  const r = calculateRisk(100000, 2, 50, 20, 2500);
  assert(r.riskAmount === 2000, "Risk amount = 2000 (2% of 1L)");
  assert(r.positionSize === 40, "Position size = 40 qty (2000/50)");
  assert(r.positionValue === 100000, "Position value = 100000 (40 * 2500)");
  assert(r.marginRequired === 20000, "Margin required = 20000 (20% of 100000)");
  assert(r.maxQtyByCapital === 200, "Max qty = 200 (1L / (2500 * 0.2))");

  // Zero stop loss distance
  const r2 = calculateRisk(100000, 2, 0, 20, 2500);
  assert(r2.positionSize === 0, "Zero SL distance → 0 position size");

  // Zero price
  const r3 = calculateRisk(100000, 2, 50, 20, 0);
  assert(r3.maxQtyByCapital === 0, "Zero price → 0 max qty");

  // High risk
  const r4 = calculateRisk(50000, 5, 100, 50, 1000);
  assert(r4.riskAmount === 2500, "5% risk of 50K = 2500");
  assert(r4.positionSize === 25, "2500 / 100 = 25 qty");
  assert(r4.marginRequired === 12500, "50% margin of 25000 = 12500");
}

// ===== CHART DRAWINGS =====
interface HorizontalLine { id: string; price: number; color: string; label?: string; }

let lineIdCounter = 0;
function createHorizontalLine(price: number, color = "#f0b429", label?: string): HorizontalLine {
  return { id: `hline-${++lineIdCounter}`, price, color, label };
}

function removeDrawing(drawings: HorizontalLine[], id: string): HorizontalLine[] {
  return drawings.filter(d => d.id !== id);
}

console.log("\n=== Chart Drawing Tests ===");
{
  lineIdCounter = 0;
  const line1 = createHorizontalLine(73500, "#26a69a", "Support");
  assert(line1.price === 73500, "Line created at 73500");
  assert(line1.color === "#26a69a", "Line color is green");
  assert(line1.label === "Support", "Line label is Support");
  assert(line1.id === "hline-1", "First line id is hline-1");

  const line2 = createHorizontalLine(74000);
  assert(line2.id === "hline-2", "Second line id is hline-2");
  assert(line2.color === "#f0b429", "Default color is amber");

  const lines = [line1, line2];
  const afterRemove = removeDrawing(lines, "hline-1");
  assert(afterRemove.length === 1, "Remove leaves 1 line");
  assert(afterRemove[0].id === "hline-2", "Remaining line is hline-2");
}

// ===== SETTINGS DEFAULTS =====
const DEFAULT_SETTINGS = {
  defaultBroker: "DHAN",
  defaultExchange: "NSE",
  defaultTimeframe: "1m",
  soundEnabled: true,
  notificationsEnabled: true,
  chartGridVisible: true,
  chartCrosshairMode: "normal",
  orderConfirmation: true,
  maxSlippagePercent: 0.5,
  theme: "dark",
  fontSize: "small",
};

console.log("\n=== Settings Defaults Tests ===");
{
  assert(DEFAULT_SETTINGS.defaultBroker === "DHAN", "Default broker is DHAN");
  assert(DEFAULT_SETTINGS.defaultExchange === "NSE", "Default exchange is NSE");
  assert(DEFAULT_SETTINGS.defaultTimeframe === "1m", "Default timeframe is 1m");
  assert(DEFAULT_SETTINGS.soundEnabled === true, "Sound enabled by default");
  assert(DEFAULT_SETTINGS.notificationsEnabled === true, "Notifications enabled by default");
  assert(DEFAULT_SETTINGS.orderConfirmation === true, "Order confirmation enabled by default");
  assert(DEFAULT_SETTINGS.maxSlippagePercent === 0.5, "Max slippage is 0.5%");
  assert(DEFAULT_SETTINGS.theme === "dark", "Dark theme by default");
  assert(DEFAULT_SETTINGS.chartGridVisible === true, "Chart grid visible by default");
  assert(DEFAULT_SETTINGS.chartCrosshairMode === "normal", "Crosshair mode is normal");
  assert(DEFAULT_SETTINGS.fontSize === "small", "Font size is small");
}

// ===== NEWS SENTIMENT =====
function sentimentColor(sentiment: string): string {
  switch (sentiment) {
    case "positive": return "#26a69a";
    case "negative": return "#ef5350";
    default: return "#94a3b8";
  }
}

console.log("\n=== News Sentiment Tests ===");
{
  assert(sentimentColor("positive") === "#26a69a", "Positive = green");
  assert(sentimentColor("negative") === "#ef5350", "Negative = red");
  assert(sentimentColor("neutral") === "#94a3b8", "Neutral = slate");
  assert(sentimentColor("unknown") === "#94a3b8", "Unknown = slate (fallback)");
}

console.log(`\n========================================`);
console.log(`Results: ${passed} passed, ${failed} failed out of ${passed + failed} tests`);
console.log(`========================================\n`);

if (failed > 0) process.exit(1);