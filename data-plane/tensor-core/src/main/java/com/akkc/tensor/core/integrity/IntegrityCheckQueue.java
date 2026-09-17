package com.akkc.tensor.core.integrity;

import com.akkc.tensor.plugin.api.error.ErrorCode;
import com.akkc.tensor.plugin.api.error.TensorException;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/** Reservations cover both uncommitted admissions and queued checks, but not running checks. */
public final class IntegrityCheckQueue {
    private final ArrayBlockingQueue<UUID> queue;
    private final Semaphore slots;

    public IntegrityCheckQueue(int capacity) {
        if (capacity <= 0) throw new IllegalArgumentException("Queue capacity must be positive");
        queue = new ArrayBlockingQueue<>(capacity);
        slots = new Semaphore(capacity);
    }

    public Reservation reserve() {
        if (!slots.tryAcquire()) throw new QueueFullException();
        return new Reservation();
    }

    public Optional<UUID> poll(Duration timeout) throws InterruptedException {
        if (timeout.isNegative()) throw new IllegalArgumentException("Negative queue timeout");
        var id = queue.poll(timeout.toNanos(), TimeUnit.NANOSECONDS);
        if (id != null) slots.release();
        return Optional.ofNullable(id);
    }

    public final class Reservation implements AutoCloseable {
        private boolean open = true;
        private Reservation() {}

        public synchronized void publish(UUID checkId) {
            if (!open) throw new IllegalStateException("Reservation is already closed or published");
            // Every queued entry holds a permit, so this reserved entry always fits.
            queue.add(Objects.requireNonNull(checkId, "checkId"));
            open = false;
        }

        @Override public synchronized void close() {
            if (open) { open = false; slots.release(); }
        }
    }

    private static final class QueueFullException extends TensorException {
        QueueFullException() { super(ErrorCode.INTEGRITY_QUEUE_FULL, ErrorCode.INTEGRITY_QUEUE_FULL.message()); }
    }
}
