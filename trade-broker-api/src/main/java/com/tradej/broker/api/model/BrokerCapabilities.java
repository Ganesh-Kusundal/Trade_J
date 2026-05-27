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
}
