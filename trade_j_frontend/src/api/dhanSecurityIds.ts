export interface DhanSecurityIdResolution {
  securityId: string;
  exchangeSegment: string;
}

const SIMPLE_MAP: Record<string, DhanSecurityIdResolution> = {
  RELIANCE: { securityId: "2885", exchangeSegment: "NSE_EQ" },
  TCS: { securityId: "11536", exchangeSegment: "NSE_EQ" },
  INFY: { securityId: "1594", exchangeSegment: "NSE_EQ" },
  HDFCBANK: { securityId: "1333", exchangeSegment: "NSE_EQ" },
  ICICIBANK: { securityId: "4963", exchangeSegment: "NSE_EQ" },
  SBIN: { securityId: "3045", exchangeSegment: "NSE_EQ" },
  NIFTY: { securityId: "13", exchangeSegment: "IDX_I" },
  BANKNIFTY: { securityId: "25", exchangeSegment: "IDX_I" },
  GOLD: { securityId: "100", exchangeSegment: "MCX_COMM" },
  SILVER: { securityId: "101", exchangeSegment: "MCX_COMM" },
  CRUDEOIL: { securityId: "102", exchangeSegment: "MCX_COMM" },
  USDINR: { securityId: "117", exchangeSegment: "NSE_CURRENCY" },
  EURINR: { securityId: "118", exchangeSegment: "NSE_CURRENCY" },
};

export function resolveDhanInstrument(symbol: string): DhanSecurityIdResolution | undefined {
  return SIMPLE_MAP[symbol.toUpperCase()];
}
