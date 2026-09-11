package com.akkc.tensor.web.dto;

import com.akkc.tensor.core.download.task.DownloadTaskService.Submission;
import com.akkc.tensor.plugin.api.download.batch.DownloadMode;
import com.akkc.tensor.plugin.api.model.DatasetKey;
import com.akkc.tensor.web.download.DownloadParameters;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** A new typed request or an already verified historical submission. */
public sealed interface DownloadTaskRequest {
    record Bound(UUID submissionId, DatasetKey dataset, DownloadMode mode,
            DownloadParameters params, Set<String> suppliedFields) implements DownloadTaskRequest {
        public Bound {
            Objects.requireNonNull(submissionId);
            Objects.requireNonNull(dataset);
            Objects.requireNonNull(mode);
            Objects.requireNonNull(params);
            suppliedFields = Set.copyOf(suppliedFields);
        }
    }

    record Replay(Submission submission) implements DownloadTaskRequest {
        public Replay { Objects.requireNonNull(submission); }
    }
}
