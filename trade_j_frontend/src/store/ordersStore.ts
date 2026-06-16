import { createStore, useStore } from "./createStore";
import type { Order, Position, Signal, ScanResult } from "./types";

interface OrdersState {
  active: Order[];
  completed: Order[];
}

const EMPTY_ORDERS: OrdersState = { active: [], completed: [] };
export const ordersStore = createStore<OrdersState>(EMPTY_ORDERS);

const ACTIVE_STATUSES = new Set(["PENDING", "OPEN", "PARTIALLY_FILLED", "ACCEPTED", "TRIGGER_PENDING"]);
const COMPLETED_STATUSES = new Set(["TRADED", "FILLED", "CANCELLED", "REJECTED", "EXPIRED"]);

function bucket(status: string): "active" | "completed" {
  return ACTIVE_STATUSES.has(status) ? "active" : COMPLETED_STATUSES.has(status) ? "completed" : "completed";
}

export function applyOrder(order: Order): void {
  ordersStore.setState((s) => {
    const active = s.active.filter((o) => o.orderId !== order.orderId);
    const completed = s.completed.filter((o) => o.orderId !== order.orderId);
    const target = bucket(order.status);
    if (target === "active") active.unshift(order);
    else completed.unshift(order);
    return { active, completed };
  });
}

export const useOrders = <S,>(selector: (s: OrdersState) => S) => useStore(ordersStore, selector);

interface PositionsState {
  positions: Position[];
  realizedPnlPaisa: number;
  unrealizedPnlPaisa: number;
  netExposurePaisa: number;
}
const EMPTY_POSITIONS: PositionsState = { positions: [], realizedPnlPaisa: 0, unrealizedPnlPaisa: 0, netExposurePaisa: 0 };
export const positionsStore = createStore<PositionsState>(EMPTY_POSITIONS);

export function applyPositions(positions: Position[], pnl: { realizedPnlPaisa: number; unrealizedPnlPaisa: number; netExposurePaisa: number }): void {
  positionsStore.setState({ positions, ...pnl });
}

export const usePositions = <S,>(selector: (s: PositionsState) => S) => useStore(positionsStore, selector);

interface SignalsState { signals: Signal[]; }
const EMPTY_SIGNALS: SignalsState = { signals: [] };
export const signalsStore = createStore<SignalsState>(EMPTY_SIGNALS);

export function applySignal(signal: Signal): void {
  signalsStore.setState((s) => ({ signals: [signal, ...s.signals].slice(0, 200) }));
}

export const useSignals = <S,>(selector: (s: SignalsState) => S) => useStore(signalsStore, selector);

// ── Scanner ──

interface ScannerState { latest: ScanResult | null; history: ScanResult[]; }
const EMPTY_SCANNER: ScannerState = { latest: null, history: [] };
export const scannerStore = createStore<ScannerState>(EMPTY_SCANNER);

export function applyScannerResult(result: ScanResult): void {
  scannerStore.setState((s) => ({
    latest: result,
    history: [result, ...s.history].slice(0, 50),
  }));
}

export const useScanner = <S,>(selector: (s: ScannerState) => S) => useStore(scannerStore, selector);
