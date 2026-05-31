package com.tradej.broker.dhan.mapper;

import com.tradej.broker.dhan.instrument.DhanInstrumentDefinition;
import com.tradej.core.domain.event.EventMetadataFactory;
import com.tradej.core.domain.event.MarketTickEvent;
import com.tradej.core.domain.event.TickReceived;
import com.tradej.core.domain.model.DepthLevel;
import com.tradej.core.domain.model.MarketDepth;
import com.tradej.core.domain.model.Order;
import com.tradej.core.domain.model.Trade;
import com.tradej.core.domain.value.FeedMode;
import com.tradej.core.domain.value.PriceMath;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public final class DhanPayloadNormalizer {

    private final EventMetadataFactory metadataFactory;

    public DhanPayloadNormalizer() {
        this(new EventMetadataFactory(new com.tradej.core.domain.time.LiveTradingClock()));
    }

    public DhanPayloadNormalizer(EventMetadataFactory metadataFactory) {
        this.metadataFactory = metadataFactory;
    }

    /**
     * @deprecated Use {@link #normalizeToMarketTick(DhanSdkResponse, DhanInstrumentDefinition, FeedMode)} instead.
     */
    @Deprecated(since = "2.0", forRemoval = true)
    public TickReceived normalizeTick(DhanSdkResponse<?> source, DhanInstrumentDefinition definition) {
        MarketDepth depth = normalizeDepth(source, definition);
        return new TickReceived(
                metadataFactory.root(),
                definition.canonicalSymbol(),
                "1s",
                marketPricePaisa(source),
                source.optionalLong("getLtq").orElse(0L),
                source.optionalLong("getVolume").orElse(0L),
                marketTimestampMs(source),
                depth
        );
    }

    /**
     * Normalizes a Dhan SDK payload into the canonical {@link MarketTickEvent}.
     *
     * @param source     the raw Dhan SDK response object
     * @param definition the instrument definition for the symbol
     * @param feedMode   the feed mode under which this tick was received
     * @return a canonical {@link MarketTickEvent} suitable for the hot path
     */
    public MarketTickEvent normalizeToMarketTick(DhanSdkResponse<?> source, DhanInstrumentDefinition definition, FeedMode feedMode) {
        MarketDepth depth = normalizeDepth(source, definition);
        return new MarketTickEvent(
                metadataFactory.root(),
                0L, // sequenceId assigned by pipeline
                definition.canonicalSymbol(),
                definition.exchangeSegment(),
                feedMode,
                marketPricePaisa(source),
                source.optionalLong("getLtq").orElse(0L),
                source.optionalLong("getVolume").orElse(0L),
                marketTimestampMs(source),
                Optional.ofNullable(depth)
        );
    }

    public Order normalizeOrder(DhanSdkResponse<?> source, DhanInstrumentDefinition definition) {
        return DhanSdkMapper.toOrder(source, definition.toInstrument());
    }

    public Trade normalizeTrade(DhanSdkResponse<?> source, DhanInstrumentDefinition definition) {
        return DhanSdkMapper.toTrade(source, definition.toInstrument());
    }

    private long marketPricePaisa(DhanSdkResponse<?> source) {
        return PriceMath.toPaisa(firstDecimal(source, "getLtp", "getLastPrice", "getIndexValue"));
    }

    private long marketTimestampMs(DhanSdkResponse<?> source) {
        return source.timestampMillis("getLastTradeTime")
                .or(() -> source.timestampMillis("getLtt"))
                .orElse(System.currentTimeMillis());
    }

    private MarketDepth normalizeDepth(DhanSdkResponse<?> source, DhanInstrumentDefinition definition) {
        Object bidsRaw = source.invoke("getBids").orElse(null);
        Object asksRaw = source.invoke("getAsks").orElse(null);
        List<DepthLevel> bidLevels = depthLevels(bidsRaw);
        List<DepthLevel> askLevels = depthLevels(asksRaw);
        if (bidLevels.isEmpty() && askLevels.isEmpty()) {
            return null;
        }
        return new MarketDepth(definition.toInstrument(), bidLevels, askLevels,
                Math.max(bidLevels.size(), askLevels.size()), marketTimestampMs(source));
    }

    private List<DepthLevel> depthLevels(Object raw) {
        if (!(raw instanceof List<?> entries)) {
            return List.of();
        }
        return entries.stream()
                .map(entry -> {
                    DhanSdkResponse<?> entryResponse = new DhanSdkResponse<>(entry);
                    return new DepthLevel(
                            PriceMath.toPaisa(entryResponse.decimal("getPrice")),
                            entryResponse.optionalLong("getQuantity").orElse(0L),
                            entryResponse.optionalLong("getOrders").orElse(0L).intValue());
                })
                .toList();
    }

    private BigDecimal firstDecimal(DhanSdkResponse<?> source, String... accessors) {
        return source.decimal(accessors);
    }
}
