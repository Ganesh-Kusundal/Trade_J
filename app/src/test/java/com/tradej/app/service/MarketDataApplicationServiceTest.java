package com.tradej.app.service;

import com.tradej.broker.api.port.MarketDataProvider;
import com.tradej.brokergateway.MarketGateway;
import com.tradej.core.domain.model.Candle;
import com.tradej.core.domain.model.CandleHistoryRequest;
import com.tradej.core.domain.model.InstrumentKey;
import com.tradej.core.domain.port.HistoricalAnalyticsService;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.historical.service.BrokerHistoricalQueryService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Tag("unit")
class MarketDataApplicationServiceTest {

    private static final long LTP_PAISA = 750_00L;
    private static final long FALLBACK_LTP_PAISA = 751_00L;

    private final InstrumentKey instrument = new InstrumentKey("SBIN", ExchangeSegment.NSE_EQ);
    private final MarketGateway marketGateway = mock(MarketGateway.class);

    @Test
    void getLtpPaisaUsesBrokerHistoricalQueryServiceWhenAvailable() {
        MarketDataProvider provider = mock(MarketDataProvider.class);
        BrokerHistoricalQueryService brokerHistoricalQueryService = new BrokerHistoricalQueryService(provider);
        when(provider.getLtpPaisa(instrument)).thenReturn(LTP_PAISA);

        MarketDataApplicationService service = new MarketDataApplicationService(
                marketGateway,
                Optional.of(brokerHistoricalQueryService),
                Optional.empty(),
                Optional.of(provider)
        );

        assertEquals(LTP_PAISA, service.getLtpPaisa(instrument));
    }

    @Test
    void getLtpPaisaFallsBackToDirectProviderWhenBrokerHistoricalFails() {
        MarketDataProvider failingBrokerProvider = mock(MarketDataProvider.class);
        MarketDataProvider directProvider = mock(MarketDataProvider.class);
        BrokerHistoricalQueryService brokerHistoricalQueryService = new BrokerHistoricalQueryService(failingBrokerProvider);
        when(failingBrokerProvider.getLtpPaisa(instrument)).thenThrow(new RuntimeException("broker unavailable"));
        when(directProvider.getLtpPaisa(instrument)).thenReturn(FALLBACK_LTP_PAISA);

        MarketDataApplicationService service = new MarketDataApplicationService(
                marketGateway,
                Optional.of(brokerHistoricalQueryService),
                Optional.empty(),
                Optional.of(directProvider)
        );

        assertEquals(FALLBACK_LTP_PAISA, service.getLtpPaisa(instrument));
    }

    @Test
    void queryCandlesUsesAnalyticsForParquetSource() {
        HistoricalAnalyticsService analytics = mock(HistoricalAnalyticsService.class);
        CandleHistoryRequest request = request();
        Candle candle = new Candle("SBIN", "5m", 1L, 2L, 3L, 4L, 5L, 6L, 7L, true);
        when(analytics.queryEquityCandles(request)).thenReturn(List.of(candle));

        MarketDataApplicationService service = new MarketDataApplicationService(
                marketGateway,
                Optional.empty(),
                Optional.of(analytics),
                Optional.empty()
        );

        assertEquals(List.of(candle), service.queryCandles(instrument, "5m", LocalDate.now(), LocalDate.now(), "parquet"));
    }

    @Test
    void queryCandlesFallsBackToDirectProviderWhenBrokerHistoricalFails() {
        MarketDataProvider failingBrokerProvider = mock(MarketDataProvider.class);
        MarketDataProvider directProvider = mock(MarketDataProvider.class);
        BrokerHistoricalQueryService brokerHistoricalQueryService = new BrokerHistoricalQueryService(failingBrokerProvider);
        CandleHistoryRequest request = request();
        Candle candle = new Candle("SBIN", "5m", 1L, 2L, 3L, 4L, 5L, 6L, 7L, true);
        when(failingBrokerProvider.getCandles(request)).thenThrow(new RuntimeException("broker unavailable"));
        when(directProvider.getCandles(request)).thenReturn(List.of(candle));

        MarketDataApplicationService service = new MarketDataApplicationService(
                marketGateway,
                Optional.of(brokerHistoricalQueryService),
                Optional.empty(),
                Optional.of(directProvider)
        );

        assertEquals(List.of(candle), service.queryCandles(instrument, "5m", LocalDate.now(), LocalDate.now(), "broker"));
    }

    private static CandleHistoryRequest request() {
        return new CandleHistoryRequest(new InstrumentKey("SBIN", ExchangeSegment.NSE_EQ), "5m", LocalDate.now(), LocalDate.now());
    }
}
