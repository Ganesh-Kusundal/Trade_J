package com.tradej.broker.api;

import com.tradej.broker.api.port.ConditionalAlertProvider;
import com.tradej.broker.api.port.FuturesProvider;
import com.tradej.broker.api.port.MarginProvider;
import com.tradej.broker.api.port.MarketDataProvider;
import com.tradej.broker.api.port.OptionsProvider;
import com.tradej.broker.api.port.OrderCommand;
import com.tradej.broker.api.port.OrderQuery;
import com.tradej.broker.api.port.PortfolioProvider;
import com.tradej.broker.api.port.WebSocketMultiplexer;
import com.tradej.broker.dhan.DhanBrokerConnection;
import com.tradej.broker.dhan.adapter.DhanInstrumentResolver;
import com.tradej.broker.dhan.client.DhanClientHolder;
import com.tradej.broker.icici.IciciBrokerConnection;
import com.tradej.broker.icici.instrument.BreezeInstrumentResolver;
import com.tradej.broker.upstox.UpstoxBrokerConnection;
import com.tradej.broker.upstox.adapter.UpstoxDataServicesProvider;
import com.tradej.broker.upstox.adapter.UpstoxProfileProvider;
import com.tradej.broker.upstox.instrument.UpstoxInstrumentLoader;
import com.tradej.broker.upstox.instrument.UpstoxInstrumentResolver;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;

/**
 * Parameterized cross-broker plugin contract test.
 *
 * <p>Verifies that every {@link IBrokerConnection} implementation in the
 * platform conforms to the same structural contract:
 * <ul>
 *   <li>{@code connect()} / {@code disconnect()} do not throw</li>
 *   <li>{@code getCapability()} returns correct types</li>
 *   <li>{@code marketData()} and {@code orders()} return non-null when connected</li>
 * </ul>
 *
 * <p>For exhaustive contract testing (all ports, capability stability, interval
 * validation) see the per-broker {@code *BrokerConnectionContractTest} classes
 * in each broker module.
 */
@Tag("unit")
class BrokerPluginContractTest {

    private IBrokerConnection createDhanConnection() {
        return new DhanBrokerConnection(
                mock(DhanClientHolder.class),
                mock(DhanInstrumentResolver.class),
                mock(MarketDataProvider.class),
                mock(FuturesProvider.class),
                mock(OptionsProvider.class),
                mock(OrderCommand.class),
                mock(OrderQuery.class),
                mock(com.tradej.broker.api.port.SliceOrderCommand.class),
                mock(com.tradej.broker.api.port.BracketOrderProvider.class),
                mock(com.tradej.broker.api.port.CoverOrderProvider.class),
                mock(com.tradej.broker.api.port.GttOrderProvider.class),
                mock(PortfolioProvider.class),
                mock(MarginProvider.class),
                mock(com.tradej.broker.api.port.SessionRiskProvider.class),
                mock(ConditionalAlertProvider.class),
                mock(WebSocketMultiplexer.class)
        );
    }

    private IBrokerConnection createUpstoxConnection() {
        return new UpstoxBrokerConnection(
                mock(MarketDataProvider.class),
                mock(OrderCommand.class),
                mock(OrderQuery.class),
                mock(PortfolioProvider.class),
                mock(MarginProvider.class),
                mock(UpstoxInstrumentResolver.class),
                mock(WebSocketMultiplexer.class),
                mock(FuturesProvider.class),
                mock(OptionsProvider.class),
                mock(com.tradej.broker.api.port.NewsProvider.class),
                mock(ConditionalAlertProvider.class),
                mock(com.tradej.broker.api.port.SliceOrderCommand.class),
                mock(UpstoxDataServicesProvider.class),
                mock(UpstoxProfileProvider.class),
                mock(UpstoxInstrumentLoader.class)
        );
    }

    private IBrokerConnection createIciciConnection() {
        return new IciciBrokerConnection(
                mock(MarketDataProvider.class),
                mock(FuturesProvider.class),
                mock(OptionsProvider.class),
                mock(OrderCommand.class),
                mock(OrderQuery.class),
                mock(PortfolioProvider.class),
                mock(MarginProvider.class),
                mock(BreezeInstrumentResolver.class),
                mock(WebSocketMultiplexer.class)
        );
    }

    private IBrokerConnection createConnection(String brokerType) {
        return switch (brokerType) {
            case "dhan" -> createDhanConnection();
            case "upstox" -> createUpstoxConnection();
            case "icici" -> createIciciConnection();
            default -> throw new IllegalArgumentException("Unknown broker: " + brokerType);
        };
    }

    // ── Parameterized tests across all broker types ───────────────

    @ParameterizedTest(name = "{0}: connect() does not throw")
    @ValueSource(strings = {"dhan", "upstox", "icici"})
    void connectDoesNotThrow(String brokerType) {
        IBrokerConnection connection = createConnection(brokerType);
        assertDoesNotThrow(connection::connect);
    }

