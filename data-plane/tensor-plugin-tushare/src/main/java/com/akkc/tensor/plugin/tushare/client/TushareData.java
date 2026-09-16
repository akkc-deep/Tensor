package com.akkc.tensor.plugin.tushare.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
record TushareData(List<String> fields, List<List<Object>> items) {
    private static final String REDACTED_TEXT = "TushareData[REDACTED]";

    @Override
    public String toString() {
        return REDACTED_TEXT;
    }
}
