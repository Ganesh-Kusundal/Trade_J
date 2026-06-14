package com.tradej.broker.upstox.reconciliation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Periodically re-runs {@link UpstoxReconciliationService#reconcile} and
 * forwards drift items to a consumer (e.g. a logger, a health metric, or
 * an OMS repair hook).
 * <p>
 * Designed to be a long-lived component on the broker connection. The
 * composition layer owns the lifecycle: typically one instance per
 * {@code UpstoxBrokerConnection} with a configurable cadence (default 5
 * minutes) and an {@code OmsSnapshot} supplier that captures the current
 * OMS state at each tick.
 * <p>
 * <h2>Usage</h2>
 * <pre>{@code
 *   var scheduler = new UpstoxReconciliationScheduler(
 *           reconciliationService,
 *           oms::snapshot,
 *           drift -> health.recordDrift(drift),
 *           Duration.ofMinutes(5));
 *   scheduler.start();
 *   // ...
 *   scheduler.stop();
 * }</pre>
 */
public final class UpstoxReconciliationScheduler {

    private static final Logger log = LoggerFactory.getLogger(UpstoxReconciliationScheduler.class);

    private final UpstoxReconciliationService service;
    private final java.util.function.Supplier<UpstoxReconciliationService.OmsSnapshot> snapshotSupplier;
    private final Consumer<UpstoxReconciliationService.DriftItem> driftHandler;
    private final java.time.Duration cadence;
    private final java.time.Duration initialDelay;
    private final String sourceLabel;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final ScheduledExecutorService executor;
    private ScheduledFuture<?> currentTask;

    public UpstoxReconciliationScheduler(
            UpstoxReconciliationService service,
            java.util.function.Supplier<UpstoxReconciliationService.OmsSnapshot> snapshotSupplier,
            Consumer<UpstoxReconciliationService.DriftItem> driftHandler,
            java.time.Duration cadence,
            java.time.Duration initialDelay,
            String sourceLabel) {
        this.service = Objects.requireNonNull(service, "service");
        this.snapshotSupplier = Objects.requireNonNull(snapshotSupplier, "snapshotSupplier");
        this.driftHandler = Objects.requireNonNull(driftHandler, "driftHandler");
        this.cadence = Objects.requireNonNull(cadence, "cadence");
        this.initialDelay = Objects.requireNonNull(initialDelay, "initialDelay");
        this.sourceLabel = Objects.requireNonNull(sourceLabel, "sourceLabel");
        this.executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "upstox-recon-" + sourceLabel);
            t.setDaemon(true);
            return t;
        });
    }

    public UpstoxReconciliationScheduler(
            UpstoxReconciliationService service,
            java.util.function.Supplier<UpstoxReconciliationService.OmsSnapshot> snapshotSupplier,
            Consumer<UpstoxReconciliationService.DriftItem> driftHandler,
            java.time.Duration cadence) {
        this(service, snapshotSupplier, driftHandler, cadence, java.time.Duration.ZERO, "upstox");
    }

    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        currentTask = executor.scheduleAtFixedRate(
                this::tick,
                initialDelay.toMillis(),
                cadence.toMillis(),
                TimeUnit.MILLISECONDS);
        log.info("Upstox reconciliation scheduler[{}] started: cadence={}ms, initialDelay={}ms",
                sourceLabel, cadence.toMillis(), initialDelay.toMillis());
    }

    public void stop() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        if (currentTask != null) {
            currentTask.cancel(false);
            currentTask = null;
        }
        executor.shutdownNow();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                log.warn("Upstox reconciliation scheduler[{}] did not terminate in 5s", sourceLabel);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        log.info("Upstox reconciliation scheduler[{}] stopped", sourceLabel);
    }

    public boolean isRunning() {
        return running.get();
    }

    private void tick() {
        try {
            var snapshot = snapshotSupplier.get();
            var report = service.reconcile(snapshot);
            if (report.hasDrift()) {
                for (var drift : report.driftItems()) {
                    try {
                        driftHandler.accept(drift);
                    } catch (Exception e) {
                        log.warn("drift handler threw for {}: {}", drift.kind(), e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("reconciliation tick failed for {}: {}", sourceLabel, e.getMessage());
        }
    }
}
