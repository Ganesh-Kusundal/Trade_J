import React from "react";
import type { DashboardSpec, WidgetProps } from "./types";
import { useMarket } from "../store/marketStore";
import { useOrders, usePositions, useSignals } from "../store/ordersStore";
import type { WidgetRenderer } from "./types";

const COLORS = {
  pnlPositive: "#22c55e",
  pnlNegative: "#ef4444",
  axis: "#475569",
  grid: "#1f2937",
  text: "#cbd5e1",
};

function PnLCurve({ spec }: WidgetProps) {
  const { realizedPnlPaisa, unrealizedPnlPaisa } = usePositions((s) => s);
  const { fills } = useMarket((s) => s);
  const points = React.useMemo(() => {
    let running = 0;
    const series: Array<{ x: number; y: number }> = [{ x: 0, y: 0 }];
    // Fills arrive newest-first; reverse for chronological P&L.
    for (const f of [...fills].reverse()) {
      const signed = (f.side === "BUY" ? 1 : -1) * f.price * f.quantity;
      running += signed;
      series.push({ x: series.length, y: running / 100 });
    }
    series.push({ x: series.length, y: (realizedPnlPaisa + unrealizedPnlPaisa) / 100 });
    return series;
  }, [fills, realizedPnlPaisa, unrealizedPnlPaisa]);

  if (points.length < 2) {
    return <div className="bg-[#0d1117] border border-[#21262d] rounded p-2 h-full text-slate-600 text-[10px]">No fills yet — P&L curve will draw on first fill.</div>;
  }

  const W = 280;
  const H = 80;
  const minY = Math.min(...points.map((p) => p.y));
  const maxY = Math.max(...points.map((p) => p.y));
  const range = maxY - minY || 1;
  const path = points
    .map((p, i) => {
      const x = (i / (points.length - 1)) * W;
      const y = H - ((p.y - minY) / range) * H;
      return `${i === 0 ? "M" : "L"} ${x.toFixed(1)} ${y.toFixed(1)}`;
    })
    .join(" ");

  const finalY = points[points.length - 1].y;
  const color = finalY >= 0 ? COLORS.pnlPositive : COLORS.pnlNegative;

  return (
    <div className="bg-[#0d1117] border border-[#21262d] rounded p-2 h-full">
      <div className="text-[9px] uppercase font-bold text-slate-500 mb-1">{spec.title ?? "P&L Curve"}</div>
      <svg width={W} height={H} viewBox={`0 0 ${W} ${H}`} preserveAspectRatio="none">
        <line x1={0} y1={H / 2} x2={W} y2={H / 2} stroke={COLORS.grid} strokeWidth="0.5" />
        <path d={path} stroke={color} strokeWidth="1.5" fill="none" />
      </svg>
      <div className={`text-[10px] font-bold ${finalY >= 0 ? "text-emerald-400" : "text-rose-400"}`}>
        {finalY >= 0 ? "+" : ""}\u20B9{finalY.toLocaleString("en-IN", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
      </div>
    </div>
  );
}

function SignalStream({ spec }: WidgetProps) {
  const { signals } = useSignals((s) => s);
  const recent = signals.slice(0, 12);
  return (
    <div className="bg-[#0d1117] border border-[#21262d] rounded p-2 h-full overflow-hidden">
      <div className="text-[9px] uppercase font-bold text-slate-500 mb-1">{spec.title ?? "Signal Stream"}</div>
      {recent.length === 0 ? (
        <div className="text-slate-600 text-[10px]">No signals yet</div>
      ) : (
        <div className="space-y-0.5 max-h-[120px] overflow-y-auto">
          {recent.map((s, i) => (
            <div key={`${s.strategyId}-${s.ts}-${i}`} className="flex items-center gap-1 text-[9px]">
              <span className={s.side === "BUY" ? "text-emerald-400 font-bold" : "text-rose-400 font-bold"}>
                {s.side}
              </span>
              <span className="text-slate-200">{s.symbol}</span>
              <span className="text-slate-500 ml-auto">{s.strategyId}</span>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

function DepthSnapshot({ spec }: WidgetProps) {
  const { bids, asks, ltp } = useMarket((s) => s);
  return (
    <div className="bg-[#0d1117] border border-[#21262d] rounded p-2 h-full overflow-hidden">
      <div className="text-[9px] uppercase font-bold text-slate-500 mb-1">{spec.title ?? "Depth (top 5)"}</div>
      <div className="grid grid-cols-3 gap-1 text-[8px] text-slate-500 mb-0.5">
        <div>Price</div><div className="text-right">Qty</div><div className="text-right">Orders</div>
      </div>
      <div className="space-y-px">
        {asks.slice(0, 5).reverse().map((a, i) => (
          <div key={`a-${i}`} className="grid grid-cols-3 text-[9px] text-rose-400">
            <div>{a.price.toFixed(2)}</div>
            <div className="text-right text-slate-200">{a.quantity}</div>
            <div className="text-right text-slate-500">{a.orders}</div>
          </div>
        ))}
        <div className="text-center text-[10px] font-bold text-amber-400 border-y border-[#21262d] py-0.5">
          {ltp > 0 ? ltp.toFixed(2) : "—"}
        </div>
        {bids.slice(0, 5).map((b, i) => (
          <div key={`b-${i}`} className="grid grid-cols-3 text-[9px] text-emerald-400">
            <div>{b.price.toFixed(2)}</div>
            <div className="text-right text-slate-200">{b.quantity}</div>
            <div className="text-right text-slate-500">{b.orders}</div>
          </div>
        ))}
      </div>
    </div>
  );
}

function ScanHits({ spec }: WidgetProps) {
  const { positions } = usePositions((s) => s);
  return (
    <div className="bg-[#0d1117] border border-[#21262d] rounded p-2 h-full overflow-hidden">
      <div className="text-[9px] uppercase font-bold text-slate-500 mb-1">{spec.title ?? "Active Positions"}</div>
      {positions.length === 0 ? (
        <div className="text-slate-600 text-[10px]">No positions</div>
      ) : (
        <div className="space-y-0.5">
          {positions.slice(0, 8).map((p) => (
            <div key={p.symbol} className="grid grid-cols-4 text-[9px]">
              <span className="text-slate-200">{p.symbol}</span>
              <span className="text-right text-slate-300">{p.netQuantity}</span>
              <span className="text-right text-slate-300">@{(p.averagePricePaisa / 100).toFixed(2)}</span>
              <span className={`text-right font-bold ${p.unrealizedPnlPaisa >= 0 ? "text-emerald-400" : "text-rose-400"}`}>
                {(p.unrealizedPnlPaisa / 100).toFixed(2)}
              </span>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

// ── OptionChain widget ──
// Shows the option chain for the underlying specified in the
// widget's dataSource. Calls /api/v1/options/chain?underlying=...
// Renders spot price, max pain, PCR, total call/Put OI, and
// the strikes table (call OI / LTP, strike, put LTP / OI).
// Empty chain (no strikes) shows a clean "no chain" message.
interface OptionChainStrike {
  strikePaisa: number;
  callOi?: number;
  callLtpPaisa?: number;
  putOi?: number;
  putLtpPaisa?: number;
}

interface OptionChainData {
  underlying?: string;
  spotPricePaisa?: number;
  maxPainStrikePaisa?: number;
  putCallRatio?: number;
  totalCallOi?: number;
  totalPutOi?: number;
  strikes?: OptionChainStrike[];
}

interface OptionChainDataSource {
  underlying?: string;
  segment?: string;
  depth?: number;
}

function OptionChain({ spec }: WidgetProps) {
  const ds: OptionChainDataSource = (spec.dataSource as OptionChainDataSource) ?? {};
  const underlying: string = ds.underlying ?? "NIFTY";
  const segment: string = ds.segment ?? "NSE_FNO";
  const depth: number = typeof ds.depth === "number" ? ds.depth : 5;

  const [data, setData] = React.useState<OptionChainData | null>(null);
  const [err, setErr] = React.useState<string | null>(null);

  React.useEffect(() => {
    let cancelled = false;
    const url = `/api/v1/options/chain?underlying=${encodeURIComponent(underlying)}&segment=${encodeURIComponent(segment)}&depth=${depth}`;
    fetch(url)
      .then((r) => r.json())
      .then((j) => { if (!cancelled) { setData(j as OptionChainData); setErr(null); } })
      .catch((e) => { if (!cancelled) setErr(String(e?.message ?? e)); });
    return () => { cancelled = true; };
  }, [underlying, segment, depth]);

  if (err) {
    return <div className="bg-[#0d1117] border border-rose-500/40 rounded p-2 text-rose-400 text-[10px]">Option chain error: {err}</div>;
  }
  if (!data) {
    return <div className="bg-[#0d1117] border border-[#21262d] rounded p-2 text-slate-600 text-[10px]">Loading option chain…</div>;
  }
  const strikes: OptionChainStrike[] = Array.isArray(data.strikes) ? data.strikes : [];
  const spotPaisa: number = data.spotPricePaisa ?? 0;
  const maxPain: number = data.maxPainStrikePaisa ?? 0;
  const pcr: number = data.putCallRatio ?? 0;
  const totalCallOi: number = data.totalCallOi ?? 0;
  const totalPutOi: number = data.totalPutOi ?? 0;

  return (
    <div className="bg-[#0d1117] border border-[#21262d] rounded p-2 h-full overflow-auto text-[10px]">
      <div className="text-[9px] uppercase font-bold text-slate-500 mb-1">
        {spec.title ?? `Option Chain — ${data.underlying ?? underlying}`}
      </div>
      <div className="grid grid-cols-4 gap-1 mb-1 text-[9px]">
        <div>
          <div className="text-slate-500">Spot</div>
          <div className="text-slate-200 font-bold">{(spotPaisa / 100).toFixed(2)}</div>
        </div>
        <div>
          <div className="text-slate-500">Max Pain</div>
          <div className="text-amber-400 font-bold">{maxPain > 0 ? (maxPain / 100).toFixed(2) : "—"}</div>
        </div>
        <div>
          <div className="text-slate-500">PCR</div>
          <div className="text-cyan-400 font-bold">{pcr.toFixed(2)}</div>
        </div>
        <div>
          <div className="text-slate-500">Strikes</div>
          <div className="text-slate-300">{strikes.length}</div>
        </div>
      </div>
      <div className="grid grid-cols-2 gap-2 mb-1 text-[9px]">
        <div className="text-emerald-400">Call OI: {totalCallOi}</div>
        <div className="text-rose-400">Put OI: {totalPutOi}</div>
      </div>
      {strikes.length === 0 ? (
        <div className="text-slate-600 text-[10px] mt-1">No strikes for {data.underlying ?? underlying} (live data required)</div>
      ) : (
        <table className="w-full text-[9px]">
          <thead className="text-slate-500">
            <tr className="grid grid-cols-5">
              <th className="text-right text-emerald-400">Call OI</th>
              <th className="text-right text-emerald-400">Call LTP</th>
              <th className="text-center">Strike</th>
              <th className="text-left text-rose-400">Put LTP</th>
              <th className="text-left text-rose-400">Put OI</th>
            </tr>
          </thead>
          <tbody>
            {strikes.map((s, i) => (
              <tr key={i} className="grid grid-cols-5 border-t border-[#21262d]">
                <td className="text-right text-slate-200">{s.callOi ?? 0}</td>
                <td className="text-right text-slate-300">{((s.callLtpPaisa ?? 0) / 100).toFixed(2)}</td>
                <td className="text-center font-bold text-amber-400">{(s.strikePaisa / 100).toFixed(0)}</td>
                <td className="text-left text-slate-300">{((s.putLtpPaisa ?? 0) / 100).toFixed(2)}</td>
                <td className="text-left text-slate-200">{s.putOi ?? 0}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
}

import { registerWidget } from "./types";
registerWidget("pnl-curve", PnLCurve);
registerWidget("signal-stream", SignalStream);
registerWidget("depth-snapshot", DepthSnapshot);
registerWidget("scan-hits", ScanHits);
registerWidget("option-chain", OptionChain);

export const newWidgets: WidgetRenderer[] = [PnLCurve, SignalStream, DepthSnapshot, ScanHits, OptionChain];
