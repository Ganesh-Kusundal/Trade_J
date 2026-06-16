import React from "react";

export type DataSource =
  | { kind: "readmodel.orders" }
  | { kind: "readmodel.positions" }
  | { kind: "readmodel.signals" }
  | { kind: "readmodel.pnl" }
  | { kind: "readmodel.ticks" }
  | { kind: "readmodel.depths" }
  | { kind: "readmodel.candles" }
  | { kind: "market.ltp"; symbol: string; segment: string }
  | { kind: "market.chain"; underlying: string; segment: string }
  | { kind: "scan.runs"; profile: string }
  | { kind: "scan.latest"; profile: string };

export type Layout =
  | { kind: "grid"; cols: number; gap: number }
  | { kind: "tabs"; tabs: string[] }
  | { kind: "split"; direction: "horizontal" | "vertical"; ratio: number };

export interface WidgetSpec {
  id: string;
  type: string;
  title?: string;
  dataSource: DataSource;
  props?: Record<string, unknown>;
  /** If nested layout, this widget contains children. */
  children?: WidgetSpec[];
}

export interface DashboardSpec {
  id: string;
  title: string;
  description?: string;
  layout: Layout;
  widgets: WidgetSpec[];
}

export interface WidgetProps {
  spec: WidgetSpec;
  key?: string; // React uses key for reconciliation; not passed at runtime
}

export type WidgetRenderer = React.ComponentType<WidgetProps>;

const REGISTRY = new Map<string, WidgetRenderer>();

export function registerWidget(type: string, renderer: WidgetRenderer): void {
  REGISTRY.set(type, renderer);
}

export function getWidget(type: string): WidgetRenderer | undefined {
  return REGISTRY.get(type);
}

export function listWidgets(): string[] {
  return Array.from(REGISTRY.keys());
}
