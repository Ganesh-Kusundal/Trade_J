# Adding a Dashboard to Trade-J

## What you can add

Two kinds of additions:

1. **A new bottom-dashboard tab** (e.g. a "P&L", "Heatmap", or "Custom" widget shown next to Watchlist, Orders, Risk, News, Options, Scanner, Strategy, Cert).
2. **A new widget in the trading grid dashboard** (e.g. a market-overview card, a custom indicator panel).

Both are React components rendered through a config-driven system. The bottom bar uses [BottomDashboard.tsx](../../trade_j_frontend/src/components/BottomDashboard.tsx), the full grid uses [DashboardRenderer.tsx](../../trade_j_frontend/src/components/DashboardRenderer.tsx). The component-to-type map is [widgetRegistry.ts](../../trade_j_frontend/src/domain/widgetRegistry.ts); the layouts are [dashboards.ts](../../trade_j_frontend/src/domain/dashboards.ts) and [dashboard.ts](../../trade_j_frontend/src/domain/dashboard.ts).

## Time required

- 15-30 minutes to add a new bottom-dashboard tab reusing an existing component.
- 1-3 hours to build a new React component and wire it into both registries.

## Prerequisites

- You are familiar with React function components and TypeScript.
- You know where the frontend lives: `trade_j_frontend/src/`. Components go in `trade_j_frontend/src/components/`, types in `trade_j_frontend/src/domain/`.
- The dashboard grid uses a 12-column layout. Each widget specifies a position `{x, y, w, h}` in grid units.

## Step 1: Create the component

Create `trade_j_frontend/src/components/MyNewWidget.tsx`. The existing empty-state pattern is the "honest" approach for a widget that has no live data wired yet — see [NewsFeed.tsx](../../trade_j_frontend/src/components/NewsFeed.tsx) for the canonical example. A real data-driven example is [RiskCalculator.tsx](../../trade_j_frontend/src/components/RiskCalculator.tsx).

A self-contained data-driven widget:

```tsx
import React, { useEffect, useState } from "react";

interface MyNewWidgetProps {
  symbol: string;
  refreshIntervalMs?: number;
}

interface Position {
  symbol: string;
  qty: number;
  avgPrice: number;
  ltp: number;
}

export default function MyNewWidget({ symbol, refreshIntervalMs = 5_000 }: MyNewWidgetProps) {
  const [positions, setPositions] = useState<Position[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    const load = async () => {
      try {
        const res = await fetch(`/api/v1/positions?symbol=${encodeURIComponent(symbol)}`);
        if (!res.ok) throw new Error(`HTTP ${res.status}`);
        const data: Position[] = await res.json();
        if (!cancelled) {
          setPositions(data);
          setLoading(false);
        }
      } catch (e) {
        if (!cancelled) setLoading(false);
      }
    };
    load();
    const id = setInterval(load, refreshIntervalMs);
    return () => {
      cancelled = true;
      clearInterval(id);
    };
  }, [symbol, refreshIntervalMs]);

  return (
    <div className="bg-[#0d1117] border border-[#21262d] rounded-lg flex flex-col h-full font-mono text-[10px]">
      <div className="flex items-center justify-between px-2 py-1 border-b border-[#21262d]">
        <span className="font-bold text-slate-300 text-[9px] uppercase tracking-wider">
          My Widget
        </span>
        <span className="text-[8px] text-slate-500">{symbol}</span>
      </div>
      <div className="flex-1 overflow-y-auto">
        {loading ? (
          <div className="p-3 text-[10px] text-slate-500">Loading…</div>
        ) : positions.length === 0 ? (
          <div className="p-3 text-[10px] text-slate-500">No open positions for {symbol}</div>
        ) : (
          <table className="w-full text-[10px]">
            <thead>
              <tr className="text-slate-500">
                <th className="text-left px-2 py-1">Symbol</th>
                <th className="text-right px-2 py-1">Qty</th>
                <th className="text-right px-2 py-1">Avg</th>
                <th className="text-right px-2 py-1">LTP</th>
              </tr>
            </thead>
            <tbody>
              {positions.map(p => (
                <tr key={p.symbol} className="border-t border-[#21262d]">
                  <td className="px-2 py-1 text-slate-200">{p.symbol}</td>
                  <td className="px-2 py-1 text-right">{p.qty}</td>
                  <td className="px-2 py-1 text-right">{p.avgPrice.toFixed(2)}</td>
                  <td className="px-2 py-1 text-right">{p.ltp.toFixed(2)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  );
}
```

