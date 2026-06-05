package com.tradej.broker.dhan.validator;

import com.tradej.broker.api.port.MarketDataProvider;
import com.tradej.broker.dhan.adapter.DhanInstrumentResolver;
import com.tradej.broker.dhan.config.DhanAuthMode;
import com.tradej.broker.dhan.config.DhanConnectionSettings;
import com.tradej.broker.dhan.exceptions.DhanValidationException;
import com.tradej.broker.dhan.instrument.DhanInstrumentDefinition;
import com.tradej.broker.dhan.validator.DhanOrderValidator;
import com.tradej.core.domain.model.InstrumentKey;
import com.tradej.core.domain.model.OrderPreview;
import com.tradej.core.domain.model.OrderRequest;
import com.tradej.core.domain.value.Exchange;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.OrderType;
import com.tradej.core.domain.value.ProductType;
import com.tradej.core.domain.value.Side;
import com.tradej.core.domain.value.Validity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@Tag("unit")
class DhanOrderValidatorUnitTest {

    private DhanInstrumentResolver instrumentResolver;
    private DhanConnectionSettings settings;
    private MarketDataProvider marketDataProvider;
    private DhanOrderValidator validator;

    @BeforeEach
    void setUp() {
        instrumentResolver = mock(DhanInstrumentResolver.class);
        settings = DhanConnectionSettings.sandboxWithDefaults("2505162156", "token");
        marketDataProvider = mock(MarketDataProvider.class);
        validator = new DhanOrderValidator(instrumentResolver, settings, marketDataProvider, true); // strict mode
    }

    private DhanInstrumentDefinition equityInstrument() {
        return new DhanInstrumentDefinition(
                "TCS",
                "TCS",
                Exchange.NSE,
                ExchangeSegment.NSE_EQ,
                "11536",
                "EQ",
                null,
                null,
                null,
                null,
                1L,
                5L,
                null
        );
    }

    private DhanInstrumentDefinition fnoInstrument() {
        return new DhanInstrumentDefinition(
                "NIFTY25JAN18000CE",
                "NIFTY 18000 CE",
                Exchange.NSE,
                ExchangeSegment.NSE_FNO,
                "50001",
                "OPTIDX",
                "NIFTY",
                LocalDate.of(2025, 1, 30),
                1800000L,
                com.tradej.core.domain.value.OptionType.CALL,
                50L,
                5L,
                "13"
        );
    }

    private DhanInstrumentDefinition mcxInstrument() {
        return new DhanInstrumentDefinition(
                "GOLDM25FEBFUT",
                "GOLDM FEB FUT",
                Exchange.MCX,
                ExchangeSegment.MCX_COMM,
                "60001",
                "FUTCOM",
                "GOLDM",
                LocalDate.of(2025, 2, 28),
                null,
                null,
                100L,
                1L,
                null
        );
    }

    private OrderRequest equityLimitOrder() {
        return new OrderRequest(
                "TCS",
                ExchangeSegment.NSE_EQ,
                Side.BUY,
                10L,
                OrderType.LIMIT,
                350000L,
                0L,
                ProductType.INTRADAY,
                Validity.DAY,
                "test-123"
        );
    }

    private OrderRequest equityMarketOrder() {
        return new OrderRequest(
                "TCS",
                ExchangeSegment.NSE_EQ,
                Side.BUY,
                10L,
                OrderType.MARKET,
                0L,
                0L,
                ProductType.INTRADAY,
                Validity.DAY,
                "test-124"
        );
    }

    private OrderRequest fnoLimitOrder() {
        return new OrderRequest(
                "NIFTY25JAN18000CE",
                ExchangeSegment.NSE_FNO,
                Side.BUY,
                50L,
                OrderType.LIMIT,
                10000L,
                0L,
                ProductType.INTRADAY,
                Validity.DAY,
                "test-125"
        );
    }

    private OrderRequest fnoCncOrder() {
        return new OrderRequest(
                "NIFTY25JAN18000CE",
                ExchangeSegment.NSE_FNO,
                Side.BUY,
                50L,
                OrderType.LIMIT,
                10000L,
                0L,
                ProductType.CNC,
                Validity.DAY,
                "test-126"
        );
    }

