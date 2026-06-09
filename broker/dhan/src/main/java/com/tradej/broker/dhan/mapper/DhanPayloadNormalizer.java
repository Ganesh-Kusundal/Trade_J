package com.tradej.broker.dhan.mapper;

import com.tradej.broker.dhan.instrument.DhanInstrumentDefinition;
import com.tradej.broker.dhan.websocket.feed.DhanMarketFeedPacket;
import com.tradej.core.domain.event.EventMetadataFactory;
import com.tradej.core.domain.event.MarketTickEvent;
import com.tradej.core.domain.model.DepthLevel;
import com.tradej.core.domain.model.MarketDepth;
import com.tradej.core.domain.model.Order;
import com.tradej.core.domain.model.Trade;
import com.tradej.core.domain.value.FeedMode;
import com.tradej.core.domain.value.PriceMath;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

public final class DhanPayloadNormalizer {

    private final EventMetadataFactory metadataFactory;
    private final AtomicLong sequenceCounter = new AtomicLong();

    public DhanPayloadNormalizer() {
        this(new EventMetadataFactory(new com.tradej.core.domain.time.LiveTradingClock()));
    }

    public DhanPayloadNormalizer(EventMetadataFactory metadataFactory) {
        this.metadataFactory = metadataFactory;
    }

    public MarketTickEvent normalizeFeedPacket(
            DhanMarketFeedPacket packet,
            DhanInstrumentDefinition definition,
            FeedMode feedMode
    ) {
        return switch (packet) {
            case DhanMarketFeedPacket.Ticker ticker -> new MarketTickEvent(
                    metadataFactory.root(),
                    sequenceCounter.incrementAndGet(),
                    definition.canonicalSymbol(),
                    definition.exchangeSegment(),
                    feedMode,
                    safeToPaisa(ticker.ltp()),
                    0L,
                    0L,
                    toEpochMs(ticker.ltt()),
                    Optional.empty(),
                    0L,
                    0L
            );
            case DhanMarketFeedPacket.Index index -> new MarketTickEvent(
                    metadataFactory.root(),
                    sequenceCounter.incrementAndGet(),
                    definition.canonicalSymbol(),
                    definition.exchangeSegment(),
                    feedMode,
                    safeToPaisa(index.indexValue()),
                    0L,
                    0L,
                    System.currentTimeMillis(),
                    Optional.empty(),
                    0L,
                    0L
            );
            case DhanMarketFeedPacket.Quote quote -> new MarketTickEvent(
                    metadataFactory.root(),
                    sequenceCounter.incrementAndGet(),
                    definition.canonicalSymbol(),
                    definition.exchangeSegment(),
                    feedMode,
                    safeToPaisa(quote.ltp()),
                    quote.ltq(),
                    quote.volume(),
                    toEpochMs(quote.ltt()),
                    Optional.empty(),
                    0L,
                    0L
            );
            case DhanMarketFeedPacket.Full full -> new MarketTickEvent(
                    metadataFactory.root(),
                    sequenceCounter.incrementAndGet(),
                    definition.canonicalSymbol(),
                    definition.exchangeSegment(),
                    feedMode,
                    safeToPaisa(full.ltp()),
                    full.ltq(),
                    full.volume(),
                    toEpochMs(full.ltt()),
                    Optional.of(normalizeFeedDepth(full, definition)),
                    full.openInterest(),
                    full.openInterest()
            );
            case DhanMarketFeedPacket.Oi oi -> new MarketTickEvent(
                    metadataFactory.root(),
                    sequenceCounter.incrementAndGet(),
                    definition.canonicalSymbol(),
                    definition.exchangeSegment(),
                    feedMode,
                    0L,
                    0L,
                    0L,
                    System.currentTimeMillis(),
                    Optional.empty(),
                    oi.openInterest(),
                    oi.openInterest()
            );
            case DhanMarketFeedPacket.Heartbeat ignored -> null;
            case DhanMarketFeedPacket.MarketStatus ignored -> null;
            case DhanMarketFeedPacket.PrevClose ignored -> null;
        };
    }

    public Order normalizeOrder(DhanJsonResponse source, DhanInstrumentDefinition definition) {
        return DhanJsonMapper.toOrder(source, definition.toInstrument());
    }

    public Trade normalizeTrade(DhanJsonResponse source, DhanInstrumentDefinition definition) {
        return DhanJsonMapper.toTrade(source, definition.toInstrument());
    }

    private MarketDepth normalizeFeedDepth(DhanMarketFeedPacket.Full full, DhanInstrumentDefinition definition) {
        List<DepthLevel> bids = full.bids().stream()
                .map(level -> new DepthLevel(safeToPaisa(level.price()), level.quantity(), level.orders()))
                .toList();
        List<DepthLevel> asks = full.asks().stream()
                .map(level -> new DepthLevel(safeToPaisa(level.price()), level.quantity(), level.orders()))
                .toList();
        return new MarketDepth(definition.toInstrument(), bids, asks, Math.max(bids.size(), asks.size()), toEpochMs(full.ltt()));
    }

    private static long safeToPaisa(String value) {
        return (value == null || value.isBlank()) ? 0L : PriceMath.toPaisa(value);
    }

    /**
     * Convert Dhan wire-format timestamp to epoch milliseconds.
     * Dhan sends timestamps in seconds (epoch) or in microseconds.
     * Values &gt; 1e12 are interpreted as microseconds and converted to ms;
     * smaller values are treated as seconds and converted to ms.
     */
    public static long toEpochMs(long dhanTimestamp) {
        return dhanTimestamp > 1_000_000_000_000L ? dhanTimestamp : dhanTimestamp * 1000L;
    }
}
