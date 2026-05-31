package com.tradej.core.testing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.IntConsumer;

/**
 * Reusable stress-test harness that runs a configurable number of worker threads
 * executing the same action in tight loops, with coordinated start via
 * {@link CountDownLatch} to maximise contention.
 * <p>
 * Thread safety: all mutable state is captured into the returned
 * {@link StressTestResult} which is safe to read after {@code run} completes.
 * <p>
 * Usage:
 * <pre>{@code
 * var result = ConcurrentStressTester
 *     .run(10, 1000, i -> counter.incrementAndGet())
 *     .assertAllPassed();
 * }</pre>
 */
public final class ConcurrentStressTester {

    private static final Logger log = LoggerFactory.getLogger(ConcurrentStressTester.class);
    private static final int DEFAULT_TIMEOUT_SECONDS = 60;

    private ConcurrentStressTester() {
    }

    /**
     * Run {@code workerCount} threads, each executing {@code action} exactly
     * {@code iterationsPerWorker} times. All threads are released simultaneously
     * via a {@link CountDownLatch} to maximise contention.
     *
     * @param workerCount        number of concurrent threads
     * @param iterationsPerWorker iterations each thread runs
     * @param action             receives the zero-based thread index
     * @param timeout            maximum wall-clock time for all workers to finish
     */
    public static StressTestResult run(
            int workerCount,
            int iterationsPerWorker,
            IntConsumer action,
            Duration timeout
    ) {
        var resultBuilder = new StressTestResult.Builder(workerCount, iterationsPerWorker);
        var startGate = new CountDownLatch(1);
        var doneGate = new CountDownLatch(workerCount);
        var executor = Executors.newFixedThreadPool(workerCount);

        resultBuilder.markStart();
        for (int t = 0; t < workerCount; t++) {
            int threadIndex = t;
            executor.submit(() -> {
                try {
                    startGate.await(); // all threads wait here until released
                    for (int i = 0; i < iterationsPerWorker; i++) {
                        action.accept(threadIndex);
                        resultBuilder.recordPass();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    resultBuilder.recordException(e);
                } catch (Throwable ex) {
                    resultBuilder.recordException(ex);
                } finally {
                    doneGate.countDown();
                }
            });
        }

        // Release all threads simultaneously
        startGate.countDown();

        try {
            boolean completed = doneGate.await(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!completed) {
                log.warn("Stress test timed out after {} — {}/{} threads finished",
                        timeout, workerCount - doneGate.getCount(), workerCount);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            resultBuilder.markEnd();
            executor.shutdownNow();
        }

        return resultBuilder.build();
    }

    /** Convenience: default 60s timeout. */
    public static StressTestResult run(int workerCount, int iterationsPerWorker, IntConsumer action) {
        return run(workerCount, iterationsPerWorker, action,
                Duration.ofSeconds(DEFAULT_TIMEOUT_SECONDS));
    }

    /**
     * Run workers where each worker operates on a range of keys, for testing
     * {@link java.util.concurrent.ConcurrentHashMap} key-level isolation.
     */
    public static StressTestResult runWithKeys(
            int workerCount,
            int keysPerWorker,
            BiConsumer<Integer, Integer> actionForKey,
            Duration timeout
    ) {
        int iterationsPerWorker = keysPerWorker;
        var resultBuilder = new StressTestResult.Builder(workerCount, iterationsPerWorker);
        var startGate = new CountDownLatch(1);
        var doneGate = new CountDownLatch(workerCount);
        var executor = Executors.newFixedThreadPool(workerCount);

        resultBuilder.markStart();
        for (int t = 0; t < workerCount; t++) {
            int threadIndex = t;
            executor.submit(() -> {
                try {
                    startGate.await();
                    for (int k = 0; k < keysPerWorker; k++) {
                        actionForKey.accept(threadIndex, k);
                        resultBuilder.recordPass();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    resultBuilder.recordException(e);
                } catch (Throwable ex) {
                    resultBuilder.recordException(ex);
                } finally {
                    doneGate.countDown();
                }
            });
        }

        startGate.countDown();

        try {
            doneGate.await(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            resultBuilder.markEnd();
            executor.shutdownNow();
        }

        return resultBuilder.build();
    }

    /**
     * Run workers then assert invariants after all threads complete.
     */
    public static StressTestResult runAndAssert(
            int workerCount,
            int iterationsPerWorker,
            IntConsumer action,
            Runnable invariantAssertion,
            Duration timeout
    ) {
        StressTestResult result = run(workerCount, iterationsPerWorker, action, timeout);
        invariantAssertion.run();
        return result;
    }
}
