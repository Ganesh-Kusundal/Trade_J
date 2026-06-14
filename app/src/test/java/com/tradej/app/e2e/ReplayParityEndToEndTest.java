package com.tradej.app.e2e;

import com.tradej.core.domain.event.CandleClosed;
import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.event.EventMetadataFactory;
import com.tradej.core.domain.event.MarketTickEvent;
import com.tradej.core.domain.event.ReplayTimeChangedEvent;
import com.tradej.core.domain.event.SignalGenerated;
import com.tradej.core.domain.event.SimpleEventBus;
import com.tradej.core.domain.model.Candle;
import com.tradej.core.domain.port.EventBus;
import com.tradej.core.domain.time.LiveTradingClock;
import com.tradej.replay.engine.ReplayController;
import com.tradej.strategy.api.GraphStrategyPlugin;
import com.tradej.strategy.service.GraphStrategySandbox;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Live-vs-replay parity test. For every registered
 * {@link GraphStrategyPlugin}, replays a fixed historical candle stream
 * through the {@link ReplayController} wrapped in a
 * {@link GraphStrategySandbox}, and asserts that the set of
 * {@link SignalGenerated} events is deterministic across two runs.
 *
 * <p>This is the standard "did the live change break the replay" smoke
 * test. Each plugin is run twice with the same input — the two signal
 * sequences must be equal. If a plugin is non-deterministic by design
 * (e.g. uses {@code System.currentTimeMillis()}) it should be excluded
 * from {@code META-INF/services/com.tradej.strategy.api.GraphStrategyPlugin}.
 */
@Tag("runtime-e2e")
class ReplayParityEndToEndTest {

    @TempDir
    Path tempDir;

    @Test
    void signalSequenceIsDeterministicAcrossRuns() {
        GraphStrategyPlugin[] plugins = ServiceLoader.load(GraphStrategyPlugin.class)
                .stream()
                .map(ServiceLoader.Provider::get)
                .toArray(GraphStrategyPlugin[]::new);
        if (plugins.length == 0) {
            // Nothing to certify yet — skip without failing.
            return;
        }

        EventMetadataFactory metadataFactory =
                new EventMetadataFactory(new LiveTradingClock(java.time.Clock.systemDefaultZone()));

        for (GraphStrategyPlugin plugin : plugins) {
            List<SignalGenerated> run1 = runReplay(plugin, metadataFactory);
            List<SignalGenerated> run2 = runReplay(plugin, metadataFactory);
            assertThat(run1)
                    .as("Replay must be deterministic for plugin " + plugin.getClass().getSimpleName())
                    .isEqualTo(run2);
        }
    }

    @Test
    void unknownPluginIdIsRejected() {
        // The StrategyReplayParityReporter must reject unknown plugin ids
        // by throwing IllegalArgumentException so the CLI sub-command
        // gets a non-zero exit code instead of producing an empty report.
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> com.tradej.strategy.certification.StrategyReplayParityReporter
                        .resolvePlugin("definitely-not-a-plugin-id"));
    }

    private List<SignalGenerated> runReplay(GraphStrategyPlugin plugin, EventMetadataFactory factory) {
        EventBus bus = new SimpleEventBus();
        List<DomainEvent> captured = new CopyOnWriteArrayList<>();
        bus.subscribe(SignalGenerated.class, captured::add);
        bus.subscribe(CandleClosed.class, captured::add);
        bus.subscribe(MarketTickEvent.class, captured::add);
        bus.subscribe(ReplayTimeChangedEvent.class, captured::add);
        bus.start();

        try {
            // Drive a fixed sequence: 60 one-minute candles starting at 09:15.
            long baseMs = java.time.LocalDate.of(2026, 6, 11)
                    .atStartOfDay(java.time.ZoneId.of("Asia/Kolkata"))
                    .toInstant().toEpochMilli();
            List<Candle> candles = new ArrayList<>();
            for (int i = 0; i < 60; i++) {
                long t = baseMs + i * 60_000L;
                candles.add(new Candle(
                        "SBIN", "1m", t, t + 59_000L,
                        75_000L + i * 5L, 75_100L + i * 5L, 75_000L + i * 5L,
                        75_050L + i * 5L, 100L, true
                ));
            }

            // Wire the plugin through the sandbox so signals are produced
            // via the real evaluation path (not bypassing it).
            GraphStrategySandbox sandbox = new GraphStrategySandbox(
                    List.of(plugin), 5_000L, factory);
            bus.subscribe(CandleClosed.class,
                    e -> sandbox.onDomainEvent(e, captured::add));
            bus.subscribe(MarketTickEvent.class,
                    e -> sandbox.onDomainEvent(e, captured::add));

            ReplayController controller = new ReplayController(bus);
            controller.start(candles);
            while (controller.step()) {
                // drain
            }
            controller.stop();
        } finally {
            bus.stop();
        }

        return captured.stream()
                .filter(SignalGenerated.class::isInstance)
                .map(SignalGenerated.class::cast)
                .collect(Collectors.toList());
    }
}
