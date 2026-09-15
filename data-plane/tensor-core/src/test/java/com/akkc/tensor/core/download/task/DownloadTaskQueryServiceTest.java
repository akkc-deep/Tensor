package com.akkc.tensor.core.download.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.akkc.tensor.plugin.api.download.batch.DownloadMode;
import com.akkc.tensor.plugin.api.error.ErrorCode;
import com.akkc.tensor.plugin.api.error.TensorException;
import com.akkc.tensor.plugin.api.model.ApiName;
import com.akkc.tensor.plugin.api.model.DatasetKey;
import com.akkc.tensor.plugin.api.model.PluginId;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class DownloadTaskQueryServiceTest {
    final DownloadTaskRepository repository = mock(DownloadTaskRepository.class);
    final DownloadTaskQueryService service = new DownloadTaskQueryService(repository);
    final UUID id = UUID.randomUUID();
    final DownloadTaskRepository.Counts zero = new DownloadTaskRepository.Counts(0, 0, 0, 0, 0, 0, 0, 0, 0);

    @Test
    void detailReturnsOneSnapshotWithoutInferringSuccessFromZeroBatches() {
        var snapshot = new DownloadTaskRepository.TaskSnapshot(Optional.of(task()), zero);
        when(repository.snapshot(id)).thenReturn(snapshot);

        var result = service.detail(id);

        assertThat(result).isSameAs(snapshot);
        assertThat(result.task().orElseThrow().status()).isEqualTo(DownloadTask.Status.QUEUED);
        assertThat(result.task().orElseThrow().planReady()).isFalse();
        verify(repository).snapshot(id);
        verifyNoMoreInteractions(repository);
    }

    @Test
    void missingDetailAndBatchesAreNotFoundButSubmissionLookupCanBeEmpty() {
        when(repository.snapshot(id)).thenReturn(new DownloadTaskRepository.TaskSnapshot(Optional.empty(), zero));
        code(ErrorCode.TASK_NOT_FOUND, () -> service.detail(id));
        code(ErrorCode.TASK_NOT_FOUND, () -> service.batches(id, null, 1, 20));
        assertThat(service.findSubmission(id)).isEmpty();
        verify(repository, never()).batches(any(), any(), anyInt(), anyInt());
    }

    @Test
    void queryFailuresKeepClassificationButInvalidArgumentsAreSanitized() {
        when(repository.tasks(null, 0, 20)).thenThrow(new IllegalArgumentException("private-input"));
        code(ErrorCode.PARAM_INVALID, () -> service.tasks(null, 0, 20));
        var failure = new TensorException(ErrorCode.QUERY_FAILED, "Query failed") {};
        when(repository.snapshot(id)).thenThrow(failure);
        assertThatThrownBy(() -> service.detail(id)).isSameAs(failure);
    }

    @Test
    void tailPagesRemainEmptyAndPreserveRequestedPage() {
        var empty = new DownloadTaskRepository.Page<DownloadTask>(12, List.of());
        when(repository.tasks(null, 99, 50)).thenReturn(empty);
        assertThat(service.tasks(null, 99, 50)).isEqualTo(empty);
        verify(repository).tasks(null, 99, 50);
    }

    @Test
    void nullIdentityIsInvalidAndAllPublicUsesRejectOuterTransactions() {
        code(ErrorCode.PARAM_INVALID, () -> service.detail(null));
        code(ErrorCode.PARAM_INVALID, () -> service.findSubmission(null));
        code(ErrorCode.PARAM_INVALID, () -> service.batches(null, null, 1, 20));
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertThatThrownBy(() -> service.detail(id)).isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> service.findSubmission(id)).isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> service.tasks(null, 1, 20)).isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> service.batches(id, null, 1, 20)).isInstanceOf(IllegalStateException.class);
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
        verifyNoInteractions(repository);
    }

    private DownloadTask task() {
        var now = Instant.parse("2026-09-11T00:00:00Z");
        return new DownloadTask(id, UUID.randomUUID(), "a".repeat(64),
                DatasetKey.of(PluginId.of("test"), ApiName.of("daily")), DownloadMode.SINGLE,
                Map.of(), "b".repeat(64), "{\"mode\":\"SINGLE\",\"schemaVersion\":1}",
                DownloadTask.Status.QUEUED, false, UUID.randomUUID(), 0, 1, 0, 0,
                null, now, now, now, null, null, null);
    }

    private void code(ErrorCode expected, org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action).isInstanceOf(TensorException.class)
                .hasMessageNotContaining("private-input").hasNoCause()
                .extracting(e -> ((TensorException) e).code()).isEqualTo(expected);
    }
}