    private OrderRequest fnoInvalidLotSize() {
        return new OrderRequest(
                "NIFTY25JAN18000CE",
                ExchangeSegment.NSE_FNO,
                Side.BUY,
                51L, // Not multiple of 50
                OrderType.LIMIT,
                10000L,
                0L,
                ProductType.INTRADAY,
                Validity.DAY,
                "test-127"
        );
    }

    private OrderRequest highNotionalOrder() {
        return new OrderRequest(
                "TCS",
                ExchangeSegment.NSE_EQ,
                Side.BUY,
                10000L,
                OrderType.LIMIT,
                350000L,
                0L,
                ProductType.INTRADAY,
                Validity.DAY,
                "test-128"
        );
    }

    @Nested
    @DisplayName("Product Type Validation")
    class ProductTypeValidation {

        @Test
        @DisplayName("EQ segment allows INTRADAY, CNC, MARGIN")
        void eqSegmentAllowsValidProductTypes() {
            when(instrumentResolver.requireDhanDefinition("TCS", ExchangeSegment.NSE_EQ))
                    .thenReturn(equityInstrument());

            OrderRequest cncOrder = new OrderRequest(
                    "TCS", ExchangeSegment.NSE_EQ, Side.BUY, 10L,
                    OrderType.LIMIT, 350000L, 0L, ProductType.CNC, Validity.DAY, "test"
            );

            OrderPreview preview = validator.previewOrder(cncOrder);
            assertTrue(preview.valid());
            assertFalse(preview.hasErrors());
        }

        @Test
        @DisplayName("FNO segment rejects CNC")
        void fnoSegmentRejectsCnc() {
            when(instrumentResolver.requireDhanDefinition("NIFTY25JAN18000CE", ExchangeSegment.NSE_FNO))
                    .thenReturn(fnoInstrument());

            OrderPreview preview = validator.previewOrder(fnoCncOrder());
            assertFalse(preview.valid());
            assertTrue(preview.hasErrors());
            assertEquals(DhanValidationException.ValidationCode.INVALID_PRODUCT_TYPE_FOR_SEGMENT.name(),
                    preview.errors().get(0).code());
        }

        @Test
        @DisplayName("FNO segment allows INTRADAY and MARGIN")
        void fnoSegmentAllowsIntradayAndMargin() {
            when(instrumentResolver.requireDhanDefinition("NIFTY25JAN18000CE", ExchangeSegment.NSE_FNO))
                    .thenReturn(fnoInstrument());

            OrderRequest marginOrder = new OrderRequest(
                    "NIFTY25JAN18000CE", ExchangeSegment.NSE_FNO, Side.BUY, 50L,
                    OrderType.LIMIT, 10000L, 0L, ProductType.MARGIN, Validity.DAY, "test"
            );

            OrderPreview preview = validator.previewOrder(marginOrder);
            assertTrue(preview.valid());
        }

        @Test
        @DisplayName("MCX segment rejects CNC")
        void mcxSegmentRejectsCnc() {
            when(instrumentResolver.requireDhanDefinition("GOLDM25FEBFUT", ExchangeSegment.MCX_COMM))
                    .thenReturn(mcxInstrument());

            OrderRequest cncOrder = new OrderRequest(
                    "GOLDM25FEBFUT", ExchangeSegment.MCX_COMM, Side.BUY, 1L,
                    OrderType.LIMIT, 500000L, 0L, ProductType.CNC, Validity.DAY, "test"
            );

            OrderPreview preview = validator.previewOrder(cncOrder);
            assertFalse(preview.valid());
            assertTrue(preview.hasErrors());
        }
    }

    @Nested
    @DisplayName("Lot Size Validation")
    class LotSizeValidation {

        @Test
        @DisplayName("FNO rejects quantity not multiple of lot size")
        void fnoRejectsInvalidLotSize() {
            when(instrumentResolver.requireDhanDefinition("NIFTY25JAN18000CE", ExchangeSegment.NSE_FNO))
                    .thenReturn(fnoInstrument());

            OrderPreview preview = validator.previewOrder(fnoInvalidLotSize());
            assertFalse(preview.valid());
            assertTrue(preview.hasErrors());
            assertEquals(DhanValidationException.ValidationCode.INVALID_LOT_SIZE.name(),
                    preview.errors().get(0).code());
        }

