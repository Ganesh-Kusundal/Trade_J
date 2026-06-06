package com.tradej.scanner.engine;

import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.event.EventMetadata;
import com.tradej.core.domain.event.MarketTickEvent;
import com.tradej.core.domain.scan.ScanHit;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.FeedMode;
import com.tradej.scanner.criterion.StreamingScanCriterion;
import com.tradej.scanner.model.ScanContext;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class ScanEngineStreamingTest {

    @Test
    void registerStreamingCriterion_increasesCount() {
        ScanEngine engine = createMinimalEngine();
        assertEquals(0, engine.streamingCriterionCount());

        engine.registerStreamingCriterion(new TestStreamingCriterion(true));
        assertEquals(1, engine.streamingCriterionCount());
    }

    @Test
    void onEvent_routesToMatchingCriterion() {
        ScanEngine engine = createMinimalEngine();
        CopyOnWriteArrayList<ScanHit> hits = new CopyOnWriteArrayList<>();
        engine.onStreamingHit(hits::add);

        engine.registerStreamingCriterion(new TestStreamingCriterion(true));

        engine.onEvent(createTick());

        assertEquals(1, hits.size());
        assertTrue(hits.get(0).promoted());
    }

    @Test
    void onEvent_skipsNonMatchingCriterion() {
        ScanEngine engine = createMinimalEngine();
        CopyOnWriteArrayList<ScanHit> hits = new CopyOnWriteArrayList<>();
        engine.onStreamingHit(hits::add);

        engine.registerStreamingCriterion(new TestStreamingCriterion(false));

        engine.onEvent(createTick());

        assertTrue(hits.isEmpty());
    }

    @Test
    void onEvent_noCriteria_doesNothing() {
        ScanEngine engine = createMinimalEngine();
        CopyOnWriteArrayList<ScanHit> hits = new CopyOnWriteArrayList<>();
        engine.onStreamingHit(hits::add);

        engine.onEvent(createTick());

        assertTrue(hits.isEmpty());
    }

    @Test
    void resetStreamingCriteria_resetsAllRegistered() {
        ScanEngine engine = createMinimalEngine();
        TestStreamingCriterion criterion = new TestStreamingCriterion(true);
        engine.registerStreamingCriterion(criterion);

        engine.resetStreamingCriteria();
        assertTrue(criterion.wasReset);
    }

    private static MarketTickEvent createTick() {
        return new MarketTickEvent(
                EventMetadata.root(), 1L, "NIFTY", ExchangeSegment.NSE_EQ,
                FeedMode.TICKER, 25000_00L, 100L, 5000L, 0L,
                Optional.empty(), 0L, 0L);
    }

    private static ScanEngine createMinimalEngine() {
        return new ScanEngine(new ScanDependencies(null, null, null, null));
    }

    private static final class TestStreamingCriterion implements StreamingScanCriterion {
        private final boolean shouldMatch;
        boolean wasReset = false;

        TestStreamingCriterion(boolean shouldMatch) {
            this.shouldMatch = shouldMatch;
        }

        @Override public String type() { return "test"; }

        @Override
        public void onEvent(DomainEvent event, ScanContext context) {
        }

        @Override
        public List<Class<? extends DomainEvent>> subscribedEventTypes() {
            return List.of(MarketTickEvent.class);
        }

        @Override
        public void reset() {
            wasReset = true;
        }

        @Override
        public boolean matches(ScanContext context) {
            return shouldMatch;
        }

        @Override
        public double score(ScanContext context) {
            return 1.0;
        }

        @Override
        public String reason(ScanContext context) {
            return "test match";
        }
    }
}
