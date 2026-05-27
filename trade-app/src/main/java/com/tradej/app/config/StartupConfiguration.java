package com.tradej.app.config;

import com.tradej.app.admin.RuntimeHealthState;
import com.tradej.broker.api.IBrokerConnection;
import com.tradej.broker.api.model.BrokerCapabilities;
import com.tradej.broker.api.model.MarketSubscriptionRequest;
import com.tradej.broker.dhan.auth.DhanTokenProvider;
import com.tradej.broker.dhan.DhanBrokerConnection;
import com.tradej.broker.dhan.config.DhanBrokerStartup;
import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.event.PositionMismatch;
import com.tradej.core.domain.model.CandleHistoryRequest;
import com.tradej.core.domain.model.InstrumentKey;
import com.tradej.core.domain.port.EventBus;
import com.tradej.execution.reconcile.ReconciliationAlertLogger;
import com.tradej.persistence.chronicle.ChronicleAuditLogWriter;
import com.tradej.persistence.duckdb.DuckDbEventStore;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Configures runtime startup orchestration and health state tracking.
 *
 * <p>Contains the {@link ApplicationRunner} that wires event subscriptions,
 * broker connectivity, and preflight validation at application startup.
 * Private helper methods from the original {@link TradingRuntimeConfiguration}
 * are co-located here as they are only used during startup.
 *
 * <p>Extracted from {@link TradingRuntimeConfiguration} to separate startup
 * orchestration from bean definitions (Phase A.2).
 */
@Configuration
public class StartupConfiguration {
    private static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");

    @Bean
    RuntimeHealthState runtimeHealthState() {
        return new RuntimeHealthState();
    }

    @Bean
    ApplicationRunner runtimeStarter(
            TradingProperties properties,
            IBrokerConnection brokerConnection,
            BrokerCapabilities brokerCapabilities,
            DhanTokenProvider dhanTokenProvider,
            RuntimeHealthState runtimeHealthState,
            EventBus eventBus,
            ChronicleAuditLogWriter chronicleAuditLogWriter,
            DuckDbEventStore duckDbEventStore,
            ReconciliationAlertLogger reconciliationAlertLogger
    ) {
        return args -> {
            loadCatalog(properties, brokerConnection, runtimeHealthState);
            List<MarketSubscriptionRequest> subscriptions = validateSubscriptions(properties, brokerConnection, brokerCapabilities);
            dhanTokenProvider.ensureValid();
            verifyBrokerPreflight(brokerConnection, subscriptions);
            runtimeHealthState.markBrokerPreflightPassed();

            eventBus.subscribe(DomainEvent.class, chronicleAuditLogWriter::onEvent);
            eventBus.subscribe(DomainEvent.class, duckDbEventStore::onEvent);
            eventBus.subscribe(PositionMismatch.class, reconciliationAlertLogger::onEvent);
            brokerConnection.websocket().onMarketData(eventBus::publish);
            brokerConnection.websocket().onOrderUpdate(eventBus::publish);

            eventBus.start();
            brokerConnection.connect();
            subscribeExplicitly(brokerConnection, properties, subscriptions);
            runtimeHealthState.markStartupCompleted();
        };
    }

    private void loadCatalog(TradingProperties properties, IBrokerConnection brokerConnection, RuntimeHealthState runtimeHealthState) {
        TradingProperties.InstrumentProperties instruments = properties.instruments();
        String csvPath = instruments == null ? null : instruments.csvPath();
        Path loadedPath = null;
        if (csvPath != null && !csvPath.isBlank()) {
            Path path = Path.of(csvPath);
            if (!Files.exists(path)) {
                throw new IllegalStateException("Instrument catalog file does not exist: " + path);
            }
            brokerConnection.loadInstrumentCatalog(path);
            loadedPath = path;
        } else if (instruments != null && instruments.autoDownload() && brokerConnection instanceof DhanBrokerConnection dhanConnection) {
            String cacheDirectory = instruments.cacheDirectory();
            if (cacheDirectory == null || cacheDirectory.isBlank()) {
                throw new IllegalStateException("Dhan runtime requires `trade.instruments.cache-directory` when auto-download is enabled");
            }
            loadedPath = dhanConnection.loadDailyInstrumentCatalog(Path.of(cacheDirectory), false);
        } else {
            throw new IllegalStateException("Dhan runtime requires `trade.instruments.csv-path` or instrument auto-download");
        }
        if (brokerConnection.instruments().allInstruments().isEmpty()) {
            throw new IllegalStateException("Instrument catalog loaded zero instruments from " + loadedPath);
        }
        runtimeHealthState.markCatalogLoaded(brokerConnection.instruments().allInstruments().size());
    }

