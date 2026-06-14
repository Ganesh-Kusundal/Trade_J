import React, { useState, useEffect } from "react";
import { discoveryApi } from "../generated/api";

interface StrategyInfo {
  name: string;
}

interface StrategyStudioProps {
  segment?: string;
}

export default function StrategyStudio({ segment }: StrategyStudioProps) {
  const [strategies, setStrategies] = useState<StrategyInfo[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [selected, setSelected] = useState<string | null>(null);

  useEffect(() => {
    const loadStrategies = async () => {
      try {
        setLoading(true);
        const discovery = await discoveryApi.all();
        const stratList = (discovery.strategies || []).map((s: any) => ({
          name: s.name,
        }));
        setStrategies(stratList);
        if (stratList.length > 0 && !selected) {
          setSelected(stratList[0].name);
        }
      } catch (err: any) {
        setError(err.message || "Failed to load strategies");
      } finally {
        setLoading(false);
      }
    };
    loadStrategies();
  }, []);

  if (loading) {
    return (
      <div className="bg-[#0d1117] border border-[#21262d] rounded-lg flex items-center justify-center h-64">
        <div className="w-8 h-8 rounded-full border-2 border-[#f0b429] border-t-transparent animate-spin" />
      </div>
    );
  }

  if (error) {
    return (
      <div className="bg-[#0d1117] border border-[#21262d] rounded-lg flex items-center justify-center h-64">
        <div className="text-[#ef5350] text-[10px]">{error}</div>
      </div>
    );
  }

  const selectedStrategy = strategies.find(s => s.name === selected);

  return (
    <div className="bg-[#0d1117] border border-[#21262d] rounded-lg flex flex-col font-mono text-[10px] h-full">
      {/* Header */}
      <div className="flex items-center justify-between px-3 py-1.5 border-b border-[#21262d]">
        <div className="flex items-center gap-2">
          <span className="font-black text-[11px] text-[#f0b429]">Strategy Studio</span>
          <span className="text-[9px] text-slate-500">{strategies.length} registered</span>
        </div>
      </div>

      <div className="flex flex-1 overflow-hidden">
        {/* Strategy List */}
        <div className="w-48 border-r border-[#21262d] overflow-y-auto">
          {strategies.map(strategy => (
            <button
              key={strategy.name}
              onClick={() => setSelected(strategy.name)}
              className={`w-full text-left px-3 py-1.5 text-[10px] border-b border-[#21262d]/30 cursor-pointer transition ${
                selected === strategy.name
                  ? "bg-[#f0b429]/10 text-[#f0b429] font-bold"
                  : "text-slate-400 hover:bg-[#161b22] hover:text-slate-200"
              }`}
            >
              {strategy.name}
            </button>
          ))}
          {strategies.length === 0 && (
            <div className="px-3 py-4 text-slate-600 text-center text-[10px]">
              No strategies registered.
              <br />
              <span className="text-[9px]">Use <code className="text-[#f0b429]">tradej scaffold strategy</code> to create one.</span>
            </div>
          )}
        </div>

        {/* Strategy Detail */}
        <div className="flex-1 p-3 overflow-y-auto">
          {selectedStrategy ? (
            <div className="space-y-3">
              <div>
                <h3 className="text-[12px] font-bold text-slate-200 mb-1">{selectedStrategy.name}</h3>
                <span className="text-[9px] bg-[#26a69a]/15 text-[#26a69a] px-1.5 py-0.5 rounded font-bold">
                  ACTIVE
                </span>
              </div>

              <div className="space-y-1">
                <div className="text-[9px] text-slate-500 font-bold uppercase">Configuration</div>
                <div className="bg-[#161b22] rounded p-2 space-y-1">
                  <div className="flex justify-between">
                    <span className="text-slate-400">Name</span>
                    <span className="text-slate-200 font-bold">{selectedStrategy.name}</span>
                  </div>
                  <div className="flex justify-between">
                    <span className="text-slate-400">Type</span>
                    <span className="text-slate-200 font-bold">GraphStrategyPlugin</span>
                  </div>
                  <div className="flex justify-between">
                    <span className="text-slate-400">SPI</span>
                    <span className="text-slate-300 font-mono text-[8px]">com.tradej.strategy.api.GraphStrategyPlugin</span>
                  </div>
                </div>
              </div>

              <div className="space-y-1">
                <div className="text-[9px] text-slate-500 font-bold uppercase">Event Subscriptions</div>
                <div className="bg-[#161b22] rounded p-2">
                  <span className="text-[9px] text-slate-400">
                    Defined in strategy source — check <code className="text-[#f0b429]">subscribedEventTypes()</code>
                  </span>
                </div>
              </div>

              <div className="space-y-1">
                <div className="text-[9px] text-slate-500 font-bold uppercase">Pipeline Integration</div>
                <div className="bg-[#161b22] rounded p-2">
                  <span className="text-[9px] text-slate-400">
                    Signals flow through SignalExecutionBridge → RiskCheckChain → OrderManagementService
                  </span>
                </div>
              </div>
            </div>
          ) : (
            <div className="flex items-center justify-center h-full text-slate-600 text-[10px]">
              Select a strategy to view details
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
