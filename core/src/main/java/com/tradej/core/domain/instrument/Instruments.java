package com.tradej.core.domain.instrument;

import com.tradej.core.domain.model.InstrumentKey;
import com.tradej.core.domain.value.ExchangeSegment;

public final class Instruments {
    private Instruments() {}
    public static InstrumentKey nifty() { return InstrumentKey.of(IndexSymbols.NIFTY, ExchangeSegment.IDX_I); }
    public static InstrumentKey bankNifty() { return InstrumentKey.of(IndexSymbols.NIFTY_BANK, ExchangeSegment.IDX_I); }
    public static InstrumentKey finNifty() { return InstrumentKey.of(IndexSymbols.NIFTY_FIN_SERVICE, ExchangeSegment.IDX_I); }
    public static InstrumentKey midcpNifty() { return InstrumentKey.of(IndexSymbols.NIFTY_MID_SELECT, ExchangeSegment.IDX_I); }
    public static InstrumentKey sensex() { return InstrumentKey.of(IndexSymbols.SENSEX, ExchangeSegment.IDX_I); }
    public static InstrumentKey bankex() { return InstrumentKey.of(IndexSymbols.BANKEX, ExchangeSegment.IDX_I); }
    public static InstrumentKey niftyFuture() { return InstrumentKey.of(IndexSymbols.NIFTY, ExchangeSegment.NSE_FNO); }
    public static InstrumentKey bankNiftyFuture() { return InstrumentKey.of(IndexSymbols.NIFTY_BANK, ExchangeSegment.NSE_FNO); }
    public static InstrumentKey equity(String symbol) { return InstrumentKey.of(symbol, ExchangeSegment.NSE_EQ); }
    public static InstrumentKey fno(String symbol) { return InstrumentKey.of(symbol, ExchangeSegment.NSE_FNO); }
    public static InstrumentKey commodity(String symbol) { return InstrumentKey.of(symbol, ExchangeSegment.MCX_COMM); }
    public static InstrumentKey currency(String symbol) { return InstrumentKey.of(symbol, ExchangeSegment.NSE_CURRENCY); }
    public static InstrumentKey bseEquity(String symbol) { return InstrumentKey.of(symbol, ExchangeSegment.BSE_EQ); }
}
