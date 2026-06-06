package com.tradej.replay.engine;

import com.tradej.core.domain.event.EventMetadataFactory;
import com.tradej.core.domain.event.MarketTickEvent;
import com.tradej.core.domain.time.TradingClock;
import com.tradej.core.domain.value.ExchangeSegment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class TickReplaySessionTest {

    private static final TradingClock FIXED_CLOCK = new TradingClock() {
        private final long fixedMs = 1_700_000_000_000L;

        @Override
        public Instant instant() {
            return Instant.ofEpochMilli(fixedMs);
        }

        @Override
        public LocalDateTime now() {
            return LocalDateTime.ofInstant(instant(), ZoneId.of("Asia/Kolkata"));
        }

        @Override
        public long millis() {
            return fixedMs;
        }
    };

    private TickReplaySession session;

    @BeforeEach
    void setUp() {
        EventMetadataFactory metadataFactory = new EventMetadataFactory(FIXED_CLOCK);
        session = new TickReplaySession(Optional.empty(), metadataFactory);
    }

    @AfterEach
    void tearDown() {
        session.close();
    }

    // ── load() ────────────────────────────────────────────────────────────

    @Test
    void loadSetsTicksAndResetsState() {
        List<TickReplaySession.TickRecord> ticks = sampleTicks(5);

        session.load(ticks);

        assertEquals(TickReplaySession.ReplayState.STOPPED, session.state());
        assertEquals(0, session.currentIndex());
        assertEquals(5, session.totalTicks());
    }

    @Test
    void loadWithEmptyListResetsState() {
        session.load(List.of());

        assertEquals(TickReplaySession.ReplayState.STOPPED, session.state());
        assertEquals(0, session.currentIndex());
        assertEquals(0, session.totalTicks());
    }

    @Test
    void loadReplacesExistingTicks() {
        session.load(sampleTicks(3));
        assertEquals(3, session.totalTicks());

        session.load(sampleTicks(7));
        assertEquals(7, session.totalTicks());
        assertEquals(0, session.currentIndex());
        assertEquals(TickReplaySession.ReplayState.STOPPED, session.state());
    }

    // ── play() ────────────────────────────────────────────────────────────

    @Test
    void playTransitionsToPlayingState() {
        session.load(sampleTicks(3));
        session.play();

        assertEquals(TickReplaySession.ReplayState.PLAYING, session.state());
    }

    @Test
    void playOnAlreadyPlayingIsNoOp() {
        session.load(sampleTicks(3));
        session.play();
        session.play(); // should not throw or change state

        assertEquals(TickReplaySession.ReplayState.PLAYING, session.state());
    }

    @Test
    void playOnEmptyTicksIsNoOp() {
        session.load(List.of());
        session.play();

        assertEquals(TickReplaySession.ReplayState.STOPPED, session.state());
    }

    // ── pause() ───────────────────────────────────────────────────────────

    @Test
    void pauseTransitionsToPausedState() {
        session.load(sampleTicks(3));
        session.play();
        session.pause();

        assertEquals(TickReplaySession.ReplayState.PAUSED, session.state());
    }

    @Test
    void pausePreservesCurrentIndex() {
        session.load(sampleTicks(5));
        session.step(); // index -> 1
        session.step(); // index -> 2
        session.pause();

        assertEquals(TickReplaySession.ReplayState.PAUSED, session.state());
        assertEquals(2, session.currentIndex());
    }

    // ── stop() ────────────────────────────────────────────────────────────

    @Test
    void stopResetsToStoppedWithIndexZero() {
        session.load(sampleTicks(5));
        session.step();
        session.step();
        session.stop();

        assertEquals(TickReplaySession.ReplayState.STOPPED, session.state());
        assertEquals(0, session.currentIndex());
    }

    @Test
    void stopFromPlayingState() {
        session.load(sampleTicks(3));
        session.play();
        session.stop();

        assertEquals(TickReplaySession.ReplayState.STOPPED, session.state());
        assertEquals(0, session.currentIndex());
    }

    // ── step() ────────────────────────────────────────────────────────────

    @Test
    void stepEmitsOneTickAndAdvancesIndex() {
        session.load(sampleTicks(3));
        assertEquals(0, session.currentIndex());

        session.step();
        assertEquals(1, session.currentIndex());

        session.step();
        assertEquals(2, session.currentIndex());
    }

    @Test
    void stepAtEndDoesNothing() {
        session.load(sampleTicks(2));
        session.step();
        session.step();
        assertEquals(2, session.currentIndex());

        session.step(); // beyond end
        assertEquals(2, session.currentIndex()); // should not advance further
    }

    @Test
    void stepOnEmptyListDoesNothing() {
        session.load(List.of());
        session.step();

        assertEquals(0, session.currentIndex());
    }

    // ── setSpeed() ────────────────────────────────────────────────────────

    @Test
    void setSpeedClampsToLowerBound() {
        session.setSpeed(0.01);
        assertEquals(0.1, session.speed(), 1e-9);
    }

    @Test
    void setSpeedClampsToUpperBound() {
        session.setSpeed(500.0);
        assertEquals(100.0, session.speed(), 1e-9);
    }

    @Test
    void setSpeedAcceptsValueInRange() {
        session.setSpeed(5.0);
        assertEquals(5.0, session.speed(), 1e-9);
    }

    @Test
    void setSpeedAcceptsBoundaryValues() {
        session.setSpeed(0.1);
        assertEquals(0.1, session.speed(), 1e-9);

        session.setSpeed(100.0);
        assertEquals(100.0, session.speed(), 1e-9);
    }

    @Test
    void setSpeedClampsNegativeValue() {
        session.setSpeed(-5.0);
        assertEquals(0.1, session.speed(), 1e-9);
    }

    // ── onTick() listener ─────────────────────────────────────────────────

    @Test
    void onTickListenerReceivesEvents() {
        List<MarketTickEvent> received = new ArrayList<>();
        session.onTick(received::add);

        session.load(sampleTicks(3));
        session.step();
        session.step();

        assertEquals(2, received.size());
        assertEquals("RELIANCE", received.get(0).symbol());
        assertEquals(ExchangeSegment.NSE_EQ, received.get(0).segment());
    }

    @Test
    void onTickListenerReceivesCorrectPrices() {
        List<MarketTickEvent> received = new ArrayList<>();
        session.onTick(received::add);

        List<TickReplaySession.TickRecord> ticks = List.of(
                new TickReplaySession.TickRecord("TCS", ExchangeSegment.NSE_EQ,
                        380_000L, 379_900L, 380_100L, 1_700_000_000_000L)
        );
        session.load(ticks);
        session.step();

        assertEquals(1, received.size());
        MarketTickEvent event = received.get(0);
        assertEquals("TCS", event.symbol());
        assertEquals(380_000L, event.ltpPaisa());
    }

    @Test
    void onTickWithoutListenerDoesNotThrow() {
        session.load(sampleTicks(2));
        assertDoesNotThrow(() -> session.step());
    }

    // ── state(), currentIndex(), totalTicks() accessors ───────────────────

    @Test
    void initialStateIsStopped() {
        assertEquals(TickReplaySession.ReplayState.STOPPED, session.state());
    }

    @Test
    void initialCurrentIndexIsZero() {
        assertEquals(0, session.currentIndex());
    }

    @Test
    void initialTotalTicksIsZero() {
        assertEquals(0, session.totalTicks());
    }

    @Test
    void defaultSpeedIsOne() {
        assertEquals(1.0, session.speed(), 1e-9);
    }

    @Test
    void accessorsReflectFullLifecycle() {
        List<TickReplaySession.TickRecord> ticks = sampleTicks(10);
        session.load(ticks);

        assertEquals(TickReplaySession.ReplayState.STOPPED, session.state());
        assertEquals(0, session.currentIndex());
        assertEquals(10, session.totalTicks());

        session.step();
        session.step();
        session.step();
        assertEquals(3, session.currentIndex());
        assertEquals(10, session.totalTicks());

        session.play();
        assertEquals(TickReplaySession.ReplayState.PLAYING, session.state());

        session.pause();
        assertEquals(TickReplaySession.ReplayState.PAUSED, session.state());

        session.stop();
        assertEquals(TickReplaySession.ReplayState.STOPPED, session.state());
        assertEquals(0, session.currentIndex());
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private static List<TickReplaySession.TickRecord> sampleTicks(int count) {
        List<TickReplaySession.TickRecord> ticks = new ArrayList<>();
        long baseTimestamp = 1_700_000_000_000L;
        for (int i = 0; i < count; i++) {
            ticks.add(new TickReplaySession.TickRecord(
                    "RELIANCE",
                    ExchangeSegment.NSE_EQ,
                    250_000L + i * 100L,
                    249_900L + i * 100L,
                    250_100L + i * 100L,
                    baseTimestamp + i * 1000L
            ));
        }
        return ticks;
    }
}
