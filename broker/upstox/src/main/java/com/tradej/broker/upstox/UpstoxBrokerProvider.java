package com.tradej.broker.upstox;

import com.tradej.broker.api.IBrokerConnection;
import com.tradej.broker.api.spi.BrokerDescriptor;
import com.tradej.broker.api.spi.BrokerProvider;
import com.tradej.broker.api.spi.BrokerSource;
import com.tradej.broker.api.spi.CapabilityMetadata;
import com.tradej.broker.upstox.config.UpstoxBrokerConnectionFactory;
import com.tradej.broker.upstox.config.UpstoxConnectionSettings;

import java.util.List;
import java.util.Map;

/**
 * Upstox broker provider — implements BrokerProvider SPI for ServiceLoader discovery.
 * 
 * <p>This class bridges the generic SPI configuration to Upstox-specific connection creation.
 * It lives in the broker-upstox module to maintain broker module independence.
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
    public IBrokerConnection create(Map<String, Object> configuration) {
        String apiKey = (String) configuration.getOrDefault("apiKey", System.getenv("UPSTOX_API_KEY"));
        String apiSecret = (String) configuration.getOrDefault("apiSecret", System.getenv("UPSTOX_API_SECRET"));
        String accessToken = (String) configuration.get("accessToken");
        String environment = (String) configuration.getOrDefault("environment", "SANDBOX");
        String redirectUri = (String) configuration.getOrDefault("redirectUri", "http://localhost");
        String tokenStateFile = (String) configuration.getOrDefault("tokenStateFile", "runtime/upstox-token-state.json");

        if (apiKey == null || apiSecret == null) {
            throw new IllegalArgumentException("Upstox apiKey and apiSecret are required in configuration");
        }

        UpstoxConnectionSettings settings = new UpstoxConnectionSettings(
                apiKey,
                apiSecret,
                redirectUri,
                accessToken,
                null, // refreshToken - will be obtained from token state
                null, // analyticsToken
                "false", // extendedToken - String type
                false, // analyticsOnly
                "SANDBOX".equals(environment), // isSandbox
                8080, // redirectServerPort
                300000L, // refreshBufferMs (5 minutes)
                60000L // tokenExpiryBufferMs (1 minute)
        );
        return UpstoxBrokerConnectionFactory.create(settings);
    }
}
