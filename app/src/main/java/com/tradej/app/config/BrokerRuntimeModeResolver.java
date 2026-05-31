package com.tradej.app.config;

import com.tradej.broker.dhan.config.DhanApiEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public final class BrokerRuntimeModeResolver {

    private final Environment environment;
    private final TradingProperties properties;

    public BrokerRuntimeModeResolver(Environment environment, TradingProperties properties) {
        this.environment = environment;
        this.properties = properties;
    }

    public BrokerRuntimeMode resolve() {
        String brokerType = environment.getProperty("trade.broker-type", "dhan");
        if ("upstox".equalsIgnoreCase(brokerType)) {
            TradingProperties.UpstoxProperties upstox = properties.upstox();
            if (upstox != null && upstox.analyticsOnly()) {
                return BrokerRuntimeMode.UPSTOX_ANALYTICS_REST;
            }
            return BrokerRuntimeMode.UPSTOX_TRADING_WS;
        }
        if (properties.broker() != null && properties.broker().environment() == DhanApiEnvironment.SANDBOX) {
            return BrokerRuntimeMode.DHAN_SANDBOX;
        }
        return BrokerRuntimeMode.DHAN_LIVE_WS;
    }
}
