import type { BrokerFeedClient, FeedEvent, FeedTick, FeedDepth, FeedFill } from "./brokerFeedClient";
import type { DataOrigin } from "../store/types";

interface UpstoxConfig {
  accessToken: string;
}

export class UpstoxBrokerFeedClient implements BrokerFeedClient {
  readonly broker = "UPSTOX";
  private listeners = new Set<(e: FeedEvent) => void>();
  private ws: WebSocket | null = null;
  private connected = false;
  private readonly url: string;
  private readonly instrumentKeys: string[];

  constructor(private readonly config: UpstoxConfig, instrumentKeys: string[] = []) {
    this.instrumentKeys = instrumentKeys;
    this.url = `wss://api.upstox.com/v3/feed/market-data-feed?requestTimeout=30&requestType=1`;
  }

  async connect(symbols: string[]): Promise<void> {
    if (this.ws) this.disconnect();
    this.ws = new WebSocket(this.url);
    this.ws.binaryType = "arraybuffer";

    this.ws.onopen = () => {
      this.connected = true;
      this.emit({ type: "status", data: { connected: true } });
      this.sendSubscribe(symbols);
    };

    this.ws.onmessage = (event) => {
      try {
        this.handleBinaryFrame(event.data);
      } catch (e) {
        console.error("[UpstoxBrokerFeedClient] parse error", e);
      }
    };

    this.ws.onclose = (event) => {
      this.connected = false;
      this.emit({ type: "status", data: { connected: false, reason: `code ${event.code}` } });
    };

    this.ws.onerror = () => {
      this.emit({ type: "status", data: { connected: false, reason: "websocket error" } });
    };
  }

  disconnect(): void {
    if (this.ws) {
      this.ws.close(1000, "user-disconnect");
      this.ws = null;
    }
    this.connected = false;
  }

  subscribe(listener: (e: FeedEvent) => void): () => void {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  }

  isConnected(): boolean {
    return this.connected;
  }

  private emit(event: FeedEvent): void {
    this.listeners.forEach((l) => {
      try { l(event); } catch (e) { console.error("[UpstoxBrokerFeedClient] listener error", e); }
    });
  }

  private sendSubscribe(symbols: string[]): void {
    if (!this.ws || this.ws.readyState !== WebSocket.OPEN) return;
    const message = {
      guid: "tradej-upstox",
      method: "sub",
      data: {
        mode: "full",
        instrumentKeys: symbols,
      },
    };
    this.ws.send(JSON.stringify(message));
  }

  private handleBinaryFrame(_data: ArrayBuffer | string): void {
    // Upstox protobuf schema (v3) is required to decode ltp/depth/ff.
    // Intentionally a no-op until the proto is wired into the frontend bundle.
    // The backend's /api/v1/stream/read-model projection is the
    // canonical source; this client is for direct push only.
  }
}

export class IciciBrokerFeedClient implements BrokerFeedClient {
  readonly broker = "ICICI";
  private listeners = new Set<(e: FeedEvent) => void>();
  private connected = false;

  constructor(private readonly config: { sessionToken: string }) {}

  async connect(symbols: string[]): Promise<void> {
    // ICICI Breeze does not expose a public WS market-data stream.
    // We poll the historical/REST quote endpoint instead.
    const promises = symbols.map((sym) => this.poll(sym));
    await Promise.allSettled(promises);
  }

  disconnect(): void {
    this.connected = false;
  }

  subscribe(listener: (e: FeedEvent) => void): () => void {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  }

  isConnected(): boolean {
    return this.connected;
  }

  private async poll(symbol: string): Promise<void> {
    const res = await fetch(`/api/v1/market/ltp?symbol=${encodeURIComponent(symbol)}&exchangeSegment=NSE_EQ`);
    if (!res.ok) {
      this.emit({ type: "status", data: { connected: false, reason: `HTTP ${res.status}` } });
      return;
    }
    const data: { ltpPaisa: number; symbol: string } = await res.json();
    const event: FeedEvent = {
      type: "tick",
      data: {
        symbol: data.symbol,
        exchangeSegment: "NSE_EQ",
        ltp: data.ltpPaisa / 100,
        ts: Date.now(),
        origin: "BROKER_LIVE" as DataOrigin,
      },
    };
    this.connected = true;
    this.emit(event);
    this.emit({ type: "status", data: { connected: true } });
  }

  private emit(event: FeedEvent): void {
    this.listeners.forEach((l) => {
      try { l(event); } catch (e) { console.error("[IciciBrokerFeedClient] listener error", e); }
    });
  }
}
