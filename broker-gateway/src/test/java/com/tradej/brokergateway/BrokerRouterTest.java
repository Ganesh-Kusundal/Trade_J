package com.tradej.brokergateway;

import com.tradej.broker.api.IBrokerConnection;
import com.tradej.broker.api.port.InstrumentResolver;
import com.tradej.brokergateway.result.BrokerSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BrokerRouterTest {

    @Test
    void activeReturnsDefaultBroker() {
        IBrokerConnection conn = mock(IBrokerConnection.class);
        InstrumentResolver resolver = mock(InstrumentResolver.class);
        when(conn.instruments()).thenReturn(resolver);

        BrokerGateway gateway = BrokerGateway.of(BrokerSource.DHAN, conn);
        BrokerRouter router = new BrokerRouter(gateway);

        assertEquals(BrokerSource.DHAN, router.activeSource());
        assertNotNull(router.active());
    }

    @Test
    void setActiveThrowsForUnavailableBroker() {
        IBrokerConnection dhanConn = mock(IBrokerConnection.class);
        InstrumentResolver resolver = mock(InstrumentResolver.class);
        when(dhanConn.instruments()).thenReturn(resolver);

        BrokerGateway gateway = BrokerGateway.of(BrokerSource.DHAN, dhanConn);
        BrokerRouter router = new BrokerRouter(gateway);

        assertEquals(BrokerSource.DHAN, router.activeSource());
        assertThrows(IllegalArgumentException.class, () -> router.setActive(BrokerSource.UPSTOX));
    }

    @Test
    void gatewayReturnsUnderlyingGateway() {
        IBrokerConnection conn = mock(IBrokerConnection.class);
        InstrumentResolver resolver = mock(InstrumentResolver.class);
        when(conn.instruments()).thenReturn(resolver);

        BrokerGateway gateway = BrokerGateway.of(BrokerSource.DHAN, conn);
        BrokerRouter router = new BrokerRouter(gateway);

        assertSame(gateway, router.gateway());
    }
}
