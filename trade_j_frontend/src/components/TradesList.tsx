import React from "react";
import type { TradeTick, MarketState } from "../domain/instrument";

interface Props {
  trades: TradeTick[];
  priceUnit?: string;
  qtyUnit?: string;
  qtyDecimals?: number;
  marketState?: MarketState;
  dataMode?: string;
}

export default function TradesList({ trades, priceUnit = "INR", qtyUnit = "SHARES", qtyDecimals = 0, marketState, dataMode }: Props) {
  const isClosed = marketState === "CLOSED" || marketState === "HOLIDAY";

  return (
    <div className="flex flex-col h-full bg-[#0d1117] text-[#c9d1d9] font-mono text-[11px] p-2">
      <div className="flex items-center justify-between border-b border-[#21262d] pb-1 mb-1">
        <span className="font-bold text-[10px] uppercase tracking-wider flex items-center gap-1">
          <span className="w-1.5 h-1.5 rounded bg-[#26a69a]" /> Recent Trades
        </span>
        <span className={`text-[9px] font-bold px-1 py-0.5 rounded ${isClosed ? "text-[#ef5350] bg-[#ef5350]/10" : "text-[#26a69a] bg-[#26a69a]/10"}`}>
          {isClosed ? "CLOSED" : "LIVE"}
        </span>
      </div>
      {dataMode === "HISTORICAL" && trades.length > 0 && (
        <div className="px-2 py-0.5 text-[8px] text-slate-500 border-b border-[#21262d]/30">
          Last trade: {new Date(trades[0].time * 1000).toLocaleTimeString(undefined, { hour: "2-digit", minute: "2-digit", second: "2-digit", hour12: false })} — Session closed
        </div>
      )}
      <div className="grid grid-cols-3 text-[9px] text-slate-500 pb-0.5 border-b border-[#21262d]/50 font-bold uppercase">
        <div>Time</div><div className="text-center">Price ({priceUnit})</div><div className="text-right">Qty ({qtyUnit})</div>
      </div>
      <div className="flex-1 overflow-y-auto space-y-px mt-0.5">
        {trades.length === 0 ? (
          <div className="text-center text-slate-600 py-6 text-[10px]">
            {isClosed ? "Market closed — no trades" : "Waiting for trades..."}
          </div>
        ) : trades.map((trade, i) => {
          const t = new Date(trade.time * 1000).toLocaleTimeString(undefined, { hour: "2-digit", minute: "2-digit", second: "2-digit", hour12: false });
          const isBuy = trade.side === "BUY";
          return (
            <div key={`${trade.time}-${i}`} className="grid grid-cols-3 py-0.5 text-[10px] hover:bg-[#161b22]">
              <div className="text-slate-500">{t}</div>
              <div className={`text-center font-bold ${isBuy ? "text-[#26a69a]" : "text-[#ef5350]"}`}>
                {trade.price.toLocaleString("en-IN", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
              </div>
              <div className="text-right text-slate-200">{trade.quantity.toLocaleString(undefined, { maximumFractionDigits: qtyDecimals })}</div>
            </div>
          );
        })}
      </div>
    </div>
  );
}
