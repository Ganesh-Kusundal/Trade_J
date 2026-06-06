import {useEffect, useRef} from 'react';
import {useTerminalStore} from '@/state/terminalStore';

export function DOMTradingScreen() {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const snapshot = useTerminalStore((s) =>
    s.selectedSymbol ? s.orderBookSnapshotBySymbol[s.selectedSymbol] : null
  );

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    const {width, height} = canvas;
    ctx.clearRect(0, 0, width, height);

    if (!snapshot || (snapshot.bids.length === 0 && snapshot.asks.length === 0)) {
      ctx.fillStyle = '#666';
      ctx.font = '14px monospace';
      ctx.fillText('No depth data', width / 2 - 50, height / 2);
      return;
    }

    const allLevels = [...snapshot.bids, ...snapshot.asks];
    const maxQty = Math.max(...allLevels.map((l) => l.quantity));
    const rowHeight = Math.min(24, height / allLevels.length);
    const midY = height / 2;

    snapshot.bids.forEach((bid, i) => {
      const y = midY + i * rowHeight;
      const barWidth = (bid.quantity / maxQty) * (width * 0.4);
      ctx.fillStyle = 'rgba(0, 200, 100, 0.3)';
      ctx.fillRect(width * 0.5 - barWidth, y, barWidth, rowHeight - 2);
      ctx.fillStyle = '#0c8';
      ctx.font = '11px monospace';
      ctx.fillText(`${(bid.pricePaisa / 100).toFixed(2)}`, 4, y + 14);
      ctx.fillText(`${bid.quantity}`, width * 0.5 + 4, y + 14);
    });

    snapshot.asks.forEach((ask, i) => {
      const y = midY - (i + 1) * rowHeight;
      const barWidth = (ask.quantity / maxQty) * (width * 0.4);
      ctx.fillStyle = 'rgba(200, 50, 50, 0.3)';
      ctx.fillRect(width * 0.5, y, barWidth, rowHeight - 2);
      ctx.fillStyle = '#c44';
      ctx.font = '11px monospace';
      ctx.fillText(`${(ask.pricePaisa / 100).toFixed(2)}`, 4, y + 14);
      ctx.fillText(`${ask.quantity}`, width * 0.5 + 4, y + 14);
    });

    ctx.fillStyle = '#fff';
    ctx.font = 'bold 13px monospace';
    ctx.fillText(
      `Mid: ${(snapshot.midPricePaisa / 100).toFixed(2)}  Spread: ${(snapshot.spreadPaisa / 100).toFixed(2)}`,
      4, midY - 4
    );
  }, [snapshot]);

  return (
    <div style={{background: '#1a1a2e', borderRadius: 8, padding: 8}}>
      <div style={{color: '#aaa', fontSize: 12, marginBottom: 4}}>
        DOM — {snapshot?.symbol ?? 'No symbol selected'}
      </div>
      <canvas ref={canvasRef} width={500} height={600} style={{width: '100%', height: 600}} />
    </div>
  );
}
