import React, { useEffect, useState } from "react";
import { marketBus } from "../api/MarketDataBus";
import type { ReplayControlState } from "../api/marketContracts";

/**
 * PCR (Put-Call Ratio) timeseries widget. Subscribes to REPLAY_CONTROL
 * events filtered for OI_UPDATE state. Renders the last 60 samples
 * (one per poll interval) as a sparkline.
 */
const PcrTimeseries: React.FC = () => {
  const [history, setHistory] = useState<number[]>([]);

  useEffect(() => {
    return marketBus.subscribe((e) => {
      if (e.type === "REPLAY_CONTROL") {
        const pcr = (e.state as unknown as { pcr?: number })?.pcr;
        if (typeof pcr === "number") {
          setHistory((prev) => [...prev.slice(-59), pcr]);
        }
      }
    });
  }, []);

  return (
    <div className="bg-[#0d1117] border border-[#21262d] rounded p-2 h-full">
      <div className="flex items-center justify-between mb-1">
        <span className="text-[10px] font-bold text-slate-200 uppercase">PCR</span>
        <span className="text-[9px] text-slate-500">
          {history.length > 0 ? history[history.length - 1].toFixed(2) : "—"}
        </span>
      </div>
      <div className="h-[180px] flex items-end gap-[2px]">
        {history.map((v, i) => (
          <div
            key={i}
            className="flex-1 bg-[#f0b429]/60 rounded-t"
            style={{ height: `${Math.min(100, v * 50)}%` }}
            title={`PCR ${v.toFixed(2)}`}
          />
        ))}
      </div>
    </div>
  );
};

export default PcrTimeseries;

export const pcrTimeseriesWidget = {
  type: "pcr-timeseries" as const,
  component: async () => ({ default: PcrTimeseries }),
  defaultProps: {},
  displayName: "PCR Timeseries",
  category: "analysis" as const,
};
