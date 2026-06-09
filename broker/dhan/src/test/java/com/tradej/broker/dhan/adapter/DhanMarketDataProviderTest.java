package com.tradej.broker.dhan.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tradej.broker.dhan.constants.DhanApiUrlResolver;
import com.tradej.broker.dhan.historical.DhanHistoricalDataClient;
import com.tradej.broker.dhan.historical.DhanHistoricalDataMapper;
import com.tradej.broker.dhan.http.DhanAuthenticatedHttpClient;
import com.tradej.broker.dhan.instrument.DhanInstrumentDefinition;
import com.tradej.broker.dhan.mapper.DhanJsonResponse;
import com.tradej.broker.dhan.mapper.DhanJsonMapper;
import com.tradej.broker.dhan.rate.ApiCategory;
import com.tradej.core.domain.model.Candle;
import com.tradej.core.domain.model.CandleHistoryRequest;
import com.tradej.core.domain.model.Instrument;
import com.tradej.core.domain.model.InstrumentKey;
import com.tradej.core.domain.model.MarketDepth;
import com.tradej.core.domain.model.Quote;
import com.tradej.core.domain.value.Exchange;
import com.tradej.core.domain.value.ExchangeSegment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class DhanMarketDataProviderTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String SECURITY_ID = "3045";
    private static final String NSE_SEGMENT_WIRE = "NSE_EQ";
    private static final String LTP_URL = "https://api.dhan.co/v2/marketfeed/ltp";
    private static final String QUOTE_URL = "https://api.dhan.co/v2/marketfeed/quote";

    @Mock private DhanAdapterContext context;
    @Mock private DhanHistoricalDataClient historicalDataClient;
    @Mock private DhanHistoricalDataMapper historicalDataMapper;
    @Mock private DhanAuthenticatedHttpClient httpClient;
    @Mock private DhanApiUrlResolver apiUrlResolver;

    @Captor private ArgumentCaptor<ObjectNode> requestBodyCaptor;

    private DhanMarketDataProvider provider;
    private DhanInstrumentDefinition def;
    private InstrumentKey key;
    private Instrument instrument;

    @BeforeEach
    void setUp() {
        provider = new DhanMarketDataProvider(
                context, historicalDataClient, historicalDataMapper, httpClient, apiUrlResolver
        );

        key = new InstrumentKey("RELIANCE", ExchangeSegment.NSE_EQ);
        def = new DhanInstrumentDefinition(
                "RELIANCE", "RELIANCE", Exchange.NSE, ExchangeSegment.NSE_EQ,
                SECURITY_ID, "EQ", "RELIANCE", null, null, null, 1L, 5L, SECURITY_ID
        );
        instrument = def.toInstrument();
    }

    // ── Helper to make context.execute() run the supplier ──────────────

    @SuppressWarnings("unchecked")
    private <T> void stubExecuteReturns(T result) {
        when(context.execute(any(ApiCategory.class), anyString(), any(Supplier.class)))
                .thenAnswer(invocation -> {
                    Supplier<T> supplier = invocation.getArgument(2);
                    return supplier.get();
                });
    }

    @SuppressWarnings("unchecked")
    private <T> void stubExecuteThrows(ApiCategory category, String operation, RuntimeException ex) {
        when(context.execute(eq(category), eq(operation), any(Supplier.class)))
                .thenThrow(ex);
    }

    // ── Single-instrument helpers ─────────────────────────────────────

    private ObjectNode singleQuoteResponse() {
        ObjectNode root = MAPPER.createObjectNode();
        ObjectNode segment = MAPPER.createObjectNode();
        ObjectNode security = MAPPER.createObjectNode();
        security.put("last_price", "2450.00");
        security.put("lastPrice", "2450.00");
        security.put("ltp", "2450.00");
        security.put("volume", 50000);
        security.put("buy_quantity", 25000);
        security.put("sell_quantity", 25000);
        security.put("oi", 100000);
        security.put("last_trade_time", 1700000000000L);
        ObjectNode ohlc = MAPPER.createObjectNode();
        ohlc.put("open", "2400.00");
        ohlc.put("high", "2475.00");
        ohlc.put("low", "2390.00");
        ohlc.put("close", "2410.00");
        security.set("ohlc", ohlc);
        segment.set(SECURITY_ID, security);
        root.set(NSE_SEGMENT_WIRE, segment);
        return root;
    }

    private ObjectNode ltpResponse() {
        ObjectNode root = MAPPER.createObjectNode();
        ObjectNode segment = MAPPER.createObjectNode();
        ObjectNode security = MAPPER.createObjectNode();
        security.put("last_price", "2450.00");
        segment.set(SECURITY_ID, security);
        root.set(NSE_SEGMENT_WIRE, segment);
        return root;
    }

    // ── Initial state & constructor ───────────────────────────────────

    @Nested
    class InitialStateTests {

        @Test
        void capabilitiesReturnsDhanDefaults() {
            var caps = provider.capabilities();
            assertNotNull(caps);
            assertTrue(caps.supportedIntervals().contains("1m"));
            assertFalse(caps.supportsSecondHistorical());
            assertEquals(90, caps.maxIntradayDaysPerRequest());
        }
    }

    // ── getLtpPaisa ───────────────────────────────────────────────────

    @Nested
    class GetLtpPaisaTests {

        @Test
        void returnsPriceForValidInstrument() {
            when(context.resolveDef(key)).thenReturn(def);
            when(apiUrlResolver.marketFeedLtpUrl()).thenReturn(LTP_URL);
            when(httpClient.postJson(eq(LTP_URL), any(ObjectNode.class)))
                    .thenReturn(new DhanJsonResponse(ltpResponse()));
            stubExecuteReturns(245000L);

            long ltp = provider.getLtpPaisa(key);

            assertEquals(245000L, ltp);
            verify(context).execute(eq(ApiCategory.QUOTE), eq("quote-ltp"), any());
        }

        @Test
        void resolvesInstrumentKey() {
            when(context.resolveDef(key)).thenReturn(def);
            when(apiUrlResolver.marketFeedLtpUrl()).thenReturn(LTP_URL);
            when(httpClient.postJson(eq(LTP_URL), any(ObjectNode.class)))
                    .thenReturn(new DhanJsonResponse(ltpResponse()));
            stubExecuteReturns(245000L);

            provider.getLtpPaisa(key);

            verify(context).resolveDef(key);
        }
    }

    // ── getQuote ──────────────────────────────────────────────────────

    @Nested
    class GetQuoteTests {

        @Test
        void returnsQuoteForValidInstrument() {
            when(context.resolveDef(key)).thenReturn(def);
            when(apiUrlResolver.marketFeedQuoteUrl()).thenReturn(QUOTE_URL);
            ObjectNode response = singleQuoteResponse();
            when(httpClient.postJson(eq(QUOTE_URL), any(ObjectNode.class)))
                    .thenReturn(new DhanJsonResponse(response));
            stubExecuteReturns(DhanJsonMapper.toQuote(
                    new DhanJsonResponse(response.path(NSE_SEGMENT_WIRE).path(SECURITY_ID)),
                    instrument));

            Quote quote = provider.getQuote(key);

            assertNotNull(quote);
            assertEquals(instrument, quote.instrument());
            verify(context).execute(eq(ApiCategory.QUOTE), eq("quote-snapshot"), any());
        }

        @Test
        void usesCorrectApiCategory() {
            when(context.resolveDef(key)).thenReturn(def);
            when(apiUrlResolver.marketFeedQuoteUrl()).thenReturn(QUOTE_URL);
            when(httpClient.postJson(eq(QUOTE_URL), any(ObjectNode.class)))
                    .thenReturn(new DhanJsonResponse(singleQuoteResponse()));
            stubExecuteReturns(DhanJsonMapper.toQuote(
                    new DhanJsonResponse(singleQuoteResponse().path(NSE_SEGMENT_WIRE).path(SECURITY_ID)),
                    instrument));

            provider.getQuote(key);

            verify(context).execute(eq(ApiCategory.QUOTE), eq("quote-snapshot"), any());
        }
    }

    // ── getDepth ──────────────────────────────────────────────────────

    @Nested
    class GetDepthTests {

        @Test
        void returnsDepthForValidInstrument() {
            when(context.resolveDef(key)).thenReturn(def);
            when(apiUrlResolver.marketFeedQuoteUrl()).thenReturn(QUOTE_URL);
            ObjectNode response = singleQuoteResponse();
            // Add depth data
            ObjectNode depth = MAPPER.createObjectNode();
            depth.set("buy", MAPPER.createArrayNode());
            depth.set("sell", MAPPER.createArrayNode());
            ((ObjectNode) response.path(NSE_SEGMENT_WIRE).path(SECURITY_ID)).set("depth", depth);

            when(httpClient.postJson(eq(QUOTE_URL), any(ObjectNode.class)))
                    .thenReturn(new DhanJsonResponse(response));
            stubExecuteReturns(DhanJsonMapper.toDepth(
                    new DhanJsonResponse(response.path(NSE_SEGMENT_WIRE).path(SECURITY_ID)),
                    instrument));

            MarketDepth depthResult = provider.getDepth(key);

            assertNotNull(depthResult);
            assertEquals(instrument, depthResult.instrument());
            verify(context).execute(eq(ApiCategory.QUOTE), eq("quote-depth"), any());
        }

        @Test
        void depthWithoutDepthFieldReturnsEmptyDepth() {
            when(context.resolveDef(key)).thenReturn(def);
            when(apiUrlResolver.marketFeedQuoteUrl()).thenReturn(QUOTE_URL);
            ObjectNode response = singleQuoteResponse(); // No depth field
            when(httpClient.postJson(eq(QUOTE_URL), any(ObjectNode.class)))
                    .thenReturn(new DhanJsonResponse(response));
            stubExecuteReturns(DhanJsonMapper.toDepth(
                    new DhanJsonResponse(response.path(NSE_SEGMENT_WIRE).path(SECURITY_ID)),
                    instrument));

            MarketDepth depthResult = provider.getDepth(key);

            assertNotNull(depthResult);
            assertTrue(depthResult.bids().isEmpty());
            assertTrue(depthResult.asks().isEmpty());
        }
    }

    // ── getOhlcSnapshot ───────────────────────────────────────────────

    @Nested
    class GetOhlcSnapshotTests {

        @Test
        void delegatesToGetQuote() {
            when(context.resolveDef(key)).thenReturn(def);
            when(apiUrlResolver.marketFeedQuoteUrl()).thenReturn(QUOTE_URL);
            ObjectNode response = singleQuoteResponse();
            when(httpClient.postJson(eq(QUOTE_URL), any(ObjectNode.class)))
                    .thenReturn(new DhanJsonResponse(response));
            stubExecuteReturns(DhanJsonMapper.toQuote(
                    new DhanJsonResponse(response.path(NSE_SEGMENT_WIRE).path(SECURITY_ID)),
                    instrument));

            Quote ohlc = provider.getOhlcSnapshot(key);

            assertNotNull(ohlc);
            // Should have called the same path as getQuote
            verify(context).execute(eq(ApiCategory.QUOTE), eq("quote-snapshot"), any());
        }
    }

    // ── getCandles ────────────────────────────────────────────────────

    @Nested
    class GetCandlesTests {

        private CandleHistoryRequest request;
        private List<Candle> candlesFromClient;

        @BeforeEach
        void setUp() {
            request = new CandleHistoryRequest(key, "1d", 1000000L, 2000000L);
            candlesFromClient = List.of(
                    new Candle("RELIANCE", "1d", 1000000L, 1100000L, 100L, 110L, 99L, 105L, 1000L, true),
                    new Candle("RELIANCE", "1d", 1100000L, 1200000L, 110L, 120L, 109L, 115L, 1500L, true)
            );
        }

        @Test
        void fetchesAndMapsCandles() {
            when(context.resolveDef(request.instrument())).thenReturn(def);
            CandleHistoryRequest capturedRequest = request; // effectively final
            when(historicalDataClient.fetchRange(eq(capturedRequest), eq(def)))
                    .thenReturn(List.of(new DhanJsonResponse(MAPPER.createObjectNode())));
            when(historicalDataMapper.toCandles(any(JsonNode.class), eq(instrument), eq("1d")))
                    .thenReturn(candlesFromClient);

            List<Candle> result = provider.getCandles(request);

            assertEquals(2, result.size());
            assertEquals(1000000L, result.get(0).startTimeMs());
            assertEquals(1100000L, result.get(1).startTimeMs());
        }

        @Test
        void mergesDuplicatesFromMultiplePages() {
            when(context.resolveDef(request.instrument())).thenReturn(def);
            when(historicalDataClient.fetchRange(eq(request), eq(def)))
                    .thenReturn(List.of(
                            new DhanJsonResponse(MAPPER.createObjectNode()),
                            new DhanJsonResponse(MAPPER.createObjectNode())));
            // Return overlapping candles from each page
            Candle candle1 = new Candle("RELIANCE", "1d", 1000000L, 1100000L, 100L, 110L, 99L, 105L, 1000L, true);
            Candle candle2 = new Candle("RELIANCE", "1d", 1100000L, 1200000L, 110L, 120L, 109L, 115L, 1500L, true);
            // Return candle1 from first page, candle1+candle2 from second page (overlap)
            when(historicalDataMapper.toCandles(any(JsonNode.class), eq(instrument), eq("1d")))
                    .thenReturn(List.of(candle1))
                    .thenReturn(List.of(candle1, candle2));

            List<Candle> result = provider.getCandles(request);

            assertEquals(2, result.size(), "Should deduplicate overlapping candle");
        }

        @Test
        void returnsEmptyListForNoCandles() {
            when(context.resolveDef(request.instrument())).thenReturn(def);
            when(historicalDataClient.fetchRange(eq(request), eq(def)))
                    .thenReturn(List.of(new DhanJsonResponse(MAPPER.createObjectNode())));
            when(historicalDataMapper.toCandles(any(JsonNode.class), eq(instrument), eq("1d")))
                    .thenReturn(List.of());

            List<Candle> result = provider.getCandles(request);

            assertTrue(result.isEmpty());
        }
    }

    // ── Batch operations ──────────────────────────────────────────────

    @Nested
    class BatchOperationTests {

        private InstrumentKey key2;
        private DhanInstrumentDefinition def2;
        private static final String SECURITY_ID_2 = "11536";

        @BeforeEach
        void setUp() {
            key2 = new InstrumentKey("TCS", ExchangeSegment.NSE_EQ);
            def2 = new DhanInstrumentDefinition(
                    "TCS", "TCS", Exchange.NSE, ExchangeSegment.NSE_EQ,
                    SECURITY_ID_2, "EQ", "TCS", null, null, null, 1L, 5L, SECURITY_ID_2
            );
        }

        @Test
        void getLtpBatchReturnsEmptyForNullInput() {
            Map<InstrumentKey, Long> result = provider.getLtpBatch(null);

            assertTrue(result.isEmpty());
            verifyNoInteractions(httpClient, context);
        }

        @Test
        void getLtpBatchReturnsEmptyForEmptyInput() {
            Map<InstrumentKey, Long> result = provider.getLtpBatch(List.of());

            assertTrue(result.isEmpty());
            verifyNoInteractions(httpClient);
        }

        @Test
        void getLtpBatchReturnsPricesForMultipleInstruments() {
            when(context.resolveDef(key)).thenReturn(def);
            when(context.resolveDef(key2)).thenReturn(def2);
            when(apiUrlResolver.marketFeedLtpUrl()).thenReturn(LTP_URL);

            ObjectNode response = MAPPER.createObjectNode();
            ObjectNode segment = MAPPER.createObjectNode();
            ObjectNode s1 = MAPPER.createObjectNode();
            s1.put("last_price", "2450.00");
            ObjectNode s2 = MAPPER.createObjectNode();
            s2.put("last_price", "4200.00");
            segment.set(SECURITY_ID, s1);
            segment.set(SECURITY_ID_2, s2);
            response.set(NSE_SEGMENT_WIRE, segment);
            DhanJsonResponse dhanResponse = new DhanJsonResponse(response);
            when(httpClient.postJson(eq(LTP_URL), any(ObjectNode.class)))
                    .thenReturn(dhanResponse);

            stubExecuteReturns(dhanResponse);

            Map<InstrumentKey, Long> result = provider.getLtpBatch(List.of(key, key2));

            assertEquals(2, result.size());
            assertTrue(result.containsKey(key));
            assertTrue(result.containsKey(key2));
        }

        @Test
        void getLtpBatchContinuesOnIndividualExtractionFailure() {
            when(context.resolveDef(key)).thenReturn(def);
            when(context.resolveDef(key2)).thenReturn(def2);
            when(apiUrlResolver.marketFeedLtpUrl()).thenReturn(LTP_URL);

            ObjectNode response = MAPPER.createObjectNode();
            ObjectNode segment = MAPPER.createObjectNode();
            ObjectNode s1 = MAPPER.createObjectNode();
            s1.put("last_price", "2450.00");
            segment.set(SECURITY_ID, s1);
            // SECURITY_ID_2 is missing from response — will cause extraction failure
            response.set(NSE_SEGMENT_WIRE, segment);

            when(httpClient.postJson(eq(LTP_URL), any(ObjectNode.class)))
                    .thenReturn(new DhanJsonResponse(response));
            stubExecuteReturns(new DhanJsonResponse(response));

            Map<InstrumentKey, Long> result = provider.getLtpBatch(List.of(key, key2));

            // key should have its price, key2 should be absent due to extraction failure
            assertTrue(result.containsKey(key), "First instrument should succeed");
            assertEquals(1, result.size(), "Second instrument should be skipped on failure");
        }

        @Test
        void getLtpBatchBuildsRequestBodyWithSegmentGrouping() {
            when(context.resolveDef(key)).thenReturn(def);
            when(apiUrlResolver.marketFeedLtpUrl()).thenReturn(LTP_URL);

            ObjectNode response = MAPPER.createObjectNode();
            ObjectNode segment = MAPPER.createObjectNode();
            ObjectNode s1 = MAPPER.createObjectNode();
            s1.put("last_price", "2450.00");
            segment.set(SECURITY_ID, s1);
            response.set(NSE_SEGMENT_WIRE, segment);

            when(httpClient.postJson(eq(LTP_URL), requestBodyCaptor.capture()))
                    .thenReturn(new DhanJsonResponse(response));
            stubExecuteReturns(new DhanJsonResponse(response));

            provider.getLtpBatch(List.of(key));

            ObjectNode body = requestBodyCaptor.getValue();
            assertTrue(body.has(NSE_SEGMENT_WIRE));
            assertTrue(body.get(NSE_SEGMENT_WIRE).get(0).isNumber());
            assertEquals(3045L, body.get(NSE_SEGMENT_WIRE).get(0).asLong());
        }

        @Test
        void getQuoteBatchReturnsQuotesForMultipleInstruments() {
            when(context.resolveDef(key)).thenReturn(def);
            when(context.resolveDef(key2)).thenReturn(def2);
            when(apiUrlResolver.marketFeedQuoteUrl()).thenReturn(QUOTE_URL);

            ObjectNode response = MAPPER.createObjectNode();
            ObjectNode segment = MAPPER.createObjectNode();
            ObjectNode s1 = MAPPER.createObjectNode();
            s1.put("last_price", "2450.00");
            s1.put("volume", 50000);
            s1.put("last_trade_time", 1700000000000L);
            ObjectNode ohlc1 = MAPPER.createObjectNode();
            ohlc1.put("open", "2400.00");
            ohlc1.put("high", "2475.00");
            ohlc1.put("low", "2390.00");
            ohlc1.put("close", "2410.00");
            s1.set("ohlc", ohlc1);
            ObjectNode s2 = MAPPER.createObjectNode();
            s2.put("last_price", "4200.00");
            s2.put("volume", 30000);
            s2.put("last_trade_time", 1700000000000L);
            ObjectNode ohlc2 = MAPPER.createObjectNode();
            ohlc2.put("open", "4150.00");
            ohlc2.put("high", "4220.00");
            ohlc2.put("low", "4140.00");
            ohlc2.put("close", "4160.00");
            s2.set("ohlc", ohlc2);
            segment.set(SECURITY_ID, s1);
            segment.set(SECURITY_ID_2, s2);
            response.set(NSE_SEGMENT_WIRE, segment);
            DhanJsonResponse dhanResponse = new DhanJsonResponse(response);

            when(httpClient.postJson(eq(QUOTE_URL), any(ObjectNode.class)))
                    .thenReturn(dhanResponse);
            stubExecuteReturns(dhanResponse);

            Map<InstrumentKey, Quote> result = provider.getQuoteBatch(List.of(key, key2));

            assertEquals(2, result.size());
            assertTrue(result.containsKey(key));
            assertTrue(result.containsKey(key2));
        }

        @Test
        void getQuoteBatchReturnsEmptyForNullInput() {
            Map<InstrumentKey, Quote> result = provider.getQuoteBatch(null);

            assertTrue(result.isEmpty());
            verifyNoInteractions(httpClient);
        }

        @Test
        void getQuoteBatchReturnsEmptyForEmptyInput() {
            Map<InstrumentKey, Quote> result = provider.getQuoteBatch(List.of());

            assertTrue(result.isEmpty());
            verifyNoInteractions(httpClient);
        }

        @Test
        void getOhlcBatchDelegatesToGetQuoteBatch() {
            when(context.resolveDef(key)).thenReturn(def);
            when(apiUrlResolver.marketFeedQuoteUrl()).thenReturn(QUOTE_URL);

            ObjectNode response = MAPPER.createObjectNode();
            ObjectNode segment = MAPPER.createObjectNode();
            ObjectNode s1 = MAPPER.createObjectNode();
            s1.put("last_price", "2450.00");
            s1.put("volume", 50000);
            s1.put("last_trade_time", 1700000000000L);
            ObjectNode ohlc = MAPPER.createObjectNode();
            ohlc.put("open", "2400.00");
            ohlc.put("high", "2475.00");
            ohlc.put("low", "2390.00");
            ohlc.put("close", "2410.00");
            s1.set("ohlc", ohlc);
            segment.set(SECURITY_ID, s1);
            response.set(NSE_SEGMENT_WIRE, segment);

            when(httpClient.postJson(eq(QUOTE_URL), any(ObjectNode.class)))
                    .thenReturn(new DhanJsonResponse(response));
            stubExecuteReturns(new DhanJsonResponse(response));

            Map<InstrumentKey, Quote> result = provider.getOhlcBatch(List.of(key));

            assertEquals(1, result.size());
            assertTrue(result.containsKey(key));
        }
    }

    // ── Error handling ────────────────────────────────────────────────

    @Nested
    class ErrorHandlingTests {

        @Test
        void getLtpPaisaThrowsWhenInstrumentNotFound() {
            when(context.resolveDef(key)).thenThrow(new IllegalArgumentException("Unknown symbol: RELIANCE"));

            assertThrows(IllegalArgumentException.class, () -> provider.getLtpPaisa(key));
        }

        @Test
        void getQuoteThrowsWhenInstrumentNotFound() {
            when(context.resolveDef(key)).thenThrow(new IllegalArgumentException("Unknown symbol: RELIANCE"));

            assertThrows(IllegalArgumentException.class, () -> provider.getQuote(key));
        }

        @Test
        void getCandlesThrowsOnInvalidInterval() {
            CandleHistoryRequest badRequest = new CandleHistoryRequest(key, "bad-interval", 1000L, 2000L);

            assertThrows(IllegalArgumentException.class, () -> provider.getCandles(badRequest));
        }
    }

    // ── Private method tests (via reflection) ─────────────────────────

    @Nested
    class SecurityIdParsingTests {

        @Test
        void parseSecurityIdParsesValidId() throws Exception {
            var method = DhanMarketDataProvider.class.getDeclaredMethod("parseSecurityId", String.class);
            method.setAccessible(true);

            long result = (long) method.invoke(null, "3045");

            assertEquals(3045L, result);
        }

        @Test
        void parseSecurityIdTrimsWhitespace() throws Exception {
            var method = DhanMarketDataProvider.class.getDeclaredMethod("parseSecurityId", String.class);
            method.setAccessible(true);

            long result = (long) method.invoke(null, "  3045  ");

            assertEquals(3045L, result);
        }

        @Test
        void parseSecurityIdThrowsForBlankInput() throws Exception {
            var method = DhanMarketDataProvider.class.getDeclaredMethod("parseSecurityId", String.class);
            method.setAccessible(true);

            var ex = assertThrows(InvocationTargetException.class,
                    () -> method.invoke(null, ""));
            assertInstanceOf(IllegalArgumentException.class, ex.getCause());
        }

        @Test
        void parseSecurityIdThrowsForNonNumericInput() throws Exception {
            var method = DhanMarketDataProvider.class.getDeclaredMethod("parseSecurityId", String.class);
            method.setAccessible(true);

            var ex = assertThrows(InvocationTargetException.class,
                    () -> method.invoke(null, "ABC"));
            assertInstanceOf(IllegalArgumentException.class, ex.getCause());
        }
    }

    @Nested
    class ExtractQuotePayloadTests {

        @Test
        void extractsFromRootSegment() throws Exception {
            var method = DhanMarketDataProvider.class.getDeclaredMethod(
                    "extractQuotePayload", DhanJsonResponse.class, DhanInstrumentDefinition.class);
            method.setAccessible(true);

            ObjectNode root = MAPPER.createObjectNode();
            ObjectNode segment = MAPPER.createObjectNode();
            ObjectNode security = MAPPER.createObjectNode();
            security.put("last_price", "2450.00");
            segment.set(SECURITY_ID, security);
            root.set(NSE_SEGMENT_WIRE, segment);

            DhanJsonResponse result = (DhanJsonResponse) method.invoke(
                    provider, new DhanJsonResponse(root), def);

            assertNotNull(result);
            assertFalse(result.isMissingNode());
            assertEquals(245000L, result.decimalPrice("last_price"));
        }

        @Test
        void extractsFromDataEnvelope() throws Exception {
            var method = DhanMarketDataProvider.class.getDeclaredMethod(
                    "extractQuotePayload", DhanJsonResponse.class, DhanInstrumentDefinition.class);
            method.setAccessible(true);

            ObjectNode root = MAPPER.createObjectNode();
            ObjectNode data = MAPPER.createObjectNode();
            ObjectNode segment = MAPPER.createObjectNode();
            ObjectNode security = MAPPER.createObjectNode();
            security.put("last_price", "2450.00");
            segment.set(SECURITY_ID, security);
            data.set(NSE_SEGMENT_WIRE, segment);
            root.set("data", data);

            DhanJsonResponse result = (DhanJsonResponse) method.invoke(
                    provider, new DhanJsonResponse(root), def);

            assertNotNull(result);
            assertFalse(result.isMissingNode());
            assertEquals(245000L, result.decimalPrice("last_price"));
        }

        @Test
        void prefersDataEnvelopeOverRoot() throws Exception {
            var method = DhanMarketDataProvider.class.getDeclaredMethod(
                    "extractQuotePayload", DhanJsonResponse.class, DhanInstrumentDefinition.class);
            method.setAccessible(true);

            ObjectNode root = MAPPER.createObjectNode();
            // Root level — different price
            ObjectNode segmentRoot = MAPPER.createObjectNode();
            ObjectNode securityRoot = MAPPER.createObjectNode();
            securityRoot.put("last_price", "2400.00");
            segmentRoot.set(SECURITY_ID, securityRoot);
            root.set(NSE_SEGMENT_WIRE, segmentRoot);
            // Data envelope — preferred
            ObjectNode data = MAPPER.createObjectNode();
            ObjectNode segmentData = MAPPER.createObjectNode();
            ObjectNode securityData = MAPPER.createObjectNode();
            securityData.put("last_price", "2500.00");
            segmentData.set(SECURITY_ID, securityData);
            data.set(NSE_SEGMENT_WIRE, segmentData);
            root.set("data", data);

            DhanJsonResponse result = (DhanJsonResponse) method.invoke(
                    provider, new DhanJsonResponse(root), def);

            // Should prefer data envelope
            assertEquals(250000L, result.decimalPrice("last_price"));
        }

        @Test
        void throwsWhenSecurityNotFound() throws Exception {
            var method = DhanMarketDataProvider.class.getDeclaredMethod(
                    "extractQuotePayload", DhanJsonResponse.class, DhanInstrumentDefinition.class);
            method.setAccessible(true);

            ObjectNode root = MAPPER.createObjectNode();
            root.set("NSE_FNO", MAPPER.createObjectNode()); // Wrong segment

            var ex = assertThrows(InvocationTargetException.class,
                    () -> method.invoke(provider, new DhanJsonResponse(root), def));
            assertInstanceOf(IllegalStateException.class, ex.getCause());
            assertTrue(ex.getCause().getMessage().contains("Missing Dhan quote payload"));
        }
    }

    // ── Integration: execute() wraps errors properly ──────────────────

    @Nested
    class ExecuteErrorTests {

        @Test
        void executePropagatesHttpError() {
            when(context.resolveDef(key)).thenReturn(def);
            when(apiUrlResolver.marketFeedLtpUrl()).thenReturn(LTP_URL);
            stubExecuteThrows(ApiCategory.QUOTE, "quote-ltp",
                    new RuntimeException("HTTP 429 Rate limit exceeded"));

            assertThrows(RuntimeException.class, () -> provider.getLtpPaisa(key));
        }
    }
}
