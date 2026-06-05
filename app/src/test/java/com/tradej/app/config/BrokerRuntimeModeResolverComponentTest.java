package com.tradej.app.config;

import com.tradej.broker.dhan.config.DhanApiEnvironment;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Tag("component")
class BrokerRuntimeModeResolverComponentTest {

    @Test
    void resolvesGatewayProfile() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("trade.broker-type", "gateway");
        BrokerRuntimeModeResolver resolver = new BrokerRuntimeModeResolver(environment, gatewayProperties());
        assertEquals(BrokerRuntimeMode.BROKER_GATEWAY, resolver.resolve());
    }

    @Test
    void resolvesDhanSandbox() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("trade.broker-type", "dhan");
        TradingProperties properties = propertiesWithBroker(new TradingProperties.DhanProperties(
                        "id",
                        "token",
                        DhanApiEnvironment.SANDBOX,
                        null,
                        false,
                        3,
                        10,
                        true,
                        true,
                        com.tradej.broker.dhan.config.DhanAuthMode.STATIC,
                        "config/dhan-pin.txt",
                        "config/dhan-totp-secret.txt",
                        "runtime/dhan-token-state.json",
                        10L,
                        5L
                ));
        BrokerRuntimeModeResolver resolver = new BrokerRuntimeModeResolver(environment, properties);
        assertEquals(BrokerRuntimeMode.DHAN_SANDBOX, resolver.resolve());
    }

    private static TradingProperties gatewayProperties() {
        return propertiesWithBroker(null);
    }

    private static TradingProperties propertiesWithBroker(TradingProperties.DhanProperties broker) {
        return new TradingProperties(
                broker, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null
        );
    }
}
