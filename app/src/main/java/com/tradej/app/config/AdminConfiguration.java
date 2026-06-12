package com.tradej.app.config;

import com.tradej.broker.api.IBrokerConnection;
import com.tradej.core.domain.event.EventMetadataFactory;
import com.tradej.core.domain.port.EventBus;
import com.tradej.core.domain.runtime.RuntimeMode;
import com.tradej.core.domain.runtime.RuntimeModeHolder;
import com.tradej.core.domain.time.TradingClock;
import com.tradej.execution.reconcile.OrderReconciler;
import com.tradej.execution.reconcile.ReconciliationAlertLogger;
import com.tradej.execution.reconcile.ReconciliationScheduler;
import com.tradej.execution.risk.DailyRiskResetScheduler;
import com.tradej.persistence.oms.EventSourcedOrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * Unified admin configuration consolidating scheduling, reconciliation,
 * daily risk reset, and replay isolation safety.
 */
@Configuration
@EnableScheduling
public class AdminConfiguration {

    private static final Logger log = LoggerFactory.getLogger(AdminConfiguration.class);

    // ── Scheduler thread pools ──

    @Bean
    ThreadPoolTaskScheduler reconciliationTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("reconciliation-");
        scheduler.setDaemon(true);
        return scheduler;
    }

    @Bean
    ThreadPoolTaskScheduler dailyRiskResetTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("daily-risk-reset-");
        scheduler.setDaemon(true);
        return scheduler;
    }

    // ── Reconciliation service beans ──

    @Bean
    OrderReconciler orderReconciler(
            EventSourcedOrderRepository omsRepo,
            IBrokerConnection brokerConnection,
            EventMetadataFactory metadataFactory
    ) {
        return new OrderReconciler(omsRepo, brokerConnection, metadataFactory);
    }

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

    @Bean
    ReconciliationScheduler reconciliationScheduler(
            OrderReconciler orderReconciler,
            EventBus eventBus,
            com.tradej.composition.FullComposition fullComposition
    ) {
        return new ReconciliationScheduler(orderReconciler, eventBus, fullComposition.executionComposition().netPositionProvider());
    }

    @Bean
    DailyRiskResetScheduler dailyRiskResetScheduler(com.tradej.composition.FullComposition fullComposition) {
        return new DailyRiskResetScheduler(fullComposition.executionComposition().positionRiskHandler());
    }

    // ── Scheduled triggers ──

    @Configuration
    static class ScheduledTasks {

        private static final Logger log = LoggerFactory.getLogger(ScheduledTasks.class);

        private final ReconciliationScheduler reconciliationScheduler;
        private final DailyRiskResetScheduler dailyRiskResetScheduler;

        public ScheduledTasks(
                @Lazy ReconciliationScheduler reconciliationScheduler,
                @Lazy DailyRiskResetScheduler dailyRiskResetScheduler
        ) {
            this.reconciliationScheduler = reconciliationScheduler;
            this.dailyRiskResetScheduler = dailyRiskResetScheduler;
        }

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

    // ── Replay isolation boundary ──

    /**
     * Capital safety guard that dynamically intercepts Spring bean initialization.
     * Throws a fatal exception if any live broker connection bean (implementing IBrokerConnection)
     * or live broker adapter resides in the replay context, preventing live order leaks.
     * Only active when runtime mode is REPLAY or BACKTEST.
     */
    @Bean
    ReplayIsolationBoundary replayIsolationBoundary(RuntimeModeHolder modeHolder) {
        return new ReplayIsolationBoundary(modeHolder);
    }

    static final class ReplayIsolationBoundary implements BeanPostProcessor {

        private final RuntimeModeHolder modeHolder;

        ReplayIsolationBoundary(RuntimeModeHolder modeHolder) {
            this.modeHolder = modeHolder;
        }

        @Override
        public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
            RuntimeMode mode = modeHolder.mode();
            if (mode != RuntimeMode.REPLAY && mode != RuntimeMode.BACKTEST) {
                return bean;
            }

            if (bean instanceof IBrokerConnection) {
                throw new IllegalStateException(
                    "FATAL CAPITAL SAFETY VIOLATION: Live broker connection '" + beanName +
                    "' (" + bean.getClass().getName() + ") detected in " + mode + " context! "
                    + "Replays are strictly forbidden from loading live connections."
                );
            }

            String className = bean.getClass().getName();
            if (className.startsWith("com.tradej.broker.dhan") || className.startsWith("com.tradej.broker.upstox")) {
                throw new IllegalStateException(
                    "FATAL CAPITAL SAFETY VIOLATION: Live broker adapter component '" + className +
                    "' detected in " + mode + " context! This is strictly forbidden."
                );
            }

            return bean;
        }
    }
}
