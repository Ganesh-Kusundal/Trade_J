import React from "react";
import type { DashboardSpec, WidgetSpec, WidgetProps } from "./types";
import { getWidget } from "./types";
import { useOrders, usePositions, useSignals } from "../store/ordersStore";

function WidgetRenderer({ spec }: WidgetProps) {
  const Renderer = getWidget(spec.type);
  if (!Renderer) {
    return (
      <div className="bg-[#161b22] border border-rose-500/40 rounded p-2 text-rose-400 text-[10px] font-mono">
        Unknown widget type: {spec.type}
      </div>
    );
  }
  return <Renderer spec={spec} />;
}

function OrdersTable({ spec }: WidgetProps) {
  const { active, completed } = useOrders((s) => s);
  const list = spec.id === "orders" ? active : [...active, ...completed].slice(0, 50);
  return (
    <div className="bg-[#0d1117] border border-[#21262d] rounded p-2 h-full overflow-auto">
      <div className="text-[9px] uppercase font-bold text-slate-500 mb-1">{spec.title ?? "Orders"}</div>
      {list.length === 0 ? <div className="text-slate-600 text-[10px]">No orders</div> : (
        <table className="w-full text-[10px]">
          <thead><tr className="text-slate-500"><th className="text-left">Symbol</th><th>Side</th><th>Qty</th><th>Status</th></tr></thead>
          <tbody>{list.map((o) => (
            <tr key={o.orderId} className="border-t border-[#21262d]/40">
              <td className="text-slate-200">{o.symbol}</td>
              <td className={o.side === "BUY" ? "text-emerald-400" : "text-rose-400"}>{o.side}</td>
              <td className="text-slate-300 text-right">{o.filledQuantity}/{o.quantity}</td>
              <td className="text-slate-400">{o.status}</td>
            </tr>
          ))}</tbody>
        </table>
      )}
    </div>
  );
}

function PositionsTable({ spec }: WidgetProps) {
  const { positions } = usePositions((s) => s);
  return (
    <div className="bg-[#0d1117] border border-[#21262d] rounded p-2 h-full overflow-auto">
      <div className="text-[9px] uppercase font-bold text-slate-500 mb-1">{spec.title ?? "Positions"}</div>
      {positions.length === 0 ? <div className="text-slate-600 text-[10px]">No positions</div> : (
        <table className="w-full text-[10px]">
          <thead><tr className="text-slate-500"><th className="text-left">Symbol</th><th>Qty</th><th>Avg</th><th>uP&L</th></tr></thead>
          <tbody>{positions.map((p) => (
            <tr key={p.symbol} className="border-t border-[#21262d]/40">
              <td className="text-slate-200">{p.symbol}</td>
              <td className="text-slate-300 text-right">{p.netQuantity}</td>
              <td className="text-slate-300 text-right">{(p.averagePricePaisa / 100).toFixed(2)}</td>
              <td className={`text-right ${p.unrealizedPnlPaisa >= 0 ? "text-emerald-400" : "text-rose-400"}`}>
                {(p.unrealizedPnlPaisa / 100).toFixed(2)}
              </td>
            </tr>
          ))}</tbody>
        </table>
      )}
    </div>
  );
}

function PnLSummary({ spec }: WidgetProps) {
  const { realizedPnlPaisa, unrealizedPnlPaisa, netExposurePaisa } = usePositions((s) => s);
  return (
    <div className="bg-[#0d1117] border border-[#21262d] rounded p-2 h-full">
      <div className="text-[9px] uppercase font-bold text-slate-500 mb-1">{spec.title ?? "P&L"}</div>
      <div className="grid grid-cols-3 gap-2 text-[10px]">
        <div>
          <div className="text-slate-500 text-[9px]">Realized</div>
          <div className={`font-bold ${realizedPnlPaisa >= 0 ? "text-emerald-400" : "text-rose-400"}`}>{(realizedPnlPaisa / 100).toFixed(2)}</div>
        </div>
        <div>
          <div className="text-slate-500 text-[9px]">Unrealized</div>
          <div className={`font-bold ${unrealizedPnlPaisa >= 0 ? "text-emerald-400" : "text-rose-400"}`}>{(unrealizedPnlPaisa / 100).toFixed(2)}</div>
        </div>
        <div>
          <div className="text-slate-500 text-[9px]">Net</div>
          <div className="font-bold text-slate-200">{(netExposurePaisa / 100).toFixed(2)}</div>
        </div>
      </div>
    </div>
  );
}

