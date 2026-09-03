package com.akkc.tensor.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record DownloadRequest(
        @NotBlank @Pattern(regexp = "^[a-z][a-z0-9_]{1,63}$") String pluginId,
        @NotBlank @Pattern(regexp = "^[a-z][a-z0-9_]{1,63}$") String apiName,
        @NotNull Map<String, Object> params) {
    public DownloadRequest {
        if (params != null) {
            params = Collections.unmodifiableMap(new LinkedHashMap<>(params));
        }
    }
}
