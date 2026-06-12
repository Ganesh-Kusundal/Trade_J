import type { ComponentType } from "react";
import type { WidgetType } from "./dashboard";

// Lazy-load components for code splitting
interface WidgetDefinition {
  component: () => Promise<{ default: ComponentType<any> }>;
  defaultProps: Record<string, unknown>;
  displayName: string;
  category: "market" | "trading" | "analysis" | "tools";
}

// Map all 15 widget types to their components using dynamic imports
export const WIDGET_REGISTRY: Record<WidgetType, WidgetDefinition> = {
  chart: {
    component: () => import("../components/CandlestickChart"),
    defaultProps: {},
    displayName: "Candlestick Chart",
    category: "market",
  },
  orderbook: {
    component: () => import("../components/OrderBook"),
    defaultProps: {},
    displayName: "Order Book",
    category: "market",
  },
  trades: {
    component: () => import("../components/TradesList"),
    defaultProps: {},
    displayName: "Trades List",
    category: "market",
  },
  watchlist: {
    component: () => import("../components/WatchlistPanel"),
    defaultProps: {},
    displayName: "Watchlist",
    category: "trading",
  },
  scanner: {
    component: () => import("../components/ScannerResults"),
    defaultProps: {},
    displayName: "Scanner",
    category: "analysis",
  },
  options: {
    component: () => import("../components/OptionChain"),
    defaultProps: {},
    displayName: "Option Chain",
    category: "market",
  },
  portfolio: {
    component: () => import("../components/PortfolioPanel"),
    defaultProps: {},
    displayName: "Portfolio",
    category: "trading",
  },
  "equity-curve": {
    component: () => import("../components/EquityCurve"),
    defaultProps: {},
    displayName: "Equity Curve",
    category: "analysis",
  },
  replay: {
    component: () => import("../components/ReplayControls"),
    defaultProps: {},
    displayName: "Replay Controls",
    category: "tools",
  },
  strategy: {
    component: () => import("../components/StrategyVisualization"),
    defaultProps: {},
    displayName: "Strategy Visualization",
    category: "analysis",
  },
  news: {
    component: () => import("../components/NewsFeed"),
    defaultProps: {},
    displayName: "News Feed",
    category: "market",
  },
  alerts: {
    component: () => import("../components/PriceAlerts"),
    defaultProps: {},
    displayName: "Price Alerts",
    category: "tools",
  },
  risk: {
    component: () => import("../components/RiskMonitor"),
    defaultProps: {},
    displayName: "Risk & PnL",
    category: "analysis",
  },
  "market-overview": {
    component: () => import("../components/MarketOverview"),
    defaultProps: {},
    displayName: "Market Overview",
    category: "market",
  },
  settings: {
    component: () => import("../components/SettingsPanel"),
    defaultProps: {},
    displayName: "Settings",
    category: "tools",
  },
  certification: {
    component: () => import("../components/CertificationDashboard"),
    defaultProps: {},
    displayName: "Certification Dashboard",
    category: "tools",
  },
  "strategy-studio": {
    component: () => import("../components/StrategyStudio"),
    defaultProps: {},
    displayName: "Strategy Studio",
    category: "analysis",
  },
  "max-pain": {
    component: () => import("../components/MaxPainChart"),
    defaultProps: {},
    displayName: "Max Pain Chart",
    category: "analysis",
  },
  "pcr-timeseries": {
    component: () => import("../components/PcrTimeseries"),
    defaultProps: {},
    displayName: "PCR Timeseries",
    category: "analysis",
  },
  "gamma-heatmap": {
    component: () => import("../components/GammaHeatmap"),
    defaultProps: {},
    displayName: "Gamma Heatmap",
    category: "analysis",
  },
  "strategy-catalog": {
    component: () => import("../pages/StrategyCatalogPage"),
    defaultProps: {},
    displayName: "Strategy Catalog",
    category: "analysis",
  },
};

export function getWidgetDefinition(
  type: WidgetType,
): WidgetDefinition | undefined {
  return WIDGET_REGISTRY[type];
}

export function listWidgets(
  category?: string,
): { type: WidgetType; definition: WidgetDefinition }[] {
  return Object.entries(WIDGET_REGISTRY)
    .filter(([_, def]) => !category || def.category === category)
    .map(([type, definition]) => ({
      type: type as WidgetType,
      definition,
    }));
}
