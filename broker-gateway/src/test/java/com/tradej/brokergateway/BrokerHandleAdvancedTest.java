package com.tradej.brokergateway;

import com.tradej.broker.api.IBrokerConnection;
import com.tradej.broker.api.port.ConditionalAlertProvider;
import com.tradej.broker.api.port.InstrumentResolver;
import com.tradej.broker.api.port.MarginProvider;
import com.tradej.broker.api.port.MarketDataProvider;
import com.tradej.broker.api.port.OptionsProvider;
import com.tradej.broker.api.port.OrderCommand;
import com.tradej.broker.api.port.OrderQuery;
import com.tradej.broker.api.port.PortfolioProvider;
import com.tradej.broker.api.spi.BrokerSource;
import com.tradej.brokergateway.result.GatewayResult;
import com.tradej.core.domain.model.Balance;
import com.tradej.core.domain.model.ConditionalAlert;
import com.tradej.core.domain.model.ConditionalAlertRequest;
import com.tradej.core.domain.model.Instrument;
import com.tradej.core.domain.model.InstrumentKey;
import com.tradej.core.domain.model.Quote;
import com.tradej.core.domain.model.RollingOptionHistoryRequest;
import com.tradej.core.domain.model.RollingOptionSeries;
import com.tradej.core.domain.value.ExchangeSegment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("unit")
class BrokerHandleAdvancedTest {

    private IBrokerConnection connection;
    private MarketDataProvider marketDataProvider;
    private OptionsProvider optionsProvider;
    private OrderCommand orderCommand;
    private OrderQuery orderQuery;
    private PortfolioProvider portfolioProvider;
    private MarginProvider marginProvider;
    private InstrumentResolver instrumentResolver;
    private BrokerHandle handle;

    @BeforeEach
    void setUp() {
        connection = mock(IBrokerConnection.class);
        marketDataProvider = mock(MarketDataProvider.class);
        optionsProvider = mock(OptionsProvider.class);
        orderCommand = mock(OrderCommand.class);
        orderQuery = mock(OrderQuery.class);
        portfolioProvider = mock(PortfolioProvider.class);
        marginProvider = mock(MarginProvider.class);
        instrumentResolver = mock(InstrumentResolver.class);

        when(connection.marketData()).thenReturn(marketDataProvider);
        when(connection.options()).thenReturn(optionsProvider);
        when(connection.orders()).thenReturn(orderCommand);
        when(connection.orderQuery()).thenReturn(orderQuery);
        when(connection.portfolio()).thenReturn(portfolioProvider);
        when(connection.margin()).thenReturn(marginProvider);
        when(connection.instruments()).thenReturn(instrumentResolver);

        Instrument instrument = mock(Instrument.class);
        when(instrument.key()).thenReturn(new InstrumentKey("RELIANCE", ExchangeSegment.NSE_EQ));
        when(instrumentResolver.resolveNormalized(anyString(), any())).thenReturn(instrument);

        handle = new BrokerHandle(BrokerSource.DHAN, connection);
    }

    // ── Batch Market Data ────────────────────────────────────────────

    @Test
    void batchLtpReturnsMapOfPrices() {
        InstrumentKey key1 = new InstrumentKey("RELIANCE", ExchangeSegment.NSE_EQ);
        InstrumentKey key2 = new InstrumentKey("TCS", ExchangeSegment.NSE_EQ);
        Collection<InstrumentKey> keys = List.of(key1, key2);
        Map<InstrumentKey, Long> ltpMap = Map.of(key1, 250000L, key2, 350000L);

        when(marketDataProvider.getLtpBatch(keys)).thenReturn(ltpMap);

        GatewayResult<Map<InstrumentKey, Long>> result = handle.batchLtp(keys);

        assertNotNull(result);
        assertEquals(BrokerSource.DHAN, result.source());
        assertEquals(2, result.data().size());
        assertEquals(250000L, result.data().get(key1));
        assertEquals(350000L, result.data().get(key2));
        assertTrue(result.latencyMs() >= 0);
        assertNotNull(result.requestId());
        assertTrue(result.isSuccess());
        verify(marketDataProvider).getLtpBatch(keys);
    }

