package com.tradej.execution.service;

import com.github.benmanes.caffeine.cache.Cache;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import com.tradej.core.domain.port.DeadLetterQueue;
import com.tradej.core.domain.runtime.RuntimeModeHolder;
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
                new OrderIdentityRegistry(), DeadLetterQueue.noop());
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
        handler.start();

        var tradeOpenedField = ExecutionHandler.class.getDeclaredField("tradeOpenedEmitted");
        tradeOpenedField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Cache<String, Boolean> cache = (Cache<String, Boolean>) tradeOpenedField.get(handler);

        int workers = 10;
        int addsPerWorker = 100;

        var result = ConcurrentStressTester.run(workers, addsPerWorker, threadIndex -> {
            for (int i = 0; i < addsPerWorker; i++) {
                String orderId = "ORD-" + (i % 10);
                cache.asMap().putIfAbsent(orderId, Boolean.TRUE);
            }
        });

        result.assertAllPassed().requireNoExceptions();
        assertEquals(10, cache.asMap().size(),
                "tradeOpenedEmitted cache should contain exactly 10 unique order IDs");
    }

    @Test
    void emitTradeOpenedGuardAtomicity() throws Exception {
        handler.start();

        var tradeOpenedField = ExecutionHandler.class.getDeclaredField("tradeOpenedEmitted");
        tradeOpenedField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Cache<String, Boolean> cache = (Cache<String, Boolean>) tradeOpenedField.get(handler);

        int workers = 10;
        int attemptsPerWorker = 50;
        java.util.concurrent.atomic.AtomicInteger firstAddWins = new java.util.concurrent.atomic.AtomicInteger();

        var result = ConcurrentStressTester.run(workers, attemptsPerWorker, threadIndex -> {
            for (int i = 0; i < attemptsPerWorker; i++) {
                if (cache.asMap().putIfAbsent("ORD-ATOMIC", Boolean.TRUE) == null) {
                    firstAddWins.incrementAndGet();
                }
            }
        });

        result.assertAllPassed().requireNoExceptions();
        assertEquals(1, firstAddWins.get(), "Exactly one thread should win the putIfAbsent race");
        assertEquals(1, cache.asMap().size());
    }
}
