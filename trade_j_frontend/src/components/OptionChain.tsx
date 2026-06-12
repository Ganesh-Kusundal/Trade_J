import React from "react";
import type { OptionStrike, OptionQuote } from "../api/options";

interface OptionChainProps {
  strikes: OptionStrike[];
  spotPrice: number;
  expiry: string;
  loading: boolean;
  error: string;
  underlying: string;
}

function formatNumber(n: number | undefined, decimals = 2): string {
  if (n === undefined) return "\u2014";
  return n.toLocaleString("en-IN", { minimumFractionDigits: decimals, maximumFractionDigits: decimals });
}

function formatOI(n: number | undefined): string {
  if (n === undefined) return "\u2014";
  if (n >= 1_000_000) return `${(n / 1_000_000).toFixed(1)}M`;
  if (n >= 1_000) return `${(n / 1_000).toFixed(1)}K`;
  return n.toString();
}

export default function OptionChain({ strikes, spotPrice, expiry, loading, error, underlying }: OptionChainProps) {
  if (loading) {
    return (
      <div className="bg-[#0d1117] border border-[#21262d] rounded-lg flex items-center justify-center h-64">
        <div className="text-center">
          <div className="w-8 h-8 rounded-full border-2 border-[#f0b429] border-t-transparent animate-spin mx-auto mb-2" />
          <div className="text-[10px] text-slate-500">Loading option chain...</div>
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="bg-[#0d1117] border border-[#21262d] rounded-lg flex items-center justify-center h-64">
        <div className="text-center text-[#ef5350] text-[10px]">
          <div className="font-bold mb-1">Failed to Load</div>
          <div>{error}</div>
        </div>
      </div>
    );
  }

  const atmStrike = strikes.reduce((prev, curr) =>
    Math.abs(curr.strikePrice - spotPrice) < Math.abs(prev.strikePrice - spotPrice) ? curr : prev
  , strikes[0]);

  return (
    <div className="bg-[#0d1117] border border-[#21262d] rounded-lg flex flex-col font-mono text-[10px]">
      {/* Header */}
      <div className="flex items-center justify-between px-3 py-1.5 border-b border-[#21262d]">
        <div className="flex items-center gap-2">
          <span className="font-black text-[11px] text-[#f0b429]">{underlying}</span>
          <span className="text-[9px] text-slate-500">Spot: {formatNumber(spotPrice)}</span>
          <span className="text-[9px] text-slate-500">Expiry: {expiry}</span>
        </div>
        <span className="text-[9px] text-slate-500">{strikes.length} strikes</span>
      </div>

      {/* Column Headers */}
      <div className="grid grid-cols-12 gap-1 px-2 py-1 bg-[#161b22] text-[9px] text-slate-500 font-bold border-b border-[#21262d]">
        <div className="col-span-1 text-center">Strike</div>
        <div className="col-span-1 text-center">OI</div>
        <div className="col-span-1 text-center">Chg OI</div>
        <div className="col-span-1 text-center">Vol</div>
        <div className="col-span-1 text-center">IV</div>
        <div className="col-span-1 text-center">LTP</div>
        <div className="col-span-1"></div>
        <div className="col-span-1 text-center">LTP</div>
        <div className="col-span-1 text-center">IV</div>
        <div className="col-span-1 text-center">Vol</div>
        <div className="col-span-1 text-center">Chg OI</div>
        <div className="col-span-1 text-center">OI</div>
      </div>

      {/* Strikes */}
      <div className="flex-1 overflow-y-auto max-h-[400px]">
        {strikes.map((strike, idx) => {
          const isATM = strike.strikePricePaisa === atmStrike?.strikePricePaisa;
          const isITMCall = strike.strikePrice < spotPrice;
          const isITMPut = strike.strikePrice > spotPrice;

          return (
            <div
              key={idx}
              className={`grid grid-cols-12 gap-1 px-2 py-1 border-b border-[#21262d]/30 ${
                isATM ? "bg-[#f0b429]/10" : ""
              }`}
            >
              {/* Call Side */}
              <div className={`col-span-1 text-center font-bold ${isITMCall ? "text-[#26a69a]" : "text-slate-400"}`}>
                {formatNumber(strike.strikePrice, 0)}
              </div>
              <OptionData quote={strike.call} />

              {/* Strike Marker */}
              <div className="col-span-1 flex items-center justify-center">
                {isATM && <div className="w-1.5 h-1.5 rounded-full bg-[#f0b429]" />}
              </div>

              {/* Put Side */}
              <OptionData quote={strike.put} mirror />
            </div>
          );
        })}
      </div>
    </div>
  );
}

function OptionData({ quote, mirror = false }: { quote: OptionQuote; mirror?: boolean }) {
  if (!quote.available) {
    return (
      <>
        <div className="col-span-1 text-center text-slate-700">{"\u2014"}</div>
        <div className="col-span-1 text-center text-slate-700">{"\u2014"}</div>
        <div className="col-span-1 text-center text-slate-700">{"\u2014"}</div>
        <div className="col-span-1 text-center text-slate-700">{"\u2014"}</div>
        <div className="col-span-1 text-center text-slate-700">{"\u2014"}</div>
        <div className="col-span-1 text-center text-slate-700">{"\u2014"}</div>
      </>
    );
  }

  const oiColor = (quote.changeOi ?? 0) >= 0 ? "text-[#26a69a]" : "text-[#ef5350]";

  if (mirror) {
    return (
      <>
        <div className="col-span-1 text-center text-slate-300">{formatNumber(quote.ltp)}</div>
        <div className="col-span-1 text-center text-slate-400">{quote.iv ? `${quote.iv.toFixed(1)}%` : "\u2014"}</div>
        <div className="col-span-1 text-center text-slate-400">{formatOI(quote.volume)}</div>
        <div className={`col-span-1 text-center ${oiColor}`}>{formatOI(quote.changeOi)}</div>
        <div className="col-span-1 text-center text-slate-400">{formatOI(quote.oi)}</div>
      </>
    );
  }

  return (
    <>
      <div className="col-span-1 text-center text-slate-400">{formatOI(quote.oi)}</div>
      <div className={`col-span-1 text-center ${oiColor}`}>{formatOI(quote.changeOi)}</div>
      <div className="col-span-1 text-center text-slate-400">{formatOI(quote.volume)}</div>
      <div className="col-span-1 text-center text-slate-400">{quote.iv ? `${quote.iv.toFixed(1)}%` : "\u2014"}</div>
      <div className="col-span-1 text-center text-slate-300">{formatNumber(quote.ltp)}</div>
    </>
  );
}
