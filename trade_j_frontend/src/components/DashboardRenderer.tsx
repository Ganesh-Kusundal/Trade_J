import React, { useState, useEffect } from "react";
import type { DashboardConfig, WidgetConfig } from "../domain/dashboard";
import { getWidgetDefinition } from "../domain/widgetRegistry";
import WidgetErrorBoundary from "./WidgetErrorBoundary";

// WidgetWrapper renders a single widget with loading state and grid positioning.
// It receives the parent-supplied `widgetProps` (a flat object of live data) and
// spreads them onto the component AFTER the widget's own static `props`, so that
// the live data overrides the per-widget defaults without losing them.
const WidgetWrapper = React.memo(function WidgetWrapper({
  config,
  widgetProps,
}: {
  config: WidgetConfig;
  widgetProps: Record<string, unknown>;
}) {
  const [Component, setComponent] =
    useState<React.ComponentType<any> | null>(null);
  const definition = getWidgetDefinition(config.type);
  const pos = config.position ?? { x: 0, y: 0, w: 1, h: 1 };

  useEffect(() => {
    if (definition) {
      definition.component().then((mod) => setComponent(() => mod.default));
    }
  }, [config.type, definition]);

  if (!Component) {
    return (
      <div
        className="bg-[#0d1117] border border-[#21262d] rounded animate-pulse"
        style={{
          gridColumn: `span ${pos.w}`,
          gridRow: `span ${pos.h}`,
        }}
      />
    );
  }

  return (
    <div
      style={{
        gridColumn: `span ${pos.w}`,
        gridRow: `span ${pos.h}`,
      }}
    >
      <Component {...config.props} {...widgetProps} />
    </div>
  );
});

// DashboardRenderer renders a full dashboard from config.
// `widgetProps` is the same flat object the parent (e.g. BottomDashboard)
// already passes to single widgets — it is spread onto every child so that
// each child receives the live data it needs.
export default function DashboardRenderer({
  config,
  widgetProps = {},
}: {
  config: DashboardConfig;
  widgetProps?: Record<string, unknown>;
}) {
  return (
    <div className="grid grid-cols-12 gap-1 h-full">
      {config.widgets.map((widget) => (
        <div key={widget.id} style={{
          gridColumn: `span ${widget.position?.w ?? 1}`,
          gridRow: `span ${widget.position?.h ?? 1}`,
        }}>
          <WidgetErrorBoundary widgetId={widget.id}>
            <WidgetWrapper
              config={widget}
              widgetProps={widgetProps}
            />
          </WidgetErrorBoundary>
        </div>
      ))}
    </div>
  );
}
