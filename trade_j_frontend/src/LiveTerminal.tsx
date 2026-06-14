import React, { useEffect, useMemo, useState } from "react";
import { Search, Activity, Settings as SettingsIcon } from "lucide-react";
import { placeOrder } from "./api/orders";
import { fetchSymbols } from "./api/marketData";
import { fetchBrokers } from "./api/brokerRegistry";
import type { ExchangeSegment, Side, OrderType, ProductType, Validity } from "./api/backend-contracts";
import type { Symbol } from "./api/backend-contracts";
import type { BrokerInfo } from "./api/brokerRegistry";
import {
  marketStore, setSymbol, setMode, applyTick, setCandles,
  useMarket, applyDepth, applyFill,
} from "./store/marketStore";
import { useOrders, ordersStore, applyOrder } from "./store/ordersStore";
import { useHealth, applyHealth } from "./store/healthStore";
import { startReadModelStream } from "./store/readModelStream";
import CandlestickChart from "./components/CandlestickChart";
import OrderBook from "./components/OrderBook";
import TradesList from "./components/TradesList";
import WatchlistPanel from "./components/WatchlistPanel";
import MarketOverview from "./components/MarketOverview";
import PortfolioPanel from "./components/PortfolioPanel";
import PriceAlerts from "./components/PriceAlerts";
import SettingsPanel from "./components/SettingsPanel";
import RiskCalculator from "./components/RiskCalculator";
import NewsFeed from "./components/NewsFeed";
import ErrorBoundary from "./components/ErrorBoundary";
import { resolveInstrument } from "./domain/instrument";
import { fetchSession } from "./api/marketSession";

const EXCHANGE_MAP: Record<string, ExchangeSegment> = {
  NSE: "NSE_EQ", BSE: "BSE_EQ", NFO: "NSE_FNO", MCX: "MCX_COMM", CDS: "NSE_CURRENCY",
} as const;

const DEFAULT_SYMBOLS: Record<string, string> = {
  NSE: "RELIANCE", BSE: "RELIANCE", NFO: "NIFTY", MCX: "GOLD", CDS: "USDINR",
};

const TIMEFRAMES = ["1m", "5m", "15m", "1h", "4h", "1d"] as const;
const DEFAULT_DAYS: Record<string, number> = { "1m": 5, "5m": 15, "15m": 30, "1h": 90, "4h": 180, "1d": 750 };

function safeNum(v: unknown, fb = 0): number {
  return typeof v === "number" && isFinite(v) ? v : fb;
}

