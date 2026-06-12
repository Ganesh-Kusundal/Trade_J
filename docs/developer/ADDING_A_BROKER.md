# Adding a Broker to Trade-J

## What you can add

A broker adapter is a self-contained module that integrates a new exchange/broker. You add:

1. A new Gradle module under `broker/<name>/` (e.g. `broker/zerodha/`).
2. A `BrokerProvider` SPI implementation that returns a `BrokerDescriptor` and creates an `IBrokerConnection`.
3. An `IBrokerConnection` facade plus the capability adapters it delegates to (market data, orders, options, etc.).
4. A `META-INF/services` line that registers the provider.

- SPI module: `broker/api/`
- Key interface: `com.tradej.broker.api.spi.BrokerProvider` ([source](../../broker/api/src/main/java/com/tradej/broker/api/spi/BrokerProvider.java))
- Broker source enum: `com.tradej.broker.api.spi.BrokerSource` ([source](../../broker/api/src/main/java/com/tradej/broker/api/spi/BrokerSource.java))
- Connection facade: `com.tradej.broker.api.IBrokerConnection` ([source](../../broker/api/src/main/java/com/tradej/broker/api/IBrokerConnection.java))
- Capability ports: `com.tradej.broker.api.port.*` (MarketDataProvider, OrderCommand, OrderQuery, PortfolioProvider, OptionsProvider, MarginProvider, WebSocketMultiplexer, etc.)
- Working example: `DhanBrokerProvider` ([source](../../broker/dhan/src/main/java/com/tradej/broker/dhan/DhanBrokerProvider.java)) + `DhanBrokerConnection` ([source](../../broker/dhan/src/main/java/com/tradej/broker/dhan/DhanBrokerConnection.java))

## Time required

- 4-8 hours for a read-only broker (market data, option chain, instruments).
- 1-2 weeks for a full broker with order management, websocket, auth refresh, and risk controls.

## Prerequisites

- You know Java `ServiceLoader`. The broker module is discovered through `META-INF/services/com.tradej.broker.api.spi.BrokerProvider`.
- You understand the capability port interfaces under `broker/api/src/main/java/com/tradej/broker/api/port/`. Each port (e.g. `MarketDataProvider`, `OrderCommand`) is an interface the connection must implement. Unsupported capabilities are absent from the connection's capability map.
- You have API credentials or a sandbox account for the broker. Hardcode nothing; pull from config map and fall back to environment variables.
- The `BrokerSource` enum is a closed set. You must add a new value to it (single line edit) so the rest of the platform can reference your broker by name.

## Step 1: Add a new BrokerSource enum value

Edit [broker/api/src/main/java/com/tradej/broker/api/spi/BrokerSource.java](../../broker/api/src/main/java/com/tradej/broker/api/spi/BrokerSource.java):

```java
public enum BrokerSource {
    DHAN,
    UPSTOX,
    ICICI,
    SIMULATION,
    BINANCE,
    ZERODHA;  // <- new line
}
```

This is the only place where a broker's string identity is hardcoded.

## Step 2: Create the new broker module

Follow the layout of `broker/dhan/`:

```
broker/zerodha/
  build.gradle.kts
  src/main/java/com/tradej/broker/zerodha/
    ZerodhaBrokerProvider.java
    ZerodhaBrokerConnection.java
    config/
      ZerodhaConnectionSettings.java
    adapter/
      ZerodhaMarketDataProvider.java
      ZerodhaOrderCommandAdapter.java
      ...
  src/main/resources/META-INF/services/
    com.tradej.broker.api.spi.BrokerProvider
```

The `build.gradle.kts` should depend on `broker-api` and on whatever HTTP client / SDK your broker needs.

## Step 3: Implement BrokerProvider

Create `broker/zerodha/src/main/java/com/tradej/broker/zerodha/ZerodhaBrokerProvider.java`:

