import {useEffect, useState} from 'react';
import {portfolioApi} from '@/api/client';
import type {PortfolioSnapshot} from '@/dto/types';

export function PortfolioAnalyticsPanel() {
  const [snapshot, setSnapshot] = useState<PortfolioSnapshot | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let source: EventSource | null = null;
    try {
      source = new EventSource('/api/v1/portfolio/stream');
      source.addEventListener('portfolio', (ev) => {
        try {
          setSnapshot(JSON.parse(ev.data) as PortfolioSnapshot);
          setError(null);
        } catch {
          setError('Invalid portfolio snapshot');
        }
      });
      source.onerror = () => setError('Portfolio stream disconnected');
    } catch (e) {
      setError(String(e));
    }
    return () => source?.close();
  }, []);

  if (!snapshot) {
    return (
      <div className="h-full flex items-center justify-center text-zinc-600 text-[10px] font-mono">
        {error || 'Loading portfolio…'}
      </div>
    );
  }

  const exposureEntries = Object.entries(snapshot.netPositions || {});
  const totalExposure = exposureEntries.reduce((sum, [, qty]) => sum + Math.abs(qty), 0);

  return (
    <div className="h-full overflow-y-auto p-4 font-mono text-[10px] space-y-4">
      <div className="text-[11px] font-bold text-zinc-300 uppercase tracking-wider">Portfolio Analytics</div>

      <div className="grid grid-cols-2 gap-3">
        <MetricCard label="Open Trades" value={String(snapshot.openTrades)} />
        <MetricCard
          label="Kill Switch"
          value={snapshot.killSwitchActive ? 'ACTIVE' : 'OFF'}
          alert={snapshot.killSwitchActive}
        />
        <MetricCard label="Realized Loss" value={`₹${(snapshot.realizedLossPaisa / 100).toFixed(0)}`} />
        <MetricCard label="Unrealized Loss" value={`₹${(snapshot.unrealizedLossPaisa / 100).toFixed(0)}`} />
        <MetricCard label="Net Exposure (qty)" value={String(totalExposure)} />
        <MetricCard label="Symbols" value={String(exposureEntries.length)} />
      </div>

      <div>
        <div className="text-[8px] text-zinc-500 uppercase tracking-wider mb-2">Strategy Allocations</div>
        {snapshot.allocations.length === 0 && (
          <div className="text-zinc-600 text-[9px]">No allocations</div>
        )}
        {snapshot.allocations.map((a) => {
          const pct = a.allocatedCapitalPaisa > 0
            ? (a.usedCapitalPaisa / a.allocatedCapitalPaisa) * 100
            : 0;
          return (
            <div key={a.strategy} className="mb-2">
              <div className="flex justify-between text-zinc-400 mb-1">
                <span>{a.strategy}</span>
                <span>{pct.toFixed(0)}% used</span>
              </div>
              <div className="h-1 bg-zinc-900 rounded-full overflow-hidden">
                <div className="h-full bg-[#00d2ff]" style={{width: `${Math.min(100, pct)}%`}} />
              </div>
            </div>
          );
        })}
      </div>

      <div>
        <div className="text-[8px] text-zinc-500 uppercase tracking-wider mb-2">Net Positions</div>
        {exposureEntries.map(([sym, qty]) => (
          <div key={sym} className="flex justify-between py-1 border-b border-zinc-900 text-zinc-400">
            <span>{sym}</span>
            <span className={qty >= 0 ? 'text-[#10b981]' : 'text-[#ef4444]'}>{qty}</span>
          </div>
        ))}
      </div>
    </div>
  );
}

function MetricCard({label, value, alert}: {label: string; value: string; alert?: boolean}) {
  return (
    <div className={`p-2 rounded-xs border ${alert ? 'border-[#ef4444]/40 bg-[#ef4444]/10' : 'border-zinc-800 bg-[#0e0e11]'}`}>
      <div className="text-[7.5px] text-zinc-500 uppercase">{label}</div>
      <div className={`text-[11px] font-bold ${alert ? 'text-[#ef4444]' : 'text-zinc-200'}`}>{value}</div>
    </div>
  );
}
