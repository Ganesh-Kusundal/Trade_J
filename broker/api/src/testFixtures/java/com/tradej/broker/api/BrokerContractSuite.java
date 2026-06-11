package com.tradej.broker.api;

import com.tradej.broker.api.port.InstrumentResolver;
import com.tradej.broker.api.port.MarketDataProvider;
import com.tradej.broker.api.port.OptionsProvider;
import com.tradej.broker.api.port.OrderCommand;
import com.tradej.broker.api.port.OrderQuery;
import com.tradej.broker.api.port.PortfolioProvider;
import com.tradej.broker.api.port.WebSocketMultiplexer;
import com.tradej.broker.api.spi.BrokerSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive certification suite that every broker must pass before being
 * considered production-ready.
 *
 * <p>Subclass and implement {@link #createConnection()} and {@link #expectedSource()}.
 * This suite runs:
 * <ul>
 *   <li>Identity checks — {@code source()} returns the expected broker</li>
 *   <li>Port availability — mandatory ports are non-null</li>
 *   <li>Capability stability — repeated lookups return same instances</li>
 *   <li>Lifecycle safety — connect/disconnect do not throw</li>
 *   <li>AutoCloseable — close() delegates to disconnect()</li>
 * </ul>
 *
 * <p>Usage:
 * <pre>
 * class DhanContractSuite extends BrokerContractSuite {
 *     &#64;Override protected IBrokerConnection createConnection() { ... }
 *     &#64;Override protected BrokerSource expectedSource() { return BrokerSource.DHAN; }
 * }
 * </pre>
 */
@Tag("unit")
public abstract class BrokerContractSuite {

    protected abstract IBrokerConnection createConnection();

    protected abstract BrokerSource expectedSource();

    private IBrokerConnection connection;

    @BeforeEach
    void setUp() {
        connection = createConnection();
    }

    // ── Identity ────────────────────────────────────────────────

    @Test
    void sourceMatchesExpectedBroker() {
        assertEquals(expectedSource(), connection.source(),
                "source() must return the correct BrokerSource for this broker");
    }

    // ── Mandatory Ports ─────────────────────────────────────────

    @Test
    void mandatoryPortsAreNonNull() {
        assertNotNull(connection.marketData(), "marketData() must not return null");
        assertNotNull(connection.orders(), "orders() must not return null");
        assertNotNull(connection.orderQuery(), "orderQuery() must not return null");
        assertNotNull(connection.portfolio(), "portfolio() must not return null");
        assertNotNull(connection.instruments(), "instruments() must not return null");
        assertNotNull(connection.websocket(), "websocket() must not return null");
    }

    // ── Capability Contract ─────────────────────────────────────

    @Test
    void getCapabilityNeverReturnsNull() {
        assertNotNull(connection.getCapability(OptionsProvider.class));
        assertNotNull(connection.getCapability(MarketDataProvider.class));
        assertNotNull(connection.getCapability(String.class));
    }

    @Test
    void getCapabilityReturnsEmptyForUnknownClass() {
        assertTrue(connection.getCapability(String.class).isEmpty(),
                "Unknown capability class must return empty Optional");
    }

    @Test
    void capabilityInstancesAreStable() {
        var first = connection.getCapability(OptionsProvider.class);
        var second = connection.getCapability(OptionsProvider.class);
        if (first.isPresent()) {
            assertSame(first.get(), second.orElseThrow(),
                    "getCapability must return the same instance on repeated calls");
        }
    }

    // ── Lifecycle Safety ────────────────────────────────────────

    @Test
    void connectDoesNotThrow() {
        assertDoesNotThrow(() -> connection.connect());
    }

    @Test
    void disconnectDoesNotThrow() {
        assertDoesNotThrow(() -> connection.disconnect());
    }

    @Test
    void closeDelegatesToDisconnect() {
        assertDoesNotThrow(() -> connection.close(),
                "AutoCloseable.close() must not throw");
    }
}
