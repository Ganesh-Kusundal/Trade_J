package com.tradej.broker.dhan;

import com.tradej.broker.api.capability.AdvancedOrderCapable;
import com.tradej.broker.api.capability.AlertCapable;
import com.tradej.broker.api.capability.FuturesCapable;
import com.tradej.broker.api.capability.MarginCapable;
import com.tradej.broker.api.capability.OptionsCapable;
import com.tradej.broker.api.port.BracketOrderProvider;
import com.tradej.broker.api.port.ConditionalAlertProvider;
import com.tradej.broker.api.port.FuturesProvider;
import com.tradej.broker.api.port.GttOrderProvider;
import com.tradej.broker.api.port.MarginProvider;
import com.tradej.broker.api.port.MarketDataProvider;
import com.tradej.broker.api.port.OptionsProvider;
import com.tradej.broker.api.port.OrderCommand;
import com.tradej.broker.api.port.OrderQuery;
import com.tradej.broker.api.port.PortfolioProvider;
import com.tradej.broker.api.port.SessionRiskProvider;
import com.tradej.broker.api.port.SliceOrderCommand;
import com.tradej.broker.api.port.WebSocketMultiplexer;
import com.tradej.broker.dhan.adapter.DhanInstrumentResolver;
import com.tradej.broker.dhan.client.DhanClientHolder;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

@Tag("unit")
class DhanBrokerConnectionTest {

    @Test
    void constructorRejectsNullClientHolder() {
        assertThrows(NullPointerException.class, () -> new DhanBrokerConnection(
                null,
                mock(DhanInstrumentResolver.class),
                mock(MarketDataProvider.class),
                mock(FuturesProvider.class),
                mock(OptionsProvider.class),
                mock(OrderCommand.class),
                mock(OrderQuery.class),
                mock(SliceOrderCommand.class),
                mock(BracketOrderProvider.class),
                mock(GttOrderProvider.class),
                mock(PortfolioProvider.class),
                mock(MarginProvider.class),
                mock(SessionRiskProvider.class),
                mock(ConditionalAlertProvider.class),
                mock(WebSocketMultiplexer.class)
        ));
    }

    @Test
    void getCapabilityReturnsAllCapableInterfaces() {
        DhanBrokerConnection conn = createFullyMockedConnection();

        assertTrue(conn.getCapability(OptionsCapable.class).isPresent());
        assertTrue(conn.getCapability(FuturesCapable.class).isPresent());
        assertTrue(conn.getCapability(MarginCapable.class).isPresent());
        assertTrue(conn.getCapability(AlertCapable.class).isPresent());
        assertTrue(conn.getCapability(AdvancedOrderCapable.class).isPresent());
    }

    @Test
    void getCapabilityReturnsEmptyForUnknownCapability() {
        DhanBrokerConnection conn = createFullyMockedConnection();
        assertTrue(conn.getCapability(String.class).isEmpty());
    }

    @Test
    void capabilityMethodsReturnInjectedProviders() {
        FuturesProvider futures = mock(FuturesProvider.class);
        OptionsProvider options = mock(OptionsProvider.class);
        MarginProvider margin = mock(MarginProvider.class);
        SessionRiskProvider sessionRisk = mock(SessionRiskProvider.class);
        ConditionalAlertProvider alerts = mock(ConditionalAlertProvider.class);
        BracketOrderProvider bracket = mock(BracketOrderProvider.class);
        GttOrderProvider gtt = mock(GttOrderProvider.class);
        SliceOrderCommand slice = mock(SliceOrderCommand.class);

        DhanBrokerConnection conn = new DhanBrokerConnection(
                mock(DhanClientHolder.class),
                mock(DhanInstrumentResolver.class),
                mock(MarketDataProvider.class),
                futures,
                options,
                mock(OrderCommand.class),
                mock(OrderQuery.class),
                slice,
                bracket,
                gtt,
                mock(PortfolioProvider.class),
                margin,
                sessionRisk,
                alerts,
                mock(WebSocketMultiplexer.class)
        );

        assertSame(options, conn.options());
        assertSame(futures, conn.futures());
        assertSame(margin, conn.margin());
        assertSame(sessionRisk, conn.sessionRisk());
        assertSame(alerts, conn.alerts());
        assertSame(bracket, conn.bracketOrders());
        assertSame(gtt, conn.gttOrders());
        assertSame(slice, conn.sliceOrders());
    }

