package com.tradej.app.startup;

import com.tradej.app.config.BrokerTransportProfile;
import com.tradej.app.config.TradingProperties;
import com.tradej.broker.api.IBrokerConnection;
import com.tradej.broker.api.model.BrokerCapabilities;
import com.tradej.broker.api.model.MarketSubscriptionRequest;
import com.tradej.broker.api.port.InstrumentResolver;
import com.tradej.broker.core.startup.BrokerLifecycleManager;
import com.tradej.broker.dhan.config.DhanApiEnvironment;
import com.tradej.broker.dhan.config.DhanBrokerStartup;
import com.tradej.core.domain.model.InstrumentKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

@Component
public final class DhanStartupStrategy implements BrokerStartupStrategy {

    private static final Logger log = LoggerFactory.getLogger(DhanStartupStrategy.class);

    @Override
    public boolean matches(BrokerTransportProfile profile) {
        return !profile.isUpstox() && !profile.isIcici() && !profile.gateway() && !profile.isSimulation();
    }

    @Override
    public Path loadCatalog(
            TradingProperties properties,
            IBrokerConnection brokerConnection,
            BrokerLifecycleManager lifecycleManager,
            BrokerTransportProfile profile
    ) {
        TradingProperties.InstrumentProperties instruments = properties.instruments();
        String csvPath = instruments == null ? null : instruments.csvPath();
        if (csvPath != null && !csvPath.isBlank()) {
            Path loadedPath = Path.of(csvPath);
            lifecycleManager.loadInstrumentCatalog(brokerConnection, loadedPath);
            return loadedPath;
        }
        if (instruments != null && instruments.autoDownload()) {
            String cacheDirectory = instruments.cacheDirectory();
            if (cacheDirectory == null || cacheDirectory.isBlank()) {
                throw new IllegalStateException("Dhan runtime requires `trade.instruments.cache-directory` when auto-download is enabled");
            }
            Path cachePath = Path.of(cacheDirectory);
            InstrumentResolver resolver = brokerConnection.instruments();
            Optional<Path> downloaded = resolver.downloadCatalog(cachePath);
            if (downloaded.isPresent()) {
                return downloaded.get();
            }
            throw new IllegalStateException("InstrumentResolver.downloadCatalog() returned empty — install a broker that supports API download");
        }
        if (properties.broker() != null
                && properties.broker().environment() == DhanApiEnvironment.SANDBOX) {
            return null;
        }
        throw new IllegalStateException("Runtime requires `trade.instruments.csv-path` or instrument auto-download");
    }

    @Override
    public void validateSubscriptions(
            List<TradingProperties.SubscriptionProperties> configured,
            IBrokerConnection brokerConnection,
            BrokerCapabilities brokerCapabilities,
            boolean scanEnabled,
            BrokerTransportProfile profile
    ) {
        if (configured == null || configured.isEmpty()) {
            throw new IllegalStateException(
                    "Runtime requires at least one explicit market subscription, or enable trade.scan");
        }
        for (TradingProperties.SubscriptionProperties subscription : configured) {
            DhanBrokerStartup.validateNoDepth200(subscription.exchangeSegment(), subscription.feedMode());
        }
    }

    @Override
    public void verifyPreflight(
            IBrokerConnection brokerConnection,
            InstrumentKey seedInstrument,
            BrokerLifecycleManager lifecycleManager,
            BrokerTransportProfile profile
    ) {
        TradingProperties.DhanProperties broker = null;
        lifecycleManager.verifyPreflight(brokerConnection, seedInstrument);
    }
}