Conventions to match the rest of the codebase:

- `bg-[#0d1117]`, `border-[#21262d]`, `font-mono text-[10px]`, `text-slate-*` colour palette.
- A title bar with uppercase label and a small right-aligned status indicator.
- `flex flex-col h-full` and a scrollable `overflow-y-auto` body.
- Export the component as a `default` export — the registry uses `import()` which returns `{ default: Component }`.

If you only have the shape of the UI but no live data yet, follow the empty-state pattern from `NewsFeed.tsx`:

```tsx
import React from "react";
import { Wrench } from "lucide-react";

interface MyNewWidgetProps {
  symbol: string;
}

export default function MyNewWidget({ symbol }: MyNewWidgetProps) {
  return (
    <div className="bg-[#0d1117] border border-[#21262d] rounded-lg flex flex-col h-full font-mono text-[10px]">
      <div className="flex items-center justify-between px-2 py-1 border-b border-[#21262d]">
        <span className="font-bold text-slate-300 text-[9px] uppercase tracking-wider">My Widget</span>
        <span className="text-[8px] text-slate-500">{symbol}</span>
      </div>
      <div className="flex-1 flex items-center justify-center p-6">
        <div className="flex flex-col items-center text-center">
          <Wrench className="w-12 h-12 text-slate-600 mb-3" />
          <div className="text-sm font-semibold text-slate-300">My Widget — Pending Backend Integration</div>
          <div className="text-[11px] text-slate-500 mt-1 max-w-xs">
            No data is shown. The backend endpoint is not yet wired.
          </div>
        </div>
      </div>
    </div>
  );
}
```

## Step 2: Add the widget type

Add a new string to the `WidgetType` union in [trade_j_frontend/src/domain/dashboard.ts](../../trade_j_frontend/src/domain/dashboard.ts):

```ts
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
  | "my-new-widget";  // <- new line
```

The `WidgetType` union is the source of truth for all widget types; the `WIDGET_REGISTRY` is keyed by it.

## Step 3: Register the component in the widget registry

Add a new entry in [trade_j_frontend/src/domain/widgetRegistry.ts](../../trade_j_frontend/src/domain/widgetRegistry.ts):

```ts
export const WIDGET_REGISTRY: Record<WidgetType, WidgetDefinition> = {
  // ... existing entries ...
  "my-new-widget": {
    component: () => import("../components/MyNewWidget"),
    defaultProps: {},
    displayName: "My New Widget",
    category: "tools",
  },
};
```

`component` is a dynamic import — Vite will code-split it. The categories are `"market" | "trading" | "analysis" | "tools"`. The `BottomDashboard` and `DashboardRenderer` both resolve a `WidgetType` to its component through this registry.

## Step 4: Add the tab to the bottom dashboard (or a new full dashboard)

To add a tab to the bottom row, edit [trade_j_frontend/src/domain/dashboards.ts](../../trade_j_frontend/src/domain/dashboards.ts):

```ts
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
    { id: "my-new-widget", type: "my-new-widget", label: "Mine", props: { refreshIntervalMs: 5_000 } },
  ],
};
```

The `id` is the local tab id (used by the active-state machine in `App.tsx`); the `type` is the `WidgetType` that resolves to your component. The `props` you set here are merged into the props the component receives.

To add the widget to a full grid dashboard instead (e.g. the default trading layout in [dashboard.ts](../../trade_j_frontend/src/domain/dashboard.ts)):

