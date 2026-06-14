package com.tradej.composition;

import com.tradej.broker.api.IBrokerConnection;
import com.tradej.broker.api.port.InstrumentResolver;
import com.tradej.broker.api.port.MarketDataProvider;
import com.tradej.broker.api.port.OrderCommand;
import com.tradej.broker.dhan.config.DhanApiEnvironment;
import com.tradej.broker.dhan.config.DhanAuthMode;
import com.tradej.composition.config.BrokerProfile;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that {@link BrokerComposition#create(BrokerProfile)} produces a fully wired
 * broker graph for the SPI-discovered provider.
 */
@Tag("unit")
class BrokerCompositionTest {

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

    @Test
    void createProducesNonNullBroker() {
        BrokerComposition composition = BrokerComposition.create(dhanProfile());
        assertNotNull(composition, "BrokerComposition must be wired");
        assertNotNull(composition.brokerConnection(), "IBrokerConnection must be non-null");
        assertNotNull(composition.lifecycleManager(), "BrokerLifecycleManager must be non-null");
    }

    @Test
    void createProducesCorrectSourceType() {
        BrokerComposition composition = BrokerComposition.create(dhanProfile());
        assertEquals(com.tradej.broker.api.spi.BrokerSource.DHAN,
                composition.brokerConnection().source(),
                "Dhan profile must produce a connection with source() == DHAN");
    }

    @Test
    void allMandatoryPortsResolve() {
        IBrokerConnection conn = BrokerComposition.create(dhanProfile()).brokerConnection();

        assertNotNull(conn.marketData(), "MarketDataProvider port must resolve");
        assertNotNull(conn.orders(), "OrderCommand port must resolve");
        assertNotNull(conn.orderQuery(), "OrderQuery port must resolve");
        assertNotNull(conn.portfolio(), "PortfolioProvider port must resolve");
        assertNotNull(conn.instruments(), "InstrumentResolver port must resolve");
        assertNotNull(conn.websocket(), "WebSocketMultiplexer port must resolve");
    }

    @Test
    void portTypesMatchCapabilityLookup() {
        IBrokerConnection conn = BrokerComposition.create(dhanProfile()).brokerConnection();

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
}
