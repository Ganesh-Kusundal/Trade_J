package com.tradej.brokergateway.spi.impl;

import com.tradej.broker.api.IBrokerConnection;
import com.tradej.broker.api.port.BracketOrderProvider;
import com.tradej.broker.api.port.ConditionalAlertProvider;
import com.tradej.broker.api.port.FuturesProvider;
import com.tradej.broker.api.port.GttOrderProvider;
import com.tradej.broker.api.port.NewsProvider;
import com.tradej.broker.api.port.SessionRiskProvider;
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
class DhanExtrasTest {

    @Mock IBrokerConnection connection;
    @Mock SessionRiskProvider sessionRisk;
    @Mock BracketOrderProvider bracketOrders;
    @Mock GttOrderProvider gttOrders;
    @Mock SliceOrderCommand sliceOrders;
    @Mock ConditionalAlertProvider alerts;
    @Mock FuturesProvider futures;

    @Test
    void sourceReturnsDhan() {
        DhanExtras extras = new DhanExtras(connection);
        assertEquals(BrokerSource.DHAN, extras.source());
    }

    @Test
    void newsReturnsEmpty() {
        DhanExtras extras = new DhanExtras(connection);
        assertTrue(extras.news().isEmpty());
    }

    @Test
    void methodsReturnsCorrectSet() {
        DhanExtras extras = new DhanExtras(connection);
        Set<String> expected = Set.of(
                "sessionRisk", "bracketOrders", "gttOrders",
                "sliceOrders", "alerts", "futures",
                "optionGreeks", "getOptionGreeks");
        assertEquals(expected, extras.methods());
        assertEquals(8, extras.methods().size());
    }

    @Test
    void invokeSessionRiskReturnsProviderWhenAvailable() {
        when(connection.getCapability(SessionRiskProvider.class)).thenReturn(Optional.of(sessionRisk));
        DhanExtras extras = new DhanExtras(connection);
        Optional<Object> result = extras.invoke("sessionRisk");
        assertTrue(result.isPresent());
        assertEquals(sessionRisk, result.get());
    }

    @Test
    void invokeSessionRiskReturnsEmptyWhenUnavailable() {
        when(connection.getCapability(SessionRiskProvider.class)).thenReturn(Optional.empty());
        DhanExtras extras = new DhanExtras(connection);
        assertTrue(extras.invoke("sessionRisk").isEmpty());
    }

    @Test
    void invokeBracketOrdersReturnsProviderWhenAvailable() {
        when(connection.getCapability(BracketOrderProvider.class)).thenReturn(Optional.of(bracketOrders));
        DhanExtras extras = new DhanExtras(connection);
        Optional<Object> result = extras.invoke("bracketOrders");
        assertTrue(result.isPresent());
        assertEquals(bracketOrders, result.get());
    }

    @Test
    void invokeBracketOrdersReturnsEmptyWhenUnavailable() {
        when(connection.getCapability(BracketOrderProvider.class)).thenReturn(Optional.empty());
        DhanExtras extras = new DhanExtras(connection);
        assertTrue(extras.invoke("bracketOrders").isEmpty());
    }

    @Test
    void invokeGttOrdersReturnsProviderWhenAvailable() {
        when(connection.getCapability(GttOrderProvider.class)).thenReturn(Optional.of(gttOrders));
        DhanExtras extras = new DhanExtras(connection);
        Optional<Object> result = extras.invoke("gttOrders");
        assertTrue(result.isPresent());
        assertEquals(gttOrders, result.get());
    }

    @Test
    void invokeGttOrdersReturnsEmptyWhenUnavailable() {
        when(connection.getCapability(GttOrderProvider.class)).thenReturn(Optional.empty());
        DhanExtras extras = new DhanExtras(connection);
        assertTrue(extras.invoke("gttOrders").isEmpty());
    }

    @Test
    void invokeSliceOrdersReturnsProviderWhenAvailable() {
        when(connection.getCapability(SliceOrderCommand.class)).thenReturn(Optional.of(sliceOrders));
        DhanExtras extras = new DhanExtras(connection);
        Optional<Object> result = extras.invoke("sliceOrders");
        assertTrue(result.isPresent());
        assertEquals(sliceOrders, result.get());
    }

    @Test
    void invokeSliceOrdersReturnsEmptyWhenUnavailable() {
        when(connection.getCapability(SliceOrderCommand.class)).thenReturn(Optional.empty());
        DhanExtras extras = new DhanExtras(connection);
        assertTrue(extras.invoke("sliceOrders").isEmpty());
    }

    @Test
    void invokeAlertsReturnsProviderWhenAvailable() {
        when(connection.getCapability(ConditionalAlertProvider.class)).thenReturn(Optional.of(alerts));
        DhanExtras extras = new DhanExtras(connection);
        Optional<Object> result = extras.invoke("alerts");
        assertTrue(result.isPresent());
        assertEquals(alerts, result.get());
    }

    @Test
    void invokeAlertsReturnsEmptyWhenUnavailable() {
        when(connection.getCapability(ConditionalAlertProvider.class)).thenReturn(Optional.empty());
        DhanExtras extras = new DhanExtras(connection);
        assertTrue(extras.invoke("alerts").isEmpty());
    }

    @Test
    void invokeFuturesReturnsProviderWhenAvailable() {
        when(connection.getCapability(FuturesProvider.class)).thenReturn(Optional.of(futures));
        DhanExtras extras = new DhanExtras(connection);
        Optional<Object> result = extras.invoke("futures");
        assertTrue(result.isPresent());
        assertEquals(futures, result.get());
    }

    @Test
    void invokeFuturesReturnsEmptyWhenUnavailable() {
        when(connection.getCapability(FuturesProvider.class)).thenReturn(Optional.empty());
        DhanExtras extras = new DhanExtras(connection);
        assertTrue(extras.invoke("futures").isEmpty());
    }

    @Test
    void invokeUnknownMethodThrowsUnsupportedOperationException() {
        DhanExtras extras = new DhanExtras(connection);
        assertThrows(UnsupportedOperationException.class, () -> extras.invoke("unknown"));
    }
}
