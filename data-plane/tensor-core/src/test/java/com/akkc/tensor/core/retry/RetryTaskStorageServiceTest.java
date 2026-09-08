package com.akkc.tensor.core.retry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.akkc.tensor.core.retry.RetryTaskRepository.Criteria;
import com.akkc.tensor.core.retry.RetryTaskRepository.Failure;
import com.akkc.tensor.core.retry.RetryTaskRepository.Header;
import com.akkc.tensor.core.retry.RetryTaskRepository.Item;
import com.akkc.tensor.core.retry.RetryTaskRepository.ItemKey;
import com.akkc.tensor.core.retry.RetryTaskRepository.Page;
import com.akkc.tensor.core.retry.RetryTaskRepository.SavedFailure;
import com.akkc.tensor.core.retry.RetryTaskRepository.Task;
import com.akkc.tensor.plugin.api.download.RecoverySelector;
import com.akkc.tensor.plugin.api.download.RecoverySelector.TargetType;
import com.akkc.tensor.plugin.api.download.RecoverySelector.TimeType;
import com.akkc.tensor.plugin.api.error.ErrorCode;
import com.akkc.tensor.plugin.api.error.TensorException;
import com.akkc.tensor.plugin.api.model.ApiName;
import com.akkc.tensor.plugin.api.model.DatasetKey;
import com.akkc.tensor.plugin.api.model.PluginId;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.AbstractDataSource;
import org.springframework.jdbc.datasource.ConnectionHolder;

class RetryTaskStorageServiceTest {
    private static final DatasetKey DATASET = new DatasetKey(new PluginId("test_plugin"), new ApiName("daily"));
    private static final Failure FAILURE = new Failure(
            new RecoverySelector(TargetType.REQUEST, "", TimeType.DATE, "2026-09-09"),
            ErrorCode.SOURCE_TIMEOUT);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-09T01:02:03.123456Z"), ZoneOffset.UTC);

    @AfterEach
    void clearTransactionState() {
        TransactionSynchronizationManager.clear();
    }

