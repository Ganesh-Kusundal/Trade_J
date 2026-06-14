package com.tradej.simulation;

import com.tradej.core.domain.service.PositionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;

/**
 * P4.3: Auto-reconciliation scheduler (sandbox only).
 *
 * <p>Periodically compares the canonical {@link PositionService} state against
 * a broker view of positions. On drift, logs the mismatch and (optionally)
 * applies the broker's view via {@link PositionService#applyBrokerSnapshot}.
 *
 * <p><b>Sandbox only — no live enablement.</b> Per the architecture decision
 * captured in MEMORY.md, auto-reconciliation runs in the
 * {@code trading/simulation} sandbox before any live trading. Live enablement
 * is gated on a separate certification.
 *
 * <p><b>Drift detection</b> compares per-symbol:
 * <ul>
 *   <li>Canonical net position (from {@link PositionService#getNetPosition})</li>
 *   <li>Broker-reported net position (from the injected {@link BrokerPositionView})</li>
 * </ul>
 * A mismatch larger than {@link #tolerance} (default 0) is a drift.
 *
 * <p>Full scheduling (cron / Spring {@code @Scheduled}) is left to the caller;
 * this class is the detection + reconciliation logic. The full P4.3 commit
 * wires it into the Spring config.
 */
public final class SandboxAutoReconciliationScheduler {

    private static final Logger log = LoggerFactory.getLogger(SandboxAutoReconciliationScheduler.class);

    private final PositionService positionService;
    private final BrokerPositionView brokerView;
    private final long tolerance;

    public SandboxAutoReconciliationScheduler(
            PositionService positionService,
            BrokerPositionView brokerView
    ) {
        this(positionService, brokerView, 0L);
    }

    public SandboxAutoReconciliationScheduler(
            PositionService positionService,
            BrokerPositionView brokerView,
            long tolerance
    ) {
        this.positionService = Objects.requireNonNull(positionService, "positionService");
        this.brokerView = Objects.requireNonNull(brokerView, "brokerView");
        this.tolerance = tolerance;
    }

    /**
     * Run one reconciliation pass. Returns the number of drift symbols detected.
     * If {@code applyCorrections} is true, the broker's view overwrites the
     * canonical state for each drifted symbol (sandbox-only policy).
     */
    public int reconcileOnce(boolean applyCorrections) {
        Map<String, Long> canonical = positionService.getNetPositions();
        Map<String, Long> broker = brokerView.fetchAllPositions();

        int driftCount = 0;
        // Union of symbol keys
        for (String symbol : unionKeys(canonical, broker)) {
            long canonicalQty = canonical.getOrDefault(symbol, 0L);
            long brokerQty = broker.getOrDefault(symbol, 0L);
            if (Math.abs(canonicalQty - brokerQty) > tolerance) {
                driftCount++;
                if (applyCorrections) {
                    log.warn(
                            "Drift detected symbol={} canonical={} broker={} tolerance={} — applying broker view (sandbox)",
                            symbol, canonicalQty, brokerQty, tolerance);
                    positionService.applyBrokerSnapshot(symbol, brokerQty, 0L);
                } else {
                    log.warn(
                            "Drift detected symbol={} canonical={} broker={} tolerance={} — NOT applying (dry-run)",
                            symbol, canonicalQty, brokerQty, tolerance);
                }
            }
        }
        if (driftCount == 0) {
            log.debug("No drift detected across {} symbols", canonical.size());
        } else {
            log.info("Reconciliation pass complete: {} drift(s) detected{}", driftCount,
                    applyCorrections ? " (corrections applied)" : " (dry-run)");
        }
        return driftCount;
    }

    private static Iterable<String> unionKeys(Map<String, Long> a, Map<String, Long> b) {
        java.util.Set<String> keys = new java.util.HashSet<>(a.keySet());
        keys.addAll(b.keySet());
        return keys;
    }

    /**
     * Read-only view of broker-reported positions. The full P4.3 commit will
     * back this with the {@code PaperBrokerConnection} or the broker's
     * {@code ReconciliationProvider}. For unit tests, any
     * {@code Map<String, Long>}-backed implementation works.
     */
    public interface BrokerPositionView {
        Map<String, Long> fetchAllPositions();
    }
}
