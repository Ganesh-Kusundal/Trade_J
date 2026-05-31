package com.tradej.broker.dhan.config;

import com.tradej.broker.api.model.BrokerCapabilities;
import com.tradej.broker.api.model.MarketSessionPolicy;
import com.tradej.broker.api.model.VenueCapability;
import com.tradej.broker.dhan.adapter.DhanInstrumentResolver;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.FeedMode;
import com.tradej.broker.dhan.instrument.DhanInstrumentLoader;

import java.nio.file.Path;
import java.time.LocalTime;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/**
 * Centralises Dhan-specific auto-configuration: broker capabilities, startup
 * instrument loading, and preflight subscription validation.
 *
 * <p>This is the single source of Dhan broker defaults. The Spring-based
 * {@link com.tradej.app.config.PropertiesBrokerCapabilities} supersedes
 * {@link #defaultCapabilities()} in production (YAML-driven venues), but
 * the static methods here remain the authoritative fallback and are used
 * by legacy non-DI callers.
 */
public final class DhanBrokerStartup {

    private DhanBrokerStartup() {
    }

    // ---------------------------------------------------------------
    // Broker capabilities (hardcoded defaults)
    // ---------------------------------------------------------------

    /**
     * Returns the hardcoded default {@link BrokerCapabilities} for the Dhan
     * live trading environment.
     *
     * <p>Includes NSE_EQ, NSE_FNO, BSE_EQ, BSE_FNO, MCX_COMM, and IDX_I
     * venues with their respective market sessions and supported feed modes.
     *
     * @return a fully populated BrokerCapabilities with all known Dhan venues
     */
    public static BrokerCapabilities defaultCapabilities() {
        Map<ExchangeSegment, VenueCapability> venues = new EnumMap<>(ExchangeSegment.class);
        MarketSessionPolicy cashSession = new MarketSessionPolicy(LocalTime.of(9, 15), LocalTime.of(15, 30), false);
        MarketSessionPolicy mcxSession = new MarketSessionPolicy(LocalTime.of(9, 0), LocalTime.of(23, 30), true);

        venues.put(ExchangeSegment.NSE_EQ, new VenueCapability(
                ExchangeSegment.NSE_EQ,
                Set.of(FeedMode.TICKER, FeedMode.QUOTE, FeedMode.FULL, FeedMode.DEPTH_20),
                true, false, false,
                true, true, false, true, true,
                cashSession
        ));
        venues.put(ExchangeSegment.NSE_FNO, new VenueCapability(
                ExchangeSegment.NSE_FNO,
                Set.of(FeedMode.TICKER, FeedMode.QUOTE, FeedMode.FULL, FeedMode.DEPTH_20),
                true, false, true,
                true, true, false, true, true,
                cashSession
        ));
        venues.put(ExchangeSegment.BSE_EQ, new VenueCapability(
                ExchangeSegment.BSE_EQ,
                Set.of(FeedMode.TICKER, FeedMode.QUOTE, FeedMode.FULL, FeedMode.DEPTH_20),
                true, false, false,
                true, true, false, true, true,
                cashSession
        ));
        venues.put(ExchangeSegment.BSE_FNO, new VenueCapability(
                ExchangeSegment.BSE_FNO,
                Set.of(FeedMode.TICKER, FeedMode.QUOTE, FeedMode.FULL, FeedMode.DEPTH_20),
                true, false, true,
                true, true, false, true, true,
                cashSession
        ));
        venues.put(ExchangeSegment.MCX_COMM, new VenueCapability(
                ExchangeSegment.MCX_COMM,
                Set.of(FeedMode.TICKER, FeedMode.QUOTE, FeedMode.FULL, FeedMode.DEPTH_20),
                true, false, true,
                true, true, false, true, true,
                mcxSession
        ));
        venues.put(ExchangeSegment.IDX_I, new VenueCapability(
                ExchangeSegment.IDX_I,
                Set.of(FeedMode.TICKER, FeedMode.QUOTE),
                false, false, false,
                false, false, false, false, false,
                cashSession
        ));

        return new BrokerCapabilities(Map.copyOf(venues));
    }

    // ---------------------------------------------------------------
    // Instrument catalog auto-download
    // ---------------------------------------------------------------

    /**
     * Downloads the daily instrument master from Dhan's API, saves it to the
     * given cache directory, and loads it into the supplied resolver.
     *
     * @param resolver       the Dhan instrument resolver to populate
     * @param cacheDirectory directory in which to store the downloaded CSV
     * @param forceRefresh   if {@code true}, re-download even if a cached file exists
     * @return the path to the loaded snapshot file
     */
    public static Path loadDailyInstrumentCatalog(
            DhanInstrumentResolver resolver,
            Path cacheDirectory,
            boolean forceRefresh
    ) {
        DhanInstrumentLoader loader = new DhanInstrumentLoader();
        Path snapshot = loader.ensureDailySnapshot(cacheDirectory, forceRefresh);
        resolver.loadCatalog(snapshot);
        return snapshot;
    }

    // ---------------------------------------------------------------
    // Subscription validation
    // ---------------------------------------------------------------

    /**
     * Validates that the given feed mode is not {@link FeedMode#DEPTH_200},
     * which Dhan live runtime does not support.
     *
     * @param exchangeSegment the target venue (used in the error message)
     * @param feedMode        the feed mode to validate
     * @throws IllegalArgumentException if feedMode is DEPTH_200
     */
    public static void validateNoDepth200(ExchangeSegment exchangeSegment, FeedMode feedMode) {
        if (feedMode == FeedMode.DEPTH_200) {
            throw new IllegalArgumentException(
                    "Dhan live runtime does not support DEPTH_200 subscriptions for " + exchangeSegment
            );
        }
    }
}
