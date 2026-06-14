/**
 * AUTO-GENERATED from docs/asyncapi.yaml — DO NOT EDIT MANUALLY.
 * Regenerate with: scripts/generate-gateway-types.sh
 *
 * Typed gateway WebSocket message payloads matching the AsyncAPI 3.0 spec.
 */

// ── Gateway Topic Enum ─────────────────────────────────

export enum GatewayTopic {
  MARKET_TICK = 0,
  MARKET_DEPTH = 1,
  CANDLE_DEVELOPING = 2,
  CANDLE_CLOSED = 3,
  ORDER_UPDATE = 4,
  POSITION_UPDATE = 5,
  STRATEGY_SIGNAL = 6,
  PNL_UPDATE = 7,
  REPLAY_CONTROL = 8,
  PIPELINE_HEALTH = 9,
  SCAN_COMPLETED = 10,
  DEPTH_IMBALANCE = 11,
  HEATMAP_CHUNK = 12,
  ICEBERG_ALERT = 13,
  ABSORPTION_ALERT = 14,
  SR_LEVELS_UPDATE = 15,
  ORDER_BOOK_SNAPSHOT = 16,
}

export const GATEWAY_TOPIC_NAMES: Record<number, string> = {
  0: "MARKET_TICK",
  1: "MARKET_DEPTH",
  2: "CANDLE_DEVELOPING",
  3: "CANDLE_CLOSED",
  4: "ORDER_UPDATE",
  5: "POSITION_UPDATE",
  6: "STRATEGY_SIGNAL",
  7: "PNL_UPDATE",
  8: "REPLAY_CONTROL",
  9: "PIPELINE_HEALTH",
  10: "SCAN_COMPLETED",
  11: "DEPTH_IMBALANCE",
  12: "HEATMAP_CHUNK",
  13: "ICEBERG_ALERT",
  14: "ABSORPTION_ALERT",
  15: "SR_LEVELS_UPDATE",
  16: "ORDER_BOOK_SNAPSHOT",
};

// ── Message Payloads ───────────────────────────────────

export interface MarketTickPayload {
  symbol: string;
  exchangeSegment: string;
  ltpPaisa: number;
  lastTradeQuantity: number;
  cumulativeVolume: number;
  exchangeTimestampEpochMs: number;
  openInterest: number;
  feedMode: string;
}

export interface MarketDepthPayload {
  symbol: string;
  bids: DepthLevelPayload[];
  asks: DepthLevelPayload[];
  ltpPaisa: number;
  exchangeTimestampMs: number;
}

export interface DepthLevelPayload {
  pricePaisa: number;
  quantity: number;
  orderCount: number;
}

export interface CandlePayload {
  candle: {
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
  };
}

export interface OrderUpdatePayload {
  orderId: string;
  symbol: string;
  status: string;
  quantity: number;
  filledQuantity: number;
  pricePaisa: number;
}

export interface PositionUpdatePayload {
  symbol: string;
  netQuantity: number;
  avgPricePaisa: number;
}

export interface StrategySignalPayload {
  signalId: string;
  symbol: string;
  side: string;
  strategy: string;
  confidence: number;
}

export interface PnlUpdatePayload {
  realizedPnlPaisa: number;
  unrealizedPnlPaisa: number;
  netExposurePaisa: number;
}

export interface ReplayControlPayload {
  command: "START" | "PLAY" | "PAUSE" | "STEP" | "STOP" | "SPEED";
  timestampMs: number;
}

export interface ScanCompletedPayload {
  runId: string;
  profileId: string;
  hitCount: number;
}

export interface DepthImbalancePayload {
  symbol: string;
  imbalance: number;
  bidVolume: number;
  askVolume: number;
}

export interface HeatmapChunkPayload {
  symbol: string;
  levels: number;
  data: string;
}

export interface IcebergAlertPayload {
  symbol: string;
  side: string;
  pricePaisa: number;
  detectedQuantity: number;
}

export interface AbsorptionAlertPayload {
  symbol: string;
  side: string;
  pricePaisa: number;
  absorbedVolume: number;
}

export interface SrLevelsUpdatePayload {
  symbol: string;
  support: number[];
  resistance: number[];
}

export interface OrderBookSnapshotPayload {
  symbol: string;
  bids: DepthLevelPayload[];
  asks: DepthLevelPayload[];
  exchangeTimestampMs: number;
}

// ── Topic → Payload Type Map ───────────────────────────

export interface GatewayPayloadMap {
  [GatewayTopic.MARKET_TICK]: MarketTickPayload;
  [GatewayTopic.MARKET_DEPTH]: MarketDepthPayload;
  [GatewayTopic.CANDLE_DEVELOPING]: CandlePayload;
  [GatewayTopic.CANDLE_CLOSED]: CandlePayload;
  [GatewayTopic.ORDER_UPDATE]: OrderUpdatePayload;
  [GatewayTopic.POSITION_UPDATE]: PositionUpdatePayload;
  [GatewayTopic.STRATEGY_SIGNAL]: StrategySignalPayload;
  [GatewayTopic.PNL_UPDATE]: PnlUpdatePayload;
  [GatewayTopic.REPLAY_CONTROL]: ReplayControlPayload;
  [GatewayTopic.PIPELINE_HEALTH]: string;
  [GatewayTopic.SCAN_COMPLETED]: ScanCompletedPayload;
  [GatewayTopic.DEPTH_IMBALANCE]: DepthImbalancePayload;
  [GatewayTopic.HEATMAP_CHUNK]: HeatmapChunkPayload;
  [GatewayTopic.ICEBERG_ALERT]: IcebergAlertPayload;
  [GatewayTopic.ABSORPTION_ALERT]: AbsorptionAlertPayload;
  [GatewayTopic.SR_LEVELS_UPDATE]: SrLevelsUpdatePayload;
  [GatewayTopic.ORDER_BOOK_SNAPSHOT]: OrderBookSnapshotPayload;
}

// ── Decoded Frame ──────────────────────────────────────

export interface GatewayFrame<T extends GatewayTopic = GatewayTopic> {
  topic: T;
  sequence: number;
  payload: GatewayPayloadMap[T];
}