```java
package com.tradej.broker.zerodha;

import com.tradej.broker.api.IBrokerConnection;
import com.tradej.broker.api.spi.BrokerDescriptor;
import com.tradej.broker.api.spi.BrokerProvider;
import com.tradej.broker.api.spi.BrokerSource;
import com.tradej.broker.api.spi.CapabilityMetadata;
import com.tradej.broker.api.spi.CredentialField;
import com.tradej.broker.zerodha.config.ZerodhaConnectionSettings;

import java.util.List;
import java.util.Map;

public final class ZerodhaBrokerProvider implements BrokerProvider {

    @Override
    public BrokerSource source() {
        return BrokerSource.ZERODHA;
    }

    @Override
    public String displayName() {
        return "Zerodha Kite";
    }

    @Override
    public BrokerDescriptor descriptor() {
        return new BrokerDescriptor(
                BrokerSource.ZERODHA,
                "Zerodha Kite",
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
                        Map.entry("SliceOrderCommand", false),
                        Map.entry("SessionRiskProvider", false),
                        Map.entry("ConditionalAlertProvider", true),
                        Map.entry("NewsProvider", false)
                ),
                Map.of("environment", "LIVE/SANDBOX", "authModes", "MANUAL_TOKEN"),
                List.of("NSE_EQ", "BSE_EQ", "NSE_FNO", "BSE_FNO", "MCX_COMM", "NSE_CURRENCY", "IDX_I"),
                "Orders:3rps/200rpm  Data:1rps  Quote:1rps",
                Map.ofEntries(
                        Map.entry("MarketDataProvider", new CapabilityMetadata("LTP, quote, OHLC, depth", "market", "1.0")),
                        Map.entry("OptionsProvider", new CapabilityMetadata("Option chain, expiries, instrument lookup", "market", "1.0")),
                        Map.entry("OrderCommand", new CapabilityMetadata("Place, modify, cancel regular orders", "orders", "1.0")),
                        Map.entry("OrderQuery", new CapabilityMetadata("Order book, trade book, order history", "orders", "1.0")),
                        Map.entry("PortfolioProvider", new CapabilityMetadata("Holdings, positions", "portfolio", "1.0")),
                        Map.entry("MarginProvider", new CapabilityMetadata("Margins, available cash, collateral", "risk", "1.0")),
                        Map.entry("InstrumentResolver", new CapabilityMetadata("Instrument master, symbol search", "services", "1.0")),
                        Map.entry("WebSocketMultiplexer", new CapabilityMetadata("KiteTicker streaming quotes and order updates", "streaming", "1.0")),
                        Map.entry("FuturesProvider", new CapabilityMetadata("Futures LTP, OHLC, chain", "market", "1.0")),
                        Map.entry("GttOrderProvider", new CapabilityMetadata("GTT single-leg and OCO", "orders", "1.0")),
                        Map.entry("ConditionalAlertProvider", new CapabilityMetadata("Price alerts via GTT", "services", "1.0"))
                ),
                List.of(
                        new CredentialField("apiKey", "API Key", "text", "Kite API key"),
                        new CredentialField("accessToken", "Access Token", "password", "Daily access token from Kite login flow")
                )
        );
    }

    @Override
    public IBrokerConnection create(Map<String, Object> configuration) {
        String apiKey = (String) configuration.getOrDefault("apiKey", System.getenv("ZERODHA_API_KEY"));
        String accessToken = (String) configuration.getOrDefault("accessToken", System.getenv("ZERODHA_ACCESS_TOKEN"));
        String environment = (String) configuration.getOrDefault("environment", "SANDBOX");

        if (apiKey == null || accessToken == null) {
            throw new IllegalArgumentException("Zerodha apiKey and accessToken are required");
        }

        ZerodhaConnectionSettings settings = new ZerodhaConnectionSettings(
                apiKey, accessToken, "LIVE".equalsIgnoreCase(environment), 3, 1_000L
        );
        return ZerodhaBrokerConnection.create(settings);
    }

    @Override
    public String version() {
        return "1.0.0";
    }
}
```

`BrokerDescriptor` ([source](../../broker/api/src/main/java/com/tradej/broker/api/spi/BrokerDescriptor.java)) holds the static metadata the platform uses to render the broker in the UI, build credential forms, and decide which capability ports to instantiate.

## Step 4: Implement the connection facade

