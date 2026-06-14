import React, { useState, useEffect, useRef, useMemo } from "react";
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
import StrategyStudio from "./components/StrategyStudio";
import type { OHLCVBar, L2Level, TradeTick, SessionResponse, Instrument } from "./domain/instrument";
import { MarketState, MARKET_STATE_COLORS, resolveInstrument } from "./domain/instrument";
import { filterValidBars, validateOrderBook } from "./domain/validators";
import { MarketCalendarService } from "./domain/MarketCalendarService";
import { BarChart2, Search, Activity, Settings as SettingsIcon, Power } from "lucide-react";
import { fetchSymbols } from "./api/marketData";
import { TerminalDataOrchestrator, DataMode } from "./api/TerminalDataOrchestrator";
import type { BrokerConfig, OrchestratorCallbacks } from "./api/TerminalDataOrchestrator";
import { placeOrder } from "./api/orders";
import { armKillSwitch } from "./api/killSwitch";
import RuntimeModeToggle from "./components/RuntimeModeToggle";
import { subscribeReadModel } from "./api/stream";
import { fetchSession, isMarketOpen } from "./api/marketSession";
import type { ExchangeSegment, Side, OrderType, ProductType, Validity } from "./generated/models";
import { fetchBrokers } from "./api/brokerRegistry";
import type { BrokerInfo } from "./api/brokerRegistry";
import { DASHBOARD_LAYOUTS, DASHBOARD_LAYOUT_IDS, type DashboardLayoutId } from "./components/TerminalLayout";
import TerminalLayout from "./components/TerminalLayout";

const EXCHANGE_MAP: Record<string, ExchangeSegment> = {
  NSE: "NSE_EQ" as ExchangeSegment, BSE: "BSE_EQ" as ExchangeSegment,
  NFO: "NSE_FNO" as ExchangeSegment, MCX: "MCX_COMM" as ExchangeSegment,
  CDS: "NSE_CURRENCY" as ExchangeSegment,
};

const DEFAULT_SYMBOLS: Record<string, string[]> = {
  NSE: ["RELIANCE", "TCS", "INFY", "HDFCBANK", "ICICIBANK", "SBIN"],
  BSE: ["RELIANCE", "TCS", "INFY", "SBIN"],
  NFO: ["NIFTY", "BANKNIFTY"],
  MCX: ["GOLD", "SILVER", "CRUDEOIL"],
  CDS: ["USDINR", "EURINR"],
};

const BROKER_EXCHANGES: Record<string, string[]> = {
  DHAN: ["NSE", "BSE", "NFO", "MCX", "CDS"],
  UPSTOX: ["NSE", "BSE", "NFO", "MCX", "CDS"],
  ICICI: ["NSE", "BSE", "NFO"],
  SIMULATION: ["NSE", "BSE", "NFO", "MCX", "CDS"],
};

const TIMEFRAME_CONFIG: Record<string, { interval: string; days: number }> = {
  "1m":  { interval: "1m",  days: 5 },
  "5m":  { interval: "5m",  days: 15 },
  "15m": { interval: "15m", days: 30 },
  "1h":  { interval: "1h",  days: 90 },
  "4h":  { interval: "4h",  days: 180 },
  "1d":  { interval: "1d",  days: 750 },
};

const safeNum = (v: any, fb = 0): number => typeof v === "number" && isFinite(v) ? v : fb;

