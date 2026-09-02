package com.akkc.tensor.core.persistence;

import java.util.Objects;

public final class SqlIdentifierPolicy {
    public String quote(String identifier) {
        Objects.requireNonNull(identifier, "identifier");
        if (!identifier.matches("^[a-z][a-z0-9_]{1,63}$")) {
            throw new IllegalArgumentException("Invalid SQL identifier");
        }
        return "`" + identifier + "`";
    }
}
