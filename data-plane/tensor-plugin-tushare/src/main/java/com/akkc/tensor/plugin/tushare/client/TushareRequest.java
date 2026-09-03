package com.akkc.tensor.plugin.tushare.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.Map;
import java.util.Objects;

@JsonPropertyOrder({"api_name", "token", "params", "fields"})
record TushareRequest(
        @JsonProperty("api_name") String apiName,
        String token,
        Map<String, Object> params,
        String fields) {
    TushareRequest {
        Objects.requireNonNull(apiName, "apiName");
        Objects.requireNonNull(token, "token");
        params = Map.copyOf(Objects.requireNonNull(params, "params"));
        Objects.requireNonNull(fields, "fields");
    }

    @Override
    public String toString() {
        return "TushareRequest[REDACTED]";
    }
}
