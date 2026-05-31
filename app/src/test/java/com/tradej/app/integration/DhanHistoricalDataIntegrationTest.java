package com.tradej.app.integration;

import com.tradej.broker.dhan.DhanBrokerConnection;
import com.tradej.core.domain.model.Balance;
import com.tradej.core.domain.model.Candle;
import com.tradej.core.domain.model.CandleHistoryRequest;
import com.tradej.core.domain.model.InstrumentKey;
import com.tradej.core.domain.value.Exchange;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.execution.service.CaffeineIdempotencyCache;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("integration")
@Tag("broker-rest")
class DhanHistoricalDataIntegrationTest {
    private static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");

    private DhanBrokerConnection brokerConnection;

    @AfterEach
    void tearDown() {
        if (brokerConnection != null) {
            brokerConnection.disconnect();
        }
    }

    @Test
    void connectsToBrokerAndMapsStandardBalanceDomainObject() throws Exception {
        InstrumentTarget target = connectAndLoadCatalog();

        Balance balance = brokerConnection.portfolio().getBalance();

        assertEquals(LiveDhanTestSupport.value("DHAN_CLIENT_ID", "dhan.clientId"), balance.clientId(),
                "Balance should be mapped to the configured authenticated client.");
        assertTrue(balance.cashPaisa() >= 0L, "Cash balance must be non-negative.");
        assertTrue(balance.withdrawablePaisa() >= 0L, "Withdrawable balance must be non-negative.");
    }

    @Test
    void fetchesHistoricalDailyAndIntradayCandlesThroughBrokerBoundary() throws Exception {
        InstrumentTarget target = connectAndLoadCatalog();
        LocalDate tradingDate = latestTradingDate();
        InstrumentKey instrumentKey = new InstrumentKey(target.symbol(), target.exchangeSegment());
        LocalDate intradayDate = recentIntradayDate(instrumentKey, tradingDate);

        List<Candle> daily = brokerConnection.marketData().getCandles(new CandleHistoryRequest(
                instrumentKey,
                "1d",
                tradingDate.minusDays(7),
                tradingDate
        ));
        List<Candle> intraday = brokerConnection.marketData().getCandles(new CandleHistoryRequest(
                instrumentKey,
                "5m",
                intradayDate,
                intradayDate
        ));

        assertFalse(daily.isEmpty(), "Expected at least one daily candle.");
        assertFalse(intraday.isEmpty(), "Expected at least one intraday candle.");
        assertEquals("1d", daily.get(0).interval(), "Daily candles should be mapped to the standard 1d interval.");
        assertEquals("5m", intraday.get(0).interval(), "Intraday candles should be mapped to the standard 5m interval.");
        assertTrue(daily.get(daily.size() - 1).startTimeMs() >= daily.get(0).startTimeMs(), "Daily candles should be chronological.");
        assertTrue(intraday.get(intraday.size() - 1).startTimeMs() >= intraday.get(0).startTimeMs(), "Intraday candles should be chronological.");
    }

    private InstrumentTarget connectAndLoadCatalog() throws Exception {
        InstrumentTarget target = resolveTarget();
        brokerConnection = DhanBrokerConnection.create(
                LiveDhanTestSupport.connectionSettingsOrSkip(),
                new CaffeineIdempotencyCache()
        );
        brokerConnection.loadInstrumentCatalog(writeCatalog(target));
        return target;
    }

    private InstrumentTarget resolveTarget() {
        String symbol = LiveDhanTestSupport.value("DHAN_HISTORICAL_SYMBOL", "dhan.historicalSymbol");
        String securityId = LiveDhanTestSupport.value("DHAN_HISTORICAL_SECURITY_ID", "dhan.historicalSecurityId");
        String segment = LiveDhanTestSupport.value("DHAN_HISTORICAL_SEGMENT", "dhan.historicalSegment");
        String instrument = LiveDhanTestSupport.value("DHAN_HISTORICAL_INSTRUMENT", "dhan.historicalInstrument");
        ExchangeSegment exchangeSegment = isPresent(segment) ? ExchangeSegment.valueOf(segment.toUpperCase()) : ExchangeSegment.IDX_I;
        return new InstrumentTarget(
                isPresent(symbol) ? symbol : "NIFTY",
                isPresent(securityId) ? securityId : "13",
                exchangeSegment,
                exchangeSegment.exchange(),
                isPresent(instrument) ? instrument : "INDEX"
        );
    }

    private LocalDate latestTradingDate() {
        LocalDate date = LocalDate.now(INDIA);
        if (date.getDayOfWeek() == DayOfWeek.SATURDAY) {
            return date.minusDays(1);
        }
        if (date.getDayOfWeek() == DayOfWeek.SUNDAY) {
            return date.minusDays(2);
        }
        return date;
    }

    private boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }

    private LocalDate recentIntradayDate(InstrumentKey instrumentKey, LocalDate latestTradingDate) {
        for (int offset = 0; offset < 7; offset++) {
            LocalDate candidate = latestTradingDate.minusDays(offset);
            if (candidate.getDayOfWeek() == DayOfWeek.SATURDAY || candidate.getDayOfWeek() == DayOfWeek.SUNDAY) {
                continue;
            }
            List<Candle> candles = brokerConnection.marketData().getCandles(new CandleHistoryRequest(
                    instrumentKey,
                    "5m",
                    candidate,
                    candidate
            ));
            if (!candles.isEmpty()) {
                return candidate;
            }
        }
        throw new IllegalStateException("Unable to find a recent trading day with intraday candles");
    }

    private Path writeCatalog(InstrumentTarget target) throws Exception {
        Path file = Files.createTempFile("dhan-history-catalog", ".csv");
        Files.writeString(file, """
                symbol,canonicalSymbol,exchange,exchangeSegment,securityId,instrumentType
                %s,%s,%s,%s,%s,%s
                """.formatted(
                target.symbol(),
                target.symbol(),
                target.exchange().name(),
                target.exchangeSegment().name(),
                target.securityId(),
                target.instrumentType()
        ));
        return file;
    }

    private record InstrumentTarget(
            String symbol,
            String securityId,
            ExchangeSegment exchangeSegment,
            Exchange exchange,
            String instrumentType
    ) {
    }
}
