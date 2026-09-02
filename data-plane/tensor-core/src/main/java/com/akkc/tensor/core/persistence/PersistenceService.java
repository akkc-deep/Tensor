package com.akkc.tensor.core.persistence;

import com.akkc.tensor.core.catalog.DatasetCatalog;
import com.akkc.tensor.plugin.api.dataset.DatasetDefinition;
import com.akkc.tensor.plugin.api.download.AdaptedBatch;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

public final class PersistenceService {
    private final DatasetCatalog datasetCatalog;
    private final DatasetLockManager datasetLockManager;
    private final ExistingKeyRepository existingKeyRepository;
    private final GenericUpsertRepository genericUpsertRepository;
    private final TransactionTemplate transactionTemplate;

    public PersistenceService(
            DatasetCatalog datasetCatalog,
            DatasetLockManager datasetLockManager,
            ExistingKeyRepository existingKeyRepository,
            GenericUpsertRepository genericUpsertRepository,
            PlatformTransactionManager transactionManager) {
        this.datasetCatalog = Objects.requireNonNull(datasetCatalog, "datasetCatalog");
        this.datasetLockManager = Objects.requireNonNull(datasetLockManager, "datasetLockManager");
        this.existingKeyRepository = Objects.requireNonNull(existingKeyRepository, "existingKeyRepository");
        this.genericUpsertRepository = Objects.requireNonNull(genericUpsertRepository, "genericUpsertRepository");
        this.transactionTemplate = new TransactionTemplate(
                Objects.requireNonNull(transactionManager, "transactionManager"));
        transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        transactionTemplate.setTimeout(60);
    }

    public WriteCounts persist(AdaptedBatch batch) {
        Objects.requireNonNull(batch, "batch");
        DatasetDefinition definition = datasetCatalog.find(batch.datasetKey())
                .orElseThrow(() -> new IllegalArgumentException("Dataset is not available"));
        GenericUpsertRepository.validateBatch(definition, batch);
        if (batch.rows().isEmpty()) {
            return new WriteCounts(0, 0);
        }

        BusinessKeyExtractor extractor = new BusinessKeyExtractor();
        List<BusinessKey> keys = batch.rows().stream()
                .map(row -> extractor.extract(definition, row))
                .toList();
        Lock lock = datasetLockManager.acquire(batch.datasetKey());
        AtomicBoolean transferred = new AtomicBoolean();
        try {
            WriteCounts result = transactionTemplate.execute(status -> {
                if (!TransactionSynchronizationManager.isSynchronizationActive()) {
                    throw new IllegalStateException("Transaction synchronization is not active");
                }
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        lock.unlock();
                    }
                });
                transferred.set(true);
                Set<BusinessKey> existingKeys = existingKeyRepository.findExisting(definition, keys);
                WriteCounts counts = WriteCounts.from(keys, existingKeys);
                genericUpsertRepository.upsert(definition, batch);
                return counts;
            });
            return Objects.requireNonNull(result, "transaction result");
        } finally {
            if (!transferred.get()) {
                lock.unlock();
            }
        }
    }
}
