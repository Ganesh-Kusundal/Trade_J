package com.tradej.app.config;

import com.tradej.broker.api.IBrokerConnection;
import com.tradej.composition.FullComposition;
import com.tradej.simulation.SandboxAutoReconciliationScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.HashMap;
import java.util.Map;

/**
 * Spring wiring for the {@link SandboxAutoReconciliationScheduler} (P4.3 follow-up).
 *
 * <p>Provides the scheduler bean + the periodic-runner inner class. The
 * periodic runner uses the same pattern as {@code AdminConfiguration.ScheduledTasks}:
 * an inner static {@code @Configuration} class so the {@code @Scheduled}
 * annotation is honored by Spring.
 *
 * <p>Behavior:
 * <ol>
 *   <li>Compares the canonical {@link com.tradej.core.domain.service.PositionService}
 *       state against the broker's reported positions.</li>
 *   <li>On drift, calls {@code PositionService.applyBrokerSnapshot} to correct
 *       the canonical state. This is the sandbox-only "broker wins" policy;
 *       live enablement is gated on a separate certification.</li>
 * </ol>
 */
@Configuration
public class AutoReconciliationConfiguration {

    private static final Logger log = LoggerFactory.getLogger(AutoReconciliationConfiguration.class);

    /**
     * The scheduler bean. Takes a {@link SandboxAutoReconciliationScheduler.BrokerPositionView}
     * backed by the live broker connection (when present) and the canonical
     * {@code PositionService} from the composition.
     */
    @Bean
    public SandboxAutoReconciliationScheduler autoReconciliationScheduler(
            IBrokerConnection brokerConnection,
            FullComposition fullComposition
    ) {
        var positionService = fullComposition.executionComposition().positionService();
        var brokerView = new BrokerConnectionView(brokerConnection);
        return new SandboxAutoReconciliationScheduler(positionService, brokerView);
    }

    /**
     * Read-only view of broker-reported positions, backed by the live
     * {@link IBrokerConnection#portfolio()} call. Returns an empty map if
     * the broker connection is null or throws (e.g., during early init).
     */
    public static final class BrokerConnectionView implements SandboxAutoReconciliationScheduler.BrokerPositionView {
        private final IBrokerConnection brokerConnection;

        public BrokerConnectionView(IBrokerConnection brokerConnection) {
            this.brokerConnection = brokerConnection;
        }

        @Override
        public Map<String, Long> fetchAllPositions() {
            Map<String, Long> result = new HashMap<>();
            if (brokerConnection == null) {
                return result;
            }
            try {
                for (var pos : brokerConnection.portfolio().getPositions()) {
                    String key = pos.exchangeSegment().name() + "::" + pos.symbol();
                    result.merge(key, pos.quantity(), Long::sum);
                }
            } catch (Exception e) {
                log.warn("BrokerConnectionView.fetchAllPositions failed — treating as empty", e);
            }
            return result;
        }
    }

    // ── Periodic runner ──

    /**
     * Inner static class so {@code @Scheduled} annotations on its methods
     * are honored by Spring. Mirrors {@code AdminConfiguration.ScheduledTasks}.
     */
    @Configuration
    static class ScheduledAutoReconciliation {

        private final SandboxAutoReconciliationScheduler scheduler;

        ScheduledAutoReconciliation(
                SandboxAutoReconciliationScheduler scheduler
        ) {
            this.scheduler = scheduler;
        }

        /**
         * Runs periodic sandbox reconciliation. Default: every 60 seconds
         * after a 30-second initial delay. The interval is configurable via
         * {@code trade.auto-reconciliation.interval-seconds}.
         *
         * <p>applyCorrections=true is the sandbox-only "broker wins" policy.
         * The live-enablement gate (drift → bracket order / exit) is a
         * separate certification; this method is NOT the entry point for
         * that flow.
         */
        @Scheduled(
                initialDelayString = "${trade.auto-reconciliation.initial-delay-seconds:30}000",
                fixedRateString = "${trade.auto-reconciliation.interval-seconds:60}000"
        )
        public void run() {
            try {
                int drift = scheduler.reconcileOnce(true);
                if (drift > 0) {
                    log.info("Sandbox auto-reconciliation: {} drift(s) corrected", drift);
                }
            } catch (Exception e) {
                log.error("Auto-reconciliation tick failed", e);
            }
        }
    }
}
