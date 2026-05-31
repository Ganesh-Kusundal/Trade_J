import {useEffect} from 'react';
import {useStudioStore} from '@/store/useStudioStore';
import {BarChart3, Scan, GitBranch, Settings} from 'lucide-react';

const NAV_ITEMS = [
  {view: 'chart' as const, label: 'CHART', icon: BarChart3},
  {view: 'scanner' as const, label: 'SCANNER', icon: Scan},
  {view: 'pipeline' as const, label: 'PIPELINE', icon: GitBranch},
  {view: 'admin' as const, label: 'ADMIN', icon: Settings},
] as const;

export function LeftSidebar() {
  const activeView = useStudioStore((s) => s.activeView);
  const setActiveView = useStudioStore((s) => s.setActiveView);
  const sidebarOpen = useStudioStore((s) => s.sidebarOpen);
  const startupCandidates = useStudioStore((s) => s.startupCandidates);
  const startupScanDate = useStudioStore((s) => s.startupScanDate);
  const startupScanTime = useStudioStore((s) => s.startupScanTime);
  const startupRequestedScanTime = useStudioStore((s) => s.startupRequestedScanTime);
  const startupSelectionMode = useStudioStore((s) => s.startupSelectionMode);
  const selectedSymbol = useStudioStore((s) => s.selectedSymbol);
  const selectSymbol = useStudioStore((s) => s.selectSymbol);
  const wsConnected = useStudioStore((s) => s.wsConnected);
  const indicatorParams = useStudioStore((s) => s.indicatorParams);
  const setIndicatorParams = useStudioStore((s) => s.setIndicatorParams);
  const bootstrapStartupCandidates = useStudioStore((s) => s.bootstrapStartupCandidates);

  useEffect(() => {
    bootstrapStartupCandidates().catch(console.error);
  }, [bootstrapStartupCandidates]);

  if (!sidebarOpen) return null;

  return (
    <div className="w-56 bg-[#09090b] border-r border-[#1c1c1e] flex flex-col shrink-0 text-[10px] font-mono">
      <div className="flex border-b border-[#1c1c1e]">
        {NAV_ITEMS.map(({view, label, icon: Icon}) => (
          <button
            key={view}
            onClick={() => setActiveView(view)}
            className={`flex-1 h-8 flex items-center justify-center gap-1 font-bold text-[9px] transition cursor-pointer ${
              activeView === view ? 'bg-[#0e0e11] text-[#00d2ff] border-b-2 border-[#00d2ff]' : 'text-zinc-500 hover:text-zinc-300'
            }`}
          >
            <Icon size={11} />
            {label}
          </button>
        ))}
      </div>

      <div className="px-3 py-2 border-b border-[#1c1c1e] text-[8px] text-zinc-500">
        <div className="flex items-center justify-between">
          <span>STARTUP SCAN</span>
          <span className={`w-1.5 h-1.5 rounded-full ${wsConnected ? 'bg-[#10b981]' : 'bg-[#71717a]'}`} />
        </div>
        <div className="mt-1 text-zinc-400">
          {startupScanDate || '—'} @ {startupRequestedScanTime || '09:45:00'}
          {startupScanTime && startupRequestedScanTime && startupScanTime !== startupRequestedScanTime
            ? ` · scan ${startupScanTime}`
            : ''}
          {' · '}{startupSelectionMode || 'baseline'}
        </div>
      </div>

      <div className="flex-1 overflow-y-auto">
        <div className="flex items-center justify-between px-3 h-7 border-b border-[#1c1c1e]">
          <span className="text-[#71717a] font-bold tracking-wider uppercase text-[8px]">Watchlist</span>
          <span className="text-zinc-600 text-[8px]">{startupCandidates.length} ranked</span>
        </div>
        <div className="divide-y divide-[#1c1c1e]">
          {startupCandidates.map((candidate) => (
            <div
              key={candidate.symbol}
              onClick={() => selectSymbol(candidate.symbol, 'NSE_EQ')}
              className={`flex items-center justify-between px-3 py-1.5 cursor-pointer hover:bg-[#0e0e11] transition ${
                selectedSymbol === candidate.symbol ? 'bg-[#0e0e11] border-l-2 border-[#00d2ff]' : ''
              }`}
            >
              <div>
                <span className="text-zinc-300 font-bold">{candidate.symbol}</span>
                <div className="text-[8px] text-zinc-600">#{candidate.rank} · score {candidate.masterScore.toFixed(2)}</div>
              </div>
              <span className="text-[#10b981] text-[8px]">RS {candidate.rsScore.toFixed(2)}</span>
            </div>
          ))}
        </div>
      </div>

      <div className="border-t border-[#1c1c1e] p-3 space-y-2">
        <div className="text-[#71717a] font-bold tracking-wider uppercase text-[8px]">Indicators</div>
        <label className="flex items-center justify-between text-[8px] text-zinc-500">
          Amplitude
          <input
            type="number"
            min={1}
            max={10}
            value={indicatorParams.amplitude}
            onChange={(e) => setIndicatorParams({amplitude: Number(e.target.value)})}
            className="w-12 bg-[#0e0e11] border border-zinc-800 rounded-xs px-1 text-zinc-300"
          />
        </label>
        <label className="flex items-center justify-between text-[8px] text-zinc-500">
          Deviation
          <input
            type="number"
            min={1}
            max={10}
            value={indicatorParams.channelDeviation}
            onChange={(e) => setIndicatorParams({channelDeviation: Number(e.target.value)})}
            className="w-12 bg-[#0e0e11] border border-zinc-800 rounded-xs px-1 text-zinc-300"
          />
        </label>
      </div>
    </div>
  );
}
