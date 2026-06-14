import React, { useMemo } from "react";
import type { L2Level } from "../domain/instrument";
import type { MarketState } from "../domain/instrument";
import { ChevronUp, ChevronDown } from "lucide-react";

interface Props {
  bids: L2Level[];
  asks: L2Level[];
  lastPrice: number;
  priceChange: number;
  symbol: string;
  onSelectPrice?: (price: number) => void;
  priceUnit?: string;
  qtyUnit?: string;
  marketState?: MarketState;
  dataMode?: string;
}

export default function OrderBook({ bids, asks, lastPrice, priceChange, symbol, onSelectPrice, priceUnit = "INR", qtyUnit = "SHARES", marketState, dataMode }: Props) {
  const visibleAsks = useMemo(() => [...asks].slice(0, 14).reverse(), [asks]);
  const visibleBids = useMemo(() => [...bids].slice(0, 14), [bids]);

  const fmt = (p: number) => (typeof p === "number" && isFinite(p)) ? p.toLocaleString("en-IN", { minimumFractionDigits: 2, maximumFractionDigits: 2 }) : "\u2014";
  const isClosed = marketState === "CLOSED" || marketState === "HOLIDAY";

  const displayAsks = useMemo(() => visibleAsks, [visibleAsks]);
  const displayBids = useMemo(() => visibleBids, [visibleBids]);

  const displayMaxCum = useMemo(() => {
    const aMax = displayAsks.length > 0 ? displayAsks.reduce((s, a) => s + a.quantity, 0) : 1;
    const bMax = displayBids.length > 0 ? displayBids.reduce((s, b) => s + b.quantity, 0) : 1;
    return Math.max(aMax, bMax);
  }, [displayAsks, displayBids]);

  let bidCum = 0, askCum = 0;
  const isHistorical = dataMode === "HISTORICAL";

  return (
    <div className="flex flex-col h-full bg-[#0d1117] text-[#c9d1d9] font-mono text-[11px] p-2">
      <div className="flex items-center justify-between border-b border-[#21262d] pb-1 mb-1">
        <span className="font-bold text-[10px] uppercase tracking-wider flex items-center gap-1">
          <span className="w-1.5 h-1.5 rounded bg-[#f0b429]" /> Order Book
        </span>
        <span className="text-[9px] text-[#f0b429] font-bold bg-[#f0b429]/10 px-1 py-0.5 rounded">
          {isHistorical ? "\uD83D\uDCF8 SNAPSHOT" : "L2"}
        </span>
      </div>
      {isHistorical && (
        <div className="px-2 py-0.5 text-[8px] text-[#3b82f6] bg-[#3b82f6]/5 border-b border-[#21262d]/30">
          Frozen snapshot \u2014 market closed
        </div>
      )}
      <div className="grid grid-cols-3 text-[9px] text-slate-500 pb-0.5 border-b border-[#21262d]/50 font-bold uppercase">
        <div>Price ({priceUnit})</div><div className="text-right">Qty ({qtyUnit})</div><div className="text-right">Orders</div>
      </div>
      <div className="flex-1 flex flex-col justify-end overflow-hidden py-0.5 space-y-px min-h-[100px]">
        {displayAsks.length === 0 ? (
          <div className="text-center text-slate-600 py-3 text-[10px]">{isClosed ? "Market closed" : "No asks"}</div>
        ) : displayAsks.map((ask, i) => {
          askCum += ask.quantity;
          const w = `${Math.min((askCum / displayMaxCum) * 100, 100)}%`;
          return (
            <button key={`a-${i}`} onClick={() => onSelectPrice?.(ask.price)}
              className={`grid grid-cols-3 py-0.5 hover:bg-[#161b22] relative text-left w-full cursor-pointer ${isHistorical ? "opacity-60" : ""}`}>
              <div className="absolute right-0 top-0 bottom-0 bg-[#ef5350]/10 pointer-events-none" style={{ width: w }} />
              <div className="text-[#ef5350] font-bold z-10">{fmt(ask.price)}</div>
              <div className="text-right text-slate-200 z-10">{ask.quantity.toLocaleString()}</div>
              <div className="text-right text-slate-500 z-10 text-[10px]">{ask.orders}</div>
            </button>
          );
        })}
      </div>
      <div className="bg-[#161b22]/60 border-y border-[#21262d]/50 my-0.5 py-1 px-2 flex items-center justify-between">
        <span className={`font-black text-[13px] flex items-center ${priceChange >= 0 ? "text-[#26a69a]" : "text-[#ef5350]"}`}>
          {fmt(lastPrice)} {priceChange >= 0 ? <ChevronUp className="w-3 h-3" /> : <ChevronDown className="w-3 h-3" />}
        </span>
        <span className="text-[10px] text-slate-500">Spread: {displayAsks.length > 0 && displayBids.length > 0 ? fmt(displayAsks[displayAsks.length - 1].price - displayBids[0].price) : "\u2014"}</span>
      </div>
      <div className="flex-1 flex flex-col justify-start overflow-hidden py-0.5 space-y-px min-h-[100px]">
        {displayBids.length === 0 ? (
          <div className="text-center text-slate-600 py-3 text-[10px]">{isClosed ? "Market closed" : "No bids"}</div>
        ) : displayBids.map((bid, i) => {
          bidCum += bid.quantity;
          const w = `${Math.min((bidCum / displayMaxCum) * 100, 100)}%`;
          return (
            <button key={`b-${i}`} onClick={() => onSelectPrice?.(bid.price)}
              className={`grid grid-cols-3 py-0.5 hover:bg-[#161b22] relative text-left w-full cursor-pointer ${isHistorical ? "opacity-60" : ""}`}>
              <div className="absolute right-0 top-0 bottom-0 bg-[#26a69a]/10 pointer-events-none" style={{ width: w }} />
              <div className="text-[#26a69a] font-bold z-10">{fmt(bid.price)}</div>
              <div className="text-right text-slate-200 z-10">{bid.quantity.toLocaleString()}</div>
              <div className="text-right text-slate-500 z-10 text-[10px]">{bid.orders}</div>
            </button>
          );
        })}
      </div>
    </div>
  );
}