    @Test
    void validatesCriteriaAndFailureCodesBeforeStartingTransactions() {
        CountingTransactions transactions = new CountingTransactions(false);
        RetryTaskStorageService service = service(mock(RetryTaskRepository.class), transactions);

        assertThat(Criteria.defaults()).isEqualTo(new Criteria(null, null, 1, 20));
        assertThatThrownBy(() -> new Criteria(null, null, 0, 20))
                .isExactlyInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Criteria(null, null, 1, 19))
                .isExactlyInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Failure(FAILURE.selector(), ErrorCode.PARAM_INVALID))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasNoCause();
        assertThatThrownBy(() -> service.create(null, Map.of(), FAILURE))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid retry task request").hasNoCause();
        assertThat(transactions.begun()).isZero();
    }

    @Test
    void rejectsAnOuterTransactionBeforeRepositoryAccess() {
        RetryTaskRepository repository = mock(RetryTaskRepository.class);
        CountingTransactions transactions = new CountingTransactions(false);
        RetryTaskStorageService service = service(repository, transactions);
        TransactionSynchronizationManager.setActualTransactionActive(true);

        assertThatThrownBy(() -> service.create(DATASET, Map.of(), FAILURE))
                .isExactlyInstanceOf(IllegalStateException.class)
                .hasMessage("Retry task storage must not join an existing transaction");
        assertThatThrownBy(() -> service.find(UUID.randomUUID()))
                .isExactlyInstanceOf(IllegalStateException.class)
                .hasMessage("Retry task storage must not join an existing transaction");

        verify(repository, never()).insertHeader(any());
        assertThat(transactions.begun()).isZero();
    }

    @Test
    void returnsCreatedKeyOnlyAfterCommitCompletes() {
        RetryTaskRepository repository = mock(RetryTaskRepository.class);
        when(repository.freezeTaskParams(any())).thenReturn(Map.of());
        when(repository.readTaskParams(any())).thenReturn(Map.of());
        CountingTransactions transactions = new CountingTransactions(false);
        var saved = service(repository, transactions).create(DATASET, Map.of(), FAILURE);

        assertThat(saved.key().taskId()).isNotNull();
        assertThat(saved.key().selector()).isEqualTo(FAILURE.selector());
        assertThat(transactions.committed()).isOne();
        ArgumentCaptor<Header> header = ArgumentCaptor.forClass(Header.class);
        ArgumentCaptor<Instant> itemTime = ArgumentCaptor.forClass(Instant.class);
        verify(repository).insertHeader(header.capture());
        verify(repository).insertItem(any(), any(), itemTime.capture());
        Instant stored = Instant.parse("2026-09-09T01:02:03.123Z");
        assertThat(header.getValue().createdAt()).isEqualTo(stored);
        assertThat(header.getValue().updatedAt()).isEqualTo(stored);
        assertThat(itemTime.getValue()).isEqualTo(stored);
        assertThat(transactions.definitions()).singleElement().satisfies(definition -> {
            assertThat(definition.getPropagationBehavior()).isEqualTo(TransactionDefinition.PROPAGATION_REQUIRED);
            assertThat(definition.getIsolationLevel()).isEqualTo(TransactionDefinition.ISOLATION_DEFAULT);
            assertThat(definition.getTimeout()).isEqualTo(60);
            assertThat(definition.isReadOnly()).isFalse();
        });
    }

    @Test
    void commitFailureNeverReturnsAKeyAndIsNotRetried() {
        RetryTaskRepository repository = mock(RetryTaskRepository.class);
        when(repository.freezeTaskParams(any())).thenReturn(Map.of());
        when(repository.readTaskParams(any())).thenReturn(Map.of());
        CountingTransactions transactions = new CountingTransactions(true);
        RetryTaskStorageService service = service(repository, transactions);

        assertUnconfirmed(() -> service.create(DATASET, Map.of(), FAILURE));
        assertThat(transactions.begun()).isOne();
        assertThat(transactions.committed()).isOne();
        verify(repository).insertHeader(any());
    }

    @Test
    void sqlFailureDoesNotExposeCredentialsSqlOrCauseAndIsNotRetried() {
        RetryTaskRepository repository = mock(RetryTaskRepository.class);
        when(repository.freezeTaskParams(any())).thenReturn(Map.of());
        doThrow(new DataAccessResourceFailureException(
                "password=CREDENTIAL_SENTINEL SQL SELECT secret"))
                .when(repository).insertHeader(any());
        CountingTransactions transactions = new CountingTransactions(false);

        assertUnconfirmed(() -> service(repository, transactions).create(DATASET, Map.of(), FAILURE));
        assertThat(transactions.begun()).isOne();
        verify(repository).insertHeader(any());
    }

    @Test
    void repositoryPrimitivesRejectMissingOrDifferentBoundConnectionBeforeSql() {
        CountingDataSource dataSource = new CountingDataSource();
        RetryTaskRepository repository = new RetryTaskRepository(new JdbcTemplate(dataSource), new TaskParametersJson());
        UUID taskId = UUID.randomUUID();
        ItemKey key = new ItemKey(taskId, FAILURE.selector());
        List<ThrowingCall> calls = List.of(
                () -> repository.lockTask(taskId),
                () -> repository.containsItem(key),
                () -> repository.deleteItem(key),
                () -> repository.hasItems(taskId),
                () -> repository.touchTask(taskId, CLOCK.instant()),
                () -> repository.deleteEmptyTask(taskId));

        calls.forEach(call -> assertThatThrownBy(call::run)
                .isExactlyInstanceOf(IllegalStateException.class)
                .hasMessage("Retry task repository requires a bound transaction connection"));

        DataSource otherDataSource = new CountingDataSource();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.bindResource(otherDataSource, new Object());
        calls.forEach(call -> assertThatThrownBy(call::run)
                .isExactlyInstanceOf(IllegalStateException.class)
                .hasMessage("Retry task repository requires a bound transaction connection"));

        TransactionSynchronizationManager.unbindResource(otherDataSource);
        TransactionSynchronizationManager.bindResource(dataSource, new Object());
        calls.forEach(call -> assertThatThrownBy(call::run)
                .isExactlyInstanceOf(IllegalStateException.class)
                .hasMessage("Retry task repository requires a bound transaction connection"));
        TransactionSynchronizationManager.unbindResource(dataSource);
        TransactionSynchronizationManager.bindResource(dataSource, new ConnectionHolder(() -> null));
        calls.forEach(call -> assertThatThrownBy(call::run)
                .isExactlyInstanceOf(IllegalStateException.class)
                .hasMessage("Retry task repository requires a bound transaction connection"));
        assertThat(dataSource.connections()).isZero();
    }

    @Test
    void publicRecordsRejectNullsInvalidContentMismatchesAndDuplicates() {
        UUID taskId = UUID.randomUUID();
        Instant now = CLOCK.instant();
        ItemKey key = new ItemKey(taskId, FAILURE.selector());
        Header header = new Header(taskId, DATASET, Map.of(), now, now);
        Item item = new Item(key, ErrorCode.SOURCE_TIMEOUT, "safe", now);

        assertThatThrownBy(() -> new ItemKey(null, FAILURE.selector())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ItemKey(taskId, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Failure(null, ErrorCode.SOURCE_TIMEOUT)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Failure(FAILURE.selector(), null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Header(null, DATASET, Map.of(), now, now)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Header(taskId, null, Map.of(), now, now)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Header(taskId, DATASET, null, now, now)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Header(taskId, DATASET, Map.of(), null, now)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Header(taskId, DATASET, Map.of(), now, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Item(null, ErrorCode.SOURCE_TIMEOUT, "safe", now)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Item(key, null, "safe", now)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Item(key, ErrorCode.SOURCE_TIMEOUT, "", now)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Item(key, ErrorCode.SOURCE_TIMEOUT, "unsafe\n", now)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Item(key, ErrorCode.SOURCE_TIMEOUT, "x".repeat(513), now))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Item(key, ErrorCode.SOURCE_TIMEOUT, "safe", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Task(null, List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Task(header, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Task(header, List.of(item, item))).isInstanceOf(IllegalArgumentException.class);
        Item otherTask = new Item(new ItemKey(UUID.randomUUID(), FAILURE.selector()), ErrorCode.SOURCE_TIMEOUT, "safe", now);
        assertThatThrownBy(() -> new Task(header, List.of(otherTask))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Page(null, 1, 20, 0, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Page(List.of(), 1, 20, -1, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SavedFailure(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void savedUuidParserRejectsNonCanonicalTextWithoutLeakingIt() {
        assertThat(RetryTaskRepository.savedId("00000000-0000-0000-0000-000000000001"))
                .isEqualTo(new UUID(0, 1));
        for (String invalid : List.of(
                "1-1-1-1-1", "00000000-0000-0000-0000-00000000000A", "UUID-SENTINEL")) {
            assertThatThrownBy(() -> RetryTaskRepository.savedId(invalid))
                    .isInstanceOfSatisfying(TensorException.class,
                            error -> assertThat(error.code()).isEqualTo(ErrorCode.RETRY_TASK_INVALID))
                    .hasMessage("Saved retry task is invalid")
                    .hasMessageNotContaining("SENTINEL")
                    .hasNoCause();
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void headersTasksAndPagesDefensivelyCopyNestedCollections() {
        List<Object> nested = new ArrayList<>(List.of("before"));
        Map<String, Object> params = new java.util.LinkedHashMap<>();
        params.put("nested", nested);
        Header header = new Header(UUID.randomUUID(), DATASET, params, CLOCK.instant(), CLOCK.instant());
        List<Item> items = new ArrayList<>();
        Task task = new Task(header, items);
        List<Task> tasks = new ArrayList<>(List.of(task));
        Page page = new Page(tasks, 1, 20, 1, 1);

        nested.set(0, "after");
        params.put("later", true);
        items.add(new Item(new ItemKey(header.taskId(), FAILURE.selector()),
                ErrorCode.SOURCE_TIMEOUT, "safe", CLOCK.instant()));
        tasks.clear();

        assertThat((List<Object>) header.taskParams().get("nested")).containsExactly("before");
        assertThat(header.taskParams()).doesNotContainKey("later");
        assertThat(task.items()).isEmpty();
        assertThat(page.items()).containsExactly(task);
        assertThatThrownBy(() -> page.items().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void readOperationsUseRequiredRepeatableReadReadOnlySixtySecondTransactions() {
        RetryTaskRepository repository = mock(RetryTaskRepository.class);
        when(repository.findHeader(any())).thenReturn(Optional.empty());
        when(repository.count(any())).thenReturn(0L);
        CountingTransactions transactions = new CountingTransactions(false);
        RetryTaskStorageService service = service(repository, transactions);

        assertThat(service.find(UUID.randomUUID())).isEmpty();
        assertThat(service.list(Criteria.defaults())).isEqualTo(new Page(List.of(), 1, 20, 0, 0));

        assertThat(transactions.definitions()).hasSize(2).allSatisfy(definition -> {
            assertThat(definition.getPropagationBehavior()).isEqualTo(TransactionDefinition.PROPAGATION_REQUIRED);
            assertThat(definition.getIsolationLevel()).isEqualTo(TransactionDefinition.ISOLATION_REPEATABLE_READ);
            assertThat(definition.getTimeout()).isEqualTo(60);
            assertThat(definition.isReadOnly()).isTrue();
        });
    }

    @Test
    void missingTasksAndQueryFailuresUseFixedSafeTensorErrors() {
        RetryTaskRepository repository = mock(RetryTaskRepository.class);
        when(repository.lockTask(any())).thenReturn(Optional.empty());
        CountingTransactions transactions = new CountingTransactions(false);
        RetryTaskStorageService service = service(repository, transactions);

        assertThatThrownBy(() -> service.append(UUID.randomUUID(), FAILURE))
                .isInstanceOfSatisfying(TensorException.class,
                        error -> assertThat(error.code()).isEqualTo(ErrorCode.RETRY_TASK_NOT_FOUND))
                .hasMessage("Retry task was not found")
                .hasNoCause();

        doThrow(new DataAccessResourceFailureException("password=CREDENTIAL_SENTINEL SELECT"))
                .when(repository).findHeader(any());
        assertThatThrownBy(() -> service.find(UUID.randomUUID()))
                .isInstanceOfSatisfying(TensorException.class,
                        error -> assertThat(error.code()).isEqualTo(ErrorCode.QUERY_FAILED))
                .hasMessage("Retry task query failed")
                .hasNoCause();
        assertThat(transactions.begun()).isEqualTo(2);
    }

    private static RetryTaskStorageService service(
            RetryTaskRepository repository, PlatformTransactionManager transactions) {
        return new RetryTaskStorageService(repository, transactions, CLOCK);
    }

    private static void assertUnconfirmed(ThrowingCall call) {
        assertThatThrownBy(call::run)
                .isInstanceOfSatisfying(TensorException.class,
                        error -> assertThat(error.code()).isEqualTo(ErrorCode.TASK_RECORD_SAVE_UNCONFIRMED))
                .hasMessage("Failure record save is unconfirmed")
                .hasNoCause()
                .hasMessageNotContaining("CREDENTIAL_SENTINEL")
                .hasMessageNotContaining("SELECT")
                .hasMessageNotContaining("SQL");
    }

    @FunctionalInterface
    private interface ThrowingCall {
        void run();
    }

    private static final class CountingTransactions implements PlatformTransactionManager {
        private final boolean failCommit;
        private final AtomicInteger begun = new AtomicInteger();
        private final AtomicInteger committed = new AtomicInteger();
        private final List<TransactionDefinition> definitions = new ArrayList<>();

        private CountingTransactions(boolean failCommit) {
            this.failCommit = failCommit;
        }

        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) {
            begun.incrementAndGet();
            definitions.add(definition);
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus status) {
            committed.incrementAndGet();
            if (failCommit) {
                throw new TransactionSystemException("commit completed then acknowledgement failed");
            }
        }

        @Override
        public void rollback(TransactionStatus status) {}

        private int begun() {
            return begun.get();
        }

        private int committed() {
            return committed.get();
        }

        private List<TransactionDefinition> definitions() {
            return List.copyOf(definitions);
        }
    }

    private static final class CountingDataSource extends AbstractDataSource {
        private final AtomicInteger connections = new AtomicInteger();

        @Override
        public Connection getConnection() throws SQLException {
            connections.incrementAndGet();
            throw new SQLException("No connection expected");
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            return getConnection();
        }

        private int connections() {
            return connections.get();
        }
    }
}
