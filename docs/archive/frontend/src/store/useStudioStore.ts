import {create} from 'zustand';
import type {
  Candle,
  ScannerFilter,
  ScanResult,
  ActivePosition,
  Trade,
  StartupCandidate,
  ChartMarker,
  StructureBreak,
  OrderBlockZone,
  HalfTrendPoint,
  CvdPoint,
  IndicatorParams,
} from '@/dto/types';
import type {GatewayFrame} from '@/api/websocket';
import {scanApi, studioApi} from '@/api/client';
import {connectGateway, disconnectGateway, onGatewayMessage, onGatewayStatus} from '@/api/websocket';

export interface StudioStore {
  wsConnected: boolean;
  gatewayUrl: string;

  symbols: {symbol: string; canonicalSymbol: string; exchangeSegment: string; name: string}[];
  startupCandidates: StartupCandidate[];
  startupScanDate: string | null;
  startupScanTime: string | null;
  startupRequestedScanTime: string | null;
  startupSelectionMode: string | null;
  selectedSymbol: string;
  selectedExchange: string;

  candles: Candle[];
  halfTrend: HalfTrendPoint[];
  cvd: CvdPoint[];
  markers: ChartMarker[];
  structureBreaks: StructureBreak[];
  orderBlockZones: OrderBlockZone[];
  indicatorParams: IndicatorParams;
  interval: string;
  from: string;
  to: string;

  scannerFilter: ScannerFilter;
  scanResult: ScanResult | null;
  scanLoading: boolean;

  positions: ActivePosition[];
  trades: Trade[];

  sidebarOpen: boolean;
  activeView: 'chart' | 'scanner' | 'pipeline' | 'research' | 'admin';
  error: string | null;

  connect: (url: string) => void;
  disconnect: () => void;
  bootstrapStartupCandidates: () => Promise<void>;
  selectSymbol: (symbol: string, exchange?: string) => void;
  setInterval: (interval: string) => void;
  setDateRange: (from: string, to: string) => void;
  loadCandles: () => Promise<void>;
  runScan: (profile?: string) => Promise<void>;
  setScannerFilter: (filter: Partial<ScannerFilter>) => void;
  setIndicatorParams: (params: Partial<IndicatorParams>) => void;
  setActiveView: (view: StudioStore['activeView']) => void;
  toggleSidebar: () => void;
  clearError: () => void;
}

function subtractCalendarDays(isoDate: string, days: number): string {
  const [year, month, day] = isoDate.split('-').map(Number);
  const date = new Date(Date.UTC(year, month - 1, day));
  date.setUTCDate(date.getUTCDate() - days);
  return date.toISOString().slice(0, 10);
}

const CHART_LOOKBACK_DAYS = 20;

function frameToCandle(p: Record<string, unknown>): Candle | null {
  if (typeof p.startTimeMs !== 'number' || typeof p.openPaisa !== 'number') return null;
  return {
    startTimeMs: p.startTimeMs,
    endTimeMs: (p.endTimeMs as number) || p.startTimeMs as number,
    openPaisa: p.openPaisa as number,
    highPaisa: p.highPaisa as number,
    lowPaisa: p.lowPaisa as number,
    closePaisa: p.closePaisa as number,
    volume: (p.volume as number) || 0,
  };
}

