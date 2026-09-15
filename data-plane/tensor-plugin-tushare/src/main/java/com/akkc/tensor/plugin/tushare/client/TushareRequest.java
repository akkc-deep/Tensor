package com.akkc.tensor.plugin.tushare.client;

import com.akkc.tensor.plugin.tushare.TushareConstants;
import com.akkc.tensor.plugin.api.constant.RequestFields;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.Map;
import java.util.Objects;

@JsonPropertyOrder({TushareConstants.API_NAME_FIELD, "token", RequestFields.PARAMS, "fields"})
record TushareRequest(
        @JsonProperty(TushareConstants.API_NAME_FIELD) String apiName,
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
