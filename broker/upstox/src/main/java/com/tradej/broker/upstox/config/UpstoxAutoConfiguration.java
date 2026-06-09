package com.tradej.broker.upstox.config;

import com.tradej.broker.api.IBrokerConnection;
import com.tradej.broker.core.startup.BrokerLifecycleManager;
import com.tradej.broker.upstox.UpstoxBrokerConnection;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Spring Boot auto-configuration for the Upstox broker adapter.
 *
 * <p>Activates when {@code trade.broker-type=upstox} and
 * {@link UpstoxBrokerConnection} is on the classpath.
 */
@AutoConfiguration
@ConditionalOnClass(UpstoxBrokerConnection.class)
@ConditionalOnProperty(name = "trade.broker-type", havingValue = "upstox")
@EnableConfigurationProperties(UpstoxConnectionProperties.class)
public class UpstoxAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public UpstoxConnectionSettings upstoxConnectionSettings(UpstoxConnectionProperties properties) {
        return properties.toSettings();
    }

    @Bean
    @ConditionalOnMissingBean(IBrokerConnection.class)
    public UpstoxBrokerConnection upstoxBrokerConnection(UpstoxConnectionSettings settings) {
        return UpstoxBrokerConnectionFactory.create(settings);
    }

    @Bean
    @ConditionalOnMissingBean
    public BrokerLifecycleManager upstoxBrokerLifecycleManager() {
        return new BrokerLifecycleManager();
    }
}
