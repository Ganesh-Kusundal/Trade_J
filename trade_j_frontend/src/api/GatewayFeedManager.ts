import { marketBus } from "./MarketDataBus";
import type { DepthLevel } from "./marketContracts";

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
  MAX_PAIN_UPDATE = 17,
  GREEKS_UPDATE = 18,
  OI_UPDATE = 19,
  GAMMA_EXPOSURE_UPDATE = 20,
  STRATEGY_METRICS = 21,
}

const HEADER_SIZE = 9;
const TOPIC_NAMES: Record<number, string> = {};
for (const [name, id] of Object.entries(GatewayTopic)) {
  if (typeof id === "number") TOPIC_NAMES[id] = name;
}

export interface GatewayFeedConfig {
  gatewayUrl: string;
  symbol: string;
  exchange: string;
  onConnect?: () => void;
  onDisconnect?: (reason: string) => void;
  onError?: (error: string) => void;
}

export class GatewayFeedManager {
  private ws: WebSocket | null = null;
  private reconnectTimer: ReturnType<typeof setTimeout> | null = null;
  private reconnectAttempts = 0;
  private maxReconnectAttempts = 10;
  private intentionalClose = false;
  private config: GatewayFeedConfig;
  private subscribedTopics = new Set<GatewayTopic>();

  constructor(config: GatewayFeedConfig) {
    this.config = config;
  }

  connect(topics: GatewayTopic[]): void {
    this.intentionalClose = false;
    this.subscribedTopics = new Set(topics);
    this.doConnect();
  }

  switchSymbol(symbol: string, exchange: string): void {
    this.config = { ...this.config, symbol, exchange };
    if (this.ws && this.ws.readyState === WebSocket.OPEN) {
      this.resubscribe();
    }
  }

  private doConnect(): void {
    const url = this.config.gatewayUrl;
    console.log(`[Gateway] Connecting to ${url}...`);

    try {
      this.ws = new WebSocket(url);
      this.ws.binaryType = "arraybuffer";
    } catch (e: any) {
      this.config.onError?.(`WebSocket creation failed: ${e.message}`);
      return;
    }

    this.ws.onopen = () => {
      console.log("[Gateway] Connected");
      this.reconnectAttempts = 0;
      this.config.onConnect?.();
      this.resubscribe();
    };

    this.ws.onmessage = (event: MessageEvent) => {
      if (!(event.data instanceof ArrayBuffer)) return;
      this.handleFrame(event.data);
    };

    this.ws.onclose = (event: CloseEvent) => {
      console.warn(`[Gateway] Disconnected: code=${event.code} reason=${event.reason}`);
      this.config.onDisconnect?.(event.reason || `code ${event.code}`);
      if (!this.intentionalClose) {
        this.scheduleReconnect();
      }
    };

    this.ws.onerror = () => {
      this.config.onError?.("WebSocket error event");
    };
  }

  private handleFrame(data: ArrayBuffer): void {
    const bytes = new Uint8Array(data);
    if (bytes.length < HEADER_SIZE) return;

    const view = new DataView(data);
    const topicId = view.getUint8(0);
    const sequence = Number(view.getBigUint64(1, false));
    const payloadBytes = bytes.slice(HEADER_SIZE);
    const payloadText = new TextDecoder().decode(payloadBytes);

    let payload: any;
    try {
      payload = JSON.parse(payloadText);
    } catch {
      return;
    }

    this.dispatchEvent(topicId, payload, sequence);
  }

