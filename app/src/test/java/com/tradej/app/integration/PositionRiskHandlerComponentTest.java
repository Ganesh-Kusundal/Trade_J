package com.tradej.app.integration;

import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.event.EventMetadata;
import com.tradej.core.domain.event.SignalGenerated;
import com.tradej.core.domain.event.SignalPendingExecution;
import com.tradej.core.domain.event.SignalSuppressed;
import com.tradej.core.domain.value.Side;
import com.tradej.strategy.portfolio.PortfolioEngine;
import com.tradej.core.domain.event.TradeClosed;
import com.tradej.core.domain.event.TradeOpened;
import com.tradej.core.domain.model.OrderRequest;
import com.tradej.core.domain.model.RiskLimits;
import com.tradej.core.domain.port.NetPositionProvider;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.OrderType;
import com.tradej.core.domain.value.ProductType;
import com.tradej.core.domain.value.Side;
import com.tradej.core.domain.value.Validity;
import com.tradej.execution.risk.PositionRiskHandler;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("component")
class PositionRiskHandlerComponentTest {

    private static NetPositionProvider emptyPositions() {
        return Map::of;
    }

    private static OrderRequest sbinBuyOrder(long quantity, long pricePaisa, String correlationId) {
        return new OrderRequest(
                "SBIN",
                ExchangeSegment.NSE_EQ,
                Side.BUY,
                quantity,
                OrderType.LIMIT,
                pricePaisa,
                0L,
                ProductType.INTRADAY,
                Validity.DAY,
                correlationId
        );
    }

    private static SignalPendingExecution sbinSignal(String signalId, OrderRequest order) {
        return new SignalPendingExecution(
                EventMetadata.root(),
                signalId,
                order,
                Map.of("test", true)
        );
    }

    @Test
    void qualifiesSignalIntoExecutableOrderRequest() {
        PositionRiskHandler handler = new PositionRiskHandler(
                RiskLimits.withOpenPositionQuantity(1_000_000L, 3, 5_000_000L, 3),
                emptyPositions()
        );

        List<DomainEvent> emitted = new ArrayList<>();
        handler.onDomainEvent(sbinSignal("sig-1",
                sbinBuyOrder(10L, 75_000L, "correlation-1")), emitted::add);

        assertEquals(1, emitted.size(), "Passing signal must be forwarded to execution");
        assertInstanceOf(SignalPendingExecution.class, emitted.getFirst());
    }

    @Test
    void qualifiesSignalGeneratedIntoPendingExecution() {
        PortfolioEngine portfolio = new PortfolioEngine(10_000_000L, 50_000_000L);
        PositionRiskHandler handler = new PositionRiskHandler(
                RiskLimits.withOpenPositionQuantity(1_000_000L, 3, 5_000_000L, 3),
                emptyPositions(),
                portfolio
        );

        List<DomainEvent> emitted = new ArrayList<>();
        SignalGenerated signal = new SignalGenerated(
                EventMetadata.root(),
                "sig-gen-1",
                "SBIN",
                "5m",
                Side.BUY,
                75_000L,
                70_000L,
                80_000L,
                "test",
                Map.of("strategyName", "test-strategy", "quantity", 10L)
        );
        handler.onDomainEvent(signal, emitted::add);

        assertEquals(1, emitted.size());
        SignalPendingExecution pending = assertInstanceOf(SignalPendingExecution.class, emitted.getFirst());
        assertEquals("sig-gen-1", pending.signalId());
        assertEquals(10L, pending.orderRequest().quantity());
    }

    @Test
    void suppressesSignalWhenOrderValueBreachesRiskLimit() {
        PositionRiskHandler handler = new PositionRiskHandler(
                RiskLimits.withOpenPositionQuantity(1_000_000L, 3, 100_000L, 3),
                emptyPositions()
        );

        List<DomainEvent> emitted = new ArrayList<>();
        handler.onDomainEvent(sbinSignal("sig-2",
                sbinBuyOrder(10L, 75_000L, "correlation-2")), // 750_000 paisa > 100_000 limit
                emitted::add);

        SignalSuppressed suppressed = assertInstanceOf(SignalSuppressed.class, emitted.get(0));
        assertEquals("sig-2", suppressed.signalId());
        assertEquals("max_notional_value", suppressed.reason());
    }

    @Test
    void suppressesSignalWhenPositionFlipExceedsMaxOrderValue() {
        // Current position is short 100 — flipping would create a buy of 110
        NetPositionProvider shortPosition = () -> Map.of("SBIN", new NetPositionProvider.Position("SBIN", -100L, 0L));

        PositionRiskHandler handler = new PositionRiskHandler(
                RiskLimits.withOpenPositionQuantity(1_000_000L, 3, 50L, 3),
                shortPosition
        );

        List<DomainEvent> emitted = new ArrayList<>();
        handler.onDomainEvent(sbinSignal("sig-3",
                sbinBuyOrder(20L, 75_000L, "correlation-3")), emitted::add);

        SignalSuppressed suppressed = assertInstanceOf(SignalSuppressed.class, emitted.get(0));
        assertEquals("max_order_value_breach", suppressed.reason());
    }

