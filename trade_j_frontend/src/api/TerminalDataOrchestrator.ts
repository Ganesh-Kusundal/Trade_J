import { GatewayFeedManager, GatewayTopic } from "./GatewayFeedManager";
import { fetchCandles } from "./marketData";
import { marketBus } from "./MarketDataBus";
import type { OHLCVBar, Instrument } from "../domain/instrument";
import { MarketState } from "../domain/instrument";
import { marketApi } from "../generated/api";

export enum DataMode {
  LIVE = "LIVE",
  HISTORICAL = "HISTORICAL",
  SIMULATION = "SIMULATION",
}

export interface BrokerConfig {
  isConfigured: boolean;
}

export interface OrchestratorCallbacks {
  onBars?: (bars: OHLCVBar[]) => void;
  onLtp?: (ltp: number) => void;
  onDepth?: (bids: { price: number; quantity: number; orders: number }[], asks: { price: number; quantity: number; orders: number }[]) => void;
  onModeChange?: (mode: DataMode, marketState: MarketState) => void;
  onFeedHealth?: (health: "healthy" | "delayed" | "stale" | "disconnected") => void;
}

export class TerminalDataOrchestrator {
  private currentMode: DataMode | null = null;
  private gatewayFeed: GatewayFeedManager | null = null;
  private sessionTimer: ReturnType<typeof setInterval> | null = null;
  private feedHealthTimer: ReturnType<typeof setInterval> | null = null;
  private lastUpdateMs = Date.now();
  private callbacks: OrchestratorCallbacks;
  private brokerConfig: BrokerConfig;
  private currentExchange = "";
  private currentSymbol = "";
  private currentSegment = "";
  private currentInstrument: Instrument | null = null;
  private lastKnownMarketState: MarketState = MarketState.UNKNOWN;
  private unsubscribeBus: (() => void) | null = null;

  constructor(brokerConfig: BrokerConfig, callbacks: OrchestratorCallbacks) {
    this.brokerConfig = brokerConfig;
    this.callbacks = callbacks;
  }

  async initialize(exchange: string, instrument: Instrument, segment: string, interval = "1d", days = 365): Promise<void> {
    this.currentExchange = exchange;
    this.currentSymbol = instrument.symbol;
    this.currentSegment = segment;
    this.currentInstrument = instrument;

    await this.loadHistoricalBars(instrument, segment, interval, days);

    const mode = this.resolveMode();
    if (mode === this.currentMode) return;
    this.currentMode = mode;

    this.stopAllFeeds();

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

    this.startSessionPolling(exchange, instrument);
    this.startFeedHealthMonitoring();
    this.callbacks.onModeChange?.(mode, this.lastKnownMarketState);
  }

  private resolveMode(): DataMode {
    if (this.lastKnownMarketState === MarketState.OPEN) return DataMode.LIVE;
    return DataMode.HISTORICAL;
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
    this.gatewayFeed = new GatewayFeedManager({
      gatewayUrl: `${window.location.protocol === "https:" ? "wss:" : "ws:"}//${window.location.host}/ws/gateway`,
      symbol: instrument.symbol,
      exchange: this.currentExchange,
      onConnect: () => {
        this.lastUpdateMs = Date.now();
        this.callbacks.onFeedHealth?.("healthy");
      },
      onDisconnect: () => {
        this.callbacks.onFeedHealth?.("disconnected");
      },
      onError: () => {
        this.callbacks.onFeedHealth?.("stale");
      },
    });

    this.gatewayFeed.connect([
      GatewayTopic.MARKET_TICK,
      GatewayTopic.MARKET_DEPTH,
      GatewayTopic.CANDLE_DEVELOPING,
      GatewayTopic.CANDLE_CLOSED,
    ]);

    this.unsubscribeBus = marketBus.subscribe((event) => {
      this.lastUpdateMs = Date.now();
      if (event.type === "TICK") {
        this.callbacks.onLtp?.(event.ltp);
      } else if (event.type === "DEPTH") {
        this.callbacks.onDepth?.(event.bids, event.asks);
      }
    });
  }

