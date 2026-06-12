import React, { useEffect, useState } from "react";
import { marketBus } from "../api/MarketDataBus";
import type { MarketEvent } from "../api/marketContracts";

interface CurvePoint {
  timestampMs: number;
  realizedPnlPaisa: number;
}

/**
 * Equity curve widget. Polls
 * {@code GET /api/v1/portfolio/equity-curve} for the historical curve, then
 * subscribes to the {@code PNL_UPDATE} event on the bus so the line
 * keeps moving in real time as new {@code TradeClosed} events arrive.
 *
 * <p>Backed by {@link com.tradej.strategy.portfolio.PortfolioEngine}'s
 * {@code equityCurveSnapshot()}. The widget appends a synthetic
 * point per PnL tick so the chart reflects live trading between
 * REST polls.
 */
const EquityCurve: React.FC = () => {
  const [points, setPoints] = useState<CurvePoint[]>([]);
  const [liveRunning, setLiveRunning] = useState<number | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    const load = async () => {
      try {
        const res = await fetch("/api/v1/portfolio/equity-curve");
        if (!res.ok) return;
        const data = (await res.json()) as CurvePoint[];
        if (!cancelled) {
          setPoints(data);
          setLiveRunning(data.length > 0 ? data[data.length - 1].realizedPnlPaisa : 0);
          setLoading(false);
        }
      } catch {
        if (!cancelled) setLoading(false);
      }
    };
    load();
    const t = setInterval(load, 30_000); // refresh every 30s as a safety net
    return () => {
      cancelled = true;
      clearInterval(t);
    };
  }, []);

  useEffect(() => {
    // Live-append the running realized PnL on every PNL_UPDATE.
    return marketBus.subscribe((e: MarketEvent) => {
      if (e.type !== "PNL_UPDATE") return;
      setLiveRunning((prev) => (prev == null ? e.realizedPnlPaisa : prev + e.realizedPnlPaisa));
    });
  }, []);

  const merged: CurvePoint[] =
    liveRunning != null && liveRunning !== 0
      ? [...points, { timestampMs: Date.now(), realizedPnlPaisa: liveRunning }]
      : points;

  if (loading) {
    return (
      <div className="bg-[#0d1117] border border-[#21262d] rounded p-2 h-full flex items-center justify-center">
        <span className="text-[10px] text-slate-500">Loading equity curve…</span>
      </div>
    );
  }
  if (merged.length === 0) {
    return (
      <div className="bg-[#0d1117] border border-[#21262d] rounded p-2 h-full flex items-center justify-center">
        <span className="text-[10px] text-slate-500">No closed trades yet</span>
      </div>
    );
  }

  const w = 400;
  const h = 160;
  const minTs = merged[0].timestampMs;
  const maxTs = merged[merged.length - 1].timestampMs;
  const minPnl = Math.min(0, ...merged.map((p) => p.realizedPnlPaisa));
  const maxPnl = Math.max(0, ...merged.map((p) => p.realizedPnlPaisa));
  const spanPnl = Math.max(1, maxPnl - minPnl);
  const spanTs = Math.max(1, maxTs - minTs);

  const path = merged
    .map((p, i) => {
      const x = ((p.timestampMs - minTs) / spanTs) * (w - 8) + 4;
      const y = h - 4 - ((p.realizedPnlPaisa - minPnl) / spanPnl) * (h - 8);
      return `${i === 0 ? "M" : "L"}${x.toFixed(1)},${y.toFixed(1)}`;
    })
    .join(" ");
  const last = merged[merged.length - 1];

  return (
    <div className="bg-[#0d1117] border border-[#21262d] rounded p-2 h-full flex flex-col">
      <div className="flex items-center justify-between mb-1">
        <span className="text-[10px] font-bold text-slate-200 uppercase">Equity Curve</span>
        <span
          className={`text-[10px] font-bold ${
            last.realizedPnlPaisa >= 0 ? "text-[#26a69a]" : "text-[#ef5350]"
          }`}
        >
          {(last.realizedPnlPaisa / 100).toLocaleString("en-IN", {
            minimumFractionDigits: 2,
            maximumFractionDigits: 2,
          })}
        </span>
      </div>
      <svg viewBox={`0 0 ${w} ${h}`} className="w-full h-[160px]">
        <line
          x1="0" y1={h / 2} x2={w} y2={h / 2}
          stroke="#21262d" strokeDasharray="2 4" strokeWidth="1"
        />
        <path d={path} stroke="#26a69a" strokeWidth="1.5" fill="none" />
      </svg>
    </div>
  );
};

export default EquityCurve;
