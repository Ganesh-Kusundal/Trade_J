package com.tradej.broker.core.startup;

import com.tradej.broker.api.IBrokerConnection;
import com.tradej.broker.api.model.BrokerCapabilities;
import com.tradej.broker.api.model.MarketSubscriptionRequest;
import com.tradej.core.domain.model.CandleHistoryRequest;
import com.tradej.core.domain.model.InstrumentKey;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.FeedMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;

public class BrokerLifecycleManager {

    private static final Logger log = LoggerFactory.getLogger(BrokerLifecycleManager.class);
    private static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");

    public void loadInstrumentCatalog(IBrokerConnection brokerConnection, Path catalogPath) {
        if (catalogPath != null && !Files.exists(catalogPath)) {
            throw new IllegalStateException("Instrument catalog file does not exist: " + catalogPath);
        }
        brokerConnection.loadInstrumentCatalog(catalogPath);
        if (brokerConnection.instruments().allInstruments().isEmpty()) {
            throw new IllegalStateException("Instrument catalog loaded zero instruments from " + catalogPath);
        }
        log.info("Instrument catalog loaded: {} instruments from {}",
                brokerConnection.instruments().allInstruments().size(), catalogPath);
    }

    public List<MarketSubscriptionRequest> validateSubscriptions(
            List<SubscriptionConfig> subscriptions,
            BrokerCapabilities brokerCapabilities,
            IBrokerConnection brokerConnection
    ) {
        if (subscriptions == null || subscriptions.isEmpty()) {
            return List.of();
        }
        List<MarketSubscriptionRequest> requests = subscriptions.stream()
                .map(sub -> new MarketSubscriptionRequest(sub.symbol(), Objects.requireNonNull(sub.exchangeSegment(),
                        "Subscription exchange segment is required for " + sub.symbol())))
                .toList();
        for (SubscriptionConfig subscription : subscriptions) {
            if (subscription.feedMode() == null) {
                throw new IllegalStateException("Subscription feed mode is required for " + subscription.symbol());
            }
            brokerCapabilities.validateFeedMode(subscription.exchangeSegment(), subscription.feedMode());
            brokerConnection.instruments().resolve(
                    InstrumentKey.of(subscription.symbol(), subscription.exchangeSegment()));
        }
        return requests;
    }

    public PreflightResult verifyPreflight(
            IBrokerConnection brokerConnection,
            InstrumentKey instrumentKey
    ) {
        try {
            brokerConnection.portfolio().getBalance();
        } catch (RuntimeException ex) {
            log.warn("Preflight balance check failed: {}", ex.getMessage());
            return new PreflightResult(false, "Balance check failed: " + ex.getMessage());
        }
        try {
            brokerConnection.marketData().getQuote(instrumentKey);
        } catch (RuntimeException ex) {
            log.warn("Preflight quote check failed for {} (expected outside market hours): {}",
                    instrumentKey, ex.getMessage());
        }
        try {
            LocalDate latestTradingDate = latestTradingDate();
            var candles = brokerConnection.marketData().getCandles(new CandleHistoryRequest(
                    instrumentKey, "1d",
                    latestTradingDate.minusDays(7), latestTradingDate
            ));
            if (candles.isEmpty()) {
                log.warn("Preflight historical request returned zero candles for {} (expected outside market hours)",
                        instrumentKey);
            }
        } catch (RuntimeException ex) {
            log.warn("Preflight candle check failed for {} (expected outside market hours): {}",
                    instrumentKey, ex.getMessage());
        }
        return new PreflightResult(true, "Preflight passed");
    }

    public void subscribeExplicitly(
            IBrokerConnection brokerConnection,
            List<MarketSubscriptionRequest> subscriptions,
            FeedMode feedMode
    ) {
        if (subscriptions.isEmpty()) {
            throw new IllegalStateException("Refusing to start with zero active subscriptions");
        }
        brokerConnection.websocket().subscribe(subscriptions, feedMode);
    }

    public static LocalDate latestTradingDate() {
        LocalDate date = LocalDate.now(INDIA);
        if (date.getDayOfWeek() == DayOfWeek.SATURDAY) {
            return date.minusDays(1);
        }
        if (date.getDayOfWeek() == DayOfWeek.SUNDAY) {
            return date.minusDays(2);
        }
        return date;
    }

    public record SubscriptionConfig(
            String symbol,
            ExchangeSegment exchangeSegment,
            FeedMode feedMode
    ) {
    }

    public record PreflightResult(
            boolean passed,
            String message
    ) {
    }
}
