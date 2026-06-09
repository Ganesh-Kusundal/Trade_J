package com.tradej.core.domain.concurrency;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Provides structured concurrency utilities for the Trade-J platform.
 * <p>
 * Uses virtual threads when available on Java 21+, falling back to
 * platform threads on earlier runtimes. Virtual thread availability is
 * detected at runtime via reflection so the class compiles without
 * {@code --enable-preview}.
 * <p>
 * This class is Spring-free and can be used in the core module.
 */
public final class StructuredConcurrencySupport {

    private static final Logger log = LoggerFactory.getLogger(StructuredConcurrencySupport.class);

    private static final boolean VIRTUAL_THREADS_AVAILABLE;

    static {
        boolean available;
        try {
            var method = Executors.class.getMethod("newVirtualThreadPerTaskExecutor");
            available = method != null;
        } catch (NoSuchMethodException | SecurityException e) {
            available = false;
        }
        VIRTUAL_THREADS_AVAILABLE = available;
        log.info("Virtual threads available: {}", available);
    }

    private StructuredConcurrencySupport() {
    }

    /**
     * Returns {@code true} if virtual threads are available (Java 21+).
     */
    public static boolean isVirtualThreadsAvailable() {
        return VIRTUAL_THREADS_AVAILABLE;
    }

    /**
     * Creates an executor service appropriate for the current runtime.
     * <p>
     * Returns a virtual-thread-per-task executor on Java 21+, otherwise a
     * cached thread pool backed by platform threads.
     */
    public static ExecutorService createExecutor() {
        if (VIRTUAL_THREADS_AVAILABLE) {
            return createVirtualThreadExecutor();
        }
        return Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Creates an executor service appropriate for the current runtime.
     * <p>
     * On Java 21+, returns a virtual-thread-per-task executor. Otherwise
     * returns a fixed thread pool sized to {@code maxConcurrency}.
     *
     * @param maxConcurrency the maximum number of concurrent platform threads (used as fallback)
     */
    public static ExecutorService createExecutor(int maxConcurrency) {
        if (VIRTUAL_THREADS_AVAILABLE) {
            return createVirtualThreadExecutor();
        }
        return Executors.newFixedThreadPool(maxConcurrency, r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Runs multiple callable tasks concurrently and returns their results.
     * <p>
     * Tasks are executed using virtual threads when available. The method blocks
     * until all tasks complete or the first exception is encountered.
     *
     * @param tasks the list of tasks to run concurrently
     * @param <T>   the result type
     * @return a list of results in the same order as the input tasks
     * @throws ExecutionException   if any task throws an exception
     * @throws InterruptedException if the current thread is interrupted while waiting
     */
    public static <T> List<T> runConcurrently(List<Callable<T>> tasks) throws ExecutionException, InterruptedException {
        if (tasks == null || tasks.isEmpty()) {
            return List.of();
        }

        ExecutorService executor = createExecutor();
        try {
            List<Future<T>> futures = new ArrayList<>(tasks.size());
            for (Callable<T> task : tasks) {
                futures.add(executor.submit(task));
            }

            List<T> results = new ArrayList<>(futures.size());
            for (Future<T> future : futures) {
                results.add(future.get());
            }
            return results;
        } finally {
            executor.shutdown();
        }
    }

    /**
     * Runs a single callable task with a timeout.
     * <p>
     * Returns an empty Optional if the task times out.
     *
     * @param task    the task to execute
     * @param timeout the maximum time to wait
     * @param <T>     the result type
     * @return an Optional containing the result, or empty if the task times out
     * @throws ExecutionException   if the task throws an exception
     * @throws InterruptedException if the current thread is interrupted while waiting
     */
    public static <T> Optional<T> runWithTimeout(Callable<T> task, Duration timeout) throws ExecutionException, InterruptedException {
        if (task == null) {
            return Optional.empty();
        }

        ExecutorService executor = createExecutor();
        try {
            Future<T> future = executor.submit(task);
            T result = future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            return Optional.ofNullable(result);
        } catch (TimeoutException e) {
            return Optional.empty();
        } finally {
            executor.shutdown();
        }
    }

    private static ExecutorService createVirtualThreadExecutor() {
        try {
            return (ExecutorService) Executors.class.getMethod("newVirtualThreadPerTaskExecutor").invoke(null);
        } catch (Exception e) {
            log.warn("Failed to create virtual thread executor, falling back to cached pool", e);
            return Executors.newCachedThreadPool(r -> {
                Thread t = new Thread(r);
                t.setDaemon(true);
                return t;
            });
        }
    }
}
