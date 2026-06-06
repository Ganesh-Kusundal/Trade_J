package com.tradej.broker.icici.adapter;

import com.tradej.broker.api.port.MarketStatusProvider;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

public final class IciciMarketStatusProvider implements MarketStatusProvider {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final LocalTime PRE_OPEN = LocalTime.of(9, 0);
    private static final LocalTime MARKET_OPEN = LocalTime.of(9, 15);
    private static final LocalTime CLOSING_AUCTION = LocalTime.of(15, 20);
    private static final LocalTime MARKET_CLOSE = LocalTime.of(15, 30);

    @Override
    public MarketSession getSession(String segment) {
        LocalDate today = LocalDate.now(IST);
        if (today.getDayOfWeek() == DayOfWeek.SATURDAY || today.getDayOfWeek() == DayOfWeek.SUNDAY) {
            return MarketSession.CLOSED;
        }
        LocalTime now = LocalTime.now(IST);
        if (now.isBefore(PRE_OPEN)) return MarketSession.CLOSED;
        if (now.isBefore(MARKET_OPEN)) return MarketSession.PRE_OPEN;
        if (now.isBefore(CLOSING_AUCTION)) return MarketSession.OPEN;
        if (now.isBefore(MARKET_CLOSE)) return MarketSession.CLOSING_AUCTION;
        return MarketSession.CLOSED;
    }
}
