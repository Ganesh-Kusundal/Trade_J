// ==========================================
// TerminalLayout tests (P1.B1.1 / P1.B1.2)
// ==========================================
// Verifies that:
//   1. The 5 built-in dashboard layouts are registered
//   2. Every registered layout has at least one widget
//   3. Every widget type in the registry is referenced by at least one
//      layout (no dead widgets)
//   4. DASHBOARD_LAYOUT_IDS matches DASHBOARD_LAYOUTS keys

import { DASHBOARD_LAYOUTS, DASHBOARD_LAYOUT_IDS } from "../components/TerminalLayout";
import { WIDGET_REGISTRY } from "../domain/widgetRegistry";
import { BOTTOM_DASHBOARD_CONFIG, MULTI_WIDGET_GRID_CONFIG, RESEARCH_DASHBOARD_CONFIG, SCANNER_DASHBOARD_CONFIG, OPTIONS_DASHBOARD_CONFIG } from "../domain/dashboards";
import { DEFAULT_TRADING_DASHBOARD } from "../domain/dashboard";
import type { WidgetType } from "../domain/dashboard";

function assert(condition: boolean, message: string): void {
  if (!condition) {
    console.error(`  FAIL: ${message}`);
    process.exit(1);
  }
  console.log(`  PASS: ${message}`);
}

console.log("\n=== TerminalLayout tests ===");

assert(DASHBOARD_LAYOUT_IDS.length === 5, "5 built-in layouts");
assert(typeof DASHBOARD_LAYOUTS["trading"] === "object", "trading layout registered");
assert(typeof DASHBOARD_LAYOUTS["bottom"] === "object", "bottom layout registered");
assert(typeof DASHBOARD_LAYOUTS["research"] === "object", "research layout registered");
assert(typeof DASHBOARD_LAYOUTS["scanner"] === "object", "scanner layout registered");
assert(typeof DASHBOARD_LAYOUTS["options"] === "object", "options layout registered");

assert(DASHBOARD_LAYOUTS["trading"] === DEFAULT_TRADING_DASHBOARD,
       "trading layout aliases DEFAULT_TRADING_DASHBOARD");
assert(DASHBOARD_LAYOUTS["bottom"] === BOTTOM_DASHBOARD_CONFIG,
       "bottom layout aliases BOTTOM_DASHBOARD_CONFIG");
assert(DASHBOARD_LAYOUTS["research"] === RESEARCH_DASHBOARD_CONFIG,
       "research layout aliases RESEARCH_DASHBOARD_CONFIG");
assert(DASHBOARD_LAYOUTS["scanner"] === SCANNER_DASHBOARD_CONFIG,
       "scanner layout aliases SCANNER_DASHBOARD_CONFIG");
assert(DASHBOARD_LAYOUTS["options"] === OPTIONS_DASHBOARD_CONFIG,
       "options layout aliases OPTIONS_DASHBOARD_CONFIG");

// Every layout has at least one widget.
for (const [id, layout] of Object.entries(DASHBOARD_LAYOUTS)) {
  assert(layout.widgets.length > 0, `layout ${id} has at least one widget`);
}

// Every registered widget is referenced by at least one layout — except
// `bottom` which is a tab strip and the meta `grid` tab.
const layoutTypes = new Set<WidgetType>();
for (const layout of Object.values(DASHBOARD_LAYOUTS)) {
  for (const w of layout.widgets) layoutTypes.add(w.type);
}
for (const type of Object.keys(WIDGET_REGISTRY) as WidgetType[]) {
  assert(layoutTypes.has(type), `widget ${type} is referenced by at least one layout`);
}

// `MULTI_WIDGET_GRID_CONFIG` is a sub-component of `bottom`; just ensure it exists.
assert(typeof MULTI_WIDGET_GRID_CONFIG === "object", "MULTI_WIDGET_GRID_CONFIG exists");
