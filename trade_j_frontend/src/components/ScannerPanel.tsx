import React, { useMemo } from "react";
import { useScanner } from "../store/ordersStore";
import type { ScanResult, ScanHit } from "../store/types";

const MAX_HISTORY = 50;

function timeAgo(ts: number): string {
  const secs = Math.floor((Date.now() - ts) / 1000);
  if (secs < 60) return `${secs}s ago`;
  const mins = Math.floor(secs / 60);
  if (mins < 60) return `${mins}m ago`;
  const hours = Math.floor(mins / 60);
  if (hours < 24) return `${hours}h ago`;
  return `${Math.floor(hours / 24)}d ago`;
}

function scoreColor(score: number): string {
  if (score >= 80) return "text-emerald-400";
  if (score >= 50) return "text-amber-400";
  return "text-slate-500";
}

function scoreBg(score: number): string {
  if (score >= 80) return "bg-emerald-500/20";
  if (score >= 50) return "bg-amber-500/20";
  return "bg-slate-500/10";
}

interface RankedHit {
  rank: number;
  symbol: string;
  score: number;
  reasons: string;
}

export default function ScannerPanel() {
  const { latest, history } = useScanner((s) => s);

  const ranked: RankedHit[] = useMemo(() => {
    if (!latest || !latest.hits) return [];
    return [...latest.hits]
      .sort((a, b) => b.score - a.score)
      .map((h, i) => ({
        rank: i + 1,
        symbol: h.symbol,
        score: h.score,
        reasons: (h.reasons ?? []).join(", "),
      }));
  }, [latest]);

  const recentRuns = useMemo(() => history.slice(0, 10), [history]);

  return (
    <div className="flex flex-col h-full bg-[#0d1117] text-[#c9d1d9] font-mono text-[10px]">
      {/* ── Header ── */}
      <div className="flex items-center justify-between px-2 py-1 border-b border-[#21262d]">
        <span className="font-bold text-slate-300 text-[9px] uppercase tracking-wider">Scanner Results</span>
        <span className="text-[8px] text-slate-500">
          {latest ? `${latest.hitCount} hits · ${timeAgo(latest.finishedAtMs)}` : "waiting…"}
        </span>
      </div>

      {/* ── Latest run summary ── */}
      {latest && (
        <div className="px-2 py-1 border-b border-[#21262d] grid grid-cols-4 gap-2 text-[9px]">
          <div>
            <div className="text-slate-500 uppercase">Profile</div>
            <div className="font-bold text-slate-200 truncate">{latest.profileId}</div>
          </div>
          <div>
            <div className="text-slate-500 uppercase">Hits</div>
            <div className="font-bold text-[#f0b429]">{latest.hitCount}</div>
          </div>
          <div>
            <div className="text-slate-500 uppercase">Duration</div>
            <div className="font-bold text-slate-200">
              {latest.finishedAtMs > 0 ? `${latest.finishedAtMs - latest.startedAtMs}ms` : "—"}
            </div>
          </div>
          <div>
            <div className="text-slate-500 uppercase">Run</div>
            <div className="font-bold text-slate-200 text-[8px] truncate">{latest.runId.slice(-8)}</div>
          </div>
        </div>
      )}

      {/* ── Empty state ── */}
      {ranked.length === 0 && (
        <div className="flex-1 flex items-center justify-center text-slate-600 text-[10px] p-4 text-center">
          No scanner results yet.<br />
          <span className="text-slate-500 text-[9px]">Results stream in via SSE when scans complete.</span>
        </div>
      )}

      {/* ── Ranked hits table ── */}
      {ranked.length > 0 && (
        <div className="flex-1 overflow-y-auto">
          <div className="sticky top-0 bg-[#0d1117] grid grid-cols-12 gap-1 px-2 py-0.5 text-[8px] text-slate-500 font-bold uppercase border-b border-[#21262d]">
            <span className="col-span-1 text-right">#</span>
            <span className="col-span-3">Symbol</span>
            <span className="col-span-2 text-right">Score</span>
            <span className="col-span-6">Criterion</span>
          </div>
          {ranked.map((h) => (
            <div
              key={h.symbol}
              className="grid grid-cols-12 gap-1 px-2 py-0.5 text-[9px] border-b border-[#21262d]/30 hover:bg-[#161b22]"
            >
              <span className="col-span-1 text-right text-slate-500">{h.rank}</span>
              <span className="col-span-3 font-bold text-slate-200 truncate">{h.symbol}</span>
              <span className={`col-span-2 text-right font-bold ${scoreColor(h.score)}`}>
                {h.score.toFixed(0)}
              </span>
              <span className="col-span-6 text-slate-400 truncate">{h.reasons || "—"}</span>
            </div>
          ))}

          {/* ── Score distribution bar ── */}
          <div className="px-2 py-1 border-t border-[#21262d]/50 flex items-center gap-1">
            {ranked.slice(0, 20).map((h) => (
              <div
                key={h.symbol}
                className={`h-3 rounded-sm ${scoreBg(h.score)}`}
                style={{ width: `${Math.max(2, h.score / 5)}px` }}
                title={`${h.symbol}: ${h.score.toFixed(0)}`}
              />
            ))}
            <span className="text-[8px] text-slate-600 ml-1">score dist</span>
          </div>
        </div>
      )}

      {/* ── Recent runs ── */}
      {recentRuns.length > 1 && (
        <div className="border-t border-[#21262d] px-2 py-1 max-h-[80px] overflow-y-auto">
          <div className="text-[9px] text-slate-500 uppercase font-bold mb-0.5">Recent Runs</div>
          <div className="space-y-0.5">
            {recentRuns.map((r) => (
              <div key={r.runId} className="grid grid-cols-3 gap-1 text-[8px]">
                <span className="text-slate-300 truncate">{r.profileId}</span>
                <span className="text-right text-slate-400">{r.hitCount} hits</span>
                <span className="text-right text-slate-500">
                  {r.finishedAtMs > 0 ? timeAgo(r.finishedAtMs) : "running"}
                </span>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}
