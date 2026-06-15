package com.tradej.core.domain.id;

/**
 * Pluggable ID generation contract for event, signal, order, trade, and fill IDs.
 * <p>
 * In LIVE mode, implementations may use random UUIDs. In REPLAY/BACKTEST modes,
 * implementations MUST produce deterministic, repeatable IDs from a known seed so
 * that two runs with identical inputs produce identical event streams.
 * <p>
 * Replaces scattered {@code UUID.randomUUID()} calls in strategy, simulation, and
 * execution code with a single injectable abstraction.
 */
public interface IdGenerator {

    /** Returns a deterministic or random signal ID. */
    String generateSignalId();

    /** Returns a deterministic or random order ID. */
    String generateOrderId();

    /** Returns a deterministic or random trade ID. */
    String generateTradeId();

    /** Returns a deterministic or random fill ID. */
    String generateFillId();

    /** Returns a deterministic or random event/correlation ID. */
    String generateEventId();
}
