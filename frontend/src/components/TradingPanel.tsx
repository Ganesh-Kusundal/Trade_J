import {useStudioStore} from '@/store/useStudioStore';

export function TradingPanel() {
  const positions = useStudioStore((s) => s.positions);
  const trades = useStudioStore((s) => s.trades);
  const selectedSymbol = useStudioStore((s) => s.selectedSymbol);

  const placeOrder = (_side: 'BUY' | 'SELL') => {
    console.log('Order placement via REST API not yet implemented');
    // TODO: Implement via admin/execution REST endpoints
  };

  return (
    <div className="h-full flex flex-col text-[10px] font-mono">
      <div className="h-8 border-b border-[#1c1c1e] flex items-center px-3 shrink-0">
        <span className="text-[#71717a] font-bold tracking-wider uppercase text-[9px]">Order Entry</span>
      </div>

      <div className="p-3 space-y-3 flex-1 overflow-y-auto">
        <div className="flex gap-2">
          <button
            onClick={() => placeOrder('BUY')}
            className="flex-1 h-8 bg-[#10b981]/20 border border-[#10b981]/40 text-[#10b981] font-bold rounded-xs hover:bg-[#10b981]/30 transition cursor-pointer text-[11px]"
          >
            BUY
          </button>
          <button
            onClick={() => placeOrder('SELL')}
            className="flex-1 h-8 bg-[#ef4444]/20 border border-[#ef4444]/40 text-[#ef4444] font-bold rounded-xs hover:bg-[#ef4444]/30 transition cursor-pointer text-[11px]"
          >
            SELL
          </button>
        </div>

        <div>
          <div className="text-[#71717a] font-semibold uppercase tracking-wider text-[8px] mb-2">Positions</div>
          {positions.length === 0 && (
            <div className="text-zinc-600 text-[9px] py-2 text-center">No open positions</div>
          )}
          {positions.map((p) => (
            <div key={p.symbol} className="flex items-center justify-between py-1.5 border-b border-[#1c1c1e] last:border-0">
              <div>
                <span className="text-zinc-300 font-bold">{p.symbol}</span>
                <span className={`ml-2 text-[9px] ${p.side === 'LONG' ? 'text-[#10b981]' : 'text-[#ef4444]'}`}>{p.side}</span>
              </div>
              <div className="text-right">
                <div className="text-zinc-300">{p.quantity} @ ₹{(p.entryPrice / 100).toFixed(2)}</div>
                <div className={p.pnl >= 0 ? 'text-[#10b981]' : 'text-[#ef4444]'}>
                  ₹{(p.pnl / 100).toFixed(2)} ({p.pnlPercent.toFixed(2)}%)
                </div>
              </div>
            </div>
          ))}
        </div>

        <div>
          <div className="text-[#71717a] font-semibold uppercase tracking-wider text-[8px] mb-2">Recent Trades</div>
          {trades.length === 0 && (
            <div className="text-zinc-600 text-[9px] py-2 text-center">No trades yet</div>
          )}
          {trades.slice(-10).reverse().map((t) => (
            <div key={t.id} className="flex justify-between py-1 border-b border-[#1c1c1e] last:border-0 text-[9px]">
              <span className="text-zinc-400">{t.symbol}</span>
              <span className={t.side === 'BUY' ? 'text-[#10b981]' : 'text-[#ef4444]'}>{t.side}</span>
              <span className="text-zinc-300">{t.quantity} @ ₹{(t.price / 100).toFixed(2)}</span>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}
