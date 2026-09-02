package com.akkc.tensor.core.adapter;

import com.akkc.tensor.plugin.api.model.ApiName;
import java.util.Objects;

public record ConversionContext(ApiName apiName, int rowIndex) {
    public ConversionContext {
        Objects.requireNonNull(apiName, "apiName");
        if (rowIndex < 0) {
            throw new IllegalArgumentException("rowIndex must be non-negative");
        }
    }
}
