package com.tradej.broker.icici.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tradej.broker.icici.instrument.BreezeInstrumentDefinition;
import com.tradej.core.domain.model.DepthLevel;
import com.tradej.core.domain.model.Instrument;
import com.tradej.core.domain.model.MarketDepth;
import com.tradej.core.domain.model.ModifyOrderRequest;
import com.tradej.core.domain.model.Order;
import com.tradej.core.domain.model.OrderRequest;
import com.tradej.core.domain.model.Quote;
import com.tradej.core.domain.value.Exchange;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.OrderStatus;
import com.tradej.core.domain.value.OrderType;
import com.tradej.core.domain.value.ProductType;
import com.tradej.core.domain.value.Side;
import com.tradej.core.domain.value.Validity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class BreezeDomainMapperTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private BreezeDomainMapper mapper;

    private static final BreezeInstrumentDefinition BREEZE_DEF = new BreezeInstrumentDefinition(
            "TCS", "TCS-EQ", ExchangeSegment.NSE_EQ, "12345", "SCRIPT01", "NSE"
    );

    private static final Instrument EQUITY_INST = new Instrument(
            "TCS", "TCS-EQ", Exchange.NSE, ExchangeSegment.NSE_EQ,
            "EQUITY", "", null, null, null, 1L, 5L
    );

    @BeforeEach
    void setUp() {
        mapper = new BreezeDomainMapper();
    }

    // ─── toQuote ──────────────────────────────────────────────────────────

    @Nested
    class ToQuoteTests {

        @Test
        void mapsQuoteWithAllFields() {
            ObjectNode node = MAPPER.createObjectNode();
            node.put("ltp", "1500.50");
            node.put("open", "1490.00");
            node.put("high", "1510.00");
            node.put("low", "1485.00");
            node.put("close", "1500.00");
            node.put("total_quantity_traded", 100000L);

            Quote quote = mapper.toQuote(node, EQUITY_INST);

            assertEquals("TCS-EQ", quote.instrument().canonicalSymbol());
            assertEquals(150050L, quote.ltpPaisa());
            assertEquals(149000L, quote.openPaisa());
            assertEquals(151000L, quote.highPaisa());
            assertEquals(148500L, quote.lowPaisa());
            assertEquals(150000L, quote.closePaisa());
            assertEquals(100000L, quote.volume());
        }

        @Test
        void mapsQuoteWithAlternateFieldNames() {
            ObjectNode node = MAPPER.createObjectNode();
            node.put("LTP", "2000.00");
            node.put("Open", "1990.00");
            node.put("High", "2010.00");
            node.put("Low", "1985.00");
            node.put("Close", "2000.00");
            node.put("volume", 50000L);
            node.put("totalBuyQt", 25000L);
            node.put("totalSellQt", 15000L);

            Quote quote = mapper.toQuote(node, EQUITY_INST);

            assertEquals(200000L, quote.ltpPaisa());
            assertEquals(199000L, quote.openPaisa());
            assertEquals(201000L, quote.highPaisa());
            assertEquals(198500L, quote.lowPaisa());
            assertEquals(200000L, quote.closePaisa());
            assertEquals(50000L, quote.volume());
            assertEquals(25000L, quote.totalBuyQuantity());
            assertEquals(15000L, quote.totalSellQuantity());
        }

        @Test
        void mapsQuoteWithDefaultsForMissingFields() {
            ObjectNode node = MAPPER.createObjectNode();

            Quote quote = mapper.toQuote(node, EQUITY_INST);

            assertEquals(0L, quote.ltpPaisa());
            assertEquals(0L, quote.volume());
            assertEquals(0L, quote.totalBuyQuantity());
            assertEquals(0L, quote.totalSellQuantity());
        }
    }

    // ─── toDepth (with full depth arrays) ─────────────────────────────────

    @Nested
    class ToDepthTests {

        @Test
        void mapsDepthWithFullBidAskArrays() {
            ObjectNode node = MAPPER.createObjectNode();
            node.put("timestamp", "2024-05-29T10:00:00Z");

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
            node.set("bids", bids);

            ArrayNode asks = MAPPER.createArrayNode();
            ObjectNode ask1 = MAPPER.createObjectNode();
            ask1.put("price", "1501.00");
            ask1.put("quantity", 800L);
            ask1.put("orders", 4);
            asks.add(ask1);
            node.set("asks", asks);

            MarketDepth depth = mapper.toDepth(node, EQUITY_INST);

            assertEquals(2, depth.bids().size());
            assertEquals(1, depth.asks().size());

            assertEquals(150000L, depth.bids().get(0).pricePaisa());
            assertEquals(1000L, depth.bids().get(0).quantity());
            assertEquals(5, depth.bids().get(0).orderCount());

            assertEquals(149900L, depth.bids().get(1).pricePaisa());
            assertEquals(150100L, depth.asks().get(0).pricePaisa());
        }

        @Test
        void mapsDepthWithTopOfBookObjects() {
            ObjectNode node = MAPPER.createObjectNode();
            node.put("timestamp", "2024-05-29T10:00:00Z");

            ObjectNode bid = MAPPER.createObjectNode();
            bid.put("price", "1500.00");
            bid.put("quantity", 1000L);
            bid.put("orders", 5);
            node.set("bid", bid);

            ObjectNode ask = MAPPER.createObjectNode();
            ask.put("price", "1501.00");
            ask.put("quantity", 800L);
            ask.put("orders", 4);
            node.set("ask", ask);

            MarketDepth depth = mapper.toDepth(node, EQUITY_INST);

            assertEquals(1, depth.bids().size());
            assertEquals(1, depth.asks().size());
            assertEquals(150000L, depth.bids().get(0).pricePaisa());
            assertEquals(150100L, depth.asks().get(0).pricePaisa());
        }

        @Test
        void mapsDepthWithFlatTopOfBookFields() {
            ObjectNode node = MAPPER.createObjectNode();
            node.put("bid_price", "1500.50");
            node.put("bid_quantity", 1000L);
            node.put("bid_orders", 5);
            node.put("ask_price", "1501.50");
            node.put("ask_quantity", 800L);
            node.put("ask_orders", 4);

            MarketDepth depth = mapper.toDepth(node, EQUITY_INST);

            assertEquals(1, depth.bids().size());
            assertEquals(1, depth.asks().size());
            assertEquals(150050L, depth.bids().get(0).pricePaisa());
            assertEquals(1000L, depth.bids().get(0).quantity());
            assertEquals(150150L, depth.asks().get(0).pricePaisa());
            assertEquals(800L, depth.asks().get(0).quantity());
        }

        @Test
        void returnsEmptyDepthWhenNoData() {
            MarketDepth depth = mapper.toDepth(MAPPER.createObjectNode(), EQUITY_INST);

            assertTrue(depth.bids().isEmpty());
            assertTrue(depth.asks().isEmpty());
            assertEquals(0, depth.levels());
        }
    }

    // ─── toOrder ──────────────────────────────────────────────────────────

    @Nested
    class ToOrderTests {

        @Test
        void mapsOrderFromResponseNode() {
            ObjectNode node = MAPPER.createObjectNode();
            node.put("order_id", "ORD001");
            node.put("stock_code", "TCS");
            node.put("action", "buy");
            node.put("order_type", "limit");
            node.put("status", "executed");
            node.put("quantity", "10");
            node.put("price", "1500.50");
            node.put("stoploss", "0");

            OrderRequest request = new OrderRequest(
                    "TCS", ExchangeSegment.NSE_EQ, Side.BUY, 10L,
                    OrderType.LIMIT, 150050L, 0L, ProductType.CNC, Validity.DAY, null
            );

            Order order = mapper.toOrder(node, request);

            assertEquals("ORD001", order.orderId());
            assertEquals("TCS", order.symbol());
            assertEquals(Side.BUY, order.side());
            assertEquals(OrderType.LIMIT, order.orderType());
            assertEquals(OrderStatus.TRADED, order.status());
            assertEquals(10L, order.quantity());
            assertEquals(10L, order.filledQuantity());
            assertEquals(150050L, order.pricePaisa());
            assertEquals(0L, order.triggerPricePaisa());
        }

        @Test
        void mapsOrderWithDefaultValues() {
            ObjectNode node = MAPPER.createObjectNode();
            node.put("order_id", "ORD002");
            node.put("quantity", "5");
            node.put("price", "1000.00");

            Order order = mapper.toOrder(node, null);

            assertEquals("ORD002", order.orderId());
            assertEquals("", order.symbol());
            assertEquals(5L, order.quantity());
            assertEquals(100000L, order.pricePaisa());
        }

        @Test
        void computesFilledQuantityFromQuantityMinusPending() {
            ObjectNode node = MAPPER.createObjectNode();
            node.put("order_id", "ORD003");
            node.put("quantity", "10");
            node.put("pending_quantity", "3");
            node.put("price", "1000.00");

            Order order = mapper.toOrder(node, null);

            assertEquals(10L, order.quantity());
            assertEquals(7L, order.filledQuantity());
        }

        @Test
        void mapsPartiallyExecutedStatus() {
            ObjectNode node = MAPPER.createObjectNode();
            node.put("order_id", "ORD004");
            node.put("status", "partially executed");
            node.put("quantity", "10");
            node.put("price", "1000.00");

            Order order = mapper.toOrder(node, null);

            assertEquals(OrderStatus.PART_TRADED, order.status());
        }

        @Test
        void mapsCancelledStatus() {
            ObjectNode node = MAPPER.createObjectNode();
            node.put("order_id", "ORD005");
            node.put("status", "cancelled");
            node.put("quantity", "10");
            node.put("price", "1000.00");

            Order order = mapper.toOrder(node, null);

            assertEquals(OrderStatus.CANCELLED, order.status());
        }

        @Test
        void mapsRejectedStatus() {
            ObjectNode node = MAPPER.createObjectNode();
            node.put("order_id", "ORD006");
            node.put("status", "rejected");
            node.put("quantity", "10");
            node.put("price", "1000.00");

            Order order = mapper.toOrder(node, null);

            assertEquals(OrderStatus.REJECTED, order.status());
        }
    }

    // ─── Payload builders ─────────────────────────────────────────────────

    @Nested
    class PayloadBuilderTests {

        @Test
        void buildsPlaceOrderPayload() {
            OrderRequest request = new OrderRequest(
                    "TCS", ExchangeSegment.NSE_EQ, Side.BUY, 10L,
                    OrderType.LIMIT, 150050L, 0L, ProductType.CNC, Validity.DAY, "corr-001"
            );

            ObjectNode payload = mapper.toPlaceOrderPayload(request, BREEZE_DEF);

            assertEquals("TCS", payload.get("stock_code").asText());
            assertEquals("NSE", payload.get("exchange_code").asText());
            assertEquals("cash", payload.get("product").asText());
            assertEquals("buy", payload.get("action").asText());
            assertEquals("limit", payload.get("order_type").asText());
            assertEquals("10", payload.get("quantity").asText());
            assertEquals("1500.50", payload.get("price").asText());
            assertEquals("day", payload.get("validity").asText());
        }

        @Test
        void buildsPlaceOrderPayloadWithTriggerPrice() {
            OrderRequest request = new OrderRequest(
                    "TCS", ExchangeSegment.NSE_EQ, Side.SELL, 10L,
                    OrderType.STOP_LOSS, 150050L, 149000L, ProductType.INTRADAY, Validity.DAY, null
            );

            ObjectNode payload = mapper.toPlaceOrderPayload(request, BREEZE_DEF);

            assertEquals("sell", payload.get("action").asText(), "action should be sell");
            assertEquals("stoploss", payload.get("order_type").asText());
            assertEquals("1490.00", payload.get("stoploss").asText());
        }

        @Test
        void buildsModifyOrderPayload() {
            ModifyOrderRequest modify = new ModifyOrderRequest(
                    "ORD001", null, null, 15L, 155000L, null, null, null
            );

            ObjectNode payload = mapper.toModifyOrderPayload(modify, "NSE");

            assertEquals("ORD001", payload.get("order_id").asText());
            assertEquals("NSE", payload.get("exchange_code").asText());
            assertEquals("15", payload.get("quantity").asText());
            assertEquals("1550.00", payload.get("price").asText());
        }

        @Test
        void buildsCancelOrderPayload() {
            ObjectNode payload = mapper.toCancelOrderPayload("ORD001", "NSE");

            assertEquals("ORD001", payload.get("order_id").asText());
            assertEquals("NSE", payload.get("exchange_code").asText());
        }

        @Test
        void throwsForMarketOrders() {
            OrderRequest request = new OrderRequest(
                    "TCS", ExchangeSegment.NSE_EQ, Side.BUY, 10L,
                    OrderType.MARKET, 0L, 0L, ProductType.CNC, Validity.DAY, null
            );

            assertThrows(UnsupportedOperationException.class, () ->
                    mapper.toPlaceOrderPayload(request, BREEZE_DEF));
        }
    }
}
