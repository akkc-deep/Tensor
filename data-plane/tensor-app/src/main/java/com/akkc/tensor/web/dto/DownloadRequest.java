package com.akkc.tensor.web.dto;

import com.akkc.tensor.plugin.api.model.DatasetKey;
import com.akkc.tensor.web.download.DownloadParameters;
import java.util.Objects;
import java.util.Set;

public record DownloadRequest(
        DatasetKey dataset, DownloadParameters params, Set<String> suppliedFields) {
    public DownloadRequest {
        Objects.requireNonNull(dataset, "dataset");
        Objects.requireNonNull(params, "params");
        suppliedFields = Set.copyOf(suppliedFields);
    }
}
