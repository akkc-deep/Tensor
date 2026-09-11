package com.akkc.tensor.plugin.tushare.client;

import com.akkc.tensor.plugin.api.download.batch.BatchCallContext;
import com.akkc.tensor.plugin.api.error.ErrorCode;
import com.akkc.tensor.plugin.api.error.TensorException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

final class TushareRequestGate {
    private static final Duration MAX_WAIT = Duration.ofMillis(100);
    private static final long MAX_WAIT_NANOS = MAX_WAIT.toNanos();

    private final long minIntervalNanos;
    private final Clock clock;
    private final LongSupplier nanoTime;
    private final Sleeper sleeper;
    private final ReentrantLock lock = new ReentrantLock();

    private boolean completed;
    private long lastCompletionNanos;

    TushareRequestGate(Duration minInterval, Clock clock, LongSupplier nanoTime, Sleeper sleeper) {
        Duration checkedInterval = Objects.requireNonNull(minInterval, "minInterval");
        if (checkedInterval.isNegative()) {
            throw new IllegalArgumentException("minInterval must be non-negative");
        }
        this.minIntervalNanos = checkedInterval.toNanos();
        this.clock = Objects.requireNonNull(clock, "clock");
        this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime");
        this.sleeper = Objects.requireNonNull(sleeper, "sleeper");
    }

    <T> T execute(BatchCallContext context, Supplier<T> operation) {
        BatchCallContext checkedContext = Objects.requireNonNull(context, "context");
        Objects.requireNonNull(checkedContext.deadline(), "context.deadline()");
        Supplier<T> checkedOperation = Objects.requireNonNull(operation, "operation");
        checkedContext.beforeRequest();
        check(checkedContext, clock);

        boolean acquired = false;
        boolean operationStarted = false;
        try {
            acquire(checkedContext);
            acquired = true;
            waitForInterval(checkedContext);
            check(checkedContext, clock);
            operationStarted = true;
            return checkedOperation.get();
        } finally {
            if (acquired) {
                try {
                    if (operationStarted) {
                        lastCompletionNanos = nanoTime.getAsLong();
                        completed = true;
                    }
                } finally {
                    lock.unlock();
                }
            }
        }
    }

    Clock clock() {
        return clock;
    }

    static void check(BatchCallContext context, Clock clock) {
        BatchCallContext checkedContext = Objects.requireNonNull(context, "context");
        Clock checkedClock = Objects.requireNonNull(clock, "clock");
        Instant deadline = Objects.requireNonNull(checkedContext.deadline(), "context.deadline()");
        if (checkedContext.stopRequested() || Thread.currentThread().isInterrupted()) {
            throw failure(ErrorCode.EXECUTION_INTERRUPTED);
        }
        if (!checkedClock.instant().isBefore(deadline)) {
            throw failure(ErrorCode.TASK_LIMIT_EXCEEDED);
        }
    }

    static TensorException failure(ErrorCode code) {
        return switch (Objects.requireNonNull(code, "code")) {
            case EXECUTION_INTERRUPTED ->
                    new ControlException(code, "Download task execution was interrupted");
            case TASK_LIMIT_EXCEEDED -> new ControlException(code, "Download task limit exceeded");
            default -> throw new IllegalArgumentException("code must identify a request control failure");
        };
    }

    private void acquire(BatchCallContext context) {
        while (!lock.tryLock()) {
            check(context, clock);
            try {
                if (lock.tryLock(waitNanosUntilDeadline(context), TimeUnit.NANOSECONDS)) {
                    return;
                }
            } catch (InterruptedException exception) {
                throw interrupted();
            }
        }
    }

    private void waitForInterval(BatchCallContext context) {
        while (completed) {
            check(context, clock);
            long elapsedNanos = nanoTime.getAsLong() - lastCompletionNanos;
            if (elapsedNanos >= minIntervalNanos) {
                return;
            }
            long intervalWaitNanos = minIntervalNanos - elapsedNanos;
            long waitNanos = Math.min(intervalWaitNanos, waitNanosUntilDeadline(context));
            try {
                sleeper.sleep(Duration.ofNanos(waitNanos));
            } catch (InterruptedException exception) {
                throw interrupted();
            }
        }
    }

    private long waitNanosUntilDeadline(BatchCallContext context) {
        Duration remaining = Duration.between(clock.instant(), context.deadline());
        if (remaining.isNegative() || remaining.isZero()) {
            throw failure(ErrorCode.TASK_LIMIT_EXCEEDED);
        }
        return remaining.compareTo(MAX_WAIT) >= 0 ? MAX_WAIT_NANOS : remaining.toNanos();
    }

    private static TensorException interrupted() {
        Thread.currentThread().interrupt();
        return failure(ErrorCode.EXECUTION_INTERRUPTED);
    }

    @FunctionalInterface
    interface Sleeper {
        void sleep(Duration duration) throws InterruptedException;
    }

    private static final class ControlException extends TensorException {
        private ControlException(ErrorCode code, String message) {
            super(code, message);
        }
    }
}
