package com.akkc.tensor.core.integrity;

import com.akkc.tensor.plugin.api.integrity.IntegrityTaskStatus;
import java.time.*;
import java.util.*;

/** One process-local consumer. Old plans are interrupted, never replayed against a new snapshot. */
public final class IntegrityCheckCoordinator implements AutoCloseable {
    private static final System.Logger LOG = System.getLogger(IntegrityCheckCoordinator.class.getName());
    private final IntegrityCheckQueue queue;
    private final IntegrityCheckRunner runner;
    private final IntegrityCheckRepository repository;
    private final IntegrityCheckService service;
    private final Clock clock;
    private final Object lock = new Object();
    private volatile boolean stopping;
    private boolean attempted, polling;
    private Thread worker;

    public IntegrityCheckCoordinator(IntegrityCheckQueue queue, IntegrityCheckRunner runner,
            IntegrityCheckRepository repository, IntegrityCheckService service, Clock clock) {
        this.queue = Objects.requireNonNull(queue); this.runner = Objects.requireNonNull(runner);
        this.repository = Objects.requireNonNull(repository); this.service = Objects.requireNonNull(service);
        this.clock = Objects.requireNonNull(clock);
    }

    public void start() {
        synchronized (lock) {
            if (stopping) throw new IllegalStateException("Integrity coordinator cannot restart");
            if (worker != null && worker.isAlive()) return;
            if (attempted) throw new IllegalStateException("Integrity coordinator cannot restart");
            attempted = true;
            service.stopAccepting();
            repository.interruptUnfinished(clock.instant());
            worker = new Thread(this::consume, "tensor-integrity-worker");
            worker.start();
            service.startAccepting();
        }
    }

    public boolean isRunning() {
        synchronized (lock) { return worker != null && worker.isAlive(); }
    }

    private void consume() {
        try {
            while (!stopping) {
                Optional<UUID> next;
                try {
                    synchronized (lock) {
                        if (stopping) break;
                        polling = true;
                    }
                    try { next = queue.poll(Duration.ofSeconds(1)); }
                    finally { synchronized (lock) { polling = false; } }
                } catch (InterruptedException interrupted) { continue; }
                if (next.isEmpty()) continue;
                if (stopping) { interrupt(next.get()); break; }
                try { runner.run(next.get(), () -> stopping); }
                catch (RuntimeException failure) {
                    LOG.log(System.Logger.Level.ERROR, "Integrity task execution failed");
                }
            }
        } finally {
            // Clear poll wakeup before JDBC cleanup; cancellation of active work uses the supplier.
            Thread.interrupted();
            finish();
        }
    }

    @Override public void close() {
        Thread thread;
        synchronized (lock) {
            service.stopAccepting(); // Wait for every in-flight create/publish before draining.
            stopping = true;
            thread = worker;
            if (thread != null && polling) thread.interrupt();
        }
        if (thread == null) { finish(); return; }
        if (thread == Thread.currentThread()) return;
        boolean interrupted = false;
        while (thread.isAlive()) {
            try { thread.join(); }
            catch (InterruptedException ignored) { interrupted = true; }
        }
        if (interrupted) Thread.currentThread().interrupt();
    }

    private void finish() {
        try {
            Optional<UUID> next;
            while ((next = queue.poll(Duration.ZERO)).isPresent()) interrupt(next.get());
            repository.interruptUnfinished(clock.instant());
        } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
        catch (RuntimeException unavailable) {
            LOG.log(System.Logger.Level.ERROR, "Integrity shutdown state could not be saved");
        }
    }
    private void interrupt(UUID checkId) {
        try { repository.terminate(checkId, IntegrityTaskStatus.INTERRUPTED, "EXECUTION_INTERRUPTED", clock.instant()); }
        catch (RuntimeException unavailable) {
            LOG.log(System.Logger.Level.ERROR, "Integrity queued task interruption could not be saved");
        }
    }
}
