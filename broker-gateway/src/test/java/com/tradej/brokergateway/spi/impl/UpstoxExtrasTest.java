package com.tradej.brokergateway.spi.impl;

import com.tradej.broker.api.IBrokerConnection;
import com.tradej.broker.api.port.ConditionalAlertProvider;
import com.tradej.broker.api.port.FuturesProvider;
import com.tradej.broker.api.port.GttOrderProvider;
import com.tradej.broker.api.port.NewsProvider;
import com.tradej.broker.api.port.SliceOrderCommand;
import com.tradej.brokergateway.result.BrokerSource;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class UpstoxExtrasTest {

    @Mock IBrokerConnection connection;
    @Mock NewsProvider newsProvider;
    @Mock ConditionalAlertProvider alerts;
    @Mock GttOrderProvider gttOrders;
    @Mock SliceOrderCommand sliceOrders;
    @Mock FuturesProvider futures;

    @Test
    void sourceReturnsUpstox() {
        UpstoxExtras extras = new UpstoxExtras(connection);
        assertEquals(BrokerSource.UPSTOX, extras.source());
    }

    @Test
    void newsReturnsProviderWhenAvailable() {
        when(connection.getCapability(NewsProvider.class)).thenReturn(Optional.of(newsProvider));
        UpstoxExtras extras = new UpstoxExtras(connection);
        Optional<NewsProvider> result = extras.news();
        assertTrue(result.isPresent());
        assertEquals(newsProvider, result.get());
    }

    @Test
    void newsReturnsEmptyWhenUnavailable() {
        when(connection.getCapability(NewsProvider.class)).thenReturn(Optional.empty());
        UpstoxExtras extras = new UpstoxExtras(connection);
        assertTrue(extras.news().isEmpty());
    }

    @Test
    void methodsReturnsCorrectSet() {
        UpstoxExtras extras = new UpstoxExtras(connection);
        Set<String> expected = Set.of("news", "alerts", "gttOrders", "sliceOrders", "futures",
                "optionGreeks", "getOptionGreeks");
        assertEquals(expected, extras.methods());
        assertEquals(7, extras.methods().size());
    }

    @Test
    void invokeNewsReturnsProviderWhenAvailable() {
        when(connection.getCapability(NewsProvider.class)).thenReturn(Optional.of(newsProvider));
        UpstoxExtras extras = new UpstoxExtras(connection);
        Optional<Object> result = extras.invoke("news");
        assertTrue(result.isPresent());
        assertEquals(newsProvider, result.get());
    }

    @Test
    void invokeNewsReturnsEmptyWhenUnavailable() {
        when(connection.getCapability(NewsProvider.class)).thenReturn(Optional.empty());
        UpstoxExtras extras = new UpstoxExtras(connection);
        assertTrue(extras.invoke("news").isEmpty());
    }

    @Test
    void invokeAlertsReturnsProviderWhenAvailable() {
        when(connection.getCapability(ConditionalAlertProvider.class)).thenReturn(Optional.of(alerts));
        UpstoxExtras extras = new UpstoxExtras(connection);
        Optional<Object> result = extras.invoke("alerts");
        assertTrue(result.isPresent());
        assertEquals(alerts, result.get());
    }

    @Test
    void invokeAlertsReturnsEmptyWhenUnavailable() {
        when(connection.getCapability(ConditionalAlertProvider.class)).thenReturn(Optional.empty());
        UpstoxExtras extras = new UpstoxExtras(connection);
        assertTrue(extras.invoke("alerts").isEmpty());
    }

    @Test
    void invokeGttOrdersReturnsProviderWhenAvailable() {
        when(connection.getCapability(GttOrderProvider.class)).thenReturn(Optional.of(gttOrders));
        UpstoxExtras extras = new UpstoxExtras(connection);
        Optional<Object> result = extras.invoke("gttOrders");
        assertTrue(result.isPresent());
        assertEquals(gttOrders, result.get());
    }

    @Test
    void invokeGttOrdersReturnsEmptyWhenUnavailable() {
        when(connection.getCapability(GttOrderProvider.class)).thenReturn(Optional.empty());
        UpstoxExtras extras = new UpstoxExtras(connection);
        assertTrue(extras.invoke("gttOrders").isEmpty());
    }

    @Test
    void invokeSliceOrdersReturnsProviderWhenAvailable() {
        when(connection.getCapability(SliceOrderCommand.class)).thenReturn(Optional.of(sliceOrders));
        UpstoxExtras extras = new UpstoxExtras(connection);
        Optional<Object> result = extras.invoke("sliceOrders");
        assertTrue(result.isPresent());
        assertEquals(sliceOrders, result.get());
    }

    @Test
    void invokeSliceOrdersReturnsEmptyWhenUnavailable() {
        when(connection.getCapability(SliceOrderCommand.class)).thenReturn(Optional.empty());
        UpstoxExtras extras = new UpstoxExtras(connection);
        assertTrue(extras.invoke("sliceOrders").isEmpty());
    }

    @Test
    void invokeFuturesReturnsProviderWhenAvailable() {
        when(connection.getCapability(FuturesProvider.class)).thenReturn(Optional.of(futures));
        UpstoxExtras extras = new UpstoxExtras(connection);
        Optional<Object> result = extras.invoke("futures");
        assertTrue(result.isPresent());
        assertEquals(futures, result.get());
    }

    @Test
    void invokeFuturesReturnsEmptyWhenUnavailable() {
        when(connection.getCapability(FuturesProvider.class)).thenReturn(Optional.empty());
        UpstoxExtras extras = new UpstoxExtras(connection);
        assertTrue(extras.invoke("futures").isEmpty());
    }

    @Test
    void invokeUnknownMethodThrowsUnsupportedOperationException() {
        UpstoxExtras extras = new UpstoxExtras(connection);
        assertThrows(UnsupportedOperationException.class, () -> extras.invoke("unknown"));
    }
}
