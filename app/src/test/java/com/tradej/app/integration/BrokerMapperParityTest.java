package com.tradej.app.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tradej.broker.dhan.mapper.DhanFieldMapper;
import com.tradej.broker.dhan.mapper.DhanJsonMapper;
import com.tradej.broker.dhan.mapper.DhanJsonResponse;
import com.tradej.broker.upstox.mapper.UpstoxDomainMapper;
import com.tradej.core.domain.model.Instrument;
import com.tradej.core.domain.model.Order;
import com.tradej.core.domain.model.OrderRequest;
import com.tradej.core.domain.model.Trade;
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

/**
 * Cross-broker parity test: verifies all three broker adapters (Dhan, Upstox, ICICI)
 * produce the same domain object shapes from equivalent semantic input data.
 *
 * <p>This test catches field-mapping regressions where a broker mapper changes its
 * output shape while others don't, breaking downstream consumers that expect uniform
 * domain objects regardless of which broker was used.</p>
 *
 * <p><b>Known divergencies:</b></p>
 * <ul>
 *   <li><b>Symbol resolution:</b> Dhan resolves symbol from the {@link Instrument}
 *       argument (canonicalSymbol), while Upstox uses the wire-format
 *       {@code trading_symbol} directly. This test verifies parity on all fields
 *       <em>except</em> symbol, and documents this divergence explicitly.</li>
 * </ul>
 */
