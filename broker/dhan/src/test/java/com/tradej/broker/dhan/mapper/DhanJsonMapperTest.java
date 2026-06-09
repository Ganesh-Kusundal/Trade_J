package com.tradej.broker.dhan.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tradej.broker.dhan.instrument.DhanInstrumentDefinition;
import com.tradej.core.domain.model.Balance;
import com.tradej.core.domain.model.DepthLevel;
import com.tradej.core.domain.model.Holding;
import com.tradej.core.domain.model.Instrument;
import com.tradej.core.domain.model.MarketDepth;
import com.tradej.core.domain.model.Order;
import com.tradej.core.domain.model.OrderRequest;
import com.tradej.core.domain.model.Position;
import com.tradej.core.domain.model.Quote;
import com.tradej.core.domain.model.Trade;
import com.tradej.core.domain.value.Exchange;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.OrderStatus;
import com.tradej.core.domain.value.OrderType;
import com.tradej.core.domain.value.ProductType;
import com.tradej.core.domain.value.Side;
import com.tradej.core.domain.value.Validity;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class DhanJsonMapperTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final DhanInstrumentDefinition EQUITY_DEF = new DhanInstrumentDefinition(
            "TCS", "TCS-EQ", Exchange.NSE, ExchangeSegment.NSE_EQ, "11536",
            "EQUITY", "", null, null, null, 1L, 1L, null
    );

    private static final Instrument EQUITY_INST = EQUITY_DEF.toInstrument();

    // ─── toOrder ──────────────────────────────────────────────────────────

    @Nested
    class ToOrderTests {

        @Test
        void mapsFullOrderFromPlaceResponse() {
            ObjectNode node = MAPPER.createObjectNode();
            node.put("orderId", "ORD001");
            node.put("correlationId", "corr-123");
            node.put("tradingSymbol", "TCS");
            node.put("exchangeSegment", "NSE_EQ");
            node.put("transactionType", "BUY");
            node.put("productType", "INTRADAY");
            node.put("orderType", "LIMIT");
            node.put("orderStatus", "OPEN");
            node.put("quantity", 10L);
            node.put("filledQty", 5L);
            node.put("price", "3500.00");
            node.put("triggerPrice", "3450.00");
            node.put("exchangeTime", 1717000000000L);

            Order order = DhanJsonMapper.toOrder(new DhanJsonResponse(node), EQUITY_INST);

            assertEquals("ORD001", order.orderId());
            assertEquals("corr-123", order.correlationId());
            assertEquals("TCS-EQ", order.symbol());
            assertEquals(ExchangeSegment.NSE_EQ, order.exchangeSegment());
            assertEquals(Side.BUY, order.side());
            assertEquals(ProductType.INTRADAY, order.productType());
            assertEquals(OrderType.LIMIT, order.orderType());
            assertEquals(OrderStatus.OPEN, order.status());
            assertEquals(10L, order.quantity());
            assertEquals(5L, order.filledQuantity());
            assertEquals(350000L, order.pricePaisa());
            assertEquals(345000L, order.triggerPricePaisa());
            assertEquals(1717000000000L, order.exchangeTimeMs());
        }

        @Test
        void mapsOrderFromPlaceResponseUsingFixture() throws Exception {
            var json = MAPPER.readTree(getClass().getResourceAsStream(
                    "/dhan-fixtures/place-order-response.json"));

            Order order = DhanJsonMapper.toOrder(new DhanJsonResponse(json), EQUITY_INST);

            assertEquals("SANDBOX-ORDER-001", order.orderId());
            assertEquals("TCS-EQ", order.symbol());
            assertEquals(ExchangeSegment.NSE_EQ, order.exchangeSegment());
            assertEquals(Side.BUY, order.side());
            assertEquals(ProductType.INTRADAY, order.productType());
            assertEquals(OrderType.MARKET, order.orderType());
            assertEquals(OrderStatus.PENDING, order.status());
            assertEquals(1L, order.quantity());
            assertEquals(0L, order.filledQuantity());
        }

        @Test
        void mapsOrderWithFallbackFields() {
            ObjectNode node = MAPPER.createObjectNode();
            node.put("id", "ORD002");
            node.put("symbol", "TCS");
            node.put("transactionType", "SELL");
            node.put("productType", "CNC");
            node.put("orderType", "MARKET");
            node.put("status", "COMPLETE");
            node.put("quantity", 5L);
            node.put("filledQuantity", 5L);
            node.put("price", "1500.50");

            Order order = DhanJsonMapper.toOrder(new DhanJsonResponse(node), null);

            assertEquals("ORD002", order.orderId());
            assertEquals("TCS", order.symbol());
            assertEquals(Side.SELL, order.side());
            assertEquals(ProductType.CNC, order.productType());
            assertEquals(OrderType.MARKET, order.orderType());
            assertEquals(OrderStatus.TRADED, order.status());
            assertEquals(150050L, order.pricePaisa());
        }

        @Test
        void mapsOrderViaRequestBasedFactoryWithDataEnvelope() {
            ObjectNode data = MAPPER.createObjectNode();
            data.put("orderId", "ORD003");
            data.put("transactionType", "BUY");
            data.put("productType", "INTRADAY");
            data.put("orderType", "LIMIT");
            data.put("quantity", 10L);
            data.put("filledQty", 0L);
            data.put("price", "3500.00");

            ObjectNode envelope = MAPPER.createObjectNode();
            envelope.set("data", data);

            OrderRequest request = new OrderRequest(
                    "TCS", ExchangeSegment.NSE_EQ, Side.BUY, 10L,
                    OrderType.LIMIT, 350000L, 0L, ProductType.INTRADAY, Validity.DAY, "corr-456"
            );

            Order order = DhanJsonMapper.toOrder(new DhanJsonResponse(envelope), request, EQUITY_DEF);

            assertEquals("ORD003", order.orderId());
            assertEquals("corr-456", order.correlationId());
            assertEquals("TCS-EQ", order.symbol());
            assertEquals(ExchangeSegment.NSE_EQ, order.exchangeSegment());
            assertEquals(10L, order.quantity());
        }

        @Test
        void mapsForeverOrder() {
            ObjectNode node = MAPPER.createObjectNode();
            node.put("orderId", "FOREVER-001");
            node.put("tradingSymbol", "TCS");
            node.put("exchangeSegment", "NSE_EQ");
            node.put("transactionType", "BUY");
            node.put("productType", "CNC");
            node.put("orderType", "SINGLE");
            node.put("orderStatus", "TRANSIT");
            node.put("quantity", 10L);
            node.put("price", "100.00");

            Order order = DhanJsonMapper.toForeverOrder(new DhanJsonResponse(node));

            assertEquals("FOREVER-001", order.orderId());
            assertEquals("TCS", order.symbol());
            assertEquals(ExchangeSegment.NSE_EQ, order.exchangeSegment());
            assertEquals(Side.BUY, order.side());
            assertEquals(ProductType.CNC, order.productType());
            assertEquals(OrderType.LIMIT, order.orderType());
            assertEquals(OrderStatus.PENDING, order.status());
            assertEquals(10L, order.quantity());
            assertEquals(0L, order.filledQuantity());
            assertEquals(10000L, order.pricePaisa());
        }
    }

    // ─── toTrade ──────────────────────────────────────────────────────────

    @Nested
    class ToTradeTests {

        @Test
        void mapsTradeFromFields() {
            ObjectNode node = MAPPER.createObjectNode();
            node.put("exchangeTradeId", "TRD001");
            node.put("orderId", "ORD001");
            node.put("tradingSymbol", "TCS");
            node.put("exchangeSegment", "NSE_EQ");
            node.put("transactionType", "BUY");
            node.put("tradedQuantity", 10L);
            node.put("tradedPrice", "3500.50");
            node.put("exchangeTime", 1717000000000L);

            Trade trade = DhanJsonMapper.toTrade(new DhanJsonResponse(node), EQUITY_INST);

            assertEquals("TRD001", trade.tradeId());
            assertEquals("ORD001", trade.orderId());
            assertEquals("TCS-EQ", trade.symbol());
            assertEquals(ExchangeSegment.NSE_EQ, trade.exchangeSegment());
            assertEquals(Side.BUY, trade.side());
            assertEquals(10L, trade.quantity());
            assertEquals(350050L, trade.pricePaisa());
            assertEquals(1717000000000L, trade.exchangeTimeMs());
        }

        @Test
        void mapsTradeUsingFixture() throws Exception {
            var json = MAPPER.readTree(getClass().getResourceAsStream(
                    "/dhan-fixtures/trades-list-response.json"));

            Trade trade = DhanJsonMapper.toTrade(new DhanJsonResponse(json.get(0)), EQUITY_INST);

            assertEquals("SANDBOX-TRADE-001", trade.tradeId());
            assertEquals("SANDBOX-ORDER-001", trade.orderId());
            assertEquals("TCS-EQ", trade.symbol());
            assertEquals(Side.BUY, trade.side());
            assertEquals(1L, trade.quantity());
            assertEquals(350050L, trade.pricePaisa());
        }

        @Test
        void mapsTradeWithFallbackFieldNames() {
            ObjectNode node = MAPPER.createObjectNode();
            node.put("id", "TRD002");
            node.put("orderId", "ORD002");
            node.put("symbol", "TCS");
            node.put("exchangeSegment", "NSE_EQ");
            node.put("transactionType", "SELL");
            node.put("quantity", 5L);
            node.put("price", "1500.00");

            Trade trade = DhanJsonMapper.toTrade(new DhanJsonResponse(node), null);

            assertEquals("TRD002", trade.tradeId());
            assertEquals("TCS", trade.symbol());
            assertEquals(Side.SELL, trade.side());
            assertEquals(5L, trade.quantity());
            assertEquals(150000L, trade.pricePaisa());
        }
    }

    // ─── toPosition ───────────────────────────────────────────────────────

    @Nested
    class ToPositionTests {

        @Test
        void mapsPositionWithAllFields() {
            ObjectNode node = MAPPER.createObjectNode();
            node.put("netQty", 100L);
            node.put("costPrice", "1500.00");
            node.put("unrealizedProfit", "5000.00");
            node.put("ltp", "1550.00");
            node.put("positionType", "LONG");

            Position position = DhanJsonMapper.toPosition(new DhanJsonResponse(node), EQUITY_INST);

            assertEquals("TCS-EQ", position.symbol());
            assertEquals(ExchangeSegment.NSE_EQ, position.exchangeSegment());
            assertEquals(Side.BUY, position.side());
            assertEquals(100L, position.quantity());
            assertEquals(150000L, position.averagePricePaisa());
            assertEquals(500000L, position.unrealizedPnlPaisa());
            assertEquals(155000L, position.lastPricePaisa());
        }

        @Test
        void infersLastPriceWhenLTPIsZeroAndQuantityNonZero() {
            ObjectNode node = MAPPER.createObjectNode();
            node.put("netQty", 10L);
            node.put("costPrice", "1000.00");
            node.put("unrealizedProfit", "500.00");
            node.put("positionType", "LONG");

            Position position = DhanJsonMapper.toPosition(new DhanJsonResponse(node), EQUITY_INST);

            // lastPrice = avgPrice + (unrealizedPnl / qty) = 100000 + (50000 / 10) = 105000
            assertEquals(100000L, position.averagePricePaisa());
            assertEquals(105000L, position.lastPricePaisa());
        }

        @Test
        void infersLastPriceFromAvgPriceWhenBothLTPAndQuantityAreZero() {
            ObjectNode node = MAPPER.createObjectNode();
            node.put("netQty", 0L);
            node.put("costPrice", "1000.00");
            node.put("unrealizedProfit", "0.00");
            node.put("positionType", "LONG");

            Position position = DhanJsonMapper.toPosition(new DhanJsonResponse(node), EQUITY_INST);

            assertEquals(100000L, position.averagePricePaisa());
            assertEquals(100000L, position.lastPricePaisa());
        }

        @Test
        void mapsPositionWithFallbackFieldNames() {
            ObjectNode node = MAPPER.createObjectNode();
            node.put("netQuantity", 50L);
            node.put("buyAvg", "2000.00");
            node.put("lastPrice", "2050.00");
            node.put("positionType", "SHORT");

            Position position = DhanJsonMapper.toPosition(new DhanJsonResponse(node), EQUITY_INST);

            assertEquals(50L, position.quantity());
            assertEquals(200000L, position.averagePricePaisa());
            assertEquals(205000L, position.lastPricePaisa());
            assertEquals(Side.SELL, position.side());
        }
    }

    // ─── toHolding ────────────────────────────────────────────────────────

    @Nested
    class ToHoldingTests {

        @Test
        void mapsHoldingWithAllFields() {
            ObjectNode node = MAPPER.createObjectNode();
            node.put("totalQty", 100L);
            node.put("availableQty", 80L);
            node.put("collateralQty", 20L);
            node.put("avgCostPrice", "1500.00");

            Holding holding = DhanJsonMapper.toHolding(new DhanJsonResponse(node), EQUITY_INST);

            assertEquals("TCS-EQ", holding.symbol());
            assertEquals(ExchangeSegment.NSE_EQ, holding.exchangeSegment());
            assertEquals(100L, holding.totalQuantity());
            assertEquals(80L, holding.availableQuantity());
            assertEquals(20L, holding.collateralQuantity());
            assertEquals(150000L, holding.averagePricePaisa());
        }

        @Test
        void mapsHoldingWithFallbackFieldNames() {
            ObjectNode node = MAPPER.createObjectNode();
            node.put("totalQuantity", 50L);
            node.put("availableQuantity", 50L);
            node.put("averageCostPrice", "2500.00");

            Holding holding = DhanJsonMapper.toHolding(new DhanJsonResponse(node), EQUITY_INST);

            assertEquals(50L, holding.totalQuantity());
            assertEquals(50L, holding.availableQuantity());
            assertEquals(250000L, holding.averagePricePaisa());
        }

        @Test
        void mapsHoldingResolvesSegmentFromExchangeCode() {
            ObjectNode node = MAPPER.createObjectNode();
            node.put("tradingSymbol", "TCS");
            node.put("exchange", "NSE");
            node.put("totalQty", 10L);
            node.put("availableQty", 10L);
            node.put("avgCostPrice", "1000.00");

            Holding holding = DhanJsonMapper.toHolding(new DhanJsonResponse(node), null);

            assertEquals("TCS", holding.symbol());
            assertEquals(ExchangeSegment.NSE_EQ, holding.exchangeSegment());
            assertEquals(10L, holding.totalQuantity());
        }
    }

    // ─── toBalance ────────────────────────────────────────────────────────

    @Nested
    class ToBalanceTests {

        @Test
        void mapsBalanceWithAllFields() {
            ObjectNode node = MAPPER.createObjectNode();
            node.put("dhanClientId", "CLIENT001");
            node.put("availableBalance", "100000.00");
            node.put("collateralAmount", "50000.00");
            node.put("receivableAmount", "10000.00");
            node.put("utilizedAmount", "30000.00");
            node.put("withdrawableBalance", "70000.00");

            Balance balance = DhanJsonMapper.toBalance(new DhanJsonResponse(node));

            assertEquals("CLIENT001", balance.clientId());
            assertEquals(10000000L, balance.cashPaisa());
            assertEquals(5000000L, balance.collateralPaisa());
            assertEquals(1000000L, balance.receivablePaisa());
            assertEquals(3000000L, balance.utilizedPaisa());
            assertEquals(7000000L, balance.withdrawablePaisa());
        }

        @Test
        void mapsBalanceWithTypoFallbackFieldName() {
            // Dhan API sometimes returns "availabelBalance" (typo)
            ObjectNode node = MAPPER.createObjectNode();
            node.put("dhanClientId", "CLIENT002");
            node.put("availabelBalance", "50000.00");
            node.put("collateralAmount", "25000.00");

            Balance balance = DhanJsonMapper.toBalance(new DhanJsonResponse(node));

            assertEquals(5000000L, balance.cashPaisa());
            assertEquals(2500000L, balance.collateralPaisa());
        }

        @Test
        void correctSpellingTakesPriorityOverTypo() {
            // When both availableBalance AND availabelBalance are present, correct wins
            ObjectNode node = MAPPER.createObjectNode();
            node.put("dhanClientId", "CLIENT003");
            node.put("availableBalance", "80000.00");
            node.put("availabelBalance", "50000.00");

            Balance balance = DhanJsonMapper.toBalance(new DhanJsonResponse(node));

            assertEquals(8000000L, balance.cashPaisa(), "correct spelling should win over typo");
        }

        @Test
        void mapsBalanceWithDefaultZeroForMissingFields() {
            ObjectNode node = MAPPER.createObjectNode();

            Balance balance = DhanJsonMapper.toBalance(new DhanJsonResponse(node));

            assertNotNull(balance);
            assertEquals(0L, balance.cashPaisa());
            assertEquals(0L, balance.collateralPaisa());
            assertEquals(0L, balance.receivablePaisa());
            assertEquals(0L, balance.utilizedPaisa());
            assertEquals(0L, balance.withdrawablePaisa());
        }
    }

    // ─── toQuote ──────────────────────────────────────────────────────────

    @Nested
    class ToQuoteTests {

        @Test
        void mapsQuoteWithAllFields() {
            ObjectNode node = MAPPER.createObjectNode();
            node.put("last_price", "1500.50");
            node.put("volume", 100000L);
            node.put("buy_quantity", 50000L);
            node.put("sell_quantity", 30000L);
            node.put("oi", 10000L);
            node.put("last_trade_time", 1717000000000L);

            ObjectNode ohlc = MAPPER.createObjectNode();
            ohlc.put("open", "1490.00");
            ohlc.put("high", "1510.00");
            ohlc.put("low", "1485.00");
            ohlc.put("close", "1500.00");
            node.set("ohlc", ohlc);

            Quote quote = DhanJsonMapper.toQuote(new DhanJsonResponse(node), EQUITY_INST);

            assertEquals("TCS-EQ", quote.instrument().canonicalSymbol());
            assertEquals(150050L, quote.ltpPaisa());
            assertEquals(149000L, quote.openPaisa());
            assertEquals(151000L, quote.highPaisa());
            assertEquals(148500L, quote.lowPaisa());
            assertEquals(150000L, quote.closePaisa());
            assertEquals(100000L, quote.volume());
            assertEquals(50000L, quote.totalBuyQuantity());
            assertEquals(30000L, quote.totalSellQuantity());
            assertEquals(10000L, quote.openInterest());
            assertEquals(1717000000000L, quote.timestampMs());
        }

        @Test
        void mapsQuoteWithoutOhlcSubObject() {
            ObjectNode node = MAPPER.createObjectNode();
            node.put("lastPrice", "1500.50");
            node.put("volume", 50000L);
            node.put("open", "1490.00");
            node.put("high", "1510.00");
            node.put("low", "1485.00");
            node.put("close", "1500.00");
            node.put("totalBuyQuantity", 25000L);
            node.put("totalSellQuantity", 15000L);
            node.put("openInterest", 5000L);
            node.put("ltt", 1717000000000L);

            Quote quote = DhanJsonMapper.toQuote(new DhanJsonResponse(node), EQUITY_INST);

            assertEquals(150050L, quote.ltpPaisa());
            assertEquals(149000L, quote.openPaisa());
            assertEquals(151000L, quote.highPaisa());
            assertEquals(148500L, quote.lowPaisa());
            assertEquals(150000L, quote.closePaisa());
            assertEquals(25000L, quote.totalBuyQuantity());
            assertEquals(15000L, quote.totalSellQuantity());
            assertEquals(5000L, quote.openInterest());
        }
    }

    // ─── toDepth ──────────────────────────────────────────────────────────

    @Nested
    class ToDepthTests {

        @Test
        @SuppressWarnings("unchecked")
        void mapsDepthWithBidAndAskLevels() {
            ObjectNode node = MAPPER.createObjectNode();

            ObjectNode depth = MAPPER.createObjectNode();

            ArrayNode bids = MAPPER.createArrayNode();
            ObjectNode bid1 = MAPPER.createObjectNode();
            bid1.put("price", "1500.00");
            bid1.put("quantity", 1000L);
            bid1.put("orders", 5);
            bids.add(bid1);
            ObjectNode bid2 = MAPPER.createObjectNode();
            bid2.put("price", "1499.00");
            bid2.put("quantity", 500L);
            bid2.put("orders", 3);
            bids.add(bid2);
            depth.set("buy", bids);

            ArrayNode asks = MAPPER.createArrayNode();
            ObjectNode ask1 = MAPPER.createObjectNode();
            ask1.put("price", "1501.00");
            ask1.put("quantity", 800L);
            ask1.put("orders", 4);
            asks.add(ask1);
            depth.set("sell", asks);

            node.set("depth", depth);
            node.put("last_trade_time", 1717000000000L);

            MarketDepth md = DhanJsonMapper.toDepth(new DhanJsonResponse(node), EQUITY_INST);

            assertEquals(2, md.bids().size());
            assertEquals(1, md.asks().size());

            assertEquals(150000L, md.bids().get(0).pricePaisa());
            assertEquals(1000L, md.bids().get(0).quantity());
            assertEquals(5, md.bids().get(0).orderCount());

            assertEquals(149900L, md.bids().get(1).pricePaisa());
            assertEquals(150100L, md.asks().get(0).pricePaisa());
            assertEquals(2, md.levels());
            assertEquals(1717000000000L, md.timestampMs());
        }

        @Test
        void returnsEmptyDepthWhenNoDepthField() {
            ObjectNode node = MAPPER.createObjectNode();

            MarketDepth md = DhanJsonMapper.toDepth(new DhanJsonResponse(node), EQUITY_INST);

            assertTrue(md.bids().isEmpty());
            assertTrue(md.asks().isEmpty());
            assertEquals(0, md.levels());
        }
    }

    // ─── wrap ─────────────────────────────────────────────────────────────

    @Nested
    class WrapTests {

        @Test
        void wrapsJsonNode() {
            ObjectNode node = MAPPER.createObjectNode();
            node.put("test", "value");

            DhanJsonResponse response = DhanJsonMapper.wrap(node);

            assertNotNull(response);
            assertTrue(response.has("test"));
        }

        @Test
        void wrapsDhanJsonResponseIdentity() {
            ObjectNode node = MAPPER.createObjectNode();
            DhanJsonResponse original = new DhanJsonResponse(node);

            DhanJsonResponse response = DhanJsonMapper.wrap(original);

            assertSame(original, response);
        }

        @Test
        void wrapsNullAsMissing() {
            DhanJsonResponse response = DhanJsonMapper.wrap(null);

            assertTrue(response.isMissingNode());
        }

        @Test
        void throwsForUnsupportedType() {
            assertThrows(IllegalArgumentException.class, () ->
                    DhanJsonMapper.wrap("unsupported"));
        }
    }
}
