package com.tradej.broker.dhan.reactive.instrument;

import com.tradej.core.domain.model.InstrumentKey;

/**
 * Instrument resolver interface for reactive Dhan client.
 */
public interface DhanInstrumentResolver {
    
    /**
     * Resolve an instrument key to its Dhan definition.
     */
    DhanInstrumentDefinition resolve(InstrumentKey key);
}
