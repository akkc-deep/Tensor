package com.akkc.tensor.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.akkc.tensor.core.catalog.DatasetCatalog;
import com.akkc.tensor.core.catalog.DatasetStartupValidator;
import com.akkc.tensor.core.catalog.SchemaInspector;
import com.akkc.tensor.core.download.task.DownloadTaskCoordinator;
import com.akkc.tensor.core.download.task.DownloadTaskJson;
import com.akkc.tensor.core.download.task.DownloadTaskRepository;
import com.akkc.tensor.core.download.task.DownloadTaskRunner;
import com.akkc.tensor.core.download.task.DownloadTaskService;
import com.akkc.tensor.core.registry.AdapterRegistry;
import com.akkc.tensor.core.registry.PluginRegistry;
import com.akkc.tensor.core.validation.ParameterValidator;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;

class DownloadTaskConfigurationTest {
    @Test
    void explicitRegistrationStartsOneCoordinatorAndStopsItBeforeLowerPhases() {
        DownloadTaskRunner runner = mock(DownloadTaskRunner.class);
        when(runner.runNext(any())).thenReturn(idle());
        AnnotationConfigApplicationContext context = context(runner);
        DownloadTaskCoordinator coordinator;
        try (context) {
            context.refresh();
            coordinator = context.getBean(DownloadTaskCoordinator.class);
            SmartLifecycle lifecycle = context.getBean("downloadTaskLifecycle", SmartLifecycle.class);
            assertThat(context.getBeansOfType(DownloadTaskCoordinator.class)).hasSize(1);
            assertThat(context.getBeansOfType(UUID.class)).containsOnlyKeys("downloadTaskRunId");
            assertThat(lifecycle.isAutoStartup()).isTrue();
            assertThat(lifecycle.getPhase()).isEqualTo(Integer.MAX_VALUE);
            assertThat(lifecycle.isRunning()).isTrue();
            assertThat(coordinator.isRunning()).isTrue();
            lifecycle.start();
            lifecycle.stop();
            assertThat(lifecycle.isRunning()).isFalse();
            assertThat(coordinator.isRunning()).isFalse();
        }
        assertThat(coordinator.isRunning()).isFalse();
    }

    @Test
    void stopCallbackWaitsForTheActualRunnerExit() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicReference<BooleanSupplier> stop = new AtomicReference<>();
        AtomicBoolean exited = new AtomicBoolean();
        AtomicBoolean callback = new AtomicBoolean();
        DownloadTaskRunner runner = mock(DownloadTaskRunner.class);
        when(runner.runNext(any())).thenAnswer(call -> {
            stop.set(call.getArgument(0));
            entered.countDown();
            assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
            exited.set(true);
            return idle();
        });
        AnnotationConfigApplicationContext context = context(runner);
        FutureTask<Void> closing = null;
        try {
            context.refresh();
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            SmartLifecycle lifecycle = context.getBean("downloadTaskLifecycle", SmartLifecycle.class);
            closing = new FutureTask<>(() -> {
                lifecycle.stop(() -> {
                    assertThat(exited).isTrue();
                    callback.set(true);
                });
                return null;
            });
            new Thread(closing, "configuration-stop-test").start();
            assertThat(await(stop.get())).isTrue();
            assertThat(closing.isDone()).isFalse();
            assertThat(callback).isFalse();
            assertThat(exited).isFalse();
            release.countDown();
            closing.get(5, TimeUnit.SECONDS);
            assertThat(callback).isTrue();
            assertThat(lifecycle.isRunning()).isFalse();
        } finally {
            release.countDown();
            if (closing != null) closing.get(5, TimeUnit.SECONDS);
            context.close();
        }
    }

    @Test
    void componentScanningDoesNotEnableTheTaskLifecycle() {
        var scanner = new ClassPathScanningCandidateComponentProvider(true);
        assertThat(scanner.findCandidateComponents("com.akkc.tensor.config"))
                .noneMatch(bean -> DownloadTaskConfiguration.class.getName().equals(bean.getBeanClassName()));
    }

    private static AnnotationConfigApplicationContext context(DownloadTaskRunner runner) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.register(DownloadTaskConfiguration.class);
        context.registerBean(DatasetCatalog.class, () -> new DatasetStartupValidator(
                List.of(), new SchemaInspector(mock(DataSource.class))).validate());
        context.registerBean(Clock.class, Clock::systemUTC);
        DownloadTaskRepository repository = mock(DownloadTaskRepository.class);
        when(repository.unfinishedTasks()).thenReturn(List.of());
        context.registerBean(DownloadTaskRepository.class, () -> repository);
        context.registerBean(DownloadTaskService.class, () -> new DownloadTaskService(
                new PluginRegistry(List.of()), context.getBean(DatasetCatalog.class),
                new AdapterRegistry(List.of()), new ParameterValidator(), repository,
                new DownloadTaskJson(), context.getBean(Clock.class),
                context.getBean("downloadTaskRunId", UUID.class), DownloadTaskService.Settings.defaults()));
        context.registerBean(DownloadTaskRunner.class, () -> runner);
        return context;
    }

    private static DownloadTaskRunner.RunResult idle() {
        return new DownloadTaskRunner.RunResult(null, null, DownloadTaskRunner.Disposition.IDLE, null);
    }

    private static boolean await(BooleanSupplier condition) throws InterruptedException {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!condition.getAsBoolean() && System.nanoTime() < end) Thread.sleep(10);
        return condition.getAsBoolean();
    }
}
