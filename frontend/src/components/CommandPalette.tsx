import {useEffect, useRef, useState} from 'react';
import {useStudioStore} from '@/store/useStudioStore';

interface Command {
  id: string;
  label: string;
  description: string;
  action: () => void;
}

export function CommandPalette() {
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState('');
  const [selectedIndex, setSelectedIndex] = useState(0);
  const inputRef = useRef<HTMLInputElement>(null);
  const symbols = useStudioStore((s) => s.symbols);
  const selectSymbol = useStudioStore((s) => s.selectSymbol);
  const toggleSidebar = useStudioStore((s) => s.toggleSidebar);
  const setActiveView = useStudioStore((s) => s.setActiveView);

  useEffect(() => {
    const handler = (e: KeyboardEvent) => {
      if ((e.metaKey || e.ctrlKey) && e.code === 'KeyK') {
        e.preventDefault();
        setOpen((o) => !o);
      }
      if (e.code === 'Escape' && open) {
        setOpen(false);
      }
    };
    window.addEventListener('keydown', handler);
    return () => window.removeEventListener('keydown', handler);
  }, [open]);

  useEffect(() => {
    if (open) {
      setQuery('');
      setSelectedIndex(0);
      setTimeout(() => inputRef.current?.focus(), 50);
    }
  }, [open]);

  const getCommands = (): Command[] => {
    const q = query.toLowerCase().trim();
    const cmds: Command[] = [];

    // Symbol search
    const matchingSymbols = q
      ? symbols.filter((s) => s.symbol.toLowerCase().includes(q)).slice(0, 8)
      : symbols.slice(0, 5);

    matchingSymbols.forEach((s) => {
      cmds.push({
        id: `sym-${s.symbol}`,
        label: `/symbol ${s.symbol}`,
        description: `${s.name || s.symbol} · ${s.exchangeSegment}`,
        action: () => selectSymbol(s.symbol, s.exchangeSegment),
      });
    });

    cmds.push(
      {id: 'toggle-sidebar', label: '/toggle sidebar', description: 'Show/hide left sidebar', action: () => toggleSidebar()},
      {id: 'view-chart', label: '/view chart', description: 'Switch to chart view', action: () => setActiveView('chart')},
      {id: 'view-scanner', label: '/view scanner', description: 'Switch to scanner view', action: () => setActiveView('scanner')},
      {id: 'view-pipeline', label: '/view pipeline', description: 'Switch to pipeline view', action: () => setActiveView('pipeline')},
      {id: 'view-admin', label: '/view admin', description: 'Switch to admin view', action: () => setActiveView('admin')},
    );

    if (q && !q.startsWith('/')) {
      return cmds.filter((c) => c.label.toLowerCase().includes(q) || c.description.toLowerCase().includes(q));
    }
    return cmds.filter((c) => !q || c.label.toLowerCase().includes(q));
  };

  const commands = getCommands();

  const execute = (cmd: Command) => {
    cmd.action();
    setOpen(false);
  };

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.code === 'ArrowDown') {
      e.preventDefault();
      setSelectedIndex((i) => Math.min(i + 1, commands.length - 1));
    } else if (e.code === 'ArrowUp') {
      e.preventDefault();
      setSelectedIndex((i) => Math.max(i - 1, 0));
    } else if (e.code === 'Enter') {
      e.preventDefault();
      if (commands[selectedIndex]) execute(commands[selectedIndex]);
    }
  };

  if (!open) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-start justify-center pt-[15vh]" onClick={() => setOpen(false)}>
      <div className="w-full max-w-lg bg-[#0e0e11] border border-[#1c1c1e] rounded-sm shadow-2xl overflow-hidden" onClick={(e) => e.stopPropagation()}>
        <div className="flex items-center border-b border-[#1c1c1e] px-3">
          <span className="text-zinc-500 text-[10px] font-mono mr-2">⌘</span>
          <input
            ref={inputRef}
            type="text"
            name="command"
            value={query}
            onChange={(e) => {setQuery(e.target.value); setSelectedIndex(0)}}
            onKeyDown={handleKeyDown}
            placeholder="Type a command or symbol..."
            aria-label="Search commands and symbols"
            className="flex-1 h-9 bg-transparent text-zinc-200 text-[11px] font-mono outline-none placeholder:text-zinc-600"
          />
        </div>
        <div className="max-h-64 overflow-y-auto">
          {commands.map((cmd, i) => (
            <div
              key={cmd.id}
              onClick={() => execute(cmd)}
              className={`flex items-center justify-between px-3 py-2 cursor-pointer text-[10px] font-mono transition ${
                i === selectedIndex ? 'bg-[#00d2ff]/10 border-l-2 border-[#00d2ff]' : 'hover:bg-[#18181b]'
              }`}
            >
              <div>
                <span className="text-zinc-200 font-bold">{cmd.label}</span>
                <span className="text-zinc-600 ml-2">{cmd.description}</span>
              </div>
            </div>
          ))}
          {commands.length === 0 && (
            <div className="text-zinc-600 text-[10px] text-center py-6 font-mono">No matching commands</div>
          )}
        </div>
      </div>
    </div>
  );
}
