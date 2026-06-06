import {useTerminalStore} from '@/state/terminalStore';
import {WatchlistPanel} from '@/ui/panels/WatchlistPanel';
import {ChartPanel} from '@/ui/panels/ChartPanel';
import {OptionChainPanel} from '@/ui/panels/OptionChainPanel';
import {OrdersPanel} from '@/ui/panels/OrdersPanel';
import {PositionsPanel} from '@/ui/panels/PositionsPanel';
import {LogsPanel} from '@/ui/panels/LogsPanel';

export function TerminalLayout() {
  const {activePanels} = useTerminalStore();

  return (
    <div className="w-screen h-screen bg-[#070709] text-[#e4e4e7] font-mono overflow-hidden">
      <div className="h-[46px] border-b border-[#1c1c1e] bg-[#09090b] px-4 flex items-center justify-between">
        <div className="flex items-center gap-3">
          <div className="w-7 h-7 bg-[#00d2ff] flex items-center justify-center rounded-sm">
            <span className="text-[#070709] text-[11px] font-extrabold">TJ</span>
          </div>
          <div className="leading-tight">
            <div className="text-[10px] font-black tracking-widest uppercase text-white">
              Trade-J <span className="text-[#71717a] font-light">Terminal</span>
            </div>
            <div className="text-[8px] text-[#71717a] font-bold">Broker-agnostic foundation (Phase 1)</div>
          </div>
        </div>

        <div className="flex items-center gap-2 text-[10px]">
          <span className="px-2 py-1 rounded-xs border border-[#00d2ff]/35 bg-[#00d2ff]/10 text-[#7be7ff] font-black">
            WS: LIVE
          </span>
          <span className="px-2 py-1 rounded-xs border border-[#c084fc]/35 bg-[#c084fc]/10 text-[#e9d5ff] font-black">
            MODE: MOCK
          </span>
        </div>
      </div>

      {/* Hierarchy: secondary watchlist, dominant charts + option chain */}
      <div className="grid grid-cols-[280px_1fr] grid-rows-[1fr_220px] h-[calc(100vh-46px)]">
        {/* Left: watchlist (full height) */}
        <div className="border-r border-[#1c1c1e] bg-[#09090b] overflow-hidden">
          {activePanels.watchlist && <WatchlistPanel />}
        </div>

        {/* Right: main workspace */}
        <div className="grid grid-cols-[1fr_380px] grid-rows-[1fr] h-full">
          <div className="col-span-1 border-r border-[#1c1c1e] bg-[#09090b] overflow-hidden">
            {activePanels.charts && <ChartPanel />}
          </div>
          <div className="bg-[#09090b] overflow-hidden">
            {activePanels.optionChain && <OptionChainPanel />}
          </div>
        </div>

        {/* Bottom: secondary panels (collapsible-ready compact placeholders) */}
        <div className="border-t border-[#1c1c1e] bg-[#09090b] overflow-hidden col-span-2">
          <div className="grid grid-cols-3 h-full">
            <div className="border-r border-[#1c1c1e] overflow-hidden">
              {activePanels.orders && <OrdersPanel />}
            </div>
            <div className="border-r border-[#1c1c1e] overflow-hidden">
              {activePanels.positions && <PositionsPanel />}
            </div>
            <div className="overflow-hidden">
              {activePanels.logs && <LogsPanel />}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

