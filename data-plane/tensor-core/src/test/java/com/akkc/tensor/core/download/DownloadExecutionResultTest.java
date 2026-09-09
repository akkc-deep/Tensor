package com.akkc.tensor.core.download;

import static org.assertj.core.api.Assertions.*;
import com.akkc.tensor.plugin.api.model.*;
import com.akkc.tensor.plugin.api.download.RecoverySelector;
import com.akkc.tensor.plugin.api.error.ErrorCode;
import java.util.*;
import org.junit.jupiter.api.Test;
import static com.akkc.tensor.core.download.DownloadExecutionResult.*;

class DownloadExecutionResultTest {
    private final RecoverySelector scope = new RecoverySelector(RecoverySelector.TargetType.REQUEST, "", RecoverySelector.TimeType.DATE, "2026-09-03");
    @Test void copiesCollectionsAndRejectsNullsAndIllegalCombinations() {
        var failures = new ArrayList<RecoveryUnitProcessor.Failure>();
        failures.add(new RecoveryUnitProcessor.Failure(scope, ErrorCode.SOURCE_TIMEOUT, "Source timed out"));
        var result = result(Outcome.FAILED, 0, 0, 1, failures);
        failures.clear();
        assertThat(result.failures()).hasSize(1);
        assertThatThrownBy(() -> result.failures().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> result(Outcome.SUCCESS, 0, 1, 0, List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> result(Outcome.EMPTY, 1, 1, 0, List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> result(Outcome.FAILED, 0, 0, 2, result.failures())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> result(Outcome.PARTIAL, 0, 0, 1, result.failures())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> result(Outcome.NO_OPEN_DATES, 0, 0, 0, List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> result(Outcome.EMPTY, -1, 0, 0, List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> result(Outcome.EMPTY, 1, 0, 0, Arrays.asList((RecoveryUnitProcessor.Failure) null))).isInstanceOf(NullPointerException.class);
    }
    @Test void exceptionOnlyAcceptsStoppedUnconfirmedResultsAndSafeCodes() {
        var snapshot = new DownloadExecutionResult(RequestId.newId(), Outcome.UNCONFIRMED, PluginId.of("fixture"), ApiName.of("daily"),
                0, 0, 0, "结果未确认；计数仅包含此前确认项", 0, 0, 0L, 0, null, 0L, FailureRecordStatus.UNCONFIRMED,
                List.of(), List.of(), List.of(scope));
        for (var code : List.of(ErrorCode.PERSISTENCE_FAILED, ErrorCode.TASK_RECORD_SAVE_UNCONFIRMED, ErrorCode.COMMIT_UNCONFIRMED, ErrorCode.INTERNAL_ERROR)) {
            var error = new DownloadExecutionException(code, snapshot);
            assertThat(error.downloadResult()).isSameAs(snapshot);
            assertThat(error).hasNoCause();
        }
        assertThatThrownBy(() -> new DownloadExecutionException(ErrorCode.SOURCE_TIMEOUT, snapshot)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DownloadExecutionException(ErrorCode.INTERNAL_ERROR, result(Outcome.EMPTY, 1, 0, 0, List.of()))).isInstanceOf(IllegalArgumentException.class);
    }
    private DownloadExecutionResult result(Outcome outcome, long completed, long rows, long failed, List<RecoveryUnitProcessor.Failure> failures) {
        return new DownloadExecutionResult(RequestId.newId(), outcome, PluginId.of("fixture"), ApiName.of("daily"),
                rows, 0, 0, "Result", completed, failed, 0L, 0, failed == 0 ? null : UUID.randomUUID(), failed,
                failed == 0 ? FailureRecordStatus.NOT_REQUIRED : FailureRecordStatus.CONFIRMED, failures, List.of(), List.of());
    }
}
