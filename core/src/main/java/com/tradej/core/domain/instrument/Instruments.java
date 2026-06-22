package com.tradej.core.domain.instrument;

import com.tradej.core.domain.model.InstrumentKey;

public final class Instruments {
    private static final StandardInstrumentIdentityService IDENTITY = StandardInstrumentIdentityService.INSTANCE;

    private Instruments() {}
    public static InstrumentKey nifty() { return IDENTITY.index(IndexSymbols.NIFTY); }
    public static InstrumentKey bankNifty() { return IDENTITY.index(IndexSymbols.NIFTY_BANK); }
    public static InstrumentKey finNifty() { return IDENTITY.index(IndexSymbols.NIFTY_FIN_SERVICE); }
    public static InstrumentKey midcpNifty() { return IDENTITY.index(IndexSymbols.NIFTY_MID_SELECT); }
    public static InstrumentKey sensex() { return IDENTITY.index(IndexSymbols.SENSEX); }
    public static InstrumentKey bankex() { return IDENTITY.index(IndexSymbols.BANKEX); }
    public static InstrumentKey niftyFuture() { return IDENTITY.fno(IndexSymbols.NIFTY); }
    public static InstrumentKey bankNiftyFuture() { return IDENTITY.fno(IndexSymbols.NIFTY_BANK); }
    public static InstrumentKey equity(String symbol) { return IDENTITY.equity(symbol); }
    public static InstrumentKey fno(String symbol) { return IDENTITY.fno(symbol); }
    public static InstrumentKey commodity(String symbol) { return IDENTITY.commodity(symbol); }
    public static InstrumentKey currency(String symbol) { return IDENTITY.currency(symbol); }
    public static InstrumentKey bseEquity(String symbol) { return IDENTITY.bseEquity(symbol); }
}
