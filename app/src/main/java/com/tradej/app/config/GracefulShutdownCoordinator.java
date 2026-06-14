package com.tradej.app.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Coordinates graceful shutdown: drains event buses, persists pending state,
 * and ensures no data loss during SIGTERM.
 */
@Component
public class GracefulShutdownCoordinator implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(GracefulShutdownCoordinator.class);

    private final Duration shutdownTimeout;
    private final List<ShutdownHook> hooks = new CopyOnWriteArrayList<>();
    private volatile boolean running = true;

    public GracefulShutdownCoordinator() {
        this(Duration.ofSeconds(30));
    }

    public GracefulShutdownCoordinator(Duration shutdownTimeout) {
        this.shutdownTimeout = shutdownTimeout;
    }

    /**
     * Register a shutdown hook to be called during graceful shutdown.
     */
    public void register(ShutdownHook hook) {
        hooks.add(hook);
    }

    @EventListener(ContextClosedEvent.class)
    public void onApplicationShutdown(ContextClosedEvent event) {
        log.info("Graceful shutdown initiated — {} hooks registered, timeout={}s",
                hooks.size(), shutdownTimeout.toSeconds());

        Instant deadline = Instant.now().plus(shutdownTimeout);

        for (ShutdownHook hook : hooks) {
            if (Instant.now().isAfter(deadline)) {
                log.warn("Shutdown deadline exceeded — skipping remaining hooks");
                break;
            }

            Instant start = Instant.now();
            try {
                log.info("Executing shutdown hook: {}", hook.name());
                hook.shutdown(Duration.between(Instant.now(), deadline));
                log.info("Shutdown hook '{}' completed in {}ms",
                        hook.name(), Duration.between(start, Instant.now()).toMillis());
            } catch (Exception e) {
                log.error("Shutdown hook '{}' failed: {}", hook.name(), e.getMessage(), e);
            }
        }

        running = false;
        log.info("Graceful shutdown complete");
    }

    @Override
    public void start() { running = true; }

    @Override
    public void stop() { /* handled by event listener */ }

    @Override
    public boolean isRunning() { return running; }

    @Override
    public int getPhase() { return Integer.MAX_VALUE; }

    /**
     * Interface for components that need to perform cleanup during shutdown.
     */
    @FunctionalInterface
    public interface ShutdownHook {
        void shutdown(Duration remaining);

        default String name() {
            return getClass().getSimpleName();
        }
    }
}
