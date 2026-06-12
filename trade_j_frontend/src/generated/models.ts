/**
 * AUTO-GENERATED from docs/openapi.yaml — DO NOT EDIT MANUALLY.
 * Regenerate with: scripts/generate-api-client.sh
 *
 * TypeScript models matching the Trade-J OpenAPI 3.1 specification.
 */

// ── Enums ──────────────────────────────────────────────

export enum ExchangeSegment {
  NSE_EQ = "NSE_EQ",
  NSE_FNO = "NSE_FNO",
  BSE_EQ = "BSE_EQ",
  BSE_FNO = "BSE_FNO",
  MCX_COMM = "MCX_COMM",
  IDX_I = "IDX_I",
  NSE_CURRENCY = "NSE_CURRENCY",
  BSE_CURRENCY = "BSE_CURRENCY",
}

export enum OptionType {
  CALL = "CALL",
  PUT = "PUT",
  UNKNOWN = "UNKNOWN",
}

export enum Side {
  BUY = "BUY",
  SELL = "SELL",
}

export enum OrderType {
  MARKET = "MARKET",
  LIMIT = "LIMIT",
  STOP_LOSS = "STOP_LOSS",
  STOP_LOSS_MARKET = "STOP_LOSS_MARKET",
}

export enum ProductType {
  INTRADAY = "INTRADAY",
  CNC = "CNC",
  MARGIN = "MARGIN",
  CARRY_FORWARD = "CARRY_FORWARD",
}

export enum Validity {
  DAY = "DAY",
  IOC = "IOC",
}

export enum PipelineExecutionMode {
  HOT_PATH = "HOT_PATH",
  DAG = "DAG",
}

export enum EventCategory {
  MARKET = "market",
  ORDER = "order",
  TRADE = "trade",
  SIGNAL = "signal",
  POSITION = "position",
  RISK = "risk",
  SCAN = "scan",
  ANALYTICS = "analytics",
  STRATEGY = "strategy",
  INFRA = "infra",
  EXECUTION = "execution",
  OTHER = "other",
}

export enum CredentialFieldType {
  TEXT = "text",
  PASSWORD = "password",
  FILE = "file",
}

// ── Market Data ────────────────────────────────────────

export interface Candle {
  symbol: string;
  interval: string;
  startTimeMs: number;
  endTimeMs: number;
  openPaisa: number;
  highPaisa: number;
  lowPaisa: number;
  closePaisa: number;
  volume: number;
  closed: boolean;
}

export interface CandleResponse {
  symbol: string;
  canonicalSymbol: string;
  exchangeSegment: string;
  interval: string;
  from: string;
  to: string;
  source: string;
  count: number;
  candles: CandleData[];
}

export interface CandleData {
  symbol: string;
  canonicalSymbol: string;
  startTimeMs: number;
  endTimeMs: number;
  openPaisa: number;
  highPaisa: number;
  lowPaisa: number;
  closePaisa: number;
  volume: number;
}

export interface LtpResponse {
  symbol: string;
  canonicalSymbol: string;
  exchangeSegment: string;
  ltpPaisa: number;
}

export interface DepthLevel {
  pricePaisa?: number;
  quantity?: number;
  orders?: number;
  price?: number;
  amount?: number;
}

export interface DepthResponse {
  symbol: string;
  segment: string;
  bids: DepthLevel[];
  asks: DepthLevel[];
  midPricePaisa: number;
  spreadPaisa: number;
  cumulativeBidVol: number;
  cumulativeAskVol: number;
  imbalance: number;
  timestampMs: number;
}

// ── Broker Registry ────────────────────────────────────

export interface BrokerDescriptorView {
  source: string;
  displayName: string;
  supportedSegments: string[];
  capabilities: Record<string, boolean>;
  credentialFields: CredentialFieldView[];
  version: string;
}

export interface CredentialFieldView {
  key: string;
  label: string;
  type: CredentialFieldType;
  placeholder: string;
}

// ── Orders ─────────────────────────────────────────────

export interface PlaceOrderRequest {
  symbol: string;
  exchangeSegment: ExchangeSegment;
  side: Side;
  quantity: number;
  orderType: OrderType;
  pricePaisa: number;
  triggerPricePaisa: number | null;
  productType: ProductType;
  validity: Validity;
  correlationId?: string;
}

export interface OrderResponse {
  orderId: string;
  correlationId: string;
  symbol: string;
  exchangeSegment: ExchangeSegment;
  side: Side;
  productType: string;
  orderType: string;
  status: string;
  quantity: number;
  filledQuantity: number;
  pricePaisa: number;
  triggerPricePaisa: number;
  exchangeTimeMs: number;
  rejectionReason: string | null;
}

