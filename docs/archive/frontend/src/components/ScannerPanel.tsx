import {useStudioStore} from '@/store/useStudioStore';
import {useEffect} from 'react';
import {scanApi} from '@/api/client';

export function ScannerPanel() {
  const scanResult = useStudioStore((s) => s.scanResult);
  const scanLoading = useStudioStore((s) => s.scanLoading);
  const setScannerFilter = useStudioStore((s) => s.setScannerFilter);
  const scannerFilter = useStudioStore((s) => s.scannerFilter);
  const runScan = useStudioStore((s) => s.runScan);
  const selectSymbol = useStudioStore((s) => s.selectSymbol);

  useEffect(() => {
    scanApi.latest(scannerFilter.profile)
      .then((r) => useStudioStore.setState({scanResult: r}))
      .catch(() => {});
  }, []);

  return (
    <div className="h-full flex flex-col text-[10px] font-mono">
      <div className="h-8 border-b border-[#1c1c1e] flex items-center justify-between px-3 shrink-0">
        <span className="text-[#71717a] font-bold tracking-wider uppercase text-[9px]">Scanner</span>
        <button
          onClick={() => runScan()}
          disabled={scanLoading}
          className="h-5 px-2 bg-[#00d2ff]/20 border border-[#00d2ff]/40 text-[#00d2ff] font-bold rounded-xs hover:bg-[#00d2ff]/30 transition cursor-pointer disabled:opacity-40 text-[9px]"
        >
          {scanLoading ? 'RUNNING...' : 'RUN SCAN'}
        </button>
      </div>

      <div className="p-3 space-y-2 flex-1 overflow-y-auto">
        {scanResult && (
          <>
            <div className="flex items-center gap-3 text-[#71717a] text-[9px] pb-2 border-b border-[#1c1c1e]">
              <span>Run: <span className="text-zinc-300">{scanResult.run.runId.slice(0, 8)}</span></span>
              <span>Profile: <span className="text-zinc-300">{scanResult.run.profileId}</span></span>
              <span>Hits: <span className="text-zinc-300">{scanResult.run.hitCount}</span></span>
              <span>Status: <span className={scanResult.run.status === 'SUCCESS' ? 'text-[#10b981]' : 'text-[#f59e0b]'}>{scanResult.run.status}</span></span>
            </div>

            <div className="space-y-1">
              {scanResult.hits.filter((h) => h.promoted).map((hit) => (
                <div
                  key={`${hit.symbol}-${hit.exchangeSegment}`}
                  onClick={() => selectSymbol(hit.symbol, hit.exchangeSegment)}
                  className="flex items-center justify-between px-2 py-1.5 bg-[#0e0e11] border border-[#f59e0b]/30 rounded-xs cursor-pointer hover:border-[#f59e0b]/60 transition"
                >
                  <div className="flex items-center gap-2">
                    <span className="text-zinc-200 font-bold">{hit.symbol}</span>
                    {hit.underlying && <span className="text-zinc-500 text-[8px]">{hit.underlying}</span>}
                  </div>
                  <div className="flex items-center gap-3">
                    <span className="text-[#f59e0b] font-bold">{hit.score.toFixed(2)}</span>
                    <span className="text-[#10b981] text-[8px] border border-[#10b981]/30 px-1">PROMOTED</span>
                  </div>
                </div>
              ))}
              {scanResult.hits.filter((h) => !h.promoted).slice(0, 15).map((hit) => (
                <div
                  key={`${hit.symbol}-${hit.exchangeSegment}`}
                  onClick={() => selectSymbol(hit.symbol, hit.exchangeSegment)}
                  className="flex items-center justify-between px-2 py-1 bg-[#0e0e11] border border-[#1c1c1e] rounded-xs cursor-pointer hover:border-zinc-700 transition"
                >
                  <div className="flex items-center gap-2">
                    <span className="text-zinc-300 font-bold">{hit.symbol}</span>
                    <span className="text-zinc-600 text-[8px]">{hit.assetClass}</span>
                  </div>
                  <div className="flex items-center gap-2">
                    <span className="text-zinc-400">{hit.score.toFixed(2)}</span>
                    {hit.reasons.length > 0 && (
                      <span className="text-zinc-600 text-[8px] truncate max-w-[80px]">{hit.reasons.join(', ')}</span>
                    )}
                  </div>
                </div>
              ))}
            </div>
          </>
        )}

        {!scanResult && !scanLoading && (
          <div className="text-zinc-600 text-[9px] text-center py-8">
            No scan results available. Click "RUN SCAN" to start.
          </div>
        )}

        {scanLoading && (
          <div className="text-zinc-600 text-[9px] text-center py-8 animate-pulse">Running scan...</div>
        )}
      </div>
    </div>
  );
}