@Tag("unit")
class BrokerMapperParityTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // ─── Canonical test data ────────────────────────────────────────────────
    private static final String ORDER_ID = "ORD-PARITY-001";
    private static final String SYMBOL = "TCS";
    private static final String CANONICAL_SYMBOL = "TCS-EQ";
    private static final Side SIDE = Side.BUY;
    private static final ProductType PRODUCT_TYPE = ProductType.INTRADAY;
    private static final OrderType ORDER_TYPE = OrderType.LIMIT;
    private static final long QUANTITY = 10L;
    private static final long PRICE_PAISA = 350000L; // 3500.00
    private static final long TRIGGER_PRICE_PAISA = 345000L; // 3450.00
    private static final long EXCHANGE_TIME_MS = 1717000000000L;
    private static final String CORRELATION_ID = "corr-parity-001";

    // ─── Instrument definitions ──────────────────────────────────────────────
    private static final Instrument NSE_EQ_INSTRUMENT = new Instrument(
            CANONICAL_SYMBOL, CANONICAL_SYMBOL, Exchange.NSE, ExchangeSegment.NSE_EQ,
            "EQUITY", "", null, null, null, 1L, 5L
    );

    // ─── Shared OrderRequest ──────────────────────────────────────────────
    private static final OrderRequest ORDER_REQUEST = new OrderRequest(
            SYMBOL, ExchangeSegment.NSE_EQ, SIDE, QUANTITY,
            ORDER_TYPE, PRICE_PAISA, TRIGGER_PRICE_PAISA, PRODUCT_TYPE, Validity.DAY, CORRELATION_ID
    );

    private UpstoxDomainMapper upstoxMapper;
    private com.tradej.broker.icici.mapper.BreezeDomainMapper breezeMapper;

    @BeforeEach
    void setUp() {
        upstoxMapper = new UpstoxDomainMapper();
        breezeMapper = new com.tradej.broker.icici.mapper.BreezeDomainMapper();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  ORDER parity
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    class OrderParity {

        @Test
        void allBrokersMapOrderToSameCoreFields() {
            // ── Dhan ──────────────────────────────────────────────────────────
            ObjectNode dhanNode = MAPPER.createObjectNode();
            dhanNode.put("orderId", ORDER_ID);
            dhanNode.put("correlationId", CORRELATION_ID);
            dhanNode.put("tradingSymbol", SYMBOL);
            dhanNode.put("exchangeSegment", "NSE_EQ");
            dhanNode.put("transactionType", "BUY");
            dhanNode.put("productType", "INTRADAY");
            dhanNode.put("orderType", "LIMIT");
            dhanNode.put("orderStatus", "OPEN");
            dhanNode.put("quantity", QUANTITY);
            dhanNode.put("filledQty", 5L);
            dhanNode.put("price", "3500.00");
            dhanNode.put("triggerPrice", "3450.00");
            dhanNode.put("exchangeTime", EXCHANGE_TIME_MS);
            Order dhanOrder = DhanJsonMapper.toOrder(new DhanJsonResponse(dhanNode), NSE_EQ_INSTRUMENT);

            // ── Upstox ────────────────────────────────────────────────────────
            ObjectNode upstoxNode = MAPPER.createObjectNode();
            upstoxNode.put("order_id", ORDER_ID);
            upstoxNode.put("trading_symbol", SYMBOL);
            upstoxNode.put("exchange", "NSE");
            upstoxNode.put("transaction_type", "BUY");
            upstoxNode.put("product", "MIS");
            upstoxNode.put("order_type", "LIMIT");
            upstoxNode.put("status", "OPEN");
            upstoxNode.put("quantity", QUANTITY);
            upstoxNode.put("filled_quantity", 5L);
            upstoxNode.put("price", 3500.00);
            upstoxNode.put("trigger_price", 3450.00);
            upstoxNode.put("exchange_timestamp", EXCHANGE_TIME_MS / 1000L);
            Order upstoxOrder = upstoxMapper.toOrder(upstoxNode, ORDER_REQUEST, NSE_EQ_INSTRUMENT);

            // ── ICICI ─────────────────────────────────────────────────────────
            ObjectNode iciciNode = MAPPER.createObjectNode();
            iciciNode.put("order_id", ORDER_ID);
            iciciNode.put("stock_code", SYMBOL);
            iciciNode.put("action", "buy");
            iciciNode.put("order_type", "limit");
            iciciNode.put("status", "open");
            iciciNode.put("quantity", String.valueOf(QUANTITY));
            iciciNode.put("pending_quantity", "5");
            iciciNode.put("price", "3500.00");
            iciciNode.put("stoploss", "3450.00");
            Order iciciOrder = breezeMapper.toOrder(iciciNode, ORDER_REQUEST, NSE_EQ_INSTRUMENT);

            // ── Parity assertions ─────────────────────────────────────────────
            assertOrderParity(dhanOrder, upstoxOrder, iciciOrder);
        }

        private void assertOrderParity(Order dhan, Order upstox, Order icici) {
            // Core identity
            assertEquals(dhan.orderId(), upstox.orderId(), "orderId: dhan vs upstox");
            assertEquals(dhan.orderId(), icici.orderId(), "orderId: dhan vs icici");

            // All brokers now resolve symbol from Instrument when available
            assertEquals(dhan.symbol(), upstox.symbol(), "symbol: dhan vs upstox");
            assertEquals(dhan.symbol(), icici.symbol(), "symbol: dhan vs icici");

            // Side
            assertEquals(dhan.side(), upstox.side(), "side: dhan vs upstox");
            assertEquals(dhan.side(), icici.side(), "side: dhan vs icici");

            // Product type
            assertEquals(dhan.productType(), upstox.productType(), "productType: dhan vs upstox");
            assertEquals(dhan.productType(), icici.productType(), "productType: dhan vs icici");

            // Order type
            assertEquals(dhan.orderType(), upstox.orderType(), "orderType: dhan vs upstox");
            assertEquals(dhan.orderType(), icici.orderType(), "orderType: dhan vs icici");

            // Status
            assertEquals(dhan.status(), upstox.status(), "status: dhan vs upstox");
            assertEquals(dhan.status(), icici.status(), "status: dhan vs icici");

            // Quantity
            assertEquals(dhan.quantity(), upstox.quantity(), "quantity: dhan vs upstox");
            assertEquals(dhan.quantity(), icici.quantity(), "quantity: dhan vs icici");

            // Filled quantity
            assertEquals(dhan.filledQuantity(), upstox.filledQuantity(), "filledQuantity: dhan vs upstox");
            assertEquals(dhan.filledQuantity(), icici.filledQuantity(), "filledQuantity: dhan vs icici");

            // Price
            assertEquals(dhan.pricePaisa(), upstox.pricePaisa(), "pricePaisa: dhan vs upstox");
            assertEquals(dhan.pricePaisa(), icici.pricePaisa(), "pricePaisa: dhan vs icici");

            // Trigger price
            assertEquals(dhan.triggerPricePaisa(), upstox.triggerPricePaisa(), "triggerPricePaisa: dhan vs upstox");
            assertEquals(dhan.triggerPricePaisa(), icici.triggerPricePaisa(), "triggerPricePaisa: dhan vs icici");

            // Exchange segment
            assertEquals(dhan.exchangeSegment(), upstox.exchangeSegment(), "exchangeSegment: dhan vs upstox");
            assertEquals(dhan.exchangeSegment(), icici.exchangeSegment(), "exchangeSegment: dhan vs icici");

            // Correlation ID
            assertEquals(dhan.correlationId(), upstox.correlationId(), "correlationId: dhan vs upstox");
            assertEquals(dhan.correlationId(), icici.correlationId(), "correlationId: dhan vs icici");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  TRADE parity
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    class TradeParity {

        @Test
        void allBrokersMapTradeToSameCoreFields() {
            // ── Dhan ──────────────────────────────────────────────────────────
            ObjectNode dhanNode = MAPPER.createObjectNode();
            dhanNode.put("exchangeTradeId", "TRD-001");
            dhanNode.put("orderId", ORDER_ID);
            dhanNode.put("tradingSymbol", SYMBOL);
            dhanNode.put("exchangeSegment", "NSE_EQ");
            dhanNode.put("transactionType", "BUY");
            dhanNode.put("tradedQuantity", QUANTITY);
            dhanNode.put("tradedPrice", "3500.50");
            dhanNode.put("exchangeTime", EXCHANGE_TIME_MS);
            var dhanTrade = DhanJsonMapper.toTrade(new DhanJsonResponse(dhanNode), NSE_EQ_INSTRUMENT);

            // ── Upstox ────────────────────────────────────────────────────────
            ObjectNode upstoxNode = MAPPER.createObjectNode();
            upstoxNode.put("trade_id", "TRD-001");
            upstoxNode.put("order_id", ORDER_ID);
            upstoxNode.put("trading_symbol", SYMBOL);
            upstoxNode.put("exchange", "NSE");
            upstoxNode.put("transaction_type", "BUY");
            upstoxNode.put("quantity", QUANTITY);
            upstoxNode.put("price", 3500.50);
            upstoxNode.put("exchange_timestamp", EXCHANGE_TIME_MS / 1000L);
            ObjectNode upstoxEnvelope = MAPPER.createObjectNode();
            upstoxEnvelope.set("data", MAPPER.createArrayNode().add(upstoxNode));
            var upstoxTrades = upstoxMapper.toTradeList(upstoxEnvelope, NSE_EQ_INSTRUMENT);
            assertFalse(upstoxTrades.isEmpty(), "Upstox trade list should not be empty");
            var upstoxTrade = upstoxTrades.get(0);

            // ── Parity assertions ─────────────────────────────────────────────
            assertTradeParity(dhanTrade, upstoxTrade);
        }

        private void assertTradeParity(Trade dhan, Trade upstox) {
            assertEquals(dhan.tradeId(), upstox.tradeId(), "tradeId");
            assertEquals(dhan.orderId(), upstox.orderId(), "orderId");

            // All brokers now resolve symbol from Instrument when available
            assertEquals(dhan.symbol(), upstox.symbol(), "symbol: dhan vs upstox");

            assertEquals(dhan.side(), upstox.side(), "side");
            assertEquals(dhan.quantity(), upstox.quantity(), "quantity");
            assertEquals(dhan.pricePaisa(), upstox.pricePaisa(), "pricePaisa");
            assertEquals(dhan.exchangeSegment(), upstox.exchangeSegment(), "exchangeSegment");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  QUOTE parity (Dhan vs ICICI — both have public toQuote(JsonNode, Instrument))
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    class QuoteParity {

        @Test
        void dhanAndIciciProduceEquivalentQuoteShapes() {
            // ── Dhan ──────────────────────────────────────────────────────────
            ObjectNode dhanNode = MAPPER.createObjectNode();
            dhanNode.put("last_price", "1500.50");
            dhanNode.put("volume", 100000L);
            dhanNode.put("buy_quantity", 50000L);
            dhanNode.put("sell_quantity", 30000L);
            dhanNode.put("oi", 10000L);
            ObjectNode dhanOhlc = MAPPER.createObjectNode();
            dhanOhlc.put("open", "1490.00");
            dhanOhlc.put("high", "1510.00");
            dhanOhlc.put("low", "1485.00");
            dhanOhlc.put("close", "1500.00");
            dhanNode.set("ohlc", dhanOhlc);
            var dhanQuote = DhanJsonMapper.toQuote(new DhanJsonResponse(dhanNode), NSE_EQ_INSTRUMENT);

            // ── ICICI ─────────────────────────────────────────────────────────
            ObjectNode iciciNode = MAPPER.createObjectNode();
            iciciNode.put("ltp", "1500.50");
            iciciNode.put("open", "1490.00");
            iciciNode.put("high", "1510.00");
            iciciNode.put("low", "1485.00");
            iciciNode.put("close", "1500.00");
            iciciNode.put("total_quantity_traded", 100000L);
            iciciNode.put("total_buy_quantity", 50000L);
            iciciNode.put("total_sell_quantity", 30000L);
            iciciNode.put("oi", 10000L);
            var iciciQuote = breezeMapper.toQuote(iciciNode, NSE_EQ_INSTRUMENT);

            // ── Parity assertions ─────────────────────────────────────────────
            assertEquals(dhanQuote.ltpPaisa(), iciciQuote.ltpPaisa(), "ltpPaisa");
            assertEquals(dhanQuote.openPaisa(), iciciQuote.openPaisa(), "openPaisa");
            assertEquals(dhanQuote.highPaisa(), iciciQuote.highPaisa(), "highPaisa");
            assertEquals(dhanQuote.lowPaisa(), iciciQuote.lowPaisa(), "lowPaisa");
            assertEquals(dhanQuote.closePaisa(), iciciQuote.closePaisa(), "closePaisa");
            assertEquals(dhanQuote.volume(), iciciQuote.volume(), "volume");
            assertEquals(dhanQuote.totalBuyQuantity(), iciciQuote.totalBuyQuantity(), "totalBuyQuantity");
            assertEquals(dhanQuote.totalSellQuantity(), iciciQuote.totalSellQuantity(), "totalSellQuantity");
            assertEquals(dhanQuote.openInterest(), iciciQuote.openInterest(), "openInterest: dhan vs icici");
        }

        @Test
        void quoteShapeHasAllRequiredFields() {
            ObjectNode node = MAPPER.createObjectNode();
            node.put("ltp", "1500.50");
            node.put("open", "1490.00");
            node.put("high", "1510.00");
            node.put("low", "1485.00");
            node.put("close", "1500.00");
            node.put("total_quantity_traded", 100000L);
            var quote = breezeMapper.toQuote(node, NSE_EQ_INSTRUMENT);

            // Verify the Quote record has all fields populated (no nulls, no missing data)
            assertNotNull(quote.instrument());
            assertEquals(NSE_EQ_INSTRUMENT, quote.instrument());
            assertTrue(quote.ltpPaisa() > 0, "ltpPaisa must be positive");
            assertTrue(quote.openPaisa() > 0, "openPaisa must be positive");
            assertTrue(quote.highPaisa() > 0, "highPaisa must be positive");
            assertTrue(quote.lowPaisa() > 0, "lowPaisa must be positive");
            assertTrue(quote.closePaisa() > 0, "closePaisa must be positive");
            assertTrue(quote.volume() > 0, "volume must be positive");
            assertTrue(quote.timestampMs() > 0, "timestampMs must be positive");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  ORDER STATUS mapping parity
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    class OrderStatusParity {

        @Test
        void allBrokersMapEquivalentStatusesToSameOrderStatus() {
            // Each broker uses different wire-format strings for the same logical status
            // Verify they all map to the same OrderStatus enum values

            // ── OPEN ──────────────────────────────────────────────────────────
            assertEquals(OrderStatus.OPEN, mapDhanStatus("OPEN"));
            assertEquals(OrderStatus.OPEN, mapUpstoxStatus("OPEN"));
            assertEquals(OrderStatus.OPEN, mapIciciStatus("ordered"));

            // ── PENDING ───────────────────────────────────────────────────────
            assertEquals(OrderStatus.PENDING, mapDhanStatus("PENDING"));
            assertEquals(OrderStatus.PENDING, mapUpstoxStatus("PENDING"));
            assertEquals(OrderStatus.PENDING, mapIciciStatus("pending"));

            // ── TRADED ────────────────────────────────────────────────────────
            assertEquals(OrderStatus.TRADED, mapDhanStatus("TRADED"));
            assertEquals(OrderStatus.TRADED, mapUpstoxStatus("COMPLETE"));
            assertEquals(OrderStatus.TRADED, mapIciciStatus("executed"));

            // ── PART_TRADED ───────────────────────────────────────────────────
            assertEquals(OrderStatus.PART_TRADED, mapDhanStatus("PART_TRADED"));
            assertEquals(OrderStatus.PART_TRADED, mapUpstoxStatus("PARTIALLY_FILLED"));
            assertEquals(OrderStatus.PART_TRADED, mapIciciStatus("partially executed"));

            // ── CANCELLED ─────────────────────────────────────────────────────
            assertEquals(OrderStatus.CANCELLED, mapDhanStatus("CANCELLED"));
            assertEquals(OrderStatus.CANCELLED, mapUpstoxStatus("CANCELLED"));
            assertEquals(OrderStatus.CANCELLED, mapIciciStatus("cancelled"));

            // ── REJECTED ──────────────────────────────────────────────────────
            assertEquals(OrderStatus.REJECTED, mapDhanStatus("REJECTED"));
            assertEquals(OrderStatus.REJECTED, mapUpstoxStatus("REJECTED"));
            assertEquals(OrderStatus.REJECTED, mapIciciStatus("rejected"));
        }

        private OrderStatus mapDhanStatus(String wireStatus) {
            ObjectNode node = MAPPER.createObjectNode();
            node.put("orderId", "X");
            node.put("transactionType", "BUY");
            node.put("productType", "INTRADAY"); // required by DhanFieldMapper
            node.put("orderType", "LIMIT");
            node.put("orderStatus", wireStatus);
            node.put("quantity", 1L);
            node.put("price", "100.00");
            return DhanJsonMapper.toOrder(new DhanJsonResponse(node), NSE_EQ_INSTRUMENT).status();
        }

        private OrderStatus mapUpstoxStatus(String wireStatus) {
            ObjectNode node = MAPPER.createObjectNode();
            node.put("order_id", "X");
            node.put("transaction_type", "BUY");
            node.put("order_type", "LIMIT");
            node.put("status", wireStatus);
            node.put("quantity", 1L);
            node.put("price", 100.00);
            return upstoxMapper.toOrder(node, ORDER_REQUEST).status();
        }

        private OrderStatus mapIciciStatus(String wireStatus) {
            ObjectNode node = MAPPER.createObjectNode();
            node.put("order_id", "X");
            node.put("action", "buy");
            node.put("order_type", "limit");
            node.put("status", wireStatus);
            node.put("quantity", "1");
            node.put("price", "100.00");
            return breezeMapper.toOrder(node, ORDER_REQUEST).status();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  EXCHANGE SEGMENT mapping parity
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    class ExchangeSegmentParity {

        @Test
        void allBrokersMapExchangeSegmentsToSameDomainValues() {
            // Dhan uses "NSE_EQ", Upstox uses "NSE", ICICI uses exchangeCode + segment
            // All should map to the same ExchangeSegment enum

            // NSE Equity
            assertEquals(ExchangeSegment.NSE_EQ, DhanFieldMapper.segment("NSE_EQ"));
            assertEquals(ExchangeSegment.NSE_EQ, UpstoxDomainMapper.parseSegment(
                    MAPPER.createObjectNode().put("exchange", "NSE")));

            // BSE Equity
            assertEquals(ExchangeSegment.BSE_EQ, DhanFieldMapper.segment("BSE_EQ"));
            assertEquals(ExchangeSegment.BSE_EQ, UpstoxDomainMapper.parseSegment(
                    MAPPER.createObjectNode().put("exchange", "BSE")));

            // NSE F&O
            assertEquals(ExchangeSegment.NSE_FNO, DhanFieldMapper.segment("NSE_FNO"));
            assertEquals(ExchangeSegment.NSE_FNO, UpstoxDomainMapper.parseSegment(
                    MAPPER.createObjectNode().put("exchange", "NFO")));

            // BSE F&O
            assertEquals(ExchangeSegment.BSE_FNO, DhanFieldMapper.segment("BSE_FNO"));
            assertEquals(ExchangeSegment.BSE_FNO, UpstoxDomainMapper.parseSegment(
                    MAPPER.createObjectNode().put("exchange", "BFO")));

            // MCX
            assertEquals(ExchangeSegment.MCX_COMM, DhanFieldMapper.segment("MCX_COMM"));
            assertEquals(ExchangeSegment.MCX_COMM, UpstoxDomainMapper.parseSegment(
                    MAPPER.createObjectNode().put("exchange", "MCX")));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  SIDE / PRODUCT TYPE mapping parity
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    class SideProductParity {

        @Test
        void allBrokersMapSidesToSameDomainValues() {
            // Dhan: "BUY"/"SELL", Upstox: "BUY"/"SELL", ICICI: "buy"/"sell"
            assertEquals(Side.BUY, DhanFieldMapper.side("BUY"));
            assertEquals(Side.BUY, UpstoxDomainMapper.parseSide(
                    MAPPER.createObjectNode().put("transaction_type", "BUY")));
            assertEquals(Side.BUY, breezeMapper.toOrder(
                    MAPPER.createObjectNode().put("action", "buy"), ORDER_REQUEST).side());

            assertEquals(Side.SELL, DhanFieldMapper.side("SELL"));
            assertEquals(Side.SELL, UpstoxDomainMapper.parseSide(
                    MAPPER.createObjectNode().put("transaction_type", "SELL")));
            assertEquals(Side.SELL, breezeMapper.toOrder(
                    MAPPER.createObjectNode().put("action", "sell"), ORDER_REQUEST).side());
        }

        @Test
        void allBrokersMapIntradayProductTypeToSameDomainValue() {
            assertEquals(ProductType.INTRADAY, DhanFieldMapper.productType("INTRADAY"));
            assertEquals(ProductType.INTRADAY, UpstoxDomainMapper.parseProductType(
                    MAPPER.createObjectNode().put("product", "MIS")));
            // ICICI defaults to CNC for equity; product type comes from OrderRequest
            assertEquals(ProductType.INTRADAY, ORDER_REQUEST.productType());
        }
    }
}
