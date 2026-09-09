package com.akkc.tensor.core.download;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.akkc.tensor.core.download.RecoveryUnitProcessor.ReadyUnit;
import com.akkc.tensor.core.persistence.*;
import com.akkc.tensor.core.retry.*;
import com.akkc.tensor.core.retry.RetryTaskRepository.*;
import com.akkc.tensor.core.validation.ParameterValidator;
import com.akkc.tensor.plugin.api.error.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.dao.*;
import org.springframework.transaction.*;
import org.springframework.transaction.support.*;

class BatchCommitServiceTest {
    final PersistenceService persistence = mock(PersistenceService.class);
    final RetryTaskRepository tasks = mock(RetryTaskRepository.class);
    final BoundaryManager manager = new BoundaryManager();
    final BatchCommitService service = new BatchCommitService(persistence, tasks,
            new DownloadParameterConverter(new ParameterValidator()), manager, Clock.systemUTC());
    final RecoveryUnitProcessorTest.Fixture fixture = RecoveryUnitProcessorTest.fixture(false);

    @AfterEach void clear() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) TransactionSynchronizationManager.clearSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test void publishesCountsOnlyAfterCompletionWithoutConfirmingIndex() {
        var ready = ready();
        when(persistence.persist(ready.batch())).thenAnswer(call -> {
            assertThat(fixture.index().size()).isZero();
            assertThat(TransactionSynchronizationManager.getSynchronizations().getFirst().getOrder()).isEqualTo(Integer.MIN_VALUE);
            return new WriteCounts(1, 0);
        });
        assertThat(service.commitInitial(ready)).isEqualTo(new BatchCommitService.Committed(ready.selector(), 1, new WriteCounts(1, 0), false));
        assertThat(fixture.index().size()).isZero();
        fixture.index().confirmCommitted(ready);
        assertThat(fixture.index().size()).isOne();
        assertThat(manager.begins).isOne();
    }

    @Test void knownCommitSurvivesFrameworkAndAfterCommitExceptionsAndMissingCompletionIsUnknown() {
        for (String mode : List.of("afterCommit", "afterManager", "missing")) {
            manager.mode = mode;
            var ready = ready();
            doAnswer(call -> {
                if (mode.equals("afterCommit")) TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override public void afterCommit() { throw new IllegalStateException("Controlled completion fault"); }
                });
                return new WriteCounts(1, 0);
            }).when(persistence).persist(ready.batch());
            var actual = service.commitInitial(ready);
            if (mode.equals("missing")) assertThat(actual).isEqualTo(new BatchCommitService.Unconfirmed(ready.selector()));
            else assertThat(actual).isEqualTo(new BatchCommitService.Committed(ready.selector(), 1, new WriteCounts(1, 0), true));
            assertThat(fixture.index().size()).isZero();
        }
    }

    @Test void rollbackProofRequiresNoCommitPhaseAndAStorageFailure() {
        var ready = ready();
        when(persistence.persist(ready.batch())).thenThrow(new DataRetrievalFailureException("SQL SENTINEL"));
        assertThat(service.commitInitial(ready)).isEqualTo(new BatchCommitService.RolledBack(ready.selector(), false));
        manager.mode = "unknownRollback";
        assertThat(service.commitInitial(ready)).isEqualTo(new BatchCommitService.Unconfirmed(ready.selector()));
        manager.mode = "beforeCommitRollback";
        doReturn(new WriteCounts(1, 0)).when(persistence).persist(ready.batch());
        assertThat(service.commitInitial(ready)).isEqualTo(new BatchCommitService.Unconfirmed(ready.selector()));
        assertThat(fixture.index().size()).isZero();
    }

    @Test void startFailureIsUnavailableAndConfirmedProgrammingFailurePropagates() {
        var ready = ready();
        manager.mode = "startFailure";
        assertThat(service.commitInitial(ready)).isEqualTo(new BatchCommitService.Unavailable(ready.selector()));
        verifyNoInteractions(persistence, tasks);
        manager.mode = "normal";
        var programming = new IllegalStateException("Controlled programming failure");
        when(persistence.persist(ready.batch())).thenThrow(programming);
        assertThatThrownBy(() -> service.commitInitial(ready)).isSameAs(programming);
    }

    @Test void resourceClassificationUsesTypesAndSqlStateAcrossCyclicChainsWithoutLeakingText() {
        var ready = ready();
        var root = new SQLException("secret SENTINEL", "45000");
        var next = new SQLException("SELECT secret SENTINEL", "08006");
        root.setNextException(next);
        next.setNextException(root);
        List<RuntimeException> failures = List.of(new DataRetrievalFailureException("SENTINEL", root),
                new DataAccessResourceFailureException("SENTINEL"), new RecoverableDataAccessException("SENTINEL"),
                new DataRetrievalFailureException("SENTINEL", new SQLTransientConnectionException()),
                new DataRetrievalFailureException("SENTINEL", new SQLNonTransientConnectionException()),
                new DataRetrievalFailureException("SENTINEL", new SQLRecoverableException()),
                new TransactionSystemException("SENTINEL"));
        for (var failure : failures) {
            doThrow(failure).when(persistence).persist(ready.batch());
            assertThat(service.commitInitial(ready)).isEqualTo(new BatchCommitService.RolledBack(ready.selector(), true))
                    .hasToString("RolledBack[selector=" + ready.selector() + ", storageUnavailable=true]");
        }
        doThrow(new DataRetrievalFailureException("08006 connection SENTINEL")).when(persistence).persist(ready.batch());
        assertThat(service.commitInitial(ready)).isEqualTo(new BatchCommitService.RolledBack(ready.selector(), false));
    }

    @Test void rejectsOuterSynchronizationConfirmedAndCrossThreadTicketsBeforeTransaction() throws Exception {
        var ready = ready();
        TransactionSynchronizationManager.initSynchronization();
        assertThatThrownBy(() -> service.commitInitial(ready)).isInstanceOf(IllegalStateException.class)
                .hasMessage("Recovery unit commit must not join an existing transaction");
        TransactionSynchronizationManager.clearSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        assertThatThrownBy(() -> service.commitInitial(ready)).isInstanceOf(IllegalStateException.class);
        TransactionSynchronizationManager.setActualTransactionActive(false);
        try (var executor = Executors.newSingleThreadExecutor()) {
            assertThatThrownBy(() -> executor.submit(() -> service.commitInitial(ready)).get(5, TimeUnit.SECONDS))
                    .hasCauseInstanceOf(IllegalStateException.class);
        }
        fixture.index().confirmCommitted(ready);
        assertThatThrownBy(() -> service.commitInitial(ready)).isInstanceOf(IllegalStateException.class);
        assertThat(manager.begins).isZero();
        verifyNoInteractions(persistence, tasks);
    }

    @Test void retryValidatesBindingsAndCleanupCountsButAllowsUnchangedTouch() {
        var ready = ready();
        var item = new ItemKey(UUID.randomUUID(), ready.selector());
        when(tasks.lockTask(item.taskId())).thenReturn(Optional.empty());
        assertCode(() -> service.commitRetry(item, ready), ErrorCode.RETRY_TASK_NOT_FOUND);
        verifyNoInteractions(persistence);
        var header = new Header(item.taskId(), ready.batch().datasetKey(), Map.of(), Instant.EPOCH, Instant.EPOCH);
        when(tasks.lockTask(item.taskId())).thenReturn(Optional.of(header));
        assertCode(() -> service.commitRetry(item, ready), ErrorCode.RETRY_TASK_NOT_FOUND);
        when(tasks.containsItem(item)).thenReturn(true);
        doReturn(new WriteCounts(1, 0)).when(persistence).persist(ready.batch());
        assertThat(service.commitRetry(item, ready)).isEqualTo(new BatchCommitService.RolledBack(ready.selector(), false));
        when(tasks.deleteItem(item)).thenReturn(1);
        assertThat(service.commitRetry(item, ready)).isEqualTo(new BatchCommitService.RolledBack(ready.selector(), false));
        when(tasks.hasItems(item.taskId())).thenReturn(true);
        assertThat(service.commitRetry(item, ready)).isInstanceOf(BatchCommitService.Committed.class);
        assertThat(fixture.index().size()).isZero();
    }


    @Test void nullInputsAndInvalidResultCountsAreRejectedWithoutStartingTransactions() {
        var ready = ready();
        var item = new ItemKey(UUID.randomUUID(), ready.selector());
        assertThatThrownBy(() -> service.commitInitial(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.commitRetry(null, ready)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.commitRetry(item, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.commitClosedRetry(null, fixture.api(), item, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BatchCommitService.Committed(ready.selector(), -1, new WriteCounts(0, 0), false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BatchCommitService.Committed(ready.selector(), 0, null, false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BatchCommitService.Unconfirmed(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BatchCommitService.Unavailable(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BatchCommitService.RolledBack(null, false)).isInstanceOf(IllegalArgumentException.class);
        assertThat(manager.begins).isZero();
        verifyNoInteractions(persistence, tasks);
    }

    @Test void cyclicNonConnectionSqlExceptionsTerminateAndErrorEscapesNormalResultBoundary() {
        var ready = ready();
        var cycle = new SQLException("SENTINEL", "45000");
        cycle.setNextException(cycle);
        doThrow(new DataRetrievalFailureException("SENTINEL", cycle)).when(persistence).persist(ready.batch());
        assertThat(service.commitInitial(ready)).isEqualTo(new BatchCommitService.RolledBack(ready.selector(), false));
        var fatal = new AssertionError("Controlled fatal error");
        doThrow(fatal).when(persistence).persist(ready.batch());
        assertThatThrownBy(() -> service.commitInitial(ready)).isSameAs(fatal);
    }

    static void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, ErrorCode code) {
        assertThatThrownBy(call).isInstanceOfSatisfying(TensorException.class, e -> {
            assertThat(e.code()).isEqualTo(code); assertThat(e).hasNoCause();
        });
    }
    ReadyUnit ready() {
        var unit = fixture.session().accept(fixture.result(List.of(
                RecoveryUnitProcessorTest.row("a-key", "000001.SZ", "20260903", "1.20")))).units().getFirst();
        return (ReadyUnit) fixture.processor().validate(unit, fixture.index(), Instant.EPOCH);
    }

    // Only completion-edge unit tests use this synthetic boundary; physical results are proved by MySQL IT.
    static class BoundaryManager implements PlatformTransactionManager {
        int begins;
        String mode = "normal";
        public TransactionStatus getTransaction(TransactionDefinition definition) {
            begins++;
            assertThat(definition.getPropagationBehavior()).isEqualTo(TransactionDefinition.PROPAGATION_REQUIRED);
            assertThat(definition.getTimeout()).isEqualTo(60);
            if (mode.equals("startFailure")) throw new CannotCreateTransactionException("SENTINEL");
            TransactionSynchronizationManager.setActualTransactionActive(true);
            TransactionSynchronizationManager.initSynchronization();
            return new SimpleTransactionStatus();
        }
        public void commit(TransactionStatus status) {
            var callbacks = TransactionSynchronizationManager.getSynchronizations();
            try {
                callbacks.forEach(c -> c.beforeCommit(false));
                if (mode.equals("beforeCommitRollback")) {
                    callbacks.forEach(c -> c.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
                    throw new TransactionSystemException("SENTINEL");
                }
                if (!mode.equals("missing")) {
                    try { callbacks.forEach(TransactionSynchronization::afterCommit); }
                    finally { callbacks.forEach(c -> c.afterCompletion(TransactionSynchronization.STATUS_COMMITTED)); }
                }
                if (mode.equals("afterManager")) throw new TransactionSystemException("SENTINEL");
            } finally { clearState(); }
        }
        public void rollback(TransactionStatus status) {
            try { TransactionSynchronizationManager.getSynchronizations().forEach(c -> c.afterCompletion(
                    mode.equals("unknownRollback") ? TransactionSynchronization.STATUS_UNKNOWN : TransactionSynchronization.STATUS_ROLLED_BACK)); }
            finally { clearState(); }
        }
        void clearState() {
            TransactionSynchronizationManager.clearSynchronization();
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
    }
}
