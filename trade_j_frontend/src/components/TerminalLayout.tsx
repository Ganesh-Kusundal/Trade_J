import React, { useMemo } from "react";
import DashboardRenderer from "./DashboardRenderer";
import { DEFAULT_TRADING_DASHBOARD, type DashboardConfig } from "../domain/dashboard";
import { BOTTOM_DASHBOARD_CONFIG, RESEARCH_DASHBOARD_CONFIG, SCANNER_DASHBOARD_CONFIG, OPTIONS_DASHBOARD_CONFIG } from "../domain/dashboards";

/**
 * Stable identifiers for the built-in dashboard layouts. The
 * {@link DashboardLayoutId} union is a TypeScript type that guarantees
 * only valid ids can be passed to {@link TerminalLayout}.
 */
export type DashboardLayoutId =
  | "trading"
  | "bottom"
  | "research"
  | "scanner"
  | "options";

export const DASHBOARD_LAYOUT_IDS: DashboardLayoutId[] = [
  "trading", "bottom", "research", "scanner", "options",
];

/**
 * Registry of all built-in dashboards. Adding a new dashboard is a one-line
 * change here. The `id` is persisted in localStorage and is the
 * stable identifier for a layout.
 */
export const DASHBOARD_LAYOUTS: Record<DashboardLayoutId, DashboardConfig> = {
  trading: DEFAULT_TRADING_DASHBOARD,
  bottom: BOTTOM_DASHBOARD_CONFIG,
  research: RESEARCH_DASHBOARD_CONFIG,
  scanner: SCANNER_DASHBOARD_CONFIG,
  options: OPTIONS_DASHBOARD_CONFIG,
};

export interface TerminalLayoutProps {
  layoutId: DashboardLayoutId;
  widgetProps?: Record<string, unknown>;
}

/**
 * Renders the active dashboard layout using the widget registry as the
 * single source of truth. The active layout is chosen by `layoutId` and
 * is persisted in localStorage by the parent (App.tsx).
 *
 * <p>All 16 widgets registered in `widgetRegistry.ts` are reachable through
 * one of the built-in layouts. New layouts are added by extending
 * {@link DASHBOARD_LAYOUTS} and defining a `DashboardConfig` in
 * `domain/dashboards.ts`.
 */
const TerminalLayoutBase: React.FC<TerminalLayoutProps> = ({
  layoutId,
  widgetProps = {},
}) => {
  const config = useMemo(
    () => DASHBOARD_LAYOUTS[layoutId] ?? DEFAULT_TRADING_DASHBOARD,
    [layoutId]
  );
  return <DashboardRenderer config={config} widgetProps={widgetProps} />;
};

export default TerminalLayoutBase;
