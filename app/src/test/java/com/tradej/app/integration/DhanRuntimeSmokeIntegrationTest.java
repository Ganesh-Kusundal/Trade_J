package com.tradej.app.integration;

import com.tradej.app.TradingApplication;
import com.tradej.app.admin.RuntimeHealthState;
import com.tradej.broker.dhan.config.DhanAuthMode;
import com.tradej.broker.dhan.config.DhanConnectionSettings;
import com.tradej.core.domain.event.CandleDeveloping;
import com.tradej.core.domain.port.EventBus;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.nio.file.Files;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("integration")
@Tag("runtime-e2e")
class DhanRuntimeSmokeIntegrationTest {
    @Test
    void startsSpringRuntimeWithLiveBrokerPreflight() throws Exception {
        DhanConnectionSettings settings = LiveDhanTestSupport.liveConnectionSettingsOrSkip();
        String accessToken = LiveDhanTestSupport.resolveLiveAccessToken(settings);
        // Use SBIN/NSE_EQ for preflight — Dhan's quote/candle API does not return data for NIFTY/IDX_I indexes.
        String symbol = "SBIN";
        String segment = "NSE_EQ";

        java.nio.file.Path storageRoot = Files.createTempDirectory("trade-runtime-smoke");
        java.nio.file.Path chroniclePath = storageRoot.resolve("chronicle");
        java.nio.file.Path duckdbPath = storageRoot.resolve("runtime.duckdb");
        java.nio.file.Path cachePath = storageRoot.resolve("instruments");

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
                "--trade.subscriptions[0].feed-mode=QUOTE"
        )) {
            RuntimeHealthState runtimeHealthState = context.getBean(RuntimeHealthState.class);
            EventBus eventBus = context.getBean(EventBus.class);
            CountDownLatch candleDeveloping = new CountDownLatch(1);
            eventBus.subscribe(CandleDeveloping.class, event -> candleDeveloping.countDown());
            assertTrue(runtimeHealthState.catalogLoaded(), "Runtime should load the instrument catalog before startup completes.");
            assertTrue(runtimeHealthState.brokerPreflightPassed(), "Runtime should pass the broker preflight before reporting healthy startup.");
            assertTrue(runtimeHealthState.startupCompleted(), "Runtime should report completed startup once subscriptions are active.");
            if (isIndianMarketSessionOpen()) {
                assertTrue(candleDeveloping.await(30, TimeUnit.SECONDS),
                        "Runtime should transform a live tick into a candle event through the Disruptor pipeline.");
            }
        }
    }

    private boolean isIndianMarketSessionOpen() {
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Asia/Kolkata"));
        if (now.getDayOfWeek() == DayOfWeek.SATURDAY || now.getDayOfWeek() == DayOfWeek.SUNDAY) {
            return false;
        }
        LocalTime time = now.toLocalTime();
        return !time.isBefore(LocalTime.of(9, 15)) && !time.isAfter(LocalTime.of(15, 30));
    }
}
