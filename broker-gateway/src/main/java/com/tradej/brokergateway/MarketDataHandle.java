package com.tradej.brokergateway;

import com.tradej.broker.api.IBrokerConnection;
import com.tradej.brokergateway.result.BrokerSource;
import com.tradej.brokergateway.result.GatewayResult;
import com.tradej.core.domain.model.Candle;
import com.tradej.core.domain.model.CandleHistoryRequest;
import com.tradej.core.domain.model.InstrumentKey;
import com.tradej.core.domain.model.MarketDepth;
import com.tradej.core.domain.model.Quote;
import com.tradej.core.domain.value.ExchangeSegment;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Handle for market data operations (LTP, quotes, depth, OHLC, historical candles).
 * Wraps {@link com.tradej.broker.api.port.MarketDataProvider} with timing and result metadata.
 */
public final class MarketDataHandle {

    private final BrokerCallSupport support;

    MarketDataHandle(BrokerSource source, IBrokerConnection connection) {
        this.support = new BrokerCallSupport(source, connection);
    }

    public GatewayResult<Long> ltp(String symbol) {
        return ltp(symbol, support.defaultSegment(symbol));
    }

    public GatewayResult<Long> ltp(String symbol, ExchangeSegment segment) {
        return support.timed(() -> support.connection().marketData().getLtpPaisa(support.resolveKey(symbol, segment)));
    }

    public GatewayResult<Quote> quote(String symbol) {
        return quote(symbol, support.defaultSegment(symbol));
    }

    public GatewayResult<Quote> quote(String symbol, ExchangeSegment segment) {
        return support.timed(() -> support.connection().marketData().getQuote(support.resolveKey(symbol, segment)));
    }

    public GatewayResult<MarketDepth> depth(String symbol) {
        return depth(symbol, support.defaultSegment(symbol));
    }

    public GatewayResult<MarketDepth> depth(String symbol, ExchangeSegment segment) {
        return support.timed(() -> support.connection().marketData().getDepth(support.resolveKey(symbol, segment)));
    }

    public GatewayResult<Quote> ohlc(String symbol) {
        return ohlc(symbol, support.defaultSegment(symbol));
    }

    public GatewayResult<Quote> ohlc(String symbol, ExchangeSegment segment) {
        return support.timed(() -> support.connection().marketData().getOhlcSnapshot(support.resolveKey(symbol, segment)));
    }

    public GatewayResult<List<Candle>> historical(String symbol, String interval, LocalDate from, LocalDate to) {
        return historical(symbol, support.defaultSegment(symbol), interval, from, to);
    }

    public GatewayResult<List<Candle>> historical(String symbol, ExchangeSegment segment, String interval, LocalDate from, LocalDate to) {
        return support.timed(() -> support.connection().marketData().getCandles(
                new CandleHistoryRequest(support.resolveKey(symbol, segment), interval, from, to)));
    }

    public GatewayResult<Map<InstrumentKey, Long>> batchLtp(Collection<InstrumentKey> keys) {
        return support.timed(() -> support.connection().marketData().getLtpBatch(keys));
    }

    public GatewayResult<Map<InstrumentKey, Quote>> batchQuote(Collection<InstrumentKey> keys) {
        return support.timed(() -> support.connection().marketData().getQuoteBatch(keys));
    }

    public GatewayResult<Map<InstrumentKey, Quote>> batchOhlc(Collection<InstrumentKey> keys) {
        return support.timed(() -> support.connection().marketData().getOhlcBatch(keys));
    }
}
