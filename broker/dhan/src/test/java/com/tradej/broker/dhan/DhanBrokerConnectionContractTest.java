package com.tradej.broker.dhan;

import com.tradej.broker.api.IBrokerConnectionContractTest;
import com.tradej.broker.api.port.*;
import com.tradej.broker.dhan.adapter.DhanInstrumentResolver;
import com.tradej.broker.dhan.client.DhanClientHolder;
import org.junit.jupiter.api.Tag;

import static org.mockito.Mockito.mock;

@Tag("unit")
class DhanBrokerConnectionContractTest extends IBrokerConnectionContractTest {

    @Override
    protected com.tradej.broker.api.IBrokerConnection createConnection() {
        return new DhanBrokerConnection(
                mock(DhanClientHolder.class),
                mock(DhanInstrumentResolver.class),
                mock(MarketDataProvider.class),
                mock(FuturesProvider.class),
                mock(OptionsProvider.class),
                mock(OrderCommand.class),
                mock(OrderQuery.class),
                mock(SliceOrderCommand.class),
                mock(BracketOrderProvider.class),
                mock(GttOrderProvider.class),
                mock(PortfolioProvider.class),
                mock(MarginProvider.class),
                mock(SessionRiskProvider.class),
                mock(ConditionalAlertProvider.class),
                mock(WebSocketMultiplexer.class)
        );
    }
}
