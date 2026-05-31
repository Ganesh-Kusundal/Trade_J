package com.tradej.core.testing;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Result of a single {@link ConcurrentStressTester} run.
 * <p>
 * Immutable snapshot after the run completes. Use {@link #assertAllPassed()}
 * or field-level assertions to verify correctness.
 */
public record StressTestResult(
        int workerCount,
        int operationsPerWorker,
        long passes,
        long failures,
        List<Throwable> exceptions,
        Duration elapsed
) {

    public static StressTestResult empty() {
        return new StressTestResult(0, 0, 0, 0, List.of(), Duration.ZERO);
    }

    /** Assert that every operation passed. */
    public StressTestResult assertAllPassed() {
        assertEquals(passes, expectedTotal(), () ->
                "Expected " + expectedTotal() + " passes but got " + passes
                        + " (" + failures + " failures, " + exceptions.size() + " exceptions)"
        );
        return this;
    }

    /** Assert that at least {@code minPasses} operations passed. */
    public StressTestResult assertAtLeastPassed(long minPasses) {
        assertTrue(passes >= minPasses,
                "Expected at least " + minPasses + " passes but got " + passes);
        return this;
    }

    /** Assert that total passes + failures equals the expected total. */
    public StressTestResult assertNoSilentLoss() {
        assertEquals(expectedTotal(), passes + failures,
                "Total operations (passes + failures) must equal expected total");
        return this;
    }

    /** Expected total number of operations across all workers. */
    public long expectedTotal() {
        return (long) workerCount * operationsPerWorker;
    }

    /** Convenience: wrap exceptions into an AssertionError if any exist. */
    public StressTestResult requireNoExceptions() {
        if (!exceptions.isEmpty()) {
            AssertionError err = new AssertionError(
                    exceptions.size() + " unexpected exceptions during stress test");
            exceptions.forEach(err::addSuppressed);
            throw err;
        }
        return this;
    }

    /**
     * Builder used internally by {@link ConcurrentStressTester}.
     */
    public static final class Builder {
        private final int workerCount;
        private final int operationsPerWorker;
        private final AtomicLong passes = new AtomicLong();
        private final AtomicLong failures = new AtomicLong();
        private final List<Throwable> exceptions = new CopyOnWriteArrayList<>();
        private long startNanos;
        private long endNanos;

        public Builder(int workerCount, int operationsPerWorker) {
            this.workerCount = workerCount;
            this.operationsPerWorker = operationsPerWorker;
        }

        public void recordPass() {
            passes.incrementAndGet();
        }

        public void recordFailure() {
            failures.incrementAndGet();
        }

        public void recordException(Throwable t) {
            exceptions.add(t);
            failures.incrementAndGet();
        }

        public void markStart() {
            startNanos = System.nanoTime();
        }

        public void markEnd() {
            endNanos = System.nanoTime();
        }

        public long passes() {
            return passes.get();
        }

        public StressTestResult build() {
            return new StressTestResult(
                    workerCount,
                    operationsPerWorker,
                    passes.get(),
                    failures.get(),
                    List.copyOf(exceptions),
                    Duration.ofNanos(endNanos - startNanos)
            );
        }

        public String intermediateSummary() {
            return String.format("%,d/%,d passes, %,d failures, %,d exceptions",
                    passes.get(), (long) workerCount * operationsPerWorker, failures.get(), exceptions.size());
        }
    }
}
