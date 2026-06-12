import { fetchCandles } from "../api/marketData";
import { marketBus } from "../api/MarketDataBus";
import type { OHLCVBarData } from "../api/marketContracts";

export async function loadAndPublishCandles(
  symbol: string,
  segment: string,
  exchange: string,
  interval: string,
  days: number
): Promise<OHLCVBarData[]> {
  try {
    const to = new Date().toISOString().split("T")[0];
    const from = new Date(Date.now() - days * 86400000).toISOString().split("T")[0];
    const data = await fetchCandles(symbol, segment, interval, from, to);
    const candles: OHLCVBarData[] = data.candles.map(c => ({
      time: Math.floor(c.startTimeMs / 1000),
      open: c.openPaisa / 100,
      high: c.highPaisa / 100,
      low: c.lowPaisa / 100,
      close: c.closePaisa / 100,
      volume: c.volume,
    }));

    marketBus.publish({
      type: "CANDLE",
      symbol,
      exchange,
      candles,
    });

    return candles;
  } catch (e) {
    console.error("[CandleAggregator] Failed to load candles:", e);
    return [];
  }
}

export async function loadHistoricalWithGapBackfill(
  symbol: string,
  segment: string,
  exchange: string,
  interval: string,
  days: number,
  currentIntervalMs: number
): Promise<OHLCVBarData[]> {
  // First load the requested historical data
  const candles = await loadAndPublishCandles(symbol, segment, exchange, interval, days);

  // Check for gap between last historical candle and now
  if (candles.length > 0) {
    const lastCandle = candles[candles.length - 1];
    const lastCandleTime = lastCandle.time * 1000; // Convert to ms
    const now = Date.now();
    const intervalMs = currentIntervalMs;

    // Calculate how many candles are missing
    const expectedCandles = Math.floor((now - lastCandleTime) / intervalMs);
    if (expectedCandles > 1) {
      console.warn(`[GapBackfill] Missing ${expectedCandles} candles, fetching...`);
      const fromTime = new Date(lastCandleTime + intervalMs).toISOString();
      const toTime = new Date(now).toISOString();

      try {
        const fromDate = fromTime.split("T")[0];
        const toDate = toTime.split("T")[0];
        const gapData = await fetchCandles(symbol, segment, interval, fromDate, toDate);
        const gapCandles = gapData.candles.map((c: any) => ({
          time: Math.floor(c.startTimeMs / 1000),
          open: c.openPaisa / 100,
          high: c.highPaisa / 100,
          low: c.lowPaisa / 100,
          close: c.closePaisa / 100,
          volume: c.volume,
        }));

        // Merge and republish complete dataset
        const allCandles = [...candles, ...gapCandles];
        marketBus.publish({
          type: "CANDLE",
          symbol,
          exchange,
          candles: allCandles,
        });

        console.log(`[GapBackfill] Filled ${gapCandles.length} missing candles`);
        return allCandles;
      } catch (e) {
        console.error('[GapBackfill] Failed:', e);
      }
    }
  }

  return candles;
}