package com.tradej.app.service.broker;

import com.tradej.broker.api.port.MarketDataProvider;
import com.tradej.broker.api.port.PortfolioProvider;
import com.tradej.core.domain.model.InstrumentKey;
import com.tradej.core.domain.model.LivePnlSnapshot;
import com.tradej.core.domain.model.Position;

import java.util.List;
import java.util.Map;

public final class LivePnlService {
    private final PortfolioProvider portfolioProvider;
    private final MarketDataProvider marketDataProvider;

    public LivePnlService(PortfolioProvider portfolioProvider, MarketDataProvider marketDataProvider) {
        this.portfolioProvider = portfolioProvider;
        this.marketDataProvider = marketDataProvider;
    }

    public LivePnlSnapshot getLivePnl() {
        List<Position> positions = portfolioProvider.getPositions();
        if (positions.isEmpty()) {
            return new LivePnlSnapshot(0L, 0L);
        }
        List<InstrumentKey> keys = positions.stream()
                .map(position -> new InstrumentKey(position.symbol(), position.exchangeSegment()))
                .toList();
        Map<InstrumentKey, Long> ltp = marketDataProvider.getLtpBatch(keys);
        long netPnl = 0L;
        long netQty = 0L;
        for (Position position : positions) {
            InstrumentKey key = new InstrumentKey(position.symbol(), position.exchangeSegment());
            long last = ltp.getOrDefault(key, position.lastPricePaisa());
            long qty = position.quantity();
            netQty += qty;
            netPnl += (last - position.averagePricePaisa()) * qty;
        }
        return new LivePnlSnapshot(netPnl, netQty);
    }
}
