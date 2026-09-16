package com.akkc.tensor.plugin.tushare.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
record TushareResponse(Integer code, String msg, TushareData data) {
    private static final String REDACTED_TEXT = "TushareResponse[REDACTED]";

    @Override
    public String toString() {
        return REDACTED_TEXT;
    }
}
