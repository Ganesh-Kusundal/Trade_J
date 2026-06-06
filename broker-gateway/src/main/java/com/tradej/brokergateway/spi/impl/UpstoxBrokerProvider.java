package com.tradej.brokergateway.spi.impl;

import com.tradej.broker.api.IBrokerConnection;
import com.tradej.brokergateway.result.BrokerSource;
import com.tradej.brokergateway.spi.BrokerDescriptor;
import com.tradej.brokergateway.spi.BrokerProvider;
import com.tradej.brokergateway.spi.CapabilityMetadata;
import com.tradej.composition.BrokerComposition;
import com.tradej.composition.config.BrokerProfile;

import java.util.List;
import java.util.Map;

/**
 * Upstox broker provider — bridges {@link BrokerComposition} to the {@link BrokerProvider} SPI.
 */
public final class UpstoxBrokerProvider implements BrokerProvider {

    @Override
    public BrokerSource source() {
        return BrokerSource.UPSTOX;
    }

    @Override
    public String displayName() {
        return "Upstox";
    }

    @Override
    public BrokerDescriptor descriptor() {
        return new BrokerDescriptor(
                BrokerSource.UPSTOX,
                "Upstox",
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
                        Map.entry("BracketOrderProvider", false),
                        Map.entry("GttOrderProvider", true),
                        Map.entry("SliceOrderCommand", true),
                        Map.entry("SessionRiskProvider", false),
                        Map.entry("ConditionalAlertProvider", true),
                        Map.entry("NewsProvider", true)
                ),
                Map.of("environment", "LIVE/SANDBOX", "authModes", "PKCE+refresh", "newsEndpoints", "3"),
                List.of("NSE_EQ", "BSE_EQ", "NSE_FNO", "BSE_FNO", "MCX_COMM", "IDX_I"),
                "Orders:10rps/500rpm  Data:50rps/500rpm  OptionChain:1rps",
                Map.ofEntries(
                        Map.entry("MarketDataProvider", new CapabilityMetadata("LTP, quotes, OHLC, depth, historical candles", "market", "1.0")),
                        Map.entry("OptionsProvider", new CapabilityMetadata("Option chain, expiries, greeks, strike selection", "market", "1.0")),
                        Map.entry("OrderCommand", new CapabilityMetadata("Place, modify, cancel orders; multi-leg orders", "orders", "1.0")),
                        Map.entry("OrderQuery", new CapabilityMetadata("Order book, trade book, order history", "orders", "1.0")),
                        Map.entry("PortfolioProvider", new CapabilityMetadata("Holdings, positions, MTF holdings", "portfolio", "1.0")),
                        Map.entry("MarginProvider", new CapabilityMetadata("Margin calculator, order margin, available margin", "risk", "1.0")),
                        Map.entry("InstrumentResolver", new CapabilityMetadata("Instrument master, symbol search, token mapping", "services", "1.0")),
                        Map.entry("WebSocketMultiplexer", new CapabilityMetadata("Real-time streaming quotes, depth, order updates", "streaming", "1.0")),
                        Map.entry("FuturesProvider", new CapabilityMetadata("Futures quotes, chain, expiry data", "market", "1.0")),
                        Map.entry("BracketOrderProvider", new CapabilityMetadata("Not supported", "orders", "1.0")),
                        Map.entry("GttOrderProvider", new CapabilityMetadata("Good-till-triggered orders, price alerts", "orders", "1.0")),
                        Map.entry("SliceOrderCommand", new CapabilityMetadata("Slice large orders into smaller batches", "orders", "1.0")),
                        Map.entry("SessionRiskProvider", new CapabilityMetadata("Not supported", "risk", "1.0")),
                        Map.entry("ConditionalAlertProvider", new CapabilityMetadata("Price alerts, conditional triggers", "services", "1.0")),
                        Map.entry("NewsProvider", new CapabilityMetadata("Market news feed, announcements", "services", "1.0"))
                )
        );
    }

    @Override
    public IBrokerConnection connect(BrokerProfile profile) {
        if (profile.upstox() == null) {
            throw new IllegalArgumentException("Upstox configuration is required");
        }
        BrokerProfile upstoxProfile = new BrokerProfile(BrokerProfile.BrokerType.UPSTOX, null, profile.upstox(), null);
        return BrokerComposition.create(upstoxProfile).brokerConnection();
    }
}
