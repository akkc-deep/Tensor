package com.akkc.tensor.plugin.api.dataset;

import com.akkc.tensor.plugin.api.constant.ValidationConstants;
import com.akkc.tensor.plugin.api.constant.ValidationMessages;
import com.akkc.tensor.plugin.api.descriptor.ParameterDescriptor;
import com.akkc.tensor.plugin.api.descriptor.QueryMode;
import com.akkc.tensor.plugin.api.model.DatasetKey;
import com.akkc.tensor.plugin.api.model.TableName;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;

public record DatasetDefinition(
        DatasetKey datasetKey,
        String displayName,
        String category,
        QueryMode queryMode,
        List<ParameterDescriptor> parameters,
        TableName tableName,
        List<ColumnDefinition> columns,
        BusinessKeyDefinition businessKey,
        List<FilterDefinition> filters,
        String fixedColumn,
        int batchSize
) {
    private static final int MAX_BATCH_SIZE = 500;
    private static final int DEFAULT_BATCH_SIZE = MAX_BATCH_SIZE;
    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile(ValidationConstants.IDENTIFIER_REGEX);

    public DatasetDefinition {
        Objects.requireNonNull(datasetKey, "datasetKey");
        requireNonBlank(displayName, "displayName");
        if (displayName.codePointCount(0, displayName.length()) > 128) {
            throw new IllegalArgumentException("displayName must be at most 128 characters");
        }
        requireNonBlank(category, "category");
        if (category.codePointCount(0, category.length()) > ValidationConstants.MAX_CATEGORY_LENGTH) {
            throw new IllegalArgumentException(ValidationMessages.CATEGORY_TOO_LONG);
        }
        Objects.requireNonNull(queryMode, "queryMode");
        parameters = List.copyOf(Objects.requireNonNull(parameters, "parameters"));
        Objects.requireNonNull(tableName, "tableName");
        columns = List.copyOf(Objects.requireNonNull(columns, "columns"));
        Objects.requireNonNull(businessKey, "businessKey");
        filters = List.copyOf(Objects.requireNonNull(filters, "filters"));

        if (columns.isEmpty()) {
            throw new IllegalArgumentException(ValidationMessages.COLUMNS_EMPTY);
        }
        rejectDuplicates(parameters, ParameterDescriptor::name, "parameters");
        rejectDuplicates(columns, ColumnDefinition::name, "columns");
        rejectDuplicates(filters, FilterDefinition::field, "filters");
        if (!tableName.equals(TableName.from(datasetKey))) {
            throw new IllegalArgumentException(ValidationMessages.TABLE_NAME_MISMATCH);
        }

        Set<String> columnNames = columns.stream()
                .map(ColumnDefinition::name)
                .collect(java.util.stream.Collectors.toSet());
        requireReferences(columnNames, businessKey.fields(), "businessKey");
        requireReferences(columnNames, filters.stream().map(FilterDefinition::field).toList(), "filters");
        if (fixedColumn != null) {
            if (!IDENTIFIER_PATTERN.matcher(fixedColumn).matches()) {
                throw new IllegalArgumentException("Invalid fixedColumn: " + fixedColumn);
            }
            if (!columnNames.contains(fixedColumn)) {
                throw new IllegalArgumentException("fixedColumn must reference a column");
            }
        }
        if (batchSize < 1 || batchSize > MAX_BATCH_SIZE) {
            throw new IllegalArgumentException("batchSize must be between 1 and 500");
        }
    }

    public DatasetDefinition(
            DatasetKey datasetKey,
            String displayName,
            String category,
            QueryMode queryMode,
            List<ParameterDescriptor> parameters,
            TableName tableName,
            List<ColumnDefinition> columns,
            BusinessKeyDefinition businessKey,
            List<FilterDefinition> filters,
            String fixedColumn) {
        this(datasetKey, displayName, category, queryMode, parameters, tableName, columns,
                businessKey, filters, fixedColumn, DEFAULT_BATCH_SIZE);
    }

    private static <T> void rejectDuplicates(List<T> values, Function<T, String> name, String component) {
        if (values.stream().map(name).collect(java.util.stream.Collectors.toSet()).size() != values.size()) {
            throw new IllegalArgumentException(component + " must not contain duplicate names");
        }
    }

    private static void requireReferences(Set<String> columns, List<String> references, String component) {
        if (!columns.containsAll(references)) {
            throw new IllegalArgumentException(component + " must reference declared columns");
        }
    }

    private static void requireNonBlank(String value, String component) {
        Objects.requireNonNull(value, component);
        if (value.isBlank()) {
            throw new IllegalArgumentException(component + ValidationMessages.MUST_NOT_BE_BLANK);
        }
    }
}
