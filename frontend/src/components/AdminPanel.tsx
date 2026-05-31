import {useEffect, useState} from 'react';
import {adminApi, healthApi} from '@/api/client';
import {AlertTriangle, RefreshCw} from 'lucide-react';

export function AdminPanel() {
  const [runtime, setRuntime] = useState<any>(null);
  const [strategies, setStrategies] = useState<any[]>([]);
  const [summary, setSummary] = useState<Record<string, unknown> | null>(null);
  const [health, setHealth] = useState<Record<string, unknown> | null>(null);

  const loadAll = () => {
    adminApi.runtime().then(setRuntime).catch(() => {});
    adminApi.strategies().then(setStrategies).catch(() => {});
    adminApi.summary().then(setSummary).catch(() => {});
    healthApi.check().then(setHealth).catch(() => {});
  };

  useEffect(() => { loadAll(); }, []);

  const toggleKill = async () => {
    const res = await adminApi.killSwitch(summary ? !(summary as any).killSwitch : true);
    loadAll();
  };

  return (
    <div className="h-full flex flex-col text-[10px] font-mono">
      <div className="h-8 border-b border-[#1c1c1e] flex items-center justify-between px-3 shrink-0">
        <span className="text-[#71717a] font-bold tracking-wider uppercase text-[9px]">Admin</span>
        <button onClick={loadAll} aria-label="Refresh" className="h-5 w-5 flex items-center justify-center text-zinc-500 hover:text-zinc-300 cursor-pointer">
          <RefreshCw size={11} />
        </button>
      </div>

      <div className="p-3 space-y-3 flex-1 overflow-y-auto">
        {health && (
          <div className="bg-[#0e0e11] border border-[#1c1c1e] rounded-xs p-2">
            <div className="text-[#71717a] font-semibold tracking-wider text-[8px] mb-1 uppercase">System Health</div>
            <div className="grid grid-cols-2 gap-2">
              {Object.entries(health).map(([k, v]) => (
                <div key={k} className="flex justify-between">
                  <span className="text-zinc-500">{k}</span>
                  <span className={`font-bold ${String(v) === 'ok' || String(v) === 'true' || String(v) === 'UP' ? 'text-[#10b981]' : 'text-[#ef4444]'}`}>
                    {typeof v === 'boolean' ? (v ? 'OK' : 'FAIL') : String(v)}
                  </span>
                </div>
              ))}
            </div>
          </div>
        )}

        {runtime && (
          <div className="bg-[#0e0e11] border border-[#1c1c1e] rounded-xs p-2">
            <div className="text-[#71717a] font-semibold tracking-wider text-[8px] mb-1 uppercase">Runtime</div>
            <div className="grid grid-cols-2 gap-1">
              {Object.entries(runtime).map(([k, v]) => (
                <div key={k} className="flex justify-between">
                  <span className="text-zinc-500">{k}</span>
                  <span className="text-zinc-200 font-bold">{String(v)}</span>
                </div>
              ))}
            </div>
          </div>
        )}

        <div className="flex gap-2">
          <button
            onClick={toggleKill}
            className="flex items-center gap-1 h-7 px-3 bg-[#ef4444]/20 border border-[#ef4444]/40 text-[#ef4444] font-bold rounded-xs hover:bg-[#ef4444]/30 transition cursor-pointer text-[10px]"
          >
            <AlertTriangle size={11} />
            TOGGLE KILL SWITCH
          </button>
        </div>

        <div>
          <div className="text-[#71717a] font-semibold tracking-wider text-[8px] mb-1 uppercase">Strategies</div>
          {strategies.map((s, i) => (
            <div key={i} className="flex items-center justify-between bg-[#0e0e11] border border-[#1c1c1e] rounded-xs px-2 py-1.5 mb-1">
              <span className="text-zinc-300 font-bold">{(s as any).name || (s as any).id}</span>
              <span className={`text-[9px] px-1.5 ${(s as any).status === 'RUNNING' ? 'text-[#10b981] bg-[#10b981]/10 border border-[#10b981]/30' : 'text-zinc-500 bg-zinc-800/50 border border-zinc-700'}`}>
                {(s as any).status || 'UNKNOWN'}
              </span>
            </div>
          ))}
        </div>

        {summary && (
          <div className="bg-[#0e0e11] border border-[#1c1c1e] rounded-xs p-2">
            <div className="text-[#71717a] font-semibold tracking-wider text-[8px] mb-1 uppercase">Summary</div>
            <div className="grid grid-cols-2 gap-1">
              {Object.entries(summary).map(([k, v]) => (
                <div key={k} className="flex justify-between">
                  <span className="text-zinc-500">{k}</span>
                  <span className="text-zinc-200 font-bold">{typeof v === 'number' ? (v as number).toFixed(2) : String(v)}</span>
                </div>
              ))}
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
