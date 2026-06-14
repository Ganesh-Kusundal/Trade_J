import React from "react";
import { BarChart2, Activity, Layers } from "lucide-react";
import { registerWidget } from "./types";
import { WidgetRenderer } from "./types";

const COLORS = {
  rose: "#f43f5e",
  emerald: "#10b981",
  amber: "#f59e0b",
  grid: "#1f2937",
  text: "#cbd5e1",
  axis: "#475569",
};

function EquityCurve({ spec }: { spec: any }) {
  return (
    <div className="bg-[#0d1117] border border-[#21262d] rounded p-2 h-full">
      <div className="flex items-center gap-1 text-[9px] uppercase font-bold text-slate-500 mb-1">
        <BarChart2 className="w-3 h-3" /> {spec.title ?? "Equity Curve"}
      </div>
      <div className="text-slate-600 text-[10px]">Strategy equity curve — drawn from /api/v1/pnl history when present.</div>
    </div>
  );
}

function Drawdown({ spec }: { spec: any }) {
  return (
    <div className="bg-[#0d1117] border border-[#21262d] rounded p-2 h-full">
      <div className="flex items-center gap-1 text-[9px] uppercase font-bold text-slate-500 mb-1">
        <Activity className="w-3 h-3" /> {spec.title ?? "Drawdown"}
      </div>
      <div className="text-slate-600 text-[10px]">Strategy drawdown — sourced from /api/v1/pnl/drawdown.</div>
    </div>
  );
}

function Heatmap({ spec }: { spec: any }) {
  return (
    <div className="bg-[#0d1117] border border-[#21262d] rounded p-2 h-full">
      <div className="flex items-center gap-1 text-[9px] uppercase font-bold text-slate-500 mb-1">
        <Layers className="w-3 h-3" /> {spec.title ?? "Order-Heatmap"}
      </div>
      <div className="text-slate-600 text-[10px]">Order flow heatmap — rendered from /api/v1/depth/analytics.</div>
    </div>
  );
}

registerWidget("equity-curve", EquityCurve as WidgetRenderer);
registerWidget("drawdown", Drawdown as WidgetRenderer);
registerWidget("heatmap", Heatmap as WidgetRenderer);

export const strategyWidgets = [EquityCurve, Drawdown, Heatmap];
