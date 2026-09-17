package com.akkc.tensor.core.integrity;

import static com.akkc.tensor.core.integrity.IntegrityReadException.SCAN_LIMIT_EXCEEDED;
import static com.akkc.tensor.core.integrity.IntegrityReadException.UNIT_TIME_BUDGET_EXHAUSTED;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.function.BooleanSupplier;

public final class IntegrityReadBudget {
    private final long maxItems;
    private final Instant deadline;
    private final Clock clock;
    private final BooleanSupplier stopping;
    private long consumed;
    private IntegrityReadException failure;

    public IntegrityReadBudget(long maxItems, Instant deadline, Clock clock) {
        this(maxItems, deadline, clock, () -> false);
    }

    public IntegrityReadBudget(long maxItems, Instant deadline, Clock clock, BooleanSupplier stopping) {
        this.stopping = Objects.requireNonNull(stopping, "stopping");
        if (maxItems <= 0) throw new IllegalArgumentException("maxItems must be positive");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.deadline = Objects.requireNonNull(deadline, "deadline");
        if (deadline.isBefore(clock.instant())) throw new IllegalArgumentException("deadline has passed");
        this.maxItems = maxItems;
    }

    public synchronized void consume(long items) {
        if (items < 0) throw new IllegalArgumentException("items must not be negative");
        check();
        if (items > maxItems - consumed) {
            failure = new IntegrityReadException(SCAN_LIMIT_EXCEEDED, "Integrity scan item limit exceeded");
            throw failure;
        }
        consumed += items;
    }

    public synchronized void check() {
        if (failure != null) throw failure;
        if (stopping.getAsBoolean() || Thread.currentThread().isInterrupted()) {
            failure = new IntegrityReadException("EXECUTION_INTERRUPTED", "Integrity execution interrupted");
            throw failure;
        }
        if (!clock.instant().isBefore(deadline)) {
            failure = new IntegrityReadException(
                    UNIT_TIME_BUDGET_EXHAUSTED, "Integrity unit time budget exhausted");
            throw failure;
        }
    }

    public synchronized long remainingItems() {
        return maxItems - consumed;
    }

    public synchronized Duration remainingTime() {
        Duration remaining = Duration.between(clock.instant(), deadline);
        return remaining.isNegative() ? Duration.ZERO : remaining;
    }
}
