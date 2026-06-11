import React, { useState, useEffect, useMemo } from "react";

interface WatchlistItem {
  symbol: string;
  exchange: string;
  ltp: number;
  change: number;
}

interface WatchlistPanelProps {
  onSelectSymbol: (symbol: string, exchange: string) => void;
  currentSymbol: string;
  segment: string;
}

const DEFAULT_WATCHLIST: { symbol: string; exchange: string }[] = [
  { symbol: "RELIANCE", exchange: "NSE" },
  { symbol: "TCS", exchange: "NSE" },
  { symbol: "INFY", exchange: "NSE" },
  { symbol: "HDFCBANK", exchange: "NSE" },
  { symbol: "SBIN", exchange: "NSE" },
  { symbol: "GOLD", exchange: "MCX" },
];

const safeNum = (v: any, fb = 0): number => typeof v === "number" && isFinite(v) ? v : fb;

function clampChange(change: number, exchange: string): number {
  const maxChange = exchange === "MCX" ? 1.5 : (exchange === "NSE" || exchange === "BSE") ? 2.0 : 3.0;
  return Math.max(-maxChange, Math.min(maxChange, change));
}

export default function WatchlistPanel({ onSelectSymbol, currentSymbol, segment }: WatchlistPanelProps) {
  const [items, setItems] = useState<WatchlistItem[]>(() => {
    const MCX_SYMBOLS = ["GOLD", "SILVER", "CRUDEOIL", "NATURALGAS"];
    try {
      const saved = localStorage.getItem("tj_watchlist");
      if (saved) {
        const parsed = JSON.parse(saved);
        if (Array.isArray(parsed) && parsed.length > 0) {
          return parsed.map((s: any) => {
            const item = typeof s === "string"
              ? { symbol: s, exchange: "NSE", ltp: 0, change: 0 }
              : s;
            // Fix known MCX instruments that may have stale exchange values
            if (MCX_SYMBOLS.includes(item.symbol)) {
              item.exchange = "MCX";
            }
            return item;
          });
        }
      }
    } catch {}
    return DEFAULT_WATCHLIST.map(s => ({ ...s, ltp: 0, change: 0 }));
  });
  const [addSymbol, setAddSymbol] = useState("");
  const [showAdd, setShowAdd] = useState(false);

  useEffect(() => {
    localStorage.setItem("tj_watchlist", JSON.stringify(items.map(i => ({ symbol: i.symbol, exchange: i.exchange }))));
  }, [items]);

  useEffect(() => {
    const pollAll = async () => {
      const updated = await Promise.all(items.map(async (item) => {
        try {
          const itemSegment = ({ NSE: "NSE_EQ", BSE: "BSE_EQ", NFO: "NSE_FNO", MCX: "MCX_COMM", CDS: "NSE_CURRENCY" } as Record<string, string>)[item.exchange] || segment;
          const res = await fetch(`/api/v1/market/ltp?symbol=${item.symbol}&exchangeSegment=${itemSegment}`);
          if (!res.ok) return item;
          const data = await res.json();
          const ltp = data.ltpPaisa / 100;
          const rawChange = item.ltp > 0 ? ((ltp - item.ltp) / item.ltp) * 100 : 0;
          const change = clampChange(rawChange, item.exchange);
          return { ...item, ltp, change };
        } catch { return item; }
      }));
      setItems(updated);
    };
    pollAll();
    const iv = setInterval(pollAll, 5000);
    return () => clearInterval(iv);
  }, [segment, items.length]);

  const segmentToExchange: Record<string, string> = {
    NSE_EQ: "NSE", BSE_EQ: "BSE", NSE_FNO: "NFO", MCX_COMM: "MCX", NSE_CURRENCY: "CDS",
  };

  const grouped = useMemo(() => {
    const groups: Record<string, WatchlistItem[]> = {};
    for (const item of items) {
      const ex = item.exchange || "NSE";
      if (!groups[ex]) groups[ex] = [];
      groups[ex].push(item);
    }
    return groups;
  }, [items]);

  const handleAdd = () => {
    const sym = addSymbol.trim().toUpperCase();
    const inferredExchange = segmentToExchange[segment] || "NSE";
    if (sym && !items.find(i => i.symbol === sym && i.exchange === inferredExchange)) {
      setItems([...items, { symbol: sym, exchange: inferredExchange, ltp: 0, change: 0 }]);
    }
    setAddSymbol("");
    setShowAdd(false);
  };

  const handleRemove = (sym: string, exchange: string) => {
    setItems(items.filter(i => !(i.symbol === sym && i.exchange === exchange)));
  };

  return (
    <div className="bg-[#0d1117] border border-[#21262d] rounded-lg flex flex-col h-full font-mono text-[10px]">
      <div className="flex items-center justify-between px-2 py-1 border-b border-[#21262d]">
        <span className="font-bold text-slate-300 text-[9px] uppercase tracking-wider">Watchlist</span>
        <button onClick={() => setShowAdd(!showAdd)}
          className="text-[#f0b429] font-bold text-[10px] hover:text-[#f0b429]/80 cursor-pointer">+</button>
      </div>
      {showAdd && (
        <div className="flex gap-1 px-2 py-1 border-b border-[#21262d]">
          <input value={addSymbol} onChange={e => setAddSymbol(e.target.value)}
            onKeyDown={e => e.key === "Enter" && handleAdd()}
            placeholder="Symbol..." autoFocus
            className="flex-1 bg-[#161b22] border border-[#21262d] text-slate-100 text-[10px] px-1.5 py-0.5 rounded outline-none" />
          <button onClick={handleAdd} className="text-[#26a69a] font-bold text-[10px] cursor-pointer">Add</button>
        </div>
      )}
      <div className="flex-1 overflow-y-auto">
        {Object.entries(grouped).map(([exchange, groupItems]) => (
          <div key={exchange}>
            <div className="px-2 py-0.5 text-[8px] text-slate-600 font-bold uppercase bg-[#161b22] border-b border-[#21262d]/30 sticky top-0">
              {exchange}
            </div>
            {(groupItems as WatchlistItem[]).map(item => (
              <div key={`${item.exchange}-${item.symbol}`}
                onClick={() => onSelectSymbol(item.symbol, item.exchange)}
                className={`flex items-center justify-between px-2 py-1 cursor-pointer hover:bg-[#161b22] group ${
                  currentSymbol === item.symbol ? "bg-[#f0b429]/10 border-l-2 border-[#f0b429]" : "border-l-2 border-transparent"
                }`}>
                <div className="flex flex-col">
                  <div className="flex items-center gap-1">
                    <span className="font-bold text-slate-200">{item.symbol}</span>
                    <span className={`text-[7px] font-bold px-0.5 py-px rounded ${
                      item.exchange === "MCX" ? "bg-[#f0b429]/20 text-[#f0b429]" :
                      item.exchange === "NFO" ? "bg-[#a78bfa]/20 text-[#a78bfa]" :
                      "bg-[#3b82f6]/20 text-[#3b82f6]"
                    }`}>{item.exchange}</span>
                  </div>
                </div>
                <div className="flex items-center gap-2">
                  <div className="text-right">
                    <div className="font-bold text-slate-200">
                      {item.ltp > 0 ? safeNum(item.ltp).toLocaleString("en-IN", { minimumFractionDigits: 2, maximumFractionDigits: 2 }) : "\u2014"}
                    </div>
                    <div className={`text-[8px] font-bold ${item.change >= 0 ? "text-[#26a69a]" : "text-[#ef5350]"}`}>
                      {item.change !== 0 ? `${item.change >= 0 ? "+" : ""}${safeNum(item.change).toFixed(2)}%` : ""}
                    </div>
                  </div>
                  <button onClick={(e) => { e.stopPropagation(); handleRemove(item.symbol, item.exchange); }}
                    className="text-slate-600 hover:text-[#ef5350] opacity-0 group-hover:opacity-100 cursor-pointer text-[9px]">&times;</button>
                </div>
              </div>
            ))}
          </div>
        ))}
      </div>
    </div>
  );
}
