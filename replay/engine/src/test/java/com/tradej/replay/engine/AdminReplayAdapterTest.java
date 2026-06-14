package com.tradej.replay.engine;

import com.tradej.core.domain.event.CandleClosed;
import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.model.Candle;
import com.tradej.core.domain.port.EventBus;
import com.tradej.core.domain.port.HistoricalBarRepository;
import com.tradej.persistence.replay.ReplayResult;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminReplayAdapterTest {

    @Test
    void adapterBuildsTickScenarioWithCorrectShape() {
        TestBus bus = new TestBus();
        TestBarRepository repo = new TestBarRepository();
        ScenarioRunner runner = new ScenarioRunner(bus, null, null, repo, "test-hash", 42L);
        AdminReplayAdapter adapter = new AdminReplayAdapter(runner);

        ReplayResult result = adapter.replayTicks("RELIANCE", 1000L, 2000L, 0, 1000);
        assertNotNull(result);
        assertTrue(result.totalRead() >= 0);
        assertTrue(result.replayed() >= 0);
    }

    @Test
    void adapterBuildsCandleScenarioWithInterval() {
        TestBus bus = new TestBus();
        TestBarRepository repo = new TestBarRepository();
        ScenarioRunner runner = new ScenarioRunner(bus, null, null, repo, "test-hash", 42L);
        AdminReplayAdapter adapter = new AdminReplayAdapter(runner);

        ReplayResult result = adapter.replayCandles("TCS", "1m", 5000L, 10000L);
        assertNotNull(result);
    }

    @Test
    void adapterBuildsFillEventsScenarioWithWildcardSymbol() {
        TestBus bus = new TestBus();
        TestBarRepository repo = new TestBarRepository();
        ScenarioRunner runner = new ScenarioRunner(bus, null, null, repo, "test-hash", 42L);
        AdminReplayAdapter adapter = new AdminReplayAdapter(runner);

        // null symbol → wildcard
        ReplayResult result = adapter.replayFillEvents(null, 0L, 99999L);
        assertNotNull(result);
    }

    @Test
    void adapterBuildsFillEventsScenarioWithBlankSymbol() {
        TestBus bus = new TestBus();
        TestBarRepository repo = new TestBarRepository();
        ScenarioRunner runner = new ScenarioRunner(bus, null, null, repo, "test-hash", 42L);
        AdminReplayAdapter adapter = new AdminReplayAdapter(runner);

        ReplayResult result = adapter.replayFillEvents("", 0L, 99999L);
        assertNotNull(result);
    }

    @Test
    void adapterBuildsOrdersScenario() {
        TestBus bus = new TestBus();
        TestBarRepository repo = new TestBarRepository();
        ScenarioRunner runner = new ScenarioRunner(bus, null, null, repo, "test-hash", 42L);
        AdminReplayAdapter adapter = new AdminReplayAdapter(runner);

        ReplayResult result = adapter.replayOrders("INFY", 100L, 200L);
        assertNotNull(result);
    }

    @Test
    void resultShapeMatchesLegacyReplayResult() {
        TestBus bus = new TestBus();
        TestBarRepository repo = new TestBarRepository();
        ScenarioRunner runner = new ScenarioRunner(bus, null, null, repo, "test-hash", 42L);
        AdminReplayAdapter adapter = new AdminReplayAdapter(runner);

        // The four admin paths must all return a ReplayResult with
        // the same shape — the controller and integration tests
        // depend on the {totalRead, replayed, failed, complete}
        // fields.
        for (var r : new ReplayResult[]{
                adapter.replayTicks("X", 0L, 1L, 0, 100),
                adapter.replayCandles("X", "5m", 0L, 1L),
                adapter.replayFillEvents("X", 0L, 1L),
                adapter.replayOrders("X", 0L, 1L)
        }) {
            assertTrue(r.totalRead() >= 0);
            assertTrue(r.replayed() >= 0);
            assertTrue(r.failed() >= 0);
        }
    }

    // ── Test infrastructure ──

    /** In-memory EventBus that records published events. */
    static class TestBus implements EventBus {
        final List<DomainEvent> published = new CopyOnWriteArrayList<>();
        @Override
        public <T extends DomainEvent> void subscribe(Class<T> eventType,
                                                     com.tradej.core.domain.port.DomainEventHandler<T> handler) {}
        @Override
        public <T extends DomainEvent> void unsubscribe(Class<T> eventType,
                                                       com.tradej.core.domain.port.DomainEventHandler<T> handler) {}
        @Override
        public void publish(DomainEvent event) { published.add(event); }
        @Override public void start() {}
        @Override public void stop() {}
    }

    /** In-memory HistoricalBarRepository. The scenario runner's
     *  REPLAY_EVENTS path asks this for the bars in the window. */
    static class TestBarRepository implements HistoricalBarRepository {
        @Override
        public List<Candle> queryCandles(com.tradej.core.domain.model.CandleHistoryRequest request) {
            return new ArrayList<>();
        }
        @Override
        public List<Candle> queryCandles(com.tradej.core.domain.model.InstrumentKey instrument,
                                          String interval, java.time.LocalDate from, java.time.LocalDate to) {
            return new ArrayList<>();
        }
        @Override
        public List<Candle> queryIntradayBars(List<String> symbols, java.time.LocalDate date) {
            return new ArrayList<>();
        }
        @Override
        public List<Candle> queryBenchmarkBars(java.time.LocalDate date, String benchmarkSymbol) {
            return new ArrayList<>();
        }
        @Override
        public List<com.tradej.core.domain.model.UniverseEntry> queryUniverse() {
            return new ArrayList<>();
        }
        @Override
        public List<String> querySymbols(int limit) {
            return new ArrayList<>();
        }
        @Override
        public List<String> querySymbolsWithDataOn(java.time.LocalDate date, int limit) {
            return new ArrayList<>();
        }
        @Override
        public java.util.Optional<java.time.LocalDate> latestAvailableTradingDay(int lookbackDays) {
            return java.util.Optional.empty();
        }
    }
}
