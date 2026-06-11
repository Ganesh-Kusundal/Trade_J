package com.tradej.app.e2e;

import com.tradej.core.domain.event.CandleClosed;
import com.tradej.core.domain.event.CandleDeveloping;
import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.event.EventMetadata;
import com.tradej.core.domain.event.MarketTickEvent;
import com.tradej.core.domain.event.SimpleEventBus;
import com.tradej.core.domain.port.DomainEventHandler;
import com.tradej.core.domain.port.EventBus;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.FeedMode;
import com.tradej.strategy.service.CandleAggregationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("runtime-e2e")
class MarketDataFlowEndToEndTest {

    private SimpleEventBus eventBus;
    private CandleAggregationService candleService;

    private final List<MarketTickEvent> tickEvents = new CopyOnWriteArrayList<>();
    private final List<CandleDeveloping> candleDevelopingEvents = new CopyOnWriteArrayList<>();
    private final List<CandleClosed> candleClosedEvents = new CopyOnWriteArrayList<>();

    @BeforeEach
    void setUp() {
        eventBus = new SimpleEventBus();
        candleService = new CandleAggregationService(List.of("1s"));

        eventBus.subscribe(MarketTickEvent.class, tickEvents::add);
        eventBus.subscribe(CandleDeveloping.class, candleDevelopingEvents::add);
        eventBus.subscribe(CandleClosed.class, candleClosedEvents::add);

        eventBus.start();
    }

    @AfterEach
    void tearDown() {
        eventBus.stop();
    }

    @Test
    void marketTickFlowsThroughEventBusToAllSubscribers() {
        MarketTickEvent tick = createTick("RELIANCE", 245000L, 100L, 1000L, 1L);

        eventBus.publish(tick);

        assertThat(tickEvents).hasSize(1);
        assertThat(tickEvents.get(0).symbol()).isEqualTo("RELIANCE");
        assertThat(tickEvents.get(0).ltpPaisa()).isEqualTo(245000L);
        assertThat(tickEvents.get(0).lastTradeQuantity()).isEqualTo(100L);
        assertThat(tickEvents.get(0).cumulativeVolume()).isEqualTo(1000L);
    }

    @Test
    void multipleSubscribersReceiveSameEvent() {
        List<DomainEvent> subscriber1 = new CopyOnWriteArrayList<>();
        List<DomainEvent> subscriber2 = new CopyOnWriteArrayList<>();

        eventBus.subscribe(MarketTickEvent.class, subscriber1::add);
        eventBus.subscribe(MarketTickEvent.class, subscriber2::add);

        MarketTickEvent tick = createTick("INFY", 150000L, 50L, 500L, 1L);
        eventBus.publish(tick);

        assertThat(subscriber1).hasSize(1);
        assertThat(subscriber2).hasSize(1);
        assertThat(subscriber1.get(0)).isSameAs(tick);
        assertThat(subscriber2.get(0)).isSameAs(tick);
    }

    @Test
    void candleAggregationProducesCandleDevelopingOnTick() {
        MarketTickEvent tick = createTick("TCS", 350000L, 200L, 2000L, 1L);

        candleService.onDomainEvent(tick, eventBus::publish);

        assertThat(candleDevelopingEvents).isNotEmpty();
        CandleDeveloping developing = candleDevelopingEvents.get(0);
        assertThat(developing.candle().symbol()).isEqualTo("TCS");
        assertThat(developing.candle().openPaisa()).isEqualTo(350000L);
        assertThat(developing.candle().highPaisa()).isEqualTo(350000L);
        assertThat(developing.candle().lowPaisa()).isEqualTo(350000L);
        assertThat(developing.candle().closePaisa()).isEqualTo(350000L);
        assertThat(developing.candle().volume()).isEqualTo(200L);
    }

    @Test
    void candleAggregationUpdatesOHLCOnSubsequentTicks() {
        candleService.onDomainEvent(createTick("HDFCBANK", 160000L, 100L, 100L, 1L), eventBus::publish);
        candleService.onDomainEvent(createTick("HDFCBANK", 161000L, 150L, 250L, 2L), eventBus::publish);
        candleService.onDomainEvent(createTick("HDFCBANK", 159000L, 50L, 300L, 3L), eventBus::publish);

        assertThat(candleDevelopingEvents).hasSize(3);

        CandleDeveloping last = candleDevelopingEvents.getLast();
        assertThat(last.candle().openPaisa()).isEqualTo(160000L);
        assertThat(last.candle().highPaisa()).isEqualTo(161000L);
        assertThat(last.candle().lowPaisa()).isEqualTo(159000L);
        assertThat(last.candle().closePaisa()).isEqualTo(159000L);
        assertThat(last.candle().volume()).isEqualTo(300L);
    }

