package com.tradej.broker.core.routing;

import com.tradej.broker.api.IBrokerConnection;
import com.tradej.broker.api.port.MarketDataProvider;
import com.tradej.broker.api.port.WebSocketMultiplexer;
import com.tradej.core.domain.model.InstrumentKey;
import com.tradej.core.domain.model.Quote;
import com.tradej.core.domain.value.ExchangeSegment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class FallbackMarketDataProviderTest {

    @Mock private IBrokerConnection connection;
    @Mock private MarketDataProvider primaryProvider;
    @Mock private WebSocketMultiplexer webSocket;

    private FallbackMarketDataProvider fallback;
    private static final InstrumentKey RELIANCE = new InstrumentKey("RELIANCE", ExchangeSegment.NSE_EQ);

    @BeforeEach
    void setUp() {
        lenient().when(connection.marketData()).thenReturn(primaryProvider);
        lenient().when(connection.websocket()).thenReturn(webSocket);
        fallback = new FallbackMarketDataProvider(connection, 5000L);
    }

    @Test
    void webSocketConnected_usesPrimaryProvider() {
        when(webSocket.isConnected()).thenReturn(true);
        when(primaryProvider.getLtpPaisa(RELIANCE)).thenReturn(250000L);

        long ltp = fallback.getLtpPaisa(RELIANCE);
        assertEquals(250000L, ltp);
        assertFalse(fallback.isFallbackActive());
    }

    @Test
    void webSocketDisconnected_activatesFallback() {
        when(webSocket.isConnected()).thenReturn(false);
        when(primaryProvider.getLtpPaisa(RELIANCE)).thenReturn(250000L);

        long ltp = fallback.getLtpPaisa(RELIANCE);
        assertEquals(250000L, ltp);
        assertTrue(fallback.isFallbackActive(), "Fallback should be active when WebSocket is disconnected");
    }

    @Test
    void webSocketReconnected_deactivatesFallback() {
        when(webSocket.isConnected()).thenReturn(false);
        when(primaryProvider.getLtpPaisa(RELIANCE)).thenReturn(250000L);

        fallback.getLtpPaisa(RELIANCE);
        assertTrue(fallback.isFallbackActive());

        fallback.onWebSocketReconnected();
        assertFalse(fallback.isFallbackActive(), "Fallback should be deactivated after WebSocket reconnects");
    }

    @Test
    void quote_webSocketConnected() {
        when(webSocket.isConnected()).thenReturn(true);
        Quote mockQuote = mock(Quote.class);
        when(primaryProvider.getQuote(RELIANCE)).thenReturn(mockQuote);

        Quote result = fallback.getQuote(RELIANCE);
        assertSame(mockQuote, result);
        assertFalse(fallback.isFallbackActive());
    }

    @Test
    void quote_webSocketDisconnected_fallbackToRest() {
        when(webSocket.isConnected()).thenReturn(false);
        Quote mockQuote = mock(Quote.class);
        when(primaryProvider.getQuote(RELIANCE)).thenReturn(mockQuote);

        Quote result = fallback.getQuote(RELIANCE);
        assertSame(mockQuote, result);
        assertTrue(fallback.isFallbackActive());
    }

    @Test
    void ohlcSnapshot_alwaysDelegatesToPrimary() {
        Quote mockQuote = mock(Quote.class);
        when(primaryProvider.getOhlcSnapshot(RELIANCE)).thenReturn(mockQuote);

        Quote result = fallback.getOhlcSnapshot(RELIANCE);
        assertSame(mockQuote, result);
        assertFalse(fallback.isFallbackActive(), "OHLC should not trigger fallback");
    }

    @Test
    void webSocketException_treatedAsDisconnected() {
        when(webSocket.isConnected()).thenThrow(new RuntimeException("Connection error"));
        when(primaryProvider.getLtpPaisa(RELIANCE)).thenReturn(250000L);

        long ltp = fallback.getLtpPaisa(RELIANCE);
        assertEquals(250000L, ltp);
        assertTrue(fallback.isFallbackActive());
    }
}
