import React, { useState, useMemo } from "react";

interface RiskCalculatorProps {
  lastPrice: number;
  currency: string;
}

export default function RiskCalculator({ lastPrice, currency }: RiskCalculatorProps) {
  const [capital, setCapital] = useState("100000");
  const [riskPercent, setRiskPercent] = useState("2");
  const [stopLoss, setStopLoss] = useState("");
  const [marginPercent, setMarginPercent] = useState("20");

  const calculations = useMemo(() => {
    const cap = parseFloat(capital) || 0;
    const risk = parseFloat(riskPercent) || 0;
    const sl = parseFloat(stopLoss) || 0;
    const margin = parseFloat(marginPercent) || 0;
    const price = lastPrice || 0;

    const riskAmount = cap * (risk / 100);
    const slDistance = sl > 0 ? Math.abs(price - sl) : 0;
    const positionSize = slDistance > 0 ? Math.floor(riskAmount / slDistance) : 0;
    const positionValue = positionSize * price;
    const marginRequired = positionValue * (margin / 100);
    const maxQtyByCapital = price > 0 ? Math.floor(cap / (price * (margin / 100))) : 0;

    return {
      riskAmount,
      positionSize,
      positionValue,
      marginRequired,
      maxQtyByCapital,
      rewardRiskRatio: slDistance > 0 ? ((price * 0.02) / slDistance) : 0,
    };
  }, [capital, riskPercent, stopLoss, marginPercent, lastPrice]);

  const fmt = (n: number) => n.toLocaleString("en-IN", { minimumFractionDigits: 2, maximumFractionDigits: 2 });

  return (
    <div className="bg-[#0d1117] border border-[#21262d] rounded-lg flex flex-col h-full font-mono text-[10px]">
      <div className="flex items-center justify-between px-2 py-1 border-b border-[#21262d]">
        <span className="font-bold text-slate-300 text-[9px] uppercase tracking-wider">Risk Calculator</span>
        <span className="text-[8px] text-slate-500">{currency}</span>
      </div>
      <div className="flex-1 overflow-y-auto px-2 py-1.5 space-y-2">
        <div className="space-y-1">
          <div className="text-[8px] text-slate-500 font-bold uppercase">Inputs</div>
          <div className="grid grid-cols-2 gap-1">
            <div>
              <label className="text-[8px] text-slate-500">Capital</label>
              <input value={capital} onChange={e => setCapital(e.target.value)} type="number"
                className="w-full bg-[#161b22] border border-[#21262d] text-slate-100 text-[10px] px-1.5 py-0.5 rounded outline-none" />
            </div>
            <div>
              <label className="text-[8px] text-slate-500">Risk %</label>
              <input value={riskPercent} onChange={e => setRiskPercent(e.target.value)} type="number" step="0.5"
                className="w-full bg-[#161b22] border border-[#21262d] text-slate-100 text-[10px] px-1.5 py-0.5 rounded outline-none" />
            </div>
            <div>
              <label className="text-[8px] text-slate-500">Stop Loss</label>
              <input value={stopLoss} onChange={e => setStopLoss(e.target.value)} type="number" step="0.05"
                placeholder={lastPrice > 0 && isFinite(lastPrice) ? (lastPrice * 0.98).toFixed(2) : "Price"}
                className="w-full bg-[#161b22] border border-[#21262d] text-slate-100 text-[10px] px-1.5 py-0.5 rounded outline-none" />
            </div>
            <div>
              <label className="text-[8px] text-slate-500">Margin %</label>
              <input value={marginPercent} onChange={e => setMarginPercent(e.target.value)} type="number" step="5"
                className="w-full bg-[#161b22] border border-[#21262d] text-slate-100 text-[10px] px-1.5 py-0.5 rounded outline-none" />
            </div>
          </div>
        </div>
        <div className="border-t border-[#21262d] pt-1.5 space-y-1">
          <div className="text-[8px] text-slate-500 font-bold uppercase">Results</div>
          <div className="grid grid-cols-2 gap-1">
            <div className="bg-[#161b22] rounded px-1.5 py-1">
              <div className="text-[8px] text-slate-500">Risk Amount</div>
              <div className="font-bold text-[#ef5350]">{fmt(calculations.riskAmount)}</div>
            </div>
            <div className="bg-[#161b22] rounded px-1.5 py-1">
              <div className="text-[8px] text-slate-500">Position Size</div>
              <div className="font-bold text-[#26a69a]">{calculations.positionSize} qty</div>
            </div>
            <div className="bg-[#161b22] rounded px-1.5 py-1">
              <div className="text-[8px] text-slate-500">Position Value</div>
              <div className="font-bold text-slate-200">{fmt(calculations.positionValue)}</div>
            </div>
            <div className="bg-[#161b22] rounded px-1.5 py-1">
              <div className="text-[8px] text-slate-500">Margin Required</div>
              <div className="font-bold text-[#f0b429]">{fmt(calculations.marginRequired)}</div>
            </div>
            <div className="bg-[#161b22] rounded px-1.5 py-1">
              <div className="text-[8px] text-slate-500">Max Qty (Capital)</div>
              <div className="font-bold text-slate-200">{calculations.maxQtyByCapital}</div>
            </div>
            <div className="bg-[#161b22] rounded px-1.5 py-1">
              <div className="text-[8px] text-slate-500">R:R Ratio (2% target)</div>
              <div className="font-bold text-slate-200">{isFinite(calculations.rewardRiskRatio) ? calculations.rewardRiskRatio.toFixed(2) : "0.00"}:1</div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
