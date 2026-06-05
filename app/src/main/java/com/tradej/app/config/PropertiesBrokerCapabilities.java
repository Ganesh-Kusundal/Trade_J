package com.tradej.app.config;

import com.tradej.broker.api.model.BrokerCapabilities;
import com.tradej.broker.api.model.MarketSessionPolicy;
import com.tradej.broker.api.model.VenueCapability;
import com.tradej.core.domain.value.ExchangeSegment;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/**
 * Creates a {@link BrokerCapabilities} instance from YAML-configured venue
 * properties, enabling OCP-compliant venue configuration without code changes.
 *
 * <p>This is the primary source of broker capabilities in production.
 *
 * <p>See {@code application.yml → trade.venues} for the configuration format.
 */
public final class PropertiesBrokerCapabilities {

    private PropertiesBrokerCapabilities() {
    }

    /**
     * Builds a {@link BrokerCapabilities} from the given venue properties map.
     *
     * @param venueProperties map of venue name → {@link TradingProperties.VenueProperties},
     *                        typically sourced from {@code trade.venues} in application.yml
     * @return fully populated BrokerCapabilities with all configured venues
     */
    public static BrokerCapabilities from(Map<String, TradingProperties.VenueProperties> venueProperties) {
        if (venueProperties == null || venueProperties.isEmpty()) {
            throw new IllegalArgumentException("At least one venue must be configured under trade.venues");
        }

        Map<ExchangeSegment, VenueCapability> venues = new EnumMap<>(ExchangeSegment.class);
        for (Map.Entry<String, TradingProperties.VenueProperties> entry : venueProperties.entrySet()) {
            ExchangeSegment segment = ExchangeSegment.valueOf(entry.getKey());
            venues.put(segment, toVenueCapability(segment, entry.getValue()));
        }
        return new BrokerCapabilities(Map.copyOf(venues));
    }

    private static VenueCapability toVenueCapability(ExchangeSegment segment, TradingProperties.VenueProperties props) {
        MarketSessionPolicy sessionPolicy = new MarketSessionPolicy(
                props.sessionOpen(),
                props.sessionClose(),
                props.supportsLateSession()
        );
        return new VenueCapability(
                segment,
                Set.copyOf(props.supportedFeedModes()),
                props.supportsDepth20(),
                props.supportsDepth200(),
                props.requiresContractDiscovery(),
                false, false, false, false, false,
                sessionPolicy
        );
    }
}
