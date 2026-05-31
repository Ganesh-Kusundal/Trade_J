package com.tradej.disruptor.config;

import com.lmax.disruptor.EventHandler;
import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.port.FeatureStore;
import com.tradej.disruptor.MutableDomainEventEnvelope;

/**
 * Synchronously feeds market events into the hot-path {@link FeatureStore}
 * before the strategy stage so ML plugins see up-to-date candle features.
 */
public final class FeatureSyncDisruptorHandler implements EventHandler<MutableDomainEventEnvelope> {

    private final FeatureStore featureStore;
    private final StageTiming timing;

    public FeatureSyncDisruptorHandler(FeatureStore featureStore) {
        this(featureStore, StageTiming.noOp());
    }

    public FeatureSyncDisruptorHandler(FeatureStore featureStore, StageTiming timing) {
        this.featureStore = featureStore;
        this.timing = timing;
    }

    @Override
    public void onEvent(MutableDomainEventEnvelope envelope, long sequence, boolean endOfBatch) {
        DomainEvent event = envelope.event();
        if (event == null) {
            return;
        }
        long start = System.nanoTime();
        try {
            featureStore.feed(event);
        } finally {
            timing.record(System.nanoTime() - start);
        }
    }
}
