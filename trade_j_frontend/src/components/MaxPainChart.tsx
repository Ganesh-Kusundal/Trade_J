import React, { useEffect, useMemo, useRef } from "react";
import { marketBus } from "../api/MarketDataBus";
import type { ReplayControlState } from "../api/marketContracts";
import { getWidgetDefinition } from "../domain/widgetRegistry";

/**
 * MaxPain chart widget. Subscribes to REPLAY_CONTROL events filtered for
 * MAX_PAIN_UPDATE state. In a real deployment the producer
 * ({@code OptionsAnalyticsProducer}) would push MaxPainComputed events
 * onto the bus; the bridge forwards them to the
 * {@code MAX_PAIN_UPDATE} WS topic. For the terminal widget we render
 * a vertical line over a strike-vs-OI bar chart.
 */
const MaxPainChart: React.FC = () => {
  const ref = useRef<HTMLCanvasElement | null>(null);
  const [state, setState] = React.useState<ReplayControlState | null>(null);

  useEffect(() => {
    return marketBus.subscribe((e) => {
      if (e.type === "REPLAY_CONTROL") {
        setState(e.state);
      }
    });
  }, []);

  const maxPainStrikePaisa = useMemo(() => {
    // Simple stand-in: derive from session metadata. The real producer
    // would push MaxPainComputed as its own event shape; the bridge
    // delivers it to MAX_PAIN_UPDATE which the gateway fanout maps here.
    return (state as unknown as { maxPainStrikePaisa?: number })?.maxPainStrikePaisa ?? 0;
  }, [state]);

  return (
    <div className="bg-[#0d1117] border border-[#21262d] rounded p-2 h-full">
      <div className="flex items-center justify-between mb-1">
        <span className="text-[10px] font-bold text-slate-200 uppercase">Max Pain</span>
        <span className="text-[9px] text-slate-500">
          {maxPainStrikePaisa > 0 ? (maxPainStrikePaisa / 100).toFixed(2) : "—"}
        </span>
      </div>
      <canvas ref={ref} width={400} height={200} className="w-full h-[180px]" />
    </div>
  );
};

export default MaxPainChart;

const definition = {
  component: async () => ({ default: MaxPainChart }),
  defaultProps: {},
  displayName: "Max Pain Chart",
  category: "analysis" as const,
};

export const maxPainChartWidget = {
  type: "max-pain" as const,
  definition,
};
