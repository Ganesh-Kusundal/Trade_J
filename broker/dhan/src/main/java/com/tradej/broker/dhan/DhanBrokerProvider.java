package com.tradej.broker.dhan;

import com.tradej.broker.api.IBrokerConnection;
import com.tradej.broker.api.port.IdempotencyCachePort;
import com.tradej.broker.api.spi.BrokerDescriptor;
import com.tradej.broker.api.spi.BrokerProvider;
import com.tradej.broker.api.spi.BrokerSource;
import com.tradej.broker.api.spi.CapabilityMetadata;
import com.tradej.broker.dhan.config.DhanApiEnvironment;
import com.tradej.broker.dhan.config.DhanAuthMode;
import com.tradej.broker.dhan.config.DhanConnectionSettings;

import com.tradej.core.domain.model.Order;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dhan broker provider — implements BrokerProvider SPI for ServiceLoader discovery.
 * 
 * <p>This class bridges the generic SPI configuration to Dhan-specific connection creation.
 * It lives in the broker-dhan module to maintain broker module independence.
 */
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
    public IBrokerConnection create(Map<String, Object> configuration) {
        // Extract Dhan configuration from generic map
        String clientId = (String) configuration.getOrDefault("clientId", System.getenv("DHAN_CLIENT_ID"));
        String accessToken = (String) configuration.getOrDefault("accessToken", System.getenv("DHAN_ACCESS_TOKEN"));
        String environment = (String) configuration.getOrDefault("environment", "SANDBOX");
        String restBaseUrl = (String) configuration.getOrDefault("restBaseUrl", "https://api.dhan.in");
        String authMode = (String) configuration.getOrDefault("authMode", "STATIC");
        String pinFile = (String) configuration.get("pinFile");
        String totpSecretFile = (String) configuration.get("totpSecretFile");
        String tokenStateFile = (String) configuration.getOrDefault("tokenStateFile", "runtime/dhan-token-state.json");
        int refreshBufferMinutes = ((Number) configuration.getOrDefault("refreshBufferMinutes", 5)).intValue();

        if (clientId == null || accessToken == null) {
            throw new IllegalArgumentException("Dhan clientId and accessToken are required in configuration");
        }

        DhanConnectionSettings settings = new DhanConnectionSettings(
                clientId,
                accessToken,
                DhanApiEnvironment.valueOf(environment.toUpperCase()),
                restBaseUrl,
                false, // enableLogging
                3,     // maxRetries
                5,     // retryDelaySeconds
                true,  // enableTokenRefresh
                true,  // enableCircuitBreaker
                DhanAuthMode.valueOf(authMode.toUpperCase()),
                pinFile != null ? Path.of(pinFile) : null,
                totpSecretFile != null ? Path.of(totpSecretFile) : null,
                tokenStateFile != null ? Path.of(tokenStateFile) : null,
                refreshBufferMinutes,
                null,  // customHttpClient
                false  // sandboxMode
        );
        return DhanBrokerConnection.create(settings, new InMemoryIdempotencyCache());
    }

    static final class InMemoryIdempotencyCache implements IdempotencyCachePort {
        private final ConcurrentHashMap<String, Order> cache = new ConcurrentHashMap<>();

        @Override
        public Optional<Order> get(String clientOrderId) {
            return Optional.ofNullable(cache.get(clientOrderId));
        }

        @Override
        public void put(String clientOrderId, Order order) {
            cache.put(clientOrderId, order);
        }

        @Override
        public void remove(String clientOrderId) {
            cache.remove(clientOrderId);
        }
    }
}