    @Test
    void getCapabilityReturnsInstanceForProviderInterfaces() {
        MarketDataProvider marketData = mock(MarketDataProvider.class);
        OrderCommand orderCommand = mock(OrderCommand.class);
        OrderQuery orderQuery = mock(OrderQuery.class);
        PortfolioProvider portfolio = mock(PortfolioProvider.class);
        MarginProvider margin = mock(MarginProvider.class);
        SessionRiskProvider sessionRisk = mock(SessionRiskProvider.class);
        ConditionalAlertProvider alerts = mock(ConditionalAlertProvider.class);
        DhanInstrumentResolver instrumentResolver = mock(DhanInstrumentResolver.class);
        WebSocketMultiplexer websocket = mock(WebSocketMultiplexer.class);

        DhanBrokerConnection conn = new DhanBrokerConnection(
                mock(DhanClientHolder.class),
                instrumentResolver,
                marketData,
                mock(FuturesProvider.class),
                mock(OptionsProvider.class),
                orderCommand,
                orderQuery,
                mock(SliceOrderCommand.class),
                mock(BracketOrderProvider.class),
                mock(GttOrderProvider.class),
                portfolio,
                margin,
                sessionRisk,
                alerts,
                websocket
        );

        assertSame(marketData, conn.getCapability(MarketDataProvider.class).orElseThrow());
        assertSame(orderCommand, conn.getCapability(OrderCommand.class).orElseThrow());
        assertSame(orderQuery, conn.getCapability(OrderQuery.class).orElseThrow());
        assertSame(portfolio, conn.getCapability(PortfolioProvider.class).orElseThrow());
        assertSame(margin, conn.getCapability(MarginProvider.class).orElseThrow());
        assertSame(sessionRisk, conn.getCapability(SessionRiskProvider.class).orElseThrow());
        assertSame(alerts, conn.getCapability(ConditionalAlertProvider.class).orElseThrow());
        assertSame(instrumentResolver, conn.getCapability(DhanInstrumentResolver.class).orElseThrow());
        assertSame(websocket, conn.getCapability(WebSocketMultiplexer.class).orElseThrow());
    }

    @Test
    void connectDelegatesToWebSocketMultiplexer() {
        WebSocketMultiplexer ws = mock(WebSocketMultiplexer.class);
        DhanBrokerConnection conn = new DhanBrokerConnection(
                mock(DhanClientHolder.class),
                mock(DhanInstrumentResolver.class),
                mock(MarketDataProvider.class),
                mock(FuturesProvider.class),
                mock(OptionsProvider.class),
                mock(OrderCommand.class),
                mock(OrderQuery.class),
                mock(SliceOrderCommand.class),
                mock(BracketOrderProvider.class),
                mock(GttOrderProvider.class),
                mock(PortfolioProvider.class),
                mock(MarginProvider.class),
                mock(SessionRiskProvider.class),
                mock(ConditionalAlertProvider.class),
                ws
        );
        conn.connect();
        org.mockito.Mockito.verify(ws).connect();
    }

    @Test
    void disconnectDelegatesToWebSocketMultiplexer() {
        WebSocketMultiplexer ws = mock(WebSocketMultiplexer.class);
        DhanClientHolder clientHolder = mock(DhanClientHolder.class);
        DhanBrokerConnection conn = new DhanBrokerConnection(
                clientHolder,
                mock(DhanInstrumentResolver.class),
                mock(MarketDataProvider.class),
                mock(FuturesProvider.class),
                mock(OptionsProvider.class),
                mock(OrderCommand.class),
                mock(OrderQuery.class),
                mock(SliceOrderCommand.class),
                mock(BracketOrderProvider.class),
                mock(GttOrderProvider.class),
                mock(PortfolioProvider.class),
                mock(MarginProvider.class),
                mock(SessionRiskProvider.class),
                mock(ConditionalAlertProvider.class),
                ws
        );
        conn.disconnect();
        org.mockito.Mockito.verify(ws).disconnect();
        org.mockito.Mockito.verify(clientHolder).close();
    }

    private static DhanBrokerConnection createFullyMockedConnection() {
        return new DhanBrokerConnection(
                mock(DhanClientHolder.class),
                mock(DhanInstrumentResolver.class),
                mock(MarketDataProvider.class),
                mock(FuturesProvider.class),
                mock(OptionsProvider.class),
                mock(OrderCommand.class),
                mock(OrderQuery.class),
                mock(SliceOrderCommand.class),
                mock(BracketOrderProvider.class),
                mock(GttOrderProvider.class),
                mock(PortfolioProvider.class),
                mock(MarginProvider.class),
                mock(SessionRiskProvider.class),
                mock(ConditionalAlertProvider.class),
                mock(WebSocketMultiplexer.class)
        );
    }
}
