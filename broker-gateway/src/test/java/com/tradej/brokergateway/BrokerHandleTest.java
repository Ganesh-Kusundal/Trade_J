package com.tradej.brokergateway;

import com.tradej.broker.api.IBrokerConnection;
import com.tradej.broker.api.port.BracketOrderProvider;
import com.tradej.broker.api.port.FuturesProvider;
import com.tradej.broker.api.port.GttOrderProvider;
import com.tradej.broker.api.port.InstrumentResolver;
import com.tradej.broker.api.port.MarginProvider;
import com.tradej.broker.api.port.MarketDataProvider;
import com.tradej.broker.api.port.NewsProvider;
import com.tradej.broker.api.port.OptionsProvider;
import com.tradej.broker.api.port.OrderCommand;
import com.tradej.broker.api.port.OrderQuery;
import com.tradej.broker.api.port.PortfolioProvider;
import com.tradej.broker.api.port.SliceOrderCommand;
import com.tradej.brokergateway.result.BrokerSource;
import com.tradej.brokergateway.result.GatewayResult;
import com.tradej.core.domain.instrument.IndexSymbols;
import com.tradej.core.domain.model.Balance;
import com.tradej.core.domain.model.Instrument;
import com.tradej.core.domain.model.InstrumentKey;
import com.tradej.core.domain.model.MarginEstimate;
import com.tradej.core.domain.model.MarginEstimateRequest;
import com.tradej.core.domain.model.MarketDepth;
import com.tradej.core.domain.model.NewsArticle;
import com.tradej.core.domain.model.OptionQuote;
import com.tradej.core.domain.model.Order;
import com.tradej.core.domain.model.OrderPreview;
import com.tradej.core.domain.model.OrderRequest;
import com.tradej.core.domain.model.Quote;
import com.tradej.core.domain.model.SliceOrderRequest;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.OptionType;
import com.tradej.core.domain.value.StrikeSelectionKind;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BrokerHandleTest {

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

    // ── Market Data ─────────────────────────────────────────────────

    @Test
    void quoteReturnsGatewayResultWithSourceAndLatency() {
        Quote quote = mock(Quote.class);
        when(marketDataProvider.getQuote(any())).thenReturn(quote);

        GatewayResult<Quote> result = handle.quote("RELIANCE", ExchangeSegment.NSE_EQ);

        assertNotNull(result);
        assertEquals(BrokerSource.DHAN, result.source());
        assertSame(quote, result.data());
        assertTrue(result.latencyMs() >= 0);
        assertNotNull(result.requestId());
        assertTrue(result.isSuccess());
    }

    @Test
    void ltpReturnsLongValue() {
        when(marketDataProvider.getLtpPaisa(any())).thenReturn(75000L);

        GatewayResult<Long> result = handle.ltp("RELIANCE", ExchangeSegment.NSE_EQ);

        assertEquals(75000L, result.data());
        assertEquals(BrokerSource.DHAN, result.source());
    }

    @Test
    void balanceReturnsPortfolioBalance() {
        Balance balance = mock(Balance.class);
        when(portfolioProvider.getBalance()).thenReturn(balance);

        GatewayResult<Balance> result = handle.balance();

        assertSame(balance, result.data());
    }

    @Test
    void sourceReturnsBrokerSource() {
        assertEquals(BrokerSource.DHAN, handle.source());
    }

    @Test
    void supportsChecksCapability() {
        when(connection.getCapability(MarketDataProvider.class)).thenReturn(Optional.of(marketDataProvider));
        when(connection.getCapability(PortfolioProvider.class)).thenReturn(Optional.empty());

        assertTrue(handle.supports(MarketDataProvider.class));
        assertFalse(handle.supports(PortfolioProvider.class));
    }

    @Test
    void defaultSegmentDetectsIndices() {
        when(marketDataProvider.getLtpPaisa(any())).thenReturn(25000L);
        GatewayResult<Long> result = handle.ltp("NIFTY");
        assertNotNull(result);
        verify(instrumentResolver).resolveNormalized(eq("NIFTY"), eq(ExchangeSegment.IDX_I));
    }

    @Test
    void defaultSegmentDetectsAllCanonicalIndexNames() {
        when(marketDataProvider.getLtpPaisa(any())).thenReturn(25000L);
        for (String index : IndexSymbols.canonicalIndexSet()) {
            handle.ltp(index);
        }
        for (String index : IndexSymbols.canonicalIndexSet()) {
            verify(instrumentResolver).resolveNormalized(eq(index), eq(ExchangeSegment.IDX_I));
        }
    }

    @Test
    void defaultSegmentDetectsBrokerAliasesAsIndices() {
        when(marketDataProvider.getLtpPaisa(any())).thenReturn(25000L);
        handle.ltp("BANKNIFTY");
        handle.ltp("FINNIFTY");
        handle.ltp("MIDCPNIFTY");
        handle.ltp("banknifty");
        verify(instrumentResolver).resolveNormalized(eq("BANKNIFTY"), eq(ExchangeSegment.IDX_I));
        verify(instrumentResolver).resolveNormalized(eq("FINNIFTY"), eq(ExchangeSegment.IDX_I));
        verify(instrumentResolver).resolveNormalized(eq("MIDCPNIFTY"), eq(ExchangeSegment.IDX_I));
        verify(instrumentResolver).resolveNormalized(eq("banknifty"), eq(ExchangeSegment.IDX_I));
    }

    @Test
    void defaultSegmentRoutesIndexOptionContractsToFno() {
        when(marketDataProvider.getLtpPaisa(any())).thenReturn(25000L);
        // An index option contract's underlying is an index, but the contract itself
        // trades on the FNO segment — defaultSegment must route it to NSE_FNO, not IDX_I.
        handle.ltp("NIFTY 30 JUN 30000 CALL");
        handle.ltp("NIFTY BANK 30 JUN 30000 PUT");
        handle.ltp("NIFTY 30 JUN FUT");
        verify(instrumentResolver).resolveNormalized(eq("NIFTY 30 JUN 30000 CALL"), eq(ExchangeSegment.NSE_FNO));
        verify(instrumentResolver).resolveNormalized(eq("NIFTY BANK 30 JUN 30000 PUT"), eq(ExchangeSegment.NSE_FNO));
        verify(instrumentResolver).resolveNormalized(eq("NIFTY 30 JUN FUT"), eq(ExchangeSegment.NSE_FNO));
    }

    @Test
    void defaultSegmentRoutesStockOptionContractsToFno() {
        when(marketDataProvider.getLtpPaisa(any())).thenReturn(75000L);
        // Stock options are FNO-segment too — NSE_EQ is for cash equity, not the option.
        handle.ltp("RELIANCE 30 JUN 3000 CALL");
        verify(instrumentResolver).resolveNormalized(eq("RELIANCE 30 JUN 3000 CALL"), eq(ExchangeSegment.NSE_FNO));
    }

    @Test
    void defaultSegmentUsesNseEqForStocks() {
        when(marketDataProvider.getLtpPaisa(any())).thenReturn(75000L);
        GatewayResult<Long> result = handle.ltp("RELIANCE");
        assertNotNull(result);
        verify(instrumentResolver).resolveNormalized(eq("RELIANCE"), eq(ExchangeSegment.NSE_EQ));
    }

    // ── Orders (OrderCommand) ───────────────────────────────────────

    @Test
    void placeOrderReturnsOrder() {
        Order order = mock(Order.class);
        OrderRequest request = mock(OrderRequest.class);
        when(orderCommand.placeOrder(request)).thenReturn(order);

        GatewayResult<Order> result = handle.placeOrder(request);

        assertSame(order, result.data());
        assertEquals(BrokerSource.DHAN, result.source());
    }

    @Test
    void cancelOrderReturnsBoolean() {
        when(orderCommand.cancelOrder("ORD123")).thenReturn(true);

        GatewayResult<Boolean> result = handle.cancelOrder("ORD123");

        assertTrue(result.data());
    }

    @Test
    void cancelAllOpenOrdersReturnsList() {
        when(orderCommand.cancelAllOpenOrders()).thenReturn(List.of("ORD1", "ORD2"));

        GatewayResult<List<String>> result = handle.cancelAllOpenOrders();

        assertEquals(2, result.data().size());
    }

    @Test
    void killSwitchReturnsBoolean() {
        when(orderCommand.setKillSwitch(true)).thenReturn(true);

        GatewayResult<Boolean> result = handle.killSwitch(true);

        assertTrue(result.data());
    }

    @Test
    void previewOrderReturnsPreview() {
        OrderPreview preview = mock(OrderPreview.class);
        OrderRequest request = mock(OrderRequest.class);
        when(orderCommand.previewOrder(request)).thenReturn(preview);

        GatewayResult<OrderPreview> result = handle.previewOrder(request);

        assertSame(preview, result.data());
    }

    // ── Margin (MarginProvider) ─────────────────────────────────────

    @Test
    void estimateMarginReturnsEstimate() {
        MarginEstimate estimate = mock(MarginEstimate.class);
        MarginEstimateRequest request = mock(MarginEstimateRequest.class);
        when(marginProvider.estimateMargin(request)).thenReturn(estimate);

        GatewayResult<MarginEstimate> result = handle.estimateMargin(request);

        assertSame(estimate, result.data());
    }

    // ── Options (OptionsProvider) ───────────────────────────────────

    @Test
    void greeksReturnsOptionQuote() {
        OptionQuote greeks = mock(OptionQuote.class);
        InstrumentKey key = new InstrumentKey("NIFTY", ExchangeSegment.IDX_I);
        when(optionsProvider.getGreeks(key)).thenReturn(greeks);

        GatewayResult<OptionQuote> result = handle.greeks(key);

        assertSame(greeks, result.data());
    }

    @Test
    void optionContractsReturnsList() {
        when(optionsProvider.getOptionContracts("NIFTY", ExchangeSegment.IDX_I, LocalDate.of(2025, 6, 26)))
                .thenReturn(List.of(mock(Instrument.class)));

        GatewayResult<List<Instrument>> result = handle.optionContracts("NIFTY", ExchangeSegment.IDX_I, LocalDate.of(2025, 6, 26));

        assertEquals(1, result.data().size());
    }

    @Test
    void selectStrikeReturnsLong() {
        when(optionsProvider.selectStrikePaisa("NIFTY", ExchangeSegment.IDX_I, 25000_00L,
                OptionType.CALL, StrikeSelectionKind.ATM, 0)).thenReturn(25000_00L);

        GatewayResult<Long> result = handle.selectStrike("NIFTY", ExchangeSegment.IDX_I,
                25000_00L, OptionType.CALL, StrikeSelectionKind.ATM, 0);

        assertEquals(25000_00L, result.data());
    }

    // ── Futures (capability-gated) ──────────────────────────────────

    @Test
    void futuresContractsReturnsListWhenSupported() {
        FuturesProvider futuresProvider = mock(FuturesProvider.class);
        when(connection.getCapability(FuturesProvider.class)).thenReturn(Optional.of(futuresProvider));
        when(futuresProvider.getContracts("NIFTY", ExchangeSegment.NSE_FNO))
                .thenReturn(List.of(mock(Instrument.class)));

        GatewayResult<List<Instrument>> result = handle.futuresContracts("NIFTY", ExchangeSegment.NSE_FNO);

        assertEquals(1, result.data().size());
    }

    @Test
    void futuresContractsThrowsWhenUnsupported() {
        when(connection.getCapability(FuturesProvider.class)).thenReturn(Optional.empty());

        assertThrows(UnsupportedOperationException.class,
                () -> handle.futuresContracts("NIFTY", ExchangeSegment.NSE_FNO));
    }

    // ── Advanced Orders (capability-gated) ──────────────────────────

    @Test
    void bracketOrderThrowsWhenUnsupported() {
        when(connection.getCapability(BracketOrderProvider.class)).thenReturn(Optional.empty());

        assertThrows(UnsupportedOperationException.class,
                () -> handle.bracketOrder(mock(OrderRequest.class), 100L, 90L, 5L));
    }

    @Test
    void gttOrderThrowsWhenUnsupported() {
        when(connection.getCapability(GttOrderProvider.class)).thenReturn(Optional.empty());

        assertThrows(UnsupportedOperationException.class,
                () -> handle.gttOrder(mock(OrderRequest.class), "GTT", null, null, null));
    }

    @Test
    void sliceOrderThrowsWhenUnsupported() {
        when(connection.getCapability(SliceOrderCommand.class)).thenReturn(Optional.empty());

        assertThrows(UnsupportedOperationException.class,
                () -> handle.sliceOrder(mock(SliceOrderRequest.class)));
    }

    // ── News (capability-gated) ─────────────────────────────────────

    @Test
    void newsReturnsListWhenSupported() {
        NewsProvider newsProvider = mock(NewsProvider.class);
        when(connection.getCapability(NewsProvider.class)).thenReturn(Optional.of(newsProvider));
        when(newsProvider.getNewsForPositions(1, 10)).thenReturn(List.of(mock(NewsArticle.class)));

        GatewayResult<List<NewsArticle>> result = handle.news(1, 10);

        assertEquals(1, result.data().size());
    }

    @Test
    void newsThrowsWhenUnsupported() {
        when(connection.getCapability(NewsProvider.class)).thenReturn(Optional.empty());

        assertThrows(UnsupportedOperationException.class,
                () -> handle.news(1, 10));
    }

    // ── Portfolio Summary ───────────────────────────────────────────

    @Test
    void portfolioSummaryReturnsConsolidatedView() {
        Balance balance = mock(Balance.class);
        when(balance.cashPaisa()).thenReturn(500_000_00L);
        when(portfolioProvider.getBalance()).thenReturn(balance);
        when(portfolioProvider.getPositions()).thenReturn(List.of(mock(com.tradej.core.domain.model.Position.class)));
        when(portfolioProvider.getHoldings()).thenReturn(List.of(
                mock(com.tradej.core.domain.model.Holding.class),
                mock(com.tradej.core.domain.model.Holding.class)));

        GatewayResult<BrokerHandle.PortfolioSummary> result = handle.portfolioSummary();

        assertNotNull(result.data());
        assertEquals(500_000_00L, result.data().cashPaisa());
        assertEquals(1, result.data().positionCount());
        assertEquals(2, result.data().holdingCount());
    }

    // ── WebSocket Status ────────────────────────────────────────────

    @Test
    void isWebSocketConnectedDelegatesToMultiplexer() {
        com.tradej.broker.api.port.WebSocketMultiplexer ws = mock(com.tradej.broker.api.port.WebSocketMultiplexer.class);
        when(connection.websocket()).thenReturn(ws);
        when(ws.isConnected()).thenReturn(true);

        assertTrue(handle.isWebSocketConnected());
    }

    @Test
    void connectWebSocketDelegatesToMultiplexer() {
        com.tradej.broker.api.port.WebSocketMultiplexer ws = mock(com.tradej.broker.api.port.WebSocketMultiplexer.class);
        when(connection.websocket()).thenReturn(ws);

        handle.connectWebSocket();

        verify(ws).connect();
    }

    @Test
    void disconnectWebSocketDelegatesToMultiplexer() {
        com.tradej.broker.api.port.WebSocketMultiplexer ws = mock(com.tradej.broker.api.port.WebSocketMultiplexer.class);
        when(connection.websocket()).thenReturn(ws);

        handle.disconnectWebSocket();

        verify(ws).disconnect();
    }
}
