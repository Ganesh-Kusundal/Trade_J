export function OrdersPanel() {
  return (
    <div className="h-full w-full flex flex-col">
      <div className="h-11 px-3 flex items-center justify-between border-b border-[#1c1c1e]">
        <div className="text-[11px] font-black uppercase tracking-widest text-white">Orders</div>
        <div className="text-[9px] text-[#71717a]">Mock</div>
      </div>
      <div className="flex-1 p-3 text-[11px] text-[#71717a]">No orders (Phase 1 placeholder).</div>
    </div>
  );
}
