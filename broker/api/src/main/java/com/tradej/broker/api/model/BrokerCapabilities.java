package com.tradej.broker.api.model;

import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.FeedMode;

import java.util.Map;

public record BrokerCapabilities(
        Map<ExchangeSegment, VenueCapability> venues
) {
    public VenueCapability requireVenue(ExchangeSegment exchangeSegment) {
        VenueCapability capability = venues.get(exchangeSegment);
        if (capability == null) {
            throw new IllegalArgumentException("No broker capability declared for venue " + exchangeSegment);
        }
        return capability;
    }

    public void validateFeedMode(ExchangeSegment exchangeSegment, FeedMode feedMode) {
        VenueCapability capability = requireVenue(exchangeSegment);
        if (!capability.supportedFeedModes().contains(feedMode)) {
            throw new IllegalArgumentException("Feed mode " + feedMode + " is not supported for venue " + exchangeSegment);
        }
    }

    // ─── OMS capability queries ─────────────────────────────────────────────────
    // The OMS uses these to decide: synthetic orchestration vs native broker support.

    public boolean supportsBracketOrders(ExchangeSegment exchangeSegment) {
        return requireVenue(exchangeSegment).supportsBracketOrders();
    }

    public boolean supportsTrailingStop(ExchangeSegment exchangeSegment) {
        return requireVenue(exchangeSegment).supportsTrailingStop();
    }

    public boolean supportsCoverOrders(ExchangeSegment exchangeSegment) {
        return requireVenue(exchangeSegment).supportsCoverOrders();
    }

    public boolean supportsIceberg(ExchangeSegment exchangeSegment) {
        return requireVenue(exchangeSegment).supportsIceberg();
    }

    public boolean supportsGTT(ExchangeSegment exchangeSegment) {
        return requireVenue(exchangeSegment).supportsGTT();
    }

    public boolean supportsDepth(ExchangeSegment exchangeSegment, int levels) {
        VenueCapability v = requireVenue(exchangeSegment);
        if (levels <= 5) {
            return v.supportedFeedModes().contains(FeedMode.DEPTH_20);
        }
        if (levels <= 20) {
            return v.supportsDepth20();
        }
        if (levels <= 200) {
            return v.supportsDepth200();
        }
        return false;
    }

    public FeedMode maxFeedMode(ExchangeSegment exchangeSegment) {
        VenueCapability v = requireVenue(exchangeSegment);
        if (v.supportsDepth200()) {
            return FeedMode.DEPTH_200;
        }
        if (v.supportsDepth20()) {
            return FeedMode.DEPTH_20;
        }
        if (v.supportedFeedModes().contains(FeedMode.FULL)) {
            return FeedMode.FULL;
        }
        if (v.supportedFeedModes().contains(FeedMode.QUOTE)) {
            return FeedMode.QUOTE;
        }
        return FeedMode.TICKER;
    }
}
