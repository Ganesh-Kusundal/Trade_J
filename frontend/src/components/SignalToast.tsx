import type {StrategySignalToast} from '@/dto/types';

interface SignalToastProps {
  signals: StrategySignalToast[];
}

export function SignalToast({signals}: SignalToastProps) {
  if (signals.length === 0) return null;

  return (
    <div className="absolute top-14 right-4 z-30 flex flex-col gap-1 max-w-[220px]">
      {signals.slice(-3).map((s) => (
        <div
          key={`${s.signalId}-${s.timestamp}`}
          className="bg-[#09090b]/95 border border-zinc-800 rounded-xs px-3 py-2 text-[9px] font-mono shadow-lg"
        >
          <div className="text-[#00d2ff] font-bold uppercase">{s.type.replace(/_/g, ' ')}</div>
          <div className="text-zinc-300">{s.symbol} {s.side && `· ${s.side}`}</div>
          {s.setup && <div className="text-zinc-500">{s.setup}</div>}
        </div>
      ))}
    </div>
  );
}
