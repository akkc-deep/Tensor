package com.akkc.tensor.web;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.akkc.tensor.core.download.task.*;
import com.akkc.tensor.observability.DownloadTaskOperationLogger;
import com.akkc.tensor.plugin.api.download.batch.DownloadMode;
import com.akkc.tensor.plugin.api.error.ErrorCode;
import com.akkc.tensor.plugin.api.model.*;
import com.akkc.tensor.web.download.DownloadParameterResolver;
import com.akkc.tensor.web.dto.DownloadTaskQuery;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;

class DownloadTaskControllerTest {
    @Test void keepsPageMembershipButMapsEveryRowFromItsOwnCompleteSnapshot() {
        var service = mock(DownloadTaskService.class);
        var queries = mock(DownloadTaskQueryService.class);
        var controller = new DownloadTaskController(service, queries, mock(DownloadParameterResolver.class), new DownloadTaskOperationLogger());
        var first = task(UUID.randomUUID(), DownloadTask.Status.RUNNING);
        var second = task(UUID.randomUUID(), DownloadTask.Status.RUNNING);
        var query = new DownloadTaskQuery.Tasks(3, 20, new DownloadTaskRepository.TaskFilter(null, null, DownloadTask.Status.RUNNING, null));
        when(queries.tasks(query.filter(), 3, 20)).thenReturn(new DownloadTaskRepository.Page<>(57, List.of(first, second)));
        when(queries.detail(first.taskId())).thenReturn(new DownloadTaskRepository.TaskSnapshot(Optional.of(task(first.taskId(), DownloadTask.Status.SUCCEEDED)),
                new DownloadTaskRepository.Counts(2, 0, 0, 2, 0, 1, 7, 6, 1)));
        when(queries.detail(second.taskId())).thenReturn(new DownloadTaskRepository.TaskSnapshot(Optional.of(second),
                new DownloadTaskRepository.Counts(3, 1, 1, 1, 0, 2, 2, 2, 0)));
        when(service.controls(any())).thenReturn(new DownloadTaskService.ControlAvailability(false, false));
        var result = controller.tasks(query);
        assertThat(result.page()).isEqualTo(3); assertThat(result.total()).isEqualTo(57);
        assertThat(result.items()).extracting(item -> item.taskId()).containsExactly(first.taskId(), second.taskId());
        assertThat(result.items().get(0).status()).isEqualTo(DownloadTask.Status.SUCCEEDED);
        assertThat(result.items().get(0).counts().succeededBatches()).isEqualTo(2);
        assertThat(result.items().get(1).counts().runningBatches()).isEqualTo(1);
        when(queries.detail(second.taskId())).thenThrow(new com.akkc.tensor.plugin.api.error.TensorException(ErrorCode.QUERY_FAILED, "Query failed") {});
        assertThatThrownBy(() -> controller.tasks(query)).isInstanceOf(com.akkc.tensor.plugin.api.error.TensorException.class);
    }

    static DownloadTask task(UUID id, DownloadTask.Status status) {
        var now = Instant.parse("2026-09-12T00:00:00Z");
        return new DownloadTask(id, UUID.randomUUID(), "private", DatasetKey.of(PluginId.of("task_test"), ApiName.of("prices")),
                DownloadMode.SINGLE, Map.of(), "private", "private", status, true, UUID.randomUUID(), 1, 4, 1, 1, null,
                now, now, now, now, null, now.plusSeconds(60));
    }
}
