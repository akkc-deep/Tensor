package com.akkc.tensor.plugin.tushare.client;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.akkc.tensor.plugin.api.download.batch.BatchCallContext;
import com.akkc.tensor.plugin.api.error.ErrorCode;
import com.akkc.tensor.plugin.api.error.TensorException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class TushareRequestGateTest {
    @Test
    void reservesBeforeWaitingAndSpacesCallsFromPreviousCompletion() {
        List<String> events = new ArrayList<>();
        List<Long> starts = new ArrayList<>();
        MutableTime time = new MutableTime();
        BatchCallContext context = new TestContext() {
            @Override
            public void beforeRequest() {
                events.add("reserve");
            }
        };
        TushareRequestGate gate = new TushareRequestGate(
                Duration.ofMillis(1500), time, time::nanoTime, duration -> {
                    events.add("wait");
                    time.advance(duration);
                });

        for (int i = 0; i < 3; i++) {
            gate.execute(context, () -> {
                events.add("operation");
                starts.add(time.millis());
                return null;
            });
        }

        assertThat(starts).containsExactly(0L, 1500L, 3000L);
        assertThat(events.subList(0, 3)).containsExactly("reserve", "operation", "reserve");
        assertThat(events.stream().filter(event -> !event.equals("wait")))
                .containsExactly("reserve", "operation", "reserve", "operation", "reserve", "operation");
        assertThat(events).filteredOn("wait"::equals).hasSize(30);
    }

    @Test
    void zeroIntervalAddsNoWait() {
        MutableTime time = new MutableTime();
        AtomicInteger waits = new AtomicInteger();
        TushareRequestGate gate = gate(Duration.ZERO, time, duration -> waits.incrementAndGet());

        gate.execute(new TestContext(), () -> null);
        gate.execute(new TestContext(), () -> null);

        assertThat(waits).hasValue(0);
    }

    @Test
    void validatesBeforeReservationAndPropagatesBudgetFailureBeforeWaiting() {
        MutableTime time = new MutableTime();
        AtomicInteger waits = new AtomicInteger();
        AtomicInteger operations = new AtomicInteger();
        TushareRequestGate gate = gate(Duration.ofSeconds(1), time, duration -> waits.incrementAndGet());
        TestContext context = new TestContext();

        assertThatThrownBy(() -> gate.execute(null, () -> null)).hasMessage("context");
        assertThatThrownBy(() -> gate.execute(context, null)).hasMessage("operation");
        context.deadline = null;
        assertThatThrownBy(() -> gate.execute(context, () -> null)).hasMessage("context.deadline()");
        assertThat(context.reservations).hasValue(0);

        context.deadline = Instant.MAX;
        gate.execute(context, () -> null);
        TestFailure denial = new TestFailure(ErrorCode.TASK_LIMIT_EXCEEDED, "denied");
        context.beforeFailure = denial;
        Throwable thrown = catchThrowable(() -> gate.execute(context, operations::incrementAndGet));

        assertThat(thrown).isSameAs(denial);
        assertThat(context.reservations).hasValue(2);
        assertThat(waits).hasValue(0);
        assertThat(operations).hasValue(0);
    }

    @Test
    void stopInterruptAndDeadlineHaveFixedSafeFailures() {
        MutableTime time = new MutableTime();
        TestContext stopped = new TestContext();
        stopped.stopped = true;
        stopped.deadline = Instant.EPOCH;

        TensorException stopFailure = controlFailure(() -> TushareRequestGate.check(stopped, time));
        TensorException deadlineFailure = controlFailure(
                () -> TushareRequestGate.check(new TestContext(Instant.EPOCH), time));

        assertThat(stopFailure.code()).isEqualTo(ErrorCode.EXECUTION_INTERRUPTED);
        assertThat(stopFailure.getMessage()).isEqualTo("Download task execution was interrupted");
        assertThat(deadlineFailure.code()).isEqualTo(ErrorCode.TASK_LIMIT_EXCEEDED);
        assertThat(deadlineFailure.getMessage()).isEqualTo("Download task limit exceeded");
        assertThat(stopFailure).hasNoCause();
        assertThat(stopFailure.getSuppressed()).isEmpty();
        assertThat(deadlineFailure).hasNoCause();
        assertThat(deadlineFailure.getSuppressed()).isEmpty();

        Thread.currentThread().interrupt();
        try {
            TensorException interruptFailure = controlFailure(
                    () -> TushareRequestGate.check(new TestContext(), time));
            assertThat(interruptFailure.code()).isEqualTo(ErrorCode.EXECUTION_INTERRUPTED);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
        assertThatThrownBy(() -> TushareRequestGate.failure(ErrorCode.SOURCE_TIMEOUT))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reservesThenRejectsDeadlineWithoutRunningOperation() {
        MutableTime time = new MutableTime();
        TestContext context = new TestContext(Instant.EPOCH);
        AtomicInteger operations = new AtomicInteger();

        TensorException failure = controlFailure(
                () -> gate(Duration.ZERO, time, time::advance).execute(context, operations::incrementAndGet));

        assertThat(failure.code()).isEqualTo(ErrorCode.TASK_LIMIT_EXCEEDED);
        assertThat(context.reservations).hasValue(1);
        assertThat(operations).hasValue(0);
    }

    @Test
    void failedOperationStillStartsNextIntervalAtItsCompletion() {
        MutableTime time = new MutableTime();
        TushareRequestGate gate = gate(Duration.ofMillis(1500), time, time::advance);

        assertThatThrownBy(() -> gate.execute(new TestContext(), () -> {
                    time.advance(Duration.ofMillis(200));
                    throw new IllegalStateException("request failed");
                }))
                .isInstanceOf(IllegalStateException.class);
        long nextStart = gate.execute(new TestContext(), time::millis);

        assertThat(nextStart).isEqualTo(1700L);
    }

    @Test
    void throttleControlFailureKeepsPreviousCompletionAndReservation() {
        MutableTime time = new MutableTime();
        TestContext context = new TestContext();
        AtomicBoolean stopOnce = new AtomicBoolean(true);
        TushareRequestGate gate = gate(Duration.ofMillis(1500), time, duration -> {
            time.advance(duration);
            if (stopOnce.getAndSet(false)) {
                context.stopped = true;
            }
        });
        gate.execute(context, () -> null);

        TensorException stopped = controlFailure(() -> gate.execute(context, () -> null));
        context.stopped = false;
        long nextStart = gate.execute(context, time::millis);

        assertThat(stopped.code()).isEqualTo(ErrorCode.EXECUTION_INTERRUPTED);
        assertThat(context.reservations).hasValue(3);
        assertThat(nextStart).isEqualTo(1500L);
    }

    @Test
    void throttleWaitIsBoundedByDeadlineAndNanoWrapUsesSubtraction() {
        MutableTime deadlineTime = new MutableTime();
        List<Duration> waits = new ArrayList<>();
        TushareRequestGate deadlineGate = gate(Duration.ofSeconds(1), deadlineTime, duration -> {
            waits.add(duration);
            deadlineTime.advance(duration);
        });
        TestContext deadlineContext = new TestContext();
        deadlineGate.execute(deadlineContext, () -> null);
        deadlineContext.deadline = Instant.EPOCH.plusMillis(50);

        TensorException failure = controlFailure(() -> deadlineGate.execute(deadlineContext, () -> null));

        assertThat(failure.code()).isEqualTo(ErrorCode.TASK_LIMIT_EXCEEDED);
        assertThat(waits).containsExactly(Duration.ofMillis(50));

        MutableTime wrapTime = new MutableTime();
        wrapTime.monotonicNanos.set(Long.MAX_VALUE - Duration.ofMillis(50).toNanos());
        TushareRequestGate wrapGate = gate(Duration.ofMillis(150), wrapTime, wrapTime::advance);
        wrapGate.execute(new TestContext(), () -> null);
        assertThat(wrapGate.execute(new TestContext(), wrapTime::millis)).isEqualTo(150L);
    }

    @Test
    void concurrentRequestReservesBeforeGateThenWaitsFullIntervalAfterFirstCompletes() throws Exception {
        LockScenario scenario = new LockScenario(Duration.ofMillis(1500));
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maxActive = new AtomicInteger();
        AtomicLong secondStartMillis = new AtomicLong(-1);
        CountDownLatch secondEntered = new CountDownLatch(1);
        try {
            scenario.startFirst(() -> {
                int count = active.incrementAndGet();
                maxActive.accumulateAndGet(count, Math::max);
                scenario.firstEntered.countDown();
                awaitUninterruptibly(scenario.releaseFirst);
                active.decrementAndGet();
            });
            TestContext secondContext = scenario.reservingContext();
            scenario.second = scenario.executor.submit(() -> scenario.gate.execute(secondContext, () -> {
                int count = active.incrementAndGet();
                maxActive.accumulateAndGet(count, Math::max);
                secondStartMillis.set(scenario.time.millis());
                secondEntered.countDown();
                active.decrementAndGet();
                return null;
            }));

            await(scenario.secondReserved);
            assertThat(secondEntered.getCount()).isOne();
            scenario.releaseFirst.countDown();
            scenario.first.get(2, SECONDS);
            scenario.second.get(2, SECONDS);
            assertThat(maxActive).hasValue(1);
            assertThat(secondContext.reservations).hasValue(1);
            assertThat(secondStartMillis).hasValue(1500L);
        } finally {
            scenario.close();
        }
    }

    @Test
    void stopAndDeadlineAreObservedWhileWaitingForLock() throws Exception {
        assertLockWaitFailure(true, ErrorCode.EXECUTION_INTERRUPTED);
        assertLockWaitFailure(false, ErrorCode.TASK_LIMIT_EXCEEDED);
    }

    @Test
    void interruptWhileWaitingForLockRestoresFlagAndSkipsReservedOperation() throws Exception {
        LockScenario scenario = new LockScenario();
        TestContext waiting = scenario.reservingContext();
        AtomicInteger operations = new AtomicInteger();
        AtomicReference<Throwable> thrown = new AtomicReference<>();
        AtomicBoolean restored = new AtomicBoolean();
        Thread caller = new Thread(() -> {
            try {
                scenario.gate.execute(waiting, operations::incrementAndGet);
            } catch (Throwable failure) {
                thrown.set(failure);
                restored.set(Thread.currentThread().isInterrupted());
            }
        });
        try {
            scenario.startFirst(() -> {
                scenario.firstEntered.countDown();
                awaitUninterruptibly(scenario.releaseFirst);
            });
            caller.start();
            await(scenario.secondReserved);

            caller.interrupt();
            caller.join(2000);

            assertThat(caller.isAlive()).isFalse();
            assertThat(thrown.get()).isInstanceOf(TensorException.class);
            assertThat(((TensorException) thrown.get()).code()).isEqualTo(ErrorCode.EXECUTION_INTERRUPTED);
            assertThat(restored).isTrue();
            assertThat(waiting.reservations).hasValue(1);
            assertThat(operations).hasValue(0);
        } finally {
            caller.interrupt();
            caller.join(2000);
            scenario.close();
        }
    }

    @Test
    void interruptDuringThrottleRestoresFlagAndSkipsOperation() throws Exception {
        MutableTime time = new MutableTime();
        CountDownLatch sleeping = new CountDownLatch(1);
        CountDownLatch block = new CountDownLatch(1);
        TushareRequestGate gate = gate(Duration.ofSeconds(1), time, duration -> {
            sleeping.countDown();
            block.await();
        });
        gate.execute(new TestContext(), () -> null);
        AtomicReference<Throwable> thrown = new AtomicReference<>();
        AtomicBoolean restored = new AtomicBoolean();
        AtomicBoolean operationCalled = new AtomicBoolean();
        Thread caller = new Thread(() -> {
            try {
                gate.execute(new TestContext(), () -> {
                    operationCalled.set(true);
                    return null;
                });
            } catch (Throwable failure) {
                thrown.set(failure);
                restored.set(Thread.currentThread().isInterrupted());
            }
        });
        try {
            caller.start();
            await(sleeping);
            caller.interrupt();
            caller.join(2000);

            assertThat(caller.isAlive()).isFalse();
            assertThat(thrown.get()).isInstanceOf(TensorException.class);
            assertThat(((TensorException) thrown.get()).code()).isEqualTo(ErrorCode.EXECUTION_INTERRUPTED);
            assertThat(restored).isTrue();
            assertThat(operationCalled).isFalse();
        } finally {
            caller.interrupt();
            block.countDown();
            caller.join(2000);
        }
    }

    private static void assertLockWaitFailure(boolean stop, ErrorCode expected) throws Exception {
        LockScenario scenario = new LockScenario();
        AtomicInteger operations = new AtomicInteger();
        try {
            scenario.startFirst(() -> {
                scenario.firstEntered.countDown();
                awaitUninterruptibly(scenario.releaseFirst);
            });
            TestContext waiting = scenario.reservingContext();
            scenario.second = scenario.executor.submit(
                    () -> scenario.gate.execute(waiting, operations::incrementAndGet));
            await(scenario.secondReserved);
            if (stop) {
                waiting.stopped = true;
            } else {
                scenario.time.advance(Duration.ofSeconds(1));
                waiting.deadline = Instant.EPOCH.plusMillis(500);
            }

            TensorException failure = futureControlFailure(scenario.second);

            assertThat(failure.code()).isEqualTo(expected);
            assertThat(waiting.reservations).hasValue(1);
            assertThat(operations).hasValue(0);
        } finally {
            scenario.close();
        }
    }

    private static TushareRequestGate gate(
            Duration interval, MutableTime time, TushareRequestGate.Sleeper sleeper) {
        return new TushareRequestGate(interval, time, time::nanoTime, sleeper);
    }

    private static TensorException controlFailure(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        Throwable thrown = catchThrowable(action);
        assertThat(thrown).isInstanceOf(TensorException.class);
        return (TensorException) thrown;
    }

    private static TensorException futureControlFailure(Future<?> future) {
        Throwable thrown = catchThrowable(() -> future.get(2, SECONDS));
        assertThat(thrown).isInstanceOf(ExecutionException.class);
        assertThat(thrown.getCause()).isInstanceOf(TensorException.class);
        return (TensorException) thrown.getCause();
    }

    private static void await(CountDownLatch latch) throws InterruptedException {
        assertThat(latch.await(2, SECONDS)).isTrue();
    }

    private static void awaitUninterruptibly(CountDownLatch latch) {
        boolean interrupted = false;
        while (true) {
            try {
                latch.await();
                if (interrupted) {
                    Thread.currentThread().interrupt();
                }
                return;
            } catch (InterruptedException exception) {
                interrupted = true;
            }
        }
    }

    private static void cancel(Future<?> future) {
        if (future != null) {
            future.cancel(true);
        }
    }

    private static class TestContext implements BatchCallContext {
        private final AtomicInteger reservations = new AtomicInteger();
        private volatile Instant deadline;
        private volatile boolean stopped;
        private volatile TensorException beforeFailure;

        TestContext() {
            this(Instant.MAX);
        }

        TestContext(Instant deadline) {
            this.deadline = deadline;
        }

        @Override
        public Instant deadline() {
            return deadline;
        }

        @Override
        public boolean stopRequested() {
            return stopped;
        }

        @Override
        public void beforeRequest() {
            reservations.incrementAndGet();
            if (beforeFailure != null) {
                throw beforeFailure;
            }
        }
    }

    private static final class MutableTime extends Clock {
        private final AtomicLong utcNanos = new AtomicLong();
        private final AtomicLong monotonicNanos = new AtomicLong();

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return Instant.EPOCH.plusNanos(utcNanos.get());
        }

        long nanoTime() {
            return monotonicNanos.get();
        }

        void advance(Duration duration) {
            long nanos = duration.toNanos();
            utcNanos.addAndGet(nanos);
            monotonicNanos.addAndGet(nanos);
        }
    }

    private static final class TestFailure extends TensorException {
        private TestFailure(ErrorCode code, String message) {
            super(code, message);
        }
    }

    private static final class LockScenario implements AutoCloseable {
        private final MutableTime time = new MutableTime();
        private final TushareRequestGate gate;
        private final ExecutorService executor = Executors.newFixedThreadPool(2);
        private final CountDownLatch firstEntered = new CountDownLatch(1);
        private final CountDownLatch releaseFirst = new CountDownLatch(1);
        private final CountDownLatch secondReserved = new CountDownLatch(1);
        private Future<?> first;
        private Future<?> second;

        LockScenario() {
            this(Duration.ZERO);
        }

        LockScenario(Duration interval) {
            gate = gate(interval, time, time::advance);
        }

        void startFirst(Runnable body) throws InterruptedException {
            first = executor.submit(() -> gate.execute(new TestContext(), () -> {
                body.run();
                return null;
            }));
            await(firstEntered);
        }

        TestContext reservingContext() {
            return new TestContext() {
                @Override
                public void beforeRequest() {
                    super.beforeRequest();
                    secondReserved.countDown();
                }
            };
        }

        @Override
        public void close() throws Exception {
            releaseFirst.countDown();
            cancel(first);
            cancel(second);
            executor.shutdownNow();
            assertThat(executor.awaitTermination(2, SECONDS)).isTrue();
        }
    }
}
