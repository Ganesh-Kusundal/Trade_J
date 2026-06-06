import {useEffect, useRef} from 'react';
import {useTerminalStore} from '@/state/terminalStore';

export function LiquidityHeatmap() {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const chunk = useTerminalStore((s) =>
    s.selectedSymbol ? s.heatmapBySymbol[s.selectedSymbol] : null
  );

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    const {width, height} = canvas;
    ctx.clearRect(0, 0, width, height);

    if (!chunk || chunk.priceBuckets.length === 0) {
      ctx.fillStyle = '#666';
      ctx.font = '14px monospace';
      ctx.fillText('No heatmap data', width / 2 - 60, height / 2);
      return;
    }

    const buckets = chunk.priceBuckets;
    const maxVol = Math.max(...buckets.map((b) => Math.max(b.bidVol, b.askVol)));
    const cellW = width / Math.max(buckets.length, 1);
    const cellH = height / 2;

    buckets.forEach((bucket, i) => {
      const x = i * cellW;
      const bidIntensity = maxVol > 0 ? bucket.bidVol / maxVol : 0;
      ctx.fillStyle = `rgba(0, 200, 100, ${bidIntensity})`;
      ctx.fillRect(x, cellH, cellW, cellH);

      const askIntensity = maxVol > 0 ? bucket.askVol / maxVol : 0;
      ctx.fillStyle = `rgba(200, 50, 50, ${askIntensity})`;
      ctx.fillRect(x, 0, cellW, cellH);
    });

    ctx.fillStyle = '#888';
    ctx.font = '10px monospace';
    ctx.fillText('Asks ↑', 4, 12);
    ctx.fillText('Bids ↓', 4, height - 4);
  }, [chunk]);

  return (
    <div style={{background: '#1a1a2e', borderRadius: 8, padding: 8}}>
      <div style={{color: '#aaa', fontSize: 12, marginBottom: 4}}>
        Liquidity Heatmap — {chunk?.symbol ?? 'No data'}
      </div>
      <canvas ref={canvasRef} width={600} height={200} style={{width: '100%', height: 200}} />
    </div>
  );
}
