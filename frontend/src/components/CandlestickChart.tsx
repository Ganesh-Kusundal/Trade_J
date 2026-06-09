import { useEffect, useRef, useState } from 'react';
import {
  createChart,
  ColorType,
  CandlestickSeries,
  type IChartApi,
  type ISeriesApi,
} from 'lightweight-charts';
import { fetchCandles, getFiveDayRange, type ChartCandle } from '../api/marketData';

interface CandlestickChartProps {
  symbol: string;
}

export function CandlestickChart({ symbol }: CandlestickChartProps) {
  const containerRef = useRef<HTMLDivElement>(null);
  const chartRef = useRef<IChartApi | null>(null);
  const seriesRef = useRef<ISeriesApi<'Candlestick'> | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [candleCount, setCandleCount] = useState(0);

  useEffect(() => {
    if (!containerRef.current) return;

    const chart = createChart(containerRef.current, {
      layout: {
        background: { type: ColorType.Solid, color: '#0a0a0a' },
        textColor: '#555555',
        fontFamily: "'JetBrains Mono', monospace",
        fontSize: 11,
      },
      grid: {
        vertLines: { color: '#1a1a1a' },
        horzLines: { color: '#1a1a1a' },
      },
      crosshair: {
        vertLine: { color: '#333333', width: 1, style: 2 },
        horzLine: { color: '#333333', width: 1, style: 2 },
      },
      timeScale: {
        borderColor: '#2a2a2a',
        timeVisible: true,
        secondsVisible: false,
      },
      rightPriceScale: {
        borderColor: '#2a2a2a',
      },
      width: containerRef.current.clientWidth,
      height: containerRef.current.clientHeight,
    });

    const series = chart.addSeries(CandlestickSeries, {
      upColor: '#00ff41',
      downColor: '#ff3333',
      borderUpColor: '#00ff41',
      borderDownColor: '#ff3333',
      wickUpColor: '#00ff41',
      wickDownColor: '#ff3333',
    });

    chartRef.current = chart;
    seriesRef.current = series;

    const handleResize = () => {
      if (containerRef.current && chartRef.current) {
        chartRef.current.applyOptions({
          width: containerRef.current.clientWidth,
          height: containerRef.current.clientHeight,
        });
      }
    };

    window.addEventListener('resize', handleResize);

    return () => {
      window.removeEventListener('resize', handleResize);
      chart.remove();
      chartRef.current = null;
      seriesRef.current = null;
    };
  }, []);

  useEffect(() => {
    if (!symbol || !seriesRef.current) return;

    let cancelled = false;

    async function load() {
      setLoading(true);
      setError(null);
      setCandleCount(0);

      try {
        const { from, to } = getFiveDayRange();
        const candles: ChartCandle[] = await fetchCandles(symbol, from, to);

        if (cancelled) return;

        seriesRef.current?.setData(candles as any);
        setCandleCount(candles.length);
        chartRef.current?.timeScale().fitContent();
      } catch (err) {
        if (!cancelled) {
          setError(err instanceof Error ? err.message : 'Failed to load data');
        }
      } finally {
        if (!cancelled) setLoading(false);
      }
    }

    load();

    return () => {
      cancelled = true;
    };
  }, [symbol]);

  return (
    <div style={{ flex: 1, position: 'relative', minHeight: 0 }}>
      {loading && (
        <div
          style={{
            position: 'absolute',
            top: '12px',
            left: '12px',
            zIndex: 10,
            color: 'var(--amber)',
            fontSize: '11px',
          }}
        >
          LOADING...
        </div>
      )}
      {error && (
        <div
          style={{
            position: 'absolute',
            top: '12px',
            left: '12px',
            zIndex: 10,
            color: 'var(--red)',
            fontSize: '11px',
          }}
        >
          {error}
        </div>
      )}
      {!loading && !error && candleCount > 0 && (
        <div
          style={{
            position: 'absolute',
            top: '12px',
            left: '12px',
            zIndex: 10,
            color: 'var(--text-muted)',
            fontSize: '11px',
          }}
        >
          {symbol} | 1M | {candleCount} candles | 5D
        </div>
      )}
      <div ref={containerRef} style={{ width: '100%', height: '100%' }} />
    </div>
  );
}
