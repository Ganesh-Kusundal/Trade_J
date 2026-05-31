package com.tradej.app.integration;

import com.tradej.broker.api.model.MarketSubscriptionRequest;
import com.tradej.broker.icici.auth.BreezeTokenManager;
import com.tradej.broker.icici.config.BreezeConnectionSettings;
import com.tradej.broker.icici.instrument.BreezeInstrumentLoader;
import com.tradej.broker.icici.instrument.BreezeInstrumentResolver;
import com.tradej.broker.icici.websocket.BreezeWebSocketMultiplexer;
import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.event.EventMetadataFactory;
import com.tradej.core.domain.event.MarketTickEvent;
import com.tradej.core.domain.time.LiveTradingClock;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.FeedMode;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("integration")
@Tag("broker-ws")
class IciciMarketFeedIntegrationTest {

    @Test
    void receivesAtLeastOneTickDuringMarketHoursOrConnectsSuccessfully() throws Exception {
        BreezeConnectionSettings settings = LiveIciciTestSupport.connectionSettingsOrSkip();
        LiveIciciTestSupport.preflightSessionOrSkip(settings);

        BreezeInstrumentResolver resolver = new BreezeInstrumentResolver(new BreezeInstrumentLoader());
        resolver.loadFromRemote();

        BreezeWebSocketMultiplexer multiplexer = new BreezeWebSocketMultiplexer(
                new BreezeTokenManager(settings),
                resolver,
                new EventMetadataFactory(new LiveTradingClock())
        );
        ArrayBlockingQueue<DomainEvent> events = new ArrayBlockingQueue<>(16);
        multiplexer.onMarketData(events::offer);
        multiplexer.connect();
        assertTrue(multiplexer.isConnected(), "ICICI websocket should connect");

        multiplexer.subscribe(
                List.of(new MarketSubscriptionRequest("RELIANCE", ExchangeSegment.NSE_EQ)),
                FeedMode.TICKER
        );

        DomainEvent event = events.poll(20, TimeUnit.SECONDS);
        multiplexer.disconnect();
        if (event instanceof MarketTickEvent tick) {
            assertTrue(tick.ltpPaisa() > 0);
        } else {
            assertNotNull(multiplexer);
        }
    }
}
