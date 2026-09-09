package com.akkc.tensor.config;

import com.akkc.tensor.core.adapter.FingerprintKeyCodec;
import com.akkc.tensor.core.adapter.GenericDatasetAdapter;
import com.akkc.tensor.core.download.BatchCommitService;
import com.akkc.tensor.core.download.DownloadExecutionSlot;
import com.akkc.tensor.core.download.DownloadParameterConverter;
import com.akkc.tensor.core.adapter.ValueConverter;
import com.akkc.tensor.core.catalog.DatasetCatalog;
import com.akkc.tensor.core.catalog.DatasetStartupValidator;
import com.akkc.tensor.core.catalog.SchemaInspector;
import com.akkc.tensor.core.download.DownloadService;
import com.akkc.tensor.core.download.RetryDownloadService;
import com.akkc.tensor.core.metadata.MetadataQueryService;
import com.akkc.tensor.core.persistence.DatasetLockManager;
import com.akkc.tensor.core.persistence.ExistingKeyRepository;
import com.akkc.tensor.core.persistence.GenericUpsertRepository;
import com.akkc.tensor.core.persistence.PersistenceService;
import com.akkc.tensor.core.query.DatasetQueryService;
import com.akkc.tensor.core.query.GenericQueryRepository;
import com.akkc.tensor.core.registry.AdapterRegistry;
import com.akkc.tensor.core.registry.PluginRegistry;
import com.akkc.tensor.core.validation.ParameterValidator;
import com.akkc.tensor.core.retry.TaskParametersJson;
import com.akkc.tensor.core.retry.RetryTaskRepository;
import com.akkc.tensor.core.retry.RetryTaskStorageService;
import com.akkc.tensor.observability.OperationLogger;
import com.akkc.tensor.observability.TensorMetrics;
import com.akkc.tensor.plugin.api.DataSourcePlugin;
import com.akkc.tensor.plugin.api.DatasetAdapter;
import com.akkc.tensor.plugin.api.dataset.DatasetDefinition;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public final class ApplicationConfiguration {
    @Bean
    public PluginRegistry pluginRegistry(List<DataSourcePlugin> plugins) {
        return new PluginRegistry(plugins);
    }

    @Bean("tensorDatasetAdapters")
    public List<DatasetAdapter> tensorDatasetAdapters(
            @Qualifier("tushareDatasetDefinitions")
                    List<DatasetDefinition> tushareDefinitions,
            ObjectProvider<DatasetAdapter> extensions) {
        ValueConverter converter = new ValueConverter();
        FingerprintKeyCodec keyCodec = new FingerprintKeyCodec();
        List<DatasetAdapter> adapters = new ArrayList<>();
        tushareDefinitions.forEach(definition -> adapters.add(
                new GenericDatasetAdapter(definition, converter, keyCodec)));
        extensions.orderedStream().forEach(adapters::add);
        return List.copyOf(adapters);
    }

    @Bean
    @DependsOnDatabaseInitialization
    public DatasetCatalog datasetCatalog(
            @Qualifier("tensorDatasetAdapters") List<DatasetAdapter> adapters,
            DataSource dataSource) {
        return new DatasetStartupValidator(
                adapters.stream().map(DatasetAdapter::definition).toList(),
                new SchemaInspector(dataSource)).validate();
    }

    @Bean
    public AdapterRegistry adapterRegistry(
            @Qualifier("tensorDatasetAdapters") List<DatasetAdapter> adapters,
            DatasetCatalog catalog) {
        return new AdapterRegistry(adapters.stream()
                .filter(adapter -> catalog.find(adapter.datasetKey()).isPresent())
                .toList());
    }

    @Bean
    public ParameterValidator parameterValidator() {
        return new ParameterValidator();
    }

    @Bean
    public DatasetLockManager datasetLockManager() {
        return new DatasetLockManager();
    }

    @Bean
    public ExistingKeyRepository existingKeyRepository(JdbcTemplate jdbcTemplate) {
        return new ExistingKeyRepository(jdbcTemplate);
    }

    @Bean
    public GenericUpsertRepository genericUpsertRepository(JdbcTemplate jdbcTemplate) {
        return new GenericUpsertRepository(jdbcTemplate);
    }

    @Bean
    public PersistenceService persistenceService(
            DatasetCatalog catalog,
            DatasetLockManager lockManager,
            ExistingKeyRepository existingKeys,
            GenericUpsertRepository upserts,
            PlatformTransactionManager transactions) {
        return new PersistenceService(
                catalog, lockManager, existingKeys, upserts, transactions);
    }

    @Bean
    public TaskParametersJson taskParametersJson() {
        return new TaskParametersJson();
    }

    @Bean
    public RetryTaskRepository retryTaskRepository(JdbcTemplate jdbc, TaskParametersJson json) {
        return new RetryTaskRepository(jdbc, json);
    }

    @Bean
    public RetryTaskStorageService retryTaskStorageService(
            RetryTaskRepository repository, PlatformTransactionManager transactions, Clock clock) {
        return new RetryTaskStorageService(repository, transactions, clock);
    }

    @Bean
    public BatchCommitService batchCommitService(PersistenceService persistence, RetryTaskRepository repository,
            ParameterValidator validator, PlatformTransactionManager transactions, Clock clock) {
        return new BatchCommitService(persistence, repository, new DownloadParameterConverter(validator), transactions, clock);
    }

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public DownloadExecutionSlot downloadExecutionSlot() { return new DownloadExecutionSlot(); }

    @Bean
    public DownloadService downloadService(
            PluginRegistry plugins,
            AdapterRegistry adapters,
            ParameterValidator validator,
            PersistenceService persistence,
            BatchCommitService commits,
            RetryTaskStorageService failures,
            DownloadExecutionSlot slot,
            Clock clock) {
        return new DownloadService(
                plugins, adapters, validator, persistence, commits, failures, slot, clock);
    }

    @Bean
    public com.akkc.tensor.core.retry.RetryTaskQueryService retryTaskQueryService(
            RetryTaskStorageService storage, PluginRegistry plugins, AdapterRegistry adapters,
            ParameterValidator validator, DownloadExecutionSlot slot) {
        return new com.akkc.tensor.core.retry.RetryTaskQueryService(storage, plugins, adapters, validator, slot);
    }

    @Bean
    public RetryDownloadService retryDownloadService(
            PluginRegistry plugins,
            AdapterRegistry adapters,
            ParameterValidator validator,
            BatchCommitService commits,
            RetryTaskStorageService failures,
            DownloadExecutionSlot slot,
            Clock clock) {
        return new RetryDownloadService(plugins, adapters, validator, commits, failures, slot, clock);
    }

    @Bean
    public MetadataQueryService metadataQueryService(PluginRegistry plugins, DatasetCatalog catalog) {
        return new MetadataQueryService(plugins, catalog);
    }

    @Bean
    public GenericQueryRepository genericQueryRepository(JdbcTemplate jdbcTemplate) {
        return new GenericQueryRepository(jdbcTemplate);
    }

    @Bean
    public DatasetQueryService datasetQueryService(
            DatasetCatalog catalog, GenericQueryRepository repository) {
        return new DatasetQueryService(catalog, repository);
    }

    @Bean
    public TensorMetrics tensorMetrics(
            MeterRegistry meterRegistry, PluginRegistry plugins) {
        return new TensorMetrics(meterRegistry, plugins);
    }

    @Bean
    public OperationLogger operationLogger(
            PluginRegistry plugins, TensorMetrics metrics) {
        return new OperationLogger(plugins, metrics);
    }
}
