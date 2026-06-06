package com.tradej.brokergateway.simulation;

import com.tradej.broker.api.model.MarketSubscriptionRequest;
import com.tradej.core.domain.model.Balance;
import com.tradej.core.domain.model.Candle;
import com.tradej.core.domain.model.CandleHistoryRequest;
import com.tradej.core.domain.model.InstrumentKey;
import com.tradej.core.domain.model.MarketDepth;
import com.tradej.core.domain.model.Position;
import com.tradej.core.domain.model.Quote;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.FeedMode;
import com.tradej.core.domain.value.Side;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class SimulatedProviderTest {

    // ══════════════════════════════════════════════════════════════════════
    // SimulatedMarketDataProvider tests
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    class MarketDataProviderTests {

        private SimulatedMarketDataProvider provider;
        private InstrumentKey relianceKey;

        @BeforeEach
        void setUp() {
            provider = new SimulatedMarketDataProvider();
            relianceKey = new InstrumentKey("RELIANCE", ExchangeSegment.NSE_EQ);
        }

        // ── getLtpPaisa() ─────────────────────────────────────────────────

        @Test
        void getLtpPaisaReturnsPositiveValue() {
            long ltp = provider.getLtpPaisa(relianceKey);
            assertTrue(ltp > 0, "LTP should be positive");
        }

        @Test
        void getLtpPaisaReturnsValueNearBasePrice() {
            long ltp = provider.getLtpPaisa(relianceKey);
            // RELIANCE base is 250_000L, jitter is +/-0.5%
            assertTrue(ltp >= 248_000L && ltp <= 252_000L,
                    "LTP should be near base price 250000, got: " + ltp);
        }

        @Test
        void getLtpPaisaForUnknownSymbolReturnsDefault() {
            InstrumentKey unknown = new InstrumentKey("UNKNOWN_SYMBOL", ExchangeSegment.NSE_EQ);
            long ltp = provider.getLtpPaisa(unknown);
            // Default base is 100_000L with jitter
            assertTrue(ltp > 0, "LTP for unknown symbol should be positive (default base)");
        }

        // ── getQuote() ────────────────────────────────────────────────────

        @Test
        void getQuoteReturnsValidQuote() {
            Quote quote = provider.getQuote(relianceKey);
            assertNotNull(quote);
            assertTrue(quote.ltpPaisa() > 0, "LTP should be positive");
            assertTrue(quote.volume() > 0, "Volume should be positive");
            assertNotNull(quote.instrument());
        }

        @Test
        void getQuoteHasOhlcFields() {
            Quote quote = provider.getQuote(relianceKey);
            assertTrue(quote.openPaisa() > 0);
            assertTrue(quote.highPaisa() > 0);
            assertTrue(quote.lowPaisa() > 0);
            assertTrue(quote.closePaisa() > 0);
        }

        @Test
        void getQuoteHasTimestamp() {
            Quote quote = provider.getQuote(relianceKey);
            assertTrue(quote.timestampMs() > 0);
        }

        // ── getDepth() ────────────────────────────────────────────────────

        @Test
        void getDepthReturnsFiveBidLevels() {
            MarketDepth depth = provider.getDepth(relianceKey);
            assertNotNull(depth);
            assertEquals(5, depth.bids().size());
        }

        @Test
        void getDepthReturnsFiveAskLevels() {
            MarketDepth depth = provider.getDepth(relianceKey);
            assertEquals(5, depth.asks().size());
        }

        @Test
        void getDepthBidPricesArePositive() {
            MarketDepth depth = provider.getDepth(relianceKey);
            depth.bids().forEach(level ->
                    assertTrue(level.pricePaisa() > 0, "Bid price should be positive"));
        }

        @Test
        void getDepthAskPricesArePositive() {
            MarketDepth depth = provider.getDepth(relianceKey);
            depth.asks().forEach(level ->
                    assertTrue(level.pricePaisa() > 0, "Ask price should be positive"));
        }

        @Test
        void getDepthLevelsHavePositiveQuantity() {
            MarketDepth depth = provider.getDepth(relianceKey);
            depth.bids().forEach(level -> assertTrue(level.quantity() > 0));
            depth.asks().forEach(level -> assertTrue(level.quantity() > 0));
        }

        // ── getCandles() ──────────────────────────────────────────────────

        @Test
        void getCandlesReturns30Candles() {
            CandleHistoryRequest request = new CandleHistoryRequest(
                    relianceKey, "1d",
                    LocalDate.now().minusDays(30), LocalDate.now());
            List<Candle> candles = provider.getCandles(request);

            assertEquals(30, candles.size());
        }

        @Test
        void getCandlesHaveValidOhlc() {
            CandleHistoryRequest request = new CandleHistoryRequest(
                    relianceKey, "1d",
                    LocalDate.now().minusDays(30), LocalDate.now());
            List<Candle> candles = provider.getCandles(request);

            for (Candle candle : candles) {
                assertTrue(candle.openPaisa() > 0, "Open should be positive");
                assertTrue(candle.closePaisa() > 0, "Close should be positive");
                assertTrue(candle.highPaisa() > 0, "High should be positive");
                assertTrue(candle.lowPaisa() > 0, "Low should be positive");
            }
        }

        @Test
        void getCandlesMatchRequestedSymbol() {
            CandleHistoryRequest request = new CandleHistoryRequest(
                    relianceKey, "1d",
                    LocalDate.now().minusDays(30), LocalDate.now());
            List<Candle> candles = provider.getCandles(request);

            for (Candle candle : candles) {
                assertEquals("RELIANCE", candle.symbol());
                assertEquals("1d", candle.interval());
            }
        }

        @Test
        void getCandlesAreMarkedComplete() {
            CandleHistoryRequest request = new CandleHistoryRequest(
                    relianceKey, "1d",
                    LocalDate.now().minusDays(30), LocalDate.now());
            List<Candle> candles = provider.getCandles(request);

            assertTrue(candles.stream().allMatch(Candle::closed));
        }

        // ── getLtpBatch() ─────────────────────────────────────────────────

        @Test
        void getLtpBatchReturnsMapForAllKeys() {
            InstrumentKey tcsKey = new InstrumentKey("TCS", ExchangeSegment.NSE_EQ);
            InstrumentKey sbinKey = new InstrumentKey("SBIN", ExchangeSegment.NSE_EQ);

            Map<InstrumentKey, Long> batch = provider.getLtpBatch(
                    List.of(relianceKey, tcsKey, sbinKey));

            assertEquals(3, batch.size());
            assertTrue(batch.containsKey(relianceKey));
            assertTrue(batch.containsKey(tcsKey));
            assertTrue(batch.containsKey(sbinKey));
            batch.values().forEach(price -> assertTrue(price > 0, "Batch prices should be positive"));
        }

        @Test
        void getLtpBatchWithEmptyListReturnsEmptyMap() {
            Map<InstrumentKey, Long> batch = provider.getLtpBatch(List.of());
            assertTrue(batch.isEmpty());
        }

        // ── setBasePrice() ────────────────────────────────────────────────

        @Test
        void setBasePriceChangesSubsequentPrices() {
            long originalLtp = provider.getLtpPaisa(relianceKey);

            provider.setBasePrice("RELIANCE", 500_000L);
            long newLtp = provider.getLtpPaisa(relianceKey);

            // New LTP should be near 500_000 (within 0.5% jitter)
            assertTrue(newLtp >= 497_000L && newLtp <= 503_000L,
                    "New LTP should be near 500000, got: " + newLtp);
            assertNotEquals(originalLtp, newLtp,
                    "Price should change after setBasePrice");
        }

        @Test
        void setBasePriceForNewSymbolAddsIt() {
            InstrumentKey customKey = new InstrumentKey("CUSTOM", ExchangeSegment.NSE_EQ);
            provider.setBasePrice("CUSTOM", 42_000L);

            long ltp = provider.getLtpPaisa(customKey);
            assertTrue(ltp >= 41_500L && ltp <= 42_500L,
                    "LTP for CUSTOM should be near 42000, got: " + ltp);
        }

        // ── getOhlcSnapshot() ─────────────────────────────────────────────

        @Test
        void getOhlcSnapshotDelegatesToGetQuote() {
            Quote ohlc = provider.getOhlcSnapshot(relianceKey);
            assertNotNull(ohlc);
            assertTrue(ohlc.ltpPaisa() > 0);
        }

        // ── getQuoteBatch() ───────────────────────────────────────────────

        @Test
        void getQuoteBatchReturnsQuotesForAllKeys() {
            InstrumentKey tcsKey = new InstrumentKey("TCS", ExchangeSegment.NSE_EQ);
            Map<InstrumentKey, Quote> batch = provider.getQuoteBatch(List.of(relianceKey, tcsKey));

            assertEquals(2, batch.size());
            batch.values().forEach(quote -> assertTrue(quote.ltpPaisa() > 0));
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // SimulationPortfolioProvider tests
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    class PortfolioProviderTests {

        private SimulationPortfolioProvider portfolio;

        @BeforeEach
        void setUp() {
            portfolio = new SimulationPortfolioProvider(100_000_00L);
        }

        // ── getBalance() ──────────────────────────────────────────────────

        @Test
        void getBalanceReturnsCorrectCash() {
            Balance balance = portfolio.getBalance();
            assertNotNull(balance);
            assertEquals(100_000_00L, balance.cashPaisa());
            assertEquals(100_000_00L, balance.withdrawablePaisa());
        }

        @Test
        void getBalanceReturnsCorrectClientId() {
            Balance balance = portfolio.getBalance();
            assertEquals("PAPER", balance.clientId());
        }

        @Test
        void getBalanceReflectsDebitAndCredit() {
            portfolio.debitCash(10_000_00L);
            portfolio.creditCash(5_000_00L);

            Balance balance = portfolio.getBalance();
            assertEquals(95_000_00L, balance.cashPaisa());
        }

        // ── debitCash() ───────────────────────────────────────────────────

        @Test
        void debitCashReducesBalance() {
            portfolio.debitCash(25_000_00L);
            assertEquals(75_000_00L, portfolio.cashPaisa());
        }

        @Test
        void debitCashMultipleTimes() {
            portfolio.debitCash(10_000_00L);
            portfolio.debitCash(20_000_00L);
            assertEquals(70_000_00L, portfolio.cashPaisa());
        }

        // ── creditCash() ──────────────────────────────────────────────────

        @Test
        void creditCashIncreasesBalance() {
            portfolio.creditCash(50_000_00L);
            assertEquals(150_000_00L, portfolio.cashPaisa());
        }

        @Test
        void creditCashMultipleTimes() {
            portfolio.creditCash(10_000_00L);
            portfolio.creditCash(20_000_00L);
            assertEquals(130_000_00L, portfolio.cashPaisa());
        }

        // ── addPosition() ─────────────────────────────────────────────────

        @Test
        void addPositionAddsToPositionsList() {
            assertTrue(portfolio.getPositions().isEmpty());

            Position position = new Position(
                    "RELIANCE", ExchangeSegment.NSE_EQ,
                    Side.BUY, 10, 250_000L, 251_000L, 10_000L);
            portfolio.addPosition(position);

            List<Position> positions = portfolio.getPositions();
            assertEquals(1, positions.size());
            assertEquals("RELIANCE", positions.get(0).symbol());
            assertEquals(10, positions.get(0).quantity());
        }

        @Test
        void addMultiplePositions() {
            portfolio.addPosition(new Position(
                    "RELIANCE", ExchangeSegment.NSE_EQ,
                    Side.BUY, 10, 250_000L, 251_000L, 10_000L));
            portfolio.addPosition(new Position(
                    "TCS", ExchangeSegment.NSE_EQ,
                    Side.SELL, 5, 380_000L, 379_000L, 5_000L));

            assertEquals(2, portfolio.getPositions().size());
        }

        // ── removePosition() ──────────────────────────────────────────────

        @Test
        void removePositionRemovesBySymbol() {
            portfolio.addPosition(new Position(
                    "RELIANCE", ExchangeSegment.NSE_EQ,
                    Side.BUY, 10, 250_000L, 251_000L, 10_000L));
            portfolio.addPosition(new Position(
                    "TCS", ExchangeSegment.NSE_EQ,
                    Side.BUY, 5, 380_000L, 381_000L, 5_000L));

            portfolio.removePosition("RELIANCE");
            assertEquals(1, portfolio.getPositions().size());
            assertEquals("TCS", portfolio.getPositions().get(0).symbol());
        }

        // ── Default constructor ───────────────────────────────────────────

        @Test
        void defaultConstructorSetsOneLakhCash() {
            SimulationPortfolioProvider defaultPortfolio = new SimulationPortfolioProvider();
            assertEquals(100_000_00L, defaultPortfolio.cashPaisa());
        }

        // ── getPositions() returns copy ───────────────────────────────────

        @Test
        void getPositionsReturnsImmutableCopy() {
            portfolio.addPosition(new Position(
                    "RELIANCE", ExchangeSegment.NSE_EQ,
                    Side.BUY, 10, 250_000L, 251_000L, 10_000L));

            List<Position> positions = portfolio.getPositions();
            assertThrows(UnsupportedOperationException.class, () ->
                    positions.add(new Position("TCS", ExchangeSegment.NSE_EQ,
                            Side.BUY, 5, 380_000L, 381_000L, 5_000L)));
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // SimulatedWebSocketMultiplexer tests
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    class WebSocketMultiplexerTests {

        private SimulatedWebSocketMultiplexer multiplexer;

        @BeforeEach
        void setUp() {
            multiplexer = new SimulatedWebSocketMultiplexer();
        }

        // ── connect()/disconnect() toggle state ───────────────────────────

        @Test
        void initialStateIsDisconnected() {
            assertFalse(multiplexer.isConnected());
        }

        @Test
        void connectSetsConnectedState() {
            multiplexer.connect();
            assertTrue(multiplexer.isConnected());
        }

        @Test
        void disconnectSetsDisconnectedState() {
            multiplexer.connect();
            multiplexer.disconnect();
            assertFalse(multiplexer.isConnected());
        }

        @Test
        void connectDisconnectCanBeCalledMultipleTimes() {
            multiplexer.connect();
            assertTrue(multiplexer.isConnected());
            multiplexer.disconnect();
            assertFalse(multiplexer.isConnected());
            multiplexer.connect();
            assertTrue(multiplexer.isConnected());
        }

        // ── subscribe() tracks subscriptions ──────────────────────────────

        @Test
        void subscribeTracksInstruments() {
            MarketSubscriptionRequest sub1 = new MarketSubscriptionRequest(
                    "RELIANCE", ExchangeSegment.NSE_EQ);
            MarketSubscriptionRequest sub2 = new MarketSubscriptionRequest(
                    "TCS", ExchangeSegment.NSE_EQ);

            multiplexer.subscribe(List.of(sub1, sub2), FeedMode.FULL);

            Map<MarketSubscriptionRequest, FeedMode> subs = multiplexer.subscriptions();
            assertEquals(2, subs.size());
            assertEquals(FeedMode.FULL, subs.get(sub1));
            assertEquals(FeedMode.FULL, subs.get(sub2));
        }

        @Test
        void subscribeWithDifferentFeedModes() {
            MarketSubscriptionRequest sub1 = new MarketSubscriptionRequest(
                    "RELIANCE", ExchangeSegment.NSE_EQ);
            MarketSubscriptionRequest sub2 = new MarketSubscriptionRequest(
                    "TCS", ExchangeSegment.NSE_EQ);

            multiplexer.subscribe(List.of(sub1), FeedMode.FULL);
            multiplexer.subscribe(List.of(sub2), FeedMode.TICKER);

            Map<MarketSubscriptionRequest, FeedMode> subs = multiplexer.subscriptions();
            assertEquals(FeedMode.FULL, subs.get(sub1));
            assertEquals(FeedMode.TICKER, subs.get(sub2));
        }

        @Test
        void unsubscribeRemovesInstruments() {
            MarketSubscriptionRequest sub = new MarketSubscriptionRequest(
                    "RELIANCE", ExchangeSegment.NSE_EQ);
            multiplexer.subscribe(List.of(sub), FeedMode.FULL);
            assertEquals(1, multiplexer.subscriptions().size());

            multiplexer.unsubscribe(List.of(sub));
            assertTrue(multiplexer.subscriptions().isEmpty());
        }

        @Test
        void subscriptionsReturnsImmutableCopy() {
            MarketSubscriptionRequest sub = new MarketSubscriptionRequest(
                    "RELIANCE", ExchangeSegment.NSE_EQ);
            multiplexer.subscribe(List.of(sub), FeedMode.FULL);

            Map<MarketSubscriptionRequest, FeedMode> subs = multiplexer.subscriptions();
            assertThrows(UnsupportedOperationException.class, () ->
                    subs.put(new MarketSubscriptionRequest("TCS", ExchangeSegment.NSE_EQ), FeedMode.FULL));
        }

        @Test
        void subscriptionsIsEmptyInitially() {
            assertTrue(multiplexer.subscriptions().isEmpty());
        }

        @Test
        void subscribeWithEmptyListDoesNothing() {
            multiplexer.subscribe(List.of(), FeedMode.FULL);
            assertTrue(multiplexer.subscriptions().isEmpty());
        }
    }
}
