import { useState, useEffect } from "react";
import { marketBus } from "../api/MarketDataBus";
import type {
  MarketEvent, TickEvent, DepthEvent, TradeEvent, CandleEvent,
  MarketStateEvent, BrokerStatusEvent, FeedHealthEvent, DataModeEvent,
  OHLCVBarData, DepthLevel, TradeTickData,
} from "../api/marketContracts";
import type { MarketState } from "../domain/instrument";

export function useLastTick(): TickEvent | null {
  const [tick, setTick] = useState<TickEvent | null>(
    () => marketBus.getLast<TickEvent>("TICK") ?? null
  );
  useEffect(() => marketBus.subscribe((e: MarketEvent) => {
    if (e.type === "TICK") setTick(e);
  }), []);
  return tick;
}

export function useDepth(): { bids: DepthLevel[]; asks: DepthLevel[]; ltp: number } {
  const [bids, setBids] = useState<DepthLevel[]>(() => {
    const last = marketBus.getLast<DepthEvent>("DEPTH");
    return last?.bids ?? [];
  });
  const [asks, setAsks] = useState<DepthLevel[]>(() => {
    const last = marketBus.getLast<DepthEvent>("DEPTH");
    return last?.asks ?? [];
  });
  const [ltp, setLtp] = useState(() => {
    const last = marketBus.getLast<DepthEvent>("DEPTH");
    return last?.ltp ?? 0;
  });

  useEffect(() => marketBus.subscribe((e: MarketEvent) => {
    if (e.type === "DEPTH") {
      setBids(e.bids);
      setAsks(e.asks);
      setLtp(e.ltp);
    }
  }), []);

  return { bids, asks, ltp };
}

export function useTrades(): TradeTickData[] {
  const [trades, setTrades] = useState<TradeTickData[]>([]);

  useEffect(() => marketBus.subscribe((e: MarketEvent) => {
    if (e.type === "TRADE") {
      setTrades(prev => [...e.trades, ...prev].slice(0, 50));
    }
  }), []);

  return trades;
}

export function useCandles(): OHLCVBarData[] {
  const [candles, setCandles] = useState<OHLCVBarData[]>([]);

  useEffect(() => marketBus.subscribe((e: MarketEvent) => {
    if (e.type === "CANDLE") {
      if (e.isPartial && candles.length > 0) {
        setCandles(prev => {
          const copy = [...prev];
          copy[copy.length - 1] = e.candles[e.candles.length - 1];
          return copy;
        });
      } else {
        setCandles(e.candles);
      }
    }
  }), []);

  return candles;
}

export function useMarketState(): { state: MarketState; dataSource: string } {
  const [state, setState] = useState<MarketState>(
    () => marketBus.getLast<MarketStateEvent>("MARKET_STATE")?.state ?? "UNKNOWN" as MarketState
  );
  const [dataSource, setDataSource] = useState(
    () => marketBus.getLast<MarketStateEvent>("MARKET_STATE")?.dataSource ?? "SIMULATION"
  );

  useEffect(() => marketBus.subscribe((e: MarketEvent) => {
    if (e.type === "MARKET_STATE") {
      setState(e.state);
      setDataSource(e.dataSource);
    }
  }), []);

  return { state, dataSource };
}

export function useBrokerStatus(): { status: string; websocketConnected: boolean; broker: string } {
  const [status, setStatus] = useState(() => {
    const last = marketBus.getLast<BrokerStatusEvent>("BROKER_STATUS");
    return {
      status: last?.connected ? "UP" : "DOWN",
      websocketConnected: last?.websocketConnected ?? false,
      broker: last?.broker ?? "unknown",
    };
  });

  useEffect(() => marketBus.subscribe((e: MarketEvent) => {
    if (e.type === "BROKER_STATUS") {
      setStatus({
        status: e.connected ? "UP" : "DOWN",
        websocketConnected: e.websocketConnected,
        broker: e.broker,
      });
    }
  }), []);

  return status;
}

export function useFeedHealth(): "healthy" | "delayed" | "stale" | "disconnected" {
  const [health, setHealth] = useState<"healthy" | "delayed" | "stale" | "disconnected">(
    () => marketBus.getLast<FeedHealthEvent>("FEED_HEALTH")?.health ?? "healthy"
  );

  useEffect(() => marketBus.subscribe((e: MarketEvent) => {
    if (e.type === "FEED_HEALTH") setHealth(e.health);
  }), []);

  return health;
}

export function useDataMode(): "LIVE" | "SIMULATION" | "HISTORICAL" | "PAPER" {
  const [mode, setMode] = useState<"LIVE" | "SIMULATION" | "HISTORICAL" | "PAPER">(
    () => marketBus.getLast<DataModeEvent>("DATA_MODE")?.mode ?? "SIMULATION"
  );

  useEffect(() => marketBus.subscribe((e: MarketEvent) => {
    if (e.type === "DATA_MODE") setMode(e.mode);
  }), []);

  return mode;
}