        @Test
        @DisplayName("EQ allows any quantity (lot size = 1)")
        void eqAllowsAnyQuantity() {
            when(instrumentResolver.requireDhanDefinition("TCS", ExchangeSegment.NSE_EQ))
                    .thenReturn(equityInstrument());

            OrderRequest oddQty = new OrderRequest(
                    "TCS", ExchangeSegment.NSE_EQ, Side.BUY, 7L,
                    OrderType.LIMIT, 350000L, 0L, ProductType.INTRADAY, Validity.DAY, "test"
            );

            OrderPreview preview = validator.previewOrder(oddQty);
            assertTrue(preview.valid());
        }
    }

    @Nested
    @DisplayName("Notional Validation")
    class NotionalValidation {

        @Test
        @DisplayName("Notional exceeding limit generates warning")
        void highNotionalGeneratesWarning() {
            when(instrumentResolver.requireDhanDefinition("TCS", ExchangeSegment.NSE_EQ))
                    .thenReturn(equityInstrument());

            OrderPreview preview = validator.previewOrder(highNotionalOrder());
            assertTrue(preview.valid()); // Warnings don't invalidate
            assertTrue(preview.hasWarnings());
            assertTrue(preview.warnings().stream()
                    .anyMatch(w -> w.code().equals(DhanValidationException.ValidationCode.NOTIONAL_EXCEEDS_LIMIT.name())));
        }

        @Test
        @DisplayName("Market order with available LTP calculates correct notional")
        void marketOrderWithLtpCalculatesNotional() {
            when(instrumentResolver.requireDhanDefinition("TCS", ExchangeSegment.NSE_EQ))
                    .thenReturn(equityInstrument());
            when(marketDataProvider.getLtpPaisa(any(InstrumentKey.class)))
                    .thenReturn(350000L);

            OrderPreview preview = validator.previewOrder(equityMarketOrder());
            assertTrue(preview.valid());
            assertEquals(3_500_000L, preview.estimatedNotionalPaisa()); // 10 * 350000
            // Should have ORDER_TYPE_DEFAULTED_TO_LIMIT warning but NOT MARKET_ORDER_REQUIRES_LTP error
            assertTrue(preview.warnings().stream()
                    .anyMatch(w -> w.code().equals(DhanValidationException.ValidationCode.ORDER_TYPE_DEFAULTED_TO_LIMIT.name())));
            assertTrue(preview.warnings().stream()
                    .noneMatch(w -> w.code().equals(DhanValidationException.ValidationCode.MARKET_ORDER_REQUIRES_LTP.name())));
        }

        @Test
        @DisplayName("Market order without LTP returns error")
        void marketOrderWithoutLtpReturnsError() {
            when(instrumentResolver.requireDhanDefinition("TCS", ExchangeSegment.NSE_EQ))
                    .thenReturn(equityInstrument());
            when(marketDataProvider.getLtpPaisa(any(InstrumentKey.class)))
                    .thenReturn(0L);

            OrderPreview preview = validator.previewOrder(equityMarketOrder());
            assertFalse(preview.valid());
            assertTrue(preview.hasErrors());
            assertTrue(preview.errors().stream()
                    .anyMatch(w -> w.code().equals(DhanValidationException.ValidationCode.MARKET_ORDER_REQUIRES_LTP.name())));
        }

        @Test
        @DisplayName("Market order with LTP exception returns error")
        void marketOrderWithLtpExceptionReturnsError() {
            when(instrumentResolver.requireDhanDefinition("TCS", ExchangeSegment.NSE_EQ))
                    .thenReturn(equityInstrument());
            when(marketDataProvider.getLtpPaisa(any(InstrumentKey.class)))
                    .thenThrow(new RuntimeException("Feed disconnected"));

            OrderPreview preview = validator.previewOrder(equityMarketOrder());
            assertFalse(preview.valid());
            assertTrue(preview.hasErrors());
            assertTrue(preview.errors().stream()
                    .anyMatch(w -> w.code().equals(DhanValidationException.ValidationCode.MARKET_ORDER_REQUIRES_LTP.name())));
        }
    }

    @Nested
    @DisplayName("Order Type Validation")
    class OrderTypeValidation {

        @Test
        @DisplayName("MARKET order generates warning")
        void marketOrderGeneratesWarning() {
            when(instrumentResolver.requireDhanDefinition("TCS", ExchangeSegment.NSE_EQ))
                    .thenReturn(equityInstrument());

            OrderPreview preview = validator.previewOrder(equityMarketOrder());
            assertTrue(preview.hasWarnings());
            assertTrue(preview.warnings().stream()
                    .anyMatch(w -> w.code().equals(DhanValidationException.ValidationCode.ORDER_TYPE_DEFAULTED_TO_LIMIT.name())));
        }