    @Test
    void batchQuoteReturnsMapOfQuotes() {
        InstrumentKey key1 = new InstrumentKey("RELIANCE", ExchangeSegment.NSE_EQ);
        InstrumentKey key2 = new InstrumentKey("TCS", ExchangeSegment.NSE_EQ);
        Collection<InstrumentKey> keys = List.of(key1, key2);
        Quote quote1 = mock(Quote.class);
        Quote quote2 = mock(Quote.class);
        Map<InstrumentKey, Quote> quoteMap = Map.of(key1, quote1, key2, quote2);

        when(marketDataProvider.getQuoteBatch(keys)).thenReturn(quoteMap);

        GatewayResult<Map<InstrumentKey, Quote>> result = handle.batchQuote(keys);

        assertNotNull(result);
        assertEquals(BrokerSource.DHAN, result.source());
        assertEquals(2, result.data().size());
        assertSame(quote1, result.data().get(key1));
        assertSame(quote2, result.data().get(key2));
        assertTrue(result.latencyMs() >= 0);
        assertTrue(result.isSuccess());
        verify(marketDataProvider).getQuoteBatch(keys);
    }

    @Test
    void batchOhlcReturnsMapOfQuotes() {
        InstrumentKey key1 = new InstrumentKey("RELIANCE", ExchangeSegment.NSE_EQ);
        InstrumentKey key2 = new InstrumentKey("TCS", ExchangeSegment.NSE_EQ);
        Collection<InstrumentKey> keys = List.of(key1, key2);
        Quote ohlc1 = mock(Quote.class);
        Quote ohlc2 = mock(Quote.class);
        Map<InstrumentKey, Quote> ohlcMap = Map.of(key1, ohlc1, key2, ohlc2);

        when(marketDataProvider.getOhlcBatch(keys)).thenReturn(ohlcMap);

        GatewayResult<Map<InstrumentKey, Quote>> result = handle.batchOhlc(keys);

        assertNotNull(result);
        assertEquals(BrokerSource.DHAN, result.source());
        assertEquals(2, result.data().size());
        assertSame(ohlc1, result.data().get(key1));
        assertSame(ohlc2, result.data().get(key2));
        assertTrue(result.latencyMs() >= 0);
        assertTrue(result.isSuccess());
        verify(marketDataProvider).getOhlcBatch(keys);
    }

    // ── Rolling Options ──────────────────────────────────────────────

    @Test
    void rollingOptionsReturnsSeries() {
        RollingOptionHistoryRequest request = mock(RollingOptionHistoryRequest.class);
        RollingOptionSeries series = mock(RollingOptionSeries.class);

        when(optionsProvider.getExpiredOptionHistory(request)).thenReturn(series);

        GatewayResult<RollingOptionSeries> result = handle.rollingOptions(request);

        assertNotNull(result);
        assertEquals(BrokerSource.DHAN, result.source());
        assertSame(series, result.data());
        assertTrue(result.latencyMs() >= 0);
        assertTrue(result.isSuccess());
        verify(optionsProvider).getExpiredOptionHistory(request);
    }

    // ── Cancel & Square Off ──────────────────────────────────────────

    @Test
    void cancelAndSquareOffReturnsOrderIds() {
        List<String> orderIds = List.of("ORD001", "ORD002", "ORD003");

        when(orderCommand.cancelAndSquareOffIntradayPositions()).thenReturn(orderIds);

        GatewayResult<List<String>> result = handle.cancelAndSquareOff();

        assertNotNull(result);
        assertEquals(BrokerSource.DHAN, result.source());
        assertEquals(3, result.data().size());
        assertEquals("ORD001", result.data().get(0));
        assertEquals("ORD002", result.data().get(1));
        assertEquals("ORD003", result.data().get(2));
        assertTrue(result.latencyMs() >= 0);
        assertTrue(result.isSuccess());
        verify(orderCommand).cancelAndSquareOffIntradayPositions();
    }

