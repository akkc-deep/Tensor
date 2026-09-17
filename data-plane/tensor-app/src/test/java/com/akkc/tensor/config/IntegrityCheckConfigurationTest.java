package com.akkc.tensor.config;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.akkc.tensor.core.catalog.DatasetCatalog;
import com.akkc.tensor.core.integrity.*;
import com.akkc.tensor.core.registry.PluginRegistry;
import com.akkc.tensor.plugin.api.error.*;
import com.akkc.tensor.plugin.api.model.PluginId;
import java.time.Clock;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import java.util.Map;
import org.springframework.context.SmartLifecycle;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

class IntegrityCheckConfigurationTest {
    @Test void assemblesIndependentWorkerAndClosesLifecycleBeforeReturning() {
        var source = mock(javax.sql.DataSource.class);
        var jdbc = new JdbcTemplate(source); var transactions = mock(PlatformTransactionManager.class);
        try (var context = new AnnotationConfigApplicationContext()) {
            context.register(IntegrityCheckConfiguration.class);
            context.registerBean(JdbcTemplate.class, () -> jdbc);
            context.registerBean(javax.sql.DataSource.class, () -> source);
            context.registerBean("integrityCheckRepository", IntegrityCheckRepository.class,
                    () -> mock(IntegrityCheckRepository.class));
            context.registerBean(PlatformTransactionManager.class, () -> transactions);
            context.registerBean(DatasetCatalog.class, () -> mock(DatasetCatalog.class));
            context.registerBean(PluginRegistry.class, () -> new PluginRegistry(List.of()));
            context.registerBean(Clock.class, Clock::systemUTC);
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test",
                    Map.of("tensor.integrity.queue-capacity", "1")));
            context.refresh();
            for (var type : List.of(IntegrityCheckService.class, IntegrityCheckQueue.class,
                    IntegrityCheckRepository.class, IntegrityCheckJson.class, IntegrityCheckRunner.class,
                    IntegrityReadRepository.class, IntegrityUnitEvaluator.class, IntegrityCheckCoordinator.class))
                assertThat(context.getBeansOfType(type)).hasSize(1);
            var lifecycle = context.getBean(SmartLifecycle.class);
            assertThat(lifecycle.getPhase()).isEqualTo(Integer.MAX_VALUE);
            assertThat(lifecycle.isRunning()).isTrue();
            assertThat(context.getBean(IntegrityCheckService.class).capability(new PluginId("unknown"))
                    .limits().queueCapacity()).isOne();
            var queue = context.getBean(IntegrityCheckQueue.class);
            try (var slot = queue.reserve()) {
                assertThatThrownBy(queue::reserve).isInstanceOfSatisfying(TensorException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.INTEGRITY_QUEUE_FULL));
            }
            var stopped = new java.util.concurrent.atomic.AtomicBoolean();
            lifecycle.stop(() -> stopped.set(true));
            assertThat(stopped).isTrue();
            assertThat(lifecycle.isRunning()).isFalse();
            verifyNoInteractions(source, transactions);
        }
    }
}
