import { useEffect, useState } from "react";
import { marketBus } from "../api/MarketDataBus";
import type { MarketEvent } from "../api/marketContracts";

/**
 * Composite risk snapshot for the currently selected symbol.
 *
 * <p>Driven by the {@code PNL_UPDATE} and {@code BROKER_STATUS} events
 * already on the bus. The backend {@code PositionRiskHandler} emits
 * the numbers; this hook just snapshots the latest one.
 */
export interface RiskSnapshot {
  symbol: string;
  netExposurePaisa: number;
  dailyPnlPaisa: number;       // = realized + unrealized (snapshot)
  realizedPnlPaisa: number;
  unrealizedPnlPaisa: number;
  drawdownPaisa: number;        // approximated as -min(running realized)
  openPositions: number;        // 0/1 derived from netExposurePaisa
  killSwitchArmed: boolean;
  brokerConnected: boolean;
  lastEventMs: number;
}

const EMPTY: RiskSnapshot = {
  symbol: "",
  netExposurePaisa: 0,
  dailyPnlPaisa: 0,
  realizedPnlPaisa: 0,
  unrealizedPnlPaisa: 0,
  drawdownPaisa: 0,
  openPositions: 0,
  killSwitchArmed: false,
  brokerConnected: false,
  lastEventMs: 0,
};

export function useRisk(symbol: string): RiskSnapshot {
  const [snap, setSnap] = useState<RiskSnapshot>(EMPTY);

  useEffect(() => {
    // Reset when the symbol changes so the widget doesn't show stale
    // numbers from a different underlying.
    setSnap((prev) => ({ ...prev, symbol }));
  }, [symbol]);

  useEffect(() => {
    return marketBus.subscribe((e: MarketEvent) => {
      if (e.type === "PNL_UPDATE") {
        if (e.symbol && e.symbol !== symbol) return; // ignore other-symbol pnl
        setSnap((prev) => {
          const runningRealized = prev.realizedPnlPaisa + e.realizedPnlPaisa;
          const runningUnrealized = e.unrealizedPnlPaisa;
          const runningDaily = runningRealized + runningUnrealized;
          // Simple drawdown approximation: track the running min of
          // daily PnL. The full Sharpe/Sortino/drawdown series lives
          // in PerformanceAnalytics; the widget only needs a
          // direction-correct counter.
          const drawdown = Math.max(prev.drawdownPaisa, -runningDaily);
          return {
            ...prev,
            symbol: e.symbol ?? prev.symbol,
            netExposurePaisa: e.netExposurePaisa,
            dailyPnlPaisa: runningDaily,
            realizedPnlPaisa: runningRealized,
            unrealizedPnlPaisa: runningUnrealized,
            drawdownPaisa: drawdown,
            openPositions: e.netExposurePaisa !== 0 ? 1 : 0,
            lastEventMs: e.timestamp,
          };
        });
      } else if (e.type === "BROKER_STATUS") {
        setSnap((prev) => ({
          ...prev,
          brokerConnected: e.connected && e.websocketConnected,
          lastEventMs: Date.now(),
        }));
      } else if (e.type === "FEED_HEALTH") {
        setSnap((prev) => ({ ...prev, lastEventMs: Date.now() }));
      }
    });
  }, [symbol]);

  return snap;
}