  private startHistoricalFreeze(): void {
    if (!this.currentInstrument) return;
    marketApi.depth(this.currentInstrument.symbol).then(data => {
      const bids = (data.bids || []).map((b: any) => ({
        price: b.pricePaisa != null ? b.pricePaisa / 100 : (b.price ?? 0),
        quantity: b.quantity ?? b.amount ?? 0,
        orders: b.orderCount ?? b.orders ?? 1,
      }));
      const asks = (data.asks || []).map((a: any) => ({
        price: a.pricePaisa != null ? a.pricePaisa / 100 : (a.price ?? 0),
        quantity: a.quantity ?? a.amount ?? 0,
        orders: a.orderCount ?? a.orders ?? 1,
      }));
      this.callbacks.onDepth?.(bids, asks);
    }).catch(() => {});

    marketApi.ltp(this.currentInstrument.symbol, this.currentSegment).then(data => {
      this.callbacks.onLtp?.(data.ltpPaisa / 100);
    }).catch(() => {});
  }

  private startSimulation(instrument: Instrument, segment: string): void {
    const loadLtp = () => {
      marketApi.ltp(instrument.symbol, segment).then(data => {
        this.lastUpdateMs = Date.now();
        this.callbacks.onLtp?.(data.ltpPaisa / 100);
      }).catch(() => {});
    };

    const loadDepth = () => {
      marketApi.depth(instrument.symbol).then(data => {
        const bids = (data.bids || []).map((b: any) => ({
          price: b.pricePaisa != null ? b.pricePaisa / 100 : (b.price ?? 0),
          quantity: b.quantity ?? b.amount ?? 0,
          orders: b.orderCount ?? b.orders ?? 1,
        })).sort((a: any, b: any) => b.price - a.price);
        const asks = (data.asks || []).map((a: any) => ({
          price: a.pricePaisa != null ? a.pricePaisa / 100 : (a.price ?? 0),
          quantity: a.quantity ?? a.amount ?? 0,
          orders: a.orderCount ?? a.orders ?? 1,
        })).sort((a: any, b: any) => a.price - b.price);
        this.lastUpdateMs = Date.now();
        this.callbacks.onDepth?.(bids, asks);
      }).catch(() => {});
    };

    loadLtp();
    loadDepth();
    const ltpTimer = setInterval(loadLtp, 2000);
    const depthTimer = setInterval(loadDepth, 3000);
    this.sessionTimer = ltpTimer;
    this.feedHealthTimer = depthTimer;
  }

  private startSessionPolling(exchange: string, instrument: Instrument): void {
    const poll = async () => {
      try {
        const session = await marketApi.session(exchange) as any;
        this.lastKnownMarketState = session.state;
        const newMode = this.resolveMode();
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
      } catch { /* ignore */ }
    };
    poll();
    this.sessionTimer = setInterval(poll, 60000);
  }

  private startFeedHealthMonitoring(): void {
    this.feedHealthTimer = setInterval(() => {
      const age = Date.now() - this.lastUpdateMs;
      if (this.currentMode === DataMode.HISTORICAL) {
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
    if (this.gatewayFeed) {
      this.gatewayFeed.disconnect();
      this.gatewayFeed = null;
    }
    if (this.unsubscribeBus) {
      this.unsubscribeBus();
      this.unsubscribeBus = null;
    }
    if (this.sessionTimer) clearInterval(this.sessionTimer);
    if (this.feedHealthTimer) clearInterval(this.feedHealthTimer);
    this.sessionTimer = null;
    this.feedHealthTimer = null;
  }

  destroy(): void {
    this.stopAllFeeds();
  }

  getCurrentMode(): DataMode | null {
    return this.currentMode;
  }

  updateBrokerConfig(config: BrokerConfig): void {
    this.brokerConfig = config;
  }
}
