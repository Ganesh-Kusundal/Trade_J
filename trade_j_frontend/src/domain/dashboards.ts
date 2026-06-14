import type { DashboardConfig, WidgetConfig } from "./dashboard";

/**
 * The bottom-row tab switcher (replaces the App.tsx tab switch block).
 * Each entry is one tab; the active widget is rendered below the toolbar.
 *
 * The `position` field is optional on the global WidgetConfig type and is
 * unused for the bottom dashboard (no grid layout), so it is omitted.
 *
 * The last tab (`grid`) is a meta-tab: it is NOT resolved through
 * `WIDGET_REGISTRY` — `BottomDashboard` special-cases `type === "grid"`
 * and renders the full `DashboardRenderer` against `props.config`.
 */

/**
 * A 6-widget 12-column grid rendered by `DashboardRenderer` from the "Grid" tab.
 * Widget types are all valid `WidgetType` values; `props: {}` is left empty
 * because the parent passes flat `widgetProps` (live data) to every child.
 */
export const MULTI_WIDGET_GRID_CONFIG: DashboardConfig = {
  id: "multi-widget-grid",
  name: "Multi-Widget Grid",
  widgets: [
    { id: "chart", type: "chart", label: "Chart", props: {},
      position: { x: 0, y: 0, w: 8, h: 6 } },
    { id: "orderbook", type: "orderbook", label: "Order Book", props: {},
      position: { x: 8, y: 0, w: 4, h: 3 } },
    { id: "trades", type: "trades", label: "Trades", props: {},
      position: { x: 8, y: 3, w: 4, h: 3 } },
    { id: "watchlist", type: "watchlist", label: "Watchlist", props: {},
      position: { x: 0, y: 6, w: 4, h: 4 } },
    { id: "strategy-catalog", type: "strategy-catalog", label: "Strategy Catalog", props: {},
      position: { x: 4, y: 6, w: 4, h: 4 } },
    { id: "equity-curve", type: "equity-curve", label: "Equity Curve", props: {},
      position: { x: 8, y: 6, w: 4, h: 4 } },
    { id: "portfolio", type: "portfolio", label: "Portfolio", props: {},
      position: { x: 8, y: 6, w: 4, h: 4 } },
  ],
};

/**
 * The "Grid" meta-tab as a standalone WidgetConfig-shape object. The literal
 * `type: "grid"` is NOT a member of `WidgetType`; we cast through `unknown` to
 * attach the meta-tag without polluting the registry or the global types.
 */
const GRID_TAB: WidgetConfig = {
  id: "grid",
  type: "grid" as unknown as WidgetConfig["type"],
  label: "Grid",
  props: { config: MULTI_WIDGET_GRID_CONFIG } as Record<string, unknown>,
};

export const BOTTOM_DASHBOARD_CONFIG: DashboardConfig = {
  id: "bottom-tabs",
  name: "Bottom Tabs",
  widgets: [
    { id: "watchlist", type: "watchlist", label: "Watchlist", props: {} },
    { id: "orders", type: "portfolio", label: "Orders", props: {} },
    { id: "alerts", type: "alerts", label: "Alerts", props: {} },
    { id: "risk", type: "risk", label: "Risk", props: {} },
    { id: "news", type: "news", label: "News", props: {} },
    { id: "options", type: "options", label: "Options", props: {} },
    { id: "scanner", type: "scanner", label: "Scanner", props: {} },
    { id: "strategy", type: "strategy", label: "Strategy", props: {} },
    { id: "certification", type: "certification", label: "Cert", props: {} },
    GRID_TAB,
  ],
};

/**
 * Research-focused 8-widget grid. Exposed to the user as the "Research"
 * layout in the status-bar dropdown.
 */
export const RESEARCH_DASHBOARD_CONFIG: DashboardConfig = {
  id: "research",
  name: "Research",
  widgets: [
    { id: "market-overview", type: "market-overview", label: "Market Overview", props: {},
      position: { x: 0, y: 0, w: 12, h: 1 } },
    { id: "chart", type: "chart", label: "Chart", props: {},
      position: { x: 0, y: 1, w: 6, h: 5 } },
    { id: "orderbook", type: "orderbook", label: "Order Book", props: {},
      position: { x: 6, y: 1, w: 3, h: 5 } },
    { id: "trades", type: "trades", label: "Trades", props: {},
      position: { x: 9, y: 1, w: 3, h: 5 } },
    { id: "watchlist", type: "watchlist", label: "Watchlist", props: {},
      position: { x: 0, y: 6, w: 4, h: 4 } },
    { id: "scanner", type: "scanner", label: "Scanner", props: {},
      position: { x: 4, y: 6, w: 4, h: 4 } },
    { id: "news", type: "news", label: "News", props: {},
      position: { x: 8, y: 6, w: 2, h: 4 } },
    { id: "alerts", type: "alerts", label: "Alerts", props: {},
      position: { x: 10, y: 6, w: 2, h: 4 } },
  ],
};

/**
 * Scanner-focused 6-widget grid. Exposed to the user as the "Scanner"
 * layout in the status-bar dropdown.
 */
export const SCANNER_DASHBOARD_CONFIG: DashboardConfig = {
  id: "scanner",
  name: "Scanner",
  widgets: [
    { id: "market-overview", type: "market-overview", label: "Market Overview", props: {},
      position: { x: 0, y: 0, w: 12, h: 1 } },
    { id: "scanner", type: "scanner", label: "Scanner", props: {},
      position: { x: 0, y: 1, w: 8, h: 7 } },
    { id: "watchlist", type: "watchlist", label: "Watchlist", props: {},
      position: { x: 8, y: 1, w: 4, h: 3 } },
    { id: "optionchain", type: "options", label: "Option Chain", props: {},
      position: { x: 8, y: 4, w: 4, h: 4 } },
    { id: "alerts", type: "alerts", label: "Alerts", props: {},
      position: { x: 0, y: 8, w: 6, h: 2 } },
    { id: "news", type: "news", label: "News", props: {},
      position: { x: 6, y: 8, w: 6, h: 2 } },
  ],
};

/**
 * Options-focused 6-widget grid. Exposed to the user as the "Options"
 * layout in the status-bar dropdown.
 */
export const OPTIONS_DASHBOARD_CONFIG: DashboardConfig = {
  id: "options",
  name: "Options",
  widgets: [
    { id: "market-overview", type: "market-overview", label: "Market Overview", props: {},
      position: { x: 0, y: 0, w: 12, h: 1 } },
    { id: "chart", type: "chart", label: "Chart", props: {},
      position: { x: 0, y: 1, w: 8, h: 4 } },
    { id: "options", type: "options", label: "Option Chain", props: {},
      position: { x: 0, y: 5, w: 12, h: 5 } },
    { id: "orderbook", type: "orderbook", label: "Order Book", props: {},
      position: { x: 8, y: 1, w: 4, h: 4 } },
    { id: "max-pain", type: "max-pain", label: "Max Pain", props: {},
      position: { x: 0, y: 10, w: 4, h: 3 } },
    { id: "pcr-timeseries", type: "pcr-timeseries", label: "PCR", props: {},
      position: { x: 4, y: 10, w: 4, h: 3 } },
    { id: "gamma-heatmap", type: "gamma-heatmap", label: "Gamma", props: {},
      position: { x: 8, y: 10, w: 4, h: 3 } },
  ],
};

