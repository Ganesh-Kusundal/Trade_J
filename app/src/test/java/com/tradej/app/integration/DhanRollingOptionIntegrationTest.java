package com.tradej.app.integration;

import com.tradej.broker.dhan.DhanBrokerConnection;
import com.tradej.core.domain.instrument.RollingExpiryKind;
import com.tradej.core.domain.instrument.RollingExpiryRoll;
import com.tradej.core.domain.instrument.RollingOptionSeriesKey;
import com.tradej.core.domain.instrument.StrikeOffset;
import com.tradej.core.domain.model.RollingOptionHistoryRequest;
import com.tradej.core.domain.model.RollingOptionSeries;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.OptionType;
import com.tradej.execution.service.CaffeineIdempotencyCache;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("integration")
@Tag("broker-rest")
class DhanRollingOptionIntegrationTest {
    private DhanBrokerConnection brokerConnection;

    @AfterEach
    void tearDown() {
        if (brokerConnection != null) {
            brokerConnection.disconnect();
        }
    }

    @Test
    void fetchesRollingOptionHistory() throws Exception {
        Assumptions.assumeTrue("true".equalsIgnoreCase(
                LiveDhanTestSupport.value("DHAN_ROLLING_OPTION_TEST_ENABLED", "dhan.rollingOptionTestEnabled", "false")));

        brokerConnection = DhanBrokerConnection.create(
                LiveDhanTestSupport.connectionSettingsOrSkip(),
                new CaffeineIdempotencyCache()
        );
        brokerConnection.loadDailyInstrumentCatalog(Files.createTempDirectory("dhan-rolling-cache"), false);

        RollingOptionSeriesKey seriesKey = new RollingOptionSeriesKey(
                "NIFTY",
                ExchangeSegment.IDX_I,
                new RollingExpiryRoll(RollingExpiryKind.MONTH, 1),
                StrikeOffset.atm(),
                OptionType.CALL,
                5
        );
        RollingOptionSeries series = brokerConnection.options().getExpiredOptionHistory(
                new RollingOptionHistoryRequest(
                        seriesKey,
                        LocalDate.of(2025, 1, 1),
                        LocalDate.of(2025, 1, 31)
                ));
        assertFalse(series.bars().isEmpty());
        var first = series.bars().getFirst();
        assertTrue(first.timestampMs() > 0L);
        assertTrue(first.closePaisa() > 0L);
    }
}