    // ── Alerts ───────────────────────────────────────────────────────

    @Test
    void placeAlertReturnsAlertId() {
        ConditionalAlertProvider alertProvider = mock(ConditionalAlertProvider.class);
        ConditionalAlertRequest request = mock(ConditionalAlertRequest.class);
        String alertId = "ALERT123";

        when(connection.getCapability(ConditionalAlertProvider.class)).thenReturn(Optional.of(alertProvider));
        when(alertProvider.placeAlert(request)).thenReturn(alertId);

        GatewayResult<String> result = handle.placeAlert(request);

        assertNotNull(result);
        assertEquals(BrokerSource.DHAN, result.source());
        assertEquals(alertId, result.data());
        assertTrue(result.latencyMs() >= 0);
        assertTrue(result.isSuccess());
        verify(alertProvider).placeAlert(request);
    }

    @Test
    void getAlertReturnsAlert() {
        ConditionalAlertProvider alertProvider = mock(ConditionalAlertProvider.class);
        String alertId = "ALERT123";
        ConditionalAlert alert = new ConditionalAlert(alertId, "ACTIVE", "Price alert triggered");

        when(connection.getCapability(ConditionalAlertProvider.class)).thenReturn(Optional.of(alertProvider));
        when(alertProvider.getAlert(alertId)).thenReturn(alert);

        GatewayResult<ConditionalAlert> result = handle.getAlert(alertId);

        assertNotNull(result);
        assertEquals(BrokerSource.DHAN, result.source());
        assertEquals(alertId, result.data().alertId());
        assertEquals("ACTIVE", result.data().status());
        assertEquals("Price alert triggered", result.data().message());
        assertTrue(result.latencyMs() >= 0);
        assertTrue(result.isSuccess());
        verify(alertProvider).getAlert(alertId);
    }

    @Test
    void listAlertsReturnsList() {
        ConditionalAlertProvider alertProvider = mock(ConditionalAlertProvider.class);
        ConditionalAlert alert1 = new ConditionalAlert("ALERT1", "ACTIVE", "Alert 1");
        ConditionalAlert alert2 = new ConditionalAlert("ALERT2", "TRIGGERED", "Alert 2");
        List<ConditionalAlert> alerts = List.of(alert1, alert2);

        when(connection.getCapability(ConditionalAlertProvider.class)).thenReturn(Optional.of(alertProvider));
        when(alertProvider.listAlerts()).thenReturn(alerts);

        GatewayResult<List<ConditionalAlert>> result = handle.listAlerts();

        assertNotNull(result);
        assertEquals(BrokerSource.DHAN, result.source());
        assertEquals(2, result.data().size());
        assertEquals("ALERT1", result.data().get(0).alertId());
        assertEquals("ALERT2", result.data().get(1).alertId());
        assertTrue(result.latencyMs() >= 0);
        assertTrue(result.isSuccess());
        verify(alertProvider).listAlerts();
    }

    @Test
    void deleteAlertReturnsBoolean() {
        ConditionalAlertProvider alertProvider = mock(ConditionalAlertProvider.class);
        String alertId = "ALERT123";

        when(connection.getCapability(ConditionalAlertProvider.class)).thenReturn(Optional.of(alertProvider));
        when(alertProvider.deleteAlert(alertId)).thenReturn(true);

        GatewayResult<Boolean> result = handle.deleteAlert(alertId);

        assertNotNull(result);
        assertEquals(BrokerSource.DHAN, result.source());
        assertTrue(result.data());
        assertTrue(result.latencyMs() >= 0);
        assertTrue(result.isSuccess());
        verify(alertProvider).deleteAlert(alertId);
    }

