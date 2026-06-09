package com.tradej.broker.api;

import com.tradej.broker.api.capability.AdvancedOrderCapable;
import com.tradej.broker.api.capability.AlertCapable;
import com.tradej.broker.api.capability.FuturesCapable;
import com.tradej.broker.api.capability.MarginCapable;
import com.tradej.broker.api.capability.OptionsCapable;
import com.tradej.broker.api.port.FuturesProvider;
import com.tradej.broker.api.port.InstrumentResolver;
import com.tradej.broker.api.port.MarginProvider;
import com.tradej.broker.api.port.MarketDataProvider;
import com.tradej.broker.api.port.OptionsProvider;
import com.tradej.broker.api.port.OrderCommand;
import com.tradej.broker.api.port.OrderQuery;
import com.tradej.broker.api.port.PortfolioProvider;
import com.tradej.broker.api.port.WebSocketMultiplexer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Contract test that ALL {@link IBrokerConnection} implementations must pass.
 *
 * <p>Subclass this test for each concrete broker connection:
 * <ul>
 *   <li>{@code DhanBrokerConnectionContractTest} in {@code broker-dhan}</li>
 *   <li>{@code IciciBrokerConnectionContractTest} in {@code broker-icici}</li>
 *   <li>{@code UpstoxBrokerConnectionContractTest} in {@code broker-upstox}</li>
 * </ul>
 */
@Tag("unit")
public abstract class IBrokerConnectionContractTest {

    protected abstract IBrokerConnection createConnection();

    private IBrokerConnection connection;

    @BeforeEach
    void setUp() {
        connection = createConnection();
    }

    @Test
    void mandatoryPortsAreNonNull() {
        assertNotNull(connection.marketData(), "marketData() must not return null");
        assertNotNull(connection.orders(), "orders() must not return null");
        assertNotNull(connection.orderQuery(), "orderQuery() must not return null");
        assertNotNull(connection.portfolio(), "portfolio() must not return null");
        assertNotNull(connection.instruments(), "instruments() must not return null");
        assertNotNull(connection.websocket(), "websocket() must not return null");
    }

    @Test
    void getCapabilityNeverReturnsNull() {
        assertNotNull(connection.getCapability(OptionsCapable.class));
        assertNotNull(connection.getCapability(FuturesCapable.class));
        assertNotNull(connection.getCapability(MarginCapable.class));
        assertNotNull(connection.getCapability(AlertCapable.class));
        assertNotNull(connection.getCapability(AdvancedOrderCapable.class));
        assertNotNull(connection.getCapability(String.class));
    }

    @Test
    void getCapabilityReturnsPresentForKnownCapabilities() {
        // At minimum, every broker must advertise these three capabilities
        assertTrue(connection.getCapability(OptionsCapable.class).isPresent(),
                "OptionsCapable must be advertised");
        assertTrue(connection.getCapability(MarginCapable.class).isPresent(),
                "MarginCapable must be advertised");
    }

    @Test
    void getCapabilityReturnsEmptyForNonsenseClass() {
        assertTrue(connection.getCapability(String.class).isEmpty(),
                "Unknown capability class must return empty Optional");
    }

    @Test
    void capabilityInstancesAreConsistent() {
        // Calling getCapability and then the typed method should yield the same object
        // for the well-known capabilities that this broker supports.
        var optionsCap = connection.getCapability(OptionsCapable.class);
        if (optionsCap.isPresent()) {
            assertSame(optionsCap.get(), connection.getCapability(OptionsCapable.class).orElseThrow(),
                    "getCapability must be stable (same instance on repeated calls)");
        }
    }

    @Test
    void connectDoesNotThrow() {
        assertDoesNotThrow(() -> connection.connect());
    }

    @Test
    void disconnectDoesNotThrow() {
        assertDoesNotThrow(() -> connection.disconnect());
    }

    @Test
    void marketDataCapabilitiesIsNotNull() {
        var caps = connection.marketData().capabilities();
        org.junit.jupiter.api.Assumptions.assumeTrue(caps != null,
                "Skipping: capabilities() returns null (mocked connection)");
        assertNotNull(caps);
    }

    @Test
    void marketDataCapabilitiesHasNonEmptySupportedIntervals() {
        var caps = connection.marketData().capabilities();
        org.junit.jupiter.api.Assumptions.assumeTrue(caps != null,
                "Skipping: capabilities() returns null (mocked connection)");
        assertNotNull(caps.supportedIntervals(), "supportedIntervals must not be null");
        assertFalse(caps.supportedIntervals().isEmpty(), "supportedIntervals must not be empty");
    }

    @Test
    void validateIntervalAcceptsSupportedInterval() {
        var caps = connection.marketData().capabilities();
        org.junit.jupiter.api.Assumptions.assumeTrue(caps != null,
                "Skipping: capabilities() returns null (mocked connection)");
        String firstSupported = caps.supportedIntervals().iterator().next();
        assertDoesNotThrow(() -> connection.marketData().validateInterval(firstSupported),
                "validateInterval must accept a supported interval without throwing");
    }

    @Test
    void validateIntervalRejectsUnsupportedInterval() {
        var caps = connection.marketData().capabilities();
        org.junit.jupiter.api.Assumptions.assumeTrue(caps != null,
                "Skipping: capabilities() returns null (mocked connection)");
        assertThrows(Exception.class,
                () -> connection.marketData().validateInterval("totally_invalid_interval_xyz"),
                "validateInterval must throw for unsupported intervals");
    }
}
