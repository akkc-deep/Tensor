package com.akkc.tensor.plugin.api.download;

import com.akkc.tensor.plugin.api.error.ErrorCode;
import com.akkc.tensor.plugin.api.error.SourceException;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

public record FetchResult(DownloadEnvelope envelope, List<UnitFailure> failures) {
    public FetchResult {
        Objects.requireNonNull(envelope, "envelope");
        failures = List.copyOf(Objects.requireNonNull(failures, "failures"));
        var selectors = new HashSet<RecoverySelector>();
        for (UnitFailure failure : failures) {
            if (!selectors.add(failure.selector())) throw new IllegalArgumentException("Duplicate unit failure selector");
        }
        if (envelope.status() == DownloadStatus.FAILURE && !failures.isEmpty()) {
            throw new IllegalArgumentException("Failed envelope cannot contain unit failures");
        }
    }

    public record UnitFailure(RecoverySelector selector, ErrorCode errorCode, String errorMessage) {
        public UnitFailure {
            Objects.requireNonNull(selector, "selector");
            Objects.requireNonNull(errorCode, "errorCode");
            Objects.requireNonNull(errorMessage, "errorMessage");
            if (errorCode == ErrorCode.SOURCE_REQUEST_UNCONFIRMED || errorMessage.length() > 512
                    || errorMessage.chars().anyMatch(Character::isISOControl)) {
                throw new IllegalArgumentException("Invalid unit failure summary or code");
            }
            new SourceException(errorCode, errorMessage);
        }
    }
}
