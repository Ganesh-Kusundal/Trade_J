package com.tradej.app.startup;

import com.tradej.app.config.BrokerTransportProfile;
import com.tradej.app.config.TradingProperties;
import com.tradej.broker.api.IBrokerConnection;
import com.tradej.broker.api.model.BrokerCapabilities;
import com.tradej.broker.core.startup.BrokerLifecycleManager;
import com.tradej.broker.icici.IciciBrokerConnection;
import com.tradej.core.domain.model.InstrumentKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class IciciStartupStrategy implements BrokerStartupStrategy {

    private static final Logger log = LoggerFactory.getLogger(IciciStartupStrategy.class);

    @Override
    public boolean matches(BrokerTransportProfile profile) {
        return profile.isIcici();
    }

    @Override
    public Path loadCatalog(
            TradingProperties properties,
            IBrokerConnection brokerConnection,
            BrokerLifecycleManager lifecycleManager,
            BrokerTransportProfile profile
    ) {
        TradingProperties.InstrumentProperties instruments = properties.instruments();
        String cacheDirectory = instruments != null ? instruments.cacheDirectory() : null;
        if (cacheDirectory == null || cacheDirectory.isBlank()) {
            cacheDirectory = "runtime/icici-instruments";
        }
        Path cachePath = Path.of(cacheDirectory);
        if (instruments != null && instruments.autoDownload()) {
            brokerConnection.loadInstrumentCatalog(null);
            return cachePath;
        }
        if (Files.exists(cachePath)) {
            lifecycleManager.loadInstrumentCatalog(brokerConnection, cachePath);
            return cachePath;
        }
        if (brokerConnection instanceof IciciBrokerConnection) {
            brokerConnection.loadInstrumentCatalog(null);
            return cachePath;
        }
        throw new IllegalStateException("ICICI runtime requires instrument cache at " + cachePath + " or auto-download");
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
    }

    @Override
    public void verifyPreflight(
            IBrokerConnection brokerConnection,
            InstrumentKey seedInstrument,
            BrokerLifecycleManager lifecycleManager,
            BrokerTransportProfile profile
    ) {
        try {
            brokerConnection.portfolio().getBalance();
        } catch (RuntimeException ex) {
            throw new IllegalStateException("ICICI preflight funds check failed", ex);
        }
        lifecycleManager.verifyPreflight(brokerConnection, seedInstrument);
    }
}
