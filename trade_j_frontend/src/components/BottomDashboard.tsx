import React, { useMemo, useState, useEffect } from "react";
import { WIDGET_REGISTRY } from "../domain/widgetRegistry";
import type { DashboardConfig, WidgetConfig } from "../domain/dashboard";
import DashboardRenderer from "./DashboardRenderer";

interface BottomDashboardProps {
  /** The active widget id (e.g., "watchlist", "scanner"). */
  activeId: string;
  /** All available widgets to render in the toolbar. */
  config: DashboardConfig;
  /** Called when the user clicks a toolbar button. */
  onSelect: (id: string) => void;
  /** Props to pass to the active widget. */
  widgetProps: Record<string, unknown>;
}

/**
 * Renders the bottom-row tab switcher as a dashboard.
 * The toolbar shows the widget list from the config; the active widget
 * is rendered with its own dynamic import resolved via WIDGET_REGISTRY.
 *
 * Special case: when the active widget's `type` is `"grid"`, the value of
 * `widget.props.config` is rendered as a full DashboardConfig via
 * DashboardRenderer. This is a meta-tab and is NOT registered in
 * WIDGET_REGISTRY on purpose.
 */
export default function BottomDashboard({
  activeId,
  config,
  onSelect,
  widgetProps,
}: BottomDashboardProps) {
  const [Component, setComponent] = useState<React.ComponentType<any> | null>(null);
  const [error, setError] = useState<string | null>(null);

  const active = useMemo(
    () => config.widgets.find((w: WidgetConfig) => w.id === activeId),
    [config, activeId],
  );

  const isGrid = active?.type === "grid";
  const gridConfig: DashboardConfig | null = isGrid && active
    ? (active.props?.config as DashboardConfig | undefined) ?? null
    : null;

  useEffect(() => {
    if (isGrid) {
      // Grid tabs are handled by DashboardRenderer, not the registry.
      setComponent(null);
      setError(null);
      return;
    }
    let cancelled = false;
    if (!active) return;
    const def = WIDGET_REGISTRY[active.type];
    if (!def) {
      setError(`Unknown widget type: ${active.type}`);
      return;
    }
    setError(null);
    setComponent(null);
    def.component()
      .then((mod) => { if (!cancelled) setComponent(() => mod.default); })
      .catch((e) => { if (!cancelled) setError(String(e)); });
    return () => { cancelled = true; };
  }, [active, isGrid]);

  return (
    <div className="flex flex-col h-full">
      <div className="flex items-center gap-1 px-2 py-1 border-b border-[#21262d]">
        {config.widgets.map((w) => (
          <button
            key={w.id}
            onClick={() => onSelect(w.id)}
            className={`px-2 py-1 text-[10px] font-bold rounded cursor-pointer ${
              w.id === activeId
                ? "bg-[#f0b429] text-black"
                : "text-slate-400 hover:text-slate-200"
            }`}
          >
            {w.label ?? w.id}
          </button>
        ))}
      </div>
      <div className="flex-1 min-h-0 overflow-auto">
        {error && (
          <div className="p-3 text-[10px] text-red-400 font-mono">Widget error: {error}</div>
        )}
        {isGrid ? (
          gridConfig ? (
            <DashboardRenderer config={gridConfig} widgetProps={widgetProps} />
          ) : (
            <div className="p-3 text-[10px] text-red-400 font-mono">Grid tab is missing a config in its props.</div>
          )
        ) : Component ? (
          <Component {...widgetProps} />
        ) : (
          !error && <div className="p-3 text-[10px] text-slate-500 font-mono">Loading widget…</div>
        )}
      </div>
    </div>
  );
}
