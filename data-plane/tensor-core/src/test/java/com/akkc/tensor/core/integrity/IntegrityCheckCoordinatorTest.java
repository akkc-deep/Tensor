package com.akkc.tensor.core.integrity;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.akkc.tensor.core.integrity.IntegrityCheckServiceTest.*;
import com.akkc.tensor.core.registry.PluginRegistry;
import com.akkc.tensor.plugin.api.error.ErrorCode;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.*;

class IntegrityCheckCoordinatorTest {
    final IntegrityCheckRepository repository = mock(IntegrityCheckRepository.class);
    final IntegrityCheckRunner runner = mock(IntegrityCheckRunner.class);
    final IntegrityCheckQueue queue = new IntegrityCheckQueue(20);
    final LocalPlugin plugin = new LocalPlugin();
    final IntegrityCheckService service = new IntegrityCheckService(new PluginRegistry(List.of(plugin)),
            catalog(plugin.definitions()), repository, new IntegrityCheckJson(), queue,
            Clock.fixed(NOW, ZoneOffset.UTC), IntegrityCheckService.Settings.defaults());
    final Map<UUID,IntegrityCheckRepository.TaskRecord> saved = new ConcurrentHashMap<>();
    final IntegrityCheckCoordinator coordinator = new IntegrityCheckCoordinator(queue, runner, repository,
            service, Clock.fixed(NOW, ZoneOffset.UTC));

    @BeforeEach void setup() {
        doAnswer(call -> {
            var task = (IntegrityCheckRepository.NewTask) call.getArgument(0);
            saved.put(task.checkId(), record(task, ((List<?>) call.getArgument(1)).size())); return null;
        }).when(repository).create(any(), anyList());
        when(repository.find(any())).thenAnswer(call -> Optional.ofNullable(saved.get(call.getArgument(0))));
        when(repository.findBySubmissionId(any())).thenAnswer(call -> saved.values().stream()
                .filter(t -> t.submissionId().equals(call.getArgument(0))).findFirst());
    }
    @AfterEach void close() { coordinator.close(); }

    @Test void recoveryBarrierKeepsNewAdmissionsClosedButAllowsReplay() throws Exception {
        var old = request(); service.submit(old);
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        doAnswer(call -> { entered.countDown(); assertThat(release.await(5, TimeUnit.SECONDS)).isTrue(); return 1; })
                .when(repository).interruptUnfinished(any());
        try (var executor = Executors.newSingleThreadExecutor()) {
            var startup = executor.submit(coordinator::start);
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            try {
                rejects(() -> service.submit(request()), ErrorCode.INTEGRITY_UNAVAILABLE);
                assertThat(service.submit(old).created()).isFalse();
            } finally { release.countDown(); }
            startup.get(5, TimeUnit.SECONDS);
            assertThat(service.submit(request()).created()).isTrue();
        }
    }

    @Test void singleWorkerSurvivesTaskFailureAndCloseWaitsForActiveExecution() throws Exception {
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        var stopped = new CountDownLatch(1); var ids = new CopyOnWriteArrayList<UUID>();
        var names = new CopyOnWriteArrayList<String>();
        doAnswer(call -> {
            ids.add(call.getArgument(0)); names.add(Thread.currentThread().getName());
            if (ids.size() == 1) throw new IllegalStateException("sensitive task exception");
            BooleanSupplier stopping = call.getArgument(1); entered.countDown();
            while (!stopping.getAsBoolean()) Thread.onSpinWait();
            stopped.countDown(); assertThat(release.await(5, TimeUnit.SECONDS)).isTrue(); return null;
        }).when(runner).run(any(), any());
        coordinator.start(); coordinator.start();
        var one = service.submit(request()).task();
        var old = request(); var two = service.submit(old).task();
        assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
        var queued = service.submit(request()).task();
        try (var executor = Executors.newSingleThreadExecutor()) {
            var closing = executor.submit(coordinator::close);
            assertThat(stopped.await(5, TimeUnit.SECONDS)).isTrue();
            try {
                assertThat(closing.isDone()).isFalse();
                rejects(() -> service.submit(request()), ErrorCode.INTEGRITY_UNAVAILABLE);
                assertThat(service.submit(old).created()).isFalse();
            } finally { release.countDown(); }
            closing.get(5, TimeUnit.SECONDS);
        }
        assertThat(ids).containsExactly(one.checkId(), two.checkId());
        assertThat(names).containsOnly("tensor-integrity-worker");
        assertThat(queue.poll(Duration.ZERO)).isEmpty();
        assertThat(coordinator.isRunning()).isFalse();
        assertThatIllegalStateException().isThrownBy(coordinator::start);
        verify(repository).terminate(eq(queued.checkId()), eq(com.akkc.tensor.plugin.api.integrity.IntegrityTaskStatus.INTERRUPTED),
                eq("EXECUTION_INTERRUPTED"), any());
    }

    @Test void closeWaitsForInFlightAdmissionBeforeDrainingItsPublishedId() throws Exception {
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        var handled = ConcurrentHashMap.<UUID>newKeySet();
        doAnswer(call -> { handled.add(call.getArgument(0)); return null; }).when(runner).run(any(), any());
        doAnswer(call -> { handled.add(call.getArgument(0)); return null; }).when(repository).terminate(any(), any(), any(), any());
        doAnswer(call -> {
            entered.countDown(); assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
            var task = (IntegrityCheckRepository.NewTask) call.getArgument(0);
            saved.put(task.checkId(), record(task, ((List<?>) call.getArgument(1)).size())); return null;
        }).when(repository).create(any(), anyList());
        coordinator.start();
        try (var executor = Executors.newFixedThreadPool(2)) {
            var submitting = executor.submit(() -> service.submit(request()));
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            var closingEntered = new CountDownLatch(1);
            var closing = executor.submit(() -> { closingEntered.countDown(); coordinator.close(); });
            try {
                assertThat(closingEntered.await(5, TimeUnit.SECONDS)).isTrue();
                assertThat(closing.isDone()).isFalse();
            } finally { release.countDown(); }
            var accepted = submitting.get(5, TimeUnit.SECONDS).task(); closing.get(5, TimeUnit.SECONDS);
            assertThat(handled).contains(accepted.checkId());
            assertThat(queue.poll(Duration.ZERO)).isEmpty();
            rejects(() -> service.submit(request()), ErrorCode.INTEGRITY_UNAVAILABLE);
        } finally { release.countDown(); }
    }

    @Test void recoveryFailureNeverOpensAdmissionAndIdleCloseIsPrompt() {
        doThrow(new TestFailure(ErrorCode.QUERY_FAILED)).when(repository).interruptUnfinished(any());
        assertThatThrownBy(coordinator::start).isInstanceOf(TestFailure.class);
        rejects(() -> service.submit(request()), ErrorCode.INTEGRITY_UNAVAILABLE);
        assertThat(coordinator.isRunning()).isFalse();
    }
    @Test void closesAnIdleWorkerWithoutWaitingForPollTimeout() {
        coordinator.start();
        assertTimeoutPreemptively(Duration.ofMillis(800), coordinator::close);
        assertThat(coordinator.isRunning()).isFalse();
    }
    private static void assertTimeoutPreemptively(Duration timeout, org.junit.jupiter.api.function.Executable action) {
        org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(timeout, action);
    }
    Map<String,Object> request() {
        return new LinkedHashMap<>(Map.of("submissionId", UUID.randomUUID().toString(), "pluginId", "local",
                "capabilityHash", service.capability(PLUGIN).capabilityHash(), "symbols", List.of("A"),
                "startDate", "2026-01-01", "endDate", "2026-01-01"));
    }
}
