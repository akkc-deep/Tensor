package com.akkc.tensor.plugin.tushare.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
record TushareData(List<String> fields, List<List<Object>> items) {
    @Override
    public String toString() {
        return "TushareData[REDACTED]";
    }
}
