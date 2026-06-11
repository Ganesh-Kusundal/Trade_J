let passed = 0;
let failed = 0;

function assert(condition: boolean, name: string) {
  if (condition) { passed++; console.log(`  PASS: ${name}`); }
  else { failed++; console.error(`  FAIL: ${name}`); }
}

// ===== PRICE ALERT LOGIC =====
interface PriceAlert {
  id: string;
  symbol: string;
  exchange: string;
  targetPrice: number;
  direction: "ABOVE" | "BELOW";
  triggered: boolean;
}

function checkAlert(alert: PriceAlert, currentPrice: number, currentSymbol: string, currentExchange: string): boolean {
  if (alert.triggered) return false;
  if (alert.symbol !== currentSymbol || alert.exchange !== currentExchange) return false;
  if (alert.direction === "ABOVE") return currentPrice >= alert.targetPrice;
  return currentPrice <= alert.targetPrice;
}

console.log("\n=== Price Alert Tests ===");
{
  const alert: PriceAlert = { id: "a1", symbol: "GOLD", exchange: "MCX", targetPrice: 74000, direction: "ABOVE", triggered: false };

  assert(checkAlert(alert, 74500, "GOLD", "MCX") === true, "ABOVE alert triggers when price > target");
  assert(checkAlert(alert, 73500, "GOLD", "MCX") === false, "ABOVE alert doesn't trigger when price < target");
  assert(checkAlert(alert, 74000, "GOLD", "MCX") === true, "ABOVE alert triggers at exact target");

  const belowAlert: PriceAlert = { id: "a2", symbol: "RELIANCE", exchange: "NSE", targetPrice: 2400, direction: "BELOW", triggered: false };
  assert(checkAlert(belowAlert, 2350, "RELIANCE", "NSE") === true, "BELOW alert triggers when price < target");
  assert(checkAlert(belowAlert, 2450, "RELIANCE", "NSE") === false, "BELOW alert doesn't trigger when price > target");

  // Wrong symbol
  assert(checkAlert(alert, 74500, "SILVER", "MCX") === false, "Alert doesn't trigger for wrong symbol");
  assert(checkAlert(alert, 74500, "GOLD", "NSE") === false, "Alert doesn't trigger for wrong exchange");

  // Already triggered
  const triggered: PriceAlert = { ...alert, triggered: true };
  assert(checkAlert(triggered, 74500, "GOLD", "MCX") === false, "Already triggered alert doesn't fire again");
}

// ===== CSV EXPORT LOGIC =====
function formatTradesCSV(trades: { time: number; price: number; quantity: number; side: string }[]): string {
  const header = "Time,Price,Quantity,Side";
  const rows = trades.map(t => {
    const time = new Date(t.time * 1000).toISOString();
    return `${time},${t.price.toFixed(2)},${t.quantity},${t.side}`;
  });
  return [header, ...rows].join("\n");
}

function formatOrdersCSV(orders: { orderId: string; symbol: string; side: string; quantity: number; filledQuantity: number; pricePaisa: number; status: string; orderType: string }[]): string {
  const header = "OrderId,Symbol,Side,Qty,FilledQty,Price,Type,Status";
  const rows = orders.map(o => {
    const price = o.pricePaisa > 0 ? (o.pricePaisa / 100).toFixed(2) : "MKT";
    return `${o.orderId},${o.symbol},${o.side},${o.quantity},${o.filledQuantity},${price},${o.orderType},${o.status}`;
  });
  return [header, ...rows].join("\n");
}

console.log("\n=== CSV Export Tests ===");
{
  const trades = [
    { time: 1700000000, price: 73500.50, quantity: 10, side: "BUY" },
    { time: 1700000060, price: 73510.25, quantity: 5, side: "SELL" },
  ];
  const csv = formatTradesCSV(trades);
  const lines = csv.split("\n");
  assert(lines.length === 3, "Trade CSV has header + 2 rows");
  assert(lines[0] === "Time,Price,Quantity,Side", "Trade CSV header correct");
  assert(lines[1].includes("73500.50"), "Trade CSV contains price");
  assert(lines[1].includes("BUY"), "Trade CSV contains side");
  assert(lines[2].includes("SELL"), "Second trade is SELL");

  // Empty trades
  const emptyCsv = formatTradesCSV([]);
  assert(emptyCsv === "Time,Price,Quantity,Side", "Empty trade CSV has header only");
}

