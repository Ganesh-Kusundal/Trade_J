package com.tradej.pipeline.state;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe in-memory state store for node-local computed state.
 * <p>
 * Suitable for lightweight state that can be deterministically recomputed from
 * the event stream (MACD values, SMA values, ATR, etc.).
 * Not persisted across restarts.
 */
public final class InMemoryStateStore<K, V> implements StateStore<K, V> {

    private final ConcurrentHashMap<K, V> store = new ConcurrentHashMap<>();

    @Override
    public Optional<V> get(K key) {
        return Optional.ofNullable(store.get(key));
    }

    @Override
    public void put(K key, V value) {
        store.put(key, value);
    }

    @Override
    public void delete(K key) {
        store.remove(key);
    }

    @Override
    public Optional<V> getLatest(K key) {
        return get(key);
    }

    @Override
    public boolean contains(K key) {
        return store.containsKey(key);
    }

    @Override
    public void clear() {
        store.clear();
    }

    /** Number of entries in the store. */
    public int size() {
        return store.size();
    }
}
