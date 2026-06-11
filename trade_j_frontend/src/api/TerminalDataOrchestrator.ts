import { DhanFeedManager } from "./DhanFeedManager";
import { fetchCandles, fetchDepth, fetchLtp } from "./marketData";
import { fetchSession } from "./marketSession";
import type { OHLCVBar, L2Level, TradeTick, SessionResponse, Instrument } from "../domain/instrument";
import { MarketState } from "../domain/instrument";
import type { DhanQuotePacket, DhanDepthPacket } from "./dhanPacketParser";

export enum DataMode {
  LIVE = "LIVE",
  HISTORICAL = "HISTORICAL",
  SIMULATION = "SIMULATION",
}

export interface BrokerConfig {
  isConfigured: boolean;
  accessToken?: string;
  clientId?: string;
}

export interface OrchestratorCallbacks {
  onBars?: (bars: OHLCVBar[]) => void;
  onLtp?: (ltp: number) => void;
  onDepth?: (bids: L2Level[], asks: L2Level[]) => void;
  onTrade?: (trade: TradeTick) => void;
  onModeChange?: (mode: DataMode, marketState: MarketState) => void;
  onFeedHealth?: (health: "healthy" | "delayed" | "stale" | "disconnected") => void;
}

export class TerminalDataOrchestrator {
  private currentMode: DataMode | null = null;
  private feedManager: DhanFeedManager | null = null;
  private pollTimers: ReturnType<typeof setInterval>[] = [];
  private sessionTimer: ReturnType<typeof setInterval> | null = null;
  private feedHealthTimer: ReturnType<typeof setInterval> | null = null;
  private lastUpdateMs = Date.now();
  private callbacks: OrchestratorCallbacks;
  private brokerConfig: BrokerConfig;
  private currentExchange = "";
  private currentSymbol = "";
  private currentSegment = "";
  private currentInstrument: Instrument | null = null;
  private currentInterval = "1d";
  private currentDays = 365;
  private securityIdToSymbol: Map<number, string> = new Map();

  constructor(brokerConfig: BrokerConfig, callbacks: OrchestratorCallbacks) {
    this.brokerConfig = brokerConfig;
    this.callbacks = callbacks;
  }

  async initialize(exchange: string, instrument: Instrument, segment: string, interval = "1d", days = 365): Promise<void> {
    this.currentExchange = exchange;
    this.currentSymbol = instrument.symbol;
    this.currentSegment = segment;
    this.currentInstrument = instrument;

    // Always load historical bars first
    await this.loadHistoricalBars(instrument, segment, interval, days);

    // Determine mode
    const mode = this.resolveMode(exchange, instrument);
    if (mode === this.currentMode) return;
    this.currentMode = mode;

    // Stop any existing feeds
    this.stopAllFeeds();

    // Branch on mode
    switch (mode) {
      case DataMode.LIVE:
        this.startLiveFeed(instrument);
        break;
      case DataMode.HISTORICAL:
        this.startHistoricalFreeze();
        break;
      case DataMode.SIMULATION:
        this.startSimulation(instrument, segment);
        break;
    }

    // Start session polling (every 60s)
    this.startSessionPolling(exchange, instrument);

    // Start feed health monitoring
    this.startFeedHealthMonitoring();

    this.callbacks.onModeChange?.(mode, MarketState.UNKNOWN);
  }

  private resolveMode(exchange: string, instrument: Instrument): DataMode {
    const hasCreds = this.brokerConfig.isConfigured &&
      (this.brokerConfig.accessToken?.length ?? 0) > 0;

    if (!hasCreds) return DataMode.SIMULATION;

    // Check market state from last known session
    const marketState = this.getLastKnownMarketState();
    if (marketState === MarketState.OPEN) return DataMode.LIVE;
    return DataMode.HISTORICAL;
  }

  private lastKnownMarketState: MarketState = MarketState.UNKNOWN;

  private getLastKnownMarketState(): MarketState {
    return this.lastKnownMarketState;
  }

  private async loadHistoricalBars(instrument: Instrument, segment: string, interval = "1d", days = 365): Promise<void> {
    try {
      const to = new Date().toISOString().split("T")[0];
      const from = new Date(Date.now() - days * 86400000).toISOString().split("T")[0];
      const data = await fetchCandles(instrument.symbol, segment, interval, from, to);
      const bars: OHLCVBar[] = data.candles.map(c => ({
        time: Math.floor(c.startTimeMs / 1000),
        open: c.openPaisa / 100,
        high: c.highPaisa / 100,
        low: c.lowPaisa / 100,
        close: c.closePaisa / 100,
        volume: c.volume,
      }));
      this.callbacks.onBars?.(bars);
    } catch (e) {
      console.error("[Orchestrator] Failed to load historical bars:", e);
    }
  }

  private startLiveFeed(instrument: Instrument): void {
    if (!this.brokerConfig.accessToken || !this.brokerConfig.clientId) return;

    // Map security IDs for reverse lookup
    const { resolveDhanInstrument: resolve } = require("./dhanSecurityIds");
    const resolved = resolve(instrument.symbol);
    if (resolved) {
      this.securityIdToSymbol.set(parseInt(resolved.securityId), instrument.symbol);
    }

    this.feedManager = new DhanFeedManager({
      accessToken: this.brokerConfig.accessToken,
      clientId: this.brokerConfig.clientId,
      onTick: (_secId, ltp) => {
        this.lastUpdateMs = Date.now();
        this.callbacks.onLtp?.(ltp);
      },
      onQuote: (_secId, packet) => {
        this.lastUpdateMs = Date.now();
        this.callbacks.onLtp?.(packet.ltp);
      },
      onDepth: (_secId, packet) => {
        this.lastUpdateMs = Date.now();
        const bids: L2Level[] = packet.bids.map(b => ({
          price: b.price, quantity: b.quantity, orders: b.orders,
        }));
        const asks: L2Level[] = packet.asks.map(a => ({
          price: a.price, quantity: a.quantity, orders: a.orders,
        }));
        this.callbacks.onDepth?.(bids, asks);
      },
      onConnect: () => {
        this.callbacks.onFeedHealth?.("healthy");
      },
      onDisconnect: () => {
        this.callbacks.onFeedHealth?.("disconnected");
      },
      onError: () => {
        this.callbacks.onFeedHealth?.("stale");
      },
    });

    this.feedManager.connect([instrument.symbol]);
  }

