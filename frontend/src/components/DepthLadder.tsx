import type {MarketDepthState} from '@/dto/types';

interface DepthLadderProps {
  depth: MarketDepthState | null;
}

export function DepthLadder({depth}: DepthLadderProps) {
  if (!depth) {
    return (
      <div className="text-[9px] text-zinc-600 text-center py-4 font-mono">No depth data</div>
    );
  }

  const maxLevels = 5;
  const asks = [...depth.asks].reverse().slice(0, maxLevels);
  const bids = depth.bids.slice(0, maxLevels);

  return (
    <div className="font-mono text-[9px] p-2">
      <div className="text-[8px] text-zinc-500 uppercase tracking-wider mb-2">{depth.symbol} Depth</div>
      {asks.map((l, i) => (
        <div key={`a-${i}`} className="flex justify-between text-[#ef4444]/80 py-0.5">
          <span>₹{(l.pricePaisa / 100).toFixed(2)}</span>
          <span>{l.quantity}</span>
        </div>
      ))}
      <div className="border-y border-zinc-800 my-1 py-0.5 text-center text-zinc-500 text-[8px]">SPREAD</div>
      {bids.map((l, i) => (
        <div key={`b-${i}`} className="flex justify-between text-[#10b981]/80 py-0.5">
          <span>₹{(l.pricePaisa / 100).toFixed(2)}</span>
          <span>{l.quantity}</span>
        </div>
      ))}
    </div>
  );
}
