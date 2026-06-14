import React, { useEffect, useState } from "react";
import { TrendingUp, TrendingDown, Activity } from "lucide-react";

interface StrategyVisualizationProps {
  symbol: string;
  exchange: string;
}

interface CatalogRow {
  id: string;
  counters: Record<string, number>;
}

function readCount(counters: Record<string, number>, eventType: string, outcome: string): number {
  for (const [k, v] of Object.entries(counters)) {
    const parts = k.split("|");
    if (parts.length >= 3 && parts[1] === eventType && parts[2] === outcome) return v;
  }
  return 0;
}

/**
 * Strategy signals panel. Reads from the in-memory
 * {@code StrategyMetricsRegistry} via the catalog endpoint
 * ({@code GET /api/v1/strategies/catalog}). Auto-refreshes every 5 s.
 *
 * <p>Replaces the previous "Pending Backend" stub. Each row is a
 * strategy loaded in the {@code GraphStrategySandbox}, with its
 * cumulative OK / ERROR / TIMEOUT counter for the most common event
 * types (TickEvent, CandleEvent).
 */
export default function StrategyVisualization({ symbol, exchange }: StrategyVisualizationProps) {
  const [rows, setRows] = useState<CatalogRow[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    const load = async () => {
      try {
        const res = await fetch("/api/v1/strategies/catalog");
        if (!res.ok) {
          setError(`Failed to load: ${res.status}`);
          setLoading(false);
          return;
        }
        const data = (await res.json()) as CatalogRow[];
        if (cancelled) return;
        setRows(data);
        setLoading(false);
      } catch (e: any) {
        if (!cancelled) {
          setError(e?.message ?? "network error");
          setLoading(false);
        }
      }
    };
    load();
    const t = setInterval(load, 5_000);
    return () => {
      cancelled = true;
      clearInterval(t);
    };
  }, []);

  const totalOk = rows.reduce((s, r) => s + readCount(r.counters, "TickEvent", "OK"), 0);
  const totalErr = rows.reduce((s, r) => s + readCount(r.counters, "TickEvent", "ERROR"), 0);
  const totalTimeout = rows.reduce((s, r) => s + readCount(r.counters, "TickEvent", "TIMEOUT"), 0);

  return (
    <div className="bg-[#0d1117] border border-[#21262d] rounded-lg flex flex-col font-mono text-[10px]">
      <div className="flex items-center justify-between px-3 py-1.5 border-b border-[#21262d]">
        <div className="flex items-center gap-2">
          <span className="font-black text-[11px] text-[#f0b429]">📊 Strategy Signals</span>
          <span className="text-[9px] text-slate-500">{symbol} · {exchange}</span>
        </div>
        <div className="flex items-center gap-3 text-[9px]">
          <span className="text-[#26a69a] flex items-center gap-1">
            <TrendingUp className="w-3 h-3" /> {totalOk} ok
          </span>
          <span className="text-[#ef5350] flex items-center gap-1">
            <TrendingDown className="w-3 h-3" /> {totalErr} err
          </span>
          <span className="text-[#f0b429] flex items-center gap-1">
            <Activity className="w-3 h-3" /> {totalTimeout} to
          </span>
        </div>
      </div>

      <div className="flex-1 overflow-y-auto p-2">
        {loading && (
          <div className="text-[10px] text-slate-500">Loading strategy metrics…</div>
        )}
        {error && (
          <div className="text-[10px] text-[#ef5350]">{error}</div>
        )}
        {!loading && !error && rows.length === 0 && (
          <div className="flex flex-col items-center justify-center text-center p-6">
            <TrendingUp className="w-12 h-12 text-slate-600 mb-3" />
            <div className="text-sm font-semibold text-slate-300">No strategies loaded</div>
            <div className="text-[11px] text-slate-500 mt-1 max-w-xs">
              Start a strategy or replay a window to populate the
              StrategyMetricsRegistry.
            </div>
          </div>
        )}
        {rows.length > 0 && (
          <table className="w-full text-[10px]">
            <thead>
              <tr className="text-slate-500 uppercase text-[8px]">
                <th className="text-left py-1">Plugin</th>
                <th className="text-right">Signals</th>
                <th className="text-right">Errors</th>
                <th className="text-right">Timeouts</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((r) => (
                <tr key={r.id} className="border-t border-[#21262d]">
                  <td className="py-1 text-slate-200 font-mono">{r.id}</td>
                  <td className="text-right text-[#26a69a]">
                    {readCount(r.counters, "TickEvent", "OK") +
                      readCount(r.counters, "CandleEvent", "OK")}
                  </td>
                  <td className="text-right text-[#ef5350]">
                    {readCount(r.counters, "TickEvent", "ERROR") +
                      readCount(r.counters, "CandleEvent", "ERROR")}
                  </td>
                  <td className="text-right text-[#f0b429]">
                    {readCount(r.counters, "TickEvent", "TIMEOUT") +
                      readCount(r.counters, "CandleEvent", "TIMEOUT")}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      <div className="px-3 py-1.5 border-t border-[#21262d] bg-[#161b22]/50 text-[8px] text-slate-500 flex items-center justify-between">
        <span>Integration Pattern: Chart markers + performance panel</span>
        <span>Backend: /api/v1/strategies/catalog (StrategyMetricsRegistry)</span>
      </div>
    </div>
  );
}
