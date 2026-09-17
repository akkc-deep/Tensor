package com.akkc.tensor.plugin.api.integrity;

import com.akkc.tensor.plugin.api.model.DatasetKey;
import java.time.ZoneId;
import java.util.*;

public record IntegrityDescriptor(DatasetKey datasetKey, ScopeKind scopeKind,
        String symbolField, String dateField, String dateLabel, ZoneId marketZone,
        String capabilityVersion, List<IntegrityDependency> dependencies,
        List<IntegrityRuleDescriptor> rules, List<String> limitations) {
    public enum ScopeKind { STOCK_DATE, STOCK_SNAPSHOT, NON_STOCK }
    public IntegrityDescriptor {
        Objects.requireNonNull(datasetKey, "datasetKey");
        Objects.requireNonNull(scopeKind, "scopeKind");
        Objects.requireNonNull(marketZone, "marketZone");
        IntegrityValues.text(dateLabel);
        IntegrityValues.text(capabilityVersion);
        dependencies = List.copyOf(dependencies);
        rules = List.copyOf(rules);
        limitations = List.copyOf(limitations);
        limitations.forEach(IntegrityValues::text);
        var ids = new HashSet<String>();
        for (var rule : rules) if (!ids.add(rule.ruleId())) throw new IllegalArgumentException("Duplicate rule ID");
        if (scopeKind == ScopeKind.NON_STOCK) {
            if (symbolField != null || dateField != null || !rules.isEmpty())
                throw new IllegalArgumentException("NON_STOCK cannot execute stock rules");
        } else {
            IntegrityValues.column(symbolField);
            if (scopeKind == ScopeKind.STOCK_DATE) IntegrityValues.column(dateField);
            else if (dateField != null) throw new IllegalArgumentException("Snapshot has no historical date axis");
            if (rules.stream().filter(r -> r.dimension() == IntegrityRuleDescriptor.Dimension.COVERAGE).count() != 1)
                throw new IllegalArgumentException("Stock scope requires exactly one coverage rule");
        }
    }
}
