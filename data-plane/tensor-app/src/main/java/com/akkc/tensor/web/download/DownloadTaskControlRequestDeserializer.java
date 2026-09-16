package com.akkc.tensor.web.download;

import com.akkc.tensor.plugin.api.constant.RequestFields;
import com.akkc.tensor.web.dto.DownloadTaskControlRequest;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import java.io.IOException;
import java.util.Map;
import java.util.Set;

public final class DownloadTaskControlRequestDeserializer extends JsonDeserializer<DownloadTaskControlRequest> {
    @Override
    public DownloadTaskControlRequest deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        JsonNode input = DownloadTaskRequestDeserializer.object(parser, context, Set.of(RequestFields.EXPECTED_VERSION));
        JsonNode value = input.get(RequestFields.EXPECTED_VERSION);
        if (value == null || value.isNull()) DownloadTaskRequestDeserializer.reject(Map.of(RequestFields.EXPECTED_VERSION, false));
        if (!value.isIntegralNumber() || !value.canConvertToLong() || value.longValue() < 1)
            throw DownloadTaskRequestDeserializer.invalid(RequestFields.EXPECTED_VERSION);
        return new DownloadTaskControlRequest(value.longValue());
    }

    @Override
    public DownloadTaskControlRequest getNullValue(DeserializationContext context) {
        throw DownloadTaskRequestDeserializer.invalid(RequestFields.REQUEST);
    }
}
