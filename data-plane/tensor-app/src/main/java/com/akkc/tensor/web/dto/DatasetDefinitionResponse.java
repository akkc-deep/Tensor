package com.akkc.tensor.web.dto;

import com.akkc.tensor.plugin.api.dataset.ColumnDefinition;
import com.akkc.tensor.plugin.api.dataset.DatasetDefinition;
import com.akkc.tensor.plugin.api.dataset.FilterDefinition;
import com.akkc.tensor.plugin.api.dataset.LogicalType;
import com.akkc.tensor.plugin.api.descriptor.QueryMode;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public record DatasetDefinitionResponse(
        String pluginId,
        String apiName,
        String displayName,
        String category,
        QueryMode queryMode,
        List<FilterResponse> filters,
        String fixedColumn,
        List<ColumnResponse> columns) {

    public DatasetDefinitionResponse {
        Objects.requireNonNull(pluginId, "pluginId");
        Objects.requireNonNull(apiName, "apiName");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(queryMode, "queryMode");
        filters = List.copyOf(Objects.requireNonNull(filters, "filters"));
        Objects.requireNonNull(fixedColumn, "fixedColumn");
        columns = List.copyOf(Objects.requireNonNull(columns, "columns"));
    }

    public static DatasetDefinitionResponse from(DatasetDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        return new DatasetDefinitionResponse(
                definition.datasetKey().pluginId().value(),
                definition.datasetKey().apiName().value(),
                definition.displayName(),
                definition.category(),
                definition.queryMode(),
                filterResponses(definition),
                fixedColumn(definition),
                definition.columns().stream()
                        .sorted(Comparator.comparingInt(ColumnDefinition::displayOrder))
                        .map(ColumnResponse::from)
                        .toList());
    }

    private static List<FilterResponse> filterResponses(DatasetDefinition definition) {
        return definition.filters().stream().map(FilterResponse::from).toList();
    }

    private static String fixedColumn(DatasetDefinition definition) {
        if (definition.fixedColumn() != null) {
            return definition.fixedColumn();
        }
        return definition.columns().stream()
                .min(Comparator.comparingInt(ColumnDefinition::displayOrder))
                .orElseThrow()
                .name();
    }

    public record DatasetSummary(
            String pluginId,
            String apiName,
            String displayName,
            String category,
            QueryMode queryMode,
            List<FilterResponse> filters,
            String fixedColumn) {

        public DatasetSummary {
            Objects.requireNonNull(pluginId, "pluginId");
            Objects.requireNonNull(apiName, "apiName");
            Objects.requireNonNull(displayName, "displayName");
            Objects.requireNonNull(category, "category");
            Objects.requireNonNull(queryMode, "queryMode");
            filters = List.copyOf(Objects.requireNonNull(filters, "filters"));
            Objects.requireNonNull(fixedColumn, "fixedColumn");
        }

        public static DatasetSummary from(DatasetDefinition definition) {
            Objects.requireNonNull(definition, "definition");
            return new DatasetSummary(
                    definition.datasetKey().pluginId().value(),
                    definition.datasetKey().apiName().value(),
                    definition.displayName(),
                    definition.category(),
                    definition.queryMode(),
                    filterResponses(definition),
                    DatasetDefinitionResponse.fixedColumn(definition));
        }
    }

    public record FilterResponse(String field, String operator, String controlType) {
        public FilterResponse {
            Objects.requireNonNull(field, "field");
            Objects.requireNonNull(operator, "operator");
            Objects.requireNonNull(controlType, "controlType");
        }

        public static FilterResponse from(FilterDefinition definition) {
            Objects.requireNonNull(definition, "definition");
            return switch (definition.field()) {
                case "ts_code" -> new FilterResponse("ts_code", "EQ", "TEXT");
                case "trade_date", "ann_date" ->
                    new FilterResponse(definition.field(), "BETWEEN", "DATE_RANGE");
                default -> throw new IllegalArgumentException("Unsupported dataset filter");
            };
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ColumnResponse(
            String name,
            String label,
            LogicalType logicalType,
            boolean nullable,
            int displayOrder,
            Integer length,
            Integer precision,
            Integer scale,
            List<String> allowedValues,
            boolean longText) {

        public ColumnResponse {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(label, "label");
            Objects.requireNonNull(logicalType, "logicalType");
            if (allowedValues != null) {
                allowedValues = List.copyOf(allowedValues);
            }
        }

        public static ColumnResponse from(ColumnDefinition definition) {
            Objects.requireNonNull(definition, "definition");
            return new ColumnResponse(
                    definition.name(),
                    definition.label(),
                    definition.logicalType(),
                    definition.nullable(),
                    definition.displayOrder(),
                    definition.length(),
                    definition.precision(),
                    definition.scale(),
                    definition.allowedValues().isEmpty() ? null : definition.allowedValues(),
                    definition.longText());
        }
    }
}
