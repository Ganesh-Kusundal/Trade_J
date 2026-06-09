package com.tradej.broker.upstox.mapper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
class UpstoxDomainMapperTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private UpstoxDomainMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new UpstoxDomainMapper();
    }

    // ─── toOrder ──────────────────────────────────────────────────────────

    @Nested
    class ToOrderTests {

        @Test
        void mapsPlaceOrderResponseToOrder() throws Exception {
            JsonNode response = MAPPER.readTree(getClass().getResourceAsStream(
                    "/upstox-fixtures/place-order-response.json"));

            var order = mapper.toOrder(response, null);

            assertNotNull(order);
            assertEquals("TEST_ORD_001", order.orderId());
            assertEquals("SBIN", order.symbol());
            assertEquals(10L, order.quantity());
            assertEquals(85050L, order.pricePaisa());
            assertEquals(ExchangeSegment.NSE_EQ, order.exchangeSegment());
            assertEquals(Side.BUY, order.side());
            assertEquals(ProductType.INTRADAY, order.productType());
            assertEquals(OrderType.MARKET, order.orderType());
            assertEquals(OrderStatus.TRADED, order.status());
            assertEquals(10L, order.filledQuantity());
        }

        @Test
        void mapsOrderFromDataObject() {
            ObjectNode root = MAPPER.createObjectNode();
            ObjectNode data = MAPPER.createObjectNode();
            data.put("order_id", "ORD001");
            data.put("trading_symbol", "TCS");
            data.put("exchange", "NSE");
            data.put("transaction_type", "SELL");
            data.put("product", "CNC");
            data.put("order_type", "LIMIT");
            data.put("status", "COMPLETE");
            data.put("quantity", 5L);
            data.put("filled_quantity", 5L);
            data.put("price", 1500.50);
            data.put("trigger_price", 0);
            data.put("exchange_timestamp", 1717000000000L);
            data.put("status_message", "Order executed");
            root.set("data", data);

            var order = mapper.toOrder(root, null);

            assertEquals("ORD001", order.orderId());
            assertEquals("TCS", order.symbol());
            assertEquals(ExchangeSegment.NSE_EQ, order.exchangeSegment());
            assertEquals(Side.SELL, order.side());
            assertEquals(ProductType.CNC, order.productType());
            assertEquals(OrderType.LIMIT, order.orderType());
            assertEquals(OrderStatus.TRADED, order.status());
            assertEquals(5L, order.quantity());
            assertEquals(5L, order.filledQuantity());
            assertEquals(150050L, order.pricePaisa());
            assertEquals(1717000000000L, order.exchangeTimeMs());
        }

        @Test
        void mapsOrderWithNullTimestamp() {
            ObjectNode data = MAPPER.createObjectNode();
            data.put("order_id", "ORD002");
            data.put("exchange", "NSE");
            data.put("transaction_type", "BUY");
            data.put("product", "MIS");
            data.put("order_type", "MARKET");
            data.put("status", "OPEN");
            data.put("quantity", 10L);

            var order = mapper.toOrder(data, null);

            assertEquals("ORD002", order.orderId());
            assertEquals(10L, order.quantity());
            assertEquals(0L, order.filledQuantity());
        }
    }

    // ─── toOrderList ──────────────────────────────────────────────────────

    @Nested
    class ToOrderListTests {

        @Test
        void mapsArrayOfOrders() {
            ObjectNode root = MAPPER.createObjectNode();
            ArrayNode data = MAPPER.createArrayNode();

            ObjectNode o1 = MAPPER.createObjectNode();
            o1.put("order_id", "ORD001");
            o1.put("exchange", "NSE");
            o1.put("transaction_type", "BUY");
            o1.put("product", "MIS");
            o1.put("order_type", "MARKET");
            o1.put("status", "COMPLETE");
            o1.put("quantity", 10L);
            o1.put("price", 100.00);
            data.add(o1);

            ObjectNode o2 = MAPPER.createObjectNode();
            o2.put("order_id", "ORD002");
            o2.put("exchange", "NFO");
            o2.put("transaction_type", "SELL");
            o2.put("product", "NRML");
            o2.put("order_type", "LIMIT");
            o2.put("status", "OPEN");
            o2.put("quantity", 75L);
            o2.put("price", 150.00);
            data.add(o2);

            root.set("data", data);

            var orders = mapper.toOrderList(root);

            assertEquals(2, orders.size());
            assertEquals("ORD001", orders.get(0).orderId());
            assertEquals("ORD002", orders.get(1).orderId());
            assertEquals(ExchangeSegment.NSE_FNO, orders.get(1).exchangeSegment());
            assertEquals(ProductType.CARRY_FORWARD, orders.get(1).productType());
        }

        @Test
        void returnsEmptyForNoDataField() {
            ObjectNode root = MAPPER.createObjectNode();

            var orders = mapper.toOrderList(root);

            assertTrue(orders.isEmpty());
        }
    }

    // ─── toTradeList ──────────────────────────────────────────────────────

    @Nested
    class ToTradeListTests {

        @Test
        @SuppressWarnings("unchecked")
        void mapsArrayOfTrades() {
            ObjectNode root = MAPPER.createObjectNode();
            ArrayNode data = MAPPER.createArrayNode();

            ObjectNode t1 = MAPPER.createObjectNode();
            t1.put("trade_id", "TRD001");
            t1.put("order_id", "ORD001");
            t1.put("trading_symbol", "TCS");
            t1.put("exchange", "NSE");
            t1.put("transaction_type", "BUY");
            t1.put("quantity", 10L);
            t1.put("price", 1500.50);
            t1.put("exchange_timestamp", 1717000000000L);
            data.add(t1);

            ObjectNode t2 = MAPPER.createObjectNode();
            t2.put("trade_id", "TRD002");
            t2.put("order_id", "ORD002");
            t2.put("trading_symbol", "INFY");
            t2.put("exchange", "NSE");
            t2.put("transaction_type", "SELL");
            t2.put("quantity", 5L);
            t2.put("price", 1800.00);
            t2.put("exchange_timestamp", 1717000001000L);
            data.add(t2);

            root.set("data", data);

            var trades = mapper.toTradeList(root);

            assertEquals(2, trades.size());
            assertEquals("TRD001", trades.get(0).tradeId());
            assertEquals("ORD001", trades.get(0).orderId());
            assertEquals("TCS", trades.get(0).symbol());
            assertEquals(Side.BUY, trades.get(0).side());
            assertEquals(10L, trades.get(0).quantity());
            assertEquals(150050L, trades.get(0).pricePaisa());
            assertEquals(1717000000000L, trades.get(0).exchangeTimeMs());

            assertEquals("TRD002", trades.get(1).tradeId());
            assertEquals("INFY", trades.get(1).symbol());
            assertEquals(Side.SELL, trades.get(1).side());
            assertEquals(180000L, trades.get(1).pricePaisa());
        }

        @Test
        void returnsEmptyForNoData() {
            var trades = mapper.toTradeList(MAPPER.createObjectNode());

            assertTrue(trades.isEmpty());
        }
    }

    // ─── isSuccess ────────────────────────────────────────────────────────

    @Nested
    class IsSuccessTests {

        @Test
        void returnsTrueForSuccessStatus() throws Exception {
            JsonNode response = MAPPER.readTree(getClass().getResourceAsStream(
                    "/upstox-fixtures/cancel-order-response.json"));

            assertTrue(mapper.isSuccess(response));
        }

        @Test
        void returnsFalseForFailureStatus() {
            ObjectNode root = MAPPER.createObjectNode();
            root.put("status", "failure");

            assertFalse(mapper.isSuccess(root));
        }

        @Test
        void returnsFalseForMissingStatus() {
            assertFalse(mapper.isSuccess(MAPPER.createObjectNode()));
        }
    }

    // ─── parseSegment ─────────────────────────────────────────────────────

    @Nested
    class ParseSegmentTests {

        @Test
        void parsesNse() {
            ObjectNode node = MAPPER.createObjectNode();
            node.put("exchange", "NSE");
            assertEquals(ExchangeSegment.NSE_EQ, UpstoxDomainMapper.parseSegment(node));
        }

        @Test
        void parsesBse() {
            ObjectNode node = MAPPER.createObjectNode();
            node.put("exchange", "BSE");
            assertEquals(ExchangeSegment.BSE_EQ, UpstoxDomainMapper.parseSegment(node));
        }

        @Test
        void parsesNfo() {
            ObjectNode node = MAPPER.createObjectNode();
            node.put("exchange", "NFO");
            assertEquals(ExchangeSegment.NSE_FNO, UpstoxDomainMapper.parseSegment(node));
        }

        @Test
        void parsesBfo() {
            ObjectNode node = MAPPER.createObjectNode();
            node.put("exchange", "BFO");
            assertEquals(ExchangeSegment.BSE_FNO, UpstoxDomainMapper.parseSegment(node));
        }

        @Test
        void parsesMcx() {
            ObjectNode node = MAPPER.createObjectNode();
            node.put("exchange", "MCX");
            assertEquals(ExchangeSegment.MCX_COMM, UpstoxDomainMapper.parseSegment(node));
        }

        @Test
        void defaultsToNseEqForUnknownExchange() {
            ObjectNode node = MAPPER.createObjectNode();
            node.put("exchange", "UNKNOWN");
            assertEquals(ExchangeSegment.NSE_EQ, UpstoxDomainMapper.parseSegment(node));
        }

        @Test
        void defaultsToNseEqWhenMissingExchange() {
            assertEquals(ExchangeSegment.NSE_EQ, UpstoxDomainMapper.parseSegment(MAPPER.createObjectNode()));
        }

        @Test
        void isCaseInsensitive() {
            ObjectNode node = MAPPER.createObjectNode();
            node.put("exchange", "nse");
            assertEquals(ExchangeSegment.NSE_EQ, UpstoxDomainMapper.parseSegment(node));
        }
    }

    // ─── parseTimestamp ───────────────────────────────────────────────────

    @Nested
    class ParseTimestampTests {

        @Test
        void parsesMillisTimestamp() {
            ObjectNode node = MAPPER.createObjectNode();
            node.put("ts", 1717000000000L);

            long result = UpstoxDomainMapper.parseTimestamp(node.get("ts"));

            assertEquals(1717000000000L, result);
        }

        @Test
        void parsesSecondsTimestamp() {
            ObjectNode node = MAPPER.createObjectNode();
            node.put("ts", 1717000000L);

            long result = UpstoxDomainMapper.parseTimestamp(node.get("ts"));

            assertEquals(1717000000000L, result);
        }

        @Test
        void parsesIso8601OffsetDateTime() {
            ObjectNode node = MAPPER.createObjectNode();
            node.put("ts", "2024-05-29T15:30:00+05:30");

            long result = UpstoxDomainMapper.parseTimestamp(node.get("ts"));

            // 15:30 IST = 10:00 UTC on 2024-05-29 = 1716976800000 ms
            assertEquals(1716976800000L, result);
        }

        @Test
        void parsesIso8601UtcZ() {
            ObjectNode node = MAPPER.createObjectNode();
            node.put("ts", "2024-05-29T10:00:00Z");

            long result = UpstoxDomainMapper.parseTimestamp(node.get("ts"));

            // 10:00 UTC on 2024-05-29 = 1716976800000 ms
            assertEquals(1716976800000L, result);
        }

        @Test
        void returnsNowForNullNode() {
            long before = System.currentTimeMillis();
            long result = UpstoxDomainMapper.parseTimestamp(null);
            long after = System.currentTimeMillis();

            assertTrue(result >= before && result <= after);
        }

        @Test
        void returnsNowForInvalidFormat() {
            ObjectNode node = MAPPER.createObjectNode();
            node.put("ts", "not-a-timestamp");

            long before = System.currentTimeMillis();
            long result = UpstoxDomainMapper.parseTimestamp(node.get("ts"));
            long after = System.currentTimeMillis();

            assertTrue(result >= before && result <= after);
        }
    }

    // ─── Mapping helpers ──────────────────────────────────────────────────

    @Nested
    class MappingHelpersTests {

        @Test
        void productTypeMapping() {
            assertEquals("MIS", UpstoxDomainMapper.mapProductType(ProductType.INTRADAY));
            assertEquals("MIS", UpstoxDomainMapper.mapProductType(ProductType.INTRADAY_MARGIN));
            assertEquals("CNC", UpstoxDomainMapper.mapProductType(ProductType.CNC));
            assertEquals("CNC", UpstoxDomainMapper.mapProductType(ProductType.DELIVERY));
            assertEquals("NRML", UpstoxDomainMapper.mapProductType(ProductType.CARRY_FORWARD));
            assertEquals("MARGIN", UpstoxDomainMapper.mapProductType(ProductType.MARGIN));
            assertEquals("MARGIN", UpstoxDomainMapper.mapProductType(ProductType.MARGIN_FUNDING));
        }

        @Test
        void validityMapping() {
            assertEquals("DAY", UpstoxDomainMapper.mapValidity(Validity.DAY));
            assertEquals("IOC", UpstoxDomainMapper.mapValidity(Validity.IOC));
        }

        @Test
        void orderTypeMapping() {
            assertEquals("MARKET", UpstoxDomainMapper.mapOrderType(OrderType.MARKET));
            assertEquals("LIMIT", UpstoxDomainMapper.mapOrderType(OrderType.LIMIT));
            assertEquals("SL", UpstoxDomainMapper.mapOrderType(OrderType.STOP_LOSS));
            assertEquals("SL-M", UpstoxDomainMapper.mapOrderType(OrderType.STOP_LOSS_MARKET));
        }

        @Test
        void sideMapping() {
            assertEquals("BUY", UpstoxDomainMapper.mapSide(Side.BUY));
            assertEquals("SELL", UpstoxDomainMapper.mapSide(Side.SELL));
        }

        @Test
        void parseSide() {
            ObjectNode buyNode = MAPPER.createObjectNode();
            buyNode.put("transaction_type", "BUY");
            assertEquals(Side.BUY, UpstoxDomainMapper.parseSide(buyNode));

            ObjectNode sellNode = MAPPER.createObjectNode();
            sellNode.put("transaction_type", "SELL");
            assertEquals(Side.SELL, UpstoxDomainMapper.parseSide(sellNode));
        }

        @Test
        void defaultParseSideToBuy() {
            assertEquals(Side.BUY, UpstoxDomainMapper.parseSide(MAPPER.createObjectNode()));
        }

        @Test
        void parseProductType() {
            ObjectNode misNode = MAPPER.createObjectNode();
            misNode.put("product", "MIS");
            assertEquals(ProductType.INTRADAY, UpstoxDomainMapper.parseProductType(misNode));

            ObjectNode cncNode = MAPPER.createObjectNode();
            cncNode.put("product", "CNC");
            assertEquals(ProductType.CNC, UpstoxDomainMapper.parseProductType(cncNode));

            ObjectNode nrmlNode = MAPPER.createObjectNode();
            nrmlNode.put("product", "NRML");
            assertEquals(ProductType.CARRY_FORWARD, UpstoxDomainMapper.parseProductType(nrmlNode));

            ObjectNode marginNode = MAPPER.createObjectNode();
            marginNode.put("product", "MARGIN");
            assertEquals(ProductType.MARGIN, UpstoxDomainMapper.parseProductType(marginNode));
        }

        @Test
        void defaultParseProductTypeToIntraday() {
            assertEquals(ProductType.INTRADAY, UpstoxDomainMapper.parseProductType(MAPPER.createObjectNode()));
        }

        @Test
        void parseOrderType() {
            ObjectNode marketNode = MAPPER.createObjectNode();
            marketNode.put("order_type", "MARKET");
            assertEquals(OrderType.MARKET, UpstoxDomainMapper.parseOrderType(marketNode));

            ObjectNode limitNode = MAPPER.createObjectNode();
            limitNode.put("order_type", "LIMIT");
            assertEquals(OrderType.LIMIT, UpstoxDomainMapper.parseOrderType(limitNode));

            ObjectNode slNode = MAPPER.createObjectNode();
            slNode.put("order_type", "SL");
            assertEquals(OrderType.STOP_LOSS, UpstoxDomainMapper.parseOrderType(slNode));

            ObjectNode slmNode = MAPPER.createObjectNode();
            slmNode.put("order_type", "SL-M");
            assertEquals(OrderType.STOP_LOSS_MARKET, UpstoxDomainMapper.parseOrderType(slmNode));
        }

        @Test
        void defaultParseOrderTypeToMarket() {
            assertEquals(OrderType.MARKET, UpstoxDomainMapper.parseOrderType(MAPPER.createObjectNode()));
        }

        @Test
        void parseStatus() {
            ObjectNode openNode = MAPPER.createObjectNode();
            openNode.put("status", "OPEN");
            assertEquals(OrderStatus.OPEN, UpstoxDomainMapper.parseStatus(openNode));

            ObjectNode completeNode = MAPPER.createObjectNode();
            completeNode.put("status", "COMPLETE");
            assertEquals(OrderStatus.TRADED, UpstoxDomainMapper.parseStatus(completeNode));

            ObjectNode cancelledNode = MAPPER.createObjectNode();
            cancelledNode.put("status", "CANCELLED");
            assertEquals(OrderStatus.CANCELLED, UpstoxDomainMapper.parseStatus(cancelledNode));

            ObjectNode rejectedNode = MAPPER.createObjectNode();
            rejectedNode.put("status", "REJECTED");
            assertEquals(OrderStatus.REJECTED, UpstoxDomainMapper.parseStatus(rejectedNode));
        }

        @Test
        void defaultParseStatusToUnknown() {
            assertEquals(OrderStatus.UNKNOWN, UpstoxDomainMapper.parseStatus(MAPPER.createObjectNode()));
        }
    }
}
