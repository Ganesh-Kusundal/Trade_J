export function LogsPanel() {
  return (
    <div className="h-full w-full flex flex-col">
      <div className="h-11 px-3 flex items-center justify-between border-b border-[#1c1c1e]">
        <div className="text-[11px] font-black uppercase tracking-widest text-white">Logs</div>
        <div className="text-[9px] text-[#71717a]">Mock</div>
      </div>
      <div className="flex-1 p-3 overflow-auto">
        <div className="text-[11px] text-[#71717a] leading-relaxed">
          Phase 1: log stream will be wired to WebSocket/Replay events later.
        </div>
        <div className="mt-3 space-y-2">
          {['Bootstrapping terminal UI...', 'Loading mocks...', 'Ready.'].map((t) => (
            <div key={t} className="text-[10px] text-zinc-400 font-mono">
              {t}
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}
