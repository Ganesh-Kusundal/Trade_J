import { createStore, useStore } from "./createStore";
import type { HealthSnapshot } from "./types";

const EMPTY_HEALTH: HealthSnapshot = {
  brokerUp: false,
  websocketConnected: false,
  broker: "unknown",
  marketDataUp: false,
  lastTickMs: 0,
};

export const healthStore = createStore<HealthSnapshot>(EMPTY_HEALTH);

export function applyHealth(snapshot: Partial<HealthSnapshot>): void {
  healthStore.setState((s) => ({ ...s, ...snapshot }));
}

export const useHealth = <S,>(selector: (s: HealthSnapshot) => S) => useStore(healthStore, selector);
