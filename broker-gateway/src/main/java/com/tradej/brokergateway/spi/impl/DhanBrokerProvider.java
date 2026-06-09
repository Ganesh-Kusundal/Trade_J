package com.tradej.brokergateway.spi.impl;

import com.tradej.broker.api.IBrokerConnection;
import com.tradej.broker.api.port.IdempotencyCachePort;
import com.tradej.broker.dhan.DhanBrokerConnection;
import com.tradej.broker.dhan.config.DhanConnectionSettings;
import com.tradej.brokergateway.result.BrokerSource;
import com.tradej.brokergateway.spi.BrokerDescriptor;
import com.tradej.brokergateway.spi.BrokerProvider;
import com.tradej.brokergateway.spi.CapabilityMetadata;
import com.tradej.composition.config.BrokerProfile;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class DhanBrokerProvider implements BrokerProvider {

    @Override
    public BrokerSource source() {
        return BrokerSource.DHAN;
    }

    @Override
    public String displayName() {
        return "DhanHQ";
    }

    @Override
    public BrokerDescriptor descriptor() {
        return new BrokerDescriptor(
                BrokerSource.DHAN,
                "DhanHQ",
                Map.ofEntries(
                        Map.entry("MarketDataProvider", true),
                        Map.entry("OptionsProvider", true),
                        Map.entry("OrderCommand", true),
                        Map.entry("OrderQuery", true),
                        Map.entry("PortfolioProvider", true),
                        Map.entry("MarginProvider", true),
                        Map.entry("InstrumentResolver", true),
                        Map.entry("WebSocketMultiplexer", true),
                        Map.entry("FuturesProvider", true),
                        Map.entry("BracketOrderProvider", true),
                        Map.entry("GttOrderProvider", true),
                        Map.entry("SliceOrderCommand", true),
                        Map.entry("SessionRiskProvider", true),
                        Map.entry("ConditionalAlertProvider", true),
                        Map.entry("NewsProvider", false)
                ),
                Map.of("environment", "LIVE/SANDBOX", "authModes", "STATIC/TOTP/WEB_RENEWABLE"),
                List.of("NSE_EQ", "BSE_EQ", "NSE_FNO", "BSE_FNO", "MCX_COMM", "NSE_CURRENCY", "BSE_CURRENCY", "IDX_I"),
                "Orders:10rps  Data:5rps  Quotes:1rps  OptionChain:1rps",
                Map.ofEntries(
                        Map.entry("MarketDataProvider", new CapabilityMetadata("LTP, quotes, OHLC, depth, historical candles", "market", "1.0")),
                        Map.entry("OptionsProvider", new CapabilityMetadata("Option chain, expiries, greeks, strike selection, rolling options", "market", "1.0")),
                        Map.entry("OrderCommand", new CapabilityMetadata("Place, modify, cancel orders; kill switch; preview", "orders", "1.0")),
                        Map.entry("OrderQuery", new CapabilityMetadata("Order book, trade book, order status", "orders", "1.0")),
                        Map.entry("PortfolioProvider", new CapabilityMetadata("Holdings, positions, MTF, pledges", "portfolio", "1.0")),
                        Map.entry("MarginProvider", new CapabilityMetadata("Margin calculator, order margin, available margin", "risk", "1.0")),
                        Map.entry("InstrumentResolver", new CapabilityMetadata("Instrument master, symbol lookup, expiry calendar", "services", "1.0")),
                        Map.entry("WebSocketMultiplexer", new CapabilityMetadata("Real-time streaming quotes, depth, index, order updates", "streaming", "1.0")),
                        Map.entry("FuturesProvider", new CapabilityMetadata("Futures quotes, chain, rollover analytics", "market", "1.0")),
                        Map.entry("BracketOrderProvider", new CapabilityMetadata("Bracket orders with target and stop-loss legs", "orders", "1.0")),
                        Map.entry("GttOrderProvider", new CapabilityMetadata("Good-till-triggered orders, OCO, alerts", "orders", "1.0")),
                        Map.entry("SliceOrderCommand", new CapabilityMetadata("Slice large orders into smaller chunks", "orders", "1.0")),
                        Map.entry("SessionRiskProvider", new CapabilityMetadata("Session-level risk limits, kill switch, square-off", "risk", "1.0")),
                        Map.entry("ConditionalAlertProvider", new CapabilityMetadata("Price alerts, conditional triggers, notifications", "services", "1.0")),
                        Map.entry("NewsProvider", new CapabilityMetadata("Not supported", "services", "1.0"))
                )
        );
    }

    @Override
    public IBrokerConnection connect(BrokerProfile profile) {
        BrokerProfile.DhanConfig dhan = profile.dhan();
        if (dhan == null) {
            throw new IllegalArgumentException("Dhan configuration is required");
        }
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
        return DhanBrokerConnection.create(settings, new NoOpIdempotencyCache());
    }

    private static final class NoOpIdempotencyCache implements IdempotencyCachePort {
        @Override public Optional<com.tradej.core.domain.model.Order> get(String clientOrderId) { return Optional.empty(); }
        @Override public void put(String clientOrderId, com.tradej.core.domain.model.Order order) {}
        @Override public void remove(String clientOrderId) {}
    }
}
