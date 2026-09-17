package com.akkc.tensor.config;

import com.akkc.tensor.core.catalog.DatasetCatalog;
import com.akkc.tensor.core.integrity.*;
import com.akkc.tensor.core.registry.PluginRegistry;
import java.time.Clock;
import javax.sql.DataSource;
import org.springframework.context.SmartLifecycle;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

/** Independent local-check admission, worker and application lifecycle. */
@EnableConfigurationProperties(IntegrityCheckProperties.class)
public final class IntegrityCheckConfiguration {
    @Bean public IntegrityCheckJson integrityCheckJson() { return new IntegrityCheckJson(); }
    @Bean public IntegrityCheckRepository integrityCheckRepository(JdbcTemplate jdbc,
            PlatformTransactionManager transactions, IntegrityCheckJson json) {
        return new IntegrityCheckRepository(jdbc, transactions, json);
    }
    @Bean public IntegrityCheckQueue integrityCheckQueue(IntegrityCheckProperties properties) {
        return new IntegrityCheckQueue(properties.queueCapacity());
    }
    @Bean public IntegrityCheckService integrityCheckService(PluginRegistry plugins, DatasetCatalog catalog,
            IntegrityCheckRepository repository, IntegrityCheckJson json, IntegrityCheckQueue queue,
            Clock clock, IntegrityCheckProperties properties) {
        var service = new IntegrityCheckService(plugins, catalog, repository, json, queue, clock, properties.toSettings());
        service.stopAccepting();
        return service;
    }

    @Bean public IntegrityReadRepository integrityReadRepository(DataSource source, DatasetCatalog catalog, Clock clock) {
        return new IntegrityReadRepository(source, catalog, clock);
    }
    @Bean public IntegrityUnitEvaluator integrityUnitEvaluator(IntegrityReadRepository reads, Clock clock) {
        return new IntegrityUnitEvaluator(reads, clock);
    }
    @Bean public IntegrityCheckRunner integrityCheckRunner(IntegrityCheckRepository repository,
            IntegrityCheckService service, PluginRegistry plugins, IntegrityCheckJson json,
            IntegrityUnitEvaluator evaluator, Clock clock, IntegrityCheckProperties properties) {
        return new IntegrityCheckRunner(repository, service, plugins, json, evaluator, clock, properties.toSettings());
    }
    @Bean(destroyMethod = "close") public IntegrityCheckCoordinator integrityCheckCoordinator(IntegrityCheckQueue queue,
            IntegrityCheckRunner runner, IntegrityCheckRepository repository, IntegrityCheckService service, Clock clock) {
        return new IntegrityCheckCoordinator(queue, runner, repository, service, clock);
    }
    @Bean public SmartLifecycle integrityCheckLifecycle(IntegrityCheckCoordinator coordinator) {
        return new SmartLifecycle() {
            @Override public void start() { coordinator.start(); }
            @Override public boolean isRunning() { return coordinator.isRunning(); }
            @Override public boolean isAutoStartup() { return true; }
            @Override public int getPhase() { return Integer.MAX_VALUE; }
            @Override public void stop() { coordinator.close(); }
            @Override public void stop(Runnable callback) { coordinator.close(); callback.run(); }
        };
    }
}
