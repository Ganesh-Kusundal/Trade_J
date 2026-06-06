package com.tradej.strategy.portfolio;

import com.tradej.core.domain.event.TradeClosed;
import com.tradej.core.domain.event.TradeOpened;

import java.util.Map;

public interface ExposureTracker {

    long DEFAULT_MAX_NET_EXPOSURE_PAISA = 5_000_000L;

    long netPosition(String symbol);

    Map<String, Long> netPositionsSnapshot();

    void onTradeOpened(TradeOpened trade, long actualDelta, long signalDelta);

    void onTradeClosed(TradeClosed trade, String symbol, long netDelta);

    void applySignalDelta(String symbol, long delta);

    void reverseSignalDelta(String symbol, long delta);

    void reset();
}
