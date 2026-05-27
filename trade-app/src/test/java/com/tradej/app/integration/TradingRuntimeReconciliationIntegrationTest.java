package com.tradej.app.integration;

import com.tradej.app.TradingApplication;
import com.tradej.execution.reconcile.ReconciliationScheduler;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.lang.reflect.Method;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@Tag("integration")
@Tag("runtime-e2e")
class TradingRuntimeReconciliationIntegrationTest {
    @Test
    void reconciliationSchedulerRunsWithoutException() throws Exception {
        LiveDhanTestSupport.connectionSettingsOrSkip();

        var storageRoot = Files.createTempDirectory("trade-runtime-reconcile");
        var chroniclePath = storageRoot.resolve("chronicle");
        var duckdbPath = storageRoot.resolve("runtime.duckdb");
        var cachePath = storageRoot.resolve("instruments");

        SpringApplication app = new SpringApplication(TradingApplication.class);
        try (ConfigurableApplicationContext context = app.run(
                "--server.port=0",
                "--spring.profiles.active=dev-live",
                "--trade.storage.chronicle-path=" + chroniclePath,
                "--trade.storage.duckdb-path=" + duckdbPath,
                "--trade.instruments.cache-directory=" + cachePath,
                "--trade.instruments.auto-download=true",
                "--trade.reconciliation.interval-seconds=3600",
                "--trade.reconciliation.initial-delay-seconds=3600"
        )) {
            ReconciliationScheduler scheduler = context.getBean(ReconciliationScheduler.class);
            assertNotNull(scheduler);
            Method reconcile = ReconciliationScheduler.class.getDeclaredMethod("reconcilePeriodically");
            reconcile.setAccessible(true);
            assertDoesNotThrow(() -> reconcile.invoke(scheduler));
        }
    }
}