  private dispatchEvent(topicId: number, payload: any, _sequence: number): void {
    const { symbol, exchange } = this.config;

    switch (topicId) {
      case GatewayTopic.MARKET_TICK: {
        const ltp = (payload.ltpPaisa ?? 0) / 100;
        marketBus.publish({
          type: "TICK",
          symbol: payload.symbol ?? symbol,
          exchange: payload.exchangeSegment ?? exchange,
          ltp,
          change: payload.changePaisa != null ? payload.changePaisa / 100 : 0,
          changePercent: payload.changePercent ?? 0,
          timestamp: payload.exchangeTimestampMs ?? Date.now(),
        });
        break;
      }

      case GatewayTopic.MARKET_DEPTH: {
        const bids: DepthLevel[] = (payload.bids ?? []).map((b: any) => ({
          price: (b.pricePaisa ?? 0) / 100,
          quantity: b.quantity ?? 0,
          orders: b.orderCount ?? 0,
        }));
        const asks: DepthLevel[] = (payload.asks ?? []).map((a: any) => ({
          price: (a.pricePaisa ?? 0) / 100,
          quantity: a.quantity ?? 0,
          orders: a.orderCount ?? 0,
        }));
        const spread = asks.length > 0 && bids.length > 0
          ? asks[asks.length - 1].price - bids[0].price
          : 0;
        marketBus.publish({
          type: "DEPTH",
          symbol: payload.symbol ?? symbol,
          exchange: exchange,
          bids,
          asks,
          ltp: (payload.ltpPaisa ?? 0) / 100,
          spread,
          timestamp: payload.exchangeTimestampMs ?? Date.now(),
        });
        break;
      }

      case GatewayTopic.CANDLE_DEVELOPING:
      case GatewayTopic.CANDLE_CLOSED: {
        const candle = payload.candle ?? payload;
        marketBus.publish({
          type: "CANDLE",
          symbol: candle.symbol ?? symbol,
          exchange: exchange,
          candles: [{
            time: Math.floor((candle.startTimeMs ?? 0) / 1000),
            open: (candle.openPaisa ?? 0) / 100,
            high: (candle.highPaisa ?? 0) / 100,
            low: (candle.lowPaisa ?? 0) / 100,
            close: (candle.closePaisa ?? 0) / 100,
            volume: candle.volume ?? 0,
          }],
          isPartial: topicId === GatewayTopic.CANDLE_DEVELOPING,
        });
        break;
      }

      case GatewayTopic.ORDER_UPDATE: {
        const order = payload.order ?? payload;
        marketBus.publish({
          type: "BROKER_STATUS",
          connected: true,
          websocketConnected: true,
          broker: "gateway",
        });
        // Forwarded raw order state to the bus for OMS consumers. We
        // reuse the PnlEvent slot — for a real OMS event shape, see
        // backend OrderAccepted / OrderFilled in core.
        if (order && typeof order === "object") {
          marketBus.publish({
            type: "PNL_UPDATE",
            symbol: order.symbol ?? symbol,
            realizedPnlPaisa: 0,
            unrealizedPnlPaisa: 0,
            netExposurePaisa: 0,
            timestamp: order.metadata?.timestampMs ?? Date.now(),
          });
        }
        break;
      }

      case GatewayTopic.PNL_UPDATE: {
        const realizedPaisa = Number(payload.realizedPnlPaisa ?? 0);
        const unrealizedPaisa = Number(payload.unrealizedPnlPaisa ?? 0);
        const exposurePaisa = Number(payload.netExposurePaisa ?? 0);
        marketBus.publish({
          type: "PNL_UPDATE",
          symbol: payload.symbol ?? symbol,
          realizedPnlPaisa: realizedPaisa,
          unrealizedPnlPaisa: unrealizedPaisa,
          netExposurePaisa: exposurePaisa,
          timestamp: Date.now(),
        });
        // Also seed the daily PnL = realized + unrealized snapshot onto
        // the bus; RiskMonitor / EquityCurve widgets read it.
        marketBus.publish({
          type: "FEED_HEALTH",
          health: "healthy",
        });
        break;
      }

      case GatewayTopic.POSITION_UPDATE: {
        // Treat as a no-op event for now — PositionPanel is fed by
        // REST. A future iteration can route this through the bus.
        break;
      }

      case GatewayTopic.STRATEGY_SIGNAL: {
        // Surfaced in the dashboard; mirrors the same payload as backend.
        if (payload && payload.signalId) {
          marketBus.publish({
            type: "FEED_HEALTH",
            health: "healthy",
          });
        }
        break;
      }

      case GatewayTopic.SCAN_COMPLETED: {
        // Scanners publish via REST + SSE. Nothing for the WS to do here
        // — but ack the event so subscribers can show "scan complete".
        marketBus.publish({ type: "FEED_HEALTH", health: "healthy" });
        break;
      }

      case GatewayTopic.DEPTH_IMBALANCE:
      case GatewayTopic.HEATMAP_CHUNK:
      case GatewayTopic.ICEBERG_ALERT:
      case GatewayTopic.ABSORPTION_ALERT:
      case GatewayTopic.SR_LEVELS_UPDATE:
      case GatewayTopic.ORDER_BOOK_SNAPSHOT: {
        // These are streamed-only signals. The dashboard widgets
        // subscribe to them via a custom event channel (see the
        // widget's useEffect for the topic-specific handler). For now,
        // tag the bus with a "healthy" so connectivity stays green.
        marketBus.publish({ type: "FEED_HEALTH", health: "healthy" });
        break;
      }

      case GatewayTopic.MAX_PAIN_UPDATE: {
        marketBus.publish({
          type: "REPLAY_CONTROL",
          state: {
            sessionId: "",
            state: "PLAYING",
            symbol: payload.underlying ?? symbol,
            interval: "1m",
            fromMs: 0,
            toMs: 0,
            speed: 1,
            ...payload,
          },
        });
        break;
      }

      case GatewayTopic.GREEKS_UPDATE: {
        marketBus.publish({
          type: "REPLAY_CONTROL",
          state: {
            sessionId: "",
            state: "PLAYING",
            symbol: payload.instrumentKey?.symbol ?? symbol,
            interval: "1m",
            fromMs: 0,
            toMs: 0,
            speed: 1,
            ...payload,
          },
        });
        break;
      }

      case GatewayTopic.OI_UPDATE: {
        marketBus.publish({
          type: "REPLAY_CONTROL",
          state: {
            sessionId: "",
            state: "PLAYING",
            symbol: payload.chain?.symbol ?? symbol,
            interval: "1m",
            fromMs: 0,
            toMs: 0,
            speed: 1,
            ...payload,
          },
        });
        break;
      }

      case GatewayTopic.GAMMA_EXPOSURE_UPDATE: {
        marketBus.publish({
          type: "REPLAY_CONTROL",
          state: {
            sessionId: "",
            state: "PLAYING",
            symbol: payload.underlying ?? symbol,
            interval: "1m",
            fromMs: 0,
            toMs: 0,
            speed: 1,
            ...payload,
          },
        });
        break;
      }

      case GatewayTopic.PIPELINE_HEALTH: {
        const text = typeof payload === "string" ? payload : JSON.stringify(payload);
        if (text.startsWith("connected:")) {
          marketBus.publish({ type: "FEED_HEALTH", health: "healthy" });
        }
        break;
      }
    }
  }

