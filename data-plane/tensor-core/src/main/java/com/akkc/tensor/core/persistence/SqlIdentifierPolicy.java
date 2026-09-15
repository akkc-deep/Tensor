package com.akkc.tensor.core.persistence;

import com.akkc.tensor.plugin.api.constant.ValidationConstants;
import java.util.Objects;

public final class SqlIdentifierPolicy {
    public String quote(String identifier) {
        Objects.requireNonNull(identifier, "identifier");
        if (!identifier.matches(ValidationConstants.IDENTIFIER_REGEX)) {
            throw new IllegalArgumentException("Invalid SQL identifier");
        }
        return "`" + identifier + "`";
    }
}
