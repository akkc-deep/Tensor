package com.akkc.tensor.plugin.api.integrity;

import java.util.*;

public record IntegrityRuleDescriptor(String ruleId, String version, String displayName,
        Dimension dimension, List<String> requiredColumns, List<IntegrityDependency> dependencies,
        String description) {
    public enum Dimension { COVERAGE, KEY, FIELD }
    public IntegrityRuleDescriptor {
        if (!IntegrityValues.text(ruleId).matches("[a-z][a-z0-9_.-]*"))
            throw new IllegalArgumentException("Invalid rule ID");
        IntegrityValues.text(version);
        IntegrityValues.text(displayName);
        Objects.requireNonNull(dimension, "dimension");
        requiredColumns = IntegrityValues.columns(requiredColumns);
        dependencies = List.copyOf(dependencies);
        IntegrityValues.text(description);
    }
}
