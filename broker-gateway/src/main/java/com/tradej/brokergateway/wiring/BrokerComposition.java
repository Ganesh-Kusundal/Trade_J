package com.tradej.brokergateway.wiring;

import com.tradej.broker.api.IBrokerConnection;
import com.tradej.broker.api.port.IdempotencyCachePort;
import com.tradej.broker.api.spi.BrokerProvider;
import com.tradej.broker.api.spi.BrokerRegistry;
import com.tradej.broker.api.spi.BrokerSource;
import com.tradej.broker.api.spi.ServiceLoaderBrokerRegistry;
import com.tradej.broker.core.startup.BrokerLifecycleManager;
import com.tradej.brokergateway.config.BrokerProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public final class BrokerComposition {

    private static final Logger log = LoggerFactory.getLogger(BrokerComposition.class);

    private final BrokerProfile profile;
    private final IBrokerConnection brokerConnection;
    private final BrokerLifecycleManager lifecycleManager;

    private BrokerComposition(BrokerProfile profile, IBrokerConnection brokerConnection) {
        this.profile = profile;
        this.brokerConnection = brokerConnection;
        this.lifecycleManager = new BrokerLifecycleManager();
    }

    public static BrokerComposition create(BrokerProfile profile) {
        return create(profile, null);
    }

    public static BrokerComposition create(BrokerProfile profile, IdempotencyCachePort idempotencyCache) {
        // Validate configuration before creating broker
        profile.validate();
        
        // Use SPI-based broker discovery via BrokerRegistry
        BrokerRegistry registry = new ServiceLoaderBrokerRegistry();
        BrokerSource source = BrokerSource.parse(profile.brokerType().name());
        
        BrokerProvider provider = registry.provider(source)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No BrokerProvider found for broker type: " + profile.brokerType()));
        
        // Convert BrokerProfile to generic configuration map for SPI provider
        Map<String, Object> config = profile.toGenericConfig();
        
        IBrokerConnection connection = provider.create(config);
        log.info("Created {} broker composition via SPI provider: {}", profile.brokerType(), provider.displayName());
        return new BrokerComposition(profile, connection);
    }

    public BrokerProfile profile() {
        return profile;
    }

    public IBrokerConnection brokerConnection() {
        return brokerConnection;
    }

    public BrokerLifecycleManager lifecycleManager() {
        return lifecycleManager;
    }
}