export interface OrderProjectionResponse {
  orderId: string;
  symbol: string;
  totalQuantity: number;
  filledQuantity: number;
  averagePricePaisa: number;
  status: string;
}

// ── Read Model ─────────────────────────────────────────

export interface ReadModelSnapshot {
  orders: OrderView[];
  positions: PositionView[];
  ticks: TickView[];
  depths: DepthView[];
  candles: CandleView[];
  signals: SignalView[];
  pnl: PnlView;
  latestScan: ScanResultView | null;
  version: number;
}

export interface OrderView {
  orderId: string;
  symbol: string;
  status: string;
  quantity: number;
}

export interface PositionView {
  symbol: string;
  netQuantity: number;
  avgPricePaisa: number;
}

export interface TickView {
  symbol: string;
  ltpPaisa: number;
  exchangeTimestampMs: number;
}

export interface DepthView {
  symbol: string;
  bids: DepthLevelView[];
  asks: DepthLevelView[];
  exchangeTimestampMs: number;
}

export interface DepthLevelView {
  pricePaisa: number;
  quantity: number;
  orderCount: number;
}

export interface CandleView {
  symbol: string;
  interval: string;
  closePaisa: number;
  volume: number;
  closed: boolean;
}

export interface SignalView {
  signalId: string;
  symbol: string;
  side: string;
  strategy: string;
}

export interface PnlView {
  realizedPnlPaisa: number;
  unrealizedPnlPaisa: number;
  netExposurePaisa: number;
}

export interface ScanResultView {
  runId: string;
  profileId: string;
  hitCount: number;
  hits: ScanHitView[];
}

export interface ScanHitView {
  symbol: string;
  exchangeSegment: string;
  score: number;
  reasons: string[];
}

// ── Pipeline ───────────────────────────────────────────

export interface PipelineGraph {
  id: string;
  name: string;
  version: number;
  nodes: PipelineNodeDef[];
  edges: PipelineEdgeDef[];
  executionMode: PipelineExecutionMode;
}

export interface PipelineNodeDef {
  id: string;
  type: string;
  label: string;
  config: Record<string, unknown>;
}

export interface PipelineEdgeDef {
  id: string;
  source: string;
  target: string;
}

// ── Analytics ──────────────────────────────────────────

export interface AnalyticsCatalogSnapshot {
  equityRoot: string;
  optionsWarehousePath: string;
  equitySymbolCount: number;
  equityBarFileCount: number;
  equityMinMonth: string;
  equityMaxMonth: string;
  universeRowCount: number;
  optionsBarCount: number;
  optionsMinDate: string;
  optionsMaxDate: string;
  optionsAttached: boolean;
  runtimeAttached: boolean;
  views: Record<string, unknown>;
}

export interface AnalyticsQueryResult {
  columns: string[];
  rows: Record<string, unknown>[];
  rowCount: number;
  elapsedMs: number;
}

// ── Events ─────────────────────────────────────────────

export interface EventCatalogView {
  name: string;
  className: string;
  category: EventCategory;
  schemaVersion: number;
  fields: string[];
}

// ── Features ───────────────────────────────────────────

export interface FeatureView {
  name: string;
  enabled: boolean;
}

// ── Discovery ──────────────────────────────────────────

export interface DiscoveryResponse {
  brokers: { source: string; displayName: string; segments: string[] }[];
  strategies: { name: string }[];
  indicators: { name: string }[];
  transformations: { name: string }[];
  scanners: { name: string }[];
  events: { name: string; category: string; schemaVersion: number }[];
  features: { name: string; enabled: boolean }[];
  nodeTypes: { typeId: string; category: string }[];
}

// ── Symbols ────────────────────────────────────────────

export interface Symbol {
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
  symbols: Symbol[];
}

// ── Health ─────────────────────────────────────────────

export interface HealthResponse {
  status: "UP" | "DOWN";
  groups: string[];
  components: Record<string, { status: string; details?: Record<string, unknown> }>;
}

// ── Download Jobs ──────────────────────────────────────

export interface RollingOptionJobRequest {
  symbols: string;
  exchangeSegment: ExchangeSegment;
  from: string;
  to: string;
  intervals: string;
  expiries: string;
  strikes: string;
  optionTypes: string;
  delayMs: number;
  resume: boolean;
  runAsync: boolean;
}

export interface EquityJobRequest {
  universe: string;
  symbols: string;
  exchangeSegment: ExchangeSegment;
  from: string;
  to: string;
  intervals: string;
  delayMs: number;
  workers: number;
  refreshUniverse: boolean;
  resume: boolean;
  runAsync: boolean;
}

// ── Error ──────────────────────────────────────────────

export interface ErrorResponse {
  code: string;
  message: string;
  details: string;
  timestamp: number;
}