    @Test
    void suppressesSignalWhenOrderValueExceedsFlipLimit() {
        // Position of 3 + sell qty of 1 = 4 > maxOpenPositionQuantity of 3 triggers max_order_value_breach
        NetPositionProvider atLimit = () -> Map.of("SBIN", new NetPositionProvider.Position("SBIN", 3L, 0L));

        PositionRiskHandler handler = new PositionRiskHandler(
                RiskLimits.withOpenPositionQuantity(1_000_000L, 3, 5_000_000L, 3),
                atLimit
        );

        List<DomainEvent> emitted = new ArrayList<>();
        // Sell order would flip from long 3 to short
        handler.onDomainEvent(new SignalPendingExecution(
                EventMetadata.root(),
                "sig-4",
                new OrderRequest(
                        "SBIN",
                        ExchangeSegment.NSE_EQ,
                        Side.SELL,
                        1L,
                        OrderType.LIMIT,
                        75_000L,
                        0L,
                        ProductType.INTRADAY,
                        Validity.DAY,
                        "correlation-4"
                ),
                Map.of("test", true)
        ), emitted::add);

        SignalSuppressed suppressed = assertInstanceOf(SignalSuppressed.class, emitted.get(0));
        assertEquals("max_order_value_breach", suppressed.reason());
    }

    @Test
    void killSwitchSuppressesAllSignals() {
        PositionRiskHandler handler = new PositionRiskHandler(
                RiskLimits.withOpenPositionQuantity(1_000_000L, 1, 5_000_000L, 3),
                emptyPositions()
        );

        // Trigger kill switch via consecutive losses
        handler.onDomainEvent(new TradeClosed(
                EventMetadata.root(),
                "t-1",
                "SBIN",
                75_000L,
                -500L, 10L,
                "stop_loss"
        ), e -> {});

        assertTrue(handler.isKillSwitchActive(), "Kill switch should be active after loss");

        List<DomainEvent> emitted = new ArrayList<>();
        handler.onDomainEvent(sbinSignal("sig-kill",
                sbinBuyOrder(1L, 75_000L, "correlation-kill")), emitted::add);

        SignalSuppressed suppressed = assertInstanceOf(SignalSuppressed.class, emitted.get(0));
        assertEquals("kill_switch_active", suppressed.reason());
    }

    @Test
    void openTradeTrackingIsCorrect() {
        PositionRiskHandler handler = new PositionRiskHandler(
                RiskLimits.withOpenPositionQuantity(1_000_000L, 3, 5_000_000L, 3),
                emptyPositions()
        );

        assertEquals(0, handler.getOpenTrades());

        handler.onDomainEvent(new TradeOpened(
                EventMetadata.root(),
                "t-1",
                "ord-1",
                "sig-1",
                "SBIN",
                Side.BUY,
                10L,
                75_000L,
                74_000L,
                77_000L
        ), e -> {});

        assertEquals(1, handler.getOpenTrades());

        handler.onDomainEvent(new TradeClosed(
                EventMetadata.root(),
                "t-1",
                "SBIN",
                75_000L,
                100L, 10L,
                "take_profit"
        ), e -> {});

        assertEquals(0, handler.getOpenTrades());
    }

    @Test
    void snapshotAndRestorePreservesState() {
        PositionRiskHandler handler = new PositionRiskHandler(
                RiskLimits.withOpenPositionQuantity(1_000_000L, 3, 5_000_000L, 3),
                emptyPositions()
        );

        // Open a trade first so the subsequent close decrements from a valid count.
        handler.onDomainEvent(new TradeOpened(
                EventMetadata.root(),
                "t-1",
                "ord-1",
                "sig-1",
                "SBIN",
                Side.BUY,
                10L,
                75_000L,
                74_000L,
                77_000L
        ), e -> {});

        handler.onDomainEvent(new TradeClosed(
                EventMetadata.root(),
                "t-1",
                "SBIN",
                75_000L,
                -500L, 10L,
                "stop_loss"
        ), e -> {});

        PositionRiskHandler.StateSnapshot snapshot = handler.snapshot();
        assertEquals(500L, snapshot.realizedLossPaisa());
        assertEquals(1, snapshot.consecutiveLosses());

        handler.resetDailyLimits();
        assertEquals(0L, handler.getRealizedLossPaisa());

        handler.restore(snapshot);
        assertEquals(500L, handler.getRealizedLossPaisa());
        assertEquals(1, handler.getConsecutiveLosses());
    }
}
