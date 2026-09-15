package com.akkc.tensor.core.query;

import com.akkc.tensor.core.persistence.SqlConstants;
import com.akkc.tensor.core.persistence.SqlIdentifierPolicy;
import com.akkc.tensor.plugin.api.constant.DatasetFields;
import com.akkc.tensor.plugin.api.constant.StringConstants;
import com.akkc.tensor.plugin.api.dataset.BusinessKeyMode;
import com.akkc.tensor.plugin.api.dataset.DatasetDefinition;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public final class QuerySqlFactory {
    private final SqlIdentifierPolicy identifiers = new SqlIdentifierPolicy();

    public QuerySql create(DatasetDefinition definition, QueryCriteria criteria) {
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(criteria, "criteria");

        Set<String> filters = QueryCapabilities.filterNames(definition);
        if (!QueryCapabilities.supports(definition)) {
            throw new IllegalArgumentException("Unsupported dataset filter metadata");
        }

        List<String> conditions = new ArrayList<>();
        List<Object> values = new ArrayList<>();
        addTsCodeCondition(criteria, filters, conditions, values);
        addDateConditions(DatasetFields.TRADE_DATE, criteria.tradeDateFrom(), criteria.tradeDateTo(), filters, conditions, values);
        addDateConditions(DatasetFields.ANN_DATE, criteria.annDateFrom(), criteria.annDateTo(), filters, conditions, values);

        String where = conditions.isEmpty() ? StringConstants.EMPTY : SqlConstants.WHERE + String.join(SqlConstants.AND, conditions);
        String table = identifiers.quote(definition.tableName().value());
        String countSql = "SELECT COUNT(*) FROM " + table + where;
        String pageSql = SqlConstants.SELECT + selectColumns(definition) + SqlConstants.FROM + table + where + " ORDER BY "
                + orderColumns(definition) + " LIMIT ? OFFSET ?";
        List<Object> pageValues = new ArrayList<>(values);
        pageValues.add(criteria.pageSize());
        pageValues.add((long) (criteria.page() - 1) * criteria.pageSize());
        return new QuerySql(countSql, values, pageSql, pageValues);
    }

    private void addTsCodeCondition(QueryCriteria criteria, Set<String> filters, List<String> conditions, List<Object> values) {
        if (criteria.tsCode() != null) {
            requireDeclaredFilter(DatasetFields.TS_CODE, filters);
            conditions.add(identifiers.quote(DatasetFields.TS_CODE) + " = ?");
            values.add(criteria.tsCode());
        }
    }

    private void addDateConditions(String field, java.time.LocalDate from, java.time.LocalDate to, Set<String> filters,
            List<String> conditions, List<Object> values) {
        if (from == null && to == null) {
            return;
        }
        requireDeclaredFilter(field, filters);
        String column = identifiers.quote(field);
        if (from != null && to != null) {
            conditions.add(column + " BETWEEN ? AND ?");
            values.add(from);
            values.add(to);
        } else if (from != null) {
            conditions.add(column + " >= ?");
            values.add(from);
        } else {
            conditions.add(column + " <= ?");
            values.add(to);
        }
    }

    private void requireDeclaredFilter(String field, Set<String> filters) {
        if (!filters.contains(field)) {
            throw new IllegalArgumentException("Filter is not supported by dataset");
        }
    }

    private String selectColumns(DatasetDefinition definition) {
        List<String> columns = definition.columns().stream()
                .map(column -> identifiers.quote(column.name()))
                .collect(Collectors.toCollection(ArrayList::new));
        columns.add(identifiers.quote(DatasetFields.SOURCE_PLUGIN));
        columns.add(identifiers.quote(DatasetFields.SOURCE_API));
        columns.add(identifiers.quote(DatasetFields.INGESTED_AT));
        return String.join(SqlConstants.COLUMN_SEPARATOR, columns);
    }

    private String orderColumns(DatasetDefinition definition) {
        List<String> columns = definition.businessKey().fields().stream()
                .map(field -> identifiers.quote(field) + SqlConstants.ASCENDING)
                .collect(Collectors.toCollection(ArrayList::new));
        if (definition.businessKey().mode() == BusinessKeyMode.FINGERPRINT) {
            columns.add(identifiers.quote(DatasetFields.BUSINESS_KEY) + SqlConstants.ASCENDING);
        }
        return String.join(SqlConstants.COLUMN_SEPARATOR, columns);
    }
}
