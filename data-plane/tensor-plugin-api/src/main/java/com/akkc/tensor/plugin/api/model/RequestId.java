package com.akkc.tensor.plugin.api.model;

import java.util.Objects;
import java.util.UUID;

public record RequestId(UUID value) {
    public RequestId {
        Objects.requireNonNull(value, "value");
    }

    public static RequestId newId() {
        return new RequestId(UUID.randomUUID());
    }
}
