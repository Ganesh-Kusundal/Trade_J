package com.tradej.core.domain.instrument;

import com.tradej.core.domain.model.InstrumentKey;
import com.tradej.core.domain.value.ExchangeSegment;

public final class Instruments {
    private Instruments() {}
    public static InstrumentKey nifty() { return InstrumentKey.of("NIFTY", ExchangeSegment.IDX_I); }
    public static InstrumentKey bankNifty() { return InstrumentKey.of("NIFTY BANK", ExchangeSegment.IDX_I); }
    public static InstrumentKey finNifty() { return InstrumentKey.of("NIFTY FIN SERVICE", ExchangeSegment.IDX_I); }
    public static InstrumentKey midcpNifty() { return InstrumentKey.of("NIFTY MID SELECT", ExchangeSegment.IDX_I); }
    public static InstrumentKey niftyFuture() { return InstrumentKey.of("NIFTY", ExchangeSegment.NSE_FNO); }
    public static InstrumentKey bankNiftyFuture() { return InstrumentKey.of("NIFTY BANK", ExchangeSegment.NSE_FNO); }
    public static InstrumentKey equity(String symbol) { return InstrumentKey.of(symbol, ExchangeSegment.NSE_EQ); }
    public static InstrumentKey fno(String symbol) { return InstrumentKey.of(symbol, ExchangeSegment.NSE_FNO); }
    public static InstrumentKey commodity(String symbol) { return InstrumentKey.of(symbol, ExchangeSegment.MCX_COMM); }
    public static InstrumentKey currency(String symbol) { return InstrumentKey.of(symbol, ExchangeSegment.NSE_CURRENCY); }
    public static InstrumentKey bseEquity(String symbol) { return InstrumentKey.of(symbol, ExchangeSegment.BSE_EQ); }
}
