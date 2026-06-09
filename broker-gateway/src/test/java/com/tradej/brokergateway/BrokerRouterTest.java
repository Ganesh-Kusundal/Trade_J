package com.tradej.brokergateway;

import com.tradej.broker.api.IBrokerConnection;
import com.tradej.broker.api.exception.UnsupportedIntervalException;
import com.tradej.broker.api.model.HistoricalDataCapabilities;
import com.tradej.broker.api.port.InstrumentResolver;
import com.tradej.broker.api.port.MarketDataProvider;
import com.tradej.brokergateway.result.BrokerSource;
import com.tradej.core.domain.model.Candle;
import com.tradej.core.domain.model.CandleHistoryRequest;
import com.tradej.core.domain.model.InstrumentKey;
import com.tradej.core.domain.value.ExchangeSegment;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

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

    @Test
    void selectBrokersForInterval_findsMatchingBroker() {
        MarketDataProvider dhanProvider = mock(MarketDataProvider.class);
        when(dhanProvider.capabilities()).thenReturn(HistoricalDataCapabilities.dhanDefaults());

        IBrokerConnection dhanConn = mock(IBrokerConnection.class);
        when(dhanConn.marketData()).thenReturn(dhanProvider);
        when(dhanConn.instruments()).thenReturn(mock(InstrumentResolver.class));

        BrokerGateway gateway = BrokerGateway.of(BrokerSource.DHAN, dhanConn);
        BrokerRouter router = new BrokerRouter(gateway);

        List<BrokerSource> candidates = router.selectBrokersForInterval("5m");
        assertEquals(1, candidates.size());
        assertEquals(BrokerSource.DHAN, candidates.getFirst());
    }

    @Test
    void selectBrokersForInterval_returnsEmptyForUnsupported() {
        MarketDataProvider dhanProvider = mock(MarketDataProvider.class);
        when(dhanProvider.capabilities()).thenReturn(HistoricalDataCapabilities.dhanDefaults());

        IBrokerConnection dhanConn = mock(IBrokerConnection.class);
        when(dhanConn.marketData()).thenReturn(dhanProvider);
        when(dhanConn.instruments()).thenReturn(mock(InstrumentResolver.class));

        BrokerGateway gateway = BrokerGateway.of(BrokerSource.DHAN, dhanConn);
        BrokerRouter router = new BrokerRouter(gateway);

        List<BrokerSource> candidates = router.selectBrokersForInterval("1s");
        assertTrue(candidates.isEmpty(), "Dhan does not support 1s intervals");
    }

    @Test
    void supportsInterval_returnsTrueForSupported() {
        MarketDataProvider provider = mock(MarketDataProvider.class);
        when(provider.capabilities()).thenReturn(HistoricalDataCapabilities.dhanDefaults());

        IBrokerConnection conn = mock(IBrokerConnection.class);
        when(conn.marketData()).thenReturn(provider);
        when(conn.instruments()).thenReturn(mock(InstrumentResolver.class));

        BrokerGateway gateway = BrokerGateway.of(BrokerSource.DHAN, conn);
        BrokerRouter router = new BrokerRouter(gateway);

        assertTrue(router.supportsInterval("1d"));
        assertTrue(router.supportsInterval("5m"));
        assertFalse(router.supportsInterval("1s"));
    }

    @Test
    void historical_autoRoutesToSupportingBroker() {
        MarketDataProvider dhanProvider = mock(MarketDataProvider.class);
        when(dhanProvider.capabilities()).thenReturn(HistoricalDataCapabilities.dhanDefaults());
        when(dhanProvider.getCandles(any(CandleHistoryRequest.class)))
                .thenReturn(List.of(new Candle("SBIN", "1d", 1000L, 2000L, 100L, 110L, 90L, 105L, 5000L, true)));

        IBrokerConnection dhanConn = mock(IBrokerConnection.class);
        when(dhanConn.marketData()).thenReturn(dhanProvider);
        when(dhanConn.instruments()).thenReturn(mock(InstrumentResolver.class));

        BrokerGateway gateway = BrokerGateway.of(BrokerSource.DHAN, dhanConn);
        BrokerRouter router = new BrokerRouter(gateway);

        var result = router.historical("SBIN", ExchangeSegment.NSE_EQ, "1d",
                LocalDate.of(2025, 1, 1), LocalDate.of(2025, 6, 1));

        assertNotNull(result);
        assertEquals(BrokerSource.DHAN, result.source());
        assertFalse(result.data().isEmpty());
    }

    @Test
    void historical_throwsForUnsupportedInterval() {
        MarketDataProvider dhanProvider = mock(MarketDataProvider.class);
        when(dhanProvider.capabilities()).thenReturn(HistoricalDataCapabilities.dhanDefaults());

        IBrokerConnection dhanConn = mock(IBrokerConnection.class);
        when(dhanConn.marketData()).thenReturn(dhanProvider);
        when(dhanConn.instruments()).thenReturn(mock(InstrumentResolver.class));

        BrokerGateway gateway = BrokerGateway.of(BrokerSource.DHAN, dhanConn);
        BrokerRouter router = new BrokerRouter(gateway);

        assertThrows(UnsupportedIntervalException.class, () ->
                router.historical("SBIN", ExchangeSegment.NSE_EQ, "1s",
                        LocalDate.of(2025, 1, 1), LocalDate.of(2025, 6, 1)));
    }
}
