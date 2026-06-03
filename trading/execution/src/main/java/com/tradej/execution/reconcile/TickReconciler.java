package com.tradej.execution.reconcile;

import com.tradej.core.domain.event.MarketTickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Utility to reconcile broker-reported ticks against system-processed {@link MarketTickEvent}s.
 *
 * <p>Compares tick counts, identifies missing ticks, duplicate ticks, and out-of-order ticks.
 */
public final class TickReconciler {

    /**
     * Representation of a tick reported by the broker.
     */
    public record BrokerTick(
            long sequenceId,
            String symbol,
            long ltpPaisa,
            long cumulativeVolume,
            long exchangeTimestampEpochMs
    ) {
        public TickKey toKey() {
            return new TickKey(sequenceId, ltpPaisa, cumulativeVolume, exchangeTimestampEpochMs);
        }
    }

    /**
     * Composite key used to uniquely identify and match ticks when sequence ID is not sufficient.
     */
    private record TickKey(
            long sequenceId,
            long ltpPaisa,
            long cumulativeVolume,
            long exchangeTimestampEpochMs
    ) {
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            TickKey tickKey = (TickKey) o;
            if (sequenceId > 0 && tickKey.sequenceId > 0) {
                return sequenceId == tickKey.sequenceId;
            }
            return ltpPaisa == tickKey.ltpPaisa &&
                    cumulativeVolume == tickKey.cumulativeVolume &&
                    exchangeTimestampEpochMs == tickKey.exchangeTimestampEpochMs;
        }

        @Override
        public int hashCode() {
            if (sequenceId > 0) {
                return Objects.hash(sequenceId);
            }
            return Objects.hash(ltpPaisa, cumulativeVolume, exchangeTimestampEpochMs);
        }
    }

    /**
     * Detailed report of the tick reconciliation.
     */
    public record ReconciliationReport(
            int totalBrokerTicks,
            int totalSystemTicks,
            List<BrokerTick> missingTicks,
            List<MarketTickEvent> duplicateTicks,
            List<MarketTickEvent> outOfOrderTicks,
            boolean isReconciled
    ) {}

    /**
     * Reconciles broker-reported ticks against system-processed ticks for a specific symbol.
     */
    public ReconciliationReport reconcile(
            String symbol,
            List<BrokerTick> brokerTicks,
            List<MarketTickEvent> systemTicks
    ) {
        List<BrokerTick> filteredBroker = brokerTicks.stream()
                .filter(t -> t.symbol().equals(symbol))
                .toList();

        List<MarketTickEvent> filteredSystem = systemTicks.stream()
                .filter(t -> t.symbol().equals(symbol))
                .toList();

        Map<TickKey, List<MarketTickEvent>> systemLookup = new HashMap<>();
        for (MarketTickEvent tick : filteredSystem) {
            TickKey key = new TickKey(
                    tick.sequenceId(),
                    tick.ltpPaisa(),
                    tick.cumulativeVolume(),
                    tick.exchangeTimestampEpochMs()
            );
            systemLookup.computeIfAbsent(key, k -> new ArrayList<>()).add(tick);
        }

        List<BrokerTick> missingTicks = new ArrayList<>();
        List<MarketTickEvent> duplicateTicks = new ArrayList<>();

        // 1. Find missing ticks
        for (BrokerTick brokerTick : filteredBroker) {
            TickKey key = brokerTick.toKey();
            if (!systemLookup.containsKey(key)) {
                missingTicks.add(brokerTick);
            }
        }

        // 2. Find duplicate ticks
        for (List<MarketTickEvent> ticks : systemLookup.values()) {
            if (ticks.size() > 1) {
                // All but the first are duplicates
                duplicateTicks.addAll(ticks.subList(1, ticks.size()));
            }
        }

        // 3. Find out-of-order ticks
        List<MarketTickEvent> outOfOrderTicks = new ArrayList<>();
        long maxSeqOrTime = -1;
        for (MarketTickEvent tick : filteredSystem) {
            long currentVal = tick.sequenceId() > 0 ? tick.sequenceId() : tick.exchangeTimestampEpochMs();
            if (currentVal < maxSeqOrTime) {
                outOfOrderTicks.add(tick);
            } else {
                maxSeqOrTime = currentVal;
            }
        }

        boolean isReconciled = missingTicks.isEmpty() && duplicateTicks.isEmpty() && outOfOrderTicks.isEmpty()
                && filteredBroker.size() == filteredSystem.size();

        return new ReconciliationReport(
                filteredBroker.size(),
                filteredSystem.size(),
                missingTicks,
                duplicateTicks,
                outOfOrderTicks,
                isReconciled
        );
    }
}
