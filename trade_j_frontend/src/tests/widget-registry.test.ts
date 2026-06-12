import { WIDGET_REGISTRY, getWidgetDefinition, listWidgets } from "../domain/widgetRegistry";
import { DEFAULT_TRADING_DASHBOARD } from "../domain/dashboard";
import type { WidgetType } from "../domain/dashboard";

function assert(condition: boolean, message: string): void {
  if (!condition) {
    console.error(`  FAIL: ${message}`);
    process.exit(1);
  }
  console.log(`  PASS: ${message}`);
}

// ==========================================
// Widget Registry Tests
// ==========================================
console.log("\n=== Widget Registry Tests ===");

const allTypes: WidgetType[] = [
  "chart", "orderbook", "trades", "watchlist", "scanner",
  "options", "portfolio", "replay", "strategy", "news",
  "alerts", "risk", "market-overview", "settings", "certification",
  "strategy-studio",
  "max-pain", "pcr-timeseries", "gamma-heatmap", "equity-curve",
  "strategy-catalog"
];

// All 16 widget types are registered
for (const type of allTypes) {
  const def = getWidgetDefinition(type);
  assert(def !== undefined, `Widget type '${type}' is registered in registry`);
  assert(def!.displayName.length > 0, `Widget type '${type}' has a display name`);
  assert(typeof def!.component === "function", `Widget type '${type}' has a component loader`);
}

// Registry count
const widgetCount = Object.keys(WIDGET_REGISTRY).length;
assert(widgetCount === 16, `Registry has exactly 16 widget types (got ${widgetCount})`);

// listWidgets returns all types
const allWidgets = listWidgets();
assert(allWidgets.length === 16, `listWidgets() returns 16 entries (got ${allWidgets.length})`);

// listWidgets with category filter
const marketWidgets = listWidgets("market");
assert(marketWidgets.length > 0, `listWidgets("market") returns at least 1 widget`);
for (const w of marketWidgets) {
  assert(w.definition.category === "market", `${w.type} is categorized as market`);
}

// ==========================================
// Dashboard Config Tests
// ==========================================
console.log("\n=== Dashboard Config Tests ===");

assert(DEFAULT_TRADING_DASHBOARD.id === "trading", "Default dashboard has id 'trading'");
assert(DEFAULT_TRADING_DASHBOARD.name.length > 0, "Default dashboard has a name");
assert(DEFAULT_TRADING_DASHBOARD.widgets.length > 0, "Default dashboard has widgets");

// All widget configs reference valid widget types
for (const widget of DEFAULT_TRADING_DASHBOARD.widgets) {
  const def = getWidgetDefinition(widget.type);
  assert(def !== undefined, `Dashboard widget '${widget.id}' references valid type '${widget.type}'`);
  assert(widget.id.length > 0, `Widget config has non-empty id`);
  assert(widget.position.w > 0 && widget.position.h > 0, `Widget '${widget.id}' has valid dimensions`);
}

// ==========================================
// Component Loader Tests
// ==========================================
console.log("\n=== Component Loader Tests ===");

// All component loaders return valid modules
for (const type of allTypes) {
  const def = getWidgetDefinition(type)!;
  // Just verify the loader function is callable (don't actually load to avoid React dependency in test)
  assert(typeof def.component === "function", `Component loader for '${type}' is a function`);
}

console.log("\n========================================");
console.log("All widget registry tests passed!");
console.log("========================================\n");
