import React, { useMemo } from "react";
import { useSignals } from "../store/ordersStore";
import { usePositions } from "../store/ordersStore";
import type { Signal } from "../store/types";

const MAX_SIGNALS = 200;

function timeAgo(ts: number): string {
  const secs = Math.floor((Date.now() - ts) / 1000);
  if (secs < 60) return `${secs}s ago`;
  const mins = Math.floor(secs / 60);
  if (mins < 60) return `${mins}m ago`;
  const hours = Math.floor(mins / 60);
  if (hours < 24) return `${hours}h ago`;
  return `${Math.floor(hours / 24)}d ago`;
}

function formatPnlPaisa(paisa: number): string {
  const rupees = paisa / 100;
  const sign = rupees >= 0 ? "+" : "";
  return `${sign}\u20B9${rupees.toLocaleString("en-IN", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
}

interface StrategyStats {
  strategyId: string;
  count: number;
  buyCount: number;
  sellCount: number;
  lastTs: number;
  lastSymbol: string | null;
  lastSide: "BUY" | "SELL" | null;
}

function aggregate(signals: Signal[]): StrategyStats[] {
  const byStrategy = new Map<string, StrategyStats>();
  for (const s of signals) {
    const stat = byStrategy.get(s.strategyId) ?? {
      strategyId: s.strategyId,
      count: 0,
      buyCount: 0,
      sellCount: 0,
      lastTs: 0,
      lastSymbol: null,
      lastSide: null,
    };
    stat.count++;
    if (s.side === "BUY") stat.buyCount++;
    else stat.sellCount++;
    if (s.ts > stat.lastTs) {
      stat.lastTs = s.ts;
      stat.lastSymbol = s.symbol;
      stat.lastSide = s.side;
    }
    byStrategy.set(s.strategyId, stat);
  }
  return Array.from(byStrategy.values()).sort((a, b) => b.lastTs - a.lastTs);
}

export default function StrategyDashboard() {
  const { signals } = useSignals((s) => s);
  const { positions, realizedPnlPaisa, unrealizedPnlPaisa, netExposurePaisa } = usePositions((s) => s);
  const stats = useMemo(() => aggregate(signals.slice(0, MAX_SIGNALS)), [signals]);
  const recent = useMemo(() => signals.slice(0, 30), [signals]);

  return (
    <div className="flex flex-col h-full bg-[#0d1117] text-[#c9d1d9] font-mono text-[10px]">
      <div className="flex items-center justify-between px-2 py-1 border-b border-[#21262d]">
        <span className="font-bold text-slate-300 text-[9px] uppercase tracking-wider">Strategy Dashboard</span>
        <span className="text-[8px] text-slate-500">{signals.length} signals · {positions.length} positions</span>
      </div>

      <div className="px-2 py-1 border-b border-[#21262d] grid grid-cols-3 gap-2 text-[9px]">
        <div>
          <div className="text-slate-500 uppercase">Realized P&amp;L</div>
          <div className={`font-bold ${realizedPnlPaisa >= 0 ? "text-emerald-400" : "text-rose-400"}`}>{formatPnlPaisa(realizedPnlPaisa)}</div>
        </div>
        <div>
          <div className="text-slate-500 uppercase">Unrealized P&amp;L</div>
          <div className={`font-bold ${unrealizedPnlPaisa >= 0 ? "text-emerald-400" : "text-rose-400"}`}>{formatPnlPaisa(unrealizedPnlPaisa)}</div>
        </div>
        <div>
          <div className="text-slate-500 uppercase">Net Exposure</div>
          <div className="font-bold text-slate-200">{formatPnlPaisa(netExposurePaisa)}</div>
        </div>
      </div>

      {stats.length === 0 && (
        <div className="flex-1 flex items-center justify-center text-slate-600 text-[10px] p-4 text-center">
          No strategy signals yet.<br />
          <span className="text-slate-500 text-[9px]">Signals stream in via SSE as strategies publish them.</span>
        </div>
      )}

      {stats.length > 0 && (
        <>
          <div className="px-2 py-1 border-b border-[#21262d]/50">
            <div className="text-[9px] text-slate-500 uppercase font-bold mb-0.5">Active Strategies</div>
            <div className="space-y-0.5">
              {stats.map((s) => (
                <div key={s.strategyId} className="grid grid-cols-5 gap-1 text-[9px] items-center">
                  <span className="font-bold text-slate-200 truncate">{s.strategyId}</span>
                  <span className="text-right text-slate-300">{s.count}</span>
                  <span className="text-right text-emerald-400">{s.buyCount}</span>
                  <span className="text-right text-rose-400">{s.sellCount}</span>
                  <span className="text-right text-slate-500">{timeAgo(s.lastTs)}</span>
                </div>
              ))}
            </div>
          </div>

          <div className="px-2 py-1 border-b border-[#21262d]/50">
            <div className="text-[9px] text-slate-500 uppercase font-bold mb-0.5">Recent Signals</div>
            <div className="space-y-0.5 max-h-[200px] overflow-y-auto">
              {recent.length === 0 ? (
                <div className="text-slate-600 text-[9px]">No signals</div>
              ) : recent.map((s, i) => (
                <div key={`${s.strategyId}-${s.ts}-${i}`} className="grid grid-cols-5 gap-1 text-[9px]">
                  <span className="text-slate-200 truncate">{s.strategyId}</span>
                  <span className="text-slate-300 truncate">{s.symbol}</span>
                  <span className={s.side === "BUY" ? "text-emerald-400 font-bold" : "text-rose-400 font-bold"}>{s.side}</span>
                  <span className="text-right text-slate-300">{s.strength.toFixed(2)}</span>
                  <span className="text-right text-slate-500">{timeAgo(s.ts)}</span>
                </div>
              ))}
            </div>
          </div>
        </>
      )}

      {positions.length > 0 && (
        <div className="px-2 py-1 flex-1 overflow-y-auto">
          <div className="text-[9px] text-slate-500 uppercase font-bold mb-0.5">Positions</div>
          <div className="space-y-0.5">
            {positions.map((p) => (
              <div key={p.symbol} className="grid grid-cols-4 gap-1 text-[9px]">
                <span className="text-slate-200 truncate">{p.symbol}</span>
                <span className="text-right text-slate-300">{p.netQuantity}</span>
                <span className="text-right text-slate-300">@{(p.averagePricePaisa / 100).toFixed(2)}</span>
                <span className={`text-right font-bold ${p.unrealizedPnlPaisa >= 0 ? "text-emerald-400" : "text-rose-400"}`}>
                  {formatPnlPaisa(p.unrealizedPnlPaisa)}
                </span>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}
