import {useEffect, useRef} from 'react';
import {createChart, CandlestickSeries, LineSeries, createSeriesMarkers} from 'lightweight-charts';
import type {IChartApi, ISeriesApi, CandlestickData, LineData, SeriesMarker, Time} from 'lightweight-charts';
import {useStudioStore} from '@/store/useStudioStore';

export function ChartWidget() {
  const containerRef = useRef<HTMLDivElement>(null);
  const primaryContainerRef = useRef<HTMLDivElement>(null);
  const secondaryContainerRef = useRef<HTMLDivElement>(null);

  const primaryChartRef = useRef<IChartApi | null>(null);
  const secondaryChartRef = useRef<IChartApi | null>(null);
  const seriesRef = useRef<ISeriesApi<'Candlestick'> | null>(null);
  const halfTrendRef = useRef<ISeriesApi<'Line'> | null>(null);
  const cvdSeriesRef = useRef<ISeriesApi<'Line'> | null>(null);
  const markersPluginRef = useRef<ReturnType<typeof createSeriesMarkers> | null>(null);

  const candles = useStudioStore((s) => s.candles);
  const halfTrend = useStudioStore((s) => s.halfTrend);
  const cvd = useStudioStore((s) => s.cvd);
  const markers = useStudioStore((s) => s.markers);
  const startupScanDate = useStudioStore((s) => s.startupScanDate);
  const startupRequestedScanTime = useStudioStore((s) => s.startupRequestedScanTime);
  const wsConnected = useStudioStore((s) => s.wsConnected);
  const candleLookupRef = useRef<Map<number, typeof candles[number]>>(new Map());

  const toChartData = (c: typeof candles[number]): CandlestickData => ({
    time: Math.floor(c.startTimeMs / 1000) as Time,
    open: c.openPaisa / 100,
    high: c.highPaisa / 100,
    low: c.lowPaisa / 100,
    close: c.closePaisa / 100,
  });

  const scanCutoffEpochSec = (() => {
    if (!startupScanDate || !startupRequestedScanTime) return null;
    const [hours, minutes, seconds = '0'] = startupRequestedScanTime.split(':');
    const iso = `${startupScanDate}T${hours.padStart(2, '0')}:${minutes.padStart(2, '0')}:${seconds.padStart(2, '0')}+05:30`;
    const ms = Date.parse(iso);
    return Number.isFinite(ms) ? Math.floor(ms / 1000) : null;
  })();

  useEffect(() => {
    if (!primaryContainerRef.current || !secondaryContainerRef.current) return;

    const primaryChart = createChart(primaryContainerRef.current, {
      layout: {
        background: {color: '#09090b'},
        textColor: '#a1a1aa',
        fontFamily: 'JetBrains Mono, SFMono-Regular, monospace',
      },
      grid: {
        vertLines: {color: '#18181b', style: 3},
        horzLines: {color: '#18181b', style: 3},
      },
      crosshair: {
        mode: 1,
        vertLine: {color: '#3f3f46', style: 2},
        horzLine: {color: '#3f3f46', style: 2},
      },
      timeScale: {
        timeVisible: true,
        secondsVisible: false,
        borderVisible: false,
        rightOffset: 15,
        barSpacing: 8,
        minBarSpacing: 2,
      },
      handleScroll: {mouseWheel: true, pressedMouseMove: true, horzTouchDrag: true, vertTouchDrag: false},
      handleScale: {mouseWheel: true, pinch: true, axisPressedMouseMove: {time: true, price: true}, axisDoubleClickReset: true},
    });
    primaryChartRef.current = primaryChart;

    seriesRef.current = primaryChart.addSeries(CandlestickSeries, {
      upColor: '#10b981',
      downColor: '#ef4444',
      borderVisible: false,
      wickUpColor: '#10b981',
      wickDownColor: '#ef4444',
    });

    halfTrendRef.current = primaryChart.addSeries(LineSeries, {
      color: '#00d2ff',
      lineWidth: 2,
      priceLineVisible: false,
      lastValueVisible: false,
    });

    const secondaryChart = createChart(secondaryContainerRef.current, {
      layout: {
        background: {color: '#09090b'},
        textColor: '#71717a',
        fontFamily: 'JetBrains Mono, SFMono-Regular, monospace',
      },
      grid: {
        vertLines: {color: '#18181b', style: 3},
        horzLines: {color: '#18181b', style: 3},
      },
      crosshair: {
        mode: 1,
        vertLine: {color: '#3f3f46', style: 2},
        horzLine: {color: '#3f3f46', style: 2},
      },
      timeScale: {
        timeVisible: true,
        borderVisible: false,
        visible: false,
        rightOffset: 15,
        barSpacing: 8,
        minBarSpacing: 2,
      },
      handleScroll: {mouseWheel: true, pressedMouseMove: true, horzTouchDrag: true, vertTouchDrag: false},
      handleScale: {mouseWheel: true, pinch: true, axisPressedMouseMove: {time: true, price: true}, axisDoubleClickReset: true},
    });
    secondaryChartRef.current = secondaryChart;

    cvdSeriesRef.current = secondaryChart.addSeries(LineSeries, {
      color: '#f59e0b',
      lineWidth: 2,
      priceLineVisible: false,
    });

    let broadcasting = false;
    primaryChart.timeScale().subscribeVisibleTimeRangeChange((range) => {
      if (broadcasting || !range || range.from === null || range.to === null) return;
      broadcasting = true;
      try { secondaryChart.timeScale().setVisibleRange(range); } catch {}
      broadcasting = false;
    });
    secondaryChart.timeScale().subscribeVisibleTimeRangeChange((range) => {
      if (broadcasting || !range || range.from === null || range.to === null) return;
      broadcasting = true;
      try { primaryChart.timeScale().setVisibleRange(range); } catch {}
      broadcasting = false;
    });

    const resizeObserver = new ResizeObserver((entries) => {
      const entry = entries[0];
      if (!entry) return;
      const {width, height} = entry.contentRect;
      if (width === 0 || height === 0) return;
      const c1H = Math.floor(height * 0.72);
      const c2H = height - c1H;
      if (primaryContainerRef.current) primaryContainerRef.current.style.height = `${c1H}px`;
      primaryChart.resize(width, c1H);
      secondaryChart.resize(width, c2H);
    });
    if (containerRef.current) resizeObserver.observe(containerRef.current);

    return () => {
      resizeObserver.disconnect();
      primaryChart.remove();
      secondaryChart.remove();
    };
  }, []);

  useEffect(() => {
    if (!seriesRef.current || !cvdSeriesRef.current || candles.length === 0) return;

    candleLookupRef.current = new Map(candles.map((c) => [Math.floor(c.startTimeMs / 1000), c]));
    const candleData = candles.map(toChartData);
    seriesRef.current.setData(candleData);

    if (halfTrendRef.current && halfTrend.length === candles.length) {
      const htData: LineData[] = halfTrend.map((point, index) => ({
        time: Math.floor(candles[index].startTimeMs / 1000) as Time,
        value: point.value,
      }));
      halfTrendRef.current.setData(htData);
    }

    if (cvd.length === candles.length) {
      const cvdData: LineData[] = cvd.map((point, index) => ({
        time: Math.floor(candles[index].startTimeMs / 1000) as Time,
        value: point.cvd,
      }));
      cvdSeriesRef.current.setData(cvdData);
    }

    if (seriesRef.current) {
      const chartMarkers: SeriesMarker<Time>[] = markers.map((marker) => ({
        time: Math.floor(marker.timeMs / 1000) as Time,
        position: marker.type.includes('high') ? 'aboveBar' : 'belowBar',
        color: marker.type.includes('high') ? '#ef4444' : '#10b981',
        shape: marker.type.includes('high') ? 'arrowDown' : 'arrowUp',
        text: marker.type.replace('_', ' '),
      }));

      if (scanCutoffEpochSec != null) {
        chartMarkers.push({
          time: scanCutoffEpochSec as Time,
          position: 'aboveBar',
          color: '#00d2ff',
          shape: 'circle',
          text: `SCAN ${startupRequestedScanTime?.slice(0, 5) || '09:45'}`,
        });
      }

      if (chartMarkers.length > 0) {
        markersPluginRef.current = createSeriesMarkers(seriesRef.current, chartMarkers);
      }
    }

    primaryChartRef.current?.timeScale().scrollToRealTime();
    secondaryChartRef.current?.timeScale().scrollToRealTime();
  }, [candles, halfTrend, cvd, markers, scanCutoffEpochSec, startupRequestedScanTime]);

  return (
    <div ref={containerRef} className="w-full h-full flex flex-col bg-[#09090b] relative select-none">
      <div ref={primaryContainerRef} className="w-full shrink-0 relative border-b border-[#18181b]" style={{height: '72%'}} />
      <div className="w-full grow relative">
        <div className="absolute top-2 left-4 z-10 pointer-events-none flex items-center gap-3">
          <span className="text-[10px] font-bold text-[#fafafa] tracking-widest font-mono">CVD</span>
          <span className={`text-[9px] font-mono font-bold px-1.5 py-0.5 border ${wsConnected ? 'text-[#10b981] border-[#10b981]/30 bg-[#10b981]/10' : 'text-[#ef4444] border-[#ef4444]/30 bg-[#ef4444]/10'}`}>
            {wsConnected ? 'LIVE' : 'PARQUET'}
          </span>
        </div>
        <div ref={secondaryContainerRef} className="w-full h-full" />
      </div>
    </div>
  );
}
