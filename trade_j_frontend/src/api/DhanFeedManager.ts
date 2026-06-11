import { resolveDhanInstrument } from "./dhanSecurityIds";
import { parseDhanPacket, DhanPacket, DhanTickerPacket, DhanQuotePacket, DhanDepthPacket } from "./dhanPacketParser";

export interface DhanFeedConfig {
  accessToken: string;
  clientId: string;
  onTick?: (securityId: number, ltp: number) => void;
  onQuote?: (securityId: number, packet: DhanQuotePacket) => void;
  onDepth?: (securityId: number, packet: DhanDepthPacket) => void;
  onConnect?: () => void;
  onDisconnect?: (reason: string) => void;
  onError?: (error: string) => void;
}

interface SubscribedInstrument {
  securityId: string;
  exchangeSegment: string;
  symbol: string;
}

export class DhanFeedManager {
  private ws: WebSocket | null = null;
  private reconnectTimer: ReturnType<typeof setTimeout> | null = null;
  private reconnectAttempts = 0;
  private maxReconnectAttempts = 10;
  private subscribed: Map<string, SubscribedInstrument> = new Map();
  private config: DhanFeedConfig;
  private intentionalClose = false;

  constructor(config: DhanFeedConfig) {
    this.config = config;
  }

  connect(symbols: string[]): boolean {
    const instruments: SubscribedInstrument[] = [];
    for (const sym of symbols) {
      const resolved = resolveDhanInstrument(sym);
      if (resolved) {
        instruments.push({ ...resolved, symbol: sym });
      } else {
        console.warn(`[DhanFeed] No security ID for symbol: ${sym}`);
      }
    }
    if (instruments.length === 0) {
      console.error("[DhanFeed] No valid instruments to subscribe");
      return false;
    }

    instruments.forEach(i => this.subscribed.set(i.securityId, i));
    this.intentionalClose = false;
    this.doConnect(instruments);
    return true;
  }

  private doConnect(instruments: SubscribedInstrument[]): void {
    const url = `wss://api-feed.dhan.co?version=2&token=${this.config.accessToken}&clientId=${this.config.clientId}`;
    console.log(`[DhanFeed] Connecting to ${url.substring(0, 40)}...`);

    try {
      this.ws = new WebSocket(url);
      this.ws.binaryType = "arraybuffer";
    } catch (e: any) {
      this.config.onError?.(`WebSocket creation failed: ${e.message}`);
      return;
    }

    this.ws.onopen = () => {
      console.log("[DhanFeed] Connected");
      this.reconnectAttempts = 0;
      this.config.onConnect?.();
      this.subscribe(instruments);
    };

    this.ws.onmessage = (event: MessageEvent) => {
      if (!(event.data instanceof ArrayBuffer)) return;
      const packet = parseDhanPacket(event.data);
      if (!packet) return;
      this.dispatchPacket(packet);
    };

    this.ws.onclose = (event: CloseEvent) => {
      console.warn(`[DhanFeed] Disconnected: code=${event.code} reason=${event.reason}`);
      this.config.onDisconnect?.(event.reason || `code ${event.code}`);
      if (!this.intentionalClose) {
        this.scheduleReconnect();
      }
    };

    this.ws.onerror = () => {
      this.config.onError?.("WebSocket error event");
    };
  }

  private dispatchPacket(packet: DhanPacket): void {
    switch (packet.type) {
      case 2:
        this.config.onTick?.(packet.securityId, packet.ltp);
        break;
      case 3:
        this.config.onTick?.(packet.securityId, packet.ltp);
        this.config.onQuote?.(packet.securityId, packet);
        break;
      case 4:
      case 5:
      case 21:
        this.config.onTick?.(packet.securityId, packet.ltp);
        this.config.onDepth?.(packet.securityId, packet);
        break;
    }
  }

  private subscribe(instruments: SubscribedInstrument[]): void {
    if (!this.ws || this.ws.readyState !== WebSocket.OPEN) return;

    // Request code 21 = subscribe full packet (OHLC + depth + LTP)
    const message = {
      RequestCode: 21,
      InstrumentCount: instruments.length,
      InstrumentList: instruments.map(i => ({
        ExchangeSegment: i.exchangeSegment,
        SecurityId: i.securityId,
      })),
    };

    this.ws.send(JSON.stringify(message));
    console.log(`[DhanFeed] Subscribed to ${instruments.length} instruments: ${instruments.map(i => i.symbol).join(", ")}`);
  }

  private scheduleReconnect(): void {
    if (this.reconnectAttempts >= this.maxReconnectAttempts) {
      console.error(`[DhanFeed] Max reconnect attempts (${this.maxReconnectAttempts}) reached`);
      this.config.onError?.("Max reconnect attempts reached");
      return;
    }

    const delay = Math.min(1000 * Math.pow(2, this.reconnectAttempts), 30000);
    this.reconnectAttempts++;
    console.log(`[DhanFeed] Reconnecting in ${delay}ms (attempt ${this.reconnectAttempts}/${this.maxReconnectAttempts})`);

    this.reconnectTimer = setTimeout(() => {
      const instruments = Array.from(this.subscribed.values());
      this.doConnect(instruments);
    }, delay);
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
    this.subscribed.clear();
    console.log("[DhanFeed] Disconnected by user");
  }

  isConnected(): boolean {
    return this.ws !== null && this.ws.readyState === WebSocket.OPEN;
  }

  addSymbol(symbol: string): void {
    const resolved = resolveDhanInstrument(symbol);
    if (!resolved) return;
    const instrument: SubscribedInstrument = { ...resolved, symbol };
    this.subscribed.set(resolved.securityId, instrument);
    if (this.ws && this.ws.readyState === WebSocket.OPEN) {
      this.subscribe([instrument]);
    }
  }

  removeSymbol(symbol: string): void {
    const resolved = resolveDhanInstrument(symbol);
    if (!resolved) return;
    this.subscribed.delete(resolved.securityId);
    if (this.ws && this.ws.readyState === WebSocket.OPEN) {
      const message = {
        RequestCode: 22, // unsubscribe
        InstrumentCount: 1,
        InstrumentList: [{ ExchangeSegment: resolved.exchangeSegment, SecurityId: resolved.securityId }],
      };
      this.ws.send(JSON.stringify(message));
    }
  }
}
