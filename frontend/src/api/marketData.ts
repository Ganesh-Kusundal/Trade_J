export interface RawCandle {
  symbol: string;
  canonicalSymbol: string;
  exchangeSegment: string;
  interval: string;
  from: string;
  to: string;
  source: string;
  count: number;
  candles: {
    symbol: string;
    canonicalSymbol: string;
    startTimeMs: number;
    endTimeMs: number;
    openPaisa: number;
    highPaisa: number;
    lowPaisa: number;
    closePaisa: number;
    volume: number;
  }[];
}

export interface ChartCandle {
  time: number;
  open: number;
  high: number;
  low: number;
  close: number;
  volume: number;
}

function toUnixSeconds(ms: number): number {
  return Math.floor(ms / 1000);
}

function paisaToInr(paisa: number): number {
  return paisa / 100;
}

function generateMockCandles(symbol: string, from: string, to: string): ChartCandle[] {
  const basePrice: Record<string, number> = {
    RELIANCE: 285000, TCS: 352000, INFY: 148000, HDFCBANK: 168000,
    ICICIBANK: 125000, SBIN: 82000, ITC: 46500, HINDUNILVR: 24500,
    BHARTIARTL: 172000, KOTAKBANK: 178000, LT: 365000, AXISBANK: 118000,
    BAJFINANCE: 680000, MARUTI: 1250000, TITAN: 340000, SUNPHARMA: 178000,
    NTPC: 35000, TATAMOTORS: 72000, TATASTEEL: 165000, ADANIPORTS: 142000,
  };
  const base = basePrice[symbol] ?? 100000 + Math.abs(hashCode(symbol)) % 500000;

  const fromDate = new Date(from + 'T09:15:00+05:30');
  const toDate = new Date(to + 'TT15:30:00+05:30');
  const candles: ChartCandle[] = [];

  let current = new Date(fromDate);
  let price = base;
  const seed = hashCode(symbol + from);

  while (current <= toDate) {
    const day = current.getDay();
    if (day === 0 || day === 6) {
      current.setDate(current.getDate() + 1);
      current.setHours(9, 15, 0, 0);
      continue;
    }

    const marketOpen = new Date(current);
    marketOpen.setHours(9, 15, 0, 0);
    const marketClose = new Date(current);
    marketClose.setHours(15, 30, 0, 0);

    let barTime = new Date(marketOpen);
    while (barTime < marketClose) {
      const drift = (pseudoRandom(seed + barTime.getTime()) - 0.48) * base * 0.001;
      const volatility = base * 0.002;
      const open = price;
      const change1 = (pseudoRandom(seed + barTime.getTime() + 1) - 0.5) * volatility;
      const change2 = (pseudoRandom(seed + barTime.getTime() + 2) - 0.5) * volatility;
      const high = Math.max(open, open + change1, open + change2) + Math.abs(drift);
      const low = Math.min(open, open + change1, open + change2) - Math.abs(drift) * 0.5;
      const close = open + drift;

      candles.push({
        time: Math.floor(barTime.getTime() / 1000),
        open: Math.round(open / 100) / 100,
        high: Math.round(Math.max(high, open) / 100) / 100,
        low: Math.round(Math.min(low, open) / 100) / 100,
        close: Math.round(close / 100) / 100,
        volume: Math.round(500 + pseudoRandom(seed + barTime.getTime() + 3) * 5000),
      });

      price = close;
      barTime = new Date(barTime.getTime() + 60000);
    }

    current.setDate(current.getDate() + 1);
    current.setHours(9, 15, 0, 0);
  }

  return candles;
}

function hashCode(str: string): number {
  let hash = 0;
  for (let i = 0; i < str.length; i++) {
    hash = ((hash << 5) - hash + str.charCodeAt(i)) | 0;
  }
  return hash;
}

function pseudoRandom(seed: number): number {
  const x = Math.sin(seed * 9301 + 49297) * 49297;
  return x - Math.floor(x);
}

export async function fetchCandles(
  symbol: string,
  from: string,
  to: string,
): Promise<ChartCandle[]> {
  try {
    const params = new URLSearchParams({
      symbol,
      exchangeSegment: 'NSE_EQ',
      interval: '1m',
      from,
      to,
      source: 'broker',
    });

    const res = await fetch(`/api/v1/market/historical/candles?${params}`);
    if (!res.ok) {
      throw new Error(`${res.status}`);
    }

    const data: RawCandle = await res.json();

    return data.candles.map((c) => ({
      time: toUnixSeconds(c.startTimeMs),
      open: paisaToInr(c.openPaisa),
      high: paisaToInr(c.highPaisa),
      low: paisaToInr(c.lowPaisa),
      close: paisaToInr(c.closePaisa),
      volume: c.volume,
    }));
  } catch {
    return generateMockCandles(symbol, from, to);
  }
}

export function getFiveDayRange(): { from: string; to: string } {
  const now = new Date();
  const to = now.toISOString().slice(0, 10);
  const from = new Date(now);
  from.setDate(from.getDate() - 7);
  return {
    from: from.toISOString().slice(0, 10),
    to,
  };
}
