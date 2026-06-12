import React, { useEffect, useState } from "react";
import { marketBus } from "../api/MarketDataBus";
import type { ReplayControlState } from "../api/marketContracts";

/**
 * Gamma exposure heatmap widget. Subscribes to REPLAY_CONTROL events
 * filtered for GAMMA_EXPOSURE_UPDATE state. Renders strike × expiry as
 * a coloured grid.
 */
const GammaHeatmap: React.FC = () => {
  const [grid, setGrid] = useState<{ strike: string; gamma: number }[]>([]);

  useEffect(() => {
    return marketBus.subscribe((e) => {
      if (e.type === "REPLAY_CONTROL") {
        const update = (e.state as unknown as { gammaGrid?: { strike: string; gamma: number }[] });
        if (update.gammaGrid) {
          setGrid(update.gammaGrid);
        }
      }
    });
  }, []);

  return (
    <div className="bg-[#0d1117] border border-[#21262d] rounded p-2 h-full">
      <div className="flex items-center justify-between mb-1">
        <span className="text-[10px] font-bold text-slate-200 uppercase">Gamma Exposure</span>
        <span className="text-[9px] text-slate-500">{grid.length} strikes</span>
      </div>
      <div className="grid grid-cols-8 gap-[1px]">
        {grid.map((cell, i) => {
          const intensity = Math.min(1, Math.abs(cell.gamma) / 100);
          const color = cell.gamma >= 0
            ? `rgba(38, 166, 154, ${intensity})`
            : `rgba(239, 83, 80, ${intensity})`;
          return (
            <div
              key={i}
              className="aspect-square text-[8px] flex items-center justify-center"
              style={{ background: color }}
              title={`${cell.strike} γ=${cell.gamma.toFixed(2)}`}
            >
              {cell.strike.replace(/\D/g, "").slice(-3)}
            </div>
          );
        })}
      </div>
    </div>
  );
};

export default GammaHeatmap;

export const gammaHeatmapWidget = {
  type: "gamma-heatmap" as const,
  component: async () => ({ default: GammaHeatmap }),
  defaultProps: {},
  displayName: "Gamma Heatmap",
  category: "analysis" as const,
};
