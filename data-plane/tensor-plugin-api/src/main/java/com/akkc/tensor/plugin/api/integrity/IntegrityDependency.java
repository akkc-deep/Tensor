package com.akkc.tensor.plugin.api.integrity;

import com.akkc.tensor.plugin.api.model.DatasetKey;
import java.util.*;

/** Declared reference projection and its purpose; does not grant arbitrary table access. */
public record IntegrityDependency(DatasetKey datasetKey, List<String> columns, String purpose) {
    public IntegrityDependency {
        Objects.requireNonNull(datasetKey, "datasetKey");
        columns = IntegrityValues.columns(columns);
        if (columns.isEmpty()) throw new IllegalArgumentException("Reference columns are required");
        IntegrityValues.text(purpose);
    }
}
