package com.tradej.broker.upstox.websocket;

import com.tradej.broker.upstox.instrument.UpstoxInstrumentResolver;
import com.tradej.core.domain.event.EventMetadataFactory;
import com.tradej.core.domain.event.MarketTickEvent;
import com.tradej.core.domain.model.DepthLevel;
import com.tradej.core.domain.model.MarketDepth;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.FeedMode;

import java.util.Optional;

/**
 * Normalizes parsed Upstox binary frames into canonical {@link MarketTickEvent}.
 */
public final class UpstoxStreamNormalizer {

    private final UpstoxInstrumentResolver instrumentResolver;
    private final EventMetadataFactory metadataFactory;

    public UpstoxStreamNormalizer(UpstoxInstrumentResolver instrumentResolver,
                                  EventMetadataFactory metadataFactory) {
        this.instrumentResolver = instrumentResolver;
        this.metadataFactory = metadataFactory;
    }

    /**
     * Converts a parsed binary frame to a canonical MarketTickEvent.
     */
    public MarketTickEvent toMarketTick(ParsedFeedFrame frame, FeedMode feedMode, long sequenceId) {
        String symbol = instrumentResolver.resolveSymbol(frame.instrumentToken());
        ExchangeSegment segment = instrumentResolver.resolveSegment(frame.instrumentToken());
        MarketDepth depth = null;
        if (frame.hasDepth()) {
            java.util.List<DepthLevel> bids = frame.bidList().stream()
                    .map(d -> new DepthLevel(d.pricePaisa(), d.quantity(), d.orderCount()))
                    .toList();
            java.util.List<DepthLevel> asks = frame.askList().stream()
                    .map(d -> new DepthLevel(d.pricePaisa(), d.quantity(), d.orderCount()))
                    .toList();
            depth = new MarketDepth(null, bids, asks,
                    frame.bids() != null ? frame.bids().length : 0,
                    frame.exchangeTimestampMs());
        }
        return new MarketTickEvent(
                metadataFactory.root(),
                sequenceId,
                symbol,
                segment,
                feedMode,
                frame.ltpPaisa(),
                frame.lastTradeQuantity(),
                frame.volume(),
                frame.exchangeTimestampMs(),
                Optional.ofNullable(depth)
        );
    }
}