export default function App() {
  const [broker, setBroker] = useState(() => localStorage.getItem("tj_broker") || "DHAN");
  const [exchange, setExchange] = useState(() => localStorage.getItem("tj_exchange") || "NSE");
  const [symbol, setSymbol] = useState(() => localStorage.getItem("tj_symbol") || "RELIANCE");
  const [timeframe, setTimeframe] = useState(() => {
    const saved = localStorage.getItem("tj_timeframe");
    const validTFs = ["1m", "5m", "15m", "1h", "4h", "1d"];
    return saved && validTFs.includes(saved) ? saved : "1m";
  });
  const [isConnected, setIsConnected] = useState(true);
  const [searchQuery, setSearchQuery] = useState("");
  const [isSearchOpen, setIsSearchOpen] = useState(false);

  const [bars, setBars] = useState<OHLCVBar[]>([]);
  const [bids, setBids] = useState<L2Level[]>([]);
  const [asks, setAsks] = useState<L2Level[]>([]);
  const [trades, setTrades] = useState<TradeTick[]>([]);
  const [loading, setLoading] = useState(true);
  const [lastPrice, setLastPrice] = useState(0);
  const [priceChange, setPriceChange] = useState(0);
  const [availableSymbols, setAvailableSymbols] = useState<string[]>(DEFAULT_SYMBOLS.NSE);

  const [marketState, setMarketState] = useState<MarketState>(MarketState.UNKNOWN);
  const [dataSource, setDataSource] = useState("SIMULATION");
  const [feedHealth, setFeedHealth] = useState<"healthy" | "delayed" | "stale">("healthy");
  const lastUpdateRef = useRef(Date.now());

  const [showOrderPanel, setShowOrderPanel] = useState(false);
  const [showSettings, setShowSettings] = useState(false);
  const [orderSide, setOrderSide] = useState<"BUY" | "SELL">("BUY");
  const [orderQty, setOrderQty] = useState("1");
  const [orderType, setOrderType] = useState<"LIMIT" | "MARKET">("LIMIT");
  const [orderPrice, setOrderPrice] = useState("");
  const [orderStatus, setOrderStatus] = useState("");
  const [bottomTab, setBottomTab] = useState<"watchlist" | "orders" | "alerts" | "risk" | "news" | "studio">("watchlist");

  // Kill switch (STOP ALL TRADING) — confirmation modal + toast state.
  const [showKillConfirm, setShowKillConfirm] = useState(false);
  const [killSwitchStatus, setKillSwitchStatus] = useState<string>("");

  // Runtime mode toggle — a transient toast shown when the user switches
  // LIVE/PAPER (or when a switch fails). Auto-dismisses after a few seconds.
  const [runtimeModeToast, setRuntimeModeToast] = useState<{ text: string; variant: "success" | "error" } | null>(null);
  const showRuntimeModeToast = (text: string, variant: "success" | "error") => {
    setRuntimeModeToast({ text, variant });
    setTimeout(() => setRuntimeModeToast(null), 4000);
  };

  // Dashboard layout switcher: persists in localStorage and renders the
  // chosen TerminalLayout as an *additional* panel below the legacy
  // chart. The user can flip between Trading / Research / Scanner /
  // Options layouts. All five are driven by the widget registry, so
  // every widget is reachable.
  const [activeLayout, setActiveLayout] = useState<DashboardLayoutId>(() => {
    try {
      const stored = localStorage.getItem("tj_layout");
      if (stored && (DASHBOARD_LAYOUT_IDS as string[]).includes(stored)) {
        return stored as DashboardLayoutId;
      }
    } catch { /* ignore */ }
    return "trading";
  });
  const [showLayout, setShowLayout] = useState<boolean>(() => {
    try { return localStorage.getItem("tj_show_layout") === "1"; } catch { return false; }
  });
  useEffect(() => {
    try { localStorage.setItem("tj_layout", activeLayout); } catch { /* ignore */ }
  }, [activeLayout]);
  useEffect(() => {
    try { localStorage.setItem("tj_show_layout", showLayout ? "1" : "0"); } catch { /* ignore */ }
  }, [showLayout]);

  const pollRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const sseCleanupRef = useRef<(() => void) | null>(null);
  const lastTradePriceRef = useRef<number>(0);
  const lastTradeTimeRef = useRef<number>(0);
  const orchestratorRef = useRef<TerminalDataOrchestrator | null>(null);

  const [brokerCreds, setBrokerCreds] = useState<{accessToken?: string; clientId?: string} | null>(
    () => {
      try { const s = localStorage.getItem("tj_creds"); return s ? JSON.parse(s) : null; }
      catch { return null; }
    }
  );
  const [showBrokerModal, setShowBrokerModal] = useState(false);
  const [brokers, setBrokers] = useState<BrokerInfo[]>([]);
  const [brokerStatus, setBrokerStatus] = useState<{status: string; websocketConnected: boolean; broker: string}>({
    status: "DOWN",
    websocketConnected: false,
    broker: "unknown"
  });

  const segment = EXCHANGE_MAP[exchange] || "NSE_EQ";
  const instrument = useMemo(() => resolveInstrument(symbol, exchange), [symbol, exchange]);
  const marketOpen = isMarketOpen(marketState);
  const stateColor = MARKET_STATE_COLORS[marketState];
  // Determine data mode based on actual broker connection status from backend
  const brokerConnected = brokerStatus.status === "UP" && brokerStatus.websocketConnected;
  const dataMode = brokerConnected
    ? (marketOpen ? "LIVE" : "HISTORICAL")
    : (marketOpen ? "SIMULATION" : "HISTORICAL");

  const dataModeRef = useRef(dataMode);
  useEffect(() => { dataModeRef.current = dataMode; }, [dataMode]);

  useEffect(() => { localStorage.setItem("tj_broker", broker); }, [broker]);
  useEffect(() => { localStorage.setItem("tj_exchange", exchange); }, [exchange]);
  useEffect(() => { localStorage.setItem("tj_symbol", symbol); }, [symbol]);
  useEffect(() => { localStorage.setItem("tj_timeframe", timeframe); }, [timeframe]);

  useEffect(() => { dataModeRef.current = dataMode; }, [dataMode]);

  useEffect(() => {
    if (brokerCreds) localStorage.setItem("tj_creds", JSON.stringify(brokerCreds));
    else localStorage.removeItem("tj_creds");
  }, [brokerCreds]);

  useEffect(() => {
    const supported = BROKER_EXCHANGES[broker] || BROKER_EXCHANGES["DHAN"];
    if (!supported.includes(exchange)) {
      setExchange(supported[0]);
      const s = DEFAULT_SYMBOLS[supported[0]] || [];
      if (s.length) setSymbol(s[0]);
    }
  }, [broker]);

  // Clear all data stores when symbol or exchange changes
  useEffect(() => {
    // Step 1: Clear order book
    setBids([]);
    setAsks([]);

    // Step 2: Clear recent trades
    setTrades([]);

    // Step 3: Clear chart data
    setBars([]);

    // Step 4: Clear LTP and price change
    setLastPrice(0);
    setPriceChange(0);

    // Step 5: Reset trade tracking
    lastTradePriceRef.current = 0;
    lastTradeTimeRef.current = 0;

    // Step 6: Set loading state
    setLoading(true);

    console.log(`[Instrument] Changed to ${exchange}:${symbol}, all stores cleared`);
  }, [symbol, exchange]);

  useEffect(() => {
    let cancelled = false;

    const initOrchestrator = async () => {
      if (!isConnected) return;

      // Destroy previous orchestrator
      if (orchestratorRef.current) {
        orchestratorRef.current.destroy();
        orchestratorRef.current = null;
      }

      // Small delay to ensure data stores are cleared
      await new Promise(resolve => setTimeout(resolve, 100));
      if (cancelled) return;

      const brokerConfig: BrokerConfig = {
        isConfigured: !!brokerCreds?.accessToken,
        accessToken: brokerCreds?.accessToken,
        clientId: brokerCreds?.clientId,
      };

      const callbacks: OrchestratorCallbacks = {
        onBars: (newBars) => {
          const valid = filterValidBars(newBars);
          setBars(valid);
          if (valid.length > 0) {
            const last = valid[valid.length - 1];
            setLastPrice(last.close);
            const first = valid[0];
            setPriceChange(((last.close - first.open) / first.open) * 100);
          }
          setLoading(false);
        },
        onLtp: (ltp) => {
          // Freeze LTP in HISTORICAL mode — don't update
          if (dataModeRef.current === "HISTORICAL") return;
          setLastPrice(prev => {
            if (prev > 0) {
              setPriceChange(((ltp - prev) / prev) * 100);
              const tickSize = instrument.tickSize || 0.05;
              const priceMoved = Math.abs(ltp - lastTradePriceRef.current);
              if (priceMoved >= tickSize) {
                const now = Math.floor(Date.now() / 1000);
                // Don't generate trades in HISTORICAL mode or if timestamp is in the future
                if (dataModeRef.current !== "HISTORICAL" && now !== lastTradeTimeRef.current) {
                  lastTradeTimeRef.current = now;
                  lastTradePriceRef.current = ltp;
                  const trade: TradeTick = {
                    time: now, price: ltp,
                    quantity: Math.round(Math.random() * 500 + 10),
                    side: ltp >= prev ? "BUY" : "SELL",
                  };
                  // Validate: trade time must not be in the future
                  if (trade.time <= Math.floor(Date.now() / 1000)) {
                    setTrades(t => [trade, ...t.slice(0, 40)]);
                  }
                }
              }
            }
            return ltp;
          });
          lastUpdateRef.current = Date.now();
        },
        onDepth: (newBids, newAsks) => {
          if (dataModeRef.current === "HISTORICAL") return;
          setBids(newBids);
          setAsks(newAsks);
          lastUpdateRef.current = Date.now();
        },
        onModeChange: (_mode) => {
          // Mode change is handled by dataMode computed property
        },
        onFeedHealth: (health) => {
          setFeedHealth(health === "disconnected" ? "stale" : health);
        },
      };

      const orch = new TerminalDataOrchestrator(brokerConfig, callbacks);
      orchestratorRef.current = orch;
      const tfConfig = TIMEFRAME_CONFIG[timeframe] || TIMEFRAME_CONFIG["1d"];
      orch.initialize(exchange, instrument, segment, tfConfig.interval, tfConfig.days);
    };

    initOrchestrator();

    return () => {
      cancelled = true;
      if (orchestratorRef.current) {
        orchestratorRef.current.destroy();
        orchestratorRef.current = null;
      }
    };
  }, [broker, exchange, symbol, segment, instrument, brokerCreds, isConnected, timeframe]);

  useEffect(() => {
    document.title = "Trade-J Terminal";
  }, []);

  useEffect(() => {
    fetchBrokers().then(list => {
      if (list.length > 0) setBrokers(list);
    });
  }, []);

  useEffect(() => {
    if (!isConnected) return;
    const check = () => {
      fetchSession(exchange).then(s => {
        setMarketState(s.state);
        setDataSource(s.dataSource);
      }).catch(() => {});
    };
    check();
    const iv = setInterval(check, 60000);
    return () => clearInterval(iv);
  }, [exchange, isConnected]);

  // Poll backend health status every 5 seconds
  useEffect(() => {
    if (!isConnected) return;

    const checkHealth = async () => {
      try {
        const res = await fetch("/actuator/health");
        if (res.ok) {
          const data = await res.json();
          const broker = data.components?.broker || {};
          setBrokerStatus({
            status: broker.status || "DOWN",
            websocketConnected: broker.details?.websocketConnected || false,
            broker: broker.details?.broker || "unknown"
          });
        }
      } catch (e) {
        console.error("[Health] Failed to check health:", e);
      }
    };

    checkHealth();
    const iv = setInterval(checkHealth, 5000);
    return () => clearInterval(iv);
  }, [isConnected]);

  useEffect(() => {
    if (!isConnected) return;
    const iv = setInterval(() => {
      const age = Date.now() - lastUpdateRef.current;
      if (age > 30000) setFeedHealth("stale");
      else if (age > 5000) setFeedHealth("delayed");
      else setFeedHealth("healthy");
    }, 2000);
    return () => clearInterval(iv);
  }, [isConnected]);

  useEffect(() => {
    if (!isConnected) return;
    fetchSymbols(segment, searchQuery || undefined)
      .then(data => {
        const names = data.symbols.slice(0, 50).map(s => s.symbol);
        if (names.length > 0) setAvailableSymbols(names);
      }).catch(() => {});
  }, [segment, searchQuery, isConnected]);

  useEffect(() => {
    if (!isConnected) return;
    sseCleanupRef.current = subscribeReadModel(() => {});
    return () => { if (sseCleanupRef.current) sseCleanupRef.current(); };
  }, [isConnected]);

  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      // Don't trigger when typing in inputs
      if ((e.target as HTMLElement).tagName === "INPUT" || (e.target as HTMLElement).tagName === "SELECT") return;

      const tfMap: Record<string, string> = { "1": "1m", "2": "5m", "3": "15m", "4": "1h", "5": "4h", "6": "1d" };
      if (tfMap[e.key]) { setTimeframe(tfMap[e.key]); return; }

      switch (e.key.toLowerCase()) {
        case "b": setOrderSide("BUY"); setShowOrderPanel(true); break;
        case "s": setOrderSide("SELL"); setShowOrderPanel(true); break;
        case "escape": setShowOrderPanel(false); setIsSearchOpen(false); setShowBrokerModal(false); break;
        case "t": setShowOrderPanel(p => !p); break;
        case "/": case "f": e.preventDefault(); setIsSearchOpen(true); break;
      }
    };
    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, []);

  const handlePlaceOrder = async () => {
    setOrderStatus("Placing...");
    try {
      const result = await placeOrder({
        symbol, exchangeSegment: segment as ExchangeSegment,
        side: orderSide as Side, quantity: parseInt(orderQty) || 1,
        orderType: orderType as OrderType,
        pricePaisa: orderType === "LIMIT" ? Math.round(parseFloat(orderPrice || "0") * 100) : 0,
        triggerPricePaisa: null, productType: "CNC" as ProductType, validity: "DAY" as Validity,
      });
      setOrderStatus(`Order ${result.orderId} ${result.status}`);
    } catch (e: any) { setOrderStatus(`Error: ${e.message}`); }
  };

  const handleConfirmKillSwitch = async () => {
    setShowKillConfirm(false);
    setKillSwitchStatus("Arming kill switch...");
    console.warn("[KILL-SWITCH] User initiated STOP ALL TRADING from UI toolbar");
    try {
      const result = await armKillSwitch();
      setKillSwitchStatus(`Kill switch ARMED at ${new Date(result.armedAtMs).toLocaleTimeString()}`);
      setBrokerStatus(prev => ({ ...prev, status: "DOWN", websocketConnected: false }));
      setTimeout(() => setKillSwitchStatus(""), 8000);
    } catch (e: any) {
      setKillSwitchStatus(`Kill switch failed: ${e.message}`);
      setTimeout(() => setKillSwitchStatus(""), 8000);
    }
  };

  const killSwitchDisabled = dataMode === "SIMULATION";

  const filteredSymbolsList = useMemo(() => {
    if (!searchQuery) return availableSymbols;
    return availableSymbols.filter(s => s.toLowerCase().includes(searchQuery.toLowerCase()));
  }, [searchQuery, availableSymbols]);

  const feedColor = feedHealth === "healthy" ? "#26a69a" : feedHealth === "delayed" ? "#f0b429" : "#ef5350";

  return (
    <ErrorBoundary>
    <div className="min-h-screen bg-[#010409] text-[#c9d1d9] flex flex-col font-mono select-none overflow-hidden h-screen">
      {/* Status Bar */}
      <div className="bg-[#0d1117] border-b border-[#21262d] px-3 py-1 flex items-center gap-4 text-[10px] shrink-0">
        <div className="flex items-center gap-1.5">
          <div className="bg-[#f0b429] p-0.5 rounded"><BarChart2 className="w-3 h-3 text-[#0d1117]" /></div>
          <span className="font-black tracking-wider">TRADE_J<span className="text-[#f0b429]">.PRO</span></span>
        </div>
        <span className="text-slate-600">|</span>
        <div className="flex items-center bg-[#161b22] border border-[#21262d] rounded p-0.5">
          <span className="text-[9px] text-slate-500 font-bold px-1.5">BROKER:</span>
          <select value={broker} onChange={e => { setBroker(e.target.value); setShowBrokerModal(true); }}
            className="bg-transparent text-slate-100 font-bold text-[11px] py-0.5 px-1 outline-none cursor-pointer">
            {brokers.length > 0 ? (
              brokers.filter(b => b.source !== "SIMULATION").map(b => (
                <option key={b.source} value={b.source}>{b.displayName}</option>
              ))
            ) : (
              <>
                <option value="DHAN">Dhan</option>
                <option value="UPSTOX">Upstox</option>
                <option value="ICICI">ICICI Direct</option>
              </>
            )}
          </select>
          {brokerCreds?.accessToken ? (
            <span className="text-[#26a69a] text-[9px] font-bold px-1">&#10003;</span>
          ) : (
            <button onClick={() => setShowBrokerModal(true)} className="text-[#f0b429] text-[9px] font-bold px-1 hover:underline cursor-pointer">&#9888;</button>
          )}
        </div>
        <span className="text-slate-600">|</span>
        <span className="text-slate-400">EXCHANGE: <span className="font-bold text-slate-200">{exchange}</span></span>
        <span className="text-slate-600">|</span>
        <span className="flex items-center gap-1">
          MARKET: <span className="w-1.5 h-1.5 rounded-full" style={{ backgroundColor: stateColor }} />
          <span className="font-bold" style={{ color: stateColor }}>{marketState}</span>
        </span>
        <span className="text-slate-600">|</span>
        <span className="flex items-center gap-1">
          FEED: <span className="w-1.5 h-1.5 rounded-full" style={{ backgroundColor: feedColor }} />
          <span className="font-bold" style={{ color: feedColor }}>{feedHealth.toUpperCase()}</span>
        </span>
        <span className="text-slate-600">|</span>
        <span className={`px-1.5 py-0.5 rounded font-bold text-[9px] ${
          dataMode === "LIVE" ? "bg-[#26a69a]/15 text-[#26a69a]" :
          dataMode === "HISTORICAL" ? "bg-[#3b82f6]/15 text-[#3b82f6]" :
          "bg-[#f0b429]/15 text-[#f0b429]"
        }`}>
          {dataMode}
        </span>
        <div className="flex-1" />
        <button onClick={() => { if (!brokerConnected) setShowBrokerModal(true); else setIsConnected(!isConnected); }}
          className={`flex items-center gap-1 px-2 py-0.5 rounded font-bold cursor-pointer ${
            brokerConnected ? "text-[#26a69a] hover:bg-[#26a69a]/10" :
            "text-[#f0b429] hover:bg-[#f0b429]/10"
          }`}>
          {brokerConnected ? "CONNECTED" : "DISCONNECTED"}
        </button>
      </div>

      {dataMode === "SIMULATION" && (
        <div className="bg-[#f0b429]/10 border-b border-[#f0b429]/30 px-3 py-0.5 flex items-center justify-center text-[9px] shrink-0">
          <span className="text-[#f0b429] font-bold">⚠ SIMULATION MODE</span>
          <span className="text-slate-400 ml-2">
            {brokerStatus.broker === "simulation"
              ? "Backend running in simulation mode. Configure a real broker for live data."
              : "Broker not connected. Prices shown are simulated and not real market data."}
          </span>
        </div>
      )}
      {dataMode === "HISTORICAL" && (
        <div className="bg-[#3b82f6]/10 border-b border-[#3b82f6]/30 px-3 py-0.5 flex items-center justify-center text-[9px] shrink-0">
          <span className="text-[#3b82f6] font-bold">📊 HISTORICAL MODE</span>
          <span className="text-slate-400 ml-2">Market closed. Showing last session data.</span>
        </div>
      )}

      {/* Toolbar */}
      <header className="bg-[#0d1117] border-b border-[#21262d] px-3 py-1 shrink-0">
        <div className="flex items-center gap-2">
          <div className="flex items-center bg-[#161b22] border border-[#21262d] rounded p-0.5">
            <span className="text-[9px] text-slate-500 font-bold px-1.5">EXCH:</span>
            <select value={exchange} onChange={e => {
                setExchange(e.target.value);
                const s = DEFAULT_SYMBOLS[e.target.value] || [];
                if (s.length) setSymbol(s[0]);
              }}
              className="bg-transparent text-slate-100 font-bold text-[11px] py-0.5 px-1 outline-none cursor-pointer">
              {(BROKER_EXCHANGES[broker] || BROKER_EXCHANGES["DHAN"]).map(ex => (
                <option key={ex} value={ex}>{ex}</option>
              ))}
            </select>
          </div>
          <div className="relative flex items-center bg-[#161b22] border border-[#21262d] rounded p-0.5 min-w-[140px]">
            <span className="text-[9px] text-slate-500 font-bold px-1.5">SYM:</span>
            <button onClick={() => setIsSearchOpen(!isSearchOpen)} className="bg-transparent text-slate-100 font-black text-[11px] py-0.5 px-1 flex items-center gap-1 cursor-pointer">
              <span>{symbol}</span><Search className="w-3 h-3 text-slate-400" />
            </button>
            {isSearchOpen && (
              <div className="absolute top-full left-0 right-0 bg-[#161b22] border border-[#21262d] rounded shadow-xl z-50 p-2 mt-1">
                <input type="text" placeholder="Search..." value={searchQuery} onChange={e => setSearchQuery(e.target.value)}
                  className="w-full bg-[#0d1117] border border-[#21262d] text-slate-100 text-[10px] p-1.5 rounded outline-none mb-1" autoFocus />
                <div className="max-h-[160px] overflow-y-auto space-y-0.5">
                  {filteredSymbolsList.map(sym => (
                    <button key={sym} onClick={() => { setSymbol(sym); setIsSearchOpen(false); setSearchQuery(""); }}
                      className={`w-full text-left py-0.5 px-1.5 rounded text-[10px] hover:bg-[#21262d] ${symbol === sym ? "text-[#f0b429] bg-[#f0b429]/10" : "text-slate-300"}`}>
                      {sym}
                    </button>
                  ))}
                </div>
              </div>
            )}
          </div>
          <div className="flex items-center bg-[#161b22] border border-[#21262d] rounded p-0.5 gap-0.5">
            {(["watchlist", "orders", "alerts", "risk", "news", "studio"] as const).map(tab => (
              <button key={tab} onClick={() => setBottomTab(tab)}
                className={`px-2 py-0.5 rounded text-[9px] font-bold uppercase cursor-pointer transition ${
                  bottomTab === tab ? "bg-[#f0b429] text-[#0d1117]" : "text-slate-400 hover:text-slate-200 hover:bg-[#21262d]"
                }`}>
                {tab}
              </button>
            ))}
          </div>
          <div className="flex-1" />
          <div className="flex items-center bg-[#161b22] border border-[#21262d] rounded p-0.5 gap-0.5">
            {DASHBOARD_LAYOUT_IDS.map(id => (
              <button key={id} onClick={() => setActiveLayout(id)}
                className={`px-2 py-0.5 rounded text-[9px] font-bold uppercase cursor-pointer transition ${
                  activeLayout === id ? "bg-[#26a69a] text-[#0d1117]" : "text-slate-400 hover:text-slate-200 hover:bg-[#21262d]"
                }`}>
                {id}
              </button>
            ))}
            <button onClick={() => setShowLayout(v => !v)}
              className={`ml-1 px-2 py-0.5 rounded text-[9px] font-bold uppercase cursor-pointer transition ${
                showLayout ? "bg-[#f0b429] text-[#0d1117]" : "text-slate-400 hover:text-slate-200 hover:bg-[#21262d]"
              }`}
              title="Toggle the dashboard layout panel">
              {showLayout ? "HIDE PANEL" : "SHOW PANEL"}
            </button>
          </div>
          <RuntimeModeToggle onToast={showRuntimeModeToast} />
          <button onClick={() => setShowOrderPanel(!showOrderPanel)}
            className="flex items-center gap-1 px-3 py-1 rounded font-black text-[10px] bg-[#f0b429]/15 text-[#f0b429] border border-[#f0b429]/30 hover:border-[#f0b429]/60 cursor-pointer">
            <Activity className="w-3 h-3" /> TRADE
          </button>
          <span className="text-slate-600">|</span>
          <button
            onClick={() => setShowKillConfirm(true)}
            disabled={killSwitchDisabled}
            title={killSwitchDisabled ? "Disabled in SIMULATION mode (no live positions to stop)" : "STOP ALL TRADING — cancels open orders and pauses strategies"}
            className={`flex items-center gap-1 px-3 py-1 rounded font-black text-[10px] border cursor-pointer ${
              killSwitchDisabled
                ? "bg-[#21262d] text-slate-600 border-[#21262d] cursor-not-allowed"
                : "bg-[#ef5350]/20 text-[#ef5350] border-[#ef5350]/50 hover:bg-[#ef5350]/30 hover:border-[#ef5350]"
            }`}
            data-testid="stop-all-trading">
            <Power className="w-3 h-3" /> STOP ALL TRADING
          </button>
          <button onClick={() => setShowSettings(true)}
            className="flex items-center gap-1 px-2 py-1 rounded text-[10px] text-slate-400 hover:text-slate-200 hover:bg-[#21262d] cursor-pointer">
            <SettingsIcon className="w-3 h-3" />
          </button>
        </div>
      </header>

      {showOrderPanel && (
        <div className="bg-[#0d1117] border-b border-[#21262d] px-3 py-1.5 flex items-center gap-3 text-[11px] shrink-0">
          <div className="flex gap-1">
            <button onClick={() => setOrderSide("BUY")} className={`px-3 py-0.5 rounded font-bold text-[10px] ${orderSide === "BUY" ? "bg-[#26a69a]/20 text-[#26a69a] border border-[#26a69a]/40" : "bg-[#161b22] text-slate-400 border border-[#21262d]"}`}>BUY</button>
            <button onClick={() => setOrderSide("SELL")} className={`px-3 py-0.5 rounded font-bold text-[10px] ${orderSide === "SELL" ? "bg-[#ef5350]/20 text-[#ef5350] border border-[#ef5350]/40" : "bg-[#161b22] text-slate-400 border border-[#21262d]"}`}>SELL</button>
          </div>
          <span className="text-slate-500 text-[9px]">QTY:</span>
          <input type="number" value={orderQty} onChange={e => setOrderQty(e.target.value)} className="bg-[#161b22] border border-[#21262d] text-slate-100 text-[11px] w-14 px-2 py-0.5 rounded outline-none" />
          <div className="flex gap-1">
            <button onClick={() => setOrderType("LIMIT")} className={`px-2 py-0.5 rounded font-bold text-[10px] ${orderType === "LIMIT" ? "bg-[#f0b429]/20 text-[#f0b429] border border-[#f0b429]/40" : "bg-[#161b22] text-slate-400 border border-[#21262d]"}`}>LIMIT</button>
            <button onClick={() => setOrderType("MARKET")} className={`px-2 py-0.5 rounded font-bold text-[10px] ${orderType === "MARKET" ? "bg-[#f0b429]/20 text-[#f0b429] border border-[#f0b429]/40" : "bg-[#161b22] text-slate-400 border border-[#21262d]"}`}>MKT</button>
          </div>
          {orderType === "LIMIT" && <>
            <span className="text-slate-500 text-[9px]">PX:</span>
            <input type="number" step="0.05" value={orderPrice} onChange={e => setOrderPrice(e.target.value)} placeholder={safeNum(lastPrice).toFixed(2)}
              className="bg-[#161b22] border border-[#21262d] text-slate-100 text-[11px] w-20 px-2 py-0.5 rounded outline-none" />
          </>}
          <button onClick={handlePlaceOrder} className="px-4 py-0.5 rounded font-black text-[10px] bg-[#f0b429] text-[#0d1117] hover:bg-[#f0b429]/90">PLACE</button>
          {orderStatus && <span className={`text-[10px] font-bold ${orderStatus.startsWith("Error") ? "text-[#ef5350]" : "text-[#26a69a]"}`}>{orderStatus}</span>}
        </div>
      )}

      <main className="flex-1 overflow-hidden grid grid-rows-12 p-1.5 gap-1.5">
        <section className="row-span-8 flex flex-col overflow-hidden min-h-[350px]">
          <CandlestickChart instrument={instrument} timeframe={timeframe} setTimeframe={setTimeframe}
            bars={bars} loading={loading} marketState={marketState} ltp={lastPrice} />
        </section>
        <section className="row-span-4 grid grid-cols-1 md:grid-cols-3 gap-1.5 overflow-hidden min-h-[160px]">
          {bottomTab === "watchlist" && (
            <WatchlistPanel
              onSelectSymbol={(sym) => setSymbol(sym)}
              currentSymbol={symbol}
              segment={segment}
            />
          )}
          {bottomTab === "orders" && (
            <PortfolioPanel segment={segment} />
          )}
          {bottomTab === "alerts" && (
            <PriceAlerts
              currentSymbol={symbol}
              currentExchange={exchange}
              lastPrice={lastPrice}
              segment={segment}
            />
          )}
          {bottomTab === "risk" && (
            <RiskCalculator lastPrice={lastPrice} currency={instrument.currency} />
          )}
          {bottomTab === "news" && (
            <NewsFeed symbol={symbol} />
          )}
          {bottomTab === "studio" && (
            <StrategyStudio />
          )}
          <div className="bg-[#0d1117] border border-[#21262d] rounded-lg overflow-hidden flex flex-col h-full">
            <OrderBook bids={bids} asks={asks} lastPrice={lastPrice} priceChange={priceChange}
              symbol={symbol} onSelectPrice={p => setOrderPrice(safeNum(p).toFixed(2))}
              priceUnit={instrument.currency} qtyUnit={instrument.volumeUnit} marketState={marketState}
              dataMode={dataMode} />
          </div>
          <div className="bg-[#0d1117] border border-[#21262d] rounded-lg overflow-hidden flex flex-col h-full">
            <TradesList trades={trades} priceUnit={instrument.currency} qtyUnit={instrument.volumeUnit}
              qtyDecimals={instrument.qtyDecimals} marketState={marketState} dataMode={dataMode} />
          </div>
        </section>
      </main>

      {showLayout && (
        <section
          data-testid="dashboard-layout-panel"
          aria-label="Dashboard layout panel"
          className="bg-[#0d1117] border-t border-[#21262d] shrink-0 h-[55vh] min-h-[420px] overflow-hidden p-1.5"
        >
          <div className="flex items-center justify-between px-2 py-1">
            <span className="text-[10px] font-bold text-slate-200 uppercase">
              Dashboard · {DASHBOARD_LAYOUTS[activeLayout]?.name ?? activeLayout}
            </span>
            <span className="text-[9px] text-slate-500">
              Layout is registry-driven; every widget in widgetRegistry.ts is reachable.
            </span>
          </div>
          <div className="h-[calc(100%-32px)]">
            <TerminalLayout layoutId={activeLayout} />
          </div>
        </section>
      )}

      <footer className="bg-[#0d1117] border-t border-[#21262d] py-0.5 px-3 shrink-0 text-[9px] text-slate-500">
        <div className="flex justify-between items-center">
          <span>LTP: <span className="font-bold text-slate-300">{safeNum(lastPrice).toFixed(2)} {instrument.currency}</span></span>
          <span>&copy; {new Date().getFullYear()} Trade-J Terminal Systems</span>
        </div>
      </footer>

      <MarketOverview />

      {showBrokerModal && (() => {
        const selectedBroker = brokers.find(b => b.source === broker);
        const fields = selectedBroker?.credentialFields || [];
        return (
          <div className="fixed inset-0 bg-black/60 flex items-center justify-center z-[100]">
            <div className="bg-[#161b22] border border-[#21262d] rounded-lg p-4 w-80">
              <h3 className="text-sm font-bold text-slate-200 mb-3">
                Configure {selectedBroker?.displayName || broker} Credentials
              </h3>
              {fields.length === 0 ? (
                <div className="text-[10px] text-slate-400 py-2">No credentials required for this broker.</div>
              ) : (
                <div className="space-y-2">
                  {fields.map(field => (
                    <div key={field.key}>
                      <label className="text-[9px] text-slate-400 font-bold">{field.label.toUpperCase()}</label>
                      <input id={`cred-${field.key}`} type={field.type} placeholder={field.placeholder}
                        className="w-full bg-[#0d1117] border border-[#21262d] text-slate-100 text-[11px] p-1.5 rounded outline-none" />
                    </div>
                  ))}
                </div>
              )}
              <div className="flex gap-2 mt-4">
                <button onClick={() => {
                  const values: Record<string, string> = {};
                  let allFilled = true;
                  fields.forEach(field => {
                    const el = document.getElementById(`cred-${field.key}`) as HTMLInputElement;
                    const val = el?.value || "";
                    values[field.key] = val;
                    if (val.length < 3) allFilled = false;
                  });
                  if (allFilled && fields.length > 0) {
                    setBrokerCreds(values);
                    setShowBrokerModal(false);
                  }
                }} className="flex-1 px-3 py-1.5 rounded font-bold text-[10px] bg-[#26a69a] text-[#0d1117] hover:bg-[#26a69a]/90 cursor-pointer">
                  Connect {selectedBroker?.displayName || broker}
                </button>
                <button onClick={() => setShowBrokerModal(false)}
                  className="flex-1 px-3 py-1.5 rounded font-bold text-[10px] bg-[#21262d] text-slate-300 hover:bg-[#30363d] cursor-pointer">
                  Continue in Simulation
                </button>
              </div>
            </div>
          </div>
        );
      })()}

      {showKillConfirm && (
        <div className="fixed inset-0 bg-black/70 flex items-center justify-center z-[110]" data-testid="stop-all-confirm">
          <div className="bg-[#161b22] border-2 border-[#ef5350] rounded-lg p-5 w-[420px] shadow-2xl">
            <h3 className="text-base font-black text-[#ef5350] mb-2 flex items-center gap-2">
              <Power className="w-4 h-4" /> STOP ALL TRADING?
            </h3>
            <p className="text-[11px] text-slate-300 leading-relaxed mb-4">
              This will cancel all open orders and pause all strategies. The kill
              switch will remain armed until manually disarmed. Are you sure?
            </p>
            <div className="flex gap-2">
              <button
                onClick={handleConfirmKillSwitch}
                className="flex-1 px-3 py-1.5 rounded font-black text-[10px] bg-[#ef5350] text-white hover:bg-[#ef5350]/90 cursor-pointer"
                data-testid="stop-all-confirm-yes">
                YES — STOP ALL TRADING
              </button>
              <button
                onClick={() => setShowKillConfirm(false)}
                className="flex-1 px-3 py-1.5 rounded font-bold text-[10px] bg-[#21262d] text-slate-300 hover:bg-[#30363d] cursor-pointer">
                Cancel
              </button>
            </div>
          </div>
        </div>
      )}

      {killSwitchStatus && (
        <div
          data-testid="kill-switch-toast"
          className={`fixed bottom-3 right-3 z-[120] px-4 py-2 rounded shadow-lg text-[11px] font-bold border ${
            killSwitchStatus.startsWith("Kill switch failed")
              ? "bg-[#ef5350]/15 text-[#ef5350] border-[#ef5350]/40"
              : "bg-[#26a69a]/15 text-[#26a69a] border-[#26a69a]/40"
          }`}>
          {killSwitchStatus}
        </div>
      )}

      {runtimeModeToast && (
        <div
          data-testid="runtime-mode-toast"
          className={`fixed bottom-3 left-3 z-[120] px-4 py-2 rounded shadow-lg text-[11px] font-bold border ${
            runtimeModeToast.variant === "error"
              ? "bg-[#ef5350]/15 text-[#ef5350] border-[#ef5350]/40"
              : "bg-[#26a69a]/15 text-[#26a69a] border-[#26a69a]/40"
          }`}>
          {runtimeModeToast.text}
        </div>
      )}
    </div>
    <SettingsPanel isOpen={showSettings} onClose={() => setShowSettings(false)} />
    </ErrorBoundary>
  );
}
