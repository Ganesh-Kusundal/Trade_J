import { useState, useEffect } from "react";

export interface WatchlistItem {
  symbol: string;
  exchange: string;
  ltp: number;
  change: number;
}

const DEFAULT_WATCHLIST: { symbol: string; exchange: string }[] = [
  { symbol: "RELIANCE", exchange: "NSE" },
  { symbol: "TCS", exchange: "NSE" },
  { symbol: "INFY", exchange: "NSE" },
  { symbol: "HDFCBANK", exchange: "NSE" },
  { symbol: "SBIN", exchange: "NSE" },
  { symbol: "GOLD", exchange: "MCX" },
];

const MCX_SYMBOLS = ["GOLD", "SILVER", "CRUDEOIL", "NATURALGAS"];

function clampChange(change: number, exchange: string): number {
  const maxChange = exchange === "MCX" ? 1.5 : (exchange === "NSE" || exchange === "BSE") ? 2.0 : 3.0;
  return Math.max(-maxChange, Math.min(maxChange, change));
}

function loadSaved(): { symbol: string; exchange: string }[] {
  try {
    const saved = localStorage.getItem("tj_watchlist");
    if (saved) {
      const parsed = JSON.parse(saved);
      if (Array.isArray(parsed) && parsed.length > 0) {
        return parsed.map((s: any) => {
          const item = typeof s === "string"
            ? { symbol: s, exchange: "NSE" }
            : s;
          if (MCX_SYMBOLS.includes(item.symbol)) item.exchange = "MCX";
          return item;
        });
      }
    }
  } catch {}
  return DEFAULT_WATCHLIST;
}

export function useWatchlist(defaultSegment = "NSE_EQ") {
  const [items, setItems] = useState<WatchlistItem[]>(() =>
    loadSaved().map(s => ({ ...s, ltp: 0, change: 0 }))
  );

  useEffect(() => {
    localStorage.setItem("tj_watchlist", JSON.stringify(items.map(i => ({ symbol: i.symbol, exchange: i.exchange }))));
  }, [items]);

  useEffect(() => {
    const segmentToExchange: Record<string, string> = {
      NSE_EQ: "NSE", BSE_EQ: "BSE", NSE_FNO: "NFO", MCX_COMM: "MCX", NSE_CURRENCY: "CDS",
    };

    const pollAll = async () => {
      const { marketApi } = await import("../generated/api");
      const updated = await Promise.all(items.map(async (item) => {
        try {
          const itemSegment = ({ NSE: "NSE_EQ", BSE: "BSE_EQ", NFO: "NSE_FNO", MCX: "MCX_COMM", CDS: "NSE_CURRENCY" } as Record<string, string>)[item.exchange] || segmentToExchange[defaultSegment] || "NSE_EQ";
          const data = await marketApi.ltp(item.symbol, itemSegment);
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
  }, [defaultSegment, items.length]);

  const addItem = (symbol: string, exchange: string) => {
    if (!items.find(i => i.symbol === symbol && i.exchange === exchange)) {
      setItems([...items, { symbol, exchange, ltp: 0, change: 0 }]);
    }
  };

  const removeItem = (symbol: string, exchange: string) => {
    setItems(items.filter(i => !(i.symbol === symbol && i.exchange === exchange)));
  };

  return { items, addItem, removeItem };
}