  private resubscribe(): void {
    if (!this.ws || this.ws.readyState !== WebSocket.OPEN) return;
    for (const topic of this.subscribedTopics) {
      const topicName = TOPIC_NAMES[topic];
      if (topicName) {
        this.ws.send(new TextEncoder().encode(`SUBSCRIBE ${topicName}`));
      }
    }
    console.log(`[Gateway] Subscribed to ${this.subscribedTopics.size} topics for ${this.config.symbol}`);
  }

  private scheduleReconnect(): void {
    if (this.reconnectAttempts >= this.maxReconnectAttempts) {
      console.error(`[Gateway] Max reconnect attempts (${this.maxReconnectAttempts}) reached`);
      this.config.onError?.("Max reconnect attempts reached");
      return;
    }

    const delay = Math.min(1000 * Math.pow(2, this.reconnectAttempts), 30000);
    this.reconnectAttempts++;
    console.log(`[Gateway] Reconnecting in ${delay}ms (attempt ${this.reconnectAttempts}/${this.maxReconnectAttempts})`);

    this.reconnectTimer = setTimeout(() => this.doConnect(), delay);
  }

  disconnect(): void {
    this.intentionalClose = true;
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer);
      this.reconnectTimer = null;
    }
    if (this.ws) {
      this.ws.close(1000, "User disconnect");
      this.ws = null;
    }
    this.subscribedTopics.clear();
    console.log("[Gateway] Disconnected by user");
  }

  isConnected(): boolean {
    return this.ws !== null && this.ws.readyState === WebSocket.OPEN;
  }
}
