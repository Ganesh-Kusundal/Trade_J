package com.tradej.options.node;

import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.event.EventMetadata;
import com.tradej.core.domain.event.GreeksComputed;
import com.tradej.core.domain.event.OptionChainUpdated;
import com.tradej.core.domain.model.Instrument;
import com.tradej.core.domain.model.OptionChainEntry;
import com.tradej.core.domain.model.OptionChainSnapshot;
import com.tradej.core.domain.model.OptionGreeks;
import com.tradej.core.domain.model.OptionQuote;
import com.tradej.options.greeks.OptionsAnalyticsCache;
import com.tradej.core.domain.value.Exchange;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.OptionType;
import com.tradej.pipeline.graph.PipelineNodeDef;
import com.tradej.pipeline.runtime.PipelineContext;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class GreeksCalcNodeTest {

    @Test
    void computeGreeksForOptionChain() {
        OptionsAnalyticsCache cache = new OptionsAnalyticsCache();
        CopyOnWriteArrayList<DomainEvent> published = new CopyOnWriteArrayList<>();

        GreeksCalcNode node = new GreeksCalcNode(cache);
        node.init(new PipelineNodeDef("greeks-1", "Greeks", "Greeks", Map.of()), new PipelineContext() {
            @Override public void publish(DomainEvent event) { published.add(event); }
            @Override public long getClockTimeMs() { return System.currentTimeMillis(); }
            @Override public <T> Optional<T> getService(Class<T> serviceType) { return Optional.empty(); }
        });

        // Create a simple option chain
        Instrument underlying = new Instrument("NIFTY", "NIFTY", Exchange.NSE, ExchangeSegment.NSE_EQ,
                "INDEX", "NIFTY", null, 0L, null, 1, 0);
        // Provide pre-computed greeks so the node doesn't need to solve IV
        OptionGreeks callGreeks = new OptionGreeks(0.55, -0.02, 0.003, 0.18, 0.20);
        OptionGreeks putGreeks = new OptionGreeks(-0.45, -0.02, 0.003, 0.18, 0.20);
        OptionQuote callQuote = new OptionQuote(
                new Instrument("NIFTY25000CE", "NIFTY", Exchange.NSE, ExchangeSegment.NSE_EQ,
                        "OPTION", "NIFTY25000CE", LocalDate.now().plusDays(7), 25000_00L, OptionType.CALL, 1, 0),
                150_00L, 10000, 5000, 148_00L, 100, 152_00L, 100, callGreeks);
        OptionQuote putQuote = new OptionQuote(
                new Instrument("NIFTY25000PE", "NIFTY", Exchange.NSE, ExchangeSegment.NSE_EQ,
                        "OPTION", "NIFTY25000PE", LocalDate.now().plusDays(7), 25000_00L, OptionType.PUT, 1, 0),
                80_00L, 10000, 5000, 78_00L, 100, 82_00L, 100, putGreeks);
        OptionChainEntry entry = new OptionChainEntry(25000_00L, callQuote, putQuote);
        OptionChainSnapshot chain = new OptionChainSnapshot(underlying, LocalDate.now().plusDays(7), 25100_00L, List.of(entry));

        // Process
        node.onEvent(new OptionChainUpdated(EventMetadata.root(), chain));

        // Verify Greeks were published
        assertFalse(published.isEmpty(), "Should publish GreeksComputed events");
        assertTrue(published.stream().allMatch(e -> e instanceof GreeksComputed),
                "All published events should be GreeksComputed");

        GreeksComputed computed = (GreeksComputed) published.getFirst();
        assertNotNull(computed.instrumentKey());
        assertNotNull(computed.greeks());
        assertNotNull(computed.greeks().delta());
    }

    @Test
    void noopForNonOptionChainEvents() {
        OptionsAnalyticsCache cache = new OptionsAnalyticsCache();
        CopyOnWriteArrayList<DomainEvent> published = new CopyOnWriteArrayList<>();

        GreeksCalcNode node = new GreeksCalcNode(cache);
        node.init(new PipelineNodeDef("greeks-1", "Greeks", "Greeks", Map.of()), new PipelineContext() {
            @Override public void publish(DomainEvent event) { published.add(event); }
            @Override public long getClockTimeMs() { return System.currentTimeMillis(); }
            @Override public <T> Optional<T> getService(Class<T> serviceType) { return Optional.empty(); }
        });

        // Non-option event should be no-op — use a CandleClosed as a non-option event
        node.onEvent(new com.tradej.core.domain.event.CandleClosed(
                EventMetadata.root(),
                new com.tradej.core.domain.model.Candle("NIFTY", "1m", 0, 0, 25000_00L, 25100_00L, 24900_00L, 25050_00L, 1000, true)));
        assertTrue(published.isEmpty(), "Should not publish for non-option events");
    }
}
