package com.akkc.tensor.plugin.tushare.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
record TushareResponse(Integer code, String msg, TushareData data) {
    @Override
    public String toString() {
        return "TushareResponse[REDACTED]";
    }
}
