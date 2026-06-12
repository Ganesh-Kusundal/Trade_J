import React from "react";
import type { ScanHit } from "../api/scanner";

interface ScannerResultsProps {
  hits: ScanHit[];
  loading: boolean;
  error: string;
  profile: string;
  onProfileChange: (profile: string) => void;
  onRunScan: () => void;
  lastRun: Date | null;
}

export default function ScannerResults({ hits, loading, error, profile, onProfileChange, onRunScan, lastRun }: ScannerResultsProps) {
  if (loading) {
    return (
      <div className="bg-[#0d1117] border border-[#21262d] rounded-lg flex items-center justify-center h-64">
        <div className="text-center">
          <div className="w-8 h-8 rounded-full border-2 border-[#f0b429] border-t-transparent animate-spin mx-auto mb-2" />
          <div className="text-[10px] text-slate-500">Running scanner...</div>
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="bg-[#0d1117] border border-[#21262d] rounded-lg flex items-center justify-center h-64">
        <div className="text-center">
          <div className="text-[#ef5350] text-[10px] font-bold mb-1">Scan Failed</div>
          <div className="text-[#ef5350] text-[10px]">{error}</div>
        </div>
      </div>
    );
  }

  if (hits.length === 0) {
    return (
      <div className="bg-[#0d1117] border border-[#21262d] rounded-lg flex flex-col h-full">
        <div className="flex items-center justify-between px-3 py-1.5 border-b border-[#21262d]">
          <span className="font-black text-[11px] text-[#f0b429]">🔍 Scanner</span>
          <div className="flex items-center gap-2">
            <input
              type="text"
              value={profile}
              onChange={(e) => onProfileChange(e.target.value)}
              className="bg-[#161b22] border border-[#21262d] text-slate-300 text-[9px] px-2 py-0.5 rounded w-20"
              placeholder="profile"
            />
            <button
              onClick={onRunScan}
              className="px-3 py-0.5 rounded font-bold text-[9px] bg-[#f0b429] text-[#0d1117] hover:bg-[#f0b429]/90"
            >
              RUN SCAN
            </button>
          </div>
        </div>
        <div className="flex-1 flex items-center justify-center text-[10px] text-slate-500">
          <div className="text-center">
            <div className="mb-1">No scan results yet</div>
            <div className="text-[9px]">Click "RUN SCAN" to find opportunities</div>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="bg-[#0d1117] border border-[#21262d] rounded-lg flex flex-col font-mono text-[10px]">
      {/* Header */}
      <div className="flex items-center justify-between px-3 py-1.5 border-b border-[#21262d]">
        <div className="flex items-center gap-2">
          <span className="font-black text-[11px] text-[#f0b429]">🔍 Scanner Results</span>
          <span className="text-[9px] text-slate-500">{hits.length} hits</span>
          {lastRun && (
            <span className="text-[9px] text-slate-500">
              {lastRun.toLocaleTimeString("en-IN", { hour: "2-digit", minute: "2-digit" })}
            </span>
          )}
        </div>
        <div className="flex items-center gap-2">
          <input
            type="text"
            value={profile}
            onChange={(e) => onProfileChange(e.target.value)}
            className="bg-[#161b22] border border-[#21262d] text-slate-300 text-[9px] px-2 py-0.5 rounded w-20"
            placeholder="profile"
          />
          <button
            onClick={onRunScan}
            className="px-3 py-0.5 rounded font-bold text-[9px] bg-[#f0b429] text-[#0d1117] hover:bg-[#f0b429]/90"
          >
            REFRESH
          </button>
        </div>
      </div>

      {/* Column Headers */}
      <div className="grid grid-cols-12 gap-1 px-2 py-1 bg-[#161b22] text-[9px] text-slate-500 font-bold border-b border-[#21262d]">
        <div className="col-span-3">Symbol</div>
        <div className="col-span-2">Asset Class</div>
        <div className="col-span-2 text-center">Score</div>
        <div className="col-span-5">Reasons</div>
      </div>

      {/* Hits */}
      <div className="flex-1 overflow-y-auto max-h-[400px]">
        {hits.map((hit, idx) => (
          <div
            key={idx}
            className={`grid grid-cols-12 gap-1 px-2 py-1.5 border-b border-[#21262d]/30 ${
              hit.promoted ? "bg-[#f0b429]/10" : ""
            }`}
          >
            <div className="col-span-3 font-bold text-slate-300 truncate">
              {hit.symbol}
              {hit.promoted && <span className="ml-1 text-[#f0b429] text-[8px]">⭐</span>}
            </div>
            <div className="col-span-2 text-[9px] text-slate-400 truncate">{hit.assetClass}</div>
            <div className="col-span-2 text-center">
              <span className={`font-bold text-[10px] ${
                hit.score >= 80 ? "text-[#26a69a]" : hit.score >= 60 ? "text-[#f0b429]" : "text-slate-400"
              }`}>
                {hit.score.toFixed(1)}
              </span>
            </div>
            <div className="col-span-5 text-[9px] text-slate-400 truncate">
              {hit.reasons.slice(0, 3).join(", ")}
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
