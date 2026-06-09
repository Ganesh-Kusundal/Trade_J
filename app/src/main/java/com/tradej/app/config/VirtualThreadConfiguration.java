package com.tradej.app.config;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.lang.reflect.Method;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Configuration
@ConditionalOnProperty(name = "trade.virtual-threads.enabled", havingValue = "true", matchIfMissing = true)
class VirtualThreadConfiguration {

    private static final Logger log = LoggerFactory.getLogger(VirtualThreadConfiguration.class);

    @Bean(destroyMethod = "shutdown")
    ExecutorService virtualThreadExecutor(TradingProperties properties) {
        int maxConcurrency = properties.virtualThreads().maxConcurrency();
        log.info("Creating virtual thread executor with maxConcurrency={}", maxConcurrency);

        return new VirtualThreadExecutorService(maxConcurrency);
    }

    @Bean
    @ConditionalOnBean(MeterRegistry.class)
    Runnable virtualThreadMetrics(MeterRegistry registry) {
        Runnable metrics = () -> {
            java.lang.management.ThreadMXBean threadBean = java.lang.management.ManagementFactory.getThreadMXBean();
            long threadCount = threadBean.getThreadCount();
            long peakThreadCount = threadBean.getPeakThreadCount();

            Gauge.builder("jvm.threads.count", () -> (double) threadCount)
                    .description("Current number of live threads")
                    .register(registry);

            Gauge.builder("jvm.threads.peak.count", () -> (double) peakThreadCount)
                    .description("Peak number of live threads")
                    .register(registry);
        };
        return metrics;
    }

    private static class VirtualThreadExecutorService implements ExecutorService {
        private final ExecutorService delegate;
        private final int maxConcurrency;

        VirtualThreadExecutorService(int maxConcurrency) {
            this.maxConcurrency = maxConcurrency;
            this.delegate = createDelegate();
        }

        private static ExecutorService createDelegate() {
            try {
                Method method = Executors.class.getMethod("newVirtualThreadPerTaskExecutor");
                return (ExecutorService) method.invoke(null);
            } catch (Exception e) {
                log.warn("Virtual threads not available, falling back to cached thread pool");
                return Executors.newCachedThreadPool(r -> {
                    Thread t = new Thread(r);
                    t.setDaemon(true);
                    return t;
                });
            }
        }

        @Override
        public void execute(Runnable command) {
            delegate.execute(command);
        }

        @Override
        public Future<?> submit(Runnable task) {
            return delegate.submit(task);
        }

        @Override
        public <T> Future<T> submit(Callable<T> task) {
            return delegate.submit(task);
        }

        @Override
        public <T> Future<T> submit(Runnable task, T result) {
            return delegate.submit(task, result);
        }

        @Override
        public <T> java.util.List<Future<T>> invokeAll(java.util.Collection<? extends Callable<T>> tasks) throws InterruptedException {
            return delegate.invokeAll(tasks);
        }

        @Override
        public <T> java.util.List<Future<T>> invokeAll(java.util.Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException {
            return delegate.invokeAll(tasks, timeout, unit);
        }

        @Override
        public <T> T invokeAny(java.util.Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
            return delegate.invokeAny(tasks);
        }

        @Override
        public <T> T invokeAny(java.util.Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
            return delegate.invokeAny(tasks, timeout, unit);
        }

        @Override
        public void shutdown() {
            delegate.shutdown();
        }

        @Override
        public java.util.List<Runnable> shutdownNow() {
            return delegate.shutdownNow();
        }

        @Override
        public boolean isShutdown() {
            return delegate.isShutdown();
        }

        @Override
        public boolean isTerminated() {
            return delegate.isTerminated();
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
            return delegate.awaitTermination(timeout, unit);
        }
    }
}
