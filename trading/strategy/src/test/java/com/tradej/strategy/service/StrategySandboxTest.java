package com.tradej.strategy.service;

import com.tradej.core.domain.event.CandleClosed;
import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.event.EventMetadata;
import com.tradej.core.domain.event.EventMetadataFactory;
import com.tradej.core.domain.event.SignalGenerated;
import com.tradej.core.domain.event.StrategyError;
import com.tradej.core.domain.model.Candle;
import com.tradej.core.domain.time.LiveTradingClock;
import com.tradej.core.domain.value.Side;
import com.tradej.strategy.api.StrategyPlugin;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("unit")
class StrategySandboxTest {

    private final EventMetadataFactory eventMetadataFactory = new EventMetadataFactory(new LiveTradingClock());

    private static final CandleClosed TEST_CANDLE = new CandleClosed(
            EventMetadata.root(),
            new Candle("SBIN", "5m", 1_710_000_000_000L, 1_710_000_300_000L,
                    750_00L, 755_00L, 749_00L, 753_00L, 10_000L, true)
    );

    @Test
    void healthyPluginProducesSignal() throws Exception {
        var plugin = new StrategyPlugin() {
            @Override public String name() { return "healthy"; }
            @Override public Optional<SignalGenerated> onCandleClosed(CandleClosed c) {
                return Optional.of(new SignalGenerated(
                        EventMetadata.root(), "sig-1", "SBIN", "5m",
                        Side.BUY, 750_00L, 740_00L, 770_00L, "breakout", java.util.Map.of()
                ));
            }
        };

        var sandbox = new StrategySandbox(List.of(plugin), 2_000L, eventMetadataFactory);
        var emitted = new ArrayList<DomainEvent>();

        sandbox.onDomainEvent(TEST_CANDLE, emitted::add);

        // Wait for async evaluation
        awaitEmitted(emitted, 1);
        assertEquals(1, emitted.size());
        assertInstanceOf(SignalGenerated.class, emitted.get(0));
        assertEquals("SBIN", ((SignalGenerated) emitted.get(0)).symbol());
    }

    @Test
    void failingPluginDoesNotBlockOtherPlugins() throws Exception {
        var failingPlugin = new StrategyPlugin() {
            @Override public String name() { return "failing"; }
            @Override public Optional<SignalGenerated> onCandleClosed(CandleClosed c) {
                throw new RuntimeException("Intentional failure");
            }
        };
        var healthyPlugin = new StrategyPlugin() {
            @Override public String name() { return "healthy"; }
            @Override public Optional<SignalGenerated> onCandleClosed(CandleClosed c) {
                return Optional.of(new SignalGenerated(
                        EventMetadata.root(), "sig-2", "SBIN", "5m",
                        Side.BUY, 750_00L, 740_00L, 770_00L, "breakout", java.util.Map.of()
                ));
            }
        };

        var sandbox = new StrategySandbox(List.of(failingPlugin, healthyPlugin), 2_000L, eventMetadataFactory);
        var emitted = new ArrayList<DomainEvent>();

        sandbox.onDomainEvent(TEST_CANDLE, emitted::add);

        // Wait for both evaluations to complete
        awaitEmitted(emitted, 2);

        // Should have a StrategyError from failing plugin + a SignalGenerated from healthy plugin
        long errorCount = emitted.stream().filter(e -> e instanceof StrategyError).count();
        long signalCount = emitted.stream().filter(e -> e instanceof SignalGenerated).count();
        assertEquals(1, errorCount, "Should emit one StrategyError for failing plugin");
        assertEquals(1, signalCount, "Should emit one SignalGenerated from healthy plugin");

        StrategyError error = (StrategyError) emitted.stream().filter(e -> e instanceof StrategyError).findFirst().get();
        assertTrue(error.detail().contains("Intentional failure"));
    }

    @Test
    void timeoutKillsHungPlugin() throws Exception {
        var hangingPlugin = new StrategyPlugin() {
            @Override public String name() { return "hungry"; }
            @Override public Optional<SignalGenerated> onCandleClosed(CandleClosed c) {
                try { Thread.sleep(10_000L); } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return Optional.empty();
            }
        };
        var healthyPlugin = new StrategyPlugin() {
            @Override public String name() { return "fast"; }
            @Override public Optional<SignalGenerated> onCandleClosed(CandleClosed c) {
                return Optional.of(new SignalGenerated(
                        EventMetadata.root(), "sig-3", "SBIN", "5m",
                        Side.SELL, 753_00L, 760_00L, 740_00L, "reversal", java.util.Map.of()
                ));
            }
        };

        // Use a very short timeout (100ms) so the test runs quickly
        var sandbox = new StrategySandbox(List.of(hangingPlugin, healthyPlugin), 100L, eventMetadataFactory);
        var emitted = new ArrayList<DomainEvent>();

        sandbox.onDomainEvent(TEST_CANDLE, emitted::add);

        // Wait for both evaluations to complete
        awaitEmitted(emitted, 2);

        assertEquals(2, emitted.size(), "Should emit a timeout error + a signal from fast plugin");
        assertTrue(emitted.stream().anyMatch(e -> e instanceof StrategyError),
                "Should emit StrategyError for timed-out plugin");
        assertTrue(emitted.stream().anyMatch(e -> e instanceof SignalGenerated),
                "Should emit SignalGenerated from fast plugin");
    }

    @Test
    void emptyPluginListProducesNoEvents() throws Exception {
        var sandbox = new StrategySandbox(List.of(), 500L, eventMetadataFactory);
        var emitted = new ArrayList<DomainEvent>();

        sandbox.onDomainEvent(TEST_CANDLE, emitted::add);

        // Small sleep to allow any async work to complete
        Thread.sleep(100);
        assertTrue(emitted.isEmpty(), "No events should be emitted with empty plugin list");
    }

