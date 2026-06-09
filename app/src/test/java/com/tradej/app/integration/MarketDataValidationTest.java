package com.tradej.app.integration;

import com.tradej.broker.api.IBrokerConnection;
import com.tradej.broker.dhan.DhanBrokerConnection;
import com.tradej.core.domain.model.Candle;
import com.tradej.core.domain.model.CandleHistoryRequest;
import com.tradej.core.domain.model.DepthLevel;
import com.tradej.core.domain.model.InstrumentKey;
import com.tradej.core.domain.model.MarketDepth;
import com.tradej.core.domain.model.Quote;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.execution.service.CaffeineIdempotencyCache;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import java.nio.file.Files;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Tag("live")
@Isolated
class MarketDataValidationTest {

    private DhanBrokerConnection connection;

    @BeforeEach
    void setUp() throws Exception {
        connection = new DhanBrokerConnection(
                LiveDhanTestSupport.connectionSettingsOrSkip(),
                com.tradej.broker.dhan.constants.DhanProtocolConstants.defaultRateLimiter(),
                new CaffeineIdempotencyCache());
        connection.loadDailyInstrumentCatalog(Files.createTempDirectory("dhan-validation"), false);
    }

    @AfterEach
    void tearDown() {
        if (connection != null) connection.disconnect();
    }

    @Test
    void ltpIsPositive() {
        long ltp = connection.marketData().getLtpPaisa(
                new InstrumentKey("RELIANCE", ExchangeSegment.NSE_EQ));
        assertTrue(ltp > 0, "LTP must be positive, got: " + ltp);
    }

    @Test
    void quoteOhlcInvariants() {
        Quote q = connection.marketData().getQuote(
                new InstrumentKey("RELIANCE", ExchangeSegment.NSE_EQ));
        assertNotNull(q, "Quote must not be null");
        assertTrue(q.highPaisa() >= q.openPaisa(), "High >= Open");
        assertTrue(q.highPaisa() >= q.closePaisa(), "High >= Close");
        assertTrue(q.lowPaisa() <= q.openPaisa(), "Low <= Open");
        assertTrue(q.lowPaisa() <= q.closePaisa(), "Low <= Close");
        assertTrue(q.highPaisa() >= q.lowPaisa(), "High >= Low");
        assertTrue(q.ltpPaisa() > 0, "LTP > 0");
        assertTrue(q.volume() >= 0, "Volume >= 0");
    }

    @Test
    void depthHasBidsAndAsks() {
        MarketDepth depth = connection.marketData().getDepth(
                new InstrumentKey("RELIANCE", ExchangeSegment.NSE_EQ));
        assertNotNull(depth, "Depth must not be null");
        assertFalse(depth.bids().isEmpty(), "Must have bids");
        assertFalse(depth.asks().isEmpty(), "Must have asks");
    }

    @Test
    void depthBidsSortedDescending() {
        MarketDepth depth = connection.marketData().getDepth(
                new InstrumentKey("RELIANCE", ExchangeSegment.NSE_EQ));
        List<DepthLevel> bids = depth.bids();
        for (int i = 1; i < bids.size(); i++) {
            assertTrue(bids.get(i - 1).pricePaisa() >= bids.get(i).pricePaisa(),
                    "Bids must be sorted descending by price");
        }
    }

    @Test
    void depthAsksSortedAscending() {
        MarketDepth depth = connection.marketData().getDepth(
                new InstrumentKey("RELIANCE", ExchangeSegment.NSE_EQ));
        List<DepthLevel> asks = depth.asks();
        for (int i = 1; i < asks.size(); i++) {
            assertTrue(asks.get(i - 1).pricePaisa() <= asks.get(i).pricePaisa(),
                    "Asks must be sorted ascending by price");
        }
    }

    @Test
    void ltpWithinBidAskSpread() {
        InstrumentKey key = new InstrumentKey("RELIANCE", ExchangeSegment.NSE_EQ);
        long ltp = connection.marketData().getLtpPaisa(key);
        MarketDepth depth = connection.marketData().getDepth(key);
        long bestBid = depth.bids().getFirst().pricePaisa();
        long bestAsk = depth.asks().getFirst().pricePaisa();
        org.junit.jupiter.api.Assumptions.assumeTrue(bestAsk > 0,
                "Ask price is 0 — incomplete market data (market may be closed)");
        assertTrue(ltp >= bestBid && ltp <= bestAsk,
                "LTP must be within bid-ask spread: bid=" + bestBid + " ltp=" + ltp + " ask=" + bestAsk);
    }

    @Test
    void historicalCandlesOhlcInvariants() {
        List<Candle> candles = connection.marketData().getCandles(
                new CandleHistoryRequest(
                        new InstrumentKey("RELIANCE", ExchangeSegment.NSE_EQ),
                        "1d",
                        LocalDate.now().minusDays(30),
                        LocalDate.now()));
        assertNotNull(candles, "Candles must not be null");
        assertFalse(candles.isEmpty(), "Must have candles");
        for (Candle c : candles) {
            assertTrue(c.highPaisa() >= c.openPaisa(), "High >= Open");
            assertTrue(c.highPaisa() >= c.closePaisa(), "High >= Close");
            assertTrue(c.lowPaisa() <= c.openPaisa(), "Low <= Open");
            assertTrue(c.lowPaisa() <= c.closePaisa(), "Low <= Close");
            assertTrue(c.highPaisa() >= c.lowPaisa(), "High >= Low");
            assertTrue(c.volume() >= 0, "Volume >= 0");
        }
    }

    @Test
    void historicalCandlesChronological() {
        List<Candle> candles = connection.marketData().getCandles(
                new CandleHistoryRequest(
                        new InstrumentKey("RELIANCE", ExchangeSegment.NSE_EQ),
                        "1d",
                        LocalDate.now().minusDays(10),
                        LocalDate.now()));
        for (int i = 1; i < candles.size(); i++) {
            assertTrue(candles.get(i).startTimeMs() > candles.get(i - 1).startTimeMs(),
                    "Candles must be in chronological order");
        }
    }
}