The connection is a thin facade over capability adapters. The `DhanBrokerConnection` is the model:

```java
package com.tradej.broker.zerodha;

import com.tradej.broker.api.IBrokerConnection;
import com.tradej.broker.api.port.MarketDataProvider;
import com.tradej.broker.api.port.OrderCommand;
import com.tradej.broker.api.spi.BrokerSource;
import com.tradej.broker.zerodha.adapter.ZerodhaMarketDataProvider;
import com.tradej.broker.zerodha.adapter.ZerodhaOrderCommandAdapter;
import com.tradej.broker.zerodha.config.ZerodhaConnectionSettings;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class ZerodhaBrokerConnection implements IBrokerConnection {

    private final Map<Class<?>, Object> capabilities = new HashMap<>();

    public ZerodhaBrokerConnection(ZerodhaConnectionSettings settings) {
        // Construct adapters using `settings` and any client you build.
        MarketDataProvider marketData = new ZerodhaMarketDataProvider(settings);
        OrderCommand orderCommand = new ZerodhaOrderCommandAdapter(settings);
        capabilities.put(MarketDataProvider.class, marketData);
        capabilities.put(OrderCommand.class, orderCommand);
    }

    public static ZerodhaBrokerConnection create(ZerodhaConnectionSettings settings) {
        return new ZerodhaBrokerConnection(settings);
    }

    @Override
    public BrokerSource source() {
        return BrokerSource.ZERODHA;
    }

    @Override
    public <T> Optional<T> getCapability(Class<T> capabilityClass) {
        Object impl = capabilities.get(capabilityClass);
        if (impl == null) {
            return Optional.empty();
        }
        return Optional.of(capabilityClass.cast(impl));
    }

    @Override
    public void connect() { /* WS handshake */ }

    @Override
    public void disconnect() { /* close WS */ }

    @Override
    public void loadInstrumentCatalog(java.nio.file.Path catalogPath) { /* load master */ }
}
```

Each capability port is its own class — see the full set of `MarketDataProvider`, `OrderCommand`, `OrderQuery`, `PortfolioProvider`, `OptionsProvider`, `MarginProvider`, `InstrumentResolver`, `WebSocketMultiplexer`, `BracketOrderProvider`, `GttOrderProvider`, `SliceOrderCommand`, `SessionRiskProvider`, `ConditionalAlertProvider`, `NewsProvider` under `broker/api/src/main/java/com/tradej/broker/api/port/`. Implement only the ones you listed as `true` in your descriptor.

## Step 5: Register the provider

Create `broker/zerodha/src/main/resources/META-INF/services/com.tradej.broker.api.spi.BrokerProvider` with one line:

```
com.tradej.broker.zerodha.ZerodhaBrokerProvider
```

The platform calls `ServiceLoader.load(BrokerProvider.class)` at startup, and your `create(...)` factory gets called when a user picks "Zerodha" from the broker dropdown.

## Step 6: Wire DI for connection construction (if needed)

The Dhan module demonstrates the dual pattern: a multi-adapter constructor for Spring and a legacy `create(settings, ...)` factory. For most new brokers:

- Provide a `create(settings)` static factory for tests and non-Spring callers.
- Provide a multi-adapter constructor for Spring and add Spring `@Bean` methods in a `ZerodhaConfiguration` class (parallel to `TradingConfiguration` for Dhan).

If your `create(Map<String, Object>)` only constructs the connection with its adapter graph and does not depend on other Spring beans, the ServiceLoader path is enough. If you need to inject shared HTTP clients, rate limiters, or token managers, register them as Spring beans and have `ZerodhaBrokerProvider.create` look them up via `ObjectFactory<HttpClient>` or accept a pre-built client through a Spring factory method.

## Step 7: Test it

Two layers:

**Unit test the provider and connection construction:**

