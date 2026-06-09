package com.tradej.broker.dhan.mapper;

import com.tradej.broker.dhan.instrument.DhanInstrumentDefinition;
import com.tradej.broker.dhan.websocket.feed.DhanMarketFeedPacket;
import com.tradej.core.domain.event.EventMetadataFactory;
import com.tradej.core.domain.event.MarketTickEvent;
import com.tradej.core.domain.time.TradingClock;
import com.tradej.core.domain.value.Exchange;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.FeedMode;
import com.tradej.core.domain.value.OptionType;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class DhanPayloadNormalizerTest {

    private static final TradingClock FIXED_CLOCK = new TradingClock() {
        private final Instant FIXED = Instant.parse("2024-06-15T10:00:00Z");

        @Override
        public Instant instant() {
            return FIXED;
        }

        @Override
        public LocalDateTime now() {
            return LocalDateTime.ofInstant(FIXED, ZoneId.of("Asia/Kolkata"));
        }
    };

    private static final EventMetadataFactory METADATA_FACTORY = new EventMetadataFactory(FIXED_CLOCK);

    private static final DhanInstrumentDefinition INDEX_DEF = new DhanInstrumentDefinition(
            "NIFTY", "NIFTY", Exchange.INDEX, ExchangeSegment.IDX_I, "13",
            "INDEX", null, null, null, null, 1L, 5L, null
    );

    private static final DhanInstrumentDefinition EQUITY_DEF = new DhanInstrumentDefinition(
            "TCS", "TCS-EQ", Exchange.NSE, ExchangeSegment.NSE_EQ, "11536",
            "EQUITY", "", null, null, null, 1L, 1L, null
    );

    private static final DhanInstrumentDefinition FNO_DEF = new DhanInstrumentDefinition(
            "NIFTY25JUN19000CE", "NIFTY25JUN19000CE", Exchange.NFO, ExchangeSegment.NSE_FNO, "11536",
            "OPTIDX", "NIFTY", null, null, OptionType.CALL, 75L, 5L, "13"
    );

    // ─── Index ────────────────────────────────────────────────────────────

    @Nested
    class IndexTests {

        @Test
        void nullIndexValue_returnsZeroPaisa() {
            DhanPayloadNormalizer normalizer = new DhanPayloadNormalizer(METADATA_FACTORY);
            var index = new DhanMarketFeedPacket.Index(
                    ExchangeSegment.IDX_I, "13", null, "24000.00", "24600.00", "23900.00", "24100.00", "0.50"
            );

            MarketTickEvent event = normalizer.normalizeFeedPacket(index, INDEX_DEF, FeedMode.QUOTE);

            assertNotNull(event);
            assertEquals("NIFTY", event.symbol());
            assertEquals(0L, event.ltpPaisa());
        }

        @Test
        void validIndexValue_returnsCorrectPaisa() {
            DhanPayloadNormalizer normalizer = new DhanPayloadNormalizer(METADATA_FACTORY);
            var index = new DhanMarketFeedPacket.Index(
                    ExchangeSegment.IDX_I, "13", "24500.50", "24000.00", "24600.00", "23900.00", "24100.00", "0.50"
            );

            MarketTickEvent event = normalizer.normalizeFeedPacket(index, INDEX_DEF, FeedMode.QUOTE);

            assertNotNull(event);
            assertEquals(2450050L, event.ltpPaisa());
            assertEquals("NIFTY", event.symbol());
            assertEquals(ExchangeSegment.IDX_I, event.segment());
        }

        @Test
        void preservesFeedMode() {
            DhanPayloadNormalizer normalizer = new DhanPayloadNormalizer(METADATA_FACTORY);
            var index = new DhanMarketFeedPacket.Index(
                    ExchangeSegment.IDX_I, "13", "24000.00", "24000.00", "24600.00", "23900.00", "24100.00", "0.50"
            );

            MarketTickEvent event = normalizer.normalizeFeedPacket(index, INDEX_DEF, FeedMode.TICKER);

            assertEquals(FeedMode.TICKER, event.feedMode());
        }
    }

    // ─── Ticker ───────────────────────────────────────────────────────────

    @Nested
    class TickerTests {

        @Test
        void mapsTickerToMarketTickEvent() {
            DhanPayloadNormalizer normalizer = new DhanPayloadNormalizer(METADATA_FACTORY);
            var ticker = new DhanMarketFeedPacket.Ticker(
                    ExchangeSegment.NSE_EQ, "11536", "1500.50", 1717000000L
            );

            MarketTickEvent event = normalizer.normalizeFeedPacket(ticker, EQUITY_DEF, FeedMode.TICKER);

            assertNotNull(event);
            assertEquals("TCS-EQ", event.symbol());
            assertEquals(ExchangeSegment.NSE_EQ, event.segment());
            assertEquals(150050L, event.ltpPaisa());
            assertEquals(0L, event.lastTradeQuantity());
            assertEquals(0L, event.cumulativeVolume());
            // ticker ltt is in seconds -> convert to ms
            assertEquals(1717000000000L, event.exchangeTimestampEpochMs());
            assertTrue(event.depth().isEmpty());
            assertEquals(FeedMode.TICKER, event.feedMode());
        }

        @Test
        void mapsTickerWithNullLtp_returnsZeroPaisa() {
            DhanPayloadNormalizer normalizer = new DhanPayloadNormalizer(METADATA_FACTORY);
            var ticker = new DhanMarketFeedPacket.Ticker(
                    ExchangeSegment.NSE_EQ, "11536", null, 1717000000L
            );

            MarketTickEvent event = normalizer.normalizeFeedPacket(ticker, EQUITY_DEF, FeedMode.TICKER);

            assertEquals(0L, event.ltpPaisa());
        }

        @Test
        void mapsTickerWithBlankLtp_returnsZeroPaisa() {
            DhanPayloadNormalizer normalizer = new DhanPayloadNormalizer(METADATA_FACTORY);
            var ticker = new DhanMarketFeedPacket.Ticker(
                    ExchangeSegment.NSE_EQ, "11536", "", 1717000000L
            );

            MarketTickEvent event = normalizer.normalizeFeedPacket(ticker, EQUITY_DEF, FeedMode.TICKER);

            assertEquals(0L, event.ltpPaisa());
        }
    }

    // ─── Quote ────────────────────────────────────────────────────────────

    @Nested
    class QuoteTests {

        @Test
        void mapsQuoteToMarketTickEvent() {
            DhanPayloadNormalizer normalizer = new DhanPayloadNormalizer(METADATA_FACTORY);
            var quote = new DhanMarketFeedPacket.Quote(
                    ExchangeSegment.NSE_EQ, "11536", "1500.50", 100, 1717000000L,
                    "1499.00", 50000L, 25000L, 15000L,
                    "1490.00", "1500.00", "1510.00", "1485.00"
            );

            MarketTickEvent event = normalizer.normalizeFeedPacket(quote, EQUITY_DEF, FeedMode.QUOTE);

            assertNotNull(event);
            assertEquals("TCS-EQ", event.symbol());
            assertEquals(150050L, event.ltpPaisa());
            assertEquals(100, event.lastTradeQuantity());
            assertEquals(50000L, event.cumulativeVolume());
            assertEquals(1717000000000L, event.exchangeTimestampEpochMs());
            assertTrue(event.depth().isEmpty());
            assertEquals(FeedMode.QUOTE, event.feedMode());
        }

        @Test
        void mapsQuoteWithNullLtp_returnsZeroPaisa() {
            DhanPayloadNormalizer normalizer = new DhanPayloadNormalizer(METADATA_FACTORY);
            var quote = new DhanMarketFeedPacket.Quote(
                    ExchangeSegment.NSE_EQ, "11536", null, 0, 1717000000L,
                    null, 0L, 0L, 0L,
                    null, null, null, null
            );

            MarketTickEvent event = normalizer.normalizeFeedPacket(quote, EQUITY_DEF, FeedMode.QUOTE);

            assertEquals(0L, event.ltpPaisa());
            assertEquals(0, event.lastTradeQuantity());
            assertEquals(0L, event.cumulativeVolume());
        }
    }

    // ─── Full (with depth) ────────────────────────────────────────────────

    @Nested
    class FullTests {

        @Test
        void mapsFullToMarketTickEventWithDepth() {
            DhanPayloadNormalizer normalizer = new DhanPayloadNormalizer(METADATA_FACTORY);
            var bids = java.util.List.of(
                    new DhanMarketFeedPacket.DepthLevel("1500.00", 1000, 5),
                    new DhanMarketFeedPacket.DepthLevel("1499.00", 500, 3)
            );
            var asks = java.util.List.of(
                    new DhanMarketFeedPacket.DepthLevel("1501.00", 800, 4)
            );
            var full = new DhanMarketFeedPacket.Full(
                    ExchangeSegment.NSE_EQ, "11536", "1500.50", 100, 1717000000L,
                    "1499.00", 50000L, 25000L, 15000L,
                    10000L, 12000L, 8000L,
                    "1490.00", "1500.00", "1510.00", "1485.00",
                    bids, asks
            );

            MarketTickEvent event = normalizer.normalizeFeedPacket(full, EQUITY_DEF, FeedMode.FULL);

            assertNotNull(event);
            assertEquals("TCS-EQ", event.symbol());
            assertEquals(150050L, event.ltpPaisa());
            assertEquals(100, event.lastTradeQuantity());
            assertEquals(50000L, event.cumulativeVolume());
            assertEquals(10000L, event.openInterest());
            assertEquals(10000L, event.oiForTheDay());

            assertTrue(event.depth().isPresent());
            var depth = event.depth().get();
            assertEquals(2, depth.bids().size());
            assertEquals(1, depth.asks().size());
            assertEquals(150000L, depth.bids().get(0).pricePaisa());
            assertEquals(1000L, depth.bids().get(0).quantity());
            assertEquals(5, depth.bids().get(0).orderCount());
            assertEquals(150100L, depth.asks().get(0).pricePaisa());
        }

        @Test
        void mapsFullWithEmptyDepth() {
            DhanPayloadNormalizer normalizer = new DhanPayloadNormalizer(METADATA_FACTORY);
            var full = new DhanMarketFeedPacket.Full(
                    ExchangeSegment.NSE_EQ, "11536", "1500.50", 100, 1717000000L,
                    "1499.00", 50000L, 25000L, 15000L,
                    10000L, 12000L, 8000L,
                    "1490.00", "1500.00", "1510.00", "1485.00",
                    java.util.List.of(), java.util.List.of()
            );

            MarketTickEvent event = normalizer.normalizeFeedPacket(full, EQUITY_DEF, FeedMode.FULL);

            assertTrue(event.depth().isPresent());
            assertTrue(event.depth().get().bids().isEmpty());
            assertTrue(event.depth().get().asks().isEmpty());
        }
    }

    // ─── Oi ───────────────────────────────────────────────────────────────

    @Nested
    class OiTests {

        @Test
        void returnsEventWithOI() {
            DhanPayloadNormalizer normalizer = new DhanPayloadNormalizer(METADATA_FACTORY);
            var oi = new DhanMarketFeedPacket.Oi(
                    ExchangeSegment.NSE_FNO, "11536", 75_000L
            );

            MarketTickEvent event = normalizer.normalizeFeedPacket(oi, FNO_DEF, FeedMode.FULL);

            assertNotNull(event);
            assertEquals(0L, event.ltpPaisa());
            assertEquals(75_000L, event.openInterest());
            assertEquals(75_000L, event.oiForTheDay());
            assertEquals("NIFTY25JUN19000CE", event.symbol());
        }

        @Test
        void returnsEventWithZeroOI() {
            DhanPayloadNormalizer normalizer = new DhanPayloadNormalizer(METADATA_FACTORY);
            var oi = new DhanMarketFeedPacket.Oi(
                    ExchangeSegment.NSE_FNO, "11536", 0L
            );

            MarketTickEvent event = normalizer.normalizeFeedPacket(oi, FNO_DEF, FeedMode.FULL);

            assertEquals(0L, event.openInterest());
            assertEquals(0L, event.oiForTheDay());
        }
    }

    // ─── Heartbeat ────────────────────────────────────────────────────────

    @Nested
    class HeartbeatTests {

        @Test
        void returnsNull() {
            DhanPayloadNormalizer normalizer = new DhanPayloadNormalizer(METADATA_FACTORY);
            var heartbeat = new DhanMarketFeedPacket.Heartbeat();

            MarketTickEvent event = normalizer.normalizeFeedPacket(heartbeat, EQUITY_DEF, FeedMode.TICKER);

            assertNull(event);
        }
    }

    // ─── MarketStatus ─────────────────────────────────────────────────────

    @Nested
    class MarketStatusTests {

        @Test
        void returnsNull() {
            DhanPayloadNormalizer normalizer = new DhanPayloadNormalizer(METADATA_FACTORY);
            var marketStatus = new DhanMarketFeedPacket.MarketStatus(
                    ExchangeSegment.NSE_EQ, "11536", 1
            );

            MarketTickEvent event = normalizer.normalizeFeedPacket(marketStatus, EQUITY_DEF, FeedMode.FULL);

            assertNull(event);
        }

        @Test
        void returnsNullRegardlessOfStatus() {
            DhanPayloadNormalizer normalizer = new DhanPayloadNormalizer(METADATA_FACTORY);
            var marketStatus = new DhanMarketFeedPacket.MarketStatus(
                    ExchangeSegment.NSE_EQ, "11536", 0
            );

            MarketTickEvent event = normalizer.normalizeFeedPacket(marketStatus, EQUITY_DEF, FeedMode.FULL);

            assertNull(event);
        }
    }

    // ─── PrevClose ────────────────────────────────────────────────────────

    @Nested
    class PrevCloseTests {

        @Test
        void returnsNull() {
            DhanPayloadNormalizer normalizer = new DhanPayloadNormalizer(METADATA_FACTORY);
            var prevClose = new DhanMarketFeedPacket.PrevClose(
                    ExchangeSegment.NSE_EQ, "11536", "1500.00", "10000"
            );

            MarketTickEvent event = normalizer.normalizeFeedPacket(prevClose, EQUITY_DEF, FeedMode.FULL);

            assertNull(event);
        }

        @Test
        void returnsNullWithNullValues() {
            DhanPayloadNormalizer normalizer = new DhanPayloadNormalizer(METADATA_FACTORY);
            var prevClose = new DhanMarketFeedPacket.PrevClose(
                    ExchangeSegment.NSE_EQ, "11536", null, null
            );

            MarketTickEvent event = normalizer.normalizeFeedPacket(prevClose, EQUITY_DEF, FeedMode.FULL);

            assertNull(event);
        }
    }

    // ─── toEpochMs ────────────────────────────────────────────────────────

    @Nested
    class ToEpochMsTests {

        @Test
        void passesThroughLargeTimestampsUnchanged() {
            long millis = 1_717_000_000_000L; // > 1e12 — already in ms

            long result = DhanPayloadNormalizer.toEpochMs(millis);

            assertEquals(1_717_000_000_000L, result);
        }

        @Test
        void convertsSecondsToMilliseconds() {
            long seconds = 1_717_000_000L; // <= 1e12

            long result = DhanPayloadNormalizer.toEpochMs(seconds);

            assertEquals(1_717_000_000_000L, result);
        }

        @Test
        void treatsZeroAsSeconds() {
            long result = DhanPayloadNormalizer.toEpochMs(0L);

            assertEquals(0L, result);
        }

        @Test
        void treatsNegativeAsSecondsMultipliedBy1000() {
            long result = DhanPayloadNormalizer.toEpochMs(-1L);

            assertEquals(-1000L, result);
        }

        @Test
        void boundary_exactThresholdMultipliesBy1000() {
            // Exactly 1e12 is NOT > 1e12, so it gets multiplied
            long result = DhanPayloadNormalizer.toEpochMs(1_000_000_000_000L);
            assertEquals(1_000_000_000_000_000L, result);
        }

        @Test
        void boundary_oneAboveThresholdPassesThrough() {
            long result = DhanPayloadNormalizer.toEpochMs(1_000_000_000_001L);
            assertEquals(1_000_000_000_001L, result);
        }
    }
}
