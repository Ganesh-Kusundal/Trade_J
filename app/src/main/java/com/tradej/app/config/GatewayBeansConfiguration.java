package com.tradej.app.config;

import com.tradej.broker.api.IBrokerConnection;
import com.tradej.brokergateway.BrokerGateway;
import com.tradej.brokergateway.BrokerRouter;
import com.tradej.brokergateway.MarketGateway;
import com.tradej.brokergateway.result.BrokerSource;
import com.tradej.composition.BrokerComposition;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Registers broker-gateway beans: BrokerGateway, BrokerRouter, MarketGateway.
 *
 * <p>These beans provide the canonical access layer for all market operations.
 * Consumers should prefer {@link MarketGateway} over direct {@link IBrokerConnection}
 * or {@link com.tradej.broker.api.port.MarketDataProvider} injection.
 */
@Configuration
public class GatewayBeansConfiguration {

    @Bean
    BrokerGateway brokerGateway(
            ObjectProvider<BrokerComposition> compositionProvider,
            ObjectProvider<IBrokerConnection> brokerConnection
    ) {
        BrokerComposition composition = compositionProvider.getIfAvailable();
        if (composition != null) {
            BrokerSource source = toSource(composition.profile().brokerType());
            return BrokerGateway.of(source, composition.brokerConnection());
        }
        Map<BrokerSource, IBrokerConnection> connections = new LinkedHashMap<>();
        brokerConnection.orderedStream().forEach(conn -> {
            BrokerSource source = identifyBroker(conn);
            connections.putIfAbsent(source, conn);
        });
        if (connections.isEmpty()) {
            throw new IllegalStateException("No IBrokerConnection beans available for gateway");
        }
        return BrokerGateway.fromConnections(connections);
    }

    @Bean
    BrokerRouter brokerRouter(BrokerGateway gateway) {
        return new BrokerRouter(gateway);
    }

    @Bean
    MarketGateway marketGateway(BrokerRouter router) {
        return MarketGateway.create(router);
    }

    @Bean
    BrokerSource activeBrokerSource(ObjectProvider<BrokerComposition> compositionProvider) {
        BrokerComposition composition = compositionProvider.getIfAvailable();
        if (composition != null) {
            return toSource(composition.profile().brokerType());
        }
        return BrokerSource.DHAN;
    }

    private static BrokerSource toSource(com.tradej.composition.config.BrokerProfile.BrokerType type) {
        return switch (type) {
            case DHAN, GATEWAY -> BrokerSource.DHAN;
            case UPSTOX -> BrokerSource.UPSTOX;
            case ICICI -> BrokerSource.ICICI;
        };
    }

    private static BrokerSource identifyBroker(IBrokerConnection conn) {
        String className = conn.getClass().getSimpleName().toLowerCase();
        if (className.contains("dhan")) return BrokerSource.DHAN;
        if (className.contains("upstox")) return BrokerSource.UPSTOX;
        if (className.contains("icici") || className.contains("breeze")) return BrokerSource.ICICI;
        if (className.contains("loadbalanced") || className.contains("gateway")) {
            return BrokerSource.DHAN;
        }
        return BrokerSource.SIMULATION;
    }
}
