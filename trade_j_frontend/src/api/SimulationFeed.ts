import { fetchLtp, fetchDepth, fetchCandles } from "./marketData";
import { marketBus } from "./MarketDataBus";
import type { DepthLevel, TradeTickData, OHLCVBarData } from "./marketContracts";
import { FEED_CONFIG } from "../config/terminal.config";

const LTP_INTERVAL_MS = FEED_CONFIG.LTP_INTERVAL_MS;
const DEPTH_INTERVAL_MS = FEED_CONFIG.DEPTH_INTERVAL_MS;
const CANDLE_REFRESH_MS = FEED_CONFIG.CANDLE_REFRESH_MS;

export class SimulationFeed {
  private pollTimers: ReturnType<typeof setInterval>[] = [];
  private lastLtp = 0;
  private lastTradePrice = 0;
  private lastTradeTime = 0;
  private symbol = "";
  private exchange = "";
  private segment = "";
  private tickSize = 0.05;

  start(symbol: string, exchange: string, segment: string, instrumentTickSize = 0.05): void {
    this.symbol = symbol;
    this.exchange = exchange;
    this.segment = segment;
    this.tickSize = instrumentTickSize;

    console.log('[SimulationFeed] Starting for', symbol, exchange);

    this.pollLtp();
    this.pollDepth();
    this.pollCandles(); // FIX: Start polling candles
    this.pollTimers.push(setInterval(() => this.pollLtp(), LTP_INTERVAL_MS));
    this.pollTimers.push(setInterval(() => this.pollDepth(), DEPTH_INTERVAL_MS));
    this.pollTimers.push(setInterval(() => this.pollCandles(), CANDLE_REFRESH_MS)); // Refresh candles every minute
  }

  private pollLtp(): void {
    fetchLtp(this.symbol, this.segment).then(data => {
      const newPrice = data.ltpPaisa / 100;
      const change = this.lastLtp > 0 ? newPrice - this.lastLtp : 0;
      const changePercent = this.lastLtp > 0 ? (change / this.lastLtp) * 100 : 0;
      
      console.log('[SimulationFeed:LTP]', { symbol: this.symbol, ltp: newPrice, change });
      
      this.lastLtp = newPrice;
      marketBus.publish({
        type: "TICK",
        symbol: this.symbol,
        exchange: this.exchange,
        ltp: newPrice,
        change,
        changePercent,
        timestamp: Date.now(),
      });

      // FIX: Publish trade event on price change
      if (this.lastLtp > 0 && Math.abs(newPrice - this.lastTradePrice) >= this.tickSize) {
        const now = Math.floor(Date.now() / 1000);
        if (now !== this.lastTradeTime) {
          this.lastTradeTime = now;
          this.lastTradePrice = newPrice;
          
          const trade: TradeTickData = {
            time: now,
            price: newPrice,
            quantity: 0,
            side: newPrice >= this.lastLtp ? "BUY" : "SELL",
          };
          console.log('[SimulationFeed:Trade]', trade);
          marketBus.publish({
            type: "TRADE",
            symbol: this.symbol,
            exchange: this.exchange,
            trades: [trade],
          });
        }
      }
    }).catch(() => {});
  }

  private pollDepth(): void {
    fetchDepth(this.symbol).then(data => {
      const bids: DepthLevel[] = (data.bids || []).map((b: any) => ({
        price: b.pricePaisa != null ? b.pricePaisa / 100 : (b.price ?? 0),
        quantity: b.quantity ?? b.amount ?? 0,
        orders: b.orderCount ?? b.orders ?? 1,
      })).sort((a: DepthLevel, b: DepthLevel) => b.price - a.price);

      const asks: DepthLevel[] = (data.asks || []).map((a: any) => ({
        price: a.pricePaisa != null ? a.pricePaisa / 100 : (a.price ?? 0),
        quantity: a.quantity ?? a.amount ?? 0,
        orders: a.orderCount ?? a.orders ?? 1,
      })).sort((a: DepthLevel, b: DepthLevel) => a.price - b.price);

      console.log('[SimulationFeed:Depth]', { symbol: this.symbol, bids: bids.length, asks: asks.length });

      marketBus.publish({
        type: "DEPTH",
        symbol: this.symbol,
        exchange: this.exchange,
        bids,
        asks,
        ltp: this.lastLtp,
        spread: asks.length > 0 && bids.length > 0
          ? asks[asks.length - 1].price - bids[0].price
          : 0,
        timestamp: Date.now(),
      });
    }).catch(() => {});
  }

  private pollCandles(): void {
    const to = new Date().toISOString().split("T")[0];
    const from = new Date(Date.now() - 5 * 86400000).toISOString().split("T")[0];
    
    fetchCandles(this.symbol, this.segment, "1m", from, to).then(data => {
      const candles: OHLCVBarData[] = data.candles.map(c => ({
        time: Math.floor(c.startTimeMs / 1000),
        open: c.openPaisa / 100,
        high: c.highPaisa / 100,
        low: c.lowPaisa / 100,
        close: c.closePaisa / 100,
        volume: c.volume,
      }));

      console.log('[SimulationFeed:Candles]', { symbol: this.symbol, count: candles.length, last: candles[candles.length - 1]?.time });

      marketBus.publish({
        type: "CANDLE",
        symbol: this.symbol,
        exchange: this.exchange,
        candles,
      });
    }).catch(() => {});
  }

  stop(): void {
    this.pollTimers.forEach(t => clearInterval(t));
    this.pollTimers = [];
    this.lastLtp = 0;
    this.lastTradePrice = 0;
    this.lastTradeTime = 0;
    console.log('[SimulationFeed] Stopped');
  }
}
