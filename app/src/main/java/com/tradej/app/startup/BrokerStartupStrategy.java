package com.tradej.app.startup;

import com.tradej.app.admin.RuntimeHealthState;
import com.tradej.app.config.BrokerTransportProfile;
import com.tradej.app.config.TradingProperties;
import com.tradej.broker.api.IBrokerConnection;
import com.tradej.broker.api.model.BrokerCapabilities;
import com.tradej.broker.api.model.MarketSubscriptionRequest;
import com.tradej.broker.core.startup.BrokerLifecycleManager;
import com.tradej.core.domain.model.InstrumentKey;

import java.util.List;

/**
 * Broker-specific startup strategy. Each broker adapter provides its own
 * implementation for catalog loading, subscription validation, and preflight checks.
 *
 * <p>Eliminates broker-specific conditionals from {@link BrokerStartupOrchestrator}.
 */
public interface BrokerStartupStrategy {

    /**
     * Load the instrument catalog for this broker.
     *
     * @return the path where the catalog was loaded from, or null if not applicable
     */
    java.nio.file.Path loadCatalog(
            TradingProperties properties,
            IBrokerConnection brokerConnection,
            BrokerLifecycleManager lifecycleManager,
            BrokerTransportProfile profile
    );

    /**
     * Validate subscriptions for this broker. May throw if subscriptions are
     * incompatible with the broker's capabilities.
     */
    void validateSubscriptions(
            List<TradingProperties.SubscriptionProperties> configured,
            IBrokerConnection brokerConnection,
            BrokerCapabilities brokerCapabilities,
            boolean scanEnabled,
            BrokerTransportProfile profile
    );

    /**
     * Run broker-specific preflight checks (balance, LTP, historical data).
     */
    void verifyPreflight(
            IBrokerConnection brokerConnection,
            InstrumentKey seedInstrument,
            BrokerLifecycleManager lifecycleManager,
            BrokerTransportProfile profile
    );

    /**
     * Whether this strategy matches the given transport profile.
     */
    boolean matches(BrokerTransportProfile profile);
}
