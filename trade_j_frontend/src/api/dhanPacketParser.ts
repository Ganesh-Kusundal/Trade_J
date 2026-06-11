/**
 * Dhan HQ v2 binary packet parser.
 * Reference: https://dhanhq.co/docs/v2/live-market-feed/
 *
 * Packet types:
 *   2  = Ticker (LTP only)
 *   3  = Quote (OHLC + LTP + volume)
 *   4  = Depth (full 5-level bid/ask + OHLC + LTP)
 *   5  = Full (depth + OI + timestamp)
 *   7  = Prev Close
 *   8  = Timestamp
 *   21 = Market Depth (10 levels)
 */

export interface DhanTickerPacket {
  type: 2;
  securityId: number;
  ltp: number;
}

export interface DhanQuotePacket {
  type: 3;
  securityId: number;
  ltp: number;
  ltq: number;
  ltt: number;
  atp: number;
  open: number;
  high: number;
  low: number;
  close: number;
  volume: number;
  oi: number;
  exchangeTimestamp: number;
}

export interface DhanDepthLevel {
  price: number;
  quantity: number;
  orders: number;
}

export interface DhanDepthPacket {
  type: 4 | 5 | 21;
  securityId: number;
  ltp: number;
  ltq: number;
  open: number;
  high: number;
  low: number;
  close: number;
  volume: number;
  oi: number;
  bids: DhanDepthLevel[];
  asks: DhanDepthLevel[];
  exchangeTimestamp: number;
}

export type DhanPacket = DhanTickerPacket | DhanQuotePacket | DhanDepthPacket;

export function parseDhanPacket(buffer: ArrayBuffer): DhanPacket | null {
  if (buffer.byteLength < 1) return null;
  const view = new DataView(buffer);
  const packetType = view.getUint8(0);

  switch (packetType) {
    case 2: return parseTicker(view);
    case 3: return parseQuote(view);
    case 4:
    case 5:
    case 21: return parseDepth(view, packetType);
    case 7:
    case 8: return null; // metadata packets, skip
    default: return null;
  }
}

function parseTicker(view: DataView): DhanTickerPacket {
  // Offset 1: securityId (4 bytes LE)
  // Offset 5: LTP (4 bytes LE, divide by 100 for price)
  const securityId = view.getInt32(1, true);
  const ltp = view.getInt32(5, true) / 100;
  return { type: 2, securityId, ltp };
}

function parseQuote(view: DataView): DhanQuotePacket {
  let offset = 1;
  const securityId = view.getInt32(offset, true); offset += 4;
  const ltp = view.getInt32(offset, true) / 100; offset += 4;
  const ltq = view.getInt32(offset, true); offset += 4;
  const ltt = view.getInt32(offset, true); offset += 4;
  const atp = view.getInt32(offset, true) / 100; offset += 4;
  const open = view.getInt32(offset, true) / 100; offset += 4;
  const high = view.getInt32(offset, true) / 100; offset += 4;
  const low = view.getInt32(offset, true) / 100; offset += 4;
  const close = view.getInt32(offset, true) / 100; offset += 4;
  const volume = view.getInt32(offset, true); offset += 4;
  const oi = view.getInt32(offset, true); offset += 4;
  const exchangeTimestamp = view.getInt32(offset, true);

  return {
    type: 3, securityId, ltp, ltq, ltt, atp,
    open, high, low, close, volume, oi, exchangeTimestamp,
  };
}

function parseDepth(view: DataView, packetType: number): DhanDepthPacket {
  let offset = 1;
  const securityId = view.getInt32(offset, true); offset += 4;
  const ltp = view.getInt32(offset, true) / 100; offset += 4;
  const ltq = view.getInt32(offset, true); offset += 4;
  const open = view.getInt32(offset, true) / 100; offset += 4;
  const high = view.getInt32(offset, true) / 100; offset += 4;
  const low = view.getInt32(offset, true) / 100; offset += 4;
  const close = view.getInt32(offset, true) / 100; offset += 4;
  const volume = view.getInt32(offset, true); offset += 4;
  const oi = view.getInt32(offset, true); offset += 4;

  const levelCount = packetType === 21 ? 10 : 5;
  const bids: DhanDepthLevel[] = [];
  const asks: DhanDepthLevel[] = [];

  for (let i = 0; i < levelCount; i++) {
    const bidQty = view.getInt32(offset, true); offset += 4;
    const bidPrice = view.getInt32(offset, true) / 100; offset += 4;
    const bidOrders = view.getInt16(offset, true); offset += 2;
    bids.push({ price: bidPrice, quantity: bidQty, orders: bidOrders });
  }
  for (let i = 0; i < levelCount; i++) {
    const askQty = view.getInt32(offset, true); offset += 4;
    const askPrice = view.getInt32(offset, true) / 100; offset += 4;
    const askOrders = view.getInt16(offset, true); offset += 2;
    asks.push({ price: askPrice, quantity: askQty, orders: askOrders });
  }

  let exchangeTimestamp = 0;
  if (offset + 4 <= view.byteLength) {
    exchangeTimestamp = view.getInt32(offset, true);
  }

  return {
    type: packetType as 4 | 5 | 21, securityId, ltp, ltq,
    open, high, low, close, volume, oi,
    bids, asks, exchangeTimestamp,
  };
}
