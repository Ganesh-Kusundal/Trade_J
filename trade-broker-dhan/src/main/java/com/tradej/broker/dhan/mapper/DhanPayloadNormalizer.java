package com.tradej.broker.dhan.mapper;

import com.tradej.broker.dhan.instrument.DhanInstrumentDefinition;
import com.tradej.core.domain.event.EventMetadata;
import com.tradej.core.domain.event.TickReceived;
import com.tradej.core.domain.model.DepthLevel;
import com.tradej.core.domain.model.MarketDepth;
import com.tradej.core.domain.model.Order;
import com.tradej.core.domain.model.Trade;
import com.tradej.core.domain.value.PriceMath;

import java.math.BigDecimal;
import java.util.List;

public final class DhanPayloadNormalizer {
    public TickReceived normalizeTick(DhanSdkResponse<?> source, DhanInstrumentDefinition definition) {
        MarketDepth depth = normalizeDepth(source, definition);
        return new TickReceived(
                EventMetadata.root(),
                definition.canonicalSymbol(),
                "1s",
                marketPricePaisa(source),
                source.optionalLong("getLtq").orElse(0L),
                source.optionalLong("getVolume").orElse(0L),
                marketTimestampMs(source),
                depth
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
        if (bidsRaw instanceof List<?> || asksRaw instanceof List<?>) {
            List<DepthLevel> bidLevels = depthLevels(bidsRaw);
            List<DepthLevel> askLevels = depthLevels(asksRaw);
            return new MarketDepth(definition.toInstrument(), bidLevels, askLevels,
                    Math.max(bidLevels.size(), askLevels.size()), marketTimestampMs(source));
        }
        return null;
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
