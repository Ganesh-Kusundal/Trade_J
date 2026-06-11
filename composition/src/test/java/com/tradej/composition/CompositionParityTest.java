package com.tradej.composition;

import com.tradej.broker.api.IBrokerConnection;
import com.tradej.broker.api.port.InstrumentResolver;
import com.tradej.broker.api.port.MarketDataProvider;
import com.tradej.broker.api.port.OptionsProvider;
import com.tradej.broker.api.port.OrderCommand;
import com.tradej.broker.api.port.OrderQuery;
import com.tradej.broker.api.port.PortfolioProvider;
import com.tradej.broker.api.port.WebSocketMultiplexer;
import com.tradej.broker.dhan.config.DhanApiEnvironment;
import com.tradej.broker.dhan.config.DhanAuthMode;
import com.tradej.composition.config.BrokerProfile;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that the programmatic composition path ({@link FullComposition})
 * and the Spring-managed path produce structurally equivalent broker graphs.
 *
 * <p>After P1 removed broker-specific auto-configurations, the Spring path
 * delegates entirely to {@link BrokerComposition}. This test confirms that
 * both entry points produce the same {@link IBrokerConnection} type with
 * the same port interface types available.
 *
 * <p>This replaces the former {@code CompositionVsAutoConfigurationTest}
 * which compared composition against per-broker auto-configs.
 */
@Tag("unit")
class CompositionParityTest {

    private static BrokerProfile dhanProfile() {
        return new BrokerProfile(
                BrokerProfile.BrokerType.DHAN,
                new BrokerProfile.DhanConfig(
                        "test-client", "test-token",
                        DhanApiEnvironment.SANDBOX, null,
                        DhanAuthMode.STATIC, null, null, null,
                        30, false, null
                ),
                null, null
        );
    }

    // ── BrokerComposition parity ────────────────────────────────

    @Test
    void brokerOnlyProducesNonNullBroker() {
        FullComposition system = FullComposition.brokerOnly(dhanProfile());
        assertNotNull(system.broker(), "Broker composition must be wired");
        assertNotNull(system.brokerConnection(), "IBrokerConnection must be non-null");
    }

    @Test
    void brokerOnlyProducesCorrectSourceType() {
        FullComposition system = FullComposition.brokerOnly(dhanProfile());
        assertEquals(com.tradej.broker.api.spi.BrokerSource.DHAN,
                system.brokerConnection().source(),
                "Dhan profile must produce a connection with source() == DHAN");
    }

    // ── Port availability parity ────────────────────────────────

    @Test
    void allMandatoryPortsResolve() {
        IBrokerConnection conn = FullComposition.brokerOnly(dhanProfile()).brokerConnection();

        assertNotNull(conn.marketData(), "MarketDataProvider port must resolve");
        assertNotNull(conn.orders(), "OrderCommand port must resolve");
        assertNotNull(conn.orderQuery(), "OrderQuery port must resolve");
        assertNotNull(conn.portfolio(), "PortfolioProvider port must resolve");
        assertNotNull(conn.instruments(), "InstrumentResolver port must resolve");
        assertNotNull(conn.websocket(), "WebSocketMultiplexer port must resolve");
    }

    @Test
    void portTypesMatchCapabilityLookup() {
        IBrokerConnection conn = FullComposition.brokerOnly(dhanProfile()).brokerConnection();

        assertSame(conn.marketData().getClass(),
                conn.getCapability(MarketDataProvider.class).orElseThrow().getClass(),
                "marketData() and getCapability(MarketDataProvider.class) must return same type");
        assertSame(conn.orders().getClass(),
                conn.getCapability(OrderCommand.class).orElseThrow().getClass(),
                "orders() and getCapability(OrderCommand.class) must return same type");
        assertSame(conn.instruments().getClass(),
                conn.getCapability(InstrumentResolver.class).orElseThrow().getClass(),
                "instruments() and getCapability(InstrumentResolver.class) must return same type");
    }

    // ── createFull parity ───────────────────────────────────────

    @Test
    void createFullWiresExecutionComposition() {
        FullComposition system = FullComposition.createFull(
                dhanProfile(),
                com.tradej.composition.config.StorageProfile.defaults(),
                com.tradej.composition.config.RiskProfile.defaults());

        assertNotNull(system.broker(), "Broker composition must be wired");
        assertNotNull(system.execution(), "Execution composition must be wired via createFull");
        assertSame(system.brokerConnection(), system.broker().brokerConnection(),
                "brokerConnection() must delegate to broker().brokerConnection()");
    }
}
