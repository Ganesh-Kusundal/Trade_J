package com.tradej.core.domain.port;

import com.tradej.core.domain.model.InstrumentKey;
import com.tradej.core.domain.value.ExchangeSegment;

/**
 * Canonical boundary for converting external symbol input into domain instrument identity.
 */
public interface InstrumentIdentityService {

    String canonicalSymbol(String rawSymbol);

    InstrumentKey key(String rawSymbol, ExchangeSegment exchangeSegment);

    default InstrumentKey equity(String rawSymbol) {
        return key(rawSymbol, ExchangeSegment.NSE_EQ);
    }

    default InstrumentKey bseEquity(String rawSymbol) {
        return key(rawSymbol, ExchangeSegment.BSE_EQ);
    }

    default InstrumentKey index(String rawSymbol) {
        return key(rawSymbol, ExchangeSegment.IDX_I);
    }

    default InstrumentKey fno(String rawSymbol) {
        return key(rawSymbol, ExchangeSegment.NSE_FNO);
    }

    default InstrumentKey commodity(String rawSymbol) {
        return key(rawSymbol, ExchangeSegment.MCX_COMM);
    }

    default InstrumentKey currency(String rawSymbol) {
        return key(rawSymbol, ExchangeSegment.NSE_CURRENCY);
    }
}