    private List<MarketSubscriptionRequest> validateSubscriptions(
            TradingProperties properties,
            IBrokerConnection brokerConnection,
            BrokerCapabilities brokerCapabilities
    ) {
        List<TradingProperties.SubscriptionProperties> configured = properties.subscriptions();
        if (configured == null || configured.isEmpty()) {
            throw new IllegalStateException("Dhan runtime requires at least one explicit market subscription");
        }
        List<MarketSubscriptionRequest> requests = configured.stream()
                .map(subscription -> new MarketSubscriptionRequest(subscription.symbol(), Objects.requireNonNull(subscription.exchangeSegment(),
                        "Subscription exchange segment is required for " + subscription.symbol())))
                .toList();
        for (TradingProperties.SubscriptionProperties subscription : configured) {
            if (subscription.feedMode() == null) {
                throw new IllegalStateException("Subscription feed mode is required for " + subscription.symbol());
            }
            brokerCapabilities.validateFeedMode(subscription.exchangeSegment(), subscription.feedMode());
            DhanBrokerStartup.validateNoDepth200(subscription.exchangeSegment(), subscription.feedMode());
            brokerConnection.instruments().resolve(new InstrumentKey(subscription.symbol(), subscription.exchangeSegment()));
        }
        return requests;
    }

    private void subscribeExplicitly(
            IBrokerConnection brokerConnection,
            TradingProperties properties,
            List<MarketSubscriptionRequest> subscriptions
    ) {
        Map<com.tradej.core.domain.value.FeedMode, List<MarketSubscriptionRequest>> byFeedMode = properties.subscriptions().stream()
                .collect(Collectors.groupingBy(
                        TradingProperties.SubscriptionProperties::feedMode,
                        Collectors.mapping(p -> new MarketSubscriptionRequest(p.symbol(), p.exchangeSegment()), Collectors.toList())
                ));
        if (subscriptions.isEmpty()) {
            throw new IllegalStateException("Refusing to start with zero active subscriptions");
        }
        byFeedMode.forEach((feedMode, requests) -> brokerConnection.websocket().subscribe(requests, feedMode));
    }

    private void verifyBrokerPreflight(IBrokerConnection brokerConnection, List<MarketSubscriptionRequest> subscriptions) {
        if (subscriptions.isEmpty()) {
            throw new IllegalStateException("Broker preflight requires at least one validated subscription");
        }
        MarketSubscriptionRequest seed = subscriptions.get(0);
        InstrumentKey instrumentKey = new InstrumentKey(seed.symbol(), seed.exchangeSegment());
        brokerConnection.portfolio().getBalance();
        brokerConnection.marketData().getQuote(instrumentKey);
        LocalDate latestTradingDate = latestTradingDate();
        if (brokerConnection.marketData().getCandles(new CandleHistoryRequest(
                instrumentKey,
                "1d",
                latestTradingDate.minusDays(7),
                latestTradingDate
        )).isEmpty()) {
            throw new IllegalStateException("Broker preflight historical request returned zero candles for " + instrumentKey);
        }
    }

    private LocalDate latestTradingDate() {
        LocalDate date = LocalDate.now(INDIA);
        if (date.getDayOfWeek() == DayOfWeek.SATURDAY) {
            return date.minusDays(1);
        }
        if (date.getDayOfWeek() == DayOfWeek.SUNDAY) {
            return date.minusDays(2);
        }
        return date;
    }
}
