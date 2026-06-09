package com.tradej.core.domain.value;

public enum ExchangeSegment {
    NSE_EQ(Exchange.NSE),
    NSE_FNO(Exchange.NFO),
    BSE_EQ(Exchange.BSE),
    BSE_FNO(Exchange.BFO),
    IDX_I(Exchange.INDEX),
    MCX_COMM(Exchange.MCX),
    NSE_CURRENCY(Exchange.CDS),
    BSE_CURRENCY(Exchange.CDS),
    CRYPTO_SPOT(Exchange.CRYPTO),
    CRYPTO_FUTURES(Exchange.CRYPTO),
    FX_SPOT(Exchange.FX),
    US_EQUITY(Exchange.US_EQUITY),
    UNKNOWN(Exchange.UNKNOWN);

    private final Exchange exchange;

    ExchangeSegment(Exchange exchange) {
        this.exchange = exchange;
    }

    public Exchange exchange() {
        return exchange;
    }

    public Exchange venueExchange() {
        return exchange;
    }

    public static ExchangeSegment fromCode(String code) {
        for (ExchangeSegment segment : values()) {
            if (segment.name().equalsIgnoreCase(code)) {
                return segment;
            }
        }
        return UNKNOWN;
    }
}
