package com.tradej.app.config;

import com.tradej.core.domain.event.SimpleEventBus;
import com.tradej.disruptor.DisruptorBusMetrics;

class SimpleBusMetrics implements DisruptorBusMetrics {
    private final SimpleEventBus bus;

    SimpleBusMetrics(SimpleEventBus bus) {
        this.bus = bus;
    }

    @Override public long ringBufferRemainingCapacity() { return Long.MAX_VALUE; }
    @Override public int ringBufferSize() { return 0; }
    @Override public int dispatchQueueDepth() { return 0; }
    @Override public long dispatchDroppedEventCount() { return 0; }
    @Override public int subscriberCount() { return bus.subscriberCount(); }
    @Override public boolean isStarted() { return bus.isStarted(); }
}

class NoOpBusMetrics implements DisruptorBusMetrics {
    @Override public long ringBufferRemainingCapacity() { return 0; }
    @Override public int ringBufferSize() { return 0; }
    @Override public int dispatchQueueDepth() { return 0; }
    @Override public long dispatchDroppedEventCount() { return 0; }
    @Override public int subscriberCount() { return 0; }
    @Override public boolean isStarted() { return false; }
}

class NoOpMarketDataPipeline {
    public long totalTicksProcessed() { return 0; }
    public double tickRate() { return 0; }
    public long lastTickTimestampMs() { return 0; }
}

class NoOpOrderPipeline {
    public long totalOrdersAccepted() { return 0; }
    public double orderRate() { return 0; }
}
