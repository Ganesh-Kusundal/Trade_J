import {create} from 'zustand';
import type {
  Candle,
  Quote,
  OptionChain,
  MarketDepth,
  DepthImbalance,
  HeatmapChunk,
  IcebergAlert,
  AbsorptionAlert,
  SRLevelsUpdate,
  OrderBookSnapshot,
} from '@/domain/dto';

export type Timeframe = '1m' | '3m' | '5m' | '15m' | '30m' | '1h' | '4h' | '1d';

export type DataMode = 'live' | 'replay' | 'backtest';

export type PanelKey =
  | 'watchlist'
  | 'charts'
  | 'optionChain'
  | 'orders'
  | 'positions'
  | 'logs';

export type TerminalUiState = {
  dataMode: DataMode;
  selectedSymbol: string | null;
  timeframe: Timeframe;
  leftSelectedExpiry: string | null;

  // charts behavior
  chartReplayCursorMs: number | null;
  chartIsPlaying: boolean;

  // ui layout
  activePanels: Record<PanelKey, boolean>;
  setPanelVisible: (key: PanelKey, visible: boolean) => void;
  togglePanel: (key: PanelKey) => void;

  // selection
  setSelectedSymbol: (symbol: string) => void;
  setTimeframe: (tf: Timeframe) => void;
  setExpiry: (expiry: string | null) => void;

  // websocket-like status (mocked in Phase 1)
  wsConnected: boolean;
  setWsConnected: (connected: boolean) => void;

  // data (domain DTOs) - filled by mock providers in Phase 1
  quoteBySymbol: Record<string, Quote>;
  candlesBySymbol: Record<string, Candle[]>;
  optionChainBySymbol: Record<string, OptionChain>;
  depthBySymbol: Record<string, MarketDepth>;

  setQuote: (q: Quote) => void;
  setCandles: (symbol: string, candles: Candle[]) => void;
  setOptionChain: (chain: OptionChain) => void;
  setDepth: (depth: MarketDepth) => void;

  // DOM analytics state
  imbalanceBySymbol: Record<string, DepthImbalance>;
  heatmapBySymbol: Record<string, HeatmapChunk>;
  icebergAlerts: IcebergAlert[];
  absorptionAlerts: AbsorptionAlert[];
  srLevelsBySymbol: Record<string, SRLevelsUpdate>;
  orderBookSnapshotBySymbol: Record<string, OrderBookSnapshot>;

  setImbalance: (imbalance: DepthImbalance) => void;
  setHeatmap: (chunk: HeatmapChunk) => void;
  addIcebergAlert: (alert: IcebergAlert) => void;
  addAbsorptionAlert: (alert: AbsorptionAlert) => void;
  setSRLevels: (update: SRLevelsUpdate) => void;
  setOrderBookSnapshot: (snapshot: OrderBookSnapshot) => void;
};

export const useTerminalStore = create<TerminalUiState>((set) => ({
  dataMode: 'live',
  selectedSymbol: null,
  timeframe: '5m',
  leftSelectedExpiry: null,

  chartReplayCursorMs: null,
  chartIsPlaying: false,

  activePanels: {
    watchlist: true,
    charts: true,
    optionChain: true,
    orders: true,
    positions: true,
    logs: true,
  },

  setPanelVisible: (key, visible) =>
    set((s) => ({
      activePanels: {...s.activePanels, [key]: visible},
    })),

  togglePanel: (key) =>
    set((s) => ({
      activePanels: {...s.activePanels, [key]: !s.activePanels[key]},
    })),

  setSelectedSymbol: (symbol) => set({selectedSymbol: symbol}),
  setTimeframe: (tf) => set({timeframe: tf}),
  setExpiry: (expiry) => set({leftSelectedExpiry: expiry}),

  wsConnected: true,
  setWsConnected: (connected) => set({wsConnected: connected}),

  quoteBySymbol: {},
  candlesBySymbol: {},
  optionChainBySymbol: {},
  depthBySymbol: {},

  setQuote: (q) =>
    set((s) => ({
      quoteBySymbol: {...s.quoteBySymbol, [q.symbol]: q},
    })),

  setCandles: (symbol, candles) =>
    set((s) => ({
      candlesBySymbol: {...s.candlesBySymbol, [symbol]: candles},
    })),

  setOptionChain: (chain) =>
    set((s) => ({
      optionChainBySymbol: {...s.optionChainBySymbol, [chain.symbol]: chain},
    })),

  setDepth: (depth) =>
    set((s) => ({
      depthBySymbol: {...s.depthBySymbol, [depth.symbol]: depth},
    })),

  imbalanceBySymbol: {},
  heatmapBySymbol: {},
  icebergAlerts: [],
  absorptionAlerts: [],
  srLevelsBySymbol: {},
  orderBookSnapshotBySymbol: {},

  setImbalance: (imbalance) =>
    set((s) => ({
      imbalanceBySymbol: {...s.imbalanceBySymbol, [imbalance.symbol]: imbalance},
    })),
  setHeatmap: (chunk) =>
    set((s) => ({
      heatmapBySymbol: {...s.heatmapBySymbol, [chunk.symbol]: chunk},
    })),
  addIcebergAlert: (alert) =>
    set((s) => ({
      icebergAlerts: [...s.icebergAlerts.slice(-49), alert],
    })),
  addAbsorptionAlert: (alert) =>
    set((s) => ({
      absorptionAlerts: [...s.absorptionAlerts.slice(-49), alert],
    })),
  setSRLevels: (update) =>
    set((s) => ({
      srLevelsBySymbol: {...s.srLevelsBySymbol, [update.symbol]: update},
    })),
  setOrderBookSnapshot: (snapshot) =>
    set((s) => ({
      orderBookSnapshotBySymbol: {...s.orderBookSnapshotBySymbol, [snapshot.symbol]: snapshot},
    })),
}));
