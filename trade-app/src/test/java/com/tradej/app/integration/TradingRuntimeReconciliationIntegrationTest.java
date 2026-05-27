package com.tradej.app.integration;

import com.tradej.app.TradingApplication;
import com.tradej.broker.dhan.config.DhanAuthMode;
import com.tradej.broker.dhan.config.DhanConnectionSettings;
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
        DhanConnectionSettings settings = LiveDhanTestSupport.liveConnectionSettingsOrSkip();
        String accessToken = LiveDhanTestSupport.resolveLiveAccessToken(settings);
        String symbol = LiveDhanTestSupport.isPresent(LiveDhanTestSupport.value("DHAN_RUNTIME_SYMBOL", "dhan.runtimeSymbol"))
                ? LiveDhanTestSupport.value("DHAN_RUNTIME_SYMBOL", "dhan.runtimeSymbol")
                : "NIFTY";
        String segment = LiveDhanTestSupport.isPresent(LiveDhanTestSupport.value("DHAN_RUNTIME_SEGMENT", "dhan.runtimeSegment"))
                ? LiveDhanTestSupport.value("DHAN_RUNTIME_SEGMENT", "dhan.runtimeSegment")
                : "IDX_I";

        var storageRoot = Files.createTempDirectory("trade-runtime-reconcile");
        var chroniclePath = storageRoot.resolve("chronicle");
        var duckdbPath = storageRoot.resolve("runtime.duckdb");
        var cachePath = storageRoot.resolve("instruments");

        SpringApplication app = new SpringApplication(TradingApplication.class);
        try (ConfigurableApplicationContext context = app.run(
                "--server.port=0",
                "--spring.profiles.active=dev-live",
                "--trade.broker.client-id=" + settings.clientId(),
                "--trade.broker.access-token=" + accessToken,
                "--trade.broker.auth-mode=" + DhanAuthMode.STATIC.name(),
                "--trade.storage.chronicle-path=" + chroniclePath,
                "--trade.storage.duckdb-path=" + duckdbPath,
                "--trade.instruments.cache-directory=" + cachePath,
                "--trade.instruments.auto-download=true",
                "--trade.subscriptions[0].symbol=" + symbol,
                "--trade.subscriptions[0].exchange-segment=" + segment,
                "--trade.subscriptions[0].feed-mode=QUOTE",
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
