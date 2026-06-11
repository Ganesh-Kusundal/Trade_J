package com.tradej.brokergateway.simulation;

import com.tradej.broker.api.IBrokerConnection;
import com.tradej.brokergateway.BrokerGateway;
import com.tradej.brokergateway.BrokerHandle;
import com.tradej.brokergateway.MarketGateway;
import com.tradej.broker.api.spi.BrokerSource;
import com.tradej.brokergateway.result.GatewayResult;
import com.tradej.core.domain.model.Candle;
import com.tradej.core.domain.model.MarginEstimate;
import com.tradej.core.domain.model.MarginEstimateRequest;
import com.tradej.core.domain.model.MarketDepth;
import com.tradej.core.domain.model.OptionChainSnapshot;
import com.tradej.core.domain.model.Order;
import com.tradej.core.domain.model.OrderPreview;
import com.tradej.core.domain.model.OrderRequest;
import com.tradej.core.domain.model.Quote;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.OrderType;
import com.tradej.core.domain.value.ProductType;
import com.tradej.core.domain.value.Side;
import com.tradej.core.domain.value.Validity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class PaperBrokerGatewayTest {

    private BrokerGateway gateway;
    private BrokerHandle handle;

    @BeforeEach
    void setUp() {
        IBrokerConnection connection = new PaperBrokerConnection();
        gateway = BrokerGateway.of(BrokerSource.SIMULATION, connection);
        handle = gateway.broker("simulation");
    }

    @Test
    void gatewayCreatesBrokerHandle() {
        assertNotNull(handle);
        assertEquals(BrokerSource.SIMULATION, handle.source());
    }

    @Test
    void ltpRetrievesSimulatedPrice() {
        GatewayResult<Long> result = handle.ltp("RELIANCE");
        assertTrue(result.isSuccess());
        assertTrue(result.data() > 0, "LTP should be positive");
        assertEquals(BrokerSource.SIMULATION, result.source());
        assertTrue(result.latencyMs() >= 0);
    }

    @Test
    void quoteRetrievesFullSnapshot() {
        GatewayResult<Quote> result = handle.quote("TCS", ExchangeSegment.NSE_EQ);
        assertTrue(result.isSuccess());
        Quote q = result.data();
        assertNotNull(q);
        assertTrue(q.ltpPaisa() > 0);
        assertTrue(q.volume() > 0);
    }

    @Test
    void depthRetrievesBidAskLevels() {
        GatewayResult<MarketDepth> result = handle.depth("HDFCBANK", ExchangeSegment.NSE_EQ);
        assertTrue(result.isSuccess());
        MarketDepth d = result.data();
        assertEquals(5, d.bids().size());
        assertEquals(5, d.asks().size());
    }

    @Test
    void historicalRetrievesCandles() {
        GatewayResult<List<Candle>> result = handle.historical("SBIN", "1d",
                LocalDate.now().minusDays(30), LocalDate.now());
        assertTrue(result.isSuccess());
        assertEquals(30, result.data().size());
        for (Candle c : result.data()) {
            assertEquals("SBIN", c.symbol());
            assertTrue(c.closePaisa() > 0);
        }
    }

    @Test
    void optionChainRetrievesSnapshot() {
        GatewayResult<OptionChainSnapshot> result = handle.optionChain("NIFTY");
        assertTrue(result.isSuccess());
        assertNotNull(result.data());
    }

    @Test
    void balanceRetrievesPortfolio() {
        var result = handle.balance();
        assertTrue(result.isSuccess());
        assertEquals(100_000_00L, result.data().cashPaisa());
        assertEquals(100_000_00L, result.data().withdrawablePaisa());
    }

    @Test
    void placeAndCancelOrder() {
        OrderRequest request = new OrderRequest("RELIANCE", ExchangeSegment.NSE_EQ,
                Side.BUY, 10, OrderType.LIMIT, 250_000L, 0L,
                ProductType.INTRADAY, Validity.DAY, null);

        GatewayResult<Order> placed = handle.placeOrder(request);
        assertTrue(placed.isSuccess());
        assertNotNull(placed.data().orderId());

        GatewayResult<Boolean> cancelled = handle.cancelOrder(placed.data().orderId());
        assertTrue(cancelled.data());
    }

    @Test
    void previewOrderReturnsEstimate() {
        OrderRequest request = new OrderRequest("RELIANCE", ExchangeSegment.NSE_EQ,
                Side.BUY, 10, OrderType.LIMIT, 250_000L, 0L,
                ProductType.INTRADAY, Validity.DAY, null);

        GatewayResult<OrderPreview> preview = handle.previewOrder(request);
        assertTrue(preview.isSuccess());
        assertTrue(preview.data().estimatedNotionalPaisa() > 0);
        assertTrue(preview.data().estimatedMarginPaisa() > 0);
    }

    @Test
    void estimateMarginReturnsEstimate() {
        MarginEstimateRequest request = new MarginEstimateRequest("RELIANCE",
                ExchangeSegment.NSE_EQ, Side.BUY, 10,
                ProductType.INTRADAY, OrderType.LIMIT, 250_000L, 0L);

        GatewayResult<MarginEstimate> result = handle.estimateMargin(request);
        assertTrue(result.isSuccess());
        assertTrue(result.data().totalMarginPaisa() > 0);
    }

    @Test
    void marketGatewayRoutesToSimulation() {
        MarketGateway market = MarketGateway.create(gateway);
        GatewayResult<Long> ltp = market.ltp("RELIANCE");
        assertTrue(ltp.isSuccess());
        assertEquals(BrokerSource.SIMULATION, ltp.source());
    }

    @Test
    void autoSegmentDetectsIndex() {
        GatewayResult<Long> result = handle.ltp("NIFTY");
        assertTrue(result.isSuccess());
        assertTrue(result.data() > 2_000_000L, "NIFTY LTP should be > 20000 in paisa");
    }

    @Test
    void spiProviderCreatesWorkingConnection() {
        SimulationBrokerProvider provider = new SimulationBrokerProvider();
        IBrokerConnection conn = provider.create(null);
        assertNotNull(conn);
        assertEquals(BrokerSource.SIMULATION, provider.source());

        long ltp = conn.marketData().getLtpPaisa(
                new com.tradej.core.domain.model.InstrumentKey("RELIANCE", ExchangeSegment.NSE_EQ));
        assertTrue(ltp > 0);
    }
}
