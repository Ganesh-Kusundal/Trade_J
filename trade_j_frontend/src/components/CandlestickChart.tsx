import React, { useEffect, useRef, useState, useMemo } from "react";
import {
  createChart, ColorType, CrosshairMode, UTCTimestamp,
  CandlestickSeries, HistogramSeries, LineSeries,
} from "lightweight-charts";
import type { OHLCVBar, Instrument } from "../domain/instrument";

interface ChartProps {
  instrument: Instrument;
  timeframe: string;
  setTimeframe: (tf: string) => void;
  bars: OHLCVBar[];
  loading: boolean;
  marketState: string;
  ltp?: number;
}

const TIMEFRAMES = ["1m", "5m", "15m", "1h", "4h", "1d"];
const isIntraday = (tf: string) => tf !== "1d";

function formatPrice(p: number): string {
  if (isNaN(p)) return "—";
  return p.toLocaleString("en-IN", { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

function formatVol(v: number, unit: string): string {
  if (v >= 1_000_000) return safeNum(v / 1_000_000).toFixed(2) + "M " + unit;
  if (v >= 1_000) return safeNum(v / 1_000).toFixed(1) + "K " + unit;
  return v.toLocaleString() + " " + unit;
}

const safeNum = (v: any, fallback = 0): number =>
  typeof v === 'number' && isFinite(v) ? v : fallback;

function sma(bars: OHLCVBar[], period: number): Array<{ time: number; value: number }> {
  if (bars.length < period) return [];
  const result: Array<{ time: number; value: number }> = [];
  let sum = 0;
  for (let i = 0; i < period; i++) sum += bars[i].close;
  result.push({ time: bars[period - 1].time, value: sum / period });
  for (let i = period; i < bars.length; i++) {
    sum += bars[i].close - bars[i - period].close;
    result.push({ time: bars[i].time, value: sum / period });
  }
  return result;
}

function computeVWAP(bars: OHLCVBar[]): Array<{ time: number; value: number }> {
  const result: Array<{ time: number; value: number }> = [];
  let cumVol = 0;
  let cumPv = 0;
  for (const bar of bars) {
    const typical = (bar.high + bar.low + bar.close) / 3;
    cumPv += typical * bar.volume;
    cumVol += bar.volume;
    result.push({ time: bar.time, value: cumVol > 0 ? cumPv / cumVol : bar.close });
  }
  return result;
}

export default function CandlestickChart({ instrument, timeframe, setTimeframe, bars, loading, marketState, ltp }: ChartProps) {
  const containerRef = useRef<HTMLDivElement>(null);
  const chartRef = useRef<any>(null);
  const candleSeriesRef = useRef<any>(null);
  const volumeSeriesRef = useRef<any>(null);
  const ma7Ref = useRef<any>(null);
  const ma25Ref = useRef<any>(null);
  const ma99Ref = useRef<any>(null);
  const vwapRef = useRef<any>(null);
  const prevSymbolRef = useRef<string>("");
  const prevTfRef = useRef<string>("");
  const ltpPriceLineRef = useRef<any>(null);

  const [hoverBar, setHoverBar] = useState<OHLCVBar | null>(null);
  const [showMA7, setShowMA7] = useState(true);
  const [showMA25, setShowMA25] = useState(true);
  const [showMA99, setShowMA99] = useState(false);
  const [showVWAP, setShowVWAP] = useState(true);

  // Moving averages and VWAP — purely derived from bars, no state needed
  const ma7Data = useMemo(() => sma(bars, 7), [bars]);
  const ma25Data = useMemo(() => sma(bars, 25), [bars]);
  const ma99Data = useMemo(() => sma(bars, 99), [bars]);
  const vwapData = useMemo(() => computeVWAP(bars), [bars]);

  const activeBar = hoverBar || (bars.length > 0 ? bars[bars.length - 1] : null);
  const prevClose = bars.length > 1 ? bars[bars.length - 2].close : activeBar?.open ?? 0;
  const changePct = activeBar && prevClose > 0 ? ((activeBar.close - prevClose) / prevClose) * 100 : 0;

  useEffect(() => {
    if (!containerRef.current) return;

    const chart = createChart(containerRef.current, {
      layout: {
        background: { type: ColorType.Solid, color: "#0d1117" },
        textColor: "#c9d1d9",
        fontFamily: "JetBrains Mono, ui-monospace, monospace",
        fontSize: 11,
      },
      grid: {
        vertLines: { color: "#21262d" },
        horzLines: { color: "#21262d" },
      },
      crosshair: {
        mode: CrosshairMode.Normal,
        vertLine: { color: "#484f58", width: 1, style: 3, labelBackgroundColor: "#30363d" },
        horzLine: { color: "#484f58", width: 1, style: 3, labelBackgroundColor: "#30363d" },
      },
      rightPriceScale: { borderColor: "#30363d", borderVisible: true, autoScale: true },
      timeScale: {
        borderColor: "#30363d",
        borderVisible: true,
        timeVisible: isIntraday(timeframe),
        secondsVisible: false,
        rightOffset: 12,
        barSpacing: 8,
        minBarSpacing: 2,
      },
      width: containerRef.current.clientWidth,
      height: containerRef.current.clientHeight || 480,
    });

    chartRef.current = chart;

    const candleSeries = chart.addSeries(CandlestickSeries, {
      upColor: "#26a69a",
      downColor: "#ef5350",
      borderUpColor: "#26a69a",
      borderDownColor: "#ef5350",
      wickUpColor: "#26a69a",
      wickDownColor: "#ef5350",
    });
    candleSeriesRef.current = candleSeries;

    const volumeSeries = chart.addSeries(HistogramSeries, {
      priceFormat: { type: "volume" },
      priceScaleId: "volume",
    });
    volumeSeries.priceScale().applyOptions({ scaleMargins: { top: 0.82, bottom: 0 } });
    volumeSeriesRef.current = volumeSeries;

    const ma7 = chart.addSeries(LineSeries, { color: "#f0b429", lineWidth: 1, priceLineVisible: false, lastValueVisible: true });
    ma7Ref.current = ma7;
    const ma25 = chart.addSeries(LineSeries, { color: "#a78bfa", lineWidth: 1, priceLineVisible: false, lastValueVisible: true });
    ma25Ref.current = ma25;
    const ma99 = chart.addSeries(LineSeries, { color: "#22d3ee", lineWidth: 1, priceLineVisible: false, lastValueVisible: true });
    ma99Ref.current = ma99;
    const vwap = chart.addSeries(LineSeries, { color: "#3b82f6", lineWidth: 1, priceLineVisible: false, lastValueVisible: false, lineStyle: 2 });
    vwapRef.current = vwap;

    chart.subscribeCrosshairMove((param: any) => {
      if (!param?.time || !param?.point) { setHoverBar(null); return; }
      const d = param.seriesData?.get(candleSeries);
      if (d) {
        const vd = param.seriesData?.get(volumeSeries);
        setHoverBar({ time: Number(param.time) * 1000, open: d.open, high: d.high, low: d.low, close: d.close, volume: vd?.value ?? 0 });
      } else {
        setHoverBar(null);
      }
    });

    const resizeObs = new ResizeObserver(() => {
      if (containerRef.current && chartRef.current) {
        chartRef.current.resize(containerRef.current.clientWidth, containerRef.current.clientHeight || 480);
      }
    });
    resizeObs.observe(containerRef.current);

    return () => { resizeObs.disconnect(); chart.remove(); };
  }, []);

  useEffect(() => {
    if (!candleSeriesRef.current || !volumeSeriesRef.current || bars.length === 0) return;

    const chartData = bars.map(b => ({ time: b.time as UTCTimestamp, open: b.open, high: b.high, low: b.low, close: b.close }));
    candleSeriesRef.current.setData(chartData);

    // Update or create LTP price line at last bar's close price (or live ltp if provided)
    const lastClose = ltp ?? bars[bars.length - 1].close;
    if (ltpPriceLineRef.current) {
      candleSeriesRef.current.removePriceLine(ltpPriceLineRef.current);
    }
    ltpPriceLineRef.current = candleSeriesRef.current.createPriceLine({
      price: lastClose,
      color: "#ff9800",
      lineWidth: 1,
      lineStyle: 2,  // dotted
      axisLabelVisible: true,
      title: "LTP",
    });

    const volData = bars.map(b => ({
      time: b.time as UTCTimestamp,
      value: b.volume,
      color: b.close >= b.open ? "rgba(38,166,154,0.35)" : "rgba(239,83,80,0.35)",
    }));
    volumeSeriesRef.current.setData(volData);

    ma7Ref.current?.setData(showMA7 ? ma7Data.map(d => ({ time: d.time as UTCTimestamp, value: d.value })) : []);
    ma25Ref.current?.setData(showMA25 ? ma25Data.map(d => ({ time: d.time as UTCTimestamp, value: d.value })) : []);
    ma99Ref.current?.setData(showMA99 ? ma99Data.map(d => ({ time: d.time as UTCTimestamp, value: d.value })) : []);
    vwapRef.current?.setData(showVWAP ? vwapData.map(d => ({ time: d.time as UTCTimestamp, value: d.value })) : []);

    const symChanged = prevSymbolRef.current !== instrument.symbol;
    const tfChanged = prevTfRef.current !== timeframe;
    prevSymbolRef.current = instrument.symbol;
    prevTfRef.current = timeframe;

    if (tfChanged) {
      chartRef.current?.timeScale().fitContent();
    } else if (symChanged) {
      chartRef.current?.timeScale().fitContent();
    } else {
      chartRef.current?.timeScale().scrollToRealTime();
    }
  }, [bars, showMA7, showMA25, showMA99, showVWAP, instrument.symbol, timeframe, ma7Data, ma25Data, ma99Data, vwapData, ltp]);

  const isBull = activeBar ? activeBar.close >= activeBar.open : true;
  const priceColor = isBull ? "#26a69a" : "#ef5350";

  return (
    <div className="bg-[#0d1117] border border-[#21262d] rounded-lg flex flex-col h-full font-mono text-[11px] select-none">
      {/* Toolbar */}
      <div className="flex items-center justify-between px-3 py-1.5 border-b border-[#21262d]">
        <div className="flex items-center gap-2">
          <span className="font-black text-[13px] text-[#c9d1d9]">{instrument.exchange}:{instrument.symbol}</span>
          <span className={`text-[9px] font-bold px-1.5 py-0.5 rounded ${marketState === "OPEN" ? "bg-[#26a69a]/15 text-[#26a69a]" : "bg-[#ef5350]/15 text-[#ef5350]"}`}>
            {marketState}
          </span>
          <span className="text-[9px] font-bold px-1.5 py-0.5 rounded bg-[#161b22] text-slate-400 border border-[#21262d]">
            {timeframe}
          </span>
          <div className="flex bg-[#161b22] p-0.5 rounded border border-[#21262d]">
            {TIMEFRAMES.map(tf => (
              <button key={tf} onClick={() => setTimeframe(tf)}
                aria-current={timeframe === tf ? "true" : undefined}
                className={`px-2 py-0.5 rounded text-[10px] font-bold transition ${timeframe === tf ? "bg-[#f0b429] text-[#0d1117] ring-1 ring-[#f0b429]/50" : "text-slate-400 hover:text-slate-200 hover:bg-[#21262d]"}`}>
                {tf}
              </button>
            ))}
          </div>
        </div>
        <div className="flex items-center gap-2 text-[10px]">
          {[
            { label: "MA7", color: "#f0b429", show: showMA7, toggle: () => setShowMA7(!showMA7), data: ma7Data },
            { label: "MA25", color: "#a78bfa", show: showMA25, toggle: () => setShowMA25(!showMA25), data: ma25Data },
            { label: "MA99", color: "#22d3ee", show: showMA99, toggle: () => setShowMA99(!showMA99), data: ma99Data },
            { label: "VWAP", color: "#3b82f6", show: showVWAP, toggle: () => setShowVWAP(!showVWAP), data: vwapData },
          ].map(ind => (
            <button key={ind.label} onClick={ind.toggle}
              className={`flex items-center gap-1 px-1.5 py-0.5 rounded border ${ind.show ? `border-current text-current opacity-100` : "border-transparent text-slate-600"}`}
              style={ind.show ? { color: ind.color, borderColor: ind.color + "80", backgroundColor: ind.color + "10" } : {}}>
              <span className="font-bold">{ind.label}</span>
              <span>{ind.data.length > 0 ? formatPrice(ind.data[ind.data.length - 1].value) : "—"}</span>
            </button>
          ))}
        </div>
      </div>

      {/* OHLCV Header */}
      {activeBar && (
        <div className="flex items-center gap-4 px-3 py-1 border-b border-[#21262d]/50 text-[10px]">
          <span className="text-slate-500">O <span className="font-bold text-slate-300">{formatPrice(activeBar.open)}</span></span>
          <span className="text-slate-500">H <span className="font-bold text-[#26a69a]">{formatPrice(activeBar.high)}</span></span>
          <span className="text-slate-500">L <span className="font-bold text-[#ef5350]">{formatPrice(activeBar.low)}</span></span>
          <span className="text-slate-500">C <span className={`font-black ${isBull ? "text-[#26a69a]" : "text-[#ef5350]"}`}>{formatPrice(activeBar.close)}</span></span>
          <span className={`font-bold ${changePct >= 0 ? "text-[#26a69a]" : "text-[#ef5350]"}`}>{changePct >= 0 ? "+" : ""}{safeNum(changePct).toFixed(2)}%</span>
          <span className="text-slate-500">Vol <span className="font-bold text-slate-300">{formatVol(activeBar.volume, instrument.volumeUnit)}</span></span>
        </div>
      )}

      {/* Chart */}
      <div className="relative flex-1 min-h-[400px]">
        {loading && bars.length === 0 && (
          <div className="absolute inset-0 flex items-center justify-center bg-[#0d1117]/90 z-20">
            <div className="w-8 h-8 rounded-full border-2 border-[#f0b429] border-t-transparent animate-spin" />
          </div>
        )}
        <div ref={containerRef} className="w-full h-full" />
      </div>
    </div>
  );
}
