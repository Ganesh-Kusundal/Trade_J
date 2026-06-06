package com.tradej.brokergateway;

import com.tradej.broker.api.IBrokerConnection;
import com.tradej.broker.api.port.InstrumentResolver;
import com.tradej.broker.api.port.OptionsProvider;
import com.tradej.brokergateway.result.BrokerSource;
import com.tradej.brokergateway.result.GatewayResult;
import com.tradej.brokergateway.spi.BrokerExtras;
import com.tradej.core.domain.model.Instrument;
import com.tradej.core.domain.model.InstrumentKey;
import com.tradej.core.domain.model.OptionGreeks;
import com.tradej.core.domain.model.OptionQuote;
import com.tradej.core.domain.value.ExchangeSegment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for the dynamic-dispatch surface of {@link BrokerHandle} and {@link BrokerExtras},
 * specifically the {@code invoke("getOptionGreeks", ...)} and
 * {@code extras().optionGreeks(...)} paths.
 */
class BrokerHandleInvokeTest {

    private IBrokerConnection connection;
    private OptionsProvider optionsProvider;
    private InstrumentResolver instrumentResolver;
    private BrokerHandle handle;

    private final InstrumentKey niftyKey =
            new InstrumentKey("NIFTY", ExchangeSegment.NSE_FNO);
    private final OptionQuote greeksQuote = new OptionQuote(
            null, 150_00L, 1000L, 500L, 145_00L, 100L, 155_00L, 200L,
            new OptionGreeks(0.55, -12.3, 0.005, 8.1, 0.18));

    @BeforeEach
    void setUp() {
        connection = mock(IBrokerConnection.class);
        optionsProvider = mock(OptionsProvider.class);
        instrumentResolver = mock(InstrumentResolver.class);

        when(connection.options()).thenReturn(optionsProvider);
        when(connection.instruments()).thenReturn(instrumentResolver);
        when(connection.getCapability(OptionsProvider.class))
                .thenReturn(Optional.of(optionsProvider));

        Instrument instrument = mock(Instrument.class);
        when(instrument.key()).thenReturn(niftyKey);
        when(instrumentResolver.resolveNormalized(eq("NIFTY"), eq(ExchangeSegment.NSE_FNO)))
                .thenReturn(instrument);
        when(optionsProvider.getGreeks(niftyKey)).thenReturn(greeksQuote);

        handle = new BrokerHandle(BrokerSource.DHAN, connection);
    }

    // ── typed path: handle.greeks(key) ─────────────────────────────

    @Test
    void typedGreeksReturnsGatewayResult() {
        GatewayResult<OptionQuote> result = handle.greeks(niftyKey);
        assertNotNull(result);
        assertEquals(BrokerSource.DHAN, result.source());
        assertSame(greeksQuote, result.data());
        assertTrue(result.isSuccess());
        verify(optionsProvider).getGreeks(niftyKey);
    }

    // ── invoke("getOptionGreeks", params) with symbol+segment ───────

    @Test
    void invokeGetOptionGreeksResolvesKeyFromSymbolAndSegment() {
        Map<String, Object> params = Map.of(
                "symbol", "NIFTY",
                "segment", "NSE_FNO");

        Optional<Object> result = handle.invoke("getOptionGreeks", params);

        assertTrue(result.isPresent());
        assertInstanceOf(GatewayResult.class, result.get());
        @SuppressWarnings("unchecked")
        GatewayResult<OptionQuote> gr = (GatewayResult<OptionQuote>) result.get();
        assertSame(greeksQuote, gr.data());
        assertEquals(BrokerSource.DHAN, gr.source());
        verify(instrumentResolver).resolveNormalized("NIFTY", ExchangeSegment.NSE_FNO);
        verify(optionsProvider).getGreeks(niftyKey);
    }

    @Test
    void invokeOptionGreeksAliasAlsoWorks() {
        Map<String, Object> params = Map.of(
                "symbol", "NIFTY",
                "segment", "NSE_FNO");

        Optional<Object> result = handle.invoke("optionGreeks", params);

        assertTrue(result.isPresent());
        assertInstanceOf(GatewayResult.class, result.get());
    }

