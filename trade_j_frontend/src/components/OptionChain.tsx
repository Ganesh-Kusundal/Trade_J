import React, { useEffect, useState } from "react";

interface OptionLeg {
  present?: boolean;
  ltpPaisa?: number;
  openInterest?: number;
  volume?: number;
  bestBidPricePaisa?: number;
  bestBidQuantity?: number;
  bestAskPricePaisa?: number;
  bestAskQuantity?: number;
  delta?: number | null;
  theta?: number | null;
  gamma?: number | null;
  vega?: number | null;
  iv?: number | null;
}

interface OptionChainStrike {
  strikePaisa: number;
  call: OptionLeg;
  put: OptionLeg;
}

interface OptionChainResponse {
  underlying?: string;
  segment?: string;
  expiry?: string;
  expiries?: string[];
  spotPricePaisa?: number;
  maxPainStrikePaisa?: number;
  putCallRatio?: number;
  totalCallOi?: number;
  totalPutOi?: number;
  strikeCount?: number;
  strikes?: OptionChainStrike[];
  error?: string;
}

interface OptionChainProps {
  underlying: string;
  segment?: string;
}

function fmtP(p: number | undefined | null): string {
  if (p == null) return "—";
  return (p / 100).toLocaleString("en-IN", { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

function fmtN(n: number | undefined | null): string {
  if (n == null) return "—";
  return n.toLocaleString("en-IN");
}

function fmtPct(p: number | undefined | null): string {
  if (p == null) return "—";
  return `${(p * 100).toFixed(2)}%`;
}

export default function OptionChain({ underlying, segment = "NSE_FNO" }: OptionChainProps) {
  const [data, setData] = useState<OptionChainResponse | null>(null);
  const [expiry, setExpiry] = useState<string | null>(null);
  const [status, setStatus] = useState<"loading" | "ready" | "empty" | "error">("loading");
  const [errorMsg, setErrorMsg] = useState<string>("");

  useEffect(() => {
    let cancelled = false;
    setStatus("loading");
    const url = new URL("/api/v1/options/chain", window.location.origin);
    url.searchParams.set("underlying", underlying);
    url.searchParams.set("segment", segment);
    if (expiry) url.searchParams.set("expiry", expiry);
    fetch(url.toString())
      .then((r) => r.ok ? r.json() : Promise.reject(new Error(`HTTP ${r.status}`)))
      .then((d: OptionChainResponse) => {
        if (cancelled) return;
        if (d.error) {
          setErrorMsg(d.error);
          setStatus("error");
          return;
        }
        setData(d);
        setStatus(d.strikes && d.strikes.length > 0 ? "ready" : "empty");
      })
      .catch((e) => { if (!cancelled) { setErrorMsg(e.message); setStatus("error"); } });
    return () => { cancelled = true; };
  }, [underlying, segment, expiry]);

  return (
    <div className="flex flex-col h-full bg-[#0d1117] text-[#c9d1d9] font-mono text-[10px]">
      <div className="flex items-center justify-between px-2 py-1 border-b border-[#21262d]">
        <span className="font-bold text-slate-300 text-[9px] uppercase tracking-wider">Option Chain · {underlying}</span>
        {data?.expiries && data.expiries.length > 1 && (
          <select value={expiry ?? data.expiry ?? ""} onChange={(e) => setExpiry(e.target.value)} className="bg-[#161b22] border border-[#21262d] text-slate-200 text-[9px] px-1 py-0.5 rounded">
            {data.expiries.map((e) => <option key={e} value={e}>{e}</option>)}
          </select>
        )}
      </div>

      {data && (
        <div className="px-2 py-1 border-b border-[#21262d] grid grid-cols-4 gap-2 text-[9px]">
          <div>
            <div className="text-slate-500 uppercase">Spot</div>
            <div className="font-bold text-slate-200">{fmtP(data.spotPricePaisa)}</div>
          </div>
          <div>
            <div className="text-slate-500 uppercase">Max Pain</div>
            <div className="font-bold text-slate-200">{fmtP(data.maxPainStrikePaisa)}</div>
          </div>
          <div>
            <div className="text-slate-500 uppercase">PCR</div>
            <div className="font-bold text-slate-200">{(data.putCallRatio ?? 0).toFixed(2)}</div>
          </div>
          <div>
            <div className="text-slate-500 uppercase">Strikes</div>
            <div className="font-bold text-slate-200">{data.strikeCount ?? (data.strikes?.length ?? 0)}</div>
          </div>
        </div>
      )}

      {status === "loading" && (
        <div className="flex-1 flex items-center justify-center text-slate-600 text-[10px]">Loading option chain…</div>
      )}
      {status === "error" && (
        <div className="flex-1 flex items-center justify-center text-amber-500 text-[10px] p-4 text-center">
          Option chain unavailable<br /><span className="text-slate-500 text-[9px]">{errorMsg}</span>
        </div>
      )}
      {status === "empty" && (
        <div className="flex-1 flex items-center justify-center text-slate-600 text-[10px] p-4 text-center">
          No strikes for {underlying} {expiry ?? data?.expiry ?? ""}<br />
          <span className="text-slate-500 text-[9px]">Connect a live broker or ingest historical options data.</span>
        </div>
      )}

      {status === "ready" && data?.strikes && (
        <div className="flex-1 overflow-y-auto">
          <div className="sticky top-0 bg-[#0d1117] grid grid-cols-9 gap-1 px-2 py-0.5 text-[8px] text-slate-500 font-bold uppercase border-b border-[#21262d]">
            <div className="text-right">OI</div>
            <div className="text-right">IV</div>
            <div className="text-right">Δ</div>
            <div className="text-right">LTP</div>
            <div className="text-center">Strike</div>
            <div className="text-right">LTP</div>
            <div className="text-right">Δ</div>
            <div className="text-right">IV</div>
            <div className="text-right">OI</div>
          </div>
          {data.strikes.map((s) => (
            <div key={s.strikePaisa} className="grid grid-cols-9 gap-1 px-2 py-0.5 text-[9px] border-b border-[#21262d]/30 hover:bg-[#161b22]">
              <div className="text-right text-slate-300">{fmtN(s.put?.openInterest)}</div>
              <div className="text-right text-slate-400">{fmtPct(s.put?.iv)}</div>
              <div className="text-right text-rose-400">{s.put?.delta != null ? s.put.delta.toFixed(3) : "—"}</div>
              <div className="text-right text-rose-400 font-bold">{fmtP(s.put?.ltpPaisa)}</div>
              <div className="text-center font-bold text-slate-200">{(s.strikePaisa / 100).toFixed(0)}</div>
              <div className="text-right text-emerald-400 font-bold">{fmtP(s.call?.ltpPaisa)}</div>
              <div className="text-right text-emerald-400">{s.call?.delta != null ? s.call.delta.toFixed(3) : "—"}</div>
              <div className="text-right text-slate-400">{fmtPct(s.call?.iv)}</div>
              <div className="text-right text-slate-300">{fmtN(s.call?.openInterest)}</div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
