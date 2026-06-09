package com.tradej.app.config;

import com.tradej.broker.api.IBrokerConnection;
import com.tradej.broker.api.model.BrokerTransportCapabilities;
import com.tradej.broker.core.rate.MultiBucketRateLimiter;
import com.tradej.broker.dhan.auth.DhanTokenManager;
import com.tradej.broker.dhan.auth.DhanTokenProvider;
import com.tradej.broker.dhan.config.DhanConfigPaths;
import com.tradej.broker.dhan.config.DhanConnectionSettings;
import com.tradej.broker.dhan.constants.DhanProtocolConstants;
import com.tradej.composition.BrokerComposition;
import com.tradej.composition.config.BrokerProfile;
import com.tradej.execution.service.CaffeineIdempotencyCache;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Dhan-specific broker configuration beans.
 *
 * @deprecated Migrated to {@link UnifiedBrokerConfiguration} to resolve competing
 * dependency graphs. This class will be removed in a future release.
 * All beans are now created conditionally based on {@code trade.broker-type} property.
 *
 * <p>Active when {@code trade.broker-type=dhan} or {@code trade.broker-type=gateway}.
 */
@Deprecated(since = "2026-06-09", forRemoval = true)
@Configuration
@ConditionalOnExpression("'${trade.broker-type:dhan}' == 'dhan' || '${trade.broker-type:dhan}' == 'gateway'")
public class DhanBrokerConfiguration {

    @Bean
    @Primary
    MultiBucketRateLimiter multiBucketRateLimiter() {
        return DhanProtocolConstants.defaultRateLimiter();
    }

    @Bean
    DhanConnectionSettings dhanConnectionSettings(TradingProperties properties) {
        TradingProperties.DhanProperties broker = properties.broker();
        return new DhanConnectionSettings(
                broker.clientId(),
                broker.accessToken(),
                broker.environment(),
                broker.restBaseUrl(),
                broker.loggingEnabled(),
                broker.rateLimitRetries(),
                broker.maxReconnectAttempts(),
                broker.autoReconnectEnabled(),
                broker.autoResubscribeEnabled(),
                broker.authMode(),
                DhanConfigPaths.resolve(broker.pinFile()),
                DhanConfigPaths.resolve(broker.totpSecretFile()),
                DhanConfigPaths.resolve(broker.tokenStateFile()),
                broker.refreshBufferMinutes(),
                null,
                false
        );
    }

    @Bean
    DhanTokenProvider dhanTokenProvider(DhanConnectionSettings settings) {
        return new DhanTokenManager(settings);
    }

    @Bean
    BrokerComposition brokerComposition(TradingProperties properties, CaffeineIdempotencyCache idempotencyCache) {
        TradingProperties.DhanProperties broker = properties.broker();
        TradingProperties.InstrumentProperties instruments = properties.instruments();
        BrokerProfile.DhanConfig dhanConfig = new BrokerProfile.DhanConfig(
                broker.clientId(),
                broker.accessToken(),
                broker.environment(),
                broker.restBaseUrl(),
                broker.authMode(),
                DhanConfigPaths.resolve(broker.pinFile()),
                DhanConfigPaths.resolve(broker.totpSecretFile()),
                DhanConfigPaths.resolve(broker.tokenStateFile()),
                broker.refreshBufferMinutes(),
                instruments != null && instruments.autoDownload(),
                instruments != null ? instruments.cacheDirectory() : null
        );
        BrokerProfile profile = new BrokerProfile(BrokerProfile.BrokerType.DHAN, dhanConfig, null, null);
        return BrokerComposition.create(profile, idempotencyCache);
    }

    @Bean
    BrokerTransportCapabilities dhanTransportCapabilities() {
        return BrokerTransportCapabilities.dhanLive();
    }
}