```ts
{
  id: "my-new-widget",
  type: "my-new-widget",
  props: { symbol: "RELIANCE" },
  position: { x: 0, y: 6, w: 4, h: 1 },
},
```

The grid is 12 columns wide; pick a `w` between 1 and 12 and a `y` that does not overlap other widgets.

## Step 5: Wire props from the top-level App

If your widget needs data the top-level `App` component holds (e.g. `symbol`, `lastPrice`, `optionChain`), pass it through the `widgetProps` map in [App.tsx](../../trade_j_frontend/src/App.tsx) at the `<BottomDashboard widgetProps={...} />` call site.

For a new top-level prop:

```tsx
widgetProps={{
  // ... existing props ...
  myNewSymbol: symbol,
  myNewPositions: ordersState.positions,
}}
```

The corresponding `WidgetConfig.props` in `BOTTOM_DASHBOARD_CONFIG` does not need to declare it — the runtime spread on `<Component {...widgetProps} />` in `BottomDashboard.tsx` will pick it up.

## Step 6: Test it

The frontend uses Vitest. Place a smoke test in `trade_j_frontend/src/components/MyNewWidget.test.tsx`:

```tsx
import { describe, it, expect, vi } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import MyNewWidget from "./MyNewWidget";

describe("MyNewWidget", () => {
  it("shows the empty state when no positions are returned", async () => {
    vi.spyOn(global, "fetch").mockResolvedValueOnce(
      new Response(JSON.stringify([]), { status: 200 })
    );
    render(<MyNewWidget symbol="RELIANCE" />);
    await waitFor(() =>
      expect(screen.getByText(/No open positions/i)).toBeInTheDocument()
    );
  });
});
```

Run with `npm test`. The existing test files in `trade_j_frontend/src/components/` (e.g. `ScannerPanel.test.tsx`, `ErrorBoundary.test.tsx`) are the patterns to copy.

## Common pitfalls

- **Forgetting the `WidgetType` union entry** — TypeScript will reject the registry and the `BOTTOM_DASHBOARD_CONFIG` until the new string is added to the union. The error is a literal-type check, not a runtime failure.
- **Forgetting to register the component** — the registry is a strict `Record<WidgetType, WidgetDefinition>`. Every `WidgetType` must have an entry, or TypeScript fails compilation.
- **Props mismatch** — the `BottomDashboard` spreads `widgetProps` onto the component, so the component must accept those props by name. Either declare them in your `interface MyNewWidgetProps` or use rest spread.
- **Dynamic import path typo** — `import("../components/MyNewWidget")` must match the file name exactly (case-sensitive). Vite will report a build-time error if the path is wrong.
- **Using a non-`default` export** — the registry expects `import("../...")` to return `{ default: Component }`. Always export your component as `default`.
- **Forgetting to add the tab to `BOTTOM_DASHBOARD_CONFIG`** — the type is registered but the bottom tab is hidden until you add a `widgets` entry. There is no auto-discovery.
- **Empty grid slots** — when adding a widget to the default trading dashboard, double-check that the `position` does not overlap existing widgets (`y: 7, w: 12` collides with the bottom watchlist).

## See also

- Bottom dashboard component: [BottomDashboard.tsx](../../trade_j_frontend/src/components/BottomDashboard.tsx)
- Grid dashboard renderer: [DashboardRenderer.tsx](../../trade_j_frontend/src/components/DashboardRenderer.tsx)
- Widget registry: [widgetRegistry.ts](../../trade_j_frontend/src/domain/widgetRegistry.ts)
- Dashboard configs: [dashboards.ts](../../trade_j_frontend/src/domain/dashboards.ts)
- Type definitions: [dashboard.ts](../../trade_j_frontend/src/domain/dashboard.ts)
- Honest empty-state example: [NewsFeed.tsx](../../trade_j_frontend/src/components/NewsFeed.tsx)
- Data-driven example: [RiskCalculator.tsx](../../trade_j_frontend/src/components/RiskCalculator.tsx)
- Tab-button integration site: [App.tsx](../../trade_j_frontend/src/App.tsx)