console.log("\n=== Orders CSV Tests ===");
{
  const orders = [
    { orderId: "SIM-1001", symbol: "GOLD", side: "BUY", quantity: 10, filledQuantity: 10, pricePaisa: 7350000, status: "TRADED", orderType: "LIMIT" },
    { orderId: "SIM-1002", symbol: "RELIANCE", side: "SELL", quantity: 5, filledQuantity: 0, pricePaisa: 0, status: "OPEN", orderType: "MARKET" },
  ];
  const csv = formatOrdersCSV(orders);
  const lines = csv.split("\n");
  assert(lines.length === 3, "Orders CSV has header + 2 rows");
  assert(lines[0] === "OrderId,Symbol,Side,Qty,FilledQty,Price,Type,Status", "Orders CSV header correct");
  assert(lines[1].includes("73500.00"), "LIMIT order shows price in rupees");
  assert(lines[2].includes("MKT"), "MARKET order shows MKT for price");
  assert(lines[2].includes("OPEN"), "Open order shows OPEN status");
}

// ===== PORTFOLIO ORDER FILTERING =====
function filterOrders(orders: any[], tab: "active" | "completed"): any[] {
  if (tab === "active") {
    return orders.filter(o => o.status !== "TRADED" && o.status !== "CANCELLED" && o.status !== "REJECTED");
  }
  return orders.filter(o => o.status === "TRADED" || o.status === "CANCELLED" || o.status === "REJECTED");
}

console.log("\n=== Portfolio Order Filtering ===");
{
  const orders = [
    { orderId: "1", status: "OPEN" },
    { orderId: "2", status: "PENDING" },
    { orderId: "3", status: "TRADED" },
    { orderId: "4", status: "CANCELLED" },
    { orderId: "5", status: "REJECTED" },
    { orderId: "6", status: "OPEN" },
  ];

  const active = filterOrders(orders, "active");
  assert(active.length === 3, `Active orders: 3 (got ${active.length})`);
  assert(active.every(o => ["OPEN", "PENDING"].includes(o.status)), "Active = OPEN + PENDING");

  const completed = filterOrders(orders, "completed");
  assert(completed.length === 3, `Completed orders: 3 (got ${completed.length})`);
  assert(completed.every(o => ["TRADED", "CANCELLED", "REJECTED"].includes(o.status)), "Completed = TRADED + CANCELLED + REJECTED");

  // Empty
  assert(filterOrders([], "active").length === 0, "Empty orders → 0 active");
  assert(filterOrders([], "completed").length === 0, "Empty orders → 0 completed");
}

// ===== ORDER STATUS COLORS =====
function statusColor(status: string): string {
  switch (status) {
    case "TRADED": return "#26a69a";
    case "OPEN": case "PENDING": return "#f0b429";
    case "CANCELLED": case "REJECTED": return "#ef5350";
    default: return "#94a3b8";
  }
}

console.log("\n=== Order Status Colors ===");
{
  assert(statusColor("TRADED") === "#26a69a", "TRADED = green");
  assert(statusColor("OPEN") === "#f0b429", "OPEN = amber");
  assert(statusColor("PENDING") === "#f0b429", "PENDING = amber");
  assert(statusColor("CANCELLED") === "#ef5350", "CANCELLED = red");
  assert(statusColor("REJECTED") === "#ef5350", "REJECTED = red");
  assert(statusColor("UNKNOWN") === "#94a3b8", "UNKNOWN = slate");
}

console.log(`\n========================================`);
console.log(`Results: ${passed} passed, ${failed} failed out of ${passed + failed} tests`);
console.log(`========================================\n`);

if (failed > 0) process.exit(1);
