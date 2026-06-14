import React, { useState, useEffect } from "react";
import { featureApi } from "../generated/api";
import type { FeatureView } from "../generated/models";

interface FeatureFlagsProps {
  // empty for now — self-contained
}

export default function FeatureFlags(_props: FeatureFlagsProps) {
  const [features, setFeatures] = useState<FeatureView[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    featureApi
      .list()
      .then((data) => {
        setFeatures(data);
        setLoading(false);
      })
      .catch((err) => {
        setError(err instanceof Error ? err.message : String(err));
        setLoading(false);
      });
  }, []);

  const toggle = (name: string) => {
    setFeatures((prev) =>
      prev.map((f) => (f.name === name ? { ...f, enabled: !f.enabled } : f)),
    );
  };

  if (loading) {
    return (
      <div className="bg-[#0d1117] border border-[#21262d] rounded p-3 font-mono text-[10px] text-slate-400">
        Loading feature flags…
      </div>
    );
  }

  if (error) {
    return (
      <div className="bg-[#0d1117] border border-[#21262d] rounded p-3 font-mono text-[10px] text-red-400">
        Failed to load features: {error}
      </div>
    );
  }

  return (
    <div className="bg-[#0d1117] border border-[#21262d] rounded p-3 font-mono text-[10px]">
      <div className="text-slate-300 font-bold text-[11px] mb-2">Feature Flags</div>
      {features.length === 0 && (
        <div className="text-slate-500">No feature flags configured.</div>
      )}
      <ul className="space-y-1">
        {features.map((f) => (
          <li
            key={f.name}
            className="flex items-center justify-between py-1 px-2 rounded hover:bg-[#161b22]"
          >
            <span className="text-slate-300 truncate">{f.name}</span>
            <div className="flex items-center gap-2">
              <span
                className={`px-1.5 py-0.5 rounded text-[9px] font-bold ${
                  f.enabled
                    ? "bg-emerald-900/40 text-emerald-400"
                    : "bg-red-900/40 text-red-400"
                }`}
              >
                {f.enabled ? "ON" : "OFF"}
              </span>
              <button
                type="button"
                role="switch"
                aria-checked={f.enabled}
                onClick={() => toggle(f.name)}
                className={`relative inline-flex h-4 w-7 shrink-0 cursor-pointer rounded-full transition-colors ${
                  f.enabled ? "bg-emerald-600" : "bg-slate-600"
                }`}
              >
                <span
                  className={`pointer-events-none inline-block h-3 w-3 rounded-full bg-white shadow transform transition-transform mt-0.5 ${
                    f.enabled ? "translate-x-3.5" : "translate-x-0.5"
                  }`}
                />
              </button>
            </div>
          </li>
        ))}
      </ul>
    </div>
  );
}
