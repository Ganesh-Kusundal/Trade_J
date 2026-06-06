import {CandlestickChart} from '@/ui/widgets/charts/CandlestickChart';

export function ChartPanel() {
  return (
    <div className="h-full w-full flex flex-col">
      <div className="h-11 px-3 flex items-center justify-between border-b border-[#1c1c1e]">
        <div className="text-[11px] font-black uppercase tracking-widest text-white">Charts</div>
        <div className="text-[9px] text-[#71717a]">Mock data</div>
      </div>
      <div className="flex-1 p-2">
        <CandlestickChart />
      </div>
    </div>
  );
}
