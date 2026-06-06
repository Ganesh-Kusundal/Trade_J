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
 * ICICI broker provider — bridges {@link BrokerComposition} to the {@link BrokerProvider} SPI.
 */
public final class IciciBrokerProvider implements BrokerProvider {

    @Override
    public BrokerSource source() {
        return BrokerSource.ICICI;
    }

    @Override
    public String displayName() {
        return "ICICI Direct";
    }

    @Override
    public BrokerDescriptor descriptor() {
        return new BrokerDescriptor(
                BrokerSource.ICICI,
                "ICICI Direct",
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
                        Map.entry("GttOrderProvider", false),
                        Map.entry("SliceOrderCommand", false),
                        Map.entry("SessionRiskProvider", false),
                        Map.entry("ConditionalAlertProvider", false),
                        Map.entry("NewsProvider", false)
                ),
                Map.of("environment", "LIVE", "authModes", "BROWSER_AUTOMATED/STATIC/TOTP_6DIGIT/TOTP_EXTERNAL",
                        "notes", "No MARKET orders, no kill switch, no square-off batch"),
                List.of("NSE_EQ", "BSE_EQ", "NSE_FNO", "BSE_FNO", "MCX_COMM"),
                "Breeze API rate limits apply",
                Map.ofEntries(
                        Map.entry("MarketDataProvider", new CapabilityMetadata("LTP, quotes, OHLC, depth, historical data", "market", "1.0")),
                        Map.entry("OptionsProvider", new CapabilityMetadata("Option chain, expiries, basic greeks", "market", "1.0")),
                        Map.entry("OrderCommand", new CapabilityMetadata("Place, modify, cancel orders (LIMIT only, no MARKET)", "orders", "1.0")),
                        Map.entry("OrderQuery", new CapabilityMetadata("Order book, trade book, order status", "orders", "1.0")),
                        Map.entry("PortfolioProvider", new CapabilityMetadata("Holdings, positions, MTF holdings, pledges", "portfolio", "1.0")),
                        Map.entry("MarginProvider", new CapabilityMetadata("Margin calculator, available margin", "risk", "1.0")),
                        Map.entry("InstrumentResolver", new CapabilityMetadata("Instrument master, symbol lookup, token mapping", "services", "1.0")),
                        Map.entry("WebSocketMultiplexer", new CapabilityMetadata("Real-time streaming quotes and order updates", "streaming", "1.0")),
                        Map.entry("FuturesProvider", new CapabilityMetadata("Futures quotes, expiry chain", "market", "1.0")),
                        Map.entry("BracketOrderProvider", new CapabilityMetadata("Not supported", "orders", "1.0")),
                        Map.entry("GttOrderProvider", new CapabilityMetadata("Not supported", "orders", "1.0")),
                        Map.entry("SliceOrderCommand", new CapabilityMetadata("Not supported", "orders", "1.0")),
                        Map.entry("SessionRiskProvider", new CapabilityMetadata("Not supported", "risk", "1.0")),
                        Map.entry("ConditionalAlertProvider", new CapabilityMetadata("Not supported", "services", "1.0")),
                        Map.entry("NewsProvider", new CapabilityMetadata("Not supported", "services", "1.0"))
                )
        );
    }

    @Override
    public IBrokerConnection connect(BrokerProfile profile) {
        if (profile.icici() == null) {
            throw new IllegalArgumentException("ICICI configuration is required");
        }
        BrokerProfile iciciProfile = new BrokerProfile(BrokerProfile.BrokerType.ICICI, null, null, profile.icici());
        return BrokerComposition.create(iciciProfile).brokerConnection();
    }
}
