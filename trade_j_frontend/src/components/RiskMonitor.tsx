import React from "react";
import { useRisk } from "../hooks/useRisk";
import { armKillSwitch } from "../api/killSwitch";

/**
 * Composite "Risk & PnL" widget. Replaces the old {@code RiskCalculator}
 * (a form-based calculator) with a live view. The kill switch button
 * posts to the same endpoint the CLI uses; the broker status row is
 * driven by the {@code BROKER_STATUS} event.
 */
export const RiskMonitor: React.FC<{ symbol: string }> = ({ symbol }) => {
  const snap = useRisk(symbol);

  return (
    <div className="bg-[#0d1117] border border-[#21262d] rounded p-2 h-full flex flex-col">
      <div className="flex items-center justify-between mb-2">
        <span className="text-[10px] font-bold text-slate-200 uppercase">Risk &amp; PnL</span>
        <span
          className={`text-[9px] font-bold ${
            snap.brokerConnected ? "text-[#26a69a]" : "text-[#ef5350]"
          }`}
        >
          {snap.brokerConnected ? "CONNECTED" : "DISCONNECTED"}
        </span>
      </div>
      <div className="grid grid-cols-2 gap-2 text-[10px] flex-1">
        <Stat label="Net Exposure" value={paisaToRupees(snap.netExposurePaisa)} />
        <Stat label="Daily PnL" value={paisaToRupees(snap.dailyPnlPaisa)} accent />
        <Stat label="Unrealized" value={paisaToRupees(snap.unrealizedPnlPaisa)} />
        <Stat label="Drawdown" value={paisaToRupees(snap.drawdownPaisa)} danger />
        <Stat label="Open Positions" value={String(snap.openPositions)} />
        <Stat label="Last Event" value={snap.lastEventMs > 0 ? new Date(snap.lastEventMs).toLocaleTimeString() : "—"} />
      </div>
      <button
        onClick={() => {
          if (confirm("STOP ALL TRADING? This will halt the broker OMS.")) {
            armKillSwitch().catch((e) => console.error("Kill switch failed", e));
          }
        }}
        className="mt-2 px-3 py-1.5 rounded font-black text-[10px] bg-[#ef5350] text-white hover:bg-[#ef5350]/90 cursor-pointer"
      >
        STOP ALL TRADING
      </button>
    </div>
  );
};

const Stat: React.FC<{ label: string; value: string; accent?: boolean; danger?: boolean }> = ({
  label,
  value,
  accent,
  danger,
}) => {
  const color = danger
    ? "text-[#ef5350]"
    : accent
    ? "text-[#26a69a]"
    : "text-slate-200";
  return (
    <div className="bg-[#161b22] border border-[#21262d] rounded p-1.5">
      <div className="text-[8px] uppercase text-slate-500">{label}</div>
      <div className={`text-[12px] font-bold ${color}`}>{value}</div>
    </div>
  );
};

function paisaToRupees(paisa: number): string {
  return (paisa / 100).toLocaleString("en-IN", {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  });
}

export default RiskMonitor;
