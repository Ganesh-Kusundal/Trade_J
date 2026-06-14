package com.tradej.broker.upstox;

import com.tradej.broker.api.IBrokerConnectionContractTest;
import com.tradej.broker.api.port.*;
import com.tradej.broker.upstox.adapter.UpstoxDataServicesProvider;
import com.tradej.broker.upstox.adapter.UpstoxProfileProvider;
import com.tradej.broker.upstox.depth.UpstoxMarketDepthProvider;
import com.tradej.broker.upstox.depth.UpstoxTwentyDepthWebSocketClient;
import com.tradej.broker.upstox.instrument.UpstoxInstrumentLoader;
import com.tradej.broker.upstox.instrument.UpstoxInstrumentResolver;
import org.junit.jupiter.api.Tag;

import static org.mockito.Mockito.mock;

/**
 * Contract test for Upstox broker connection.
 * 
 * <p>Verifies that {@link UpstoxBrokerConnection} adheres to the
 * {@link com.tradej.broker.api.IBrokerConnection} contract:
 * <ul>
 *   <li>All mandatory ports are non-null</li>
 *   <li>getCapability() never returns null</li>
 *   <li>Known capabilities are advertised correctly</li>
 *   <li>Unknown capabilities return empty Optional</li>
 *   <li>Capability instances are stable</li>
 *   <li>connect/disconnect don't throw</li>
 *   <li>Market data capabilities are properly configured</li>
 * </ul>
 * 
 * @see IBrokerConnectionContractTest
 */
@Tag("unit")
class UpstoxBrokerConnectionContractTest extends IBrokerConnectionContractTest {

    @Override
    protected com.tradej.broker.api.IBrokerConnection createConnection() {
        return new UpstoxBrokerConnection(
                mock(MarketDataProvider.class),
                mock(OrderCommand.class),
                mock(OrderQuery.class),
                mock(PortfolioProvider.class),
                mock(MarginProvider.class),
                mock(UpstoxInstrumentResolver.class),
                mock(WebSocketMultiplexer.class),
                mock(FuturesProvider.class),
                mock(OptionsProvider.class),
                mock(NewsProvider.class),
                mock(ConditionalAlertProvider.class),
                mock(SliceOrderCommand.class),
                mock(UpstoxDataServicesProvider.class),
                mock(UpstoxProfileProvider.class),
                mock(UpstoxInstrumentLoader.class),
                mock(UpstoxTwentyDepthWebSocketClient.class),
                mock(UpstoxMarketDepthProvider.class)
        );
    }
}
