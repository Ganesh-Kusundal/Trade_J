package com.tradej.broker.icici;

import com.tradej.broker.api.IBrokerConnectionContractTest;
import com.tradej.broker.api.port.*;
import com.tradej.broker.icici.instrument.BreezeInstrumentResolver;
import org.junit.jupiter.api.Tag;

import static org.mockito.Mockito.mock;

@Tag("unit")
class IciciBrokerConnectionContractTest extends IBrokerConnectionContractTest {

    @Override
    protected com.tradej.broker.api.IBrokerConnection createConnection() {
        return new IciciBrokerConnection(
                mock(MarketDataProvider.class),
                mock(FuturesProvider.class),
                mock(OptionsProvider.class),
                mock(OrderCommand.class),
                mock(OrderQuery.class),
                mock(PortfolioProvider.class),
                mock(MarginProvider.class),
                mock(BreezeInstrumentResolver.class),
                mock(WebSocketMultiplexer.class)
        );
    }
}
