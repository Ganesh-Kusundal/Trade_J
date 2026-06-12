export type WidgetType =
  | "chart"
  | "orderbook"
  | "trades"
  | "watchlist"
  | "scanner"
  | "options"
  | "portfolio"
  | "replay"
  | "strategy"
  | "news"
  | "alerts"
  | "risk"
  | "market-overview"
  | "settings"
  | "certification"
  | "strategy-studio"
  | "max-pain"
  | "pcr-timeseries"
  | "gamma-heatmap"
  | "equity-curve"
  | "strategy-catalog";

export interface WidgetPosition {
  x: number;
  y: number;
  w: number;
  h: number;
}

export interface WidgetConfig {
  id: string;
  type: WidgetType;
  label?: string;
  props: Record<string, unknown>;
  position?: WidgetPosition;
}

export interface DashboardConfig {
  id: string;
  name: string;
  widgets: WidgetConfig[];
}

// Default "Trading" dashboard that replicates the current App.tsx layout
export const DEFAULT_TRADING_DASHBOARD: DashboardConfig = {
  id: "trading",
  name: "Trading Terminal",
  widgets: [
    {
      id: "overview",
      type: "market-overview",
      props: {},
      position: { x: 0, y: 0, w: 12, h: 1 },
    },
    {
      id: "chart",
      type: "chart",
      props: {},
      position: { x: 0, y: 1, w: 8, h: 6 },
    },
    {
      id: "orderbook",
      type: "orderbook",
      props: {},
      position: { x: 8, y: 1, w: 4, h: 3 },
    },
    {
      id: "trades",
      type: "trades",
      props: {},
      position: { x: 8, y: 4, w: 4, h: 3 },
    },
    {
      id: "bottom-panel",
      type: "watchlist",
      props: {},
      position: { x: 0, y: 7, w: 12, h: 4 },
    },
  ],
};
