import {useEffect, useRef} from 'react';
import {useTerminalStore} from '@/state/terminalStore';

const GATEWAY_WS_URL = 'ws://localhost:8080/ws/gateway';

export function useGatewaySocket() {
  const wsRef = useRef<WebSocket | null>(null);
  const setWsConnected = useTerminalStore((s) => s.setWsConnected);
  const setDepth = useTerminalStore((s) => s.setDepth);
  const setImbalance = useTerminalStore((s) => s.setImbalance);
  const setHeatmap = useTerminalStore((s) => s.setHeatmap);
  const addIcebergAlert = useTerminalStore((s) => s.addIcebergAlert);
  const addAbsorptionAlert = useTerminalStore((s) => s.addAbsorptionAlert);
  const setSRLevels = useTerminalStore((s) => s.setSRLevels);

  useEffect(() => {
    const ws = new WebSocket(GATEWAY_WS_URL);
    wsRef.current = ws;

    ws.onopen = () => setWsConnected(true);
    ws.onclose = () => setWsConnected(false);
    ws.onerror = () => setWsConnected(false);

    ws.onmessage = (event) => {
      try {
        const data = JSON.parse(event.data);
        const topic = data.topic;
        const payload = data.payload ?? data;

        switch (topic) {
          case 'MARKET_DEPTH':
            if (payload.symbol) setDepth(payload);
            break;
          case 'DEPTH_IMBALANCE':
            if (payload.symbol) setImbalance(payload);
            break;
          case 'HEATMAP_CHUNK':
            if (payload.symbol) setHeatmap(payload);
            break;
          case 'ICEBERG_ALERT':
            addIcebergAlert(payload);
            break;
          case 'ABSORPTION_ALERT':
            addAbsorptionAlert(payload);
            break;
          case 'SR_LEVELS_UPDATE':
            if (payload.symbol) setSRLevels(payload);
            break;
        }
      } catch {
        // Malformed message — skip
      }
    };

    return () => {
      ws.close();
      wsRef.current = null;
    };
  }, []);

  return wsRef;
}