  private startHistoricalFreeze(): void {
    // In historical mode, we already loaded bars. Just freeze everything.
    // Load one final depth snapshot and freeze
    if (this.currentInstrument) {
      fetchDepth(this.currentInstrument.symbol).then(data => {
        const bids: L2Level[] = (data.bids || []).map((b: any) => ({
          price: b.pricePaisa != null ? b.pricePaisa / 100 : (b.price ?? 0),
          quantity: b.quantity ?? b.amount ?? 0,
          orders: b.orderCount ?? b.orders ?? 1,
        }));
        const asks: L2Level[] = (data.asks || []).map((a: any) => ({
          price: a.pricePaisa != null ? a.pricePaisa / 100 : (a.price ?? 0),
          quantity: a.quantity ?? a.amount ?? 0,
          orders: a.orderCount ?? a.orders ?? 1,
        }));
        this.callbacks.onDepth?.(bids, asks);
      }).catch(() => {});

      fetchLtp(this.currentInstrument.symbol, this.currentSegment).then(data => {
        this.callbacks.onLtp?.(data.ltpPaisa / 100);
      }).catch(() => {});
    }
  }

  private startSimulation(instrument: Instrument, segment: string): void {
    // Poll LTP and depth from backend simulation endpoints
    const loadLtp = () => {
      fetchLtp(instrument.symbol, segment).then(data => {
        const newPrice = data.ltpPaisa / 100;
        this.lastUpdateMs = Date.now();
        this.callbacks.onLtp?.(newPrice);
      }).catch(() => {});
    };

    const loadDepth = () => {
      fetchDepth(instrument.symbol).then(data => {
        const bids: L2Level[] = (data.bids || []).map((b: any) => ({
          price: b.pricePaisa != null ? b.pricePaisa / 100 : (b.price ?? 0),
          quantity: b.quantity ?? b.amount ?? 0,
          orders: b.orderCount ?? b.orders ?? 1,
        })).sort((a: L2Level, b: L2Level) => b.price - a.price);

        const asks: L2Level[] = (data.asks || []).map((a: any) => ({
          price: a.pricePaisa != null ? a.pricePaisa / 100 : (a.price ?? 0),
          quantity: a.quantity ?? a.amount ?? 0,
          orders: a.orderCount ?? a.orders ?? 1,
        })).sort((a: L2Level, b: L2Level) => a.price - b.price);

        this.lastUpdateMs = Date.now();
        this.callbacks.onDepth?.(bids, asks);
      }).catch(() => {});
    };

    loadLtp();
    loadDepth();
    this.pollTimers.push(setInterval(loadLtp, 2000));
    this.pollTimers.push(setInterval(loadDepth, 3000));
  }

  private startSessionPolling(exchange: string, instrument: Instrument): void {
    const poll = () => {
      fetchSession(exchange).then(session => {
        this.lastKnownMarketState = session.state;
        // Check if mode should change
        const newMode = this.resolveMode(exchange, instrument);
        if (newMode !== this.currentMode) {
          this.currentMode = newMode;
          this.stopAllFeeds();
          switch (newMode) {
            case DataMode.LIVE: this.startLiveFeed(instrument); break;
            case DataMode.HISTORICAL: this.startHistoricalFreeze(); break;
            case DataMode.SIMULATION: this.startSimulation(instrument, this.currentSegment); break;
          }
          this.callbacks.onModeChange?.(newMode, session.state);
        }
      }).catch(() => {});
    };
    poll();
    this.sessionTimer = setInterval(poll, 60000);
  }

  private startFeedHealthMonitoring(): void {
    this.feedHealthTimer = setInterval(() => {
      const age = Date.now() - this.lastUpdateMs;
      if (this.currentMode === DataMode.HISTORICAL) {
        // Historical mode - feed is intentionally frozen
        this.callbacks.onFeedHealth?.("healthy");
      } else if (age > 30000) {
        this.callbacks.onFeedHealth?.("stale");
      } else if (age > 5000) {
        this.callbacks.onFeedHealth?.("delayed");
      } else {
        this.callbacks.onFeedHealth?.("healthy");
      }
    }, 2000);
  }

  private stopAllFeeds(): void {
    if (this.feedManager) {
      this.feedManager.disconnect();
      this.feedManager = null;
    }
    this.pollTimers.forEach(t => clearInterval(t));
    this.pollTimers = [];
  }

  destroy(): void {
    this.stopAllFeeds();
    if (this.sessionTimer) clearInterval(this.sessionTimer);
    if (this.feedHealthTimer) clearInterval(this.feedHealthTimer);
    this.securityIdToSymbol.clear();
  }

  getCurrentMode(): DataMode | null {
    return this.currentMode;
  }

  updateBrokerConfig(config: BrokerConfig): void {
    this.brokerConfig = config;
  }
}
