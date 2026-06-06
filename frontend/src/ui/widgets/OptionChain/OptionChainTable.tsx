import {useMemo, useState} from 'react';
import type {OptionChain} from '@/domain/dto';

type Row = {
  strike: number;
  ce: {
    oi: number;
    chgOi: number;
    iv: number;
    ltp: number;
  };
  pe: {
    oi: number;
    chgOi: number;
    iv: number;
    ltp: number;
  };
};

function mockChain(symbol: string): OptionChain {
  // Keep minimal shape aligned to DTOs; render uses only a subset for now.
  const strikes = Array.from({length: 13}).map((_, i) => 14000 + i * 50);
  const ce = strikes.map((strike, i) => ({
    strike,
    oi: 1000 + i * 120,
    changeOi: 50 + i * 7,
    iv: 12 + (i % 6) * 0.6,
    ltp: 120 + i * 4.1,
  }));
  const pe = strikes.map((strike, i) => ({
    strike,
    oi: 980 + i * 110,
    changeOi: -30 + i * 5,
    iv: 11 + (i % 7) * 0.55,
    ltp: 115 + i * 3.9,
  }));

  // @ts-expect-error - mock-only until full DTO adoption in Phase 1
  return {symbol, expiry: '2026-06-05', ce, pe, strikes};
}

export function OptionChainTable() {
  const [symbol] = useState('NIFTY');
  const chain = useMemo(() => mockChain(symbol), [symbol]);

  const rows: Row[] = useMemo(() => {
    const strikes: number[] = (chain as any).strikes ?? [];
    const ce = (chain as any).ce ?? [];
    const pe = (chain as any).pe ?? [];
    return strikes.map((strike, i) => {
      const ceRow = ce[i] ?? {oi: 0, changeOi: 0, iv: 0, ltp: 0};
      const peRow = pe[i] ?? {oi: 0, changeOi: 0, iv: 0, ltp: 0};
      return {
        strike,
        ce: {oi: ceRow.oi, chgOi: ceRow.changeOi, iv: ceRow.iv, ltp: ceRow.ltp},
        pe: {oi: peRow.oi, chgOi: peRow.changeOi, iv: peRow.iv, ltp: peRow.ltp},
      };
    });
  }, [chain]);

  return (
    <div className="w-full">
      <div className="flex items-center justify-between px-2 py-2">
        <div className="text-[9px] text-[#71717a] font-bold uppercase tracking-wider">Expiry</div>
        <div className="text-[10px] text-zinc-200">Mock</div>
      </div>

      <div className="overflow-auto border border-[#1c1c1e] rounded-xs">
        <table className="min-w-[620px] w-full border-collapse">
          <thead>
            <tr className="bg-[#0b0b0e] text-[#71717a]">
              <th className="text-[10px] font-bold py-2 px-2 border-b border-[#1c1c1e]">CE</th>
              <th className="text-[10px] font-bold py-2 px-2 border-b border-[#1c1c1e]">Strike</th>
              <th className="text-[10px] font-bold py-2 px-2 border-b border-[#1c1c1e]">PE</th>
            </tr>
            <tr className="bg-[#0b0b0e] text-[#71717a]">
              <th className="text-[9px] font-bold py-1 px-2 border-b border-[#1c1c1e]">OI / ChgOI / IV</th>
              <th className="text-[9px] font-bold py-1 px-2 border-b border-[#1c1c1e]"></th>
              <th className="text-[9px] font-bold py-1 px-2 border-b border-[#1c1c1e]">OI / ChgOI / IV</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((r) => {
              const ceUp = r.ce.chgOi >= 0;
              const peUp = r.pe.chgOi >= 0;
              return (
                <tr key={r.strike} className="text-zinc-200 text-[10px]">
                  <td className="py-1 px-2 border-b border-[#1c1c1e]">
                    <div className="flex items-center justify-between gap-2">
                      <span className="font-bold">OI {r.ce.oi.toLocaleString()}</span>
                      <span className={ceUp ? 'text-[#10b981]' : 'text-[#ef4444]'}>{ceUp ? '+' : ''}{r.ce.chgOi}</span>
                      <span className="text-[#71717a]">IV {r.ce.iv.toFixed(2)}</span>
                    </div>
                  </td>
                  <td className="py-1 px-2 border-b border-[#1c1c1e] text-center text-[#e4e4e7] font-extrabold">
                    {r.strike}
                  </td>
                  <td className="py-1 px-2 border-b border-[#1c1c1e]">
                    <div className="flex items-center justify-between gap-2">
                      <span className="font-bold">OI {r.pe.oi.toLocaleString()}</span>
                      <span className={peUp ? 'text-[#10b981]' : 'text-[#ef4444]'}>{peUp ? '+' : ''}{r.pe.chgOi}</span>
                      <span className="text-[#71717a]">IV {r.pe.iv.toFixed(2)}</span>
                    </div>
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
    </div>
  );
}
