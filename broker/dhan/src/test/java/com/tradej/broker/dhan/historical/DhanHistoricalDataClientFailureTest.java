package com.tradej.broker.dhan.historical;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.tradej.broker.dhan.auth.DhanTokenInfo;
import com.tradej.broker.dhan.auth.DhanTokenProvider;
import com.tradej.broker.dhan.config.DhanConnectionSettings;
import com.tradej.broker.dhan.constants.DhanApiUrlResolver;
import com.tradej.broker.dhan.exceptions.DhanHttpException;
import com.tradej.broker.dhan.http.DhanAuthenticatedHttpClient;
import com.tradej.broker.dhan.instrument.DhanInstrumentDefinition;
import com.tradej.broker.core.rate.RateLimitConfig;
import com.tradej.broker.core.rate.MultiBucketRateLimiter;
import com.tradej.broker.core.resilience.CircuitBreaker;
import com.tradej.broker.dhan.resilience.DhanRetryExecutor;
import com.tradej.core.domain.model.CandleHistoryRequest;
import com.tradej.core.domain.model.InstrumentKey;
import com.tradej.core.domain.value.Exchange;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.OptionType;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("unit")
class DhanHistoricalDataClientFailureTest {

    @Test
    void fetchRangeFailsFastWhenLaterChunkKeepsFailing() throws Exception {
        AtomicInteger callCount = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/charts/intraday", exchange -> {
            int call = callCount.incrementAndGet();
            byte[] body = (call == 1 ? "{\"open\":[],\"high\":[],\"low\":[],\"close\":[],\"volume\":[],\"timestamp\":[]}" : "{\"error\":\"boom\"}")
                    .getBytes();
            int code = call == 1 ? 200 : 500;
            exchange.sendResponseHeaders(code, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            } finally {
                exchange.close();
            }
        });
        server.start();
        try {
            MultiBucketRateLimiter rateLimiter = new MultiBucketRateLimiter(Map.of(
                    "DATA", new RateLimitConfig("DATA", 100, 100)
            ));
            DhanHistoricalDataClient client = new DhanHistoricalDataClient(
                    new DhanAuthenticatedHttpClient(
                            HttpClient.newHttpClient(),
                            new ObjectMapper(),
                            fixedTokenProvider(),
                            DhanConnectionSettings.withDefaults("client-id", "token")
                    ),
                    new DhanApiUrlResolver("http://localhost:" + server.getAddress().getPort()),
                    new DhanRetryExecutor(rateLimiter, new CircuitBreaker())
            );

            CandleHistoryRequest request = new CandleHistoryRequest(
                    InstrumentKey.of("NIFTY", ExchangeSegment.NSE_EQ),
                    "1m",
                    LocalDate.of(2026, 1, 1),
                    LocalDate.of(2026, 4, 15)
            );

            DhanInstrumentDefinition definition = new DhanInstrumentDefinition(
                    "NIFTY",
                    "NIFTY",
                    Exchange.NSE,
                    ExchangeSegment.NSE_EQ,
                    "123",
                    "EQUITY",
                    "NIFTY",
                    null,
                    null,
                    OptionType.UNKNOWN,
                    1,
                    5,
                    "123"
            );

            assertThrows(RuntimeException.class, () -> client.fetchRange(request, definition));
            assertTrue(callCount.get() >= 2);
        } finally {
            server.stop(0);
        }
    }

    private DhanTokenProvider fixedTokenProvider() {
        return new DhanTokenProvider() {
            @Override
            public String getAccessToken() {
                return "token";
            }

            @Override
            public DhanTokenInfo getTokenInfo() {
                return new DhanTokenInfo(true, System.currentTimeMillis() + 60_000, false);
            }

            @Override
            public void ensureValid() {
                // static token provider for test transport.
            }
        };
    }
}
