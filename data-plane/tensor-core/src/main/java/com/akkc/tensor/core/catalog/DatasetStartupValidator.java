package com.akkc.tensor.core.catalog;

import com.akkc.tensor.core.catalog.SchemaInspector.ColumnMetadata;
import com.akkc.tensor.core.catalog.SchemaInspector.TableSchema;
import com.akkc.tensor.plugin.api.dataset.BusinessKeyMode;
import com.akkc.tensor.plugin.api.dataset.ColumnDefinition;
import com.akkc.tensor.plugin.api.dataset.DatasetDefinition;
import com.akkc.tensor.plugin.api.descriptor.ParameterDescriptor;
import com.akkc.tensor.plugin.api.descriptor.ParameterType;
import com.akkc.tensor.plugin.api.model.DatasetKey;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class DatasetStartupValidator {
    private static final System.Logger LOGGER = System.getLogger(DatasetStartupValidator.class.getName());
    private static final String DISABLED_WARNING = "Dataset disabled by startup validation";
    private static final String DUPLICATE_WARNING = "Duplicate dataset key disabled";

    private final List<DatasetDefinition> definitions;
    private final SchemaInspector schemaInspector;

    public DatasetStartupValidator(List<DatasetDefinition> definitions, SchemaInspector schemaInspector) {
        Objects.requireNonNull(definitions, "definitions");
        this.definitions = Collections.unmodifiableList(new ArrayList<>(definitions));
        this.schemaInspector = Objects.requireNonNull(schemaInspector, "schemaInspector");
    }

    public DatasetCatalog validate() {
        Map<DatasetKey, Integer> counts = new LinkedHashMap<>();
        for (DatasetDefinition definition : definitions) {
            if (definition != null) {
                counts.merge(definition.datasetKey(), 1, Integer::sum);
            }
        }

        Set<DatasetKey> warnedDuplicates = new HashSet<>();
        List<DatasetDefinition> accepted = new ArrayList<>();
        for (DatasetDefinition definition : definitions) {
            if (definition == null) {
                warn(DISABLED_WARNING);
                continue;
            }
            DatasetKey key = definition.datasetKey();
            if (counts.get(key) > 1) {
                if (warnedDuplicates.add(key)) {
                    warn(DUPLICATE_WARNING);
                }
                continue;
            }
            if (!validDefinition(definition)) {
                warn(DISABLED_WARNING);
                continue;
            }
            var actual = schemaInspector.inspect(definition.tableName());
            if (actual.isEmpty() || !matches(definition, actual.orElseThrow())) {
                warn(DISABLED_WARNING);
                continue;
            }
            accepted.add(definition);
        }
        accepted.sort(java.util.Comparator
                .comparing((DatasetDefinition value) -> value.datasetKey().pluginId().value())
                .thenComparing(value -> value.datasetKey().apiName().value()));
        return new DatasetCatalog(accepted);
    }

    private static boolean validDefinition(DatasetDefinition definition) {
        for (int index = 0; index < definition.columns().size(); index++) {
            if (definition.columns().get(index).displayOrder() != index) {
                return false;
            }
        }
        Map<String, ParameterDescriptor> parameters = new LinkedHashMap<>();
        definition.parameters().forEach(parameter -> parameters.put(parameter.name(), parameter));
        for (ParameterDescriptor parameter : definition.parameters()) {
            String related = parameter.relatedParameter();
            if (related != null && !parameters.containsKey(related)) {
                return false;
            }
            if (parameter.type() == ParameterType.DATE_RANGE_MEMBER) {
                ParameterDescriptor peer = parameters.get(related);
                if (peer == null || peer.type() != ParameterType.DATE_RANGE_MEMBER
                        || !parameter.name().equals(peer.relatedParameter())) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean matches(DatasetDefinition definition, TableSchema actual) {
        Set<String> actualColumns = new HashSet<>();
        actual.columns().forEach(column -> actualColumns.add(column.name()));
        if (!actualColumns.containsAll(actual.primaryKey()) || actual.uniqueKeys().stream()
                .flatMap(key -> key.columns().stream()).anyMatch(column -> !actualColumns.contains(column))) {
            return false;
        }

        List<ColumnMetadata> expectedColumns = new ArrayList<>();
        definition.columns().stream().map(DatasetStartupValidator::expectedColumn).forEach(expectedColumns::add);
        if (definition.businessKey().mode() == BusinessKeyMode.FINGERPRINT) {
            expectedColumns.add(new ColumnMetadata("business_key", Types.CHAR, false));
        }
        expectedColumns.add(new ColumnMetadata("source_plugin", Types.VARCHAR, false));
        expectedColumns.add(new ColumnMetadata("source_api", Types.VARCHAR, false));
        expectedColumns.add(new ColumnMetadata("ingested_at", Types.TIMESTAMP, false));
        if (!actual.columns().equals(expectedColumns)) {
            return false;
        }

        List<String> expectedPrimary = definition.businessKey().mode() == BusinessKeyMode.FINGERPRINT
                ? List.of("business_key") : definition.businessKey().fields();
        return actual.primaryKey().equals(expectedPrimary) && actual.uniqueKeys().isEmpty();
    }

    private static ColumnMetadata expectedColumn(ColumnDefinition column) {
        int jdbcType = switch (column.logicalType()) {
            case STRING -> Types.VARCHAR;
            case TEXT -> Types.LONGVARCHAR;
            case DATE -> Types.DATE;
            case MONTH, ENUM -> Types.CHAR;
            case LONG -> Types.BIGINT;
            case DECIMAL -> Types.DECIMAL;
        };
        return new ColumnMetadata(column.name(), jdbcType, column.nullable());
    }

    private static void warn(String message) {
        LOGGER.log(System.Logger.Level.WARNING, message);
    }
}