    @Test
    void nonCandleClosedEventsAreIgnored() throws Exception {
        var plugin = new StrategyPlugin() {
            @Override public String name() { return "ignored"; }
            @Override public Optional<SignalGenerated> onCandleClosed(CandleClosed c) {
                return Optional.of(new SignalGenerated(
                        EventMetadata.root(), "sig-4", "SBIN", "5m",
                        Side.BUY, 750_00L, 740_00L, 770_00L, "breakout", java.util.Map.of()
                ));
            }
        };

        var sandbox = new StrategySandbox(List.of(plugin), 500L, eventMetadataFactory);
        var emitted = new ArrayList<DomainEvent>();

        // Send a non-CandleClosed event
        sandbox.onDomainEvent(new com.tradej.core.domain.event.TickReceived(
                EventMetadata.root(), "SBIN", "5m", 750_00L, 10L, 10L,
                System.currentTimeMillis(), null
        ), emitted::add);

        // Small sleep to allow any async work to complete
        Thread.sleep(100);
        assertTrue(emitted.isEmpty(), "Non-CandleClosed events should be ignored");
    }

    @Test
    void pluginCountMatchesRegisteredPlugins() {
        var p1 = new StrategyPlugin() {
            @Override public String name() { return "a"; }
            @Override public Optional<SignalGenerated> onCandleClosed(CandleClosed c) { return Optional.empty(); }
        };
        var p2 = new StrategyPlugin() {
            @Override public String name() { return "b"; }
            @Override public Optional<SignalGenerated> onCandleClosed(CandleClosed c) { return Optional.empty(); }
        };
        var sandbox = new StrategySandbox(List.of(p1, p2), 500L, eventMetadataFactory);
        assertEquals(2, sandbox.pluginCount());
    }

    @Test
    @Tag("stress")
    void handleAtomicityPreventsDuplicateEmissions() throws Exception {
        // Verifies the Phase 5a fix: CompletableFuture.handle() ensures atomic
        // result coordination — a single plugin evaluation emits either a signal
        // OR an error, never both.
        //
        // Runs 10 concurrent events, each evaluated by 3 plugins (fast, hanging,
        // failing). For each evaluation, the count of signal+error must equal 1.
        //
        // Note: strategyName on anonymous classes is "" (getSimpleName() on anonymous
        // classes returns empty string), so we distinguish error types by detail content.
        var fastPlugin = new StrategyPlugin() {
            @Override public String name() { return "fast"; }
            @Override public Optional<SignalGenerated> onCandleClosed(CandleClosed c) {
                return Optional.of(new SignalGenerated(
                        EventMetadata.root(), "sig-fast", "SBIN", "5m",
                        Side.BUY, 750_00L, 740_00L, 770_00L, "breakout", java.util.Map.of()
                ));
            }
        };
        var hangingPlugin = new StrategyPlugin() {
            @Override public String name() { return "hungry"; }
            @Override public Optional<SignalGenerated> onCandleClosed(CandleClosed c) {
                try { Thread.sleep(30_000L); } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return Optional.empty();
            }
        };
        var failingPlugin = new StrategyPlugin() {
            @Override public String name() { return "failing"; }
            @Override public Optional<SignalGenerated> onCandleClosed(CandleClosed c) {
                throw new RuntimeException("Intentional failure");
            }
        };

        var sandbox = new StrategySandbox(List.of(fastPlugin, hangingPlugin, failingPlugin), 100L, eventMetadataFactory);
        var emitted = new ArrayList<DomainEvent>();

        // Fire 10 concurrent candle evaluations
        int eventCount = 10;
        var threads = new ArrayList<Thread>();
        for (int i = 0; i < eventCount; i++) {
            var t = new Thread(() -> sandbox.onDomainEvent(TEST_CANDLE, emitted::add));
            threads.add(t);
            t.start();
        }
        for (Thread t : threads) {
            t.join(10_000L);
        }

        // Wait for all async evaluations to complete
        awaitEmitted(emitted, eventCount * 3);

        // Each of the 10 events × 3 plugins should contribute exactly 1 emission
        assertEquals(eventCount * 3, emitted.size(),
                "Each plugin evaluation must produce exactly 1 event (signal or error)");

        // Distinguish error types by detail content (strategyName is "" for
        // anonymous classes, since getSimpleName() returns empty string).
        long fastSignals = emitted.stream()
                .filter(e -> e instanceof SignalGenerated)
                .count();
        long slowErrors = emitted.stream()
                .filter(e -> e instanceof StrategyError se && se.detail().contains("Timed out"))
                .count();
        long failErrors = emitted.stream()
                .filter(e -> e instanceof StrategyError se && se.detail().contains("Intentional failure"))
                .count();

        assertEquals(eventCount, fastSignals,
                "Fast plugin should produce exactly " + eventCount + " signals");
        assertEquals(eventCount, slowErrors,
                "Hungry plugin should produce exactly " + eventCount + " timeout errors");
        assertEquals(eventCount, failErrors,
                "Failing plugin should produce exactly " + eventCount + " errors");
    }

    // ── Test support ──

    /**
     * Waits up to 10 seconds for the emitted list to reach the expected size.
     * This is needed because sandbox evaluations run on virtual threads and
     * complete asynchronously.
     */
    private static void awaitEmitted(List<DomainEvent> emitted, int expectedSize) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10_000L;
        while (emitted.size() < expectedSize && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }
    }
}
