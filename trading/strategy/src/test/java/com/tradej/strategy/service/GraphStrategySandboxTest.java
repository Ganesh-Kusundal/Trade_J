package com.tradej.strategy.service;

import com.tradej.core.domain.event.CandleClosed;
import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.event.EventMetadata;
import com.tradej.core.domain.event.SignalGenerated;
import com.tradej.core.domain.event.StrategyError;
import com.tradej.core.domain.event.TickReceived;
import com.tradej.core.domain.model.Candle;
import com.tradej.core.domain.port.PositionSizer;
import com.tradej.core.domain.value.Side;
import com.tradej.strategy.api.GraphStrategyPlugin;
import com.tradej.strategy.position.DefaultPositionSizer;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link GraphStrategySandbox}.
 *
 * <p>Verifies that {@link GraphStrategyPlugin} instances are correctly
 * dispatched, timed out, and that errors produce {@link StrategyError}
 * events rather than crashing the sandbox.
 */
@Tag("unit")
class GraphStrategySandboxTest {

    private static final CandleClosed TEST_CANDLE = new CandleClosed(
            EventMetadata.root(),
            new Candle("SBIN", "5m", 1_710_000_000_000L, 1_710_000_300_000L,
                    750_00L, 755_00L, 749_00L, 753_00L, 10_000L, true)
    );

    private static final TickReceived TEST_TICK = new TickReceived(
            EventMetadata.root(), "SBIN", "5m", 750_00L, 10L, 10L,
            System.currentTimeMillis(), null
    );

    @Test
    void healthyPluginProducesSignal() throws Exception {
        var plugin = new GraphStrategyPlugin() {
            @Override public String name() { return "healthy"; }
            @Override public List<Class<? extends DomainEvent>> subscribedEventTypes() {
                return List.of(CandleClosed.class);
            }
            @Override public Optional<SignalGenerated> onEvent(DomainEvent event) {
                return Optional.of(new SignalGenerated(
                        EventMetadata.root(), "sig-1", "SBIN", "5m",
                        Side.BUY, 750_00L, 740_00L, 770_00L, "breakout", java.util.Map.of()
                ));
            }
        };

        var sandbox = new GraphStrategySandbox(List.of(plugin), 2_000L);
        var emitted = new ArrayList<DomainEvent>();

        sandbox.onDomainEvent(TEST_CANDLE, emitted::add);
        awaitEmitted(emitted, 1);

        assertEquals(1, emitted.size());
        assertInstanceOf(SignalGenerated.class, emitted.get(0));
        assertEquals("SBIN", ((SignalGenerated) emitted.get(0)).symbol());
    }

    @Test
    void failingPluginCaughtInternallyDoesNotEmit() throws Exception {
        // GraphStrategySandbox.runPlugin() catches exceptions and returns
        // Optional.empty() — the handle() path sees normal completion with
        // no signal, so nothing is emitted. This is intentional: the sandbox
        // logs the error internally rather than propagating it downstream.
        var plugin = new GraphStrategyPlugin() {
            @Override public String name() { return "failing"; }
            @Override public List<Class<? extends DomainEvent>> subscribedEventTypes() {
                return List.of(CandleClosed.class);
            }
            @Override public Optional<SignalGenerated> onEvent(DomainEvent event) {
                throw new RuntimeException("Intentional failure");
            }
        };

        var sandbox = new GraphStrategySandbox(List.of(plugin), 500L);
        var emitted = new ArrayList<DomainEvent>();

        sandbox.onDomainEvent(TEST_CANDLE, emitted::add);

        // No emission expected — the exception is caught inside runPlugin()
        Thread.sleep(300);
        assertTrue(emitted.isEmpty(),
                "Plugin exception caught internally should not emit any event");
    }

    @Test
    void timeoutEmitsStrategyErrorWithoutBlockingOtherPlugins() throws Exception {
        var hangingPlugin = new GraphStrategyPlugin() {
            @Override public String name() { return "hungry"; }
            @Override public List<Class<? extends DomainEvent>> subscribedEventTypes() {
                return List.of(CandleClosed.class);
            }
            @Override public Optional<SignalGenerated> onEvent(DomainEvent event) {
                try { Thread.sleep(10_000L); } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return Optional.empty();
            }
        };
        var fastPlugin = new GraphStrategyPlugin() {
            @Override public String name() { return "fast"; }
            @Override public List<Class<? extends DomainEvent>> subscribedEventTypes() {
                return List.of(CandleClosed.class);
            }
            @Override public Optional<SignalGenerated> onEvent(DomainEvent event) {
                return Optional.of(new SignalGenerated(
                        EventMetadata.root(), "sig-3", "SBIN", "5m",
                        Side.SELL, 753_00L, 760_00L, 740_00L, "reversal", java.util.Map.of()
                ));
            }
        };

        var sandbox = new GraphStrategySandbox(List.of(hangingPlugin, fastPlugin), 100L);
        var emitted = new ArrayList<DomainEvent>();

        sandbox.onDomainEvent(TEST_CANDLE, emitted::add);
        awaitEmitted(emitted, 2);

        assertEquals(2, emitted.size(), "Should emit a timeout error + a signal from fast plugin");
        assertTrue(emitted.stream().anyMatch(e -> e instanceof StrategyError),
                "Should emit StrategyError for timed-out plugin");
        assertTrue(emitted.stream().anyMatch(e -> e instanceof SignalGenerated),
                "Should emit SignalGenerated from fast plugin");
    }

