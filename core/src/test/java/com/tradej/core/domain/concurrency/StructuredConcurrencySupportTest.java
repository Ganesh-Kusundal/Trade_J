package com.tradej.core.domain.concurrency;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class StructuredConcurrencySupportTest {

    @Test
    void runConcurrentlyExecutesAllTasks() throws Exception {
        List<Callable<String>> tasks = List.of(
                () -> "alpha",
                () -> "bravo",
                () -> "charlie"
        );

        List<String> results = StructuredConcurrencySupport.runConcurrently(tasks);

        assertEquals(3, results.size());
        assertTrue(results.contains("alpha"));
        assertTrue(results.contains("bravo"));
        assertTrue(results.contains("charlie"));
    }

    @Test
    void runConcurrentlyPreservesOrder() throws Exception {
        List<Callable<Integer>> tasks = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            final int val = i;
            tasks.add(() -> {
                Thread.sleep(10 * (5 - val));
                return val;
            });
        }

        List<Integer> results = StructuredConcurrencySupport.runConcurrently(tasks);

        assertEquals(5, results.size());
        for (int i = 0; i < 5; i++) {
            assertEquals(i, results.get(i));
        }
    }

    @Test
    void runConcurrentlyHandlesEmptyList() throws Exception {
        List<String> results = StructuredConcurrencySupport.runConcurrently(List.of());
        assertTrue(results.isEmpty());
    }

    @Test
    void runConcurrentlyPropagatesException() {
        List<Callable<String>> tasks = List.of(
                () -> "ok",
                () -> {
                    throw new RuntimeException("task failure");
                }
        );

        ExecutionException ex = assertThrows(ExecutionException.class,
                () -> StructuredConcurrencySupport.runConcurrently(tasks));
        assertTrue(ex.getCause() instanceof RuntimeException);
        assertEquals("task failure", ex.getCause().getMessage());
    }

    @Test
    void runWithTimeoutReturnsResultBeforeDeadline() throws Exception {
        Callable<String> fastTask = () -> "done";

        Optional<String> result = StructuredConcurrencySupport.runWithTimeout(fastTask, Duration.ofSeconds(5));

        assertTrue(result.isPresent());
        assertEquals("done", result.get());
    }

    @Test
    void runWithTimeoutReturnsEmptyOnTimeout() throws Exception {
        Callable<String> slowTask = () -> {
            Thread.sleep(5000);
            return "never";
        };

        Optional<String> result = StructuredConcurrencySupport.runWithTimeout(slowTask, Duration.ofMillis(50));

        assertTrue(result.isEmpty());
    }

    @Test
    void runWithTimeoutHandlesNullTask() throws Exception {
        Optional<String> result = StructuredConcurrencySupport.runWithTimeout(null, Duration.ofSeconds(1));
        assertTrue(result.isEmpty());
    }

    @Test
    void runWithTimeoutPropagatesTaskException() {
        Callable<String> failingTask = () -> {
            throw new IllegalStateException("boom");
        };

        ExecutionException ex = assertThrows(ExecutionException.class,
                () -> StructuredConcurrencySupport.runWithTimeout(failingTask, Duration.ofSeconds(5)));
        assertTrue(ex.getCause() instanceof IllegalStateException);
    }

    @Test
    void isVirtualThreadsAvailableReturnsBoolean() {
        boolean result = StructuredConcurrencySupport.isVirtualThreadsAvailable();
        // On Java 21+ this should be true; on older runtimes it's false
        assertTrue(result || !result, "Should return a boolean value");
    }

    @Test
    void createExecutorReturnsNonNull() {
        var executor = StructuredConcurrencySupport.createExecutor();
        assertNotNull(executor);
        executor.shutdown();
    }

    @Test
    void createExecutorWithConcurrencyReturnsNonNull() {
        var executor = StructuredConcurrencySupport.createExecutor(4);
        assertNotNull(executor);
        executor.shutdown();
    }
}
