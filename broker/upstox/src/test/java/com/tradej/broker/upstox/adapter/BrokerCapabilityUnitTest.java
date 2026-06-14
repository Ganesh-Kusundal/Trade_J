package com.tradej.broker.upstox.adapter;

import com.tradej.broker.api.port.*;
import com.tradej.broker.upstox.UpstoxBrokerConnection;
import com.tradej.broker.upstox.depth.UpstoxMarketDepthProvider;
import com.tradej.broker.upstox.depth.UpstoxTwentyDepthWebSocketClient;
import com.tradej.broker.upstox.instrument.UpstoxInstrumentLoader;
import com.tradej.broker.upstox.instrument.UpstoxInstrumentResolver;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.withSettings;

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
        // Use ConditionalAlertProvider mock that also implements GttOrderProvider
        // to reflect that UpstoxGttOrderAdapter implements both interfaces.
        ConditionalAlertProvider conditionalAlertProvider = mock(ConditionalAlertProvider.class,
                withSettings().extraInterfaces(GttOrderProvider.class));
        SliceOrderCommand sliceOrderCommand = mock(SliceOrderCommand.class);
        UpstoxDataServicesProvider dataServicesProvider = mock(UpstoxDataServicesProvider.class);
        UpstoxProfileProvider profileProvider = mock(UpstoxProfileProvider.class);
        UpstoxInstrumentLoader instrumentLoader = mock(UpstoxInstrumentLoader.class);
        UpstoxTwentyDepthWebSocketClient depthClient = mock(UpstoxTwentyDepthWebSocketClient.class);
        UpstoxMarketDepthProvider marketDepthProvider = mock(UpstoxMarketDepthProvider.class);

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
                conditionalAlertProvider,
                sliceOrderCommand,
                dataServicesProvider,
                profileProvider,
                instrumentLoader,
                depthClient,
                marketDepthProvider
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

        // GTT orders are supported via ConditionalAlertProvider (and also GttOrderProvider
        // since UpstoxGttOrderAdapter implements both interfaces)
        Optional<GttOrderProvider> gttCap = connection.getCapability(GttOrderProvider.class);
        assertTrue(gttCap.isPresent(), "GttOrderProvider should be supported via Upstox GTT adapter");
        assertSame(conditionalAlertProvider, gttCap.get());

        Optional<ConditionalAlertProvider> alertCap = connection.getCapability(ConditionalAlertProvider.class);
        assertTrue(alertCap.isPresent(), "ConditionalAlertProvider (GTT) should be supported");
        assertSame(conditionalAlertProvider, alertCap.get());

        Optional<SliceOrderCommand> sliceCap = connection.getCapability(SliceOrderCommand.class);
        assertTrue(sliceCap.isPresent(), "Upstox SliceOrderCommand should now be supported");
        assertSame(sliceOrderCommand, sliceCap.get());
    }
}
