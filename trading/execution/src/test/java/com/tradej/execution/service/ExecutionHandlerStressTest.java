package com.tradej.execution.service;

import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.event.EventMetadata;
import com.tradej.core.domain.event.OrderFilled;
import com.tradej.core.domain.event.TradeOpened;
import com.tradej.core.domain.model.Order;
import com.tradej.core.domain.model.Trade;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import com.tradej.core.domain.port.DeadLetterQueue;
import com.tradej.core.domain.runtime.RuntimeModeHolder;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.OrderStatus;
import com.tradej.core.domain.value.OrderType;
import com.tradej.core.domain.value.ProductType;
import com.tradej.core.domain.value.Side;
import com.tradej.core.testing.ConcurrentStressTester;
import com.tradej.execution.identity.OrderIdentityRegistry;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Tag("stress")
class ExecutionHandlerStressTest {

    private Path tempDir;
    private ExecutionHandler handler;
    private CopyOnWriteArrayList<com.tradej.core.domain.event.DomainEvent> emitted;

    @BeforeEach
    void setUp() throws IOException {
        tempDir = Files.createTempDirectory("exec-stress-");
        handler = new ExecutionHandler(null, new RuntimeModeHolder(),
                new com.tradej.core.domain.time.LiveTradingClock(),
                new TradingCircuitBreaker(10, 30_000),
                new OrderIdentityRegistry(), DeadLetterQueue.noop(),
                ExecutionConfig.DEFAULTS);
        emitted = new CopyOnWriteArrayList<>();
    }

    @AfterEach
    void tearDown() {
        handler.stop();
        try (var paths = Files.walk(tempDir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try { Files.deleteIfExists(path); } catch (IOException ignored) {}
            });
        } catch (IOException ignored) {}
    }

    @Test
    void tradeOpenedEmittedCacheIsThreadSafe() throws Exception {
        TradeEventEmissionService service = new TradeEventEmissionService();
        Order order = order("ORD-STRESS");
        OrderFilled filled = filled(order);

        int workers = 10;
        int addsPerWorker = 100;

        var result = ConcurrentStressTester.run(workers, addsPerWorker, threadIndex -> {
            for (int i = 0; i < addsPerWorker; i++) {
                service.emit(order.orderId(), order, filled, emitted::add);
            }
        });

        result.assertAllPassed().requireNoExceptions();
        long openedCount = emitted.stream().filter(TradeOpened.class::isInstance).count();
        assertEquals(1, openedCount, "duplicate fill emission must open a trade only once");
    }

    @Test
    void emitTradeOpenedGuardAtomicity() throws Exception {
        TradeEventEmissionService service = new TradeEventEmissionService();
        Order order = order("ORD-ATOMIC");
        OrderFilled filled = filled(order);

        int workers = 10;
        int attemptsPerWorker = 50;

        var result = ConcurrentStressTester.run(workers, attemptsPerWorker, threadIndex -> {
            for (int i = 0; i < attemptsPerWorker; i++) {
                service.emit(order.orderId(), order, filled, emitted::add);
            }
        });

        result.assertAllPassed().requireNoExceptions();
        long openedCount = emitted.stream().filter(TradeOpened.class::isInstance).count();
        assertEquals(1, openedCount, "Exactly one thread should win the TradeOpened race");
    }

    private static Order order(String orderId) {
        return new Order(
                orderId,
                "sig-stress",
                "SBIN",
                ExchangeSegment.NSE_EQ,
                Side.BUY,
                ProductType.INTRADAY,
                OrderType.LIMIT,
                OrderStatus.TRADED,
                100,
                100,
                150_00L,
                0L,
                1_000L,
                "");
    }

    private static OrderFilled filled(Order order) {
        Trade fill = new Trade(
                "T-" + order.orderId(),
                order.orderId(),
                order.symbol(),
                order.exchangeSegment(),
                order.side(),
                order.filledQuantity(),
                order.pricePaisa(),
                order.exchangeTimeMs());
        return new OrderFilled(EventMetadata.correlated(order.correlationId(), 1L), order, java.util.List.of(fill));
    }
}
