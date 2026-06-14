import type { MarketEvent } from "./marketContracts";

type Listener = (event: MarketEvent) => void;

export class MarketDataBus {
  private listeners = new Set<Listener>();
  private lastEvents = new Map<string, MarketEvent>();

  subscribe(listener: Listener): () => void {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  }

  publish(event: MarketEvent): void {
    this.lastEvents.set(event.type, event);
    this.listeners.forEach(l => l(event));
  }

  getLast<T extends MarketEvent>(type: T["type"]): T | undefined {
    return this.lastEvents.get(type) as T | undefined;
  }

  clear(): void {
    this.lastEvents.clear();
  }

  get listenerCount(): number {
    return this.listeners.size;
  }
}

export const marketBus = new MarketDataBus();
