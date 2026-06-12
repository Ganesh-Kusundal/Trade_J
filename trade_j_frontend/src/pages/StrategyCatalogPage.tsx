import React, { useEffect, useState } from "react";

interface CatalogEntry {
  id: string;
  signals: number;
  errors: number;
  timeouts: number;
  parityStatus: "OK" | "UNKNOWN" | "STALE";
}

interface ApiRow {
  id: string;
  counters: Record<string, number>;
}

/**
 * Strategy catalog page. Fetches
 * {@code GET /api/v1/strategies/catalog} (which reads from the
 * in-memory {@code StrategyMetricsRegistry}) and renders one row
 * per loaded GraphStrategyPlugin with its outcome counters
 * aggregated from the registry.
 */
const StrategyCatalogPage: React.FC = () => {
  const [entries, setEntries] = useState<CatalogEntry[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    const load = async () => {
      try {
        const res = await fetch("/api/v1/strategies/catalog");
        if (!res.ok) {
          setError(`Failed to load catalog: ${res.status}`);
          setLoading(false);
          return;
        }
        const data = (await res.json()) as ApiRow[];
        if (cancelled) return;
        setEntries(
          data.map((row) => {
            const sig = sumCounter(row.counters, "OK");
            const err = sumCounter(row.counters, "ERROR");
            const to = sumCounter(row.counters, "TIMEOUT");
            return {
              id: row.id,
              signals: sig,
              errors: err,
              timeouts: to,
              // Parity status is reported only after the user explicitly
              // runs `tradej strategies parity <id>`. Until then it
              // stays UNKNOWN.
              parityStatus: "UNKNOWN" as const,
            };
          })
        );
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

  return (
    <div className="bg-[#0d1117] border border-[#21262d] rounded p-2 h-full overflow-y-auto">
      <div className="flex items-center justify-between mb-2">
        <span className="text-[10px] font-bold text-slate-200 uppercase">Strategy Catalog</span>
        <span className="text-[9px] text-slate-500">
          {loading ? "loading…" : error ? error : `${entries.length} loaded`}
        </span>
      </div>
      {entries.length === 0 && !loading && !error ? (
        <div className="text-[10px] text-slate-500">
          No strategies have published metrics yet. Start the engine and
          replay a window, or run <code>tradej strategies parity</code>.
        </div>
      ) : (
        <table className="w-full text-[10px]">
          <thead>
            <tr className="text-slate-500 uppercase text-[8px]">
              <th className="text-left py-1">Plugin</th>
              <th className="text-right">Signals</th>
              <th className="text-right">Errors</th>
              <th className="text-right">Timeouts</th>
              <th className="text-right">Parity</th>
            </tr>
          </thead>
          <tbody>
            {entries.map((e) => (
              <tr key={e.id} className="border-t border-[#21262d]">
                <td className="py-1 text-slate-200 font-mono">{e.id}</td>
                <td className="text-right text-[#26a69a]">{e.signals}</td>
                <td className="text-right text-[#ef5350]">{e.errors}</td>
                <td className="text-right text-[#f0b429]">{e.timeouts}</td>
                <td className="text-right">
                  <span
                    className={`text-[8px] font-bold ${
                      e.parityStatus === "OK"
                        ? "text-[#26a69a]"
                        : e.parityStatus === "STALE"
                        ? "text-[#ef5350]"
                        : "text-slate-500"
                    }`}
                  >
                    {e.parityStatus}
                  </span>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
};

function sumCounter(counters: Record<string, number>, outcome: string): number {
  let sum = 0;
  for (const [k, v] of Object.entries(counters)) {
    const parts = k.split("|");
    if (parts.length >= 3 && parts[2] === outcome) sum += v;
  }
  return sum;
}

export default StrategyCatalogPage;
