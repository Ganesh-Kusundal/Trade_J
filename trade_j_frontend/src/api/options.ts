import { optionsApi } from "../generated/api";

export interface OptionQuote {
  available: boolean;
  symbol?: string;
  ltpPaisa?: number;
  ltp?: number;
  oi?: number;
  changeOi?: number;
  volume?: number;
  bidPaisa?: number;
  askPaisa?: number;
  iv?: number;
  delta?: number;
  theta?: number;
  gamma?: number;
  vega?: number;
}

export interface OptionStrike {
  strikePricePaisa: number;
  strikePrice: number;
  call: OptionQuote;
  put: OptionQuote;
}

export interface OptionChainResponse {
  underlying: string;
  exchangeSegment: string;
  expiry: string;
  spotPricePaisa: number;
  spotPrice: number;
  strikeCount: number;
  strikes: OptionStrike[];
}

export function fetchOptionChain(
  underlying: string,
  exchangeSegment: string,
  expiry: string
): Promise<OptionChainResponse> {
  return optionsApi.chain(underlying, exchangeSegment, expiry) as Promise<OptionChainResponse>;
}
