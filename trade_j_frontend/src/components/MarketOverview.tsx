import React, { useState, useEffect } from "react";

interface IndexData {
  name: string;
  value: number | null;
  change: number | null;
  exchange: string;
  isApprox: boolean;
}

const INDICES = [
  { name: "NIFTY 50", exchange: "NSE", segment: "IDX_I" },
  { name: "BANK NIFTY", exchange: "NSE", segment: "IDX_I" },
  { name: "INDIA VIX", exchange: "NSE", segment: "IDX_I" },
];

const INDEX_FALLBACKS: Record<string, number> = {
  "NIFTY 50": 24500,
  "BANK NIFTY": 52800,
  "INDIA VIX": 14.2,
};

const safeNum = (v: any, fb = 0): number => typeof v === "number" && isFinite(v) ? v : fb;

const VALID_RANGES: Record<string, { min: number; max: number }> = {
  'NIFTY 50': { min: 10000, max: 35000 },
  'BANK NIFTY': { min: 25000, max: 80000 },
  'INDIA VIX': { min: 5, max: 90 }
};

function sanitizeIndexValue(key: string, value: number | null | undefined): number | null {
  if (value == null || isNaN(value)) return null;
  const range = VALID_RANGES[key];
  if (!range) return Math.abs(value); // For unknown indices, just ensure positive
  const absValue = Math.abs(value);
  if (absValue < range.min || absValue > range.max) return null;
  return absValue;
}

function formatIndex(name: string, value: number | null, isApprox: boolean): string {
  if (value == null) return "\u2014";
  const formatted = value.toLocaleString("en-IN", { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  return isApprox ? `~${formatted}` : formatted;
}

export default function MarketOverview() {
  const [indices, setIndices] = useState<IndexData[]>(
    INDICES.map(i => ({
      ...i,
      value: sanitizeIndexValue(i.name, INDEX_FALLBACKS[i.name]),
      change: null,
      isApprox: true
    }))
  );

  useEffect(() => {
    const poll = async () => {
      const updated = await Promise.all(INDICES.map(async (idx) => {
        try {
          const res = await fetch(`/api/v1/market/ltp?symbol=${encodeURIComponent(idx.name)}&exchangeSegment=${idx.segment}`);
          if (!res.ok) return { ...idx, value: sanitizeIndexValue(idx.name, INDEX_FALLBACKS[idx.name]), change: null, isApprox: true };
          const data = await res.json();
          const rawValue = data.ltpPaisa / 100;
          const sanitized = sanitizeIndexValue(idx.name, rawValue);
          const value = sanitized != null ? sanitized : sanitizeIndexValue(idx.name, INDEX_FALLBACKS[idx.name]);
          return { ...idx, value, change: null, isApprox: sanitized == null };
        } catch {
          return { ...idx, value: sanitizeIndexValue(idx.name, INDEX_FALLBACKS[idx.name]), change: null, isApprox: true };
        }
      }));
      setIndices(updated);
    };
    poll();
    const iv = setInterval(poll, 10000);
    return () => clearInterval(iv);
  }, []);

  return (
    <div className="bg-[#0d1117] border-t border-[#21262d] px-3 py-0.5 flex items-center gap-4 text-[9px] font-mono shrink-0">
      {indices.map(idx => (
        <div key={idx.name} className="flex items-center gap-1.5">
          <span className="text-slate-500 font-bold">{idx.name}</span>
          <span className="font-bold text-slate-300">
            {formatIndex(idx.name, idx.value, idx.isApprox)}
          </span>
          {idx.change != null && idx.value != null && (
            <span className={`font-bold ${idx.change >= 0 ? "text-[#26a69a]" : "text-[#ef5350]"}`}>
              {idx.change >= 0 ? "+" : ""}{safeNum(idx.change).toFixed(2)}%
            </span>
          )}
        </div>
      ))}
      <div className="flex-1" />
      <span className="text-slate-600">{new Date().toLocaleTimeString("en-IN", { hour12: false })} IST</span>
    </div>
  );
}
