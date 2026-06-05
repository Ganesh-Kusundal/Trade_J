package com.tradej.app.metrics;

import com.tradej.broker.api.port.MarketDataProvider;
import com.tradej.broker.core.observability.ObservableMarketDataProvider;
import com.tradej.core.domain.model.*;
import com.tradej.core.domain.value.Exchange;
import com.tradej.core.domain.value.ExchangeSegment;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("unit")
class ObservableMarketDataProviderTest {

    private MarketDataProvider delegate;
    private ObservableMarketDataProvider observable;
    private MeterRegistry registry;

    private final Instrument testInstrument = new Instrument(
            "SBIN", "SBIN", Exchange.NSE, ExchangeSegment.NSE_EQ, "EQUITY",
            null, null, null, null, 1, 5L
    );
    private final InstrumentKey testKey = testInstrument.key();

    @BeforeEach
    void setUp() {
        delegate = mock(MarketDataProvider.class);
        registry = new SimpleMeterRegistry();
        observable = new ObservableMarketDataProvider("dhan", delegate, registry);
    }

    @Test
    void delegatesGetQuoteAndRecordsMetrics() {
        Quote expected = new Quote(testInstrument, 750_00L, 751_00L, 749_00L, 752_00L, 748_00L, 10000L, 5000L, 3000L, 0L, System.currentTimeMillis());
        when(delegate.getQuote(testKey)).thenReturn(expected);

        Quote result = observable.getQuote(testKey);

        assertSame(expected, result);
        assertEquals(1, registry.counter("dhan.marketdata.calls", "method", "getQuote").count());
        assertNotNull(registry.find("dhan.marketdata.latency").timer());
    }

    @Test
    void delegatesGetLtpPaisaAndRecordsMetrics() {
        when(delegate.getLtpPaisa(testKey)).thenReturn(750_00L);

        long result = observable.getLtpPaisa(testKey);

        assertEquals(750_00L, result);
        assertEquals(1, registry.counter("dhan.marketdata.calls", "method", "getLtpPaisa").count());
    }

    @Test
    void delegatesGetCandlesAndRecordsMetrics() {
        CandleHistoryRequest request = new CandleHistoryRequest(testKey, "1d", null, null);
        when(delegate.getCandles(request)).thenReturn(List.of());

        List<Candle> result = observable.getCandles(request);

        assertNotNull(result);
        assertEquals(1, registry.counter("dhan.marketdata.calls", "method", "getCandles").count());
    }

    @Test
    void delegatesGetDepth() {
        MarketDepth expected = new MarketDepth(testInstrument, List.of(), List.of(), 5, System.currentTimeMillis());
        when(delegate.getDepth(testKey)).thenReturn(expected);

        MarketDepth result = observable.getDepth(testKey);

        assertSame(expected, result);
    }

    @Test
    void delegatesBatchMethods() {
        Set<InstrumentKey> keys = Set.of(testKey);
        when(delegate.getLtpBatch(keys)).thenReturn(Map.of());
        when(delegate.getQuoteBatch(keys)).thenReturn(Map.of());
        when(delegate.getOhlcBatch(keys)).thenReturn(Map.of());

        assertNotNull(observable.getLtpBatch(keys));
        assertNotNull(observable.getQuoteBatch(keys));
        assertNotNull(observable.getOhlcBatch(keys));
    }

    @Test
    void delegatesGetOhlcSnapshot() {
        Quote expected = new Quote(testInstrument, 750_00L, 0, 0, 0, 0, 0, 0, 0, 0L, 0);
        when(delegate.getOhlcSnapshot(testKey)).thenReturn(expected);

        Quote result = observable.getOhlcSnapshot(testKey);

        assertSame(expected, result);
    }
}
