package com.tradej.app.config;

import com.tradej.broker.api.IBrokerConnection;
import com.tradej.core.domain.event.EventMetadataFactory;
import com.tradej.core.domain.port.EventBus;
import com.tradej.core.domain.port.NetPositionProvider;
import com.tradej.execution.reconcile.OrderReconciler;
import com.tradej.execution.reconcile.ReconciliationAlertLogger;
import com.tradej.execution.reconcile.ReconciliationScheduler;
import com.tradej.execution.risk.DailyRiskResetScheduler;
import com.tradej.execution.risk.PositionRiskHandler;
import com.tradej.persistence.oms.EventSourcedOrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Wires Spring-free services into the app's scheduling lifecycle.
 *
 * <p>Provides {@link Bean} definitions for services whose {@code @Service}
 * annotations were removed as part of the framework-independent migration,
 * and exposes {@link Scheduled} triggers for periodic reconciliation
 * and daily risk reset.
 */
@Configuration
public class ReconciliationSchedulerTasks {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationSchedulerTasks.class);

    private final ReconciliationScheduler reconciliationScheduler;
    private final DailyRiskResetScheduler dailyRiskResetScheduler;

    public ReconciliationSchedulerTasks(
            ReconciliationScheduler reconciliationScheduler,
            DailyRiskResetScheduler dailyRiskResetScheduler
    ) {
        this.reconciliationScheduler = reconciliationScheduler;
        this.dailyRiskResetScheduler = dailyRiskResetScheduler;
    }

    // ── Bean definitions for Spring-free services ──

    /** Bean definition for {@link OrderReconciler}. */
    @Bean
    OrderReconciler orderReconciler(
            EventSourcedOrderRepository omsRepo,
            IBrokerConnection brokerConnection,
            EventMetadataFactory metadataFactory
    ) {
        return new OrderReconciler(omsRepo, brokerConnection, metadataFactory);
    }

    /** Bean definition for {@link ReconciliationAlertLogger}. */
    @Bean
    ReconciliationAlertLogger reconciliationAlertLogger(
            EventBus eventBus,
            TradingProperties properties
    ) {
        TradingProperties.ReconciliationProperties reconf = properties.reconciliation();
        return new ReconciliationAlertLogger(
                eventBus,
                reconf.autoHalt(),
                reconf.mismatchToleranceQty());
    }

    /** Bean definition for {@link ReconciliationScheduler}. */
    @Bean
    ReconciliationScheduler reconciliationScheduler(
            OrderReconciler orderReconciler,
            EventBus eventBus,
            NetPositionProvider netPositionProvider
    ) {
        return new ReconciliationScheduler(orderReconciler, eventBus, netPositionProvider);
    }

    /** Bean definition for {@link DailyRiskResetScheduler}. */
    @Bean
    DailyRiskResetScheduler dailyRiskResetScheduler(PositionRiskHandler positionRiskHandler) {
        return new DailyRiskResetScheduler(positionRiskHandler);
    }

    // ── Scheduled triggers ──

    /**
     * Runs periodic reconciliation at the configured interval.
     * Default: every 60 seconds after a 30-second initial delay.
     */
    @Scheduled(initialDelayString = "${trade.reconciliation.initial-delay-seconds:30}000",
               fixedRateString = "${trade.reconciliation.interval-seconds:60}000")
    void runReconciliation() {
        try {
            reconciliationScheduler.reconcilePeriodically();
        } catch (Exception e) {
            log.error("Periodic reconciliation failed", e);
        }
    }

    /**
     * Resets daily loss limits and kill switch at 9:00 AM IST (3:30 AM UTC)
     * every weekday, giving a 15-minute buffer before market open at 9:15 AM IST.
     */
    @Scheduled(cron = "0 30 3 * * MON-FRI", zone = "UTC")
    void runDailyRiskReset() {
        try {
            dailyRiskResetScheduler.resetDailyLimits();
        } catch (Exception e) {
            log.error("Daily risk reset failed", e);
        }
    }
}
