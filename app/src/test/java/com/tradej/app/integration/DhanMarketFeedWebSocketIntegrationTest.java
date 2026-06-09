package com.tradej.app.integration;

import com.tradej.broker.api.model.MarketSubscriptionRequest;
import com.tradej.broker.dhan.DhanBrokerConnection;
import com.tradej.core.domain.event.MarketTickEvent;
import com.tradej.core.domain.event.StreamHealthChanged;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.FeedMode;
import com.tradej.execution.service.CaffeineIdempotencyCache;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Live smoke test for the native Dhan market feed WebSocket ({@code wss://api-feed.dhan.co}).
 */
@Tag("integration")
@Tag("broker-ws")
class DhanMarketFeedWebSocketIntegrationTest {
    private DhanBrokerConnection brokerConnection;

    @AfterEach
    void tearDown() {
        if (brokerConnection != null) {
            brokerConnection.disconnect();
        }
    }

    @Test
    void receivesTickerForTcsDuringMarketHours() throws Exception {
        Assumptions.assumeTrue(isExchangeSessionOpen(),
                "NSE cash session is closed; skipping live market feed assertion.");

        brokerConnection = new DhanBrokerConnection(
                LiveDhanTestSupport.connectionSettingsOrSkip(),
                com.tradej.broker.dhan.constants.DhanProtocolConstants.defaultRateLimiter(),
                new CaffeineIdempotencyCache()
        );
        brokerConnection.loadDailyInstrumentCatalog(Files.createTempDirectory("dhan-market-feed-cache"), false);

        CountDownLatch tick = new CountDownLatch(1);
        AtomicReference<MarketTickEvent> latestTick = new AtomicReference<>();
        brokerConnection.websocket().onMarketData(event -> {
            if (event instanceof MarketTickEvent marketTick
                    && "TCS".equals(marketTick.symbol())
                    && marketTick.segment() == ExchangeSegment.NSE_EQ
                    && marketTick.ltpPaisa() > 0L) {
                latestTick.set(marketTick);
                tick.countDown();
            }
            if (event instanceof StreamHealthChanged health && "CONNECTED".equals(health.status())) {
                // no-op; connection confirmed
            }
        });

        brokerConnection.connect();
        brokerConnection.websocket().subscribe(
                List.of(new MarketSubscriptionRequest("TCS", ExchangeSegment.NSE_EQ)),
                FeedMode.TICKER
        );

        assertTrue(tick.await(45, TimeUnit.SECONDS),
                "Expected native market feed tick for TCS during market hours.");

        MarketTickEvent received = latestTick.get();
        assertNotNull(received);
        assertEquals("TCS", received.symbol());
        assertEquals(ExchangeSegment.NSE_EQ, received.segment());
        assertTrue(received.ltpPaisa() > 0L, "Tick price should be positive.");
    }

    private boolean isExchangeSessionOpen() {
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Asia/Kolkata"));
        DayOfWeek day = now.getDayOfWeek();
        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
            return false;
        }
        LocalTime time = now.toLocalTime();
        return !time.isBefore(LocalTime.of(9, 15)) && !time.isAfter(LocalTime.of(15, 30));
    }
}
