import {useMemo} from 'react';
import {useTerminalStore} from '@/state/terminalStore';

type WatchItem = {
  symbol: string;
  exchange: string;
  ltp: number;
  changePct: number;
};

const mockInitial: WatchItem[] = [
  {symbol: 'RELIANCE', exchange: 'NSE', ltp: 2895.2, changePct: 0.82},
  {symbol: 'TCS', exchange: 'NSE', ltp: 3985.7, changePct: -0.31},
  {symbol: 'INFY', exchange: 'NSE', ltp: 1712.15, changePct: 1.14},
  {symbol: 'HDFCBANK', exchange: 'NSE', ltp: 1498.05, changePct: 0.22},
  {symbol: 'ICICIBANK', exchange: 'NSE', ltp: 1087.8, changePct: -0.44},
];

export function Watchlist() {
  const selectedSymbol = useTerminalStore((s) => s.selectedSymbol);
  const setSelectedSymbol = useTerminalStore((s) => s.setSelectedSymbol);

  const items = useMemo(() => mockInitial, []);

  return (
    <div className="h-full flex flex-col">
      <div className="h-11 px-3 flex items-center justify-between border-b border-[#1c1c1e]">
        <div className="text-[11px] font-black uppercase tracking-widest text-white">Watchlist</div>
        <div className="text-[9px] text-[#71717a]">Mock</div>
      </div>

      <div className="p-3">
        <div className="mb-3">
          <div className="text-[9px] text-[#71717a] font-bold mb-1 uppercase tracking-wider">Symbol Search</div>
          <input
            className="w-full h-8 rounded-xs border border-[#1c1c1e] bg-[#0e0e11] text-zinc-200 px-2 text-[11px] outline-none"
            placeholder="Type to search (mock)"
            onChange={() => {}}
          />
        </div>

        <div className="text-[9px] text-[#71717a] font-bold uppercase tracking-wider mb-2">Instruments</div>

        {/* Scan-friendly rows (no cramped blocks) */}
        <div className="divide-y divide-[#1c1c1e]">
          {items.map((it) => {
            const isActive = selectedSymbol === it.symbol;
            const isUp = it.changePct >= 0;
            return (
              <button
                key={it.symbol}
                onClick={() => setSelectedSymbol(it.symbol)}
                className={[
                  'w-full text-left px-2 py-2 transition',
                  isActive
                    ? 'bg-[#00d2ff]/10'
                    : 'bg-transparent hover:bg-[#1c1c1e]/30',
                ].join(' ')}
              >
                <div className="grid grid-cols-[1fr_96px] items-center gap-2">
                  <div className="min-w-0">
                    <div className="flex items-baseline gap-2">
                      <div className="text-[11px] font-bold text-zinc-200 leading-none truncate">{it.symbol}</div>
                      <div className="text-[8.5px] text-[#71717a] font-semibold">{it.exchange}</div>
                    </div>
                  </div>
                  <div className="text-right">
                    <div className="text-[11px] font-extrabold text-zinc-100 leading-none">₹{it.ltp.toFixed(2)}</div>
                    <div
                      className={[
                        'text-[9px] font-black leading-none',
                        isUp ? 'text-[#10b981]' : 'text-[#ef4444]',
                      ].join(' ')}
                    >
                      {isUp ? '▲ ' : '▼ '}
                      {Math.abs(it.changePct).toFixed(2)}%
                    </div>
                  </div>
                </div>
              </button>
            );
          })}
        </div>

      </div>
    </div>
  );
}
