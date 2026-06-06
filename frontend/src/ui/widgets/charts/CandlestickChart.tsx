import {useEffect, useMemo, useRef} from 'react';
import {
  createChart,
  type IChartApi,
  type ISeriesApi,
  type CandlestickData,
  CandlestickSeries,
} from 'lightweight-charts';

function makeMockCandles(count: number): CandlestickData[] {
  const start = Math.floor(Date.now() / 1000) - count * 60;
  let price = 200;

  return Array.from({length: count}).map((_, i) => {
    const time = (start + i * 60) as CandlestickData['time'];
    const open = price;
    const delta = (Math.random() - 0.45) * 2.5;
    const close = open + delta;
    const high = Math.max(open, close) + Math.random() * 1.5;
    const low = Math.min(open, close) - Math.random() * 1.5;
    price = close;

    return {time, open, high, low, close};
  });
}

export function CandlestickChart() {
  const containerRef = useRef<HTMLDivElement | null>(null);
  const chartRef = useRef<IChartApi | null>(null);
  const seriesRef = useRef<ISeriesApi<'Candlestick'> | null>(null);

  const mock = useMemo(() => makeMockCandles(200), []);

  useEffect(() => {
    if (!containerRef.current) return;

    const chart = createChart(containerRef.current, {
      layout: {
        background: {color: '#070709'},
        textColor: '#a1a1aa',
      },
      grid: {
        vertLines: {color: '#18181b', style: 3},
        horzLines: {color: '#18181b', style: 3},
      },
      crosshair: {
        mode: 1,
      },
      timeScale: {
        timeVisible: true,
        secondsVisible: false,
      },
    });

    const series = chart.addSeries(CandlestickSeries, {
      upColor: '#10b981',
      downColor: '#ef4444',
      borderUpColor: '#10b981',
      borderDownColor: '#ef4444',
      wickUpColor: '#10b981',
      wickDownColor: '#ef4444',
    });

    series.setData(mock);

    chartRef.current = chart;
    seriesRef.current = series;

    return () => {
      chart.remove();
      chartRef.current = null;
      seriesRef.current = null;
    };
  }, [mock]);

  return <div ref={containerRef} className="h-full w-full" />;
}
