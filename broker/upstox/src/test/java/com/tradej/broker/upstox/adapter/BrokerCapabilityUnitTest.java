package com.tradej.broker.upstox.adapter;

import com.tradej.broker.api.port.*;
import com.tradej.broker.upstox.UpstoxBrokerConnection;
import com.tradej.broker.upstox.instrument.UpstoxInstrumentLoader;
import com.tradej.broker.upstox.instrument.UpstoxInstrumentResolver;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

@Tag("unit")
class BrokerCapabilityUnitTest {

    @Test
    void testUpstoxSupportedAndUnsupportedCapabilities() {
        MarketDataProvider marketDataProvider = mock(MarketDataProvider.class);
        OrderCommand orderCommand = mock(OrderCommand.class);
        OrderQuery orderQuery = mock(OrderQuery.class);
        PortfolioProvider portfolioProvider = mock(PortfolioProvider.class);
        MarginProvider marginProvider = mock(MarginProvider.class);
        UpstoxInstrumentResolver instrumentResolver = mock(UpstoxInstrumentResolver.class);
        WebSocketMultiplexer webSocketMultiplexer = mock(WebSocketMultiplexer.class);
        FuturesProvider futuresProvider = mock(FuturesProvider.class);
        OptionsProvider optionsProvider = mock(OptionsProvider.class);
        NewsProvider newsProvider = mock(NewsProvider.class);
        UpstoxInstrumentLoader instrumentLoader = mock(UpstoxInstrumentLoader.class);

        UpstoxBrokerConnection connection = new UpstoxBrokerConnection(
                marketDataProvider,
                orderCommand,
                orderQuery,
                portfolioProvider,
                marginProvider,
                instrumentResolver,
                webSocketMultiplexer,
                futuresProvider,
                optionsProvider,
                newsProvider,
                instrumentLoader
        );

        // Verify supported capabilities return non-empty optionals containing the mock
        Optional<MarketDataProvider> marketDataCap = connection.getCapability(MarketDataProvider.class);
        assertTrue(marketDataCap.isPresent());
        assertSame(marketDataProvider, marketDataCap.get());

        Optional<OrderCommand> ordersCap = connection.getCapability(OrderCommand.class);
        assertTrue(ordersCap.isPresent());
        assertSame(orderCommand, ordersCap.get());

        Optional<FuturesProvider> futuresCap = connection.getCapability(FuturesProvider.class);
        assertTrue(futuresCap.isPresent());
        assertSame(futuresProvider, futuresCap.get());

        // Verify unsupported capability return empty optionals (LSP conformance, no exceptions)
        Optional<BracketOrderProvider> bracketCap = connection.getCapability(BracketOrderProvider.class);
        assertFalse(bracketCap.isPresent(), "Upstox does not support bracket orders; getCapability should return empty");

        Optional<GttOrderProvider> gttCap = connection.getCapability(GttOrderProvider.class);
        assertFalse(gttCap.isPresent(), "Upstox does not support GTT orders; getCapability should return empty");

        Optional<SliceOrderCommand> sliceCap = connection.getCapability(SliceOrderCommand.class);
        assertFalse(sliceCap.isPresent(), "Upstox does not support slice orders; getCapability should return empty");
    }
}
