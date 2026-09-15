package com.akkc.tensor.plugin.api.download.batch;

import java.time.Instant;

/** Per-run call permission shared by planning and downloading. */
public interface BatchCallContext {
    /** Non-null UTC deadline. Waiting and network timeouts must not exceed the remaining time. */
    Instant deadline();

    /** Must also be checked while waiting for a request slot or upstream completion. */
    boolean stopRequested();

    /**
     * Checks stop/deadline and reserves one request from the run's budget, atomically.
     * Call once before each actual upstream request, before throttling and sending it.
     * Denial throws a classified TensorException (TASK_LIMIT_EXCEEDED or EXECUTION_INTERRUPTED).
     * A reserved request remains counted when subsequent waiting or sending fails.
     */
    void beforeRequest();
}
