package com.tradej.disruptor;

import com.lmax.disruptor.BusySpinWaitStrategy;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.port.DomainEventHandler;
import com.tradej.core.domain.port.EventBus;
import com.tradej.disruptor.config.CandleAggregationDisruptorHandler;
import com.tradej.disruptor.config.ExecutionDisruptorHandler;
import com.tradej.disruptor.config.PositionRiskDisruptorHandler;
import com.tradej.disruptor.config.StrategyDisruptorHandler;
import com.tradej.disruptor.config.SubscriberDispatchHandler;
import com.tradej.execution.risk.PositionRiskHandler;
import com.tradej.execution.service.ExecutionHandler;
import com.tradej.strategy.service.CandleAggregationService;
import com.tradej.strategy.service.StrategyEngine;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;

public final class DisruptorEventBus implements EventBus {
    private final Map<Class<? extends DomainEvent>, List<DomainEventHandler<? extends DomainEvent>>> subscribers = new ConcurrentHashMap<>();
    private final Map<String, Long> seenEvents = new ConcurrentHashMap<>();
    private final Disruptor<MutableDomainEventEnvelope> disruptor;
    private final ExecutionHandler executionHandler;
    private volatile boolean started;

    public DisruptorEventBus(
            PositionRiskHandler positionRiskHandler,
            CandleAggregationService candleAggregationService,
            StrategyEngine strategyEngine,
            ExecutionHandler executionHandler
    ) {
        this.executionHandler = executionHandler;
        this.disruptor = new Disruptor<>(
                MutableDomainEventEnvelope::new,
                8192,
                Executors.defaultThreadFactory(),
                ProducerType.MULTI,
                new BusySpinWaitStrategy()
        );

        PositionRiskDisruptorHandler riskStage = new PositionRiskDisruptorHandler(positionRiskHandler, this::publish);
        CandleAggregationDisruptorHandler candleStage = new CandleAggregationDisruptorHandler(candleAggregationService, this::publish);
        StrategyDisruptorHandler strategyStage = new StrategyDisruptorHandler(strategyEngine, this::publish);
        ExecutionDisruptorHandler executionStage = new ExecutionDisruptorHandler(executionHandler, this::publish);
        SubscriberDispatchHandler dispatchStage = new SubscriberDispatchHandler(subscribers);

        disruptor.handleEventsWith(riskStage)
                .then(candleStage)
                .then(strategyStage)
                .then(executionStage)
                .then(dispatchStage);
    }

    @Override
    public <T extends DomainEvent> void subscribe(Class<T> eventType, DomainEventHandler<T> handler) {
        subscribers.computeIfAbsent(eventType, ignored -> new CopyOnWriteArrayList<>()).add(handler);
    }

    @Override
    public <T extends DomainEvent> void unsubscribe(Class<T> eventType, DomainEventHandler<T> handler) {
        subscribers.getOrDefault(eventType, List.of()).remove(handler);
    }

    @Override
    public void publish(DomainEvent event) {
        if (event == null || isDuplicate(event)) {
            return;
        }
        disruptor.getRingBuffer().publishEvent((envelope, sequence) -> envelope.setEvent(event));
    }

    @Override
    public void start() {
        if (started) {
            return;
        }
        executionHandler.start();
        disruptor.start();
        started = true;
    }

    @Override
    public void stop() {
        executionHandler.stop();
        disruptor.shutdown();
        started = false;
    }

    private boolean isDuplicate(DomainEvent event) {
        long now = System.currentTimeMillis();
        pruneOldEntries(now);
        Long previous = seenEvents.putIfAbsent(event.eventId(), now);
        return previous != null;
    }

    private void pruneOldEntries(long nowMs) {
        long ttlMs = Duration.ofMinutes(5).toMillis();
        Set<String> keys = seenEvents.keySet();
        for (String key : keys) {
            Long timestamp = seenEvents.get(key);
            if (timestamp != null && (nowMs - timestamp) > ttlMs) {
                seenEvents.remove(key, timestamp);
            }
        }
    }
}