function SignalsTable({ spec }: WidgetProps) {
  const { signals } = useSignals((s) => s);
  const list = signals.slice(0, 20);
  return (
    <div className="bg-[#0d1117] border border-[#21262d] rounded p-2 h-full overflow-auto">
      <div className="text-[9px] uppercase font-bold text-slate-500 mb-1">{spec.title ?? "Signals"}</div>
      {list.length === 0 ? <div className="text-slate-600 text-[10px]">No signals</div> : (
        <table className="w-full text-[10px]">
          <thead><tr className="text-slate-500"><th className="text-left">Strategy</th><th>Symbol</th><th>Side</th><th>Strength</th></tr></thead>
          <tbody>{list.map((s, i) => (
            <tr key={`${s.strategyId}-${s.ts}-${i}`} className="border-t border-[#21262d]/40">
              <td className="text-slate-200">{s.strategyId}</td>
              <td className="text-slate-300">{s.symbol}</td>
              <td className={s.side === "BUY" ? "text-emerald-400" : "text-rose-400"}>{s.side}</td>
              <td className="text-slate-300 text-right">{s.strength.toFixed(2)}</td>
            </tr>
          ))}</tbody>
        </table>
      )}
    </div>
  );
}

import { registerWidget } from "./types";
registerWidget("orders-table", OrdersTable);
registerWidget("positions-table", PositionsTable);
registerWidget("pnl-summary", PnLSummary);
registerWidget("signals-table", SignalsTable);

import "./widgetsExtra";
import "./widgetsStrategy";

// Re-export type for external consumers
export type { DashboardSpec, WidgetSpec, WidgetProps } from "./types";

export function DashboardRenderer({ spec }: { spec: DashboardSpec }) {
  if (spec.layout.kind === "grid") {
    const cols = spec.layout.cols;
    return (
      <div className="grid h-full" style={{ gridTemplateColumns: `repeat(${cols}, 1fr)`, gap: spec.layout.gap }}>
        {spec.widgets.map((w) => <WidgetRenderer key={w.id} spec={w} />)}
      </div>
    );
  }
  if (spec.layout.kind === "tabs") {
    return <TabbedDashboard spec={spec} />;
  }
  if (spec.layout.kind === "split") {
    return <SplitDashboard spec={spec} direction={spec.layout.direction} ratio={spec.layout.ratio} />;
  }
  return <div className="p-2 text-slate-500 text-[10px]">Unsupported layout: {(spec.layout as { kind: string }).kind}</div>;
}

function TabbedDashboard({ spec }: { spec: DashboardSpec }) {
  const tabs = spec.layout.kind === "tabs" ? spec.layout.tabs : [];
  const [active, setActive] = React.useState(tabs[0] ?? "");
  return (
    <div className="h-full flex flex-col">
      <div className="flex items-center gap-1 bg-[#0d1117] border-b border-[#21262d] p-1">
        {tabs.map((t) => (
          <button key={t} onClick={() => setActive(t)}
            className={`px-2 py-0.5 text-[9px] font-bold rounded uppercase ${
              active === t ? "bg-amber-500 text-[#0d1117]" : "text-slate-400 hover:bg-[#21262d]"
            }`}>
            {t}
          </button>
        ))}
      </div>
      <div className="flex-1 grid gap-2 p-1" style={{ gridTemplateColumns: `repeat(${Math.max(1, Math.ceil(spec.widgets.length / Math.max(1, tabs.length)))}, minmax(0, 1fr))` }}>
        {spec.widgets.map((w) => <WidgetRenderer key={w.id} spec={w} />)}
      </div>
    </div>
  );
}

function SplitDashboard({ spec, direction, ratio }: { spec: DashboardSpec; direction: "horizontal" | "vertical"; ratio: number }) {
  const left = spec.widgets.slice(0, Math.ceil(spec.widgets.length * ratio));
  const right = spec.widgets.slice(Math.ceil(spec.widgets.length * ratio));
  const style: React.CSSProperties = direction === "horizontal"
    ? { gridTemplateColumns: `${ratio}fr ${1 - ratio}fr` }
    : { gridTemplateRows: `${ratio}fr ${1 - ratio}fr` };
  return (
    <div className="h-full grid gap-2 p-1" style={style}>
      <div className="grid gap-2" style={direction === "horizontal" ? { gridTemplateRows: `repeat(${left.length}, 1fr)` } : { gridTemplateColumns: `repeat(${left.length}, 1fr)` }}>
        {left.map((w) => <WidgetRenderer key={w.id} spec={w} />)}
      </div>
      <div className="grid gap-2" style={direction === "horizontal" ? { gridTemplateRows: `repeat(${right.length}, 1fr)` } : { gridTemplateColumns: `repeat(${right.length}, 1fr)` }}>
        {right.map((w) => <WidgetRenderer key={w.id} spec={w} />)}
      </div>
    </div>
  );
}
