package com.akkc.tensor.core.query;

import com.akkc.tensor.plugin.api.constant.DatasetFields;
import com.akkc.tensor.plugin.api.dataset.DatasetDefinition;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public final class QueryCapabilities {
    private static final Set<String> SUPPORTED_FILTERS = Set.of(DatasetFields.TS_CODE, DatasetFields.TRADE_DATE, DatasetFields.ANN_DATE);

    private QueryCapabilities() {
    }

    public static boolean supports(DatasetDefinition definition) {
        return SUPPORTED_FILTERS.containsAll(filterNames(definition));
    }

    static boolean accepts(DatasetDefinition definition, QueryCriteria criteria) {
        Set<String> filters = filterNames(definition);
        return (criteria.tsCode() == null || filters.contains(DatasetFields.TS_CODE))
                && (criteria.tradeDateFrom() == null && criteria.tradeDateTo() == null
                        || filters.contains(DatasetFields.TRADE_DATE))
                && (criteria.annDateFrom() == null && criteria.annDateTo() == null
                        || filters.contains(DatasetFields.ANN_DATE));
    }

    static Set<String> filterNames(DatasetDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        return definition.filters().stream()
                .map(filter -> filter.field())
                .collect(Collectors.toUnmodifiableSet());
    }
}
