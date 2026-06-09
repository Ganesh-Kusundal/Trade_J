package com.tradej.broker.dhan.mapper;

import com.tradej.core.domain.value.Exchange;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.OrderStatus;
import com.tradej.core.domain.value.OrderType;
import com.tradej.core.domain.value.ProductType;
import com.tradej.core.domain.value.Side;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class DhanFieldMapperTest {

    // ─── OrderType ────────────────────────────────────────────────────────

    @Nested
    class OrderTypeTests {

        @Test
        void mapsLimit() {
            assertEquals(OrderType.LIMIT, DhanFieldMapper.orderType("LIMIT"));
        }

        @Test
        void mapsMarket() {
            assertEquals(OrderType.MARKET, DhanFieldMapper.orderType("MARKET"));
        }

        @Test
        void mapsStopLoss() {
            assertEquals(OrderType.STOP_LOSS, DhanFieldMapper.orderType("STOP_LOSS"));
        }

        @Test
        void mapsStopLossMarket() {
            assertEquals(OrderType.STOP_LOSS_MARKET, DhanFieldMapper.orderType("STOP_LOSS_MARKET"));
        }

        @Test
        void mapsForeverOrderFlagsToLimit() {
            assertEquals(OrderType.LIMIT, DhanFieldMapper.orderType("SINGLE"));
            assertEquals(OrderType.LIMIT, DhanFieldMapper.orderType("OCO"));
        }

        @Test
        void throwsForUnknownType() {
            assertThrows(IllegalStateException.class, () ->
                    DhanFieldMapper.orderType("UNKNOWN"));
        }

        @Test
        void throwsForNull() {
            assertThrows(IllegalStateException.class, () ->
                    DhanFieldMapper.orderType(null));
        }

        @Test
        void throwsForBlank() {
            assertThrows(IllegalStateException.class, () ->
                    DhanFieldMapper.orderType(""));
        }
    }

    // ─── ProductType ──────────────────────────────────────────────────────

    @Nested
    class ProductTypeTests {

        @Test
        void mapsCnc() {
            assertEquals(ProductType.CNC, DhanFieldMapper.productType("CNC"));
        }

        @Test
        void mapsMargin() {
            assertEquals(ProductType.MARGIN, DhanFieldMapper.productType("MARGIN"));
        }

        @Test
        void mapsCarryForward() {
            assertEquals(ProductType.CARRY_FORWARD, DhanFieldMapper.productType("CARRY_FORWARD"));
        }

        @Test
        void mapsIntraday() {
            assertEquals(ProductType.INTRADAY, DhanFieldMapper.productType("INTRADAY"));
        }

        @Test
        void throwsForUnknownType() {
            assertThrows(IllegalStateException.class, () ->
                    DhanFieldMapper.productType("DELIVERY"));
        }

        @Test
        void throwsForNull() {
            assertThrows(IllegalStateException.class, () ->
                    DhanFieldMapper.productType(null));
        }

        @Test
        void throwsForBlank() {
            assertThrows(IllegalStateException.class, () ->
                    DhanFieldMapper.productType(""));
        }

        @Test
        void isCaseInsensitive() {
            assertEquals(ProductType.INTRADAY, DhanFieldMapper.productType("intraday"));
            assertEquals(ProductType.CNC, DhanFieldMapper.productType("cnc"));
        }
    }

    // ─── Side ─────────────────────────────────────────────────────────────

    @Nested
    class SideTests {

        @Test
        void mapsBuy() {
            assertEquals(Side.BUY, DhanFieldMapper.side("BUY"));
        }

        @Test
        void mapsSell() {
            assertEquals(Side.SELL, DhanFieldMapper.side("SELL"));
        }

        @Test
        void mapsLongToBuy() {
            assertEquals(Side.BUY, DhanFieldMapper.side("LONG"));
        }

        @Test
        void mapsShortToSell() {
            assertEquals(Side.SELL, DhanFieldMapper.side("SHORT"));
        }

        @Test
        void mapsUnknownSideToUnknown() {
            assertEquals(Side.UNKNOWN, DhanFieldMapper.side("SOMETHING_ELSE"));
        }

        @Test
        void mapsNullToUnknown() {
            assertEquals(Side.UNKNOWN, DhanFieldMapper.side(null));
        }

        @Test
        void isCaseInsensitive() {
            assertEquals(Side.BUY, DhanFieldMapper.side("buy"));
            assertEquals(Side.SELL, DhanFieldMapper.side("sell"));
        }
    }

    // ─── RequiredSide ─────────────────────────────────────────────────────

    @Nested
    class RequiredSideTests {

        @Test
        void mapsValidSide() {
            assertEquals(Side.BUY, DhanFieldMapper.requiredSide("BUY"));
            assertEquals(Side.SELL, DhanFieldMapper.requiredSide("SELL"));
        }

        @Test
        void throwsForUnknownSide() {
            assertThrows(IllegalStateException.class, () ->
                    DhanFieldMapper.requiredSide("UNKNOWN"));
        }

        @Test
        void throwsForNull() {
            assertThrows(IllegalStateException.class, () ->
                    DhanFieldMapper.requiredSide(null));
        }
    }

    // ─── OrderStatus ──────────────────────────────────────────────────────

    @Nested
    class OrderStatusTests {

        @Test
        void mapsPending() {
            assertEquals(OrderStatus.PENDING, DhanFieldMapper.orderStatus("PENDING"));
        }

        @Test
        void mapsOpen() {
            assertEquals(OrderStatus.OPEN, DhanFieldMapper.orderStatus("OPEN"));
        }

        @Test
        void mapsTransitToPending() {
            assertEquals(OrderStatus.PENDING, DhanFieldMapper.orderStatus("TRANSIT"));
        }

        @Test
        void mapsConfirmToPending() {
            assertEquals(OrderStatus.PENDING, DhanFieldMapper.orderStatus("CONFIRM"));
        }

        @Test
        void mapsPartTraded() {
            assertEquals(OrderStatus.PART_TRADED, DhanFieldMapper.orderStatus("PART_TRADED"));
        }

        @Test
        void mapsTraded() {
            assertEquals(OrderStatus.TRADED, DhanFieldMapper.orderStatus("TRADED"));
        }

        @Test
        void mapsCompleteToTraded() {
            assertEquals(OrderStatus.TRADED, DhanFieldMapper.orderStatus("COMPLETE"));
        }

        @Test
        void mapsCancelled() {
            assertEquals(OrderStatus.CANCELLED, DhanFieldMapper.orderStatus("CANCELLED"));
        }

        @Test
        void mapsCanceledVariant() {
            assertEquals(OrderStatus.CANCELLED, DhanFieldMapper.orderStatus("CANCELED"));
        }

        @Test
        void mapsRejected() {
            assertEquals(OrderStatus.REJECTED, DhanFieldMapper.orderStatus("REJECTED"));
        }

        @Test
        void mapsExpiredToCancelled() {
            assertEquals(OrderStatus.CANCELLED, DhanFieldMapper.orderStatus("EXPIRED"));
        }

        @Test
        void mapsNullToUnknown() {
            assertEquals(OrderStatus.UNKNOWN, DhanFieldMapper.orderStatus(null));
        }

        @Test
        void mapsBlankToUnknown() {
            assertEquals(OrderStatus.UNKNOWN, DhanFieldMapper.orderStatus(""));
        }

        @Test
        void mapsUnknownToUnknown() {
            assertEquals(OrderStatus.UNKNOWN, DhanFieldMapper.orderStatus("SOMETHING_ELSE"));
        }

        @Test
        void isCaseInsensitive() {
            assertEquals(OrderStatus.PENDING, DhanFieldMapper.orderStatus("pending"));
            assertEquals(OrderStatus.TRADED, DhanFieldMapper.orderStatus("traded"));
        }
    }

    // ─── RequiredOrderStatus ──────────────────────────────────────────────

    @Nested
    class RequiredOrderStatusTests {

        @Test
        void mapsValidStatus() {
            assertEquals(OrderStatus.PENDING, DhanFieldMapper.requiredOrderStatus("PENDING"));
        }

        @Test
        void throwsForUnknown() {
            assertThrows(IllegalStateException.class, () ->
                    DhanFieldMapper.requiredOrderStatus("UNKNOWN"));
        }

        @Test
        void throwsForNull() {
            assertThrows(IllegalStateException.class, () ->
                    DhanFieldMapper.requiredOrderStatus(null));
        }
    }

    // ─── ExchangeSegment ──────────────────────────────────────────────────

    @Nested
    class ExchangeSegmentTests {

        @Test
        void mapsNseEqFromCode() {
            assertEquals(ExchangeSegment.NSE_EQ, DhanFieldMapper.segment("NSE_EQ"));
        }

        @Test
        void mapsNseFno() {
            assertEquals(ExchangeSegment.NSE_FNO, DhanFieldMapper.segment("NSE_FNO"));
        }

        @Test
        void mapsBseEq() {
            assertEquals(ExchangeSegment.BSE_EQ, DhanFieldMapper.segment("BSE_EQ"));
        }

        @Test
        void mapsBseFno() {
            assertEquals(ExchangeSegment.BSE_FNO, DhanFieldMapper.segment("BSE_FNO"));
        }

        @Test
        void mapsMcxComm() {
            assertEquals(ExchangeSegment.MCX_COMM, DhanFieldMapper.segment("MCX_COMM"));
        }

        @Test
        void mapsIdxI() {
            assertEquals(ExchangeSegment.IDX_I, DhanFieldMapper.segment("IDX_I"));
        }

        @Test
        void mapsNullToUnknown() {
            assertEquals(ExchangeSegment.fromCode(null), DhanFieldMapper.segment(null));
        }
    }

    // ─── EquitySegment ────────────────────────────────────────────────────

    @Nested
    class EquitySegmentTests {

        @Test
        void mapsNseToNseEq() {
            assertEquals(ExchangeSegment.NSE_EQ, DhanFieldMapper.equitySegment("NSE"));
        }

        @Test
        void mapsBseToBseEq() {
            assertEquals(ExchangeSegment.BSE_EQ, DhanFieldMapper.equitySegment("BSE"));
        }

        @Test
        void mapsNullToUnknown() {
            assertEquals(ExchangeSegment.UNKNOWN, DhanFieldMapper.equitySegment(null));
        }

        @Test
        void mapsBlankToUnknown() {
            assertEquals(ExchangeSegment.UNKNOWN, DhanFieldMapper.equitySegment(""));
        }

        @Test
        void mapsUnrecognizedToUnknown() {
            assertEquals(ExchangeSegment.UNKNOWN, DhanFieldMapper.equitySegment("MCX"));
        }

        @Test
        void isCaseInsensitive() {
            assertEquals(ExchangeSegment.NSE_EQ, DhanFieldMapper.equitySegment("nse"));
            assertEquals(ExchangeSegment.BSE_EQ, DhanFieldMapper.equitySegment("bse"));
        }

        @Test
        void trimsWhitespace() {
            assertEquals(ExchangeSegment.NSE_EQ, DhanFieldMapper.equitySegment("  NSE  "));
        }
    }

    // ─── toInstrument ─────────────────────────────────────────────────────

    @Nested
    class ToInstrumentTests {

        @Test
        void createsInstrumentWithGivenFields() {
            var instrument = DhanFieldMapper.toInstrument("TCS", "11536", ExchangeSegment.NSE_EQ);

            assertEquals("TCS", instrument.symbol());
            assertEquals("TCS", instrument.canonicalSymbol());
            assertEquals(ExchangeSegment.NSE_EQ, instrument.exchangeSegment());
            assertEquals(Exchange.NSE, instrument.exchange());
        }
    }
}