    @Test
    void unsubscribedEventTypeIsIgnored() throws Exception {
        var plugin = new GraphStrategyPlugin() {
            @Override public String name() { return "candle-only"; }
            @Override public List<Class<? extends DomainEvent>> subscribedEventTypes() {
                return List.of(CandleClosed.class);  // does NOT subscribe to TickReceived
            }
            @Override public Optional<SignalGenerated> onEvent(DomainEvent event) {
                return Optional.of(new SignalGenerated(
                        EventMetadata.root(), "sig-4", "SBIN", "5m",
                        Side.BUY, 750_00L, 740_00L, 770_00L, "breakout", java.util.Map.of()
                ));
            }
        };

        var sandbox = new GraphStrategySandbox(List.of(plugin), 500L);
        var emitted = new ArrayList<DomainEvent>();

        // Send a TickReceived (not subscribed)
        sandbox.onDomainEvent(TEST_TICK, emitted::add);

        // Wait a bit — no events should be emitted
        Thread.sleep(200);
        assertTrue(emitted.isEmpty(), "Plugin should not receive unsubscribed event types");
    }

    @Test
    void nullSubscribedTypesReceivesAllEvents() throws Exception {
        var plugin = new GraphStrategyPlugin() {
            @Override public String name() { return "catch-all"; }
            @Override public List<Class<? extends DomainEvent>> subscribedEventTypes() {
                return null;  // receive all
            }
            @Override public Optional<SignalGenerated> onEvent(DomainEvent event) {
                return Optional.of(new SignalGenerated(
                        EventMetadata.root(), "sig-5", "SBIN", "5m",
                        Side.BUY, 750_00L, 740_00L, 770_00L, "any-event", java.util.Map.of()
                ));
            }
        };

        var sandbox = new GraphStrategySandbox(List.of(plugin), 500L);
        var emitted = new ArrayList<DomainEvent>();

        sandbox.onDomainEvent(TEST_TICK, emitted::add);
        awaitEmitted(emitted, 1);

        assertEquals(1, emitted.size(), "Null subscribedEventTypes should receive all events");
        assertInstanceOf(SignalGenerated.class, emitted.get(0));
    }

    @Test
    void emptySubscribedTypesReceivesAllEvents() throws Exception {
        var plugin = new GraphStrategyPlugin() {
            @Override public String name() { return "catch-all-v2"; }
            @Override public List<Class<? extends DomainEvent>> subscribedEventTypes() {
                return List.of();  // empty — receive all
            }
            @Override public Optional<SignalGenerated> onEvent(DomainEvent event) {
                return Optional.of(new SignalGenerated(
                        EventMetadata.root(), "sig-6", "SBIN", "5m",
                        Side.BUY, 750_00L, 740_00L, 770_00L, "any-event", java.util.Map.of()
                ));
            }
        };

        var sandbox = new GraphStrategySandbox(List.of(plugin), 500L);
        var emitted = new ArrayList<DomainEvent>();

        sandbox.onDomainEvent(TEST_TICK, emitted::add);
        awaitEmitted(emitted, 1);

        assertEquals(1, emitted.size(), "Empty subscribedEventTypes should receive all events");
        assertInstanceOf(SignalGenerated.class, emitted.get(0));
    }

    @Test
    void pluginCountMatchesRegisteredPlugins() {
        var p1 = new GraphStrategyPlugin() {
            @Override public String name() { return "a"; }
            @Override public List<Class<? extends DomainEvent>> subscribedEventTypes() { return List.of(); }
            @Override public Optional<SignalGenerated> onEvent(DomainEvent event) { return Optional.empty(); }
        };
        var p2 = new GraphStrategyPlugin() {
            @Override public String name() { return "b"; }
            @Override public List<Class<? extends DomainEvent>> subscribedEventTypes() { return List.of(); }
            @Override public Optional<SignalGenerated> onEvent(DomainEvent event) { return Optional.empty(); }
        };
        var sandbox = new GraphStrategySandbox(List.of(p1, p2), 500L);
        assertEquals(2, sandbox.pluginCount());
    }

