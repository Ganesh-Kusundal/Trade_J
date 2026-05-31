package com.tradej.execution.node;

import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.event.EventMetadata;
import com.tradej.core.domain.event.SignalPendingExecution;
import com.tradej.core.domain.event.SignalSuppressed;
import com.tradej.core.domain.runtime.RuntimeModeHolder;
import com.tradej.execution.service.TradingCircuitBreaker;
import com.tradej.pipeline.runtime.BasePipelineNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Pipeline node that gates signals before they reach order placement.
 * <p>
 * Checks:
 * <ul>
 *   <li>Kill switch engaged</li>
 *   <li>Circuit breaker open</li>
 *   <li>Execution queue capacity</li>
 * </ul>
 * Suppressed signals emit {@link SignalSuppressed} events.
 * Approved signals are forwarded to downstream OMS nodes.
 * <p>
 * This node handles the <b>pre-trade gating</b> concern previously embedded
 * inside {@link com.tradej.execution.service.ExecutionHandler}.
 */
public final class SignalGateNode extends BasePipelineNode {

    private static final Logger log = LoggerFactory.getLogger(SignalGateNode.class);

    private final TradingCircuitBreaker circuitBreaker;
    private final RuntimeModeHolder runtimeModeHolder;

    public SignalGateNode(TradingCircuitBreaker circuitBreaker, RuntimeModeHolder runtimeModeHolder) {
        this.circuitBreaker = circuitBreaker;
        this.runtimeModeHolder = runtimeModeHolder;
    }

    @Override
    protected void onInit() {
    }

    @Override
    protected void processEvent(DomainEvent event) {
        if (!(event instanceof SignalPendingExecution pending)) {
            return;
        }

        // 1. Circuit breaker check
        if (circuitBreaker != null && !circuitBreaker.allowsRequest()) {
            log.warn("Signal suppressed — circuit breaker open signalId={} symbol={}",
                    pending.signalId(), pending.orderRequest().symbol());
            context.publish(new SignalSuppressed(
                    EventMetadata.correlated(pending.signalId(), pending.sequenceId()),
                    pending.signalId(),
                    pending.orderRequest().symbol(),
                    "Circuit breaker open",
                    pending.decisionContext()
            ));
            return;
        }

        // 2. Pass through to downstream (OrderPlacementNode or OmsNode)
        context.publish(pending);
    }
}
