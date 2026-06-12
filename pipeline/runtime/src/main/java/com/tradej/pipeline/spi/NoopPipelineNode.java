package com.tradej.pipeline.spi;

import com.tradej.core.domain.event.DomainEvent;
import com.tradej.pipeline.runtime.BasePipelineNode;
import com.tradej.pipeline.runtime.PipelineNode;

/**
 * Shared no-op pipeline node used by SPI providers when a required
 * collaborator is missing from the wiring config. Matches the legacy
 * fallback behaviour in {@code PipelineNodeFactory}.
 */
public final class NoopPipelineNode {

    private NoopPipelineNode() {}

    public static PipelineNode create(String reason) {
        return new BasePipelineNode() {
            private final String label = reason;

            @Override
            protected void onInit() {
            }

            @Override
            protected void processEvent(DomainEvent event) {
            }

            @Override
            protected void onError(DomainEvent event, Throwable t) {
            }

            @Override
            public String toString() {
                return "NoopPipelineNode(" + label + ")";
            }
        };
    }
}
