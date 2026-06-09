package com.tradej.broker.dhan.config;

import com.tradej.broker.api.IBrokerConnection;
import com.tradej.broker.api.port.IdempotencyCachePort;
import com.tradej.core.domain.model.Order;
import com.tradej.broker.core.rate.MultiBucketRateLimiter;
import com.tradej.broker.core.startup.BrokerLifecycleManager;
import com.tradej.broker.dhan.DhanBrokerConnection;
import com.tradej.broker.dhan.auth.DhanTokenManager;
import com.tradej.broker.dhan.auth.DhanTokenProvider;
import com.tradej.broker.dhan.constants.DhanProtocolConstants;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

import java.nio.file.Path;
import java.util.Optional;

@AutoConfiguration
@ConditionalOnClass(DhanBrokerConnection.class)
@ConditionalOnProperty(name = "trade.broker-type", havingValue = "dhan")
public class DhanAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public DhanConnectionSettings dhanConnectionSettings(Environment environment) {
        String clientId = environment.getRequiredProperty("trade.dhan.client-id");
        String accessToken = environment.getProperty("trade.dhan.access-token");

        DhanApiEnvironment dhanEnv = DhanApiEnvironment.valueOf(
                environment.getProperty("trade.dhan.environment", "LIVE"));
        DhanAuthMode authMode = DhanAuthMode.valueOf(
                environment.getProperty("trade.dhan.auth-mode", "STATIC"));
        String restBaseUrl = environment.getProperty("trade.dhan.rest-base-url");
        Path pinFile = getPathOrNull(environment, "trade.dhan.pin-file");
        Path totpSecretFile = getPathOrNull(environment, "trade.dhan.totp-secret-file");
        Path tokenStateFile = getPathOrNull(environment, "trade.dhan.token-state-file");
        long refreshBufferMinutes = environment.getProperty(
                "trade.dhan.refresh-buffer-minutes", Long.class, 10L);

        return DhanConnectionSettings.withDefaults(
                clientId,
                accessToken,
                dhanEnv,
                restBaseUrl,
                authMode,
                pinFile,
                totpSecretFile,
                tokenStateFile,
                refreshBufferMinutes
        );
    }

    @Bean
    @ConditionalOnMissingBean(DhanTokenProvider.class)
    public DhanTokenManager dhanTokenManager(DhanConnectionSettings settings) {
        return new DhanTokenManager(settings);
    }

    @Bean
    @ConditionalOnMissingBean
    public IdempotencyCachePort dhanIdempotencyCache() {
        return new NoOpIdempotencyCache();
    }

    @Bean
    @ConditionalOnMissingBean(IBrokerConnection.class)
    public DhanBrokerConnection dhanBrokerConnection(
            DhanConnectionSettings settings,
            IdempotencyCachePort idempotencyCache) {
        MultiBucketRateLimiter rateLimiter = DhanProtocolConstants.defaultRateLimiter();
        return new DhanBrokerConnection(settings, rateLimiter, idempotencyCache);
    }

    @Bean
    @ConditionalOnMissingBean
    public BrokerLifecycleManager dhanBrokerLifecycleManager() {
        return new BrokerLifecycleManager();
    }

    private static Path getPathOrNull(Environment environment, String propertyName) {
        String value = environment.getProperty(propertyName);
        if (value == null || value.isBlank()) {
            return null;
        }
        return Path.of(value);
    }

    private static final class NoOpIdempotencyCache implements IdempotencyCachePort {
        @Override
        public Optional<Order> get(String clientOrderId) {
            return Optional.empty();
        }

        @Override
        public void put(String clientOrderId, Order order) {
        }

        @Override
        public void remove(String clientOrderId) {
        }
    }
}
