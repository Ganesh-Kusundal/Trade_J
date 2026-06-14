import type { DataOrigin } from "./types";
import { applyTick, applyDepth, applyFill, marketStore } from "./marketStore";
import { applyOrder, applyPositions, signalsStore } from "./ordersStore";

type ReadModelSnapshot = {
  version: number;
  orders: Array<Record<string, unknown>>;
  positions: Array<Record<string, unknown>>;
  ticks: Array<Record<string, unknown>>;
  depths: Array<Record<string, unknown>>;
  candles: Array<Record<string, unknown>>;
  signals: Array<Record<string, unknown>>;
  pnl: { realizedPnlPaisa: number; unrealizedPnlPaisa: number; netExposurePaisa: number };
};

const ORIGIN_BY_KEY: Record<string, DataOrigin> = {
  BROKER_LIVE: "BROKER_LIVE",
  REPLAY_VIRTUAL: "REPLAY_VIRTUAL",
  PAPER_MATCHED: "PAPER_MATCHED",
  SIMULATED: "SIMULATED",
};

function originOf(raw: unknown): DataOrigin {
  const key = typeof raw === "string" ? raw : "UNKNOWN";
  return ORIGIN_BY_KEY[key] ?? "UNKNOWN";
}

let lastVersion = -1;
let es: EventSource | null = null;
let retries = 0;
let retryTimer: ReturnType<typeof setTimeout> | null = null;

function dispatch(snapshot: ReadModelSnapshot): void {
  for (const t of snapshot.ticks ?? []) {
    const price = Number(t.pricePaisa ?? t.price ?? 0) / (t.pricePaisa != null ? 100 : 1);
    const ts = Number(t.timestampMs ?? t.ts ?? Date.now());
    if (price > 0) applyTick(price, ts, originOf(t.origin));
  }
  for (const d of snapshot.depths ?? []) {
    const map = (lvl: Record<string, unknown>) => ({
      price: Number(lvl.pricePaisa ?? lvl.price ?? 0) / (lvl.pricePaisa != null ? 100 : 1),
      quantity: Number(lvl.quantity ?? lvl.amount ?? 0),
      orders: Number(lvl.orders ?? lvl.orderCount ?? 1),
    });
    applyDepth(((d.bids as Array<Record<string, unknown>>) ?? []).map(map), ((d.asks as Array<Record<string, unknown>>) ?? []).map(map));
  }
  for (const o of snapshot.orders ?? []) {
    applyOrder({
      orderId: String(o.orderId ?? ""),
      symbol: String(o.symbol ?? ""),
      side: String(o.side ?? ""),
      quantity: Number(o.quantity ?? 0),
      filledQuantity: Number(o.filledQuantity ?? 0),
      pricePaisa: Number(o.pricePaisa ?? 0),
      status: String(o.status ?? "PENDING"),
      orderType: String(o.orderType ?? "LIMIT"),
      correlationId: o.correlationId ? String(o.correlationId) : undefined,
      rejectionReason: o.rejectionReason ? String(o.rejectionReason) : null,
    });
  }
  for (const f of (snapshot as Record<string, unknown>).fills as Array<Record<string, unknown>> ?? []) {
    applyFill({
      time: Number(f.time ?? f.ts ?? Date.now()),
      symbol: String(f.symbol ?? ""),
      side: (f.side === "SELL" ? "SELL" : "BUY") as "BUY" | "SELL",
      price: Number(f.pricePaisa ?? f.price ?? 0) / (f.pricePaisa != null ? 100 : 1),
      quantity: Number(f.quantity ?? 0),
      orderId: String(f.orderId ?? ""),
      origin: originOf(f.origin),
    });
  }
  const positions = (snapshot.positions ?? []).map((p) => ({
    symbol: String(p.symbol ?? ""),
    exchangeSegment: String(p.exchangeSegment ?? "NSE_EQ"),
    netQuantity: Number(p.netQuantity ?? 0),
    averagePricePaisa: Number(p.averagePricePaisa ?? 0),
    currentPricePaisa: Number(p.currentPricePaisa ?? 0),
    realizedPnlPaisa: Number(p.realizedPnlPaisa ?? 0),
    unrealizedPnlPaisa: Number(p.unrealizedPnlPaisa ?? 0),
  }));
  applyPositions(positions, snapshot.pnl ?? { realizedPnlPaisa: 0, unrealizedPnlPaisa: 0, netExposurePaisa: 0 });
  for (const s of snapshot.signals ?? []) {
    signalsStore.setState((prev) => ({
      signals: [{
        strategyId: String(s.strategyId ?? s.strategy ?? "unknown"),
        symbol: String(s.symbol ?? ""),
        side: (s.side === "SELL" ? "SELL" : "BUY") as "BUY" | "SELL",
        strength: Number(s.strength ?? 0),
        reason: String(s.reason ?? ""),
        ts: Number(s.ts ?? Date.now()),
      }, ...prev.signals].slice(0, 200),
    }));
  }
}

function connect(): void {
  if (es) {
    es.close();
    es = null;
  }
  es = new EventSource("/api/v1/stream/read-model");
  es.addEventListener("read-model", (event) => {
    try {
      const snapshot = JSON.parse((event as MessageEvent).data) as ReadModelSnapshot;
      if (snapshot.version === lastVersion) return;
      lastVersion = snapshot.version;
      dispatch(snapshot);
    } catch (e) {
      console.error("[read-model-stream] parse error", e);
    }
  });
  es.onopen = () => { retries = 0; };
  es.onerror = () => {
    if (es) es.close();
    es = null;
    const delay = Math.min(1000 * Math.pow(2, retries), 30000);
    retries++;
    retryTimer = setTimeout(connect, delay);
  };
}

export function startReadModelStream(): () => void {
  if (typeof window === "undefined") return () => {};
  connect();
  return () => {
    if (retryTimer) clearTimeout(retryTimer);
    if (es) es.close();
    es = null;
  };
}

export function currentSymbol(): string | null {
  return marketStore.getState().symbol;
}
