import {useEffect, useRef, useState} from 'react';
import {useStudioStore} from '@/store/useStudioStore';
import {Activity, Compass, Sliders, ShieldAlert} from 'lucide-react';

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
  const candles = useStudioStore((s) => s.candles);
  const indicatorParams = useStudioStore((s) => s.indicatorParams);
  const setIndicatorParams = useStudioStore((s) => s.setIndicatorParams);
  const bootstrapStartupCandidates = useStudioStore((s) => s.bootstrapStartupCandidates);
  const structureBreaks = useStudioStore((s) => s.structureBreaks);

  const [localAmplitude, setLocalAmplitude] = useState(indicatorParams.amplitude);
  const [localDeviation, setLocalDeviation] = useState(indicatorParams.channelDeviation);
  const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    setLocalAmplitude(indicatorParams.amplitude);
  }, [indicatorParams.amplitude]);

  useEffect(() => {
    setLocalDeviation(indicatorParams.channelDeviation);
  }, [indicatorParams.channelDeviation]);

  const debouncedUpdate = (amp: number, dev: number) => {
    if (debounceRef.current) clearTimeout(debounceRef.current);
    debounceRef.current = setTimeout(() => {
      setIndicatorParams({amplitude: amp, channelDeviation: dev});
    }, 150);
  };

  const handleAmplitudeChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const val = Number(e.target.value);
    setLocalAmplitude(val);
    debouncedUpdate(val, localDeviation);
  };

  const handleDeviationChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const val = Number(e.target.value);
    setLocalDeviation(val);
    debouncedUpdate(localAmplitude, val);
  };

  const recentBreaks = structureBreaks.slice(-12).reverse();

  const currentPrice = candles.length > 0 ? candles[candles.length - 1].closePaisa / 100 : 0;

  if (!sidebarOpen) return null;

  return (
    <div className="w-80 bg-[#0e0e11] border-r border-[#1c1c1e] flex flex-col shrink-0 text-[10px] font-mono">
      <div className="h-12 border-b border-[#18181b] flex items-center px-4 gap-2 bg-[#09090b] text-xs font-bold text-zinc-300 tracking-wider">
        <Activity size={14} className="text-[#00d2ff]" />
        <span>MARKET INTELLIGENCE</span>
      </div>

      <div className="flex-1 overflow-y-auto divide-y divide-[#18181b]">
        <div className="p-4">
          <h3 className="text-[10px] font-bold text-zinc-500 uppercase tracking-widest flex items-center gap-1.5 mb-3">
            <Compass size={12} />
            <span>Market Watch</span>
          </h3>
          {startupCandidates.length > 0 && (
            <div className="mb-3 space-y-1">
              <p className="text-[9px] text-zinc-600">
                Startup scan: {startupScanDate || 'n/a'} @ {startupRequestedScanTime || 'n/a'}
                {startupScanTime && startupScanTime !== startupRequestedScanTime ? ` · scan ${startupScanTime}` : ''}
              </p>
              <p className="text-[9px] text-zinc-500">
                Selection: {startupSelectionMode || 'baseline'} · Ranked
              </p>
            </div>
          )}
          <div className="flex flex-col gap-1.5">
            {startupCandidates.length > 0 ? startupCandidates.map((candidate) => {
              const isActive = candidate.symbol === selectedSymbol;
              return (
                <button
                  key={candidate.symbol}
                  onClick={() => selectSymbol(candidate.symbol, 'NSE_EQ')}
                  className={`w-full text-left flex items-center justify-between p-2.5 transition-all border ${
                    isActive
                      ? 'bg-[#181822] border-[#2563eb]/40 text-[#00d2ff]'
                      : 'bg-[#09090b]/80 border-[#1c1c1e] text-zinc-400 hover:text-zinc-200 hover:bg-[#121215]'
                  }`}
                >
                  <div className="min-w-0">
                    <p className="font-bold text-[11px] leading-tight flex items-center gap-1.5">
                      {isActive && <span className="w-1.5 h-1.5 rounded-full bg-[#00d2ff] animate-pulse" />}
                      {candidate.symbol}
                    </p>
                    <p className="text-[9px] text-zinc-500 mt-0.5">
                      #{candidate.rank} · score {candidate.masterScore.toFixed(2)}
                    </p>
                  </div>
                  <div className="text-right">
                    <p className="font-bold text-[11px] leading-tight text-zinc-200 font-mono">
                      {isActive && currentPrice > 0 ? `₹${currentPrice.toFixed(2)}` : '--'}
                    </p>
                    <p className={`text-[9px] font-bold mt-0.5 ${isActive ? 'text-zinc-400' : 'text-zinc-600'}`}>
                      {isActive ? 'ACTIVE' : 'LOAD'}
                    </p>
                  </div>
                </button>
              );
            }) : (
              <p className="text-[10px] text-[#71717a] text-center py-4 border border-dashed border-[#1c1c1e] rounded bg-[#09090b]/40">
                Waiting for startup scan candidates...
              </p>
            )}
          </div>
        </div>

        <div className="p-4 flex-1 min-h-0 flex flex-col">
          <h3 className="text-[10px] font-bold text-zinc-500 uppercase tracking-widest flex items-center gap-1.5 mb-3">
            <ShieldAlert size={12} className="text-yellow-500" />
            <span>Structure Scanner</span>
          </h3>
          <div className="flex-grow overflow-y-auto max-h-64 flex flex-col gap-2">
            {recentBreaks.length > 0 ? (
              recentBreaks.map((br, idx) => {
                const isBullish = br.direction === 'bullish';
                const isCHOCH = br.type === 'CHOCH';
                return (
                  <div
                    key={`${br.timeMs}_${idx}`}
                    className={`p-2 border leading-normal border-l-2 bg-[#09090c]/70 hover:bg-[#121215] cursor-pointer ${
                      isCHOCH
                        ? 'border-[#1c1c1e] border-l-amber-500'
                        : 'border-[#1c1c1e] border-l-blue-500'
                    }`}
                  >
                    <div className="flex justify-between items-center text-[10px]">
                      <span className={`font-bold ${isCHOCH ? 'text-amber-500' : 'text-blue-500'}`}>
                        {br.type} {isBullish ? '▲ BULLISH' : '▼ BEARISH'}
                      </span>
                      <span className="text-zinc-600 font-mono text-[9px]">
                        {new Date(br.timeMs).toISOString().slice(11, 19)}
                      </span>
                    </div>
                    <p className="text-[10px] font-bold text-zinc-300 mt-1">
                      Price: ₹{br.price.toFixed(1)}
                    </p>
                  </div>
                );
              })
            ) : (
              <p className="text-[10px] text-[#71717a] text-center py-4 border border-dashed border-[#1c1c1e] rounded bg-[#09090b]/40">
                Awaiting structural breaks in stream...
              </p>
            )}
          </div>
        </div>

        <div className="p-4 border-t border-[#18181b]">
          <h3 className="text-[10px] font-bold text-zinc-500 uppercase tracking-widest flex items-center gap-1.5 mb-3">
            <Sliders size={12} className="text-[#00d2ff]" />
            <span>Indicator Controls</span>
          </h3>
          <div className="space-y-3">
            <div>
              <div className="flex justify-between text-[9px] text-zinc-400 mb-1">
                <span>Amplitude</span>
                <span>{localAmplitude} Bars</span>
              </div>
              <input
                type="range"
                min={1}
                max={10}
                value={localAmplitude}
                onChange={handleAmplitudeChange}
                className="w-full h-1 bg-zinc-700 rounded-lg appearance-none cursor-pointer"
              />
            </div>
            <div>
              <div className="flex justify-between text-[9px] text-zinc-400 mb-1">
                <span>Channel Deviation</span>
                <span>{localDeviation.toFixed(1)} ATR</span>
              </div>
              <input
                type="range"
                min={0.5}
                max={5}
                step={0.5}
                value={localDeviation}
                onChange={handleDeviationChange}
                className="w-full h-1 bg-zinc-700 rounded-lg appearance-none cursor-pointer"
              />
            </div>
          </div>
        </div>
      </div>

      <div className="h-8 border-t border-[#18181b] bg-[#09090b] flex items-center justify-between px-3 text-[9px] text-zinc-600">
        <span>SCANNER v2.8</span>
        <span className="text-[#10b981]">SYSTEM ACTIVE</span>
      </div>
    </div>
  );
}
