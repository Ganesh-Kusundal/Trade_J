package com.tradej.app.startup;

import com.tradej.app.config.BrokerTransportProfile;
import com.tradej.app.config.TradingProperties;
import com.tradej.broker.api.IBrokerConnection;
import com.tradej.broker.api.model.BrokerCapabilities;
import com.tradej.broker.core.startup.BrokerLifecycleManager;
import com.tradej.core.domain.model.InstrumentKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.List;

public final class GatewayStartupStrategy implements BrokerStartupStrategy {

    private static final Logger log = LoggerFactory.getLogger(GatewayStartupStrategy.class);

    @Override
    public boolean matches(BrokerTransportProfile profile) {
        return profile.gateway();
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
        String cacheDirectory = instruments != null ? instruments.cacheDirectory() : null;
        if (cacheDirectory == null || cacheDirectory.isBlank()) {
            cacheDirectory = "runtime-prod/instruments";
        }
        Path loadedPath = Path.of(cacheDirectory);
        lifecycleManager.loadInstrumentCatalog(brokerConnection, loadedPath);
        return loadedPath;
    }

    @Override
    public void validateSubscriptions(
            List<TradingProperties.SubscriptionProperties> configured,
            IBrokerConnection brokerConnection,
            BrokerCapabilities brokerCapabilities,
            boolean scanEnabled,
            BrokerTransportProfile profile
    ) {
        // Gateway accepts any subscription — individual broker nodes validate their own
    }

    @Override
    public void verifyPreflight(
            IBrokerConnection brokerConnection,
            InstrumentKey seedInstrument,
            BrokerLifecycleManager lifecycleManager,
            BrokerTransportProfile profile
    ) {
        lifecycleManager.verifyPreflight(brokerConnection, seedInstrument);
    }
}
