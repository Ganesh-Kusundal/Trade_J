import React, { useState, useEffect } from "react";

interface NewsItem {
  id: string;
  headline: string;
  source: string;
  timestamp: number;
  sentiment: "positive" | "negative" | "neutral";
  symbols: string[];
}

interface NewsFeedProps {
  symbol: string;
}

interface NewsResponse {
  news?: Array<{
    id?: string;
    headline?: string;
    source?: string;
    timestamp?: number;
    sentiment?: string;
    symbols?: string[];
  }>;
}

const SENTIMENT_COLORS: Record<string, string> = {
  positive: "text-emerald-400",
  negative: "text-rose-400",
  neutral: "text-slate-400",
};
const SENTIMENT_ICONS: Record<string, string> = { positive: "▲", negative: "▼", neutral: "●" };

function timeAgo(ts: number): string {
  const mins = Math.floor((Date.now() - ts) / 60000);
  if (mins < 60) return `${mins}m ago`;
  const hours = Math.floor(mins / 60);
  if (hours < 24) return `${hours}h ago`;
  return `${Math.floor(hours / 24)}d ago`;
}

export default function NewsFeed({ symbol }: NewsFeedProps) {
  const [news, setNews] = useState<NewsItem[]>([]);
  const [status, setStatus] = useState<"loading" | "ready" | "empty" | "error">("loading");

  useEffect(() => {
    let cancelled = false;
    setStatus("loading");
    fetch(`/api/v1/news?symbol=${encodeURIComponent(symbol)}`)
      .then((r) => r.ok ? r.json() : Promise.reject(new Error(`HTTP ${r.status}`)))
      .then((data: NewsResponse) => {
        if (cancelled) return;
        const items: NewsItem[] = (data.news ?? []).map((n, i) => ({
          id: String(n.id ?? `news-${i}`),
          headline: String(n.headline ?? ""),
          source: String(n.source ?? ""),
          timestamp: Number(n.timestamp ?? Date.now()),
          sentiment: (n.sentiment === "positive" || n.sentiment === "negative") ? n.sentiment : "neutral",
          symbols: Array.isArray(n.symbols) ? n.symbols.map(String) : [symbol],
        }));
        setNews(items);
        setStatus(items.length > 0 ? "ready" : "empty");
      })
      .catch(() => { if (!cancelled) setStatus("error"); });
    return () => { cancelled = true; };
  }, [symbol]);

  return (
    <div className="bg-[#0d1117] border border-[#21262d] rounded-lg flex flex-col h-full font-mono text-[10px]">
      <div className="flex items-center justify-between px-2 py-1 border-b border-[#21262d]">
        <span className="font-bold text-slate-300 text-[9px] uppercase tracking-wider">News</span>
        <span className="text-[8px] text-slate-500">{symbol}</span>
      </div>
      <div className="flex-1 overflow-y-auto">
        {status === "loading" && (
          <div className="flex items-center justify-center h-full text-slate-600 text-[10px]">Loading…</div>
        )}
        {status === "empty" && (
          <div className="flex items-center justify-center h-full text-slate-600 text-[10px]">No news for {symbol}</div>
        )}
        {status === "error" && (
          <div className="flex items-center justify-center h-full text-amber-500 text-[10px]">News feed unavailable</div>
        )}
        {status === "ready" && news.map((item) => (
          <div key={item.id} className="px-2 py-1.5 border-b border-[#21262d]/30 hover:bg-[#161b22] cursor-pointer">
            <div className="flex items-start gap-1.5">
              <span className={`text-[9px] mt-0.5 ${SENTIMENT_COLORS[item.sentiment] ?? "text-slate-400"}`}>
                {SENTIMENT_ICONS[item.sentiment] ?? "●"}
              </span>
              <div className="flex-1 min-w-0">
                <div className="text-slate-200 text-[10px] leading-tight">{item.headline}</div>
                <div className="flex items-center gap-2 mt-0.5">
                  <span className="text-[8px] text-slate-500">{item.source}</span>
                  <span className="text-[8px] text-slate-600">{timeAgo(item.timestamp)}</span>
                </div>
              </div>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
