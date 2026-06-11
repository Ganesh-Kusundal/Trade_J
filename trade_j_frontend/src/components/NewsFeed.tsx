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

// Generate mock news based on symbol (until real news API is integrated)
function generateMockNews(symbol: string): NewsItem[] {
  const templates = [
    { headline: `${symbol} reports strong quarterly earnings, beats estimates`, sentiment: "positive" as const },
    { headline: `${symbol} announces new partnership for expansion`, sentiment: "positive" as const },
    { headline: `Analysts upgrade ${symbol} to 'Buy' rating`, sentiment: "positive" as const },
    { headline: `${symbol} faces regulatory scrutiny over compliance`, sentiment: "negative" as const },
    { headline: `Market volatility impacts ${symbol} trading volumes`, sentiment: "neutral" as const },
    { headline: `${symbol} declares dividend of Rs 10 per share`, sentiment: "positive" as const },
    { headline: `Foreign investors increase stake in ${symbol}`, sentiment: "positive" as const },
    { headline: `${symbol} plans Rs 5000 Cr capex for FY27`, sentiment: "neutral" as const },
  ];
  const now = Date.now();
  return templates.slice(0, 6).map((t, i) => ({
    id: `news-${i}`,
    headline: t.headline,
    source: ["Reuters", "Bloomberg", "ET Markets", "Moneycontrol", "CNBC", "LiveMint"][i % 6],
    timestamp: now - (i * 3600000 + Math.random() * 1800000),
    sentiment: t.sentiment,
    symbols: [symbol],
  }));
}

export default function NewsFeed({ symbol }: NewsFeedProps) {
  const [news, setNews] = useState<NewsItem[]>([]);

  useEffect(() => {
    setNews(generateMockNews(symbol));
  }, [symbol]);

  const sentimentColor = (s: string) => {
    switch (s) {
      case "positive": return "text-[#26a69a]";
      case "negative": return "text-[#ef5350]";
      default: return "text-slate-400";
    }
  };

  const sentimentIcon = (s: string) => {
    switch (s) {
      case "positive": return "▲";
      case "negative": return "▼";
      default: return "●";
    }
  };

  const timeAgo = (ts: number) => {
    const mins = Math.floor((Date.now() - ts) / 60000);
    if (mins < 60) return `${mins}m ago`;
    const hours = Math.floor(mins / 60);
    if (hours < 24) return `${hours}h ago`;
    return `${Math.floor(hours / 24)}d ago`;
  };

  return (
    <div className="bg-[#0d1117] border border-[#21262d] rounded-lg flex flex-col h-full font-mono text-[10px]">
      <div className="flex items-center justify-between px-2 py-1 border-b border-[#21262d]">
        <span className="font-bold text-slate-300 text-[9px] uppercase tracking-wider">News</span>
        <span className="text-[8px] text-slate-500">{symbol}</span>
      </div>
      <div className="flex-1 overflow-y-auto">
        {news.length === 0 ? (
          <div className="flex items-center justify-center h-full text-slate-600 text-[10px]">
            No news available
          </div>
        ) : (
          news.map(item => (
            <div key={item.id} className="px-2 py-1.5 border-b border-[#21262d]/30 hover:bg-[#161b22] cursor-pointer">
              <div className="flex items-start gap-1.5">
                <span className={`text-[9px] mt-0.5 ${sentimentColor(item.sentiment)}`}>
                  {sentimentIcon(item.sentiment)}
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
          ))
        )}
      </div>
    </div>
  );
}