export const useStudioStore = create<StudioStore>((set, get) => {
  onGatewayMessage((frame: GatewayFrame) => {
    const p = frame.payload;
    switch (frame.topic) {
      case 'CANDLE_CLOSED':
      case 'CANDLE_DEVELOPING': {
        if (p.symbol !== get().selectedSymbol) break;
        const candle = frameToCandle(p);
        if (candle) {
          set((s) => {
            const idx = s.candles.findIndex((c) => c.startTimeMs === candle.startTimeMs);
            const updated = idx >= 0
              ? s.candles.map((c, i) => i === idx ? candle : c)
              : [...s.candles, candle];
            return {candles: updated.length > 5000 ? updated.slice(-5000) : updated};
          });
        }
        break;
      }
      case 'MARKET_TICK': {
        if (p.symbol !== get().selectedSymbol) break;
        if (typeof p.ltpPaisa === 'number') {
          set((s) => {
            if (s.candles.length === 0) return {};
            const last = {...s.candles[s.candles.length - 1]};
            last.closePaisa = p.ltpPaisa as number;
            if ((p.ltpPaisa as number) > last.highPaisa) last.highPaisa = p.ltpPaisa as number;
            if ((p.ltpPaisa as number) < last.lowPaisa || last.lowPaisa === 0) last.lowPaisa = p.ltpPaisa as number;
            const candles = [...s.candles];
            candles[candles.length - 1] = last;
            return {candles};
          });
        }
        break;
      }
      case 'POSITION_UPDATE': {
        const symbol = p.symbol as string;
        const size = (p.size as number) || 0;
        const entryPrice = (p.entryPricePaisa as number) || 0;
        const action = (p.action as string) || 'OPEN';
        set((s) => {
          if (action === 'CLOSED' || size === 0) {
            return {positions: s.positions.filter((pos) => pos.symbol !== symbol)};
          }
          const existing = s.positions.findIndex((pos) => pos.symbol === symbol);
          const pos: ActivePosition = {
            symbol,
            side: size >= 0 ? 'LONG' : 'SHORT',
            quantity: Math.abs(size),
            entryPrice,
            currentPrice: entryPrice,
            pnl: 0,
            pnlPercent: 0,
          };
          const positions = [...s.positions];
          if (existing >= 0) positions[existing] = pos;
          else positions.push(pos);
          return {positions};
        });
        break;
      }
      case 'ORDER_UPDATE': {
        const trade: Trade = {
          id: (p.orderId as string) || String(Date.now()),
          symbol: (p.symbol as string) || '',
          side: (p.side as 'BUY' | 'SELL') || 'BUY',
          quantity: (p.quantity as number) || 0,
          price: (p.pricePaisa as number) || 0,
          timestamp: Date.now(),
        };
        set((s) => ({trades: [...s.trades, trade].slice(-200)}));
        break;
      }
      case 'PNL_UPDATE': {
        const pnlSymbol = p.symbol as string;
        if (typeof p.unrealizedPnlPaisa === 'number' && pnlSymbol) {
          set((s) => ({
            positions: s.positions.map((pos) => {
              if (pos.symbol !== pnlSymbol) return pos;
              return {
                ...pos,
                pnl: (p.unrealizedPnlPaisa as number),
                pnlPercent: pos.entryPrice > 0
                  ? ((p.unrealizedPnlPaisa as number) / (pos.quantity * pos.entryPrice)) * 100
                  : 0,
              };
            }),
          }));
        }
        break;
      }
    }
  });

  onGatewayStatus((connected: boolean) => set({wsConnected: connected}));

  return {
    wsConnected: false,
    gatewayUrl: '',

    symbols: [],
    startupCandidates: [],
    startupScanDate: null,
    startupScanTime: null,
    startupRequestedScanTime: null,
    startupSelectionMode: null,
    selectedSymbol: '',
    selectedExchange: 'NSE_EQ',

    candles: [],
    halfTrend: [],
    cvd: [],
    markers: [],
    structureBreaks: [],
    orderBlockZones: [],
    indicatorParams: {amplitude: 2, channelDeviation: 2, atrPeriod: 100},
    interval: '5m',
    from: new Date(Date.now() - 7 * 86400000).toISOString().split('T')[0],
    to: new Date().toISOString().split('T')[0],

    scannerFilter: {profile: 'institutional-baseline'},
    scanResult: null,
    scanLoading: false,

    positions: [],
    trades: [],

    sidebarOpen: true,
    activeView: 'chart',
    error: null,

    connect: (url: string) => {
      set({gatewayUrl: url});
      connectGateway(url);
    },

    disconnect: () => {
      disconnectGateway();
      set({wsConnected: false});
    },

    bootstrapStartupCandidates: async () => {
      try {
        const res = await studioApi.startupCandidates(5);
        const lookbackDays = res.chartLookbackDays ?? CHART_LOOKBACK_DAYS;
        const chartFrom = subtractCalendarDays(res.scanDate, lookbackDays - 1);
        const mapped = res.candidates.map((c) => ({
          symbol: c.symbol,
          canonicalSymbol: c.symbol,
          exchangeSegment: 'NSE_EQ',
          name: c.symbol,
        }));
        set({
          startupCandidates: res.candidates,
          startupScanDate: res.scanDate,
          startupScanTime: res.scanTime,
          startupRequestedScanTime: res.requestedScanTime || res.provenance?.requestedScanTime || '09:45:00',
          startupSelectionMode: res.selectionMode,
          symbols: mapped,
          selectedSymbol: mapped[0]?.symbol || '',
          from: chartFrom,
          to: res.scanDate,
          error: null,
        });
        if (mapped[0]?.symbol) {
          await get().loadCandles();
        }
      } catch (e) {
        set({error: `Failed to bootstrap startup candidates: ${e instanceof Error ? e.message : e}`});
      }
    },

    selectSymbol: (symbol: string, exchange?: string) => {
      set({selectedSymbol: symbol, selectedExchange: exchange || 'NSE_EQ'});
      get().loadCandles().catch(() => {});
    },

    setInterval: (interval: string) => {
      set({interval});
      get().loadCandles().catch(() => {});
    },

    setDateRange: (from: string, to: string) => {
      set({from, to});
      get().loadCandles().catch(() => {});
    },

    loadCandles: async () => {
      const {selectedSymbol, selectedExchange, interval, from, to} = get();
      if (!selectedSymbol) return;
      try {
        const res = await studioApi.chart(selectedSymbol, selectedExchange, interval, from, to);
        set({
          candles: res.candles,
          halfTrend: res.halfTrend || [],
          cvd: res.cvd || [],
          markers: res.markers || [],
          structureBreaks: res.structureBreaks || [],
          orderBlockZones: res.orderBlockZones || [],
          error: null,
        });
      } catch (e) {
        set({error: `Failed to load chart: ${e instanceof Error ? e.message : e}`});
      }
    },

    runScan: async (profile?: string) => {
      set({scanLoading: true, error: null});
      try {
        const result = await scanApi.run(profile || get().scannerFilter.profile);
        set({scanResult: result, scanLoading: false});
      } catch (e) {
        set({error: `Scan failed: ${e instanceof Error ? e.message : e}`, scanLoading: false});
      }
    },

    setScannerFilter: (filter: Partial<ScannerFilter>) => {
      set((s) => ({scannerFilter: {...s.scannerFilter, ...filter}}));
    },

    setIndicatorParams: (params: Partial<IndicatorParams>) => {
      set((s) => ({indicatorParams: {...s.indicatorParams, ...params}}));
    },

    setActiveView: (view: StudioStore['activeView']) => set({activeView: view}),
    toggleSidebar: () => set((s) => ({sidebarOpen: !s.sidebarOpen})),
    clearError: () => set({error: null}),
  };
});
