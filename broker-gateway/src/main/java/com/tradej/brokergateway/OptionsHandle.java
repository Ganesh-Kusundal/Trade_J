package com.tradej.brokergateway;

import com.tradej.broker.api.IBrokerConnection;
import com.tradej.broker.api.spi.BrokerSource;
import com.tradej.brokergateway.result.GatewayResult;
import com.tradej.core.domain.model.Instrument;
import com.tradej.core.domain.model.InstrumentKey;
import com.tradej.core.domain.model.OptionChainSnapshot;
import com.tradej.core.domain.model.OptionQuote;
import com.tradej.core.domain.model.RollingOptionHistoryRequest;
import com.tradej.core.domain.model.RollingOptionSeries;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.OptionType;
import com.tradej.core.domain.value.StrikeSelectionKind;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Handle for options operations (chain, greeks, expiries, strike selection).
 * Wraps {@link com.tradej.broker.api.port.OptionsProvider} with timing and result metadata.
 */
public final class OptionsHandle {

    private final BrokerCallSupport support;

    OptionsHandle(BrokerSource source, IBrokerConnection connection) {
        this.support = new BrokerCallSupport(source, connection);
    }

    public GatewayResult<List<LocalDate>> expiries(String underlying) {
        return expiries(underlying, ExchangeSegment.IDX_I);
    }

    public GatewayResult<List<LocalDate>> expiries(String underlying, ExchangeSegment segment) {
        return support.timed(() -> support.connection().options().getExpiries(underlying, segment));
    }

    public GatewayResult<OptionChainSnapshot> optionChain(String underlying) {
        List<LocalDate> expiries = support.connection().options().getExpiries(underlying, ExchangeSegment.IDX_I);
        if (expiries.isEmpty()) {
            Instrument inst = support.instruments().resolveNormalized(underlying, ExchangeSegment.IDX_I);
            return support.result(new OptionChainSnapshot(inst, null, 0L, List.of()));
        }
        return optionChain(underlying, ExchangeSegment.IDX_I, expiries.getFirst());
    }

    public GatewayResult<OptionChainSnapshot> optionChain(String underlying, ExchangeSegment segment, LocalDate expiry) {
        return support.timed(() -> support.connection().options().getOptionChain(underlying, segment, expiry));
    }

    /**
     * Batch-fetch option chains for multiple expiries of the same underlying.
     * More efficient than calling {@link #optionChain} in a loop when the broker
     * supports batch retrieval.
     */
    public GatewayResult<Map<LocalDate, OptionChainSnapshot>> optionChainBatch(
            String underlying, ExchangeSegment segment, List<LocalDate> expiries) {
        return support.timed(() -> support.connection().options().getOptionChainBatch(underlying, segment, expiries));
    }

    public GatewayResult<OptionQuote> greeks(InstrumentKey key) {
        return support.timed(() -> support.connection().options().getGreeks(key));
    }

    public GatewayResult<List<Instrument>> optionContracts(String underlying, ExchangeSegment segment, LocalDate expiry) {
        return support.timed(() -> support.connection().options().getOptionContracts(underlying, segment, expiry));
    }

    public GatewayResult<Long> selectStrike(String underlying, ExchangeSegment segment,
                                            long spotPaisa, OptionType type,
                                            StrikeSelectionKind kind, int depth) {
        return support.timed(() -> support.connection().options().selectStrikePaisa(underlying, segment, spotPaisa, type, kind, depth));
    }

    public GatewayResult<RollingOptionSeries> rollingOptions(RollingOptionHistoryRequest request) {
        return support.timed(() -> support.connection().options().getExpiredOptionHistory(request));
    }
}
