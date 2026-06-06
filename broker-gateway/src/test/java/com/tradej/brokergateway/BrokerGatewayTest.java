package com.tradej.brokergateway;

import com.tradej.broker.api.IBrokerConnection;
import com.tradej.broker.api.port.InstrumentResolver;
import com.tradej.brokergateway.result.BrokerSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BrokerGatewayTest {

    @Test
    void createFromSingleBrokerComposition() {
        IBrokerConnection connection = mock(IBrokerConnection.class);
        InstrumentResolver resolver = mock(InstrumentResolver.class);
        when(connection.instruments()).thenReturn(resolver);

        BrokerGateway gateway = BrokerGateway.of(BrokerSource.DHAN, connection);

        assertNotNull(gateway);
        assertTrue(gateway.availableBrokers().contains(BrokerSource.DHAN));
        assertEquals(1, gateway.availableBrokers().size());
    }

    @Test
    void brokerByNameReturnsCorrectHandle() {
        IBrokerConnection connection = mock(IBrokerConnection.class);
        InstrumentResolver resolver = mock(InstrumentResolver.class);
        when(connection.instruments()).thenReturn(resolver);

        BrokerGateway gateway = BrokerGateway.of(BrokerSource.DHAN, connection);

        BrokerHandle handle = gateway.broker("dhan");
        assertNotNull(handle);
        assertEquals(BrokerSource.DHAN, handle.source());
    }

    @Test
    void brokerByInvalidNameThrows() {
        IBrokerConnection connection = mock(IBrokerConnection.class);
        InstrumentResolver resolver = mock(InstrumentResolver.class);
        when(connection.instruments()).thenReturn(resolver);

        BrokerGateway gateway = BrokerGateway.of(BrokerSource.DHAN, connection);

        assertThrows(IllegalArgumentException.class, () -> gateway.broker("upstox"));
    }

    @Test
    void hasBrokerReturnsTrueForAvailable() {
        IBrokerConnection connection = mock(IBrokerConnection.class);
        InstrumentResolver resolver = mock(InstrumentResolver.class);
        when(connection.instruments()).thenReturn(resolver);

        BrokerGateway gateway = BrokerGateway.of(BrokerSource.DHAN, connection);

        assertTrue(gateway.hasBroker("dhan"));
        assertFalse(gateway.hasBroker("upstox"));
        assertFalse(gateway.hasBroker("invalid"));
    }

    @Test
    void firstReturnsAvailableHandle() {
        IBrokerConnection connection = mock(IBrokerConnection.class);
        InstrumentResolver resolver = mock(InstrumentResolver.class);
        when(connection.instruments()).thenReturn(resolver);

        BrokerGateway gateway = BrokerGateway.of(BrokerSource.DHAN, connection);

        BrokerHandle first = gateway.first();
        assertNotNull(first);
        assertEquals(BrokerSource.DHAN, first.source());
    }
}