export default function LiveTerminal() {
  const market = useMarket((s) => s);
  const orders = useOrders((s) => s);
  const health = useHealth((s) => s);

  const [broker, setBroker] = useState(() => localStorage.getItem("tj_broker") || "DHAN");
  const [exchange, setExchangeState] = useState(() => localStorage.getItem("tj_exchange") || "NSE");
  const [symbolState, setSymbolState] = useState(() => localStorage.getItem("tj_symbol") || "RELIANCE");
  const [timeframe, setTimeframe] = useState<typeof TIMEFRAMES[number]>(() => {
    const v = localStorage.getItem("tj_timeframe");
    return (TIMEFRAMES as readonly string[]).includes(v ?? "") ? (v as typeof TIMEFRAMES[number]) : "1m";
  });
  const [searchOpen, setSearchOpen] = useState(false);
  const [searchQuery, setSearchQuery] = useState("");
  const [symbols, setSymbols] = useState<Symbol[]>([]);
  const [brokers, setBrokers] = useState<BrokerInfo[]>([]);
  const [showSettings, setShowSettings] = useState(false);
  const [bottomTab, setBottomTab] = useState<"watchlist" | "orders" | "alerts" | "risk" | "news">("watchlist");
  const [orderPanelOpen, setOrderPanelOpen] = useState(false);
  const [orderSide, setOrderSide] = useState<Side>("BUY");
  const [orderQty, setOrderQty] = useState("1");
  const [orderType, setOrderType] = useState<OrderType>("LIMIT");
  const [orderPrice, setOrderPrice] = useState("");
  const [orderStatus, setOrderStatus] = useState("");
  const [marketStateText, setMarketStateText] = useState("UNKNOWN");

  const segment = EXCHANGE_MAP[exchange] ?? "NSE_EQ";
  const instrument = useMemo(() => resolveInstrument(symbolState, exchange), [symbolState, exchange]);

  useEffect(() => {
    localStorage.setItem("tj_broker", broker);
    localStorage.setItem("tj_exchange", exchange);
    localStorage.setItem("tj_symbol", symbolState);
    localStorage.setItem("tj_timeframe", timeframe);
  }, [broker, exchange, symbolState, timeframe]);

  useEffect(() => startReadModelStream(), []);

  useEffect(() => {
    setSymbol(symbolState, exchange, segment);
    const tfDays = DEFAULT_DAYS[timeframe] ?? 365;
    const to = new Date().toISOString().split("T")[0];
    const from = new Date(Date.now() - tfDays * 86400000).toISOString().split("T")[0];
    fetch(`/api/v1/market/historical/candles?symbol=${encodeURIComponent(symbolState)}&exchangeSegment=${segment}&interval=${timeframe}&from=${from}&to=${to}&source=broker`)
      .then((r) => r.json())
      .then((data: { candles?: Array<{ startTimeMs: number; openPaisa: number; highPaisa: number; lowPaisa: number; closePaisa: number; volume: number }> }) => {
        const candles = (data.candles ?? []).map((c) => ({
          time: Math.floor(c.startTimeMs / 1000),
          open: c.openPaisa / 100,
          high: c.highPaisa / 100,
          low: c.lowPaisa / 100,
          close: c.closePaisa / 100,
          volume: c.volume,
        }));
        setCandles(timeframe, candles);
      })
      .catch(() => setCandles(timeframe, []));
  }, [symbolState, exchange, segment, timeframe]);

  useEffect(() => {
    const id = setInterval(() => {
      fetch("/actuator/health")
        .then((r) => r.json())
        .then((data: { components?: Record<string, { status: string; details?: Record<string, unknown> }> }) => {
          const brokerComp = data.components?.broker?.details ?? {};
          const md = data.components?.marketData?.details ?? {};
          applyHealth({
            brokerUp: data.components?.broker?.status === "UP",
            websocketConnected: Boolean(brokerComp.websocketConnected),
            broker: String(brokerComp.broker ?? "unknown"),
            marketDataUp: data.components?.marketData?.status === "UP",
            lastTickMs: Number(md.lastTickTimestampMs ?? 0),
          });
        })
        .catch(() => applyHealth({ brokerUp: false, websocketConnected: false }));
    }, 5000);
    return () => clearInterval(id);
  }, []);

  useEffect(() => {
    fetchSession(exchange).then((s) => setMarketStateText(s.state)).catch(() => setMarketStateText("UNKNOWN"));
    const id = setInterval(() => {
      fetchSession(exchange).then((s) => setMarketStateText(s.state)).catch(() => {});
    }, 60000);
    return () => clearInterval(id);
  }, [exchange]);

  useEffect(() => {
    fetchBrokers().then((b) => setBrokers(b.length > 0 ? b : [])).catch(() => setBrokers([]));
  }, []);

  useEffect(() => {
    fetchSymbols(segment).then((r) => setSymbols(r.symbols ?? [])).catch(() => setSymbols([]));
  }, [segment, searchQuery]);

  useEffect(() => {
    const handler = (e: KeyboardEvent) => {
      if ((e.target as HTMLElement).tagName === "INPUT" || (e.target as HTMLElement).tagName === "SELECT") return;
      const tfMap: Record<string, typeof TIMEFRAMES[number]> = { "1": "1m", "2": "5m", "3": "15m", "4": "1h", "5": "4h", "6": "1d" };
      if (tfMap[e.key]) { setTimeframe(tfMap[e.key]); return; }
      switch (e.key.toLowerCase()) {
        case "b": setOrderSide("BUY"); setOrderPanelOpen(true); break;
        case "s": setOrderSide("SELL"); setOrderPanelOpen(true); break;
        case "t": setOrderPanelOpen((p) => !p); break;
        case "escape": setOrderPanelOpen(false); setSearchOpen(false); break;
        case "/": case "f": e.preventDefault(); setSearchOpen(true); break;
      }
    };
    window.addEventListener("keydown", handler);
    return () => window.removeEventListener("keydown", handler);
  }, []);

  const filteredSymbols = useMemo(() => {
    const q = searchQuery.toLowerCase();
    const list = q ? symbols.filter((s) => s.symbol.toLowerCase().includes(q)) : symbols;
    return list.slice(0, 50);
  }, [symbols, searchQuery]);

  const modePill = market.mode === "LIVE"
    ? "bg-emerald-500/15 text-emerald-400"
    : market.mode === "PAPER"
      ? "bg-amber-500/15 text-amber-400"
      : "bg-blue-500/15 text-blue-400";

  const onPlace = async () => {
    setOrderStatus("Placing...");
    try {
      const r = await placeOrder({
        symbol: symbolState, exchangeSegment: segment,
        side: orderSide, quantity: parseInt(orderQty) || 1,
        orderType: orderType,
        pricePaisa: orderType === "LIMIT" ? Math.round(parseFloat(orderPrice || "0") * 100) : 0,
        triggerPricePaisa: null, productType: "CNC" as ProductType, validity: "DAY" as Validity,
      });
      applyOrder({
        orderId: r.orderId, symbol: r.symbol, side: r.side,
        quantity: r.quantity, filledQuantity: r.filledQuantity,
        pricePaisa: r.pricePaisa, status: r.status, orderType: r.orderType,
        correlationId: r.correlationId, rejectionReason: r.rejectionReason,
      });
      setOrderStatus(`Order ${r.orderId} ${r.status}`);
    } catch (e: unknown) {
      const msg = e instanceof Error ? e.message : String(e);
      setOrderStatus(`Error: ${msg}`);
    }
  };

  return (
    <ErrorBoundary>
      <div className="min-h-screen bg-[#010409] text-[#c9d1d9] flex flex-col font-mono select-none overflow-hidden h-screen">
        <div className="bg-[#0d1117] border-b border-[#21262d] px-3 py-1 flex items-center gap-4 text-[10px] shrink-0">
          <div className="flex items-center gap-1.5">
            <div className="bg-[#f0b429] p-0.5 rounded"><Activity className="w-3 h-3 text-[#0d1117]" /></div>
            <span className="font-black tracking-wider">TRADE_J<span className="text-[#f0b429]">.PRO</span></span>
          </div>
          <span className="text-slate-600">|</span>
          <div className="flex items-center bg-[#161b22] border border-[#21262d] rounded p-0.5">
            <span className="text-[9px] text-slate-500 font-bold px-1.5">BROKER:</span>
            <select value={broker} onChange={(e) => setBroker(e.target.value)} className="bg-transparent text-slate-100 font-bold text-[11px] py-0.5 px-1 outline-none">
              {brokers.length > 0
                ? brokers.filter((b) => b.source !== "SIMULATION").map((b) => <option key={b.source} value={b.source}>{b.displayName}</option>)
                : <><option value="DHAN">Dhan</option><option value="UPSTOX">Upstox</option><option value="ICICI">ICICI Direct</option></>}
            </select>
          </div>
          <span className="text-slate-600">|</span>
          <span className="text-slate-400">EXCHANGE: <span className="font-bold text-slate-200">{exchange}</span></span>
          <span className="text-slate-600">|</span>
          <span className="text-slate-400">MARKET: <span className="font-bold text-slate-200">{marketStateText}</span></span>
          <span className="text-slate-600">|</span>
          <span className="text-slate-400">FEED: <span className={`font-bold ${health.marketDataUp ? "text-emerald-400" : "text-amber-400"}`}>{health.marketDataUp ? "UP" : "DOWN"}</span></span>
          <span className="text-slate-600">|</span>
          <span className={`px-1.5 py-0.5 rounded font-bold text-[9px] ${modePill}`}>{market.mode}</span>
          <div className="flex-1" />
          <button onClick={() => setShowSettings(true)} className="text-slate-400 hover:text-slate-200 hover:bg-[#21262d] px-1 py-0.5 rounded">
            <SettingsIcon className="w-3 h-3" />
          </button>
        </div>

        <header className="bg-[#0d1117] border-b border-[#21262d] px-3 py-1 shrink-0 flex items-center gap-2">
          <div className="flex items-center bg-[#161b22] border border-[#21262d] rounded p-0.5">
            <span className="text-[9px] text-slate-500 font-bold px-1.5">EXCH:</span>
            <select value={exchange} onChange={(e) => { setExchangeState(e.target.value); setSymbolState(DEFAULT_SYMBOLS[e.target.value] ?? ""); }} className="bg-transparent text-slate-100 font-bold text-[11px] py-0.5 px-1 outline-none">
              {Object.keys(EXCHANGE_MAP).map((ex) => <option key={ex} value={ex}>{ex}</option>)}
            </select>
          </div>
          <div className="relative flex items-center bg-[#161b22] border border-[#21262d] rounded p-0.5 min-w-[140px]">
            <span className="text-[9px] text-slate-500 font-bold px-1.5">SYM:</span>
            <button onClick={() => setSearchOpen((p) => !p)} className="bg-transparent text-slate-100 font-black text-[11px] py-0.5 px-1 flex items-center gap-1">
              <span>{symbolState}</span><Search className="w-3 h-3 text-slate-400" />
            </button>
            {searchOpen && (
              <div className="absolute top-full left-0 right-0 bg-[#161b22] border border-[#21262d] rounded shadow-xl z-50 p-2 mt-1">
                <input type="text" placeholder="Search..." value={searchQuery} onChange={(e) => setSearchQuery(e.target.value)} className="w-full bg-[#0d1117] border border-[#21262d] text-slate-100 text-[10px] p-1.5 rounded outline-none mb-1" autoFocus />
                <div className="max-h-[160px] overflow-y-auto space-y-0.5">
                  {filteredSymbols.map((s) => (
                    <button key={s.symbol} onClick={() => { setSymbolState(s.symbol); setSearchOpen(false); setSearchQuery(""); }} className={`w-full text-left py-0.5 px-1.5 rounded text-[10px] hover:bg-[#21262d] ${symbolState === s.symbol ? "text-[#f0b429] bg-[#f0b429]/10" : "text-slate-300"}`}>
                      {s.symbol}
                    </button>
                  ))}
                </div>
              </div>
            )}
          </div>
          <div className="flex-1" />
          <button onClick={() => setOrderPanelOpen((p) => !p)} className="flex items-center gap-1 px-3 py-1 rounded font-black text-[10px] bg-[#f0b429]/15 text-[#f0b429] border border-[#f0b429]/30">
            <Activity className="w-3 h-3" /> TRADE
          </button>
        </header>

        {orderPanelOpen && (
          <div className="bg-[#0d1117] border-b border-[#21262d] px-3 py-1.5 flex items-center gap-3 text-[11px] shrink-0">
            <div className="flex gap-1">
              <button onClick={() => setOrderSide("BUY")} className={`px-3 py-0.5 rounded font-bold text-[10px] ${orderSide === "BUY" ? "bg-emerald-500/20 text-emerald-400 border border-emerald-500/40" : "bg-[#161b22] text-slate-400 border border-[#21262d]"}`}>BUY</button>
              <button onClick={() => setOrderSide("SELL")} className={`px-3 py-0.5 rounded font-bold text-[10px] ${orderSide === "SELL" ? "bg-rose-500/20 text-rose-400 border border-rose-500/40" : "bg-[#161b22] text-slate-400 border border-[#21262d]"}`}>SELL</button>
            </div>
            <span className="text-slate-500 text-[9px]">QTY:</span>
            <input type="number" value={orderQty} onChange={(e) => setOrderQty(e.target.value)} className="bg-[#161b22] border border-[#21262d] text-slate-100 text-[11px] w-14 px-2 py-0.5 rounded outline-none" />
            <div className="flex gap-1">
              <button onClick={() => setOrderType("LIMIT")} className={`px-2 py-0.5 rounded font-bold text-[10px] ${orderType === "LIMIT" ? "bg-amber-500/20 text-amber-400 border border-amber-500/40" : "bg-[#161b22] text-slate-400 border border-[#21262d]"}`}>LIMIT</button>
              <button onClick={() => setOrderType("MARKET")} className={`px-2 py-0.5 rounded font-bold text-[10px] ${orderType === "MARKET" ? "bg-amber-500/20 text-amber-400 border border-amber-500/40" : "bg-[#161b22] text-slate-400 border border-[#21262d]"}`}>MKT</button>
            </div>
            {orderType === "LIMIT" && (
              <>
                <span className="text-slate-500 text-[9px]">PX:</span>
                <input type="number" step="0.05" value={orderPrice} onChange={(e) => setOrderPrice(e.target.value)} placeholder={safeNum(market.ltp).toFixed(2)} className="bg-[#161b22] border border-[#21262d] text-slate-100 text-[11px] w-20 px-2 py-0.5 rounded outline-none" />
              </>
            )}
            <button onClick={onPlace} className="px-4 py-0.5 rounded font-black text-[10px] bg-amber-500 text-[#0d1117]">PLACE</button>
            {orderStatus && <span className={`text-[10px] font-bold ${orderStatus.startsWith("Error") ? "text-rose-400" : "text-emerald-400"}`}>{orderStatus}</span>}
          </div>
        )}

        <main className="flex-1 overflow-hidden grid grid-rows-12 p-1.5 gap-1.5">
          <section className="row-span-8 flex flex-col overflow-hidden min-h-[350px]">
            <CandlestickChart instrument={instrument} timeframe={timeframe} setTimeframe={setTimeframe} bars={market.candles[timeframe] ?? []} loading={false} marketState={marketStateText} ltp={market.ltp} />
          </section>
          <section className="row-span-4 grid grid-cols-1 md:grid-cols-3 gap-1.5 overflow-hidden min-h-[160px]">
            {bottomTab === "watchlist" && <WatchlistPanel onSelectSymbol={(s) => setSymbolState(s)} currentSymbol={symbolState} segment={segment} />}
            {bottomTab === "orders" && <PortfolioPanel segment={segment} refreshTrigger={orders.active.length + orders.completed.length} />}
            {bottomTab === "alerts" && <PriceAlerts currentSymbol={symbolState} currentExchange={exchange} lastPrice={market.ltp} segment={segment} />}
            {bottomTab === "risk" && <RiskCalculator lastPrice={market.ltp} currency={instrument.currency} />}
            {bottomTab === "news" && <NewsFeed symbol={symbolState} />}
            <OrderBook bids={market.bids} asks={market.asks} lastPrice={market.ltp} priceChange={market.priceChangePct} symbol={symbolState} onSelectPrice={(p) => setOrderPrice(safeNum(p).toFixed(2))} priceUnit={instrument.currency} qtyUnit={instrument.volumeUnit} marketState={marketStateText} dataMode={market.mode} />
            <TradesList trades={market.fills} priceUnit={instrument.currency} qtyUnit={instrument.volumeUnit} qtyDecimals={instrument.qtyDecimals} marketState={marketStateText} dataMode={market.mode} />
          </section>
        </main>

        <footer className="bg-[#0d1117] border-t border-[#21262d] py-0.5 px-3 shrink-0 text-[9px] text-slate-500 flex justify-between">
          <span>LTP: <span className="font-bold text-slate-300">{safeNum(market.ltp).toFixed(2)} {instrument.currency}</span></span>
          <span>{orders.active.length} active orders · {market.fills.length} fills · origin: {market.lastTickOrigin}</span>
        </footer>

        <MarketOverview />
        <SettingsPanel isOpen={showSettings} onClose={() => setShowSettings(false)} />
      </div>
    </ErrorBoundary>
  );
}
