package com.tradej.scanner.option;

import com.tradej.core.domain.model.Instrument;
import com.tradej.core.domain.model.OptionChainEntry;
import com.tradej.core.domain.model.OptionChainSnapshot;
import com.tradej.core.domain.model.OptionGreeks;
import com.tradej.core.domain.model.OptionQuote;
import com.tradej.core.domain.value.Exchange;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.OptionType;
import com.tradej.scanner.model.OptionScanSpec;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@Tag("unit")
class OptionLiquidityScannerTest {

    @Test
    void ranksContractsByLiquidityScore() {
        LocalDate expiry = LocalDate.of(2026, 6, 5);
        Instrument underlying = new Instrument(
                "NIFTY", "NIFTY", Exchange.NSE, ExchangeSegment.IDX_I,
                "INDEX", "NIFTY", null, null, null, 0, 0
        );
        OptionChainSnapshot chain = new OptionChainSnapshot(
                underlying,
                expiry,
                2400000L,
                List.of(
                        entry(2400000L, 1000, 100, OptionType.CALL),
                        entry(2400000L, 50000, 5000, OptionType.CALL),
                        entry(2400000L, 800, 50, OptionType.PUT)
                )
        );
        OptionScanSpec spec = new OptionScanSpec(
                OptionExpiryPolicy.NEAREST,
                null,
                OptionSideFilter.BOTH,
                500,
                0,
                500,
                false,
                2,
                0
        );
        List<OptionContractHit> hits = new OptionLiquidityScanner(new StubOptionsProvider(chain))
                .scan(OptionScanRequest.of(
                        "NIFTY",
                        ExchangeSegment.IDX_I,
                        OptionExpiryPolicy.NEAREST,
                        null,
                        OptionSideFilter.BOTH,
                        500,
                        0,
                        500,
                        false,
                        2
                ))
                .contracts();

        assertEquals(2, hits.size());
        assertEquals(OptionType.CALL, hits.getFirst().optionType());
        assertFalse(hits.getFirst().liquidityScore() <= hits.get(1).liquidityScore());
    }

    private static OptionChainEntry entry(long strike, long oi, long volume, OptionType type) {
        Instrument instrument = new Instrument(
                "LEG", "LEG", Exchange.NSE, ExchangeSegment.NSE_FNO,
                "OPTION", "NIFTY", LocalDate.of(2026, 6, 5), strike, type, 25, 5
        );
        OptionQuote quote = new OptionQuote(
                instrument,
                10000,
                oi,
                volume,
                9900,
                100,
                10100,
                100,
                new OptionGreeks(null, null, null, null, null)
        );
        if (type == OptionType.CALL) {
            return new OptionChainEntry(strike, quote, null);
        }
        return new OptionChainEntry(strike, null, quote);
    }

    private static final class StubOptionsProvider implements com.tradej.broker.api.port.OptionsProvider {
        private final OptionChainSnapshot chain;

        private StubOptionsProvider(OptionChainSnapshot chain) {
            this.chain = chain;
        }

        @Override
        public List<LocalDate> getExpiries(String underlying, ExchangeSegment exchangeSegment) {
            return List.of(chain.expiry());
        }

        @Override
        public List<Instrument> getOptionContracts(String underlying, ExchangeSegment exchangeSegment, LocalDate expiry) {
            return List.of();
        }

        @Override
        public OptionChainSnapshot getOptionChain(String underlying, ExchangeSegment exchangeSegment, LocalDate expiry) {
            return chain;
        }

        @Override
        public OptionQuote getGreeks(com.tradej.core.domain.model.InstrumentKey instrumentKey) {
            throw new UnsupportedOperationException();
        }

        @Override
        public com.tradej.core.domain.model.RollingOptionSeries getExpiredOptionHistory(
                com.tradej.core.domain.model.RollingOptionHistoryRequest request
        ) {
            return new com.tradej.core.domain.model.RollingOptionSeries(request, List.of());
        }

        @Override
        public long selectStrikePaisa(
                String underlying,
                ExchangeSegment exchangeSegment,
                long spotPaisa,
                OptionType optionType,
                com.tradej.core.domain.value.StrikeSelectionKind selectionKind,
                int depth
        ) {
            return 0L;
        }
    }
}
