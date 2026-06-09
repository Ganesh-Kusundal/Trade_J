package com.tradej.broker.dhan.adapter;

import com.tradej.broker.dhan.client.DhanClientHolder;
import com.tradej.broker.dhan.instrument.DhanInstrumentDefinition;
import com.tradej.broker.dhan.rate.ApiCategory;
import com.tradej.broker.dhan.resilience.DhanRetryExecutor;
import com.tradej.core.domain.model.InstrumentKey;
import com.tradej.core.domain.value.ExchangeSegment;

import java.util.function.Supplier;

public final class DhanAdapterContext {
    private final DhanClientHolder clientHolder;
    private final DhanInstrumentResolver resolver;
    private final DhanRetryExecutor executor;

    public DhanAdapterContext(
            DhanClientHolder clientHolder,
            DhanInstrumentResolver resolver,
            DhanRetryExecutor executor
    ) {
        this.clientHolder = clientHolder;
        this.resolver = resolver;
        this.executor = executor;
    }

    public DhanAdapterContext(
            DhanInstrumentResolver resolver,
            DhanRetryExecutor executor
    ) {
        this.clientHolder = null;
        this.resolver = resolver;
        this.executor = executor;
    }

    public DhanClientHolder clientHolder() {
        if (clientHolder == null) {
            throw new UnsupportedOperationException("This adapter context does not use DhanClientHolder");
        }
        return clientHolder;
    }

    public boolean hasClientHolder() {
        return clientHolder != null;
    }

    public DhanInstrumentResolver resolver() {
        return resolver;
    }

    public DhanRetryExecutor executor() {
        return executor;
    }

    // ---- Helper Shortcuts ----

    public DhanInstrumentDefinition resolveDef(InstrumentKey key) {
        return resolver.requireDhanDefinition(key);
    }

    public DhanInstrumentDefinition resolveDef(String symbol, ExchangeSegment segment) {
        return resolver.requireDhanDefinition(symbol, segment);
    }

    public DhanInstrumentDefinition resolvePayload(Object payload) {
        return resolver.resolveDhanPayload(payload);
    }

    public <T> T execute(ApiCategory category, String operation, Supplier<T> supplier) {
        return executor.execute(category, operation, supplier);
    }

    public void run(ApiCategory category, String operation, Runnable action) {
        executor.run(category, operation, action);
    }
}
