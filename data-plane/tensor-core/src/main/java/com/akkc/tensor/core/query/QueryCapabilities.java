package com.akkc.tensor.core.query;

import com.akkc.tensor.plugin.api.dataset.DatasetDefinition;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public final class QueryCapabilities {
    private static final Set<String> SUPPORTED_FILTERS = Set.of("ts_code", "trade_date", "ann_date");

    private QueryCapabilities() {
    }

    public static boolean supports(DatasetDefinition definition) {
        return SUPPORTED_FILTERS.containsAll(filterNames(definition));
    }

    static boolean accepts(DatasetDefinition definition, QueryCriteria criteria) {
        Set<String> filters = filterNames(definition);
        return (criteria.tsCode() == null || filters.contains("ts_code"))
                && (criteria.tradeDateFrom() == null && criteria.tradeDateTo() == null
                        || filters.contains("trade_date"))
                && (criteria.annDateFrom() == null && criteria.annDateTo() == null
                        || filters.contains("ann_date"));
    }

    static Set<String> filterNames(DatasetDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        return definition.filters().stream()
                .map(filter -> filter.field())
                .collect(Collectors.toUnmodifiableSet());
    }
}
