package com.tradej.app.integration;

import com.tradej.broker.dhan.DhanBrokerConnection;
import com.tradej.core.domain.model.CandleHistoryRequest;
import com.tradej.core.domain.model.InstrumentKey;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.execution.service.CaffeineIdempotencyCache;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;

@Tag("integration")
@Tag("broker-rest")
class HistoricalSpeedBenchmarkTest {

    private static DhanBrokerConnection dhan;
    private static final LocalDate TODAY = LocalDate.now(ZoneId.of("Asia/Kolkata"));
    private static final LocalDate LAST_WEEK = TODAY.minusDays(7);
    private static final LocalDate LAST_MONTH = TODAY.minusDays(30);
    private static final LocalDate LAST_3_MONTHS = TODAY.minusDays(90);
    private static final LocalDate LAST_YEAR = TODAY.minusDays(365);

    @BeforeAll
    static void setUp() throws Exception {
        dhan = new DhanBrokerConnection(
                LiveDhanTestSupport.connectionSettingsOrSkip(),
                com.tradej.broker.dhan.constants.DhanProtocolConstants.defaultRateLimiter(),
                new CaffeineIdempotencyCache()
        );
        dhan.loadDailyInstrumentCatalog(Files.createTempDirectory("dhan-bench"), false);
    }

    @AfterAll
    static void tearDown() {
        if (dhan != null) dhan.disconnect();
    }

    @Test
    void benchmarkDhanIntervals() {
        InstrumentKey sbin = new InstrumentKey("SBIN", ExchangeSegment.NSE_EQ);
        InstrumentKey nifty = new InstrumentKey("NIFTY", ExchangeSegment.IDX_I);

        // Daily - 1 year
        measure("Dhan 1d 1yr", () ->
                dhan.marketData().getCandles(new CandleHistoryRequest(sbin, "1d", LAST_YEAR, TODAY)));

        // Daily - 1 month
        measure("Dhan 1d 1mo", () ->
                dhan.marketData().getCandles(new CandleHistoryRequest(sbin, "1d", LAST_MONTH, TODAY)));

        // 1m - 1 week
        measure("Dhan 1m 1wk", () ->
                dhan.marketData().getCandles(new CandleHistoryRequest(sbin, "1m", LAST_WEEK, TODAY)));

        // 1m - 1 day
        measure("Dhan 1m 1day", () ->
                dhan.marketData().getCandles(new CandleHistoryRequest(sbin, "1m", TODAY, TODAY)));

        // 5m - 1 week
        measure("Dhan 5m 1wk", () ->
                dhan.marketData().getCandles(new CandleHistoryRequest(sbin, "5m", LAST_WEEK, TODAY)));

        // 15m - 1 week
        measure("Dhan 15m 1wk", () ->
                dhan.marketData().getCandles(new CandleHistoryRequest(sbin, "15m", LAST_WEEK, TODAY)));

        // 60m - 1 month
        measure("Dhan 60m 1mo", () ->
                dhan.marketData().getCandles(new CandleHistoryRequest(sbin, "60m", LAST_MONTH, TODAY)));

        // Index daily
        measure("Dhan 1d NIFTY 1yr", () ->
                dhan.marketData().getCandles(new CandleHistoryRequest(nifty, "1d", LAST_YEAR, TODAY)));
    }

    private static void measure(String label, CandleFetcher fetcher) {
        try {
            long start = System.currentTimeMillis();
            var candles = fetcher.fetch();
            long elapsed = System.currentTimeMillis() - start;
            System.out.printf("  %-25s %6dms  candles=%d%n", label, elapsed, candles.size());
            assertFalse(candles.isEmpty(), label + " returned no candles");
        } catch (Exception ex) {
            System.out.printf("  %-25s FAILED: %s%n", label, ex.getMessage());
        }
    }

    @FunctionalInterface
    interface CandleFetcher {
        List<com.tradej.core.domain.model.Candle> fetch() throws Exception;
    }
}