        @Test
        @DisplayName("LIMIT order has no order type warning")
        void limitOrderNoWarning() {
            when(instrumentResolver.requireDhanDefinition("TCS", ExchangeSegment.NSE_EQ))
                    .thenReturn(equityInstrument());

            OrderPreview preview = validator.previewOrder(equityLimitOrder());
            assertTrue(preview.warnings().stream()
                    .noneMatch(w -> w.code().equals(DhanValidationException.ValidationCode.ORDER_TYPE_DEFAULTED_TO_LIMIT.name())));
        }
    }

    @Nested
    @DisplayName("Instrument Not Found")
    class InstrumentNotFound {

        @Test
        @DisplayName("Unknown instrument returns error preview")
        void unknownInstrumentReturnsError() {
            when(instrumentResolver.requireDhanDefinition("UNKNOWN", ExchangeSegment.NSE_EQ))
                    .thenThrow(new IllegalArgumentException("Instrument not found"));

            OrderRequest request = new OrderRequest(
                    "UNKNOWN", ExchangeSegment.NSE_EQ, Side.BUY, 10L,
                    OrderType.LIMIT, 10000L, 0L, ProductType.INTRADAY, Validity.DAY, "test"
            );

            OrderPreview preview = validator.previewOrder(request);
            assertFalse(preview.valid());
            assertTrue(preview.hasErrors());
            assertEquals(DhanValidationException.ValidationCode.INSTRUMENT_NOT_FOUND.name(),
                    preview.errors().get(0).code());
        }
    }

    @Nested
    @DisplayName("Strict vs Warn Mode")
    class StrictVsWarnMode {

        @Test
        @DisplayName("Strict mode throws on error")
        void strictModeThrows() {
            when(instrumentResolver.requireDhanDefinition("NIFTY25JAN18000CE", ExchangeSegment.NSE_FNO))
                    .thenReturn(fnoInstrument());

            assertThrows(DhanValidationException.class, () -> validator.validateOrThrow(fnoCncOrder()));
        }

        @Test
        @DisplayName("Warn mode logs but doesn't throw")
        void warnModeDoesNotThrow() {
            DhanOrderValidator warnValidator = new DhanOrderValidator(instrumentResolver, settings, marketDataProvider, false);
            when(instrumentResolver.requireDhanDefinition("NIFTY25JAN18000CE", ExchangeSegment.NSE_FNO))
                    .thenReturn(fnoInstrument());

            assertDoesNotThrow(() -> warnValidator.validateOrThrow(fnoCncOrder()));
        }
    }

    @Nested
    @DisplayName("Valid Order Preview")
    class ValidOrderPreview {

        @Test
        @DisplayName("Valid EQ order returns correct preview")
        void validEqOrderPreview() {
            when(instrumentResolver.requireDhanDefinition("TCS", ExchangeSegment.NSE_EQ))
                    .thenReturn(equityInstrument());

            OrderPreview preview = validator.previewOrder(equityLimitOrder());
            assertTrue(preview.valid());
            assertEquals("TCS", preview.symbol());
            assertEquals(ExchangeSegment.NSE_EQ, preview.exchangeSegment());
            assertEquals(Side.BUY, preview.side());
            assertEquals(10L, preview.quantity());
            assertEquals(350000L, preview.pricePaisa());
            assertEquals(ProductType.INTRADAY, preview.productType());
            assertEquals(3_500_000L, preview.estimatedNotionalPaisa()); // 10 * 350000
            assertTrue(preview.estimatedMarginPaisa() > 0);
        }

        @Test
        @DisplayName("Valid FNO order returns correct preview")
        void validFnoOrderPreview() {
            when(instrumentResolver.requireDhanDefinition("NIFTY25JAN18000CE", ExchangeSegment.NSE_FNO))
                    .thenReturn(fnoInstrument());

            OrderPreview preview = validator.previewOrder(fnoLimitOrder());
            assertTrue(preview.valid());
            assertEquals(50L, preview.quantity());
            assertEquals(10000L, preview.pricePaisa());
            assertEquals(500_000L, preview.estimatedNotionalPaisa()); // 50 * 10000
        }
    }
}