package com.tradej.composition;

import com.tradej.broker.api.IBrokerConnection;
import com.tradej.broker.api.port.IdempotencyCachePort;
import com.tradej.broker.core.startup.BrokerLifecycleManager;
import com.tradej.broker.dhan.DhanBrokerConnection;
import com.tradej.broker.dhan.config.DhanConnectionSettings;
import com.tradej.broker.upstox.config.UpstoxConnectionSettings;
import com.tradej.composition.config.BrokerProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.Optional;

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
        
        IBrokerConnection connection = switch (profile.brokerType()) {
            case DHAN, GATEWAY -> createDhan(profile.dhan(), idempotencyCache);
            case UPSTOX -> createUpstox(profile.upstox());
            case ICICI -> IciciBrokerFactory.create(profile.icici());
        };
        log.info("Created {} broker composition", profile.brokerType());
        return new BrokerComposition(profile, connection);
    }

    private static IBrokerConnection createDhan(
            BrokerProfile.DhanConfig dhan,
            IdempotencyCachePort idempotencyCache
    ) {
        DhanConnectionSettings settings = new DhanConnectionSettings(
                dhan.clientId(),
                dhan.accessToken(),
                dhan.environment(),
                dhan.restBaseUrl(),
                false,
                3,
                5,
                true,
                true,
                dhan.authMode(),
                dhan.pinFile(),
                dhan.totpSecretFile(),
                dhan.tokenStateFile(),
                dhan.refreshBufferMinutes(),
                null,
                false
        );
        IdempotencyCachePort cache = idempotencyCache != null ? idempotencyCache : new NoOpIdempotencyCache();
        return new DhanBrokerConnection(settings, com.tradej.broker.dhan.constants.DhanProtocolConstants.defaultRateLimiter(), cache);
    }

    private static IBrokerConnection createUpstox(BrokerProfile.UpstoxConfig upstox) {
        UpstoxConnectionSettings settings = new UpstoxConnectionSettings(
                upstox.clientId(),
                upstox.clientSecret(),
                upstox.redirectUri(),
                upstox.accessToken(),
                upstox.refreshToken(),
                upstox.analyticsToken(),
                upstox.extendedToken(),
                upstox.analyticsOnly(),
                upstox.isSandbox(),
                upstox.redirectServerPort(),
                upstox.refreshBufferMs(),
                upstox.tokenExpiryBufferMs()
        );
        return UpstoxBrokerFactory.create(settings, Path.of("runtime/upstox-token-state.json"));
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

    private static final class NoOpIdempotencyCache implements IdempotencyCachePort {
        @Override
        public Optional<com.tradej.core.domain.model.Order> get(String clientOrderId) {
            return Optional.empty();
        }

        @Override
        public void put(String clientOrderId, com.tradej.core.domain.model.Order order) {
        }

        @Override
        public void remove(String clientOrderId) {
        }
    }
}
