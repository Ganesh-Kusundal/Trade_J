package com.tradej.strategy.portfolio;

import com.tradej.core.domain.event.EventMetadata;
import com.tradej.core.domain.event.SignalGenerated;
import com.tradej.core.domain.value.Side;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class CapitalReservationServiceTest {

    private static final long CAPITAL_PER_STRATEGY = 100_000_000L;
    private DefaultCapitalReservationService service;

    @BeforeEach
    void setUp() {
        service = new DefaultCapitalReservationService(CAPITAL_PER_STRATEGY);
    }

    private static Map<String, Object> attrs(String strategyName, long quantity) {
        Map<String, Object> m = new HashMap<>();
        m.put(CapitalReservationService.ATTR_STRATEGY_NAME, strategyName);
        m.put("quantity", quantity);
        return m;
    }

    private SignalGenerated signal(String symbol, long qty, long price, Side side, String strategy) {
        return new SignalGenerated(
                EventMetadata.root(),
                "sig-" + System.nanoTime(),
                symbol,
                "5m",
                side,
                price,
                0L, 0L,
                "test",
                attrs(strategy, qty)
        );
    }

    @Test
    void reservesCapitalWithinLimit() {
        SignalGenerated s = signal("SBIN", 100, 1_000_00, Side.BUY, "TestStrategy");
        assertNull(service.reserveSignal(s));
        assertEquals(100 * 1_000_00, service.usedCapitalPaisa("TestStrategy"));
    }

    @Test
    void rejectsSignalWhenCapitalExceeded() {
        service.reserveSignal(signal("SBIN", 80, 12_500_00, Side.BUY, "TestStrategy"));
        String result = service.reserveSignal(signal("SBIN", 1, 1_00, Side.BUY, "TestStrategy"));
        assertNotNull(result);
        assertTrue(result.contains("capital limit exceeded"));
    }

    @Test
    void freesCapitalOnFreeSignalCapital() {
        SignalGenerated s = signal("SBIN", 100, 1_000_00, Side.BUY, "TestStrategy");
        service.reserveSignal(s);
        assertEquals(100 * 1_000_00, service.usedCapitalPaisa("TestStrategy"));

        String strategyName = service.freeSignalCapital(s.signalId(), "SBIN");
        assertEquals("TestStrategy", strategyName);
        assertEquals(0L, service.usedCapitalPaisa("TestStrategy"));
    }

    @Test
    void freeSignalCapitalUnknownSignalReturnsNull() {
        String result = service.freeSignalCapital("unknown-signal", "SBIN");
        assertNull(result);
    }

    @Test
    void revertReservationFreesCapital() {
        SignalGenerated s = signal("SBIN", 100, 1_000_00, Side.BUY, "TestStrategy");
        service.reserveSignal(s);
        assertEquals(100 * 1_000_00, service.usedCapitalPaisa("TestStrategy"));

        service.revertReservation(s.signalId());
        assertEquals(0L, service.usedCapitalPaisa("TestStrategy"));
    }

    @Test
    void adjustOnTradeOpenedReplacesEstimate() {
        SignalGenerated s = signal("SBIN", 100, 1_000_00, Side.BUY, "TestStrategy");
        service.reserveSignal(s);
        assertEquals(100 * 1_000_00, service.usedCapitalPaisa("TestStrategy"));

        service.adjustOnTradeOpened("TestStrategy", 100 * 1_000_00, 100 * 1_050_00);
        assertEquals(100 * 1_050_00, service.usedCapitalPaisa("TestStrategy"));
    }

    @Test
    void freeTradeCapitalReducesUsedCapital() {
        service.adjustOnTradeOpened("TestStrategy", 0L, 100 * 1_000_00);
        assertEquals(100 * 1_000_00, service.usedCapitalPaisa("TestStrategy"));

        service.freeTradeCapital("TestStrategy", 100 * 1_000_00);
        assertEquals(0L, service.usedCapitalPaisa("TestStrategy"));
    }

    @Test
    void independentCapitalAcrossStrategies() {
        service.reserveSignal(signal("SBIN", 50, 1_000_00, Side.BUY, "StratA"));
        service.reserveSignal(signal("RELIANCE", 30, 2_000_00, Side.BUY, "StratB"));

        assertEquals(50 * 1_000_00, service.usedCapitalPaisa("StratA"));
        assertEquals(30 * 2_000_00, service.usedCapitalPaisa("StratB"));
    }

    @Test
    void trackOrderIdMapsOrderToSignal() {
        service.trackOrderId("ORD-1", "sig-1");
        assertEquals("sig-1", service.signalIdForOrder("ORD-1"));
    }

    @Test
    void signalIdForOrderUnknownReturnsNull() {
        assertNull(service.signalIdForOrder("ORD-unknown"));
    }

    @Test
    void resetClearsAllState() {
        service.reserveSignal(signal("SBIN", 100, 1_000_00, Side.BUY, "TestStrategy"));
        service.trackOrderId("ORD-1", "sig-1");
        service.reset();

        assertEquals(0L, service.usedCapitalPaisa("TestStrategy"));
        assertNull(service.signalIdForOrder("ORD-1"));
        assertTrue(service.allocationsSnapshot().isEmpty());
    }

    @Test
    void allocatedCapitalPaisaReturnsDefaultForUnknownStrategy() {
        assertEquals(CAPITAL_PER_STRATEGY, service.allocatedCapitalPaisa("UnknownStrategy"));
    }

    @Test
    void usedCapitalPaisaReturnsZeroForUnknownStrategy() {
        assertEquals(0L, service.usedCapitalPaisa("UnknownStrategy"));
    }
}