    @ParameterizedTest(name = "{0}: disconnect() does not throw")
    @ValueSource(strings = {"dhan", "upstox", "icici"})
    void disconnectDoesNotThrow(String brokerType) {
        IBrokerConnection connection = createConnection(brokerType);
        assertDoesNotThrow(connection::disconnect);
    }

    @ParameterizedTest(name = "{0}: getCapability(MarketDataProvider) is present")
    @ValueSource(strings = {"dhan", "upstox", "icici"})
    void getCapabilityMarketDataProviderReturnsPresent(String brokerType) {
        IBrokerConnection connection = createConnection(brokerType);
        assertThat(connection.getCapability(MarketDataProvider.class)).isPresent();
    }

    @ParameterizedTest(name = "{0}: getCapability(OptionsProvider) is present")
    @ValueSource(strings = {"dhan", "upstox", "icici"})
    void getCapabilityOptionsProviderReturnsPresent(String brokerType) {
        IBrokerConnection connection = createConnection(brokerType);
        assertThat(connection.getCapability(OptionsProvider.class)).isPresent();
    }

    @ParameterizedTest(name = "{0}: getCapability(FuturesProvider) is present")
    @ValueSource(strings = {"dhan", "upstox", "icici"})
    void getCapabilityFuturesProviderReturnsPresent(String brokerType) {
        IBrokerConnection connection = createConnection(brokerType);
        assertThat(connection.getCapability(FuturesProvider.class)).isPresent();
    }

    @ParameterizedTest(name = "{0}: getCapability(MarginProvider) is present")
    @ValueSource(strings = {"dhan", "upstox", "icici"})
    void getCapabilityMarginProviderReturnsPresent(String brokerType) {
        IBrokerConnection connection = createConnection(brokerType);
        assertThat(connection.getCapability(MarginProvider.class)).isPresent();
    }

    @ParameterizedTest(name = "{0}: marketData() returns non-null")
    @ValueSource(strings = {"dhan", "upstox", "icici"})
    void marketDataReturnsNonNull(String brokerType) {
        IBrokerConnection connection = createConnection(brokerType);
        assertThat(connection.marketData()).isNotNull();
    }

    @ParameterizedTest(name = "{0}: orders() returns non-null")
    @ValueSource(strings = {"dhan", "upstox", "icici"})
    void ordersReturnsNonNull(String brokerType) {
        IBrokerConnection connection = createConnection(brokerType);
        assertThat(connection.orders()).isNotNull();
    }

    @ParameterizedTest(name = "{0}: getCapability returns empty for unknown class")
    @ValueSource(strings = {"dhan", "upstox", "icici"})
    void getCapabilityReturnsEmptyForUnknownClass(String brokerType) {
        IBrokerConnection connection = createConnection(brokerType);
        assertThat(connection.getCapability(String.class)).isEmpty();
    }

    @ParameterizedTest(name = "{0}: getCapability never returns null")
    @ValueSource(strings = {"dhan", "upstox", "icici"})
    void getCapabilityNeverReturnsNull(String brokerType) {
        IBrokerConnection connection = createConnection(brokerType);
        assertThat(connection.getCapability(OptionsProvider.class)).isNotNull();
        assertThat(connection.getCapability(FuturesProvider.class)).isNotNull();
        assertThat(connection.getCapability(MarginProvider.class)).isNotNull();
        assertThat(connection.getCapability(ConditionalAlertProvider.class)).isNotNull();
        assertThat(connection.getCapability(String.class)).isNotNull();
    }

    // ── Cross-broker uniformity assertions ────────────────────────

    @Test
    void allBrokerAdaptersExposeSameRequiredCapabilityTypes() {
        String[] brokerTypes = {"dhan", "upstox", "icici"};
        Class<?>[] requiredCapabilities = {
                MarketDataProvider.class,
                OptionsProvider.class,
                FuturesProvider.class,
                MarginProvider.class,
                OrderCommand.class,
                OrderQuery.class,
                PortfolioProvider.class,
                WebSocketMultiplexer.class
        };

        for (String brokerType : brokerTypes) {
            IBrokerConnection connection = createConnection(brokerType);
            for (Class<?> capability : requiredCapabilities) {
                assertThat(connection.getCapability(capability))
                        .as("%s must advertise %s", brokerType, capability.getSimpleName())
                        .isPresent();
            }
        }
    }

    @Test
    void allBrokerAdaptersReturnConsistentCapabilityInstances() {
        String[] brokerTypes = {"dhan", "upstox", "icici"};

        for (String brokerType : brokerTypes) {
            IBrokerConnection connection = createConnection(brokerType);
            var first = connection.getCapability(OptionsProvider.class);
            var second = connection.getCapability(OptionsProvider.class);
            assertThat(first).isPresent();
            assertThat(second).isPresent();
            assertThat(first.get())
                    .as("%s: getCapability must return stable instance", brokerType)
                    .isSameAs(second.get());
        }
    }
}