    @Test
    void marketTickEventMetadataIsPreservedThroughFlow() {
        EventMetadata metadata = EventMetadata.correlated("corr-123", 42L);
        MarketTickEvent tick = new MarketTickEvent(
                metadata, 42L, "SBIN", ExchangeSegment.NSE_EQ, FeedMode.QUOTE,
                62000L, 75L, 5000L, System.currentTimeMillis(),
                Optional.empty(), 0L, 0L
        );

        eventBus.publish(tick);

        assertThat(tickEvents).hasSize(1);
        assertThat(tickEvents.get(0).metadata().correlationId()).isEqualTo("corr-123");
        assertThat(tickEvents.get(0).sequenceId()).isEqualTo(42L);
    }

    @Test
    void fullPipelineTickToCandleProducesCorrectEventSequence() {
        List<DomainEvent> orderedEvents = new CopyOnWriteArrayList<>();

        eventBus.subscribe(MarketTickEvent.class, orderedEvents::add);
        eventBus.subscribe(CandleDeveloping.class, orderedEvents::add);
        eventBus.subscribe(CandleClosed.class, orderedEvents::add);

        MarketTickEvent tick1 = createTick("WIPRO", 45000L, 100L, 100L, 1L);
        MarketTickEvent tick2 = createTick("WIPRO", 46000L, 200L, 300L, 2L);

        eventBus.publish(tick1);
        candleService.onDomainEvent(tick1, eventBus::publish);
        eventBus.publish(tick2);
        candleService.onDomainEvent(tick2, eventBus::publish);

        assertThat(orderedEvents).hasSize(4);
        assertThat(orderedEvents.get(0)).isSameAs(tick1);
        assertThat(orderedEvents.get(1)).isInstanceOf(CandleDeveloping.class);
        assertThat(orderedEvents.get(2)).isSameAs(tick2);
        assertThat(orderedEvents.get(3)).isInstanceOf(CandleDeveloping.class);
    }

    @Test
    void eventBusHandlesConcurrentPublishesFromMultipleThreads() throws InterruptedException {
        int threadCount = 8;
        int eventsPerThread = 100;
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            new Thread(() -> {
                try {
                    for (int i = 0; i < eventsPerThread; i++) {
                        MarketTickEvent tick = createTick(
                                "SYM-" + threadId, 100000L + i, 10L, i * 10L,
                                threadId * eventsPerThread + i
                        );
                        eventBus.publish(tick);
                    }
                } finally {
                    latch.countDown();
                }
            }).start();
        }

        latch.await(5, TimeUnit.SECONDS);

        assertThat(tickEvents).hasSize(threadCount * eventsPerThread);
    }

    @Test
    void unsubscribeStopsReceivingEvents() {
        List<DomainEvent> temporary = new CopyOnWriteArrayList<>();
        DomainEventHandler<MarketTickEvent> handler = temporary::add;

        eventBus.subscribe(MarketTickEvent.class, handler);
        eventBus.publish(createTick("A", 100L, 1L, 1L, 1L));
        assertThat(temporary).hasSize(1);

        eventBus.unsubscribe(MarketTickEvent.class, handler);
        eventBus.publish(createTick("B", 200L, 1L, 2L, 2L));
        assertThat(temporary).hasSize(1);
    }

    @Test
    void eventBusMetricsReflectSubscriberState() {
        assertThat(eventBus.eventTypeCount()).isEqualTo(3);
        assertThat(eventBus.subscriberCount()).isGreaterThanOrEqualTo(3);
    }

    private MarketTickEvent createTick(String symbol, long ltpPaisa, long lastTradeQty,
                                        long cumulativeVolume, long sequenceId) {
        return new MarketTickEvent(
                EventMetadata.root(), sequenceId, symbol, ExchangeSegment.NSE_EQ, FeedMode.TICKER,
                ltpPaisa, lastTradeQty, cumulativeVolume, System.currentTimeMillis(),
                Optional.empty(), 0L, 0L
        );
    }
}
