// ── Market Data ──────────────────────────────────────────────────
export interface Candle {
  startTimeMs: number;
  endTimeMs: number;
  openPaisa: number;
  highPaisa: number;
  lowPaisa: number;
  closePaisa: number;
  volume: number;
}

export interface LTPResponse {
  symbol: string;
  canonicalSymbol?: string;
  exchangeSegment: string;
  ltpPaisa: number;
}

export interface HistoricalCandlesResponse {
  symbol: string;
  canonicalSymbol?: string;
  exchangeSegment: string;
  interval: string;
  from: string;
  to: string;
  source?: string;
  count: number;
  candles: Candle[];
}

// ── Studio ───────────────────────────────────────────────────────
export interface StartupCandidate {
  symbol: string;
  rank: number;
  masterScore: number;
  rsScore: number;
  volumeExpansionScore: number;
  trendEfficiencyScore: number;
  openingDriveScore: number;
  closePaisa: number;
  barTimeMs: number;
}

export interface StartupCandidatesResponse {
  scanDate: string;
  scanTime: string;
  requestedScanTime?: string;
  chartLookbackDays?: number;
  selectionMode: string;
  provenance: Record<string, string | null>;
  candidates: StartupCandidate[];
}

export interface ChartMarker {
  type: string;
  timeMs: number;
  price: number;
}

export interface OrderBlockZone {
  startTimeMs: number;
  endTimeMs: number;
  top: number;
  bottom: number;
  bias: string;
}

export interface HalfTrendPoint {
  value: number;
  direction: string;
  high: number;
  low: number;
}

export interface CvdPoint {
  cvd: number;
  volumeDelta: number;
}

export interface StudioChartResponse {
  symbol: string;
  exchangeSegment: string;
  interval: string;
  from: string;
  to: string;
  count: number;
  candles: Candle[];
  halfTrend: HalfTrendPoint[];
  cvd: CvdPoint[];
  markers: ChartMarker[];
  orderBlockZones: OrderBlockZone[];
}

export interface IndicatorParams {
  amplitude: number;
  channelDeviation: number;
  atrPeriod: number;
}

// ── Symbols ──────────────────────────────────────────────────────
export interface SymbolInfo {
  symbol: string;
  canonicalSymbol: string;
  name: string;
  exchange: string;
  exchangeSegment: string;
  instrumentType: string;
  lotSize: number;
  tickSizePaisa: number;
  active: boolean;
  segment: string;
}

export interface SymbolsResponse {
  symbols: SymbolInfo[];
  count: number;
  totalCount: number;
  generatedAtMs: number;
  cacheTtlSeconds: number;
}

// ── Scanner ──────────────────────────────────────────────────────
export interface ScanRun {
  runId: string;
  profileId: string;
  startedAtMs: number;
  finishedAtMs: number;
  status: string;
  universeSize: number;
  hitCount: number;
  partialFailureCount: number;
  errorMessage: string | null;
}

export interface ScanHit {
  symbol: string;
  exchangeSegment: string;
  assetClass: string;
  underlying: string | null;
  score: number;
  reasons: string[];
  snapshot: Record<string, number>;
  promoted: boolean;
}

export interface ScanResult {
  run: ScanRun;
  hits: ScanHit[];
}

export interface ScanRunsResponse {
  profileId: string;
  runs: ScanRun[];
}

// ── Pipeline ─────────────────────────────────────────────────────
export interface NodeTypeDescriptor {
  typeId: string;
  displayName: string;
  category: string;
  description: string;
  inputEvents: {type: string; description: string}[];
  outputEvents: {type: string; description: string}[];
  configFields: {
    key: string;
    type: string;
    label: string;
    defaultValue: unknown;
    options: string[];
  }[];
}

export interface PipelineGraph {
  id: string;
  name: string;
  version: number;
  executionMode: string;
  nodes: PipelineNode[];
  edges: PipelineEdge[];
}

export interface PipelineNode {
  id: string;
  typeId: string;
  config: Record<string, unknown>;
}

export interface PipelineEdge {
  from: string;
  to: string;
  eventType: string;
}

// ── Read Model ───────────────────────────────────────────────────
export interface ReadModelSnapshot {
  version: number;
  orders: Record<string, unknown>[];
  positions: Record<string, unknown>[];
  ticks: Record<string, unknown>[];
  depths: Record<string, unknown>[];
  candles: Record<string, unknown>[];
  signals: Record<string, unknown>[];
  pnl: Record<string, unknown>;
}

// ── Admin ────────────────────────────────────────────────────────
export interface RuntimeInfo {
  websocketConnected: boolean;
  circuitBreakerOpen: boolean;
  subscriptions: number;
  catalogLoaded: boolean;
  catalogSize: number;
  brokerPreflightPassed: boolean;
  startupCompleted: boolean;
}

export interface StrategiesResponse {
  plugins: string[];
  pluginCount: number;
}

export interface StrategyPluginRow {
  name: string;
}

export interface KillSwitchResponse {
  enabled: boolean;
  acknowledged: boolean;
}

// ── Gateway WebSocket ────────────────────────────────────────────
export type GatewayMessage =
  | {type: 'TICK'; symbol: string; ltpPaisa: number; volume: number; timestampMs: number}
  | {type: 'CANDLE'; symbol: string; interval: string; candle: Candle}
  | {type: 'ORDER_UPDATE'; orderId: string; status: string; symbol: string; quantity: number; price: number}
  | {type: 'POSITION_UPDATE'; symbol: string; quantity: number; avgPrice: number; pnl: number}
  | {type: 'ERROR'; code: string; message: string};

// ── UI State ─────────────────────────────────────────────────────
export interface PerformanceMetrics {
  totalTrades: number;
  winCount: number;
  lossCount: number;
  winRate: number;
  netProfit: number;
  grossProfit: number;
  grossLoss: number;
  profitFactor: number;
  maxDrawdown: number;
  maxDrawdownPercent: number;
  sharpeRatio: number;
  returnOnCapital: number;
}

export interface ActivePosition {
  symbol: string;
  side: 'LONG' | 'SHORT';
  quantity: number;
  entryPrice: number;
  currentPrice: number;
  pnl: number;
  pnlPercent: number;
}

export interface Trade {
  id: string;
  symbol: string;
  side: 'BUY' | 'SELL';
  quantity: number;
  price: number;
  timestamp: number;
  pnl?: number;
}

export interface ScannerFilter {
  profile: string;
  minScore?: number;
  assetClass?: string;
}
