package com.akkc.tensor.core.integrity;

import com.akkc.tensor.plugin.api.dataset.DatasetDefinition;
import java.util.*;

/** Logical keys are independent of damaged stored fingerprints and preserve physical row order. */
final class IntegrityTargetIndex {
    final Map<IntegrityBusinessKeys.Key, Map<String,Object>> actual;
    final Set<IntegrityBusinessKeys.Key> unresolved;
    final boolean invalidKeys;
    final boolean unresolvedDates;

    IntegrityTargetIndex(DatasetDefinition definition, List<IntegrityReadRepository.TargetBatch> batches, IntegrityReadBudget budget) {
        var keys = new IntegrityBusinessKeys(definition);
        var actual = new LinkedHashMap<IntegrityBusinessKeys.Key,Map<String,Object>>();
        var unresolved = new LinkedHashSet<IntegrityBusinessKeys.Key>();
        boolean invalid = false, dates = false;
        for (var batch : batches) for (var row : batch.rows()) {
            budget.check();
            dates |= batch.dateScopeUnresolved();
            try {
                var key = keys.normalizeLogical(row);
                if (batch.dateScopeUnresolved()) unresolved.add(key);
                else actual.putIfAbsent(key, row);
            } catch (IllegalArgumentException exception) { invalid = true; }
            budget.check();
        }
        this.actual = Collections.unmodifiableMap(actual); this.unresolved = Collections.unmodifiableSet(unresolved);
        this.invalidKeys = invalid; this.unresolvedDates = dates;
    }
    long size() { return actual.size(); }
    boolean complete() { return !invalidKeys && !unresolvedDates; }
}
