package com.tradej.execution.identity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe registry that maps broker-level IDs to internal {@code ORD-*}
 * order IDs, enabling {@code handleOmsFilled} to resolve the internal aggregate
 * key from the broker order ID carried by fill events originating from WS
 * callbacks.
 *
 * <p>Four lookup paths are maintained:</p>
 * <ul>
 *   <li>{@code brokerOrderId → internalOrderId}</li>
 *   <li>{@code signalId → internalOrderId}</li>
 *   <li>{@code internalOrderId → brokerOrderId}</li>
 *   <li>{@code internalOrderId → signalId} (reverse map for O(1) removal)</li>
 * </ul>
 */
public final class OrderIdentityRegistry {

    private static final Logger log = LoggerFactory.getLogger(OrderIdentityRegistry.class);

    private final ConcurrentHashMap<String, String> brokerToInternal = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> internalToBroker = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> signalToInternal = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> internalToSignal = new ConcurrentHashMap<>();

    /**
     * Register a new identity mapping. Safe to call before the broker ack arrives
     * (pass {@code null} for {@code brokerOrderId}) — the mapping will be
     * completed when {@link #acknowledge(String, String)} is called.
     *
     * @param internalOrderId the internal {@code ORD-*} aggregate key
     * @param brokerOrderId   the exchange/broker order ID, or {@code null}
     * @param signalId        the signal/correlation ID that triggered this order
     */
    public void register(String internalOrderId, String brokerOrderId, String signalId) {
        if (signalId != null && !signalId.isBlank()) {
            String prev = signalToInternal.putIfAbsent(signalId, internalOrderId);
            if (prev != null && !prev.equals(internalOrderId)) {
                log.warn("Signal ID {} already mapped to internal order {} — ignoring register for {}",
                        signalId, prev, internalOrderId);
            } else {
                internalToSignal.put(internalOrderId, signalId);
            }
        }
        if (brokerOrderId != null && !brokerOrderId.isBlank()) {
            String prev = brokerToInternal.putIfAbsent(brokerOrderId, internalOrderId);
            if (prev != null && !prev.equals(internalOrderId)) {
                log.warn("Broker order ID {} already mapped to internal order {} — ignoring register for {}",
                        brokerOrderId, prev, internalOrderId);
            } else {
                internalToBroker.put(internalOrderId, brokerOrderId);
            }
        }
    }

    /**
     * Complete a previously registered mapping with the broker-assigned order ID.
     *
     * @param internalOrderId the internal {@code ORD-*} aggregate key
     * @param brokerOrderId   the exchange/broker order ID
     */
    public void acknowledge(String internalOrderId, String brokerOrderId) {
        if (brokerOrderId != null && !brokerOrderId.isBlank()) {
            String existing = brokerToInternal.putIfAbsent(brokerOrderId, internalOrderId);
            if (existing != null && !existing.equals(internalOrderId)) {
                log.warn("Broker order ID {} already mapped to internal order {} — "
                                + "cannot acknowledge for {}",
                        brokerOrderId, existing, internalOrderId);
                return;
            }
            String prev = internalToBroker.put(internalOrderId, brokerOrderId);
            if (prev != null) {
                log.debug("Order {} already acknowledged with broker ID {} — replaced with {}",
                        internalOrderId, prev, brokerOrderId);
            }
        }
    }

    /**
     * Resolve the internal order ID from a broker-assigned order ID.
     *
     * @return the internal {@code ORD-*} ID, or {@code null} if not found
     */
    public String resolveInternalId(String brokerOrderId) {
        return brokerToInternal.get(brokerOrderId);
    }

    /**
     * Resolve the internal order ID from a signal/correlation ID.
     *
     * @return the internal {@code ORD-*} ID, or {@code null} if not found
     */
    public String resolveBySignalId(String signalId) {
        return signalToInternal.get(signalId);
    }

    /**
     * Resolve the broker-assigned order ID from an internal order ID.
     *
     * @return the broker order ID, or {@code null} if not yet acknowledged
     */
    public String resolveBrokerOrderId(String internalOrderId) {
        return internalToBroker.get(internalOrderId);
    }

    /** Remove all mappings for the given internal order ID. O(1) via reverse map. */
    public void remove(String internalOrderId) {
        String brokerId = internalToBroker.remove(internalOrderId);
        if (brokerId != null) {
            brokerToInternal.remove(brokerId);
        }
        String signalId = internalToSignal.remove(internalOrderId);
        if (signalId != null) {
            signalToInternal.remove(signalId);
        }
    }

    /** Current number of tracked internal order IDs. */
    public int size() {
        return internalToBroker.size();
    }

    /** Remove all entries. */
    public void clear() {
        brokerToInternal.clear();
        internalToBroker.clear();
        signalToInternal.clear();
        internalToSignal.clear();
    }
}
