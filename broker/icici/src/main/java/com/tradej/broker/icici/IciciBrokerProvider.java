package com.tradej.broker.icici;

import com.tradej.broker.api.IBrokerConnection;
import com.tradej.broker.api.spi.BrokerDescriptor;
import com.tradej.broker.api.spi.BrokerProvider;
import com.tradej.broker.api.spi.BrokerSource;
import com.tradej.broker.api.spi.CapabilityMetadata;
import com.tradej.broker.icici.config.IciciConnectionFactory;

import java.util.List;
import java.util.Map;

/**
 * ICICI broker provider — implements BrokerProvider SPI for ServiceLoader discovery.
 * 
 * <p>This class bridges the generic SPI configuration to ICICI-specific connection creation.
 * It lives in the broker-icici module to maintain broker module independence.
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
    public IBrokerConnection create(Map<String, Object> configuration) {
        // Delegate to IciciConnectionFactory in broker-icici module
        return IciciConnectionFactory.create(configuration);
    }
}
