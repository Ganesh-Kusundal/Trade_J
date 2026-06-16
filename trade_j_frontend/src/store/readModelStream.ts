import { applyTick, applyDepth, marketStore } from "./marketStore";
import { applyOrder, applyPositions, signalsStore, applyScannerResult } from "./ordersStore";
import type { ScanHit } from "./types";
import type {
  ReadModelSnapshot,
} from "../api/readModelContracts";

let lastVersion = -1;
let es: EventSource | null = null;
let retries = 0;
let retryTimer: ReturnType<typeof setTimeout> | null = null;

function dispatch(snapshot: ReadModelSnapshot): void {
  // ── Ticks ──
  for (const t of snapshot.ticks ?? []) {
    const price = t.ltpPaisa / 100;
    if (price > 0) applyTick(price, t.exchangeTimestampMs, "BROKER_LIVE");
  }
  // ── Depths ──
  for (const d of snapshot.depths ?? []) {
    const mapLevel = (lvl: { pricePaisa: number; quantity: number; orders: number }) => ({
      price: lvl.pricePaisa / 100,
      quantity: lvl.quantity,
      orders: lvl.orders,
    });
    applyDepth((d.bids ?? []).map(mapLevel), (d.asks ?? []).map(mapLevel));
  }
  // ── Orders ──
  for (const o of snapshot.orders ?? []) {
    applyOrder({
      orderId: o.orderId,
      symbol: o.symbol,
      side: o.side,
      quantity: o.quantity,
      filledQuantity: o.filledQuantity,
      pricePaisa: o.pricePaisa,
      status: o.status,
      orderType: o.orderType,
    });
  }
  // ── Positions + PnL ──
  const positions = (snapshot.positions ?? []).map((p): import("./types").Position => ({
    symbol: p.symbol,
    exchangeSegment: "NSE_EQ",
    netQuantity: p.netQuantity,
    averagePricePaisa: p.avgPricePaisa,
    currentPricePaisa: 0,
    realizedPnlPaisa: 0,
    unrealizedPnlPaisa: 0,
  }));
  applyPositions(positions, snapshot.pnl ?? { realizedPnlPaisa: 0, unrealizedPnlPaisa: 0, netExposurePaisa: 0 });
  // ── Signals ──
  for (const s of snapshot.signals ?? []) {
    signalsStore.setState((prev) => ({
      signals: [{
        strategyId: s.strategy ?? "unknown",
        symbol: s.symbol,
        side: (s.side === "SELL" ? "SELL" : "BUY") as "BUY" | "SELL",
        strength: 0,
        reason: "",
        ts: Date.now(),
      }, ...prev.signals].slice(0, 200),
    }));
  }
  // ── Scanner results ──
  if (snapshot.latestScan) {
    const scan = snapshot.latestScan;
    const hits: ScanHit[] = (scan.hits ?? []).map((h) => ({
      symbol: h.symbol,
      exchangeSegment: h.exchangeSegment,
      underlying: "",
      score: h.score,
      reasons: h.reasons ?? [],
    }));
    applyScannerResult({
      profileId: scan.profileId,
      runId: scan.runId,
      hitCount: hits.length,
      startedAtMs: 0,
      finishedAtMs: Date.now(),
      hits,
    });
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
