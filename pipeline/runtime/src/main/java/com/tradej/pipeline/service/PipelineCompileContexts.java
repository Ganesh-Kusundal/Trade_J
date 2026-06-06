package com.tradej.pipeline.service;

import com.tradej.core.domain.event.DomainEvent;
import com.tradej.pipeline.clock.VirtualClock;
import com.tradej.pipeline.runtime.PipelineContext;

import java.util.Optional;

final class PipelineCompileContexts {

    private PipelineCompileContexts() {
    }

    static PipelineContext create(VirtualClock virtualClock) {
        return new PipelineContext() {
            @Override
            public void publish(DomainEvent event) {
            }

            @Override
            public long getClockTimeMs() {
                return virtualClock.currentTimeMillis();
            }

            @Override
            public <T> Optional<T> getService(Class<T> serviceType) {
                if (serviceType.isInstance(virtualClock)) {
                    return Optional.of(serviceType.cast(virtualClock));
                }
                return Optional.empty();
            }
        };
    }
}
