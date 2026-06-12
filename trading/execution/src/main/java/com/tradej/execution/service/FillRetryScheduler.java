package com.tradej.execution.service;

import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.event.EventMetadata;
import com.tradej.core.domain.event.OrderFilled;
import com.tradej.core.domain.model.Order;
import com.tradej.core.domain.model.Trade;
import com.tradej.core.domain.port.DeadLetterQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;

/**
 * Schedules and reschedules fill events when the partition queue is full.
 * Unifies the previously duplicated retry paths for both {@link OrderFilled}
 * and broker-callback fills.
 */
final class FillRetryScheduler {

    private static final Logger log = LoggerFactory.getLogger(FillRetryScheduler.class);

    private final ScheduledExecutorService fillDeferExecutor;
    private final DeadLetterQueue deadLetterQueue;
    private final int maxDeferAttempts;
    private final long deferDelayMs;
    private final IntSupplier attemptBudget;
    private final IntFunction<ExecutionCommand> commandFactory;
    private final IntFunction<Runnable> retryRunnableFactory;
    private final Runnable exhaustCallback;

    private FillRetryScheduler(Builder b) {
        this.fillDeferExecutor = b.fillDeferExecutor;
        this.deadLetterQueue = b.deadLetterQueue;
        this.maxDeferAttempts = b.maxDeferAttempts;
        this.deferDelayMs = b.deferDelayMs;
        this.attemptBudget = b.attemptBudget;
        this.commandFactory = b.commandFactory;
        this.retryRunnableFactory = b.retryRunnableFactory;
        this.exhaustCallback = b.exhaustCallback;
    }

    /**
     * Attempts to enqueue the command directly. If the queue is full, schedules
     * a delayed retry up to {@code maxDeferAttempts} times.
     *
     * @param nextAttempt    current attempt count (0 for first try)
     * @param partitionQueue the partition queue to offer into
     * @param attemptCommand command factory that takes the attempt number and
     *                       produces the command to enqueue on the next attempt
     * @return {@code true} if the command was enqueued (or scheduled), {@code false}
     *         if attempts were exhausted and the event was dead-lettered
     */
    boolean offerOrRetry(int nextAttempt,
                         java.util.concurrent.BlockingQueue<ExecutionCommand> partitionQueue,
                         IntFunction<ExecutionCommand> attemptCommand) {
        if (partitionQueue.offer(attemptCommand.apply(nextAttempt))) {
            return true;
        }
        if (nextAttempt >= maxDeferAttempts) {
            exhaustCallback.run();
            return false;
        }
        int scheduledAttempt = nextAttempt + 1;
        fillDeferExecutor.schedule(() -> {
            offerOrRetry(scheduledAttempt, partitionQueue, attemptCommand);
        }, deferDelayMs, TimeUnit.MILLISECONDS);
        return true;
    }

    /** Builds commands. */
    public interface ExecutionCommand {
    }

    /** Static factory for an OMS-originated fill command. */
    static ExecutionCommand fillCommand(OrderFilled orderFilled, Consumer<DomainEvent> downstream, int deferAttempts) {
        return new SignalCommandAdapter(orderFilled, downstream, deferAttempts);
    }

    /** Adapter that records the attempt count and forwards the event downstream. */
    private record SignalCommandAdapter(
            OrderFilled orderFilled,
            Consumer<DomainEvent> downstream,
            int deferAttempts
    ) implements ExecutionCommand {
        @Override
        public String toString() {
            return "SignalCommandAdapter{orderId=" + orderFilled.order().orderId() + ", attempts=" + deferAttempts + "}";
        }
    }

    static final class Builder {
        private final ScheduledExecutorService fillDeferExecutor;
        private final DeadLetterQueue deadLetterQueue;
        private int maxDeferAttempts;
        private long deferDelayMs;
        private IntSupplier attemptBudget;
        private IntFunction<ExecutionCommand> commandFactory;
        private IntFunction<Runnable> retryRunnableFactory;
        private Runnable exhaustCallback;

        Builder(ScheduledExecutorService fillDeferExecutor, DeadLetterQueue deadLetterQueue) {
            this.fillDeferExecutor = fillDeferExecutor;
            this.deadLetterQueue = deadLetterQueue;
        }

        Builder withMaxAttempts(int n) { this.maxDeferAttempts = n; return this; }
        Builder withDelayMs(long ms) { this.deferDelayMs = ms; return this; }
        Builder onExhaust(Runnable r) { this.exhaustCallback = r; return this; }

        FillRetryScheduler build() {
            return new FillRetryScheduler(this);
        }
    }
}
