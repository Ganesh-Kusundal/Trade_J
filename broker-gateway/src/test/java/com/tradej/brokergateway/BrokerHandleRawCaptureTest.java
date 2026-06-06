package com.tradej.brokergateway;

import com.tradej.broker.api.IBrokerConnection;
import com.tradej.broker.api.port.InstrumentResolver;
import com.tradej.broker.api.port.MarketDataProvider;
import com.tradej.brokergateway.result.BrokerSource;
import com.tradej.brokergateway.result.GatewayResult;
import com.tradej.core.domain.model.Instrument;
import com.tradej.core.domain.model.InstrumentKey;
import com.tradej.core.domain.model.Quote;
import com.tradej.core.domain.value.Exchange;
import com.tradej.core.domain.value.ExchangeSegment;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BrokerHandleRawCaptureTest {

    @Mock IBrokerConnection connection;
    @Mock MarketDataProvider marketData;
    @Mock InstrumentResolver instruments;

    private Instrument testInstrument() {
        return new Instrument("RELIANCE", "RELIANCE", Exchange.NSE,
                ExchangeSegment.NSE_EQ, "EQ", null, null, null, null, 1L, 1L);
    }

    private BrokerHandle createHandle() {
        when(connection.marketData()).thenReturn(marketData);
        when(connection.instruments()).thenReturn(instruments);
        when(instruments.resolveNormalized(any(), any())).thenReturn(testInstrument());
        return new BrokerHandle(BrokerSource.DHAN, connection);
    }

    private Quote testQuote() {
        return new Quote(testInstrument(), 250000L, 251000L, 252000L, 249000L,
                250500L, 100000L, 50000L, 50000L, 10000L, System.currentTimeMillis());
    }

    @Test
    void rawCaptureDisabledByDefault() {
        BrokerHandle handle = createHandle();
        assertFalse(handle.isRawCaptureEnabled());
    }

    @Test
    void rawCaptureCanBeToggled() {
        BrokerHandle handle = createHandle();
        handle.enableRawCapture();
        assertTrue(handle.isRawCaptureEnabled());
        handle.disableRawCapture();
        assertFalse(handle.isRawCaptureEnabled());
    }

    @Test
    void rawBodyPopulatedWhenEnabled() {
        BrokerHandle handle = createHandle();
        when(marketData.getQuote(any())).thenReturn(testQuote());
        handle.enableRawCapture();

        GatewayResult<Quote> result = handle.quote("RELIANCE");
        assertNotNull(result.metadata().rawResponseBody());
        assertTrue(result.metadata().hasRawResponse());
    }

    @Test
    void rawBodyNullWhenDisabled() {
        BrokerHandle handle = createHandle();
        when(marketData.getQuote(any())).thenReturn(testQuote());

        GatewayResult<Quote> result = handle.quote("RELIANCE");
        assertNull(result.metadata().rawResponseBody());
        assertFalse(result.metadata().hasRawResponse());
    }
}
