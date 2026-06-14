import { DhanFeedManager } from "./DhanFeedManager";
import type { BrokerFeedClient, FeedEvent, FeedTick, FeedDepth, FeedFill } from "./brokerFeedClient";

interface DhanConfig {
  accessToken: string;
  clientId: string;
  mode: "TICKER" | "QUOTE" | "FULL";
}

export class DhanBrokerFeedClient implements BrokerFeedClient {
  readonly broker = "DHAN";
  private manager: DhanFeedManager | null = null;
  private listeners = new Set<(e: FeedEvent) => void>();
  private connected = false;

  constructor(private readonly config: DhanConfig) {}

  async connect(symbols: string[]): Promise<void> {
    this.manager = new DhanFeedManager({
      accessToken: this.config.accessToken,
      clientId: this.config.clientId,
      onTick: (securityId, ltp) => this.emit({ type: "tick", data: this.mapTick(securityId, ltp) }),
      onQuote: (securityId, packet) => this.emit({ type: "tick", data: this.mapTick(securityId, packet.ltp) }),
      onDepth: (securityId, packet) => {
        const event: FeedEvent = { type: "depth", data: this.mapDepth(securityId, packet) };
        this.emit(event);
      },
      onConnect: () => {
        this.connected = true;
        this.emit({ type: "status", data: { connected: true } });
      },
      onDisconnect: (reason) => {
        this.connected = false;
        this.emit({ type: "status", data: { connected: false, reason } });
      },
      onError: (error) => this.emit({ type: "status", data: { connected: false, reason: error } }),
    });
    this.manager.connect(symbols);
  }

  disconnect(): void {
    if (this.manager) {
      this.manager.disconnect();
      this.manager = null;
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
      try { l(event); } catch (e) { console.error("[DhanBrokerFeedClient] listener error", e); }
    });
  }

  private mapTick(securityId: number, ltp: number): FeedTick {
    return { symbol: String(securityId), exchangeSegment: "NSE_EQ", ltp, ts: Date.now(), origin: "BROKER_LIVE" };
  }

  private mapDepth(securityId: number, packet: { bids: Array<{ price: number; quantity: number; orders: number }>; asks: Array<{ price: number; quantity: number; orders: number }> }): FeedDepth {
    return {
      symbol: String(securityId),
      exchangeSegment: "NSE_EQ",
      bids: packet.bids.map((b) => ({ price: b.price, quantity: b.quantity, orders: b.orders })),
      asks: packet.asks.map((a) => ({ price: a.price, quantity: a.quantity, orders: a.orders })),
      ts: Date.now(),
    };
  }
}

export function fillToEvent(args: { symbol: string; exchangeSegment: string; side: "BUY" | "SELL"; price: number; quantity: number; orderId: string }): FeedFill {
  return { ...args, ts: Date.now(), origin: "BROKER_LIVE" };
}
