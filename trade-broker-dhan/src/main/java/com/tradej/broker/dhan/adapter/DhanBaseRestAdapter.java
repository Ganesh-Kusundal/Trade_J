package com.tradej.broker.dhan.adapter;

import com.tradej.broker.dhan.client.DhanClientHolder;
import com.tradej.broker.dhan.instrument.DhanInstrumentDefinition;
import com.tradej.broker.dhan.mapper.DhanSdkResponse;
import com.tradej.broker.dhan.rate.ApiCategory;
import com.tradej.broker.dhan.resilience.DhanResilienceExecutor;
import com.tradej.core.domain.model.InstrumentKey;
import com.tradej.core.domain.value.ExchangeSegment;

import java.util.function.Supplier;

/**
 * Abstract base class for Dhan REST adapters.
 *
 * <p>Eliminates the duplicated constructor/field boilerplate across
 * {@link DhanOrderCommandAdapter}, {@link DhanOrderQueryAdapter},
 * {@link DhanPortfolioProvider}, and {@link DhanMarketDataProvider}.
 *
 * <p>Subclasses call {@code super(clientHolder, resolver, executor)} and
 * add any adapter-specific fields (e.g. idempotency cache, historical data).
 */
public abstract class DhanBaseRestAdapter {
    protected final DhanClientHolder clientHolder;
    protected final DhanInstrumentResolver resolver;
    protected final DhanResilienceExecutor executor;

    protected DhanBaseRestAdapter(
            DhanClientHolder clientHolder,
            DhanInstrumentResolver resolver,
            DhanResilienceExecutor executor
    ) {
        this.clientHolder = clientHolder;
        this.resolver = resolver;
        this.executor = executor;
    }

    /**
     * Constructor for adapters that do not use the Dhan SDK client
     * (e.g. {@link DhanOptionsAdapter} which uses the REST HTTP client instead).
     * The {@link #clientHolder()} accessor will throw if called.
     */
    protected DhanBaseRestAdapter(
            DhanInstrumentResolver resolver,
            DhanResilienceExecutor executor
    ) {
        this.clientHolder = null;
        this.resolver = resolver;
        this.executor = executor;
    }

    // ---- Instrument resolution shortcuts ----

    /** Resolve an InstrumentKey to a DhanInstrumentDefinition. */
    protected DhanInstrumentDefinition resolveDef(InstrumentKey key) {
        return resolver.requireDhanDefinition(key);
    }

    /** Resolve a symbol + exchange segment pair to a DhanInstrumentDefinition. */
    protected DhanInstrumentDefinition resolveDef(String symbol, ExchangeSegment segment) {
        return resolver.requireDhanDefinition(symbol, segment);
    }

    /** Resolve an arbitrary Dhan SDK payload to a DhanInstrumentDefinition. */
    protected DhanInstrumentDefinition resolvePayload(Object payload) {
        return resolver.resolveDhanPayload(payload);
    }

    /** Resolve a typed Dhan SDK response to a DhanInstrumentDefinition. */
    protected DhanInstrumentDefinition resolvePayload(DhanSdkResponse<?> response) {
        return resolver.resolveDhanPayload(response);
    }

    // ---- Resilience execution shortcuts ----

    /** Execute a supplier with rate limiting, retries, and circuit breaking. */
    protected <T> T execute(ApiCategory category, String operation, Supplier<T> supplier) {
        return executor.execute(category, operation, supplier);
    }

    /** Execute a runnable with rate limiting, retries, and circuit breaking. */
    protected void run(ApiCategory category, String operation, Runnable action) {
        executor.run(category, operation, action);
    }

    // ---- Client access ----

    /** Access the underlying Dhan SDK client. */
    protected DhanClientHolder clientHolder() {
        if (clientHolder == null) {
            throw new UnsupportedOperationException(
                    getClass().getSimpleName() + " does not use DhanClientHolder");
        }
        return clientHolder;
    }

    /** Returns {@code true} if this adapter was constructed with a Dhan SDK client. */
    protected boolean hasClientHolder() {
        return clientHolder != null;
    }
}
