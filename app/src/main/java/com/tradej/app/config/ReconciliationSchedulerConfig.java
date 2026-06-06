package com.tradej.app.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
public class ReconciliationSchedulerConfig {

    @Bean("reconciliationScheduler")
    ThreadPoolTaskScheduler reconciliationTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("reconciliation-");
        scheduler.setDaemon(true);
        return scheduler;
    }

    @Bean("dailyRiskResetScheduler")
    ThreadPoolTaskScheduler dailyRiskResetTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("daily-risk-reset-");
        scheduler.setDaemon(true);
        return scheduler;
    }
}
