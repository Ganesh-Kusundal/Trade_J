package com.tradej.app.startup;

import com.tradej.app.config.BrokerTransportProfile;
import com.tradej.app.config.TradingProperties;
import com.tradej.broker.api.IBrokerConnection;
import com.tradej.broker.api.model.BrokerCapabilities;
import com.tradej.broker.core.startup.BrokerLifecycleManager;
import com.tradej.core.domain.model.InstrumentKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;

@Component
public final class SimulationStartupStrategy implements BrokerStartupStrategy {

    private static final Logger log = LoggerFactory.getLogger(SimulationStartupStrategy.class);

    @Override
    public boolean matches(BrokerTransportProfile profile) {
        return profile.isSimulation();
    }

    @Override
    public Path loadCatalog(
            TradingProperties properties,
            IBrokerConnection brokerConnection,
            BrokerLifecycleManager lifecycleManager,
            BrokerTransportProfile profile
    ) {
        log.info("Simulation mode: skipping instrument catalog download");
        return null;
    }

    @Override
    public void validateSubscriptions(
            List<TradingProperties.SubscriptionProperties> configured,
            IBrokerConnection brokerConnection,
            BrokerCapabilities brokerCapabilities,
            boolean scanEnabled,
            BrokerTransportProfile profile
    ) {
        log.info("Simulation mode: skipping subscription validation");
    }

    @Override
    public void verifyPreflight(
            IBrokerConnection brokerConnection,
            InstrumentKey seedInstrument,
            BrokerLifecycleManager lifecycleManager,
            BrokerTransportProfile profile
    ) {
        log.info("Simulation mode: skipping preflight checks");
    }
}
