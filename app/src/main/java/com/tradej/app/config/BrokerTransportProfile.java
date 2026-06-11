package com.tradej.app.config;

import com.tradej.broker.dhan.config.DhanApiEnvironment;
import org.springframework.core.env.Environment;

public record BrokerTransportProfile(
        boolean expectsWebSocket,
        boolean analyticsRest,
        boolean upstox,
        boolean icici,
        boolean gateway,
        boolean simulation
) {
    public BrokerTransportProfile(
            boolean expectsWebSocket,
            boolean analyticsRest,
            boolean upstox,
            boolean icici,
            boolean gateway
    ) {
        this(expectsWebSocket, analyticsRest, upstox, icici, gateway, false);
    }

    public boolean isUpstox() {
        return upstox;
    }

    public boolean isIcici() {
        return icici;
    }

    public boolean isSimulation() {
        return simulation;
    }

    public boolean isAnalyticsRest() {
        return analyticsRest;
    }

    public static BrokerTransportProfile resolve(Environment environment, TradingProperties properties) {
        String brokerType = environment.getProperty("trade.broker-type", "dhan");
        return switch (brokerType.toLowerCase()) {
            case "upstox" -> {
                TradingProperties.UpstoxProperties upstox = properties.upstox();
                if (upstox != null && upstox.analyticsOnly()) {
                    yield new BrokerTransportProfile(false, true, true, false, false, false);
                }
                yield new BrokerTransportProfile(true, false, true, false, false, false);
            }
            case "gateway" -> new BrokerTransportProfile(true, false, false, false, true, false);
            case "icici" -> new BrokerTransportProfile(true, false, false, true, false, false);
            case "simulation" -> new BrokerTransportProfile(false, false, false, false, false, true);
            default -> {
                if (properties.broker() != null
                        && properties.broker().environment() == DhanApiEnvironment.SANDBOX) {
                    yield new BrokerTransportProfile(false, false, false, false, false, false);
                }
                yield new BrokerTransportProfile(true, false, false, false, false, false);
            }
        };
    }
}
