package com.tradej.app.integration;

import com.tradej.broker.dhan.DhanBrokerConnection;
import com.tradej.core.domain.model.Instrument;
import com.tradej.core.domain.model.OptionChainSnapshot;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.OptionType;
import com.tradej.core.domain.value.StrikeSelectionKind;
import com.tradej.execution.service.CaffeineIdempotencyCache;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("integration")
@Tag("broker-rest")
class DhanStrikeSelectionIntegrationTest {
    private static final String UNDERLYING = "NIFTY";

    private DhanBrokerConnection brokerConnection;

    @AfterEach
    void tearDown() {
        if (brokerConnection != null) {
            brokerConnection.disconnect();
        }
    }

    @Test
    void selectsAtmOtmItmStrikesFromInstrumentMaster() throws Exception {
        brokerConnection = new DhanBrokerConnection(
                LiveDhanTestSupport.connectionSettingsOrSkip(),
                com.tradej.broker.dhan.constants.DhanProtocolConstants.defaultRateLimiter(),
                new CaffeineIdempotencyCache()
        );
        brokerConnection.loadDailyInstrumentCatalog(Files.createTempDirectory("dhan-strike-cache"), false);

        List<LocalDate> expiries = brokerConnection.options().getExpiries(UNDERLYING, ExchangeSegment.IDX_I);
        assertFalse(expiries.isEmpty());
        LocalDate expiry = expiries.getFirst();

        OptionChainSnapshot chain = brokerConnection.options().getOptionChain(UNDERLYING, ExchangeSegment.IDX_I, expiry);
        long spot = chain.spotPricePaisa();
        assertTrue(spot > 0L);

        Set<Long> listedStrikes = brokerConnection.options().getOptionContracts(UNDERLYING, ExchangeSegment.IDX_I, expiry).stream()
                .map(Instrument::strikePricePaisa)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        assertFalse(listedStrikes.isEmpty());

        long atm = brokerConnection.options().selectStrikePaisa(
                UNDERLYING, ExchangeSegment.IDX_I, spot, OptionType.CALL, StrikeSelectionKind.ATM, 0);
        long otmCall = brokerConnection.options().selectStrikePaisa(
                UNDERLYING, ExchangeSegment.IDX_I, spot, OptionType.CALL, StrikeSelectionKind.OTM, 1);
        long itmCall = brokerConnection.options().selectStrikePaisa(
                UNDERLYING, ExchangeSegment.IDX_I, spot, OptionType.CALL, StrikeSelectionKind.ITM, 1);

        assertTrue(listedStrikes.contains(atm), "ATM strike should exist in the instrument master.");
        assertTrue(listedStrikes.contains(otmCall), "OTM call strike should exist in the instrument master.");
        assertTrue(listedStrikes.contains(itmCall), "ITM call strike should exist in the instrument master.");
        assertTrue(otmCall > atm, "OTM call should be above ATM.");
        assertTrue(itmCall < atm, "ITM call should be below ATM.");
    }
}
