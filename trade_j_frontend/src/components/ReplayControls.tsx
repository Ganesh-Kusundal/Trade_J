import React, { useState } from "react";
import { Play, Pause, SkipBack, SkipForward, RotateCcw } from "lucide-react";

interface ReplayControlsProps {
  symbol: string;
  exchange: string;
  timeframe: string;
  onReplayStart: (from: number, to: number, speed: number) => void;
  onReplayPause: () => void;
  onReplayResume: () => void;
  onReplayStop: () => void;
  isReplaying: boolean;
  isPaused: boolean;
  progress: number; // 0-100
  currentTimestamp: number | null;
  totalEvents: number;
  replayedEvents: number;
}

const REPLAY_SPEEDS = [0.5, 1, 2, 5, 10];

export default function ReplayControls({
  symbol,
  exchange,
  timeframe,
  onReplayStart,
  onReplayPause,
  onReplayResume,
  onReplayStop,
  isReplaying,
  isPaused,
  progress,
  currentTimestamp,
  totalEvents,
  replayedEvents,
}: ReplayControlsProps) {
  const [fromDate, setFromDate] = useState(() => {
    const d = new Date();
    d.setDate(d.getDate() - 1);
    return d.toISOString().split("T")[0];
  });
  const [toDate, setToDate] = useState(() => new Date().toISOString().split("T")[0]);
  const [speed, setSpeed] = useState(1);

  const handleStart = () => {
    const from = new Date(fromDate).getTime();
    const to = new Date(toDate).getTime();
    if (from >= to) {
      alert("From date must be before to date");
      return;
    }
    onReplayStart(from, to, speed);
  };

  const formatTimestamp = (ts: number | null): string => {
    if (!ts) return "--:--:--";
    return new Date(ts).toLocaleTimeString("en-IN", { hour12: false });
  };

  const formatDate = (ts: number | null): string => {
    if (!ts) return "----";
    return new Date(ts).toLocaleDateString("en-IN", { day: "2-digit", month: "short" });
  };

  return (
    <div className="bg-[#0d1117] border border-[#21262d] rounded-lg flex flex-col font-mono text-[10px]">
      {/* Header */}
      <div className="flex items-center justify-between px-3 py-1.5 border-b border-[#21262d]">
        <div className="flex items-center gap-2">
          <span className="font-black text-[11px] text-[#f0b429]">⟳ REPLAY MODE</span>
          <span className="text-[9px] text-slate-500">
            {exchange}:{symbol} • {timeframe}
          </span>
        </div>
        <div className="flex items-center gap-1.5">
          <span
            className={`text-[9px] font-bold px-1.5 py-0.5 rounded ${
              isReplaying && !isPaused
                ? "bg-[#26a69a]/15 text-[#26a69a]"
                : isPaused
                ? "bg-[#f0b429]/15 text-[#f0b429]"
                : "bg-[#21262d] text-slate-500"
            }`}
          >
            {isReplaying && !isPaused ? "PLAYING" : isPaused ? "PAUSED" : "STOPPED"}
          </span>
        </div>
      </div>

      {/* Date Range Selector */}
      <div className="px-3 py-2 border-b border-[#21262d]">
        <div className="flex items-center gap-2">
          <div className="flex-1">
            <label className="text-[9px] text-slate-500 block mb-0.5">From</label>
            <input
              type="date"
              value={fromDate}
              onChange={(e) => setFromDate(e.target.value)}
              disabled={isReplaying}
              className="w-full bg-[#161b22] border border-[#21262d] rounded px-2 py-1 text-[10px] text-slate-300 disabled:opacity-50"
            />
          </div>
          <div className="flex-1">
            <label className="text-[9px] text-slate-500 block mb-0.5">To</label>
            <input
              type="date"
              value={toDate}
              onChange={(e) => setToDate(e.target.value)}
              disabled={isReplaying}
              className="w-full bg-[#161b22] border border-[#21262d] rounded px-2 py-1 text-[10px] text-slate-300 disabled:opacity-50"
            />
          </div>
          <div className="w-20">
            <label className="text-[9px] text-slate-500 block mb-0.5">Speed</label>
            <select
              value={speed}
              onChange={(e) => setSpeed(Number(e.target.value))}
              disabled={isReplaying}
              className="w-full bg-[#161b22] border border-[#21262d] rounded px-2 py-1 text-[10px] text-slate-300 disabled:opacity-50"
            >
              {REPLAY_SPEEDS.map((s) => (
                <option key={s} value={s}>
                  {s}x
                </option>
              ))}
            </select>
          </div>
        </div>
      </div>

      {/* Control Buttons */}
      <div className="px-3 py-2 border-b border-[#21262d]">
        <div className="flex items-center gap-1.5">
          <button
            onClick={handleStart}
            disabled={isReplaying && !isPaused}
            className="flex items-center gap-1 px-2 py-1 rounded bg-[#26a69a] text-[#0d1117] font-bold text-[10px] disabled:opacity-50 disabled:cursor-not-allowed hover:bg-[#26a69a]/90"
          >
            <RotateCcw size={12} />
            Start
          </button>
          <button
            onClick={isPaused ? onReplayResume : onReplayPause}
            disabled={!isReplaying}
            className="flex items-center gap-1 px-2 py-1 rounded bg-[#f0b429] text-[#0d1117] font-bold text-[10px] disabled:opacity-50 disabled:cursor-not-allowed hover:bg-[#f0b429]/90"
          >
            {isPaused ? <Play size={12} /> : <Pause size={12} />}
            {isPaused ? "Resume" : "Pause"}
          </button>
          <button
            onClick={onReplayStop}
            disabled={!isReplaying}
            className="flex items-center gap-1 px-2 py-1 rounded bg-[#ef5350] text-white font-bold text-[10px] disabled:opacity-50 disabled:cursor-not-allowed hover:bg-[#ef5350]/90"
          >
            <SkipBack size={12} />
            Stop
          </button>
        </div>
      </div>

      {/* Progress Bar */}
      <div className="px-3 py-2 border-b border-[#21262d]">
        <div className="flex items-center gap-2">
          <div className="flex-1 bg-[#161b22] rounded-full h-2 overflow-hidden">
            <div
              className="h-full bg-gradient-to-r from-[#f0b429] to-[#26a69a] transition-all duration-300"
              style={{ width: `${progress}%` }}
            />
          </div>
          <span className="text-[9px] font-bold text-slate-400 w-12 text-right">
            {progress.toFixed(0)}%
          </span>
        </div>
      </div>

      {/* Status Info */}
      <div className="px-3 py-1.5">
        <div className="flex items-center justify-between text-[9px]">
          <div className="flex items-center gap-3">
            <span className="text-slate-500">
              Time: <span className="text-slate-300 font-bold">{formatTimestamp(currentTimestamp)}</span>
            </span>
            <span className="text-slate-500">
              Date: <span className="text-slate-300 font-bold">{formatDate(currentTimestamp)}</span>
            </span>
          </div>
          <div className="flex items-center gap-3">
            <span className="text-slate-500">
              Events: <span className="text-slate-300 font-bold">{replayedEvents}/{totalEvents}</span>
            </span>
          </div>
        </div>
      </div>
    </div>
  );
}
