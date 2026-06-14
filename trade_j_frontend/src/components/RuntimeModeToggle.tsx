import React, { useEffect, useState } from "react";
import { fetchRuntimeMode, setRuntimeMode } from "../api/runtimeMode";

interface RuntimeModeToggleProps {
  onToast?: (message: string, variant: "success" | "error") => void;
}

/**
 * 2-state segmented control for the LIVE/PAPER runtime mode. Reads the
 * current mode from {@code GET /api/v1/runtime/mode} on mount and mutates
 * it via {@code PUT /api/v1/runtime/mode} on click. Toasts on success or
 * failure.
 */
export function RuntimeModeToggle({ onToast }: RuntimeModeToggleProps) {
  const [mode, setMode] = useState<"LIVE" | "PAPER" | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    fetchRuntimeMode()
      .then((res) => {
        if (cancelled) return;
        if (res.mode === "LIVE" || res.mode === "PAPER") {
          setMode(res.mode);
        } else {
          // REPLAY / BACKTEST (dev-only) — display as PAPER in the toggle
          // since the user cannot reach it from the UI.
          setMode("PAPER");
        }
      })
      .catch((e) => {
        if (cancelled) return;
        setError(String(e));
        onToast?.(`Runtime mode unavailable: ${e}`, "error");
      });
    return () => {
      cancelled = true;
    };
  }, [onToast]);

  const handleSwitch = async (target: "LIVE" | "PAPER") => {
    if (busy || mode === target) return;
    const previous = mode;
    setMode(target);
    setBusy(true);
    setError(null);
    try {
      const res = await setRuntimeMode(target);
      setMode(res.mode === "LIVE" || res.mode === "PAPER" ? res.mode : target);
      onToast?.(
        target === "LIVE" ? "Switched to LIVE mode" : "Switched to PAPER mode",
        "success"
      );
    } catch (e: any) {
      // Revert on failure
      setMode(previous);
      const msg = e?.message ?? String(e);
      setError(msg);
      onToast?.(`Failed to switch to ${target}: ${msg}`, "error");
    } finally {
      setBusy(false);
    }
  };

  const baseBtn =
    "px-2 py-0.5 rounded text-[9px] font-black uppercase cursor-pointer transition border";
  const liveActive =
    "bg-[#ef5350]/20 text-[#ef5350] border-[#ef5350]/60";
  const liveInactive =
    "text-slate-400 hover:text-slate-200 hover:bg-[#21262d] border-transparent";
  const paperActive =
    "bg-[#3b82f6]/20 text-[#3b82f6] border-[#3b82f6]/60";
  const paperInactive =
    "text-slate-400 hover:text-slate-200 hover:bg-[#21262d] border-transparent";

  return (
    <div
      data-testid="runtime-mode-toggle"
      className="flex items-center bg-[#161b22] border border-[#21262d] rounded p-0.5 gap-0.5"
      title={error ? `Runtime mode error: ${error}` : "LIVE/PAPER mode"}
    >
      <span className="text-[9px] text-slate-500 font-bold px-1">MODE:</span>
      <button
        type="button"
        data-testid="runtime-mode-live"
        onClick={() => handleSwitch("LIVE")}
        disabled={busy}
        className={`${baseBtn} ${mode === "LIVE" ? liveActive : liveInactive}`}
      >
        LIVE
      </button>
      <button
        type="button"
        data-testid="runtime-mode-paper"
        onClick={() => handleSwitch("PAPER")}
        disabled={busy}
        className={`${baseBtn} ${mode === "PAPER" ? paperActive : paperInactive}`}
      >
        PAPER
      </button>
    </div>
  );
}

export default RuntimeModeToggle;
