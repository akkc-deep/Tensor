package com.akkc.tensor.core.download;

import com.akkc.tensor.plugin.api.error.ErrorCode;
import com.akkc.tensor.plugin.api.error.TensorException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/** A process-wide synchronous execution lease, released only by its executing thread. */
public final class DownloadExecutionSlot {
    private final AtomicReference<Lease> current = new AtomicReference<>();

    public Lease acquire(UUID retryTaskId) {
        Lease lease = new Lease(retryTaskId);
        if (!current.compareAndSet(null, lease)) throw new BusyException();
        return lease;
    }

    public Snapshot snapshot() {
        Lease lease = current.get();
        return new Snapshot(lease != null, lease == null ? null : lease.retryTaskId);
    }

    public record Snapshot(boolean busy, UUID retryTaskId) {}

    public boolean busy() { return current.get() != null; }

    public boolean retrying(UUID taskId) {
        Lease lease = current.get();
        return taskId != null && lease != null && taskId.equals(lease.retryTaskId);
    }

    public final class Lease implements AutoCloseable {
        private final Thread ownerThread = Thread.currentThread();
        private final UUID retryTaskId;
        private Lease(UUID retryTaskId) { this.retryTaskId = retryTaskId; }
        @Override public void close() {
            if (Thread.currentThread() != ownerThread) throw new IllegalStateException("Execution lease belongs to another thread");
            current.compareAndSet(this, null);
        }
    }

    private static final class BusyException extends TensorException {
        private BusyException() { super(ErrorCode.DOWNLOAD_BUSY, "Download execution is busy"); }
    }
}
