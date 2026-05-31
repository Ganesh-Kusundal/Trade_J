import {useEffect, useRef} from 'react';
import {ChartWidget} from '@/charts/ChartWidget';
import {LeftSidebar} from '@/components/LeftSidebar';
import {CommandPalette} from '@/components/CommandPalette';
import {ErrorBoundary} from '@/components/ErrorBoundary';
import {TradingPanel} from '@/components/TradingPanel';
import {ScannerPanel} from '@/components/ScannerPanel';
import {PipelinePanel} from '@/components/PipelinePanel';
import {AdminPanel} from '@/components/AdminPanel';
import {useStudioStore} from '@/store/useStudioStore';
import {Terminal, Wifi, BarChart3, Search, GitBranch, Settings} from 'lucide-react';

export default function App() {
  const {
    selectedSymbol, interval, from, to, candles, symbols, startupCandidates, loadCandles, error, clearError,
    setActiveView, activeView, connect, disconnect, wsConnected, bootstrapStartupCandidates,
  } = useStudioStore();

  const lastLoadRef = useRef('');

  // Connect to backend gateway on mount
  useEffect(() => {
    const wsUrl = `${window.location.protocol === 'https:' ? 'wss://' : 'ws://'}${window.location.host}/ws/gateway`;
    connect(wsUrl);
    bootstrapStartupCandidates().catch(() => {});
    return () => disconnect();
  }, []);

  // Load candles when symbol/interval/date changes
  useEffect(() => {
    const key = `${selectedSymbol}|${interval}|${from}|${to}`;
    if (key !== lastLoadRef.current && selectedSymbol) {
      lastLoadRef.current = key;
      loadCandles();
    }
  }, [selectedSymbol, interval, from, to]);

  const currentPrice = candles.length > 0 ? candles[candles.length - 1].closePaisa / 100 : 0;
  const firstPrice = candles.length > 0 ? candles[0].closePaisa / 100 : 0;
  const pctChange = firstPrice > 0 ? ((currentPrice - firstPrice) / firstPrice) * 100 : 0;

  return (
    <div className="w-full h-screen bg-[#070709] text-[#e4e4e7] flex flex-col font-mono overflow-hidden select-none">
      {/* Header */}
      <header className="h-12 border-b border-[#1c1c1e] bg-[#09090b] flex items-center justify-between px-4 shrink-0">
        <div className="flex items-center gap-5">
          <div className="flex items-center gap-2">
            <div className="w-5 h-5 bg-[#00d2ff] flex items-center justify-center rounded-sm">
              <Terminal size={11} className="text-[#070709]" />
            </div>
            <span className="text-xs font-black tracking-widest text-[#ffffff] uppercase leading-none">
              Trade-J <span className="text-[#71717a] font-light text-[9.5px]">Console</span>
            </span>
          </div>

          <div className="h-4 w-[1px] bg-zinc-800" />

          <div className="flex items-center gap-3 text-[10px]">
            <div className="flex flex-col">
              <span className="text-[7.5px] text-[#71717a] font-semibold leading-none mb-1 uppercase tracking-wider">INSTRUMENT</span>
              <select
                value={selectedSymbol}
                name="instrument"
                onChange={(e) => {
                  const sym = symbols.find((s) => s.symbol === e.target.value);
                  if (sym) useStudioStore.getState().selectSymbol(sym.symbol, sym.exchangeSegment);
                }}
                aria-label="Select instrument"
                className="bg-[#0e0e11] border border-zinc-800 h-6 px-1.5 rounded-xs text-zinc-300 font-bold outline-none cursor-pointer hover:border-zinc-700 transition text-[10px]"
              >
                {startupCandidates.length > 0
                  ? startupCandidates.map((c) => (
                      <option key={c.symbol} value={c.symbol}>{c.symbol}</option>
                    ))
                  : symbols.map((s) => (
                      <option key={s.symbol} value={s.symbol}>{s.symbol}</option>
                    ))}
              </select>
            </div>

            <div className="flex flex-col">
              <span className="text-[7.5px] text-[#71717a] font-semibold leading-none mb-1 uppercase tracking-wider">TIMEFRAME</span>
              <select
                value={interval}
                name="timeframe"
                onChange={(e) => useStudioStore.getState().setInterval(e.target.value)}
                aria-label="Select timeframe"
                className="bg-[#0e0e11] border border-zinc-800 h-6 px-1.5 rounded-xs text-zinc-300 font-bold outline-none cursor-pointer hover:border-zinc-700 transition text-[10px]"
              >
                {['1m', '3m', '5m', '15m', '30m', '1h', '4h', '1d'].map((tf) => (
                  <option key={tf} value={tf}>{tf}</option>
                ))}
              </select>
            </div>

            <div className="flex flex-col">
              <span className="text-[7.5px] text-[#71717a] font-semibold leading-none mb-1 uppercase tracking-wider">STRATEGY</span>
              <span className="bg-[#10b981]/10 border border-[#10b981]/30 text-[#10b981] font-bold h-6 px-2 flex items-center rounded-xs text-[10px]">
                Institutional Baseline
              </span>
            </div>
          </div>
        </div>

        <div className="flex items-center gap-4 text-[10px]">
          <div className="flex items-center gap-3 border border-zinc-800 bg-[#0e0e11] px-3 h-7 rounded-xs text-zinc-500">
            <div className="flex items-center gap-1.5 font-semibold text-[9px]">
              <Wifi size={11} className={wsConnected ? 'text-[#10b981]' : 'text-[#ef4444]'} />
              <span className="text-zinc-400 uppercase">GW:</span>
              <span className={wsConnected ? 'text-[#10b981]' : 'text-[#ef4444]'}>{wsConnected ? 'LIVE' : 'OFF'}</span>
            </div>
            <div className="h-3 w-[1px] bg-zinc-800" />
            <div className="flex items-center gap-1.5 font-semibold text-[9px]">
              <span className="text-zinc-400">P/L:</span>
              <span className={pctChange >= 0 ? 'text-[#10b981]' : 'text-[#ef4444]'}>
                {pctChange >= 0 ? '+' : ''}{pctChange.toFixed(2)}%
              </span>
            </div>
          </div>

          <div className="flex items-center gap-1 bg-[#0e0e11] p-0.5 border border-zinc-800 rounded-xs h-7">
            {[
              {view: 'chart' as const, icon: BarChart3},
              {view: 'scanner' as const, icon: Search},
              {view: 'pipeline' as const, icon: GitBranch},
              {view: 'admin' as const, icon: Settings},
            ].map(({view, icon: Icon}) => (
              <button
                key={view}
                onClick={() => setActiveView(view)}
                className={`h-5 w-7 flex items-center justify-center rounded-xs transition cursor-pointer ${
                  activeView === view ? 'bg-zinc-800 text-white' : 'text-zinc-500 hover:text-zinc-300'
                }`}
              >
                <Icon size={12} />
              </button>
            ))}
          </div>

          <button
            onClick={() => window.dispatchEvent(new KeyboardEvent('keydown', {metaKey: true, code: 'KeyK'}))}
            className="flex items-center gap-1.5 bg-[#00d2ff] hover:bg-[#00b2d6] text-[#070709] h-7 px-3 font-bold rounded-xs transition-colors cursor-pointer text-[9px]"
          >
            Terminal (⌘K)
          </button>
        </div>
      </header>

      {/* Error banner */}
      {error && (
        <div className="h-7 bg-[#ef4444]/15 border-b border-[#ef4444]/30 flex items-center justify-between px-4 shrink-0">
          <span className="text-[#ef4444] text-[10px] font-mono">{error}</span>
          <button onClick={clearError} className="text-[#ef4444] text-[10px] cursor-pointer hover:underline">Dismiss</button>
        </div>
      )}

      {/* Main content */}
      <div className="flex flex-1 min-h-0 overflow-hidden">
        <LeftSidebar />

        <div className="flex-1 flex flex-col min-w-0">
          {activeView === 'chart' && (
            <div className="flex flex-1 min-h-0">
              <div className="flex-1 flex flex-col min-w-0">
                {/* Price HUD */}
                <div className="absolute top-3 left-4 z-40 bg-[#09090b]/92 border border-zinc-800 rounded-sm p-3 backdrop-blur-xs flex items-center gap-5 select-none shadow-xl">
                  <div className="flex flex-col">
                    <span className="text-[8px] text-zinc-500 font-bold uppercase tracking-wider leading-none mb-1 font-mono">LTP</span>
                    <span className="text-base font-black font-mono tracking-tight text-zinc-100">
                      {currentPrice > 0 ? `₹${currentPrice.toFixed(2)}` : '--'}
                    </span>
                  </div>
                  <div className="h-6 w-[1px] bg-zinc-800" />
                  <div className="flex flex-col">
                    <span className="text-[8px] text-zinc-500 font-bold uppercase tracking-wider leading-none mb-1 font-mono">CHANGE</span>
                    <span className={`text-xs font-bold font-mono ${pctChange >= 0 ? 'text-[#10b981]' : 'text-[#ef4444]'}`}>
                      {pctChange >= 0 ? '▲ +' : '▼ '}{pctChange.toFixed(2)}%
                    </span>
                  </div>
                  <div className="h-6 w-[1px] bg-zinc-800" />
                  <div className="flex flex-col">
                    <span className="text-[8px] text-zinc-500 font-bold uppercase tracking-wider leading-none mb-1 font-mono">VOLUME</span>
                    <span className="text-xs font-bold font-mono text-zinc-100">
                      {candles.length > 0 ? (candles[candles.length - 1].volume).toLocaleString() : '--'}
                    </span>
                  </div>
                </div>

                <div className="flex-grow min-h-0 relative">
                  <ErrorBoundary>
                    <ChartWidget />
                  </ErrorBoundary>
                </div>
              </div>

              {/* Right trading panel */}
              <div className="w-64 border-l border-[#1c1c1e] bg-[#09090b] shrink-0">
                <TradingPanel />
              </div>
            </div>
          )}

          {activeView === 'scanner' && <ScannerPanel />}
          {activeView === 'pipeline' && <PipelinePanel />}
          {activeView === 'admin' && <AdminPanel />}
        </div>
      </div>

      <CommandPalette />
    </div>
  );
}
