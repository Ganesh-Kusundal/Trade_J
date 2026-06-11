// Dhan exchange segment constants
export const DHAN_EXCHANGE_SEGMENTS = {
  NSE_EQ: "NSE_EQ",
  NSE_FNO: "NSE_FNO",
  MCX_COMM: "MCX_COMM",
  NSE_CURRENCY: "NSE_CURRENCY",
  BSE_EQ: "BSE_EQ",
  IDX_I: "IDX_I",
} as const;

// Security ID mapping: symbol -> { securityId, exchangeSegment }
// These are Dhan-specific security IDs that change with contract expiry
export const DHAN_SECURITY_IDS: Record<string, { securityId: string; exchangeSegment: string }> = {
  // NSE Equity
  RELIANCE: { securityId: "2885", exchangeSegment: "NSE_EQ" },
  TCS: { securityId: "2953", exchangeSegment: "NSE_EQ" },
  HDFCBANK: { securityId: "1333", exchangeSegment: "NSE_EQ" },
  INFY: { securityId: "1594", exchangeSegment: "NSE_EQ" },
  ICICIBANK: { securityId: "1270", exchangeSegment: "NSE_EQ" },
  SBIN: { securityId: "3045", exchangeSegment: "NSE_EQ" },
  // Indices
  NIFTY: { securityId: "13", exchangeSegment: "IDX_I" },
  BANKNIFTY: { securityId: "25", exchangeSegment: "IDX_I" },
  // MCX Commodities
  GOLD: { securityId: "416800", exchangeSegment: "MCX_COMM" },
  GOLDM: { securityId: "416801", exchangeSegment: "MCX_COMM" },
  SILVER: { securityId: "416802", exchangeSegment: "MCX_COMM" },
  SILVERM: { securityId: "416803", exchangeSegment: "MCX_COMM" },
  CRUDEOIL: { securityId: "416804", exchangeSegment: "MCX_COMM" },
  NATURALGAS: { securityId: "416805", exchangeSegment: "MCX_COMM" },
  // CDS
  USDINR: { securityId: "1", exchangeSegment: "NSE_CURRENCY" },
  EURINR: { securityId: "5", exchangeSegment: "NSE_CURRENCY" },
};

export function resolveDhanInstrument(symbol: string): { securityId: string; exchangeSegment: string } | null {
  const key = symbol.toUpperCase().trim();
  return DHAN_SECURITY_IDS[key] ?? null;
}
