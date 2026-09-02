package com.akkc.tensor.core.persistence;

import com.akkc.tensor.plugin.api.model.DatasetKey;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public final class DatasetLockManager {
    private static final String ACQUIRED_MESSAGE = "Lock handle is already acquired";

    private final ConcurrentHashMap<DatasetKey, LockEntry> locks = new ConcurrentHashMap<>();

    public Lock acquire(DatasetKey datasetKey) {
        Objects.requireNonNull(datasetKey, "datasetKey");
        LockEntry entry = locks.compute(datasetKey, (key, current) -> {
            LockEntry selected = current == null ? new LockEntry() : current;
            selected.references++;
            return selected;
        });
        boolean acquired = false;
        try {
            entry.lock.lock();
            acquired = true;
            return new LockHandle(datasetKey, entry);
        } catch (RuntimeException | Error failure) {
            if (acquired) {
                entry.lock.unlock();
            }
            releaseReference(datasetKey, entry);
            throw failure;
        }
    }

    private void releaseReference(DatasetKey datasetKey, LockEntry entry) {
        locks.computeIfPresent(datasetKey, (key, current) -> {
            if (current != entry) {
                return current;
            }
            current.references--;
            return current.references == 0 ? null : current;
        });
    }

    private static final class LockEntry {
        private final ReentrantLock lock = new ReentrantLock(true);
        private int references;
    }

    private final class LockHandle implements Lock {
        private final DatasetKey datasetKey;
        private final LockEntry entry;
        private boolean released;

        private LockHandle(DatasetKey datasetKey, LockEntry entry) {
            this.datasetKey = datasetKey;
            this.entry = entry;
        }

        @Override
        public void lock() {
            throw new UnsupportedOperationException(ACQUIRED_MESSAGE);
        }

        @Override
        public void lockInterruptibly() {
            throw new UnsupportedOperationException(ACQUIRED_MESSAGE);
        }

        @Override
        public boolean tryLock() {
            throw new UnsupportedOperationException(ACQUIRED_MESSAGE);
        }

        @Override
        public boolean tryLock(long time, TimeUnit unit) {
            throw new UnsupportedOperationException(ACQUIRED_MESSAGE);
        }

        @Override
        public void unlock() {
            if (released) {
                throw new IllegalStateException("Lock handle already released");
            }
            entry.lock.unlock();
            released = true;
            releaseReference(datasetKey, entry);
        }

        @Override
        public Condition newCondition() {
            throw new UnsupportedOperationException(ACQUIRED_MESSAGE);
        }
    }
}
