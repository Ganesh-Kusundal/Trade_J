package com.tradej.broker.api.port;

/**
 * Provides market status information — whether markets are open, closed,
 * or in a special session (pre-open, closing auction, etc.).
 *
 * <p>This capability allows the gateway and strategies to determine
 * if real-time data is flowing and if orders can be placed.
 */
public interface MarketStatusProvider {

    /**
     * Market session state.
     */
    enum MarketSession {
        PRE_OPEN,
        OPEN,
        CLOSING_AUCTION,
        CLOSED,
        HOLIDAY,
        UNKNOWN
    }

    /**
     * Returns the current market session for the given exchange segment.
     *
     * @param segment the exchange segment (e.g. NSE_EQ, IDX_I)
     * @return the current market session
     */
    MarketSession getSession(String segment);

    /**
     * Returns true if the market is currently open for trading
     * in the given segment.
     */
    default boolean isOpen(String segment) {
        MarketSession session = getSession(segment);
        return session == MarketSession.OPEN;
    }

    /**
     * Returns true if the market is open for any segment.
     */
    default boolean isAnyMarketOpen() {
        return isOpen("NSE_EQ") || isOpen("NSE_FNO") || isOpen("IDX_I");
    }
}