```java
package com.tradej.broker.zerodha;

import com.tradej.broker.api.spi.BrokerProvider;
import com.tradej.broker.api.spi.BrokerSource;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.ServiceLoader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Tag("integration")
class ZerodhaBrokerProviderTest {

    @Test
    void providerIsDiscoveredViaServiceLoader() {
        BrokerProvider found = null;
        for (BrokerProvider p : ServiceLoader.load(BrokerProvider.class)) {
            if (p.source() == BrokerSource.ZERODHA) {
                found = p;
                break;
            }
        }
        assertNotNull(found, "ZerodhaBrokerProvider must be in META-INF/services");
        assertEquals("Zerodha Kite", found.displayName());
    }

    @Test
    void createRejectsMissingCredentials() {
        BrokerProvider found = null;
        for (BrokerProvider p : ServiceLoader.load(BrokerProvider.class)) {
            if (p.source() == BrokerSource.ZERODHA) {
                found = p;
                break;
            }
        }
        assertNotNull(found);
        assertThrows(IllegalArgumentException.class,
                () -> found.create(Map.of()));
    }
}
```

**Integration test against the broker's sandbox.** Do not mock the broker. The test should call `IBrokerConnection.marketData().getLtp(...)` against the sandbox endpoint and assert a non-zero, finite result. Mark the test `@Tag("integration")` so it is excluded from the unit suite. See the existing tests in `broker/dhan/src/test/java` for the pattern.

## Step 8: Use it in the UI

Once the broker module is built and the JAR is on the classpath, the platform picks it up automatically. The frontend calls `GET /api/v1/brokers` to list registered brokers, renders each `BrokerDescriptor.credentialFields` as input fields in the broker modal, and uses the provider's `source()` enum value as the broker identity.

## Common pitfalls

- **Forgetting to add a `BrokerSource` enum value** — without it, the platform has no string identity for your broker and the connection is unreachable.
- **Declaring capabilities you do not implement** — set `false` in the descriptor for every port you do not build. The connection's `getCapability` will return `Optional.empty()` for those, and downstream code that calls `connection.orders()` will throw `UnsupportedOperationException`.
- **Hardcoding credentials** — read from the config map first, fall back to env vars, never bake them into the class.
- **Timezone bugs** — all `Candle` and `Quote` timestamps are epoch ms. Convert at the broker boundary, not inside the adapter.
- **Prices in paisa vs rupees** — the platform uses paisa. The broker API may use rupees; divide by 100 on read, multiply on write.
- **Resource leaks** — implement `disconnect()` and `close()` to release the websocket, the HTTP client, and any executor services. Use try-with-resources in callers.
- **Rate limits** — wrap REST calls in a `MultiBucketRateLimiter` and surface 429 responses as `BrokerRateLimitException` so the calling `RetryExecutor` can back off correctly.

## See also

- Provider SPI: [BrokerProvider.java](../../broker/api/src/main/java/com/tradej/broker/api/spi/BrokerProvider.java)
- Connection facade: [IBrokerConnection.java](../../broker/api/src/main/java/com/tradej/broker/api/IBrokerConnection.java)
- Descriptor: [BrokerDescriptor.java](../../broker/api/src/main/java/com/tradej/broker/api/spi/BrokerDescriptor.java)
- Broker source enum: [BrokerSource.java](../../broker/api/src/main/java/com/tradej/broker/api/spi/BrokerSource.java)
- Capability ports: [broker/api/src/main/java/com/tradej/broker/api/port/](../../broker/api/src/main/java/com/tradej/broker/api/port/)
- Dhan example provider: [DhanBrokerProvider.java](../../broker/dhan/src/main/java/com/tradej/broker/dhan/DhanBrokerProvider.java)
- Dhan example connection: [DhanBrokerConnection.java](../../broker/dhan/src/main/java/com/tradej/broker/dhan/DhanBrokerConnection.java)
- Upstox example: [UpsthaBrokerProvider.java](../../broker/upstox/src/main/java/com/tradej/broker/upstox/UpstoxBrokerProvider.java)
- Dhan META-INF/services: [com.tradej.broker.api.spi.BrokerProvider](../../broker/dhan/src/main/resources/META-INF/services/com.tradej.broker.api.spi.BrokerProvider)
- Upstox META-INF/services: [com.tradej.broker.api.spi.BrokerProvider](../../broker/upstox/src/main/resources/META-INF/services/com.tradej.broker.api.spi.BrokerProvider)