    @Test
    void alertsThrowsWhenUnsupported() {
        when(connection.getCapability(ConditionalAlertProvider.class)).thenReturn(Optional.empty());

        ConditionalAlertRequest request = mock(ConditionalAlertRequest.class);

        UnsupportedOperationException exception = assertThrows(
                UnsupportedOperationException.class,
                () -> handle.placeAlert(request)
        );

        assertTrue(exception.getMessage().contains("alerts"));
        verify(connection, atLeastOnce()).getCapability(ConditionalAlertProvider.class);
    }

    // ── Capabilities ─────────────────────────────────────────────────

    @Test
    void capabilitiesReturnsFullMap() {
        // Mock all capability checks to return empty (not supported)
        when(connection.getCapability(any())).thenReturn(Optional.empty());
        when(connection.instruments()).thenReturn(instrumentResolver);
        when(instrumentResolver.catalogSize()).thenReturn(1000);
        when(instrumentResolver.isLoaded()).thenReturn(true);

        Map<String, Boolean> capabilities = handle.capabilities();

        assertNotNull(capabilities);
        assertFalse(capabilities.isEmpty());

        // Verify key capabilities are present in the map
        assertTrue(capabilities.containsKey("MarketDataProvider"));
        assertTrue(capabilities.containsKey("OptionsProvider"));
        assertTrue(capabilities.containsKey("OrderCommand"));
        assertTrue(capabilities.containsKey("OrderQuery"));
        assertTrue(capabilities.containsKey("PortfolioProvider"));
        assertTrue(capabilities.containsKey("MarginProvider"));
        assertTrue(capabilities.containsKey("InstrumentResolver"));
        assertTrue(capabilities.containsKey("WebSocketMultiplexer"));
        assertTrue(capabilities.containsKey("FuturesProvider"));
        assertTrue(capabilities.containsKey("BracketOrderProvider"));
        assertTrue(capabilities.containsKey("GttOrderProvider"));
        assertTrue(capabilities.containsKey("SliceOrderCommand"));
        assertTrue(capabilities.containsKey("SessionRiskProvider"));
        assertTrue(capabilities.containsKey("ConditionalAlertProvider"));
        assertTrue(capabilities.containsKey("NewsProvider"));

        // Verify capability marker interfaces are present
        assertTrue(capabilities.containsKey("OptionsCapable"));
        assertTrue(capabilities.containsKey("FuturesCapable"));
        assertTrue(capabilities.containsKey("MarginCapable"));
        assertTrue(capabilities.containsKey("AlertCapable"));
        assertTrue(capabilities.containsKey("AdvancedOrderCapable"));
        assertTrue(capabilities.containsKey("NewsCapable"));

        // All should be false since we mocked empty Optional
        capabilities.values().forEach(supported -> assertFalse(supported));
    }

    @Test
    void capabilitiesReturnsTrueForSupportedCapabilities() {
        // Mock some capabilities as supported
        when(connection.getCapability(MarketDataProvider.class)).thenReturn(Optional.of(marketDataProvider));
        when(connection.getCapability(OptionsProvider.class)).thenReturn(Optional.of(optionsProvider));
        when(connection.getCapability(OrderCommand.class)).thenReturn(Optional.of(orderCommand));
        when(connection.getCapability(any())).thenReturn(Optional.empty());
        when(connection.getCapability(MarketDataProvider.class)).thenReturn(Optional.of(marketDataProvider));
        when(connection.getCapability(OptionsProvider.class)).thenReturn(Optional.of(optionsProvider));
        when(connection.getCapability(OrderCommand.class)).thenReturn(Optional.of(orderCommand));
        when(connection.instruments()).thenReturn(instrumentResolver);
        when(instrumentResolver.catalogSize()).thenReturn(1000);
        when(instrumentResolver.isLoaded()).thenReturn(true);

        Map<String, Boolean> capabilities = handle.capabilities();

        assertNotNull(capabilities);
        assertTrue(capabilities.get("MarketDataProvider"));
        assertTrue(capabilities.get("OptionsProvider"));
        assertTrue(capabilities.get("OrderCommand"));
    }
}
