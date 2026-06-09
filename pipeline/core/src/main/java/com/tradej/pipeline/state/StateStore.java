package com.tradej.pipeline.state;

import java.util.Optional;

/**
 * Abstraction for pipeline node state persistence and retrieval.
 * <p>
 * Different implementations support different state ownership models:
 * <ul>
 *   <li>{@link InMemoryStateStore} — node-local computed state (MACD, SMA, ATR)</li>
 *   <li>Snapshot-based — wraps DuckDB for periodic checkpoint recovery</li>
 *   <li>Event-sourced — wraps ChronicleQueue for full event sourcing</li>
 * </ul>
 */
public interface StateStore<K, V> {

    /**
     * Retrieve state for the given key. Returns empty if no state exists.
     */
    Optional<V> get(K key);

    /**
     * Persist state for the given key.
     */
    void put(K key, V value);

    /**
     * Remove state for the given key.
     */
    void delete(K key);

    /**
     * Returns the most recently written snapshot for the given key,
     * or empty if no snapshots exist.
     */
    Optional<V> getLatest(K key);

    /**
     * Returns true if this store contains state for the given key.
     */
    boolean contains(K key);

    /**
     * Remove all state entries.
     */
    void clear();
}
