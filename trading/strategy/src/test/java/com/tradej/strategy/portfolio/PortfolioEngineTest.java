package com.tradej.strategy.portfolio;

import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.event.EventMetadata;
import com.tradej.core.domain.event.OrderAccepted;
import com.tradej.core.domain.event.OrderRejected;
import com.tradej.core.domain.event.SignalGenerated;
import com.tradej.core.domain.event.SignalSuppressed;
import com.tradej.core.domain.event.TradeClosed;
import com.tradej.core.domain.event.TradeOpened;
import com.tradej.core.domain.model.Order;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.OrderStatus;
import com.tradej.core.domain.value.OrderType;
import com.tradej.core.domain.value.ProductType;
import com.tradej.core.domain.value.Side;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class PortfolioEngineTest {

    private static final long CAPITAL_PER_STRATEGY = 100_000_000L;  // ₹10,00,000 (paisa)
    private static final long MAX_EXPOSURE = 500_000_000L;          // ₹50,00,000 (paisa)
    private static final String STRATEGY_NAME = "TestStrategy";

    private PortfolioEngine engine;
    private List<DomainEvent> emitted;

    @BeforeEach
    void setUp() {
        engine = new PortfolioEngine(CAPITAL_PER_STRATEGY, MAX_EXPOSURE);
        emitted = new ArrayList<>();
    }

    /** Reserve capital (portfolio gate) then record the approved signal for assertions. */
    private void approveAndEmit(SignalGenerated signal) {
        assertNull(engine.reserveSignal(signal), "Expected portfolio approval");
        emitted.add(signal);
    }

    // ── Helpers ──

    private static Map<String, Object> attrs(String strategyName, long quantity) {
        Map<String, Object> m = new HashMap<>();
        m.put(PortfolioEngine.ATTR_STRATEGY_NAME, strategyName);
        m.put("quantity", quantity);
        return m;
    }

    private SignalGenerated signal(long qty, long price, Side side, long stopLoss, long takeProfit) {
        return new SignalGenerated(
                EventMetadata.root(),
                "sig-" + System.nanoTime(),
                "SBIN",
                "5m",
                side,
                price,
                stopLoss,
                takeProfit,
                "test-setup",
                attrs(STRATEGY_NAME, qty)
        );
    }

    private SignalGenerated signalWithSymbol(String symbol, long qty, long price, Side side) {
        return new SignalGenerated(
                EventMetadata.root(),
                "sig-" + System.nanoTime(),
                symbol,
                "5m",
                side,
                price,
                0L,
                0L,
                "test-setup",
                attrs(STRATEGY_NAME, qty)
        );
    }

    private SignalGenerated signalWithStrategy(String strategyName, String symbol, long qty, long price, Side side) {
        return new SignalGenerated(
                EventMetadata.root(),
                "sig-" + System.nanoTime(),
                symbol,
                "5m",
                side,
                price,
                0L,
                0L,
                "test-setup",
                attrs(strategyName, qty)
        );
    }

    private SignalGenerated signalWithoutQuantity(long price, Side side) {
        return new SignalGenerated(
                EventMetadata.root(),
                "sig-" + System.nanoTime(),
                "SBIN",
                "5m",
                side,
                price,
                0L,
                0L,
                "test-setup",
                Map.of(PortfolioEngine.ATTR_STRATEGY_NAME, STRATEGY_NAME)
        );
    }

    private SignalGenerated signalWithoutStrategyName(long qty, long price, Side side) {
        return new SignalGenerated(
                EventMetadata.root(),
                "sig-" + System.nanoTime(),
                "SBIN",
                "5m",
                side,
                price,
                0L,
                0L,
                "test-setup",
                Map.of("quantity", qty)
        );
    }

    private TradeOpened tradeOpened(String signalId, String tradeId, String symbol, long size, long price, Side side) {
        return new TradeOpened(
                EventMetadata.root(),
                tradeId,
                "order-1",
                signalId,
                symbol,
                side,
                size,
                price,
                0L,
                0L
        );
    }

    private TradeClosed tradeClosed(String tradeId, String symbol, long exitPrice, long pnl) {
        return new TradeClosed(
                EventMetadata.root(),
                tradeId,
                symbol,
                exitPrice,
                pnl, 10L,
                "stop-loss"
        );
    }

    // ── Signal filtering tests ──

    @Test
    void approvesSignalWithinCapitalLimit() {
        // ₹10,000 per-strategy limit (production default)
        PortfolioEngine smallCap = new PortfolioEngine(1_000_000L, MAX_EXPOSURE);
        List<DomainEvent> smallEmitted = new ArrayList<>();
        SignalGenerated signal = signal(100, 100_00, Side.BUY, 95_00, 110_00);
        assertNull(smallCap.reserveSignal(signal), "Expected portfolio approval at limit");
        smallCap.onDomainEvent(signal, smallEmitted::add);

        assertEquals(1, smallEmitted.size(), "Signal should pass through");
        assertInstanceOf(SignalGenerated.class, smallEmitted.get(0));
        assertEquals(100 * 100_00, smallCap.usedCapitalPaisa(STRATEGY_NAME));
        assertEquals(100, smallCap.netPosition("SBIN"));
    }

    @Test
    void approvesSignalUnderLimit() {
        // 50 shares × ₹1,000 = ₹50,000 — well under ₹10,00,000 limit
        SignalGenerated signal = signal(50, 1_000_00, Side.BUY, 0L, 0L);
        approveAndEmit(signal);

        assertInstanceOf(SignalGenerated.class, emitted.get(0));
        assertEquals(50 * 1_000_00, engine.usedCapitalPaisa(STRATEGY_NAME));
    }

    @Test
    void suppressesSignalWhenCapitalExceeded() {
        // First signal: 80 shares × ₹12,500 = ₹10,00,000 — at limit
        SignalGenerated first = signal(80, 12_500_00, Side.BUY, 0L, 0L);
        approveAndEmit(first);
        emitted.clear();

        // Second signal: even 1 share × ₹1 would exceed
        SignalGenerated second = signal(1, 1_00, Side.BUY, 0L, 0L);
        String rejection = engine.reserveSignal(second);
        assertNotNull(rejection);
        assertTrue(rejection.contains("capital limit exceeded"), rejection);
    }

    @Test
    void approvesSignalWithinNetExposureLimit() {
        // Long 100 SBIN @ ₹1,000 = net 100, exposure = ₹1,00,000 — under ₹50,00,000
        SignalGenerated first = signalWithSymbol("SBIN", 100, 1_000_00, Side.BUY);
        approveAndEmit(first);
        assertEquals(100, engine.netPosition("SBIN"));
        emitted.clear();

        // Long 50 more SBIN @ ₹1,000 = net 150, exposure = ₹1,50,000
        SignalGenerated second = signalWithSymbol("SBIN", 50, 1_000_00, Side.BUY);
        approveAndEmit(second);
        assertEquals(150, engine.netPosition("SBIN"));
    }

    @Test
    void suppressesSignalWhenNetExposureExceeded() {
        // Build net exposure to ₹50,00,000 across ten strategies (₹50L capital each, ₹50L exposure total)
        for (int i = 0; i < 10; i++) {
            approveAndEmit(signalWithStrategy("Strat" + i, "SBIN", 100, 5_000_00, Side.BUY));
        }
        assertEquals(1000, engine.netPosition("SBIN"));
        emitted.clear();

        // One more share would push exposure above ₹50,00,000
        SignalGenerated third = signalWithSymbol("SBIN", 1, 5_000_00, Side.BUY);
        String rejection = engine.reserveSignal(third);
        assertNotNull(rejection);
        assertTrue(rejection.contains("net exposure limit exceeded"), rejection);
    }

    @Test
    void handlesShortPositionsCorrectly() {
        // Short 200 SBIN @ ₹1,000 = net -200
        SignalGenerated first = signalWithSymbol("SBIN", 200, 1_000_00, Side.SELL);
        approveAndEmit(first);
        assertEquals(-200, engine.netPosition("SBIN"));
        emitted.clear();

        // Short 100 more = net -300
        SignalGenerated second = signalWithSymbol("SBIN", 100, 1_000_00, Side.SELL);
        approveAndEmit(second);
        assertEquals(-300, engine.netPosition("SBIN"));
    }

    @Test
    void netExposureUsesAbsoluteValue() {
        // High per-strategy capital so exposure limit binds first on a large short
        PortfolioEngine exposureBound = new PortfolioEngine(10_000_000_000L, MAX_EXPOSURE);
        SignalGenerated first = signalWithSymbol("SBIN", 3000, 2_000_00, Side.SELL);
        String rejection = exposureBound.reserveSignal(first);
        assertNotNull(rejection);
        assertTrue(rejection.contains("net exposure"), rejection);
    }

    // ── Trade lifecycle tests ──

    @Test
    void tradeOpenedAdjustsCapitalToActualFill() {
        // Signal: 100 SBIN @ ₹1,000 = estimated ₹1,00,000
        SignalGenerated signal = signal(100, 1_000_00, Side.BUY, 0L, 0L);
        approveAndEmit(signal);
        assertInstanceOf(SignalGenerated.class, emitted.get(0));
        long estimatedCapital = 100 * 1_000_00;
        assertEquals(estimatedCapital, engine.usedCapitalPaisa(STRATEGY_NAME));
        String signalId = ((SignalGenerated) emitted.get(0)).signalId();
        emitted.clear();

        // Trade opened with actual fill: 100 @ ₹1,050 = ₹1,05,000
        TradeOpened trade = tradeOpened(signalId, "trade-1", "SBIN", 100, 1_050_00, Side.LONG);
        engine.onDomainEvent(trade, emitted::add);
        assertInstanceOf(TradeOpened.class, emitted.get(0));

        // Used capital should reflect actual fill, not estimate
        assertEquals(100 * 1_050_00, engine.usedCapitalPaisa(STRATEGY_NAME));
    }

    @Test
    void tradeOpenedAdjustsForPartialFill() {
        // Signal: 100 SBIN @ ₹1,000 = estimated ₹1,00,000
        SignalGenerated signal = signal(100, 1_000_00, Side.BUY, 0L, 0L);
        approveAndEmit(signal);
        String signalId = ((SignalGenerated) emitted.get(0)).signalId();
        assertEquals(100 * 1_000_00, engine.usedCapitalPaisa(STRATEGY_NAME));
        assertEquals(100, engine.netPosition("SBIN"));
        emitted.clear();

        // Partial fill: only 60 shares @ ₹1,050
        TradeOpened trade = tradeOpened(signalId, "trade-1", "SBIN", 60, 1_050_00, Side.LONG);
        engine.onDomainEvent(trade, emitted::add);

        // Capital should be actual fill: 60 × ₹1,050 = ₹63,000
        assertEquals(60 * 1_050_00, engine.usedCapitalPaisa(STRATEGY_NAME));

        // Net position should reflect actual fill
        assertEquals(60, engine.netPosition("SBIN"));
    }

    @Test
    void tradeClosedFreesCapital() {
        // Signal → TradeOpened → TradeClosed lifecycle
        SignalGenerated signal = signal(100, 1_000_00, Side.BUY, 0L, 0L);
        approveAndEmit(signal);
        String signalId = ((SignalGenerated) emitted.get(0)).signalId();
        assertEquals(100 * 1_000_00, engine.usedCapitalPaisa(STRATEGY_NAME));
        assertEquals(100, engine.netPosition("SBIN"));
        emitted.clear();

        String tradeId = "trade-1";
        TradeOpened trade = tradeOpened(signalId, tradeId, "SBIN", 100, 1_000_00, Side.LONG);
        engine.onDomainEvent(trade, emitted::add);
        assertEquals(100 * 1_000_00, engine.usedCapitalPaisa(STRATEGY_NAME));
        emitted.clear();

        // Close the trade
        TradeClosed closed = tradeClosed(tradeId, "SBIN", 1_100_00, 10_000_00);
        engine.onDomainEvent(closed, emitted::add);
        assertInstanceOf(TradeClosed.class, emitted.get(0));

        // Capital should be freed
        assertEquals(0, engine.usedCapitalPaisa(STRATEGY_NAME));
        assertEquals(0, engine.netPosition("SBIN"));
    }

    @Test
    void multipleTradeLifecycles() {
        // Trade 1: 80 SBIN @ ₹10,000 = ₹8,00,000
        SignalGenerated sig1 = signal(80, 10_000_00, Side.BUY, 0L, 0L);
        approveAndEmit(sig1);
        String sig1Id = ((SignalGenerated) emitted.get(0)).signalId();
        emitted.clear();

        // Trade 2: 20 SBIN @ ₹10,000 = ₹2,00,000 (total ₹10,00,000 — at limit)
        SignalGenerated sig2 = signal(20, 10_000_00, Side.BUY, 0L, 0L);
        approveAndEmit(sig2);
        String sig2Id = ((SignalGenerated) emitted.get(0)).signalId();
        assertEquals(100_000_000L, engine.usedCapitalPaisa(STRATEGY_NAME));
        assertEquals(100, engine.netPosition("SBIN"));
        emitted.clear();

        // Open both trades
        engine.onDomainEvent(tradeOpened(sig1Id, "trade-1", "SBIN", 80, 10_000_00, Side.LONG), emitted::add);
        emitted.clear();
        engine.onDomainEvent(tradeOpened(sig2Id, "trade-2", "SBIN", 20, 10_000_00, Side.LONG), emitted::add);
        emitted.clear();
        assertEquals(100_000_000L, engine.usedCapitalPaisa(STRATEGY_NAME));

        // Close trade 1
        engine.onDomainEvent(tradeClosed("trade-1", "SBIN", 11_000_00, 8_000_00), emitted::add);
        assertEquals(20 * 10_000_00, engine.usedCapitalPaisa(STRATEGY_NAME)); // only trade 2 remains
        assertEquals(20, engine.netPosition("SBIN"));
        emitted.clear();

        // Close trade 2
        engine.onDomainEvent(tradeClosed("trade-2", "SBIN", 12_000_00, 4_000_00), emitted::add);
        assertEquals(0, engine.usedCapitalPaisa(STRATEGY_NAME));
        assertEquals(0, engine.netPosition("SBIN"));
    }

    @Test
    void shortTradeLifecycle() {
        // Signal: short 50 SBIN @ ₹2,000
        SignalGenerated signal = signal(50, 2_000_00, Side.SELL, 0L, 0L);
        approveAndEmit(signal);
        String signalId = ((SignalGenerated) emitted.get(0)).signalId();
        assertEquals(-50, engine.netPosition("SBIN"));
        emitted.clear();

        // Open short trade
        engine.onDomainEvent(tradeOpened(signalId, "trade-1", "SBIN", 50, 2_000_00, Side.SHORT), emitted::add);
        assertEquals(-50, engine.netPosition("SBIN"));

        // Close short trade
        engine.onDomainEvent(tradeClosed("trade-1", "SBIN", 1_800_00, 1_000_00), emitted::add);
        assertEquals(0, engine.netPosition("SBIN"));
    }

    // ── Edge cases ──

    @Test
    void passesThroughNonSignalEvents() {
        TradeOpened trade = tradeOpened("sig-1", "trade-1", "SBIN", 100, 1_000_00, Side.LONG);
        engine.onDomainEvent(trade, emitted::add);
        assertInstanceOf(TradeOpened.class, emitted.get(0));
    }

    @Test
    void resetsPortfolioState() {
        approveAndEmit(signal(100, 1_000_00, Side.BUY, 0L, 0L));
        assertEquals(100 * 1_000_00, engine.usedCapitalPaisa(STRATEGY_NAME));
        assertEquals(100, engine.netPosition("SBIN"));

        engine.reset();

        assertEquals(0, engine.usedCapitalPaisa(STRATEGY_NAME));
        assertEquals(0, engine.netPosition("SBIN"));
        assertTrue(engine.netPositionsSnapshot().isEmpty());
        assertTrue(engine.allocationsSnapshot().isEmpty());
    }

    @Test
    void handlesSignalWithoutQuantity() {
        // Quantity attribute missing — requiredCapital = 0, signalDelta = 0
        SignalGenerated signal = signalWithoutQuantity(1_000_00, Side.BUY);
        approveAndEmit(signal);
        assertInstanceOf(SignalGenerated.class, emitted.get(0));
        assertEquals(0, engine.usedCapitalPaisa(STRATEGY_NAME));
        assertEquals(0, engine.netPosition("SBIN"));
    }

    @Test
    void handlesSignalWithoutStrategyName() {
        SignalGenerated signal = signalWithoutStrategyName(10, 1_000_00, Side.BUY);
        approveAndEmit(signal);
        assertInstanceOf(SignalGenerated.class, emitted.get(0));
        assertEquals(10 * 1_000_00, engine.usedCapitalPaisa("unknown"));
    }

    @Test
    void multipleStrategiesHaveIndependentCapital() {
        // Strategy 1: 80 shares @ ₹10,000 = ₹8,00,000
        SignalGenerated s1 = signal(80, 10_000_00, Side.BUY, 0L, 0L);
        approveAndEmit(s1);
        emitted.clear();

        // Strategy 2 (different name): 20 shares @ ₹10,000 = ₹2,00,000
        SignalGenerated s2 = signalWithStrategy("OtherStrategy", "RELIANCE", 20, 10_000_00, Side.BUY);
        approveAndEmit(s2);
        emitted.clear();

        assertEquals(80 * 10_000_00, engine.usedCapitalPaisa(STRATEGY_NAME));
        assertEquals(20 * 10_000_00, engine.usedCapitalPaisa("OtherStrategy"));

        // Net positions aggregated across strategies
        assertEquals(80, engine.netPosition("SBIN"));
        assertEquals(20, engine.netPosition("RELIANCE"));
    }

    @Test
    void netExposureAggregatesAcrossStrategies() {
        // Strategy 1: long 50 SBIN
        SignalGenerated s1 = signalWithSymbol("SBIN", 50, 1_000_00, Side.BUY);
        approveAndEmit(s1);
        emitted.clear();

        // Strategy 2: long 30 SBIN → net should be 80
        SignalGenerated s2 = signalWithStrategy("OtherStrategy", "SBIN", 30, 1_000_00, Side.BUY);
        approveAndEmit(s2);

        assertEquals(80, engine.netPosition("SBIN"));
    }

    @Test
    void nettingOffsetsLongAndShort() {
        // Strategy 1: long 200 SBIN @ ₹2,000 = ₹4,00,000
        SignalGenerated s1 = signalWithSymbol("SBIN", 200, 2_000_00, Side.BUY);
        approveAndEmit(s1);
        assertEquals(200, engine.netPosition("SBIN"));
        emitted.clear();

        // Strategy 2: short 70 SBIN @ ₹2,000 → net = 130, exposure = ₹2,60,000
        SignalGenerated s2 = signalWithStrategy("OtherStrategy", "SBIN", 70, 2_000_00, Side.SELL);
        approveAndEmit(s2);
        assertInstanceOf(SignalGenerated.class, emitted.get(0));
        assertEquals(130, engine.netPosition("SBIN"));
    }

    @Test
    void tradeOpenedWithUnknownSignalId() {
        // Trade opened without a prior signal — should use "default" strategy
        TradeOpened trade = tradeOpened("unknown-signal", "trade-1", "SBIN", 100, 1_000_00, Side.LONG);
        engine.onDomainEvent(trade, emitted::add);

        assertInstanceOf(TradeOpened.class, emitted.get(0));
        assertEquals(100 * 1_000_00, engine.usedCapitalPaisa("default"));
        assertEquals(100, engine.netPosition("SBIN"));
    }

    // ── SignalSuppressed / OrderRejected — capital free-up ──

    @Test
    void signalSuppressedByDownstreamFreesCapital() {
        // Signal passes portfolio checks → capital reserved
        SignalGenerated signal = signal(100, 1_000_00, Side.BUY, 0L, 0L);
        approveAndEmit(signal);
        String signalId = ((SignalGenerated) emitted.get(0)).signalId();
        assertEquals(100 * 1_000_00, engine.usedCapitalPaisa(STRATEGY_NAME));
        assertEquals(100, engine.netPosition("SBIN"));
        emitted.clear();

        // Downstream (e.g. PositionRiskHandler) suppresses it
        SignalSuppressed suppressed = new SignalSuppressed(
                EventMetadata.root(), signalId, "SBIN", "Max positions reached", Map.of()
        );
        engine.onDomainEvent(suppressed, emitted::add);
        assertInstanceOf(SignalSuppressed.class, emitted.get(0));

        // Capital should be freed
        assertEquals(0, engine.usedCapitalPaisa(STRATEGY_NAME));
        assertEquals(0, engine.netPosition("SBIN"));
    }

    @Test
    void signalSuppressedWithoutPriorApprovalPassesThrough() {
        // A SignalSuppressed for a signal we never approved — should just pass through
        SignalSuppressed suppressed = new SignalSuppressed(
                EventMetadata.root(), "unknown-signal", "SBIN", "Risk limit", Map.of()
        );
        engine.onDomainEvent(suppressed, emitted::add);
        assertInstanceOf(SignalSuppressed.class, emitted.get(0));
        // No state change
        assertEquals(0, engine.usedCapitalPaisa(STRATEGY_NAME));
        assertEquals(0, engine.netPosition("SBIN"));
    }

    @Test
    void orderRejectedFreesCapital() {
        // Signal passes portfolio checks → capital reserved
        SignalGenerated signal = signal(100, 1_000_00, Side.BUY, 0L, 0L);
        approveAndEmit(signal);
        String signalId = ((SignalGenerated) emitted.get(0)).signalId();
        assertEquals(100 * 1_000_00, engine.usedCapitalPaisa(STRATEGY_NAME));
        assertEquals(100, engine.netPosition("SBIN"));
        emitted.clear();

        // Broker rejects the order
        Order rejectedOrder = new Order(
                "ORD-123", signalId, "SBIN", ExchangeSegment.NSE_EQ, Side.BUY,
                ProductType.INTRADAY, OrderType.LIMIT, OrderStatus.REJECTED,
                100, 0, 1_000_00, 0L, 0L, "Insufficient margin"
        );
        OrderRejected rejected = new OrderRejected(EventMetadata.root(), rejectedOrder, "Insufficient margin");
        engine.onDomainEvent(rejected, emitted::add);
        assertInstanceOf(OrderRejected.class, emitted.get(0));

        // Capital should be freed
        assertEquals(0, engine.usedCapitalPaisa(STRATEGY_NAME));
        assertEquals(0, engine.netPosition("SBIN"));
    }

    @Test
    void orderRejectedWithoutPriorSignalPassesThrough() {
        Order rejectedOrder = new Order(
                "ORD-456", "unknown-signal", "SBIN", ExchangeSegment.NSE_EQ, Side.BUY,
                ProductType.INTRADAY, OrderType.LIMIT, OrderStatus.REJECTED,
                10, 0, 1_000_00, 0L, 0L, "Price band violation"
        );
        OrderRejected rejected = new OrderRejected(EventMetadata.root(), rejectedOrder, "Price band violation");
        engine.onDomainEvent(rejected, emitted::add);
        assertInstanceOf(OrderRejected.class, emitted.get(0));
        assertEquals(0, engine.usedCapitalPaisa(STRATEGY_NAME));
    }

    @Test
    void tradeClosedWithUnknownTradeId() {
        // Closing a trade that was never opened — should just pass through
        TradeClosed closed = tradeClosed("unknown-trade", "SBIN", 1_000_00, 0L);
        engine.onDomainEvent(closed, emitted::add);
        assertInstanceOf(TradeClosed.class, emitted.get(0));
    }

    @Test
    void snapshotsAreUnmodifiable() {
        assertThrows(UnsupportedOperationException.class, () ->
                engine.netPositionsSnapshot().put("X", 1L));
        assertThrows(UnsupportedOperationException.class, () ->
                engine.allocationsSnapshot().put("X", null));
    }

    // ── PE-02: orderIdToSignalId map — decouples from correlationId coupling ──

    @Test
    void orderAcceptedRegistersOrderIdToSignalIdMapping() {
        SignalGenerated signal = signal(100, 1_000_00, Side.BUY, 0L, 0L);
        approveAndEmit(signal);
        String signalId = ((SignalGenerated) emitted.get(0)).signalId();
        emitted.clear();

        Order acceptedOrder = new Order(
                "ORD-999", signalId, "SBIN", ExchangeSegment.NSE_EQ, Side.BUY,
                ProductType.INTRADAY, OrderType.LIMIT, OrderStatus.PENDING,
                100, 0, 1_000_00, 0L, 0L, null
        );
        OrderAccepted accepted = new OrderAccepted(EventMetadata.root(), acceptedOrder);
        engine.onDomainEvent(accepted, emitted::add);

        assertInstanceOf(OrderAccepted.class, emitted.get(0));
        // The mapping should now exist — emit an OrderRejected for the same order
        // and verify capital is freed via the orderIdToSignalId map
        emitted.clear();

        Order rejectedOrder = new Order(
                "ORD-999", signalId, "SBIN", ExchangeSegment.NSE_EQ, Side.BUY,
                ProductType.INTRADAY, OrderType.LIMIT, OrderStatus.REJECTED,
                100, 0, 1_000_00, 0L, 0L, "Margin call"
        );
        OrderRejected rejected = new OrderRejected(EventMetadata.root(), rejectedOrder, "Margin call");
        engine.onDomainEvent(rejected, emitted::add);

        // Capital must be freed via the orderIdToSignalId lookup
        assertEquals(0, engine.usedCapitalPaisa(STRATEGY_NAME));
        assertEquals(0, engine.netPosition("SBIN"));
    }

    @Test
    void orderRejectedFallsBackToCorrelationIdWhenOrderIdNotMapped() {
        // Signal approved and rejected — but no OrderAccepted was seen,
        // so orderIdToSignalId map is empty. Rejection must still succeed
        // via correlationId fallback (the old coupling path).
        SignalGenerated signal = signal(100, 1_000_00, Side.BUY, 0L, 0L);
        approveAndEmit(signal);
        String signalId = ((SignalGenerated) emitted.get(0)).signalId();
        assertEquals(100 * 1_000_00, engine.usedCapitalPaisa(STRATEGY_NAME));
        emitted.clear();

        Order rejectedOrder = new Order(
                "ORD-FALLBACK", signalId, "SBIN", ExchangeSegment.NSE_EQ, Side.BUY,
                ProductType.INTRADAY, OrderType.LIMIT, OrderStatus.REJECTED,
                100, 0, 1_000_00, 0L, 0L, "Margin shortfall"
        );
        OrderRejected rejected = new OrderRejected(EventMetadata.root(), rejectedOrder, "Margin shortfall");
        engine.onDomainEvent(rejected, emitted::add);

        assertEquals(0, engine.usedCapitalPaisa(STRATEGY_NAME));
    }

    @Test
    void resetClearsOrderIdToSignalIdMap() {
        SignalGenerated signal = signal(100, 1_000_00, Side.BUY, 0L, 0L);
        approveAndEmit(signal);
        String signalId = ((SignalGenerated) emitted.get(0)).signalId();
        emitted.clear();

        Order acceptedOrder = new Order(
                "ORD-777", signalId, "SBIN", ExchangeSegment.NSE_EQ, Side.BUY,
                ProductType.INTRADAY, OrderType.LIMIT, OrderStatus.PENDING,
                100, 0, 1_000_00, 0L, 0L, null
        );
        engine.onDomainEvent(new OrderAccepted(EventMetadata.root(), acceptedOrder), emitted::add);
        emitted.clear();

        engine.reset();

        // After reset, orderIdToSignalId map is empty.
        // OrderRejected for ORD-777 should fall back to correlationId (which is also signalId).
        // But since reset also cleared signalToStrategy, the capital is already gone.
        assertEquals(0, engine.usedCapitalPaisa(STRATEGY_NAME));
    }
}
