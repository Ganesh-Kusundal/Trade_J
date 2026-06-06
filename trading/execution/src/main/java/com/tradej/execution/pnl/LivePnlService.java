package com.tradej.execution.pnl;

import com.tradej.broker.api.port.InstrumentResolver;
import com.tradej.broker.api.port.MarketDataProvider;
import com.tradej.broker.api.port.PortfolioProvider;
import com.tradej.core.domain.model.InstrumentKey;
import com.tradej.core.domain.model.LivePnlSnapshot;
import com.tradej.core.domain.model.Position;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class LivePnlService {
    private final PortfolioProvider portfolioProvider;
    private final MarketDataProvider marketDataProvider;
    private final InstrumentResolver instrumentResolver;

    public LivePnlService(
            PortfolioProvider portfolioProvider,
            MarketDataProvider marketDataProvider,
            InstrumentResolver instrumentResolver
    ) {
        this.portfolioProvider = portfolioProvider;
        this.marketDataProvider = marketDataProvider;
        this.instrumentResolver = instrumentResolver;
    }

    public LivePnlSnapshot getLivePnl() {
        List<Position> positions = portfolioProvider.getPositions();
        if (positions.isEmpty()) {
            return new LivePnlSnapshot(0L, 0L);
        }
        Map<InstrumentKey, InstrumentKey> positionKeys = new HashMap<>();
        List<InstrumentKey> keys = positions.stream()
                .map(position -> {
                    InstrumentKey key = instrumentResolver.resolveNormalized(
                            position.symbol(), position.exchangeSegment()).key();
                    positionKeys.put(new InstrumentKey(position.symbol(), position.exchangeSegment()), key);
                    return key;
                })
                .toList();
        Map<InstrumentKey, Long> ltp = marketDataProvider.getLtpBatch(keys);
        long netPnl = 0L;
        long netQty = 0L;
        for (Position position : positions) {
            InstrumentKey rawKey = new InstrumentKey(position.symbol(), position.exchangeSegment());
            InstrumentKey key = positionKeys.getOrDefault(rawKey, rawKey);
            long last = ltp.getOrDefault(key, position.lastPricePaisa());
            long qty = position.quantity();
            netQty += qty;
            netPnl += (last - position.averagePricePaisa()) * qty;
        }
        return new LivePnlSnapshot(netPnl, netQty);
    }
}
