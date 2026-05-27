package com.tradej.app.integration;

import com.tradej.broker.api.model.MarketSubscriptionRequest;
import com.tradej.broker.dhan.DhanBrokerConnection;
import com.tradej.core.domain.event.StreamHealthChanged;
import com.tradej.core.domain.event.TickReceived;
import com.tradej.core.domain.value.Exchange;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.FeedMode;
import com.tradej.execution.service.CaffeineIdempotencyCache;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("integration")
@Tag("broker-ws")
class DhanMarketFeedIntegrationTest {
    private DhanBrokerConnection brokerConnection;

    @AfterEach
    void tearDown() {
        if (brokerConnection != null) {
            brokerConnection.disconnect();
        }
    }

    @Test
    void receivesLiveTickFromDhanFeed() throws Exception {
        String symbol = LiveDhanTestSupport.value("DHAN_TEST_SYMBOL", "dhan.testSymbol");
        String exchangeCode = LiveDhanTestSupport.value("DHAN_TEST_EXCHANGE", "dhan.testExchange");
        String securityId = LiveDhanTestSupport.value("DHAN_TEST_SECURITY_ID", "dhan.testSecurityId");
        String segment = LiveDhanTestSupport.value("DHAN_TEST_SEGMENT", "dhan.testSegment");
        Assumptions.assumeTrue(LiveDhanTestSupport.isPresent(symbol)
                        && isPresent(exchangeCode) && isPresent(securityId) && isPresent(segment),
                "Set DHAN_CLIENT_ID, DHAN_ACCESS_TOKEN, DHAN_TEST_SYMBOL, DHAN_TEST_EXCHANGE, DHAN_TEST_SECURITY_ID, and DHAN_TEST_SEGMENT to run this integration test.");

        brokerConnection = DhanBrokerConnection.create(
                LiveDhanTestSupport.connectionSettingsOrSkip(),
                new CaffeineIdempotencyCache()
        );
        Exchange exchange = Exchange.valueOf(exchangeCode.toUpperCase());
        ExchangeSegment exchangeSegment = ExchangeSegment.valueOf(segment.toUpperCase());
        brokerConnection.loadInstrumentCatalog(writeCatalog(symbol, exchange.name(), segment, securityId));

        CountDownLatch connected = new CountDownLatch(1);
        CountDownLatch tick = new CountDownLatch(1);
        brokerConnection.websocket().onMarketData(event -> {
            if (event instanceof StreamHealthChanged healthChanged && "CONNECTED".equals(healthChanged.status())) {
                connected.countDown();
            }
            if (event instanceof TickReceived) {
                tick.countDown();
            }
        });

        brokerConnection.connect();
        brokerConnection.websocket().subscribe(
                java.util.List.of(new MarketSubscriptionRequest(symbol, exchangeSegment)),
                FeedMode.TICKER
        );

        assertTrue(connected.await(30, TimeUnit.SECONDS), "Expected the Dhan market feed WebSocket to connect.");
        assertTrue(brokerConnection.websocket().subscriptions().containsKey(new MarketSubscriptionRequest(symbol, exchangeSegment)),
                "Expected the Dhan market feed subscription to be registered.");

        if (isExchangeSessionOpen(exchange)) {
            assertTrue(tick.await(30, TimeUnit.SECONDS), "Expected at least one live tick from Dhan market feed during market hours.");
        }
    }

    private boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }

    private boolean isExchangeSessionOpen(Exchange exchange) {
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Asia/Kolkata"));
        DayOfWeek day = now.getDayOfWeek();
        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
            return false;
        }
        LocalTime time = now.toLocalTime();
        if (exchange == Exchange.MCX) {
            return !time.isBefore(LocalTime.of(9, 0)) && !time.isAfter(LocalTime.of(23, 30));
        }
        return !time.isBefore(LocalTime.of(9, 15)) && !time.isAfter(LocalTime.of(15, 30));
    }

    private Path writeCatalog(String symbol, String exchange, String segment, String securityId) throws Exception {
        Path file = Files.createTempFile("dhan-itest-catalog", ".csv");
        Files.writeString(file, """
                symbol,exchange,exchangeSegment,securityId
                %s,%s,%s,%s
                """.formatted(symbol, exchange, segment, securityId));
        return file;
    }
}
