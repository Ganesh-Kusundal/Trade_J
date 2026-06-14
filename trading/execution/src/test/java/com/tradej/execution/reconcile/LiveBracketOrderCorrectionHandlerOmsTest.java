package com.tradej.execution.reconcile;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.tradej.core.domain.event.EventMetadata;
import com.tradej.core.domain.event.PositionMismatch;
import com.tradej.core.domain.model.Order;
import com.tradej.core.domain.model.OrderRequest;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.OrderStatus;
import com.tradej.core.domain.value.OrderType;
import com.tradej.core.domain.value.ProductType;
import com.tradej.core.domain.value.Side;
import com.tradej.core.domain.value.Validity;
import com.tradej.execution.risk.PositionRiskHandler;
import com.tradej.execution.service.OrderManagementService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Pins the OMS integration contract of
 * {@link LiveBracketOrderCorrectionHandler} when wired with a real
 * {@link OrderManagementService} and {@link PositionRiskHandler}.
 *
 * <p>Four contracts:
 * <ol>
 *   <li><b>Low drift → OMS not invoked</b> — absDelta within tolerance
 *       means the handler short-circuits before the OMS block.</li>
 *   <li><b>High drift → OMS places order with correct {@link OrderRequest}</b>
 *       — verifies the handler builds the request correctly (symbol,
 *       segment, side, qty, order type, product, validity, correlationId)
 *       and forwards it to the OMS after a passing risk check.</li>
 *   <li><b>OMS throws → exception caught, WARN logged</b> — a broker
 *       error (timeout, rejected, circuit-breaker open) does NOT break
 *       the reconciliation pass.</li>
 *   <li><b>Risk check rejects → OMS NOT called</b> — when
 *       {@link PositionRiskHandler#canPlaceOrder} returns false, the
 *       handler logs WARN and skips placement.</li>
 * </ol>
 */
class LiveBracketOrderCorrectionHandlerOmsTest {

    private ListAppender<ILoggingEvent> appender;
    private Logger handlerLogger;

    @BeforeEach
    void attachAppender() {
        handlerLogger = (Logger) LoggerFactory.getLogger(LiveBracketOrderCorrectionHandler.class);
        appender = new ListAppender<>();
        appender.start();
        handlerLogger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        handlerLogger.detachAppender(appender);
        appender.stop();
    }

    private static PositionMismatch mismatch(String symbol, long paper, long broker) {
        return new PositionMismatch(EventMetadata.root(), symbol, paper, broker, "nse-eq::" + symbol);
    }

    private static Order fakeOrder(String id, String symbol, Side side, long qty) {
        return new Order(
                id,
                "drift-correction-nse-eq::" + symbol,
                symbol,
                ExchangeSegment.NSE_EQ,
                side,
                ProductType.INTRADAY,
                OrderType.MARKET,
                OrderStatus.OPEN,
                qty,
                0L,
                0L,
                0L,
                0L,
                null
        );
    }

    @Test
    void lowDrift_omsNotInvoked() {
        OrderManagementService oms = mock(OrderManagementService.class);
        PositionRiskHandler risk = mock(PositionRiskHandler.class);
        var handler = new LiveBracketOrderCorrectionHandler(
                new LoggingDriftAlerter(), 5L, 100L, oms, risk);

        // absDelta = 3, tolerance = 5 → within tolerance, handler returns early
        handler.onMismatch(mismatch("INFY", 100L, 103L));

        verifyNoInteractions(oms);
        verifyNoInteractions(risk);
    }

    @Test
    void highDrift_omsPlacesOrder() {
        OrderManagementService oms = mock(OrderManagementService.class);
        PositionRiskHandler risk = mock(PositionRiskHandler.class);
        when(risk.canPlaceOrder(anyString(), any(Side.class), anyLong())).thenReturn(true);
        when(oms.placeOrder(any(OrderRequest.class)))
                .thenAnswer(inv -> fakeOrder("ORD-1",
                        inv.getArgument(0, OrderRequest.class).symbol(),
                        inv.getArgument(0, OrderRequest.class).side(),
                        inv.getArgument(0, OrderRequest.class).quantity()));

        var handler = new LiveBracketOrderCorrectionHandler(
                new LoggingDriftAlerter(), 1L, 100L, oms, risk);
        // absDelta = 200, threshold = 100 → alerter + OMS both fire
        handler.onMismatch(mismatch("RELIANCE", 100L, 300L));

        // The structured WOULD PLACE log line should still fire.
        boolean hasWouldPlace = appender.list.stream()
                .anyMatch(e -> e.getMessage().startsWith("LIVE_BRACKET_CORRECTION_WOULD_PLACE"));
        assertTrue(hasWouldPlace, "WOULD PLACE log must still fire alongside OMS placement");

        // The OMS INFO log should fire with the orderId.
        boolean hasPlacedLog = appender.list.stream()
                .anyMatch(e -> e.getLevel() == Level.INFO
                        && e.getFormattedMessage().contains("Drift correction order placed")
                        && e.getFormattedMessage().contains("orderId=ORD-1"));
        assertTrue(hasPlacedLog, "OMS INFO log must fire with orderId");

        // The risk check was consulted with the right symbol + side + qty.
        verify(risk).canPlaceOrder("RELIANCE", Side.SELL, 200L);

        // The OrderRequest was built with the right fields.
        ArgumentCaptor<OrderRequest> captor = ArgumentCaptor.forClass(OrderRequest.class);
        verify(oms).placeOrder(captor.capture());
        OrderRequest req = captor.getValue();
        assertEquals("RELIANCE", req.symbol());
        assertEquals(ExchangeSegment.NSE_EQ, req.exchangeSegment());
        assertEquals(Side.SELL, req.side());
        assertEquals(200L, req.quantity());
        assertEquals(OrderType.MARKET, req.orderType());
        assertEquals(0L, req.pricePaisa());
        assertEquals(0L, req.triggerPricePaisa());
        assertEquals(ProductType.INTRADAY, req.productType());
        assertEquals(Validity.DAY, req.validity());
        assertEquals("drift-correction-nse-eq::RELIANCE", req.correlationId());
    }

    @Test
    void omsThrows_isCaught() {
        OrderManagementService oms = mock(OrderManagementService.class);
        PositionRiskHandler risk = mock(PositionRiskHandler.class);
        when(risk.canPlaceOrder(anyString(), any(Side.class), anyLong())).thenReturn(true);
        // Simulate a circuit-breaker-open failure (IllegalStateException as
        // OrderManagementService.placeOrder throws when the breaker is open).
        when(oms.placeOrder(any(OrderRequest.class)))
                .thenThrow(new IllegalStateException("trading circuit breaker is open"));

        var handler = new LiveBracketOrderCorrectionHandler(
                new LoggingDriftAlerter(), 1L, 100L, oms, risk);
        // absDelta = 200, threshold = 100

        // The handler must NOT propagate the exception.
        try {
            handler.onMismatch(mismatch("TCS", 100L, 300L));
        } catch (RuntimeException e) {
            throw new AssertionError("Handler must swallow OMS exceptions", e);
        }

        // The structured WOULD PLACE log line should still fire.
        boolean hasWouldPlace = appender.list.stream()
                .anyMatch(e -> e.getMessage().startsWith("LIVE_BRACKET_CORRECTION_WOULD_PLACE"));
        assertTrue(hasWouldPlace, "WOULD PLACE log must fire even when OMS throws");

        // A WARN log capturing the failure must fire.
        boolean hasWarnLog = appender.list.stream()
                .anyMatch(e -> e.getLevel() == Level.WARN
                        && e.getFormattedMessage().contains("Drift correction order failed")
                        && e.getFormattedMessage().contains("trading circuit breaker is open"));
        assertTrue(hasWarnLog, "Handler must log OMS failure at WARN with error message");

        // No "Drift correction order placed" INFO log must fire.
        boolean hasPlacedLog = appender.list.stream()
                .anyMatch(e -> e.getFormattedMessage().contains("Drift correction order placed"));
        assertTrue(!hasPlacedLog, "Placed-log must NOT fire when OMS throws");
    }

    @Test
    void riskCheckRejects_noOrderPlaced() {
        OrderManagementService oms = mock(OrderManagementService.class);
        PositionRiskHandler risk = mock(PositionRiskHandler.class);
        when(risk.canPlaceOrder(anyString(), any(Side.class), anyLong())).thenReturn(false);

        var handler = new LiveBracketOrderCorrectionHandler(
                new LoggingDriftAlerter(), 1L, 100L, oms, risk);
        // absDelta = 200, threshold = 100
        handler.onMismatch(mismatch("HDFC", 100L, 300L));

        // The risk check was consulted.
        verify(risk).canPlaceOrder("HDFC", Side.SELL, 200L);

        // The OMS must NOT have been called.
        verify(oms, never()).placeOrder(any(OrderRequest.class));

        // The structured WOULD PLACE log line should still fire (operator
        // visibility into the intent is preserved even when risk rejects).
        boolean hasWouldPlace = appender.list.stream()
                .anyMatch(e -> e.getMessage().startsWith("LIVE_BRACKET_CORRECTION_WOULD_PLACE"));
        assertTrue(hasWouldPlace, "WOULD PLACE log must fire even when risk rejects");

        // A WARN log capturing the risk rejection must fire.
        boolean hasWarnLog = appender.list.stream()
                .anyMatch(e -> e.getLevel() == Level.WARN
                        && e.getFormattedMessage().contains("Drift correction rejected by risk check"));
        assertTrue(hasWarnLog, "Handler must log risk rejection at WARN");

        // No "Drift correction order placed" INFO log must fire.
        boolean hasPlacedLog = appender.list.stream()
                .anyMatch(e -> e.getFormattedMessage().contains("Drift correction order placed"));
        assertTrue(!hasPlacedLog, "Placed-log must NOT fire when risk rejects");
    }
}