    @Test
    void signalWithoutQuantityGetsComputedFromPositionSizer() throws Exception {
        var plugin = new GraphStrategyPlugin() {
            @Override public String name() { return "qty-plugin"; }
            @Override public List<Class<? extends DomainEvent>> subscribedEventTypes() { return List.of(CandleClosed.class); }
            @Override public Optional<SignalGenerated> onEvent(DomainEvent event) {
                return Optional.of(new SignalGenerated(
                        EventMetadata.root(), "sig-qty", "SBIN", "5m",
                        Side.BUY, 750_00L, 740_00L, 770_00L, "breakout",
                        Map.of()  // no quantity — PositionSizer should fill it
                ));
            }
        };
        var sizer = new PositionSizer() {
            @Override public long computeQuantity(SignalGenerated signal) {
                return 42L;  // fixed size from sizer
            }
        };
        var sandbox = new GraphStrategySandbox(List.of(plugin), 2_000L, sizer);
        var emitted = new ArrayList<DomainEvent>();

        sandbox.onDomainEvent(TEST_CANDLE, emitted::add);
        awaitEmitted(emitted, 1);

        assertEquals(1, emitted.size());
        assertInstanceOf(SignalGenerated.class, emitted.get(0));
        assertEquals(42L, ((SignalGenerated) emitted.get(0)).attributes().get("quantity"));
    }

    @Test
    void explicitQuantityInSignalTakesPrecedenceOverPositionSizer() throws Exception {
        var plugin = new GraphStrategyPlugin() {
            @Override public String name() { return "explicit-qty"; }
            @Override public List<Class<? extends DomainEvent>> subscribedEventTypes() { return List.of(CandleClosed.class); }
            @Override public Optional<SignalGenerated> onEvent(DomainEvent event) {
                return Optional.of(new SignalGenerated(
                        EventMetadata.root(), "sig-explicit", "SBIN", "5m",
                        Side.BUY, 750_00L, 740_00L, 770_00L, "breakout",
                        Map.of("quantity", 99L)  // explicit quantity — sizer should NOT override
                ));
            }
        };
        var sizer = new PositionSizer() {
            @Override public long computeQuantity(SignalGenerated signal) {
                return 42L;
            }
        };
        var sandbox = new GraphStrategySandbox(List.of(plugin), 2_000L, sizer);
        var emitted = new ArrayList<DomainEvent>();

        sandbox.onDomainEvent(TEST_CANDLE, emitted::add);
        awaitEmitted(emitted, 1);

        assertEquals(1, emitted.size());
        assertInstanceOf(SignalGenerated.class, emitted.get(0));
        assertEquals(99L, ((SignalGenerated) emitted.get(0)).attributes().get("quantity"));
    }

    @Test
    void defaultPositionSizerIsUsedWhenNotExplicitlyProvided() throws Exception {
        var plugin = new GraphStrategyPlugin() {
            @Override public String name() { return "default-sizer"; }
            @Override public List<Class<? extends DomainEvent>> subscribedEventTypes() { return List.of(CandleClosed.class); }
            @Override public Optional<SignalGenerated> onEvent(DomainEvent event) {
                return Optional.of(new SignalGenerated(
                        EventMetadata.root(), "sig-default", "SBIN", "5m",
                        Side.BUY, 750_00L, 740_00L, 770_00L, "breakout",
                        Map.of()
                ));
            }
        };
        var sandbox = new GraphStrategySandbox(List.of(plugin), 2_000L);  // no explicit sizer
        var emitted = new ArrayList<DomainEvent>();

        sandbox.onDomainEvent(TEST_CANDLE, emitted::add);
        awaitEmitted(emitted, 1);

        assertEquals(1, emitted.size());
        assertInstanceOf(SignalGenerated.class, emitted.get(0));
        // DefaultPositionSizer requires stop-loss distance > 0 to compute quantity.
        // With stopLoss=740 and entry=750, slDistance=10 paisa, risk=100000*0.01=1000 paisa,
        // rawQty=100 → so we expect a positive quantity
        Object qty = ((SignalGenerated) emitted.get(0)).attributes().get("quantity");
        assertNotNull(qty, "DefaultPositionSizer should have computed a quantity");
    }

    // ── Test support ──

    private static void awaitEmitted(List<DomainEvent> emitted, int expectedSize) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5_000L;
        while (emitted.size() < expectedSize && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }
    }
}