    @Test
    void invokeGetOptionGreeksAcceptsInstrumentKeyDirectly() {
        Map<String, Object> params = Map.of("instrumentKey", niftyKey);

        Optional<Object> result = handle.invoke("getOptionGreeks", params);

        assertTrue(result.isPresent());
        verify(optionsProvider).getGreeks(niftyKey);
    }

    @Test
    void invokeGetOptionGreeksWithoutSymbolThrows() {
        Map<String, Object> params = Map.of("segment", "NSE_FNO");

        assertThrows(IllegalArgumentException.class,
                () -> handle.invoke("getOptionGreeks", params));
    }

    @Test
    void invokeUnknownMethodFallsThroughToExtrasAndThrows() {
        Map<String, Object> params = Map.of("symbol", "NIFTY");

        // Dhan extras does not expose "notARealMethod" — should throw
        assertThrows(UnsupportedOperationException.class,
                () -> handle.invoke("notARealMethod", params));
    }

    // ── extras().optionGreeks(key) ─────────────────────────────────

    @Test
    void extrasOptionGreeksReturnsUnwrappedQuote() {
        Optional<OptionQuote> result = handle.extras().optionGreeks(niftyKey);

        assertTrue(result.isPresent());
        assertSame(greeksQuote, result.get());
    }

    @Test
    void extrasOptionsProviderIsExposed() {
        assertSame(optionsProvider, handle.extras().optionsProvider().orElse(null));
    }

    @Test
    void extrasSourceIsDhan() {
        assertEquals(BrokerSource.DHAN, handle.extras().source());
    }

    @Test
    void extrasMethodsListIncludesOptionGreeks() {
        // DhanExtras is what the handle caches for source=DHAN
        BrokerExtras extras = handle.extras();
        assertTrue(extras.methods().contains("optionGreeks"));
        assertTrue(extras.methods().contains("getOptionGreeks"));
    }

    // ── extras().invoke("optionGreeks", key) ────────────────────────

    @Test
    void extrasInvokeOptionGreeksWithKey() {
        Optional<Object> result = handle.extras().invoke("optionGreeks", niftyKey);

        assertTrue(result.isPresent());
        assertSame(greeksQuote, result.get());
    }

    @Test
    void extrasInvokeGetOptionGreeksAlias() {
        Optional<Object> result = handle.extras().invoke("getOptionGreeks", niftyKey);

        assertTrue(result.isPresent());
        assertSame(greeksQuote, result.get());
    }

    @Test
    void extrasInvokeOptionGreeksWithNullArgsThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> handle.extras().invoke("optionGreeks"));
    }

    @Test
    void extrasInvokeOptionGreeksWithWrongArgTypeThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> handle.extras().invoke("optionGreeks", "notAnInstrumentKey"));
    }

    // ── extras().invoke("getOptionGreeks", params:Map) ───────────────

    @Test
    void extrasInvokeGetOptionGreeksWithMapThrowsBecauseNotATypedKey() {
        // The extras-level invoke expects an InstrumentKey positional arg,
        // not a Map. The Map-based dispatch happens via BrokerHandle.invoke, not here.
        Map<String, Object> params = Map.of("symbol", "NIFTY", "segment", "NSE_FNO");
        assertThrows(IllegalArgumentException.class,
                () -> handle.extras().invoke("getOptionGreeks", params));
    }

    // ── broker without options capability ──────────────────────────

    @Test
    void extrasOptionGreeksReturnsEmptyWhenBrokerHasNoOptionsCapability() {
        IBrokerConnection bare = mock(IBrokerConnection.class);
        when(bare.options()).thenReturn(optionsProvider);
        when(bare.instruments()).thenReturn(instrumentResolver);
        when(bare.getCapability(OptionsProvider.class)).thenReturn(Optional.empty());

        BrokerHandle bareHandle = new BrokerHandle(BrokerSource.SIMULATION, bare);
        // The default BrokerExtras has no optionsProvider; optionGreeks returns empty
        Optional<OptionQuote> result = bareHandle.extras().optionGreeks(niftyKey);
        assertTrue(result.isEmpty());

        // invoke falls through to extras().invoke which throws
        assertThrows(UnsupportedOperationException.class,
                () -> bareHandle.extras().invoke("optionGreeks", niftyKey));
    }
}
