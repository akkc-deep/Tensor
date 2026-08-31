package com.akkc.tensor.plugin.tushare.metadata;

import com.akkc.tensor.plugin.api.dataset.BusinessKeyDefinition;
import com.akkc.tensor.plugin.api.dataset.BusinessKeyMode;
import com.akkc.tensor.plugin.api.dataset.ColumnDefinition;
import com.akkc.tensor.plugin.api.dataset.DatasetDefinition;
import com.akkc.tensor.plugin.api.dataset.FilterDefinition;
import com.akkc.tensor.plugin.api.dataset.LogicalType;
import com.akkc.tensor.plugin.api.descriptor.ParameterDescriptor;
import com.akkc.tensor.plugin.api.descriptor.ParameterType;
import com.akkc.tensor.plugin.api.descriptor.QueryMode;
import com.akkc.tensor.plugin.api.error.ErrorCode;
import com.akkc.tensor.plugin.api.error.TensorException;
import com.akkc.tensor.plugin.api.model.ApiName;
import com.akkc.tensor.plugin.api.model.DatasetKey;
import com.akkc.tensor.plugin.api.model.PluginId;
import com.akkc.tensor.plugin.api.model.TableName;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion.VersionFlag;
import com.networknt.schema.ValidationMessage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;

public final class DatasetDefinitionLoader {
    private static final String SCHEMA_RESOURCE = "contracts/dataset-definition.schema.json";
    private static final String SCHEMA_NAME = "dataset-definition.schema.json";
    private static final String TUSHARE_PRO = "tushare_pro";
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final ObjectMapper YAML = new ObjectMapper(YAMLFactory.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .build()).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    public List<DatasetDefinition> loadAll(ResourcePatternResolver resolver, String pattern) {
        Objects.requireNonNull(resolver, "resolver");
        if (pattern == null || pattern.isBlank()) {
            throw new IllegalArgumentException("pattern must not be blank");
        }

        List<Diagnostic> diagnostics = new ArrayList<>();
        JsonSchema schema = loadSchema(diagnostics);
        if (!diagnostics.isEmpty()) {
            throw misconfigured(diagnostics);
        }

        Resource[] resources;
        try {
            resources = resolver.getResources(pattern);
        } catch (IOException exception) {
            diagnostics.add(new Diagnostic("<pattern>", "resource cannot be read"));
            throw misconfigured(diagnostics);
        }
        if (resources.length == 0) {
            diagnostics.add(new Diagnostic("<pattern>", "no resources matched"));
            throw misconfigured(diagnostics);
        }

        List<LoadedDefinition> loaded = new ArrayList<>();
        for (Resource resource : resources) {
            loadResource(resource, schema, diagnostics, loaded);
        }
        addDuplicateApiDiagnostics(loaded, diagnostics);
        if (!diagnostics.isEmpty()) {
            throw misconfigured(diagnostics);
        }
        return loaded.stream()
                .map(LoadedDefinition::definition)
                .sorted(Comparator.comparing(value -> value.datasetKey().apiName().value()))
                .collect(java.util.stream.Collectors.toUnmodifiableList());
    }

    private JsonSchema loadSchema(List<Diagnostic> diagnostics) {
        try (InputStream input = DatasetDefinitionLoader.class.getClassLoader().getResourceAsStream(SCHEMA_RESOURCE)) {
            if (input == null) {
                diagnostics.add(new Diagnostic(SCHEMA_NAME, "resource cannot be read"));
                return null;
            }
            return JsonSchemaFactory.getInstance(VersionFlag.V202012).getSchema(JSON.readTree(input));
        } catch (Exception exception) {
            diagnostics.add(new Diagnostic(SCHEMA_NAME, schemaReason(exception)));
            return null;
        }
    }

    private void loadResource(Resource resource, JsonSchema schema, List<Diagnostic> diagnostics, List<LoadedDefinition> loaded) {
        String resourceName = resource.getFilename() == null ? "<unnamed-resource>" : resource.getFilename();
        JsonNode node;
        try (InputStream input = resource.getInputStream()) {
            node = YAML.readTree(input);
        } catch (Exception exception) {
            diagnostics.add(new Diagnostic(resourceName, readReason(exception)));
            return;
        }
        Set<ValidationMessage> messages = schema.validate(node);
        if (!messages.isEmpty()) {
            messages.stream()
                    .map(ValidationMessage::getMessage)
                    .map(DatasetDefinitionLoader::normalize)
                    .forEach(reason -> diagnostics.add(new Diagnostic(resourceName, reason)));
            return;
        }
        try {
            RawDefinition raw = YAML.treeToValue(node, RawDefinition.class);
            validateSemantics(resourceName, raw, diagnostics);
            loaded.add(new LoadedDefinition(resourceName, map(raw)));
        } catch (Exception exception) {
            diagnostics.add(new Diagnostic(resourceName, safeReason(exception)));
        }
    }

    private void validateSemantics(String resourceName, RawDefinition raw, List<Diagnostic> diagnostics) {
        if (!TUSHARE_PRO.equals(raw.pluginId())) {
            diagnostics.add(new Diagnostic(resourceName, "pluginId must equal tushare_pro"));
        }
        Set<String> parameters = raw.parameters().stream().map(RawParameter::name)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        for (RawParameter parameter : raw.parameters()) {
            if (parameter.relatedParameter() != null && !parameters.contains(parameter.relatedParameter())) {
                diagnostics.add(new Diagnostic(resourceName,
                        "relatedParameter must reference a declared parameter: " + parameter.relatedParameter()));
            }
        }
        for (int index = 0; index < raw.columns().size(); index++) {
            RawColumn column = raw.columns().get(index);
            if (column.displayOrder() != index) {
                diagnostics.add(new Diagnostic(resourceName,
                        "displayOrder must equal column index: " + index));
            }
            if (column.scale() != null && column.precision() != null && column.scale() > column.precision()) {
                diagnostics.add(new Diagnostic(resourceName, "scale must not exceed precision"));
            }
        }
    }

    private DatasetDefinition map(RawDefinition raw) {
        DatasetKey key = new DatasetKey(new PluginId(raw.pluginId()), new ApiName(raw.apiName()));
        return new DatasetDefinition(
                key,
                raw.displayName(),
                raw.category(),
                QueryMode.valueOf(raw.queryMode()),
                raw.parameters().stream().map(this::map).toList(),
                new TableName(raw.tableName()),
                raw.columns().stream().map(this::map).toList(),
                new BusinessKeyDefinition(BusinessKeyMode.valueOf(raw.businessKey().mode()), raw.businessKey().fields()),
                raw.filters().stream().map(FilterDefinition::new).toList(),
                raw.fixedColumn());
    }

    private ParameterDescriptor map(RawParameter parameter) {
        return new ParameterDescriptor(
                parameter.name(), parameter.label(), parameter.description(), ParameterType.valueOf(parameter.type()),
                parameter.required(), parameter.defaultValue(), listOrEmpty(parameter.allowedValues()), parameter.pattern(),
                parameter.relatedParameter());
    }

    private ColumnDefinition map(RawColumn column) {
        return new ColumnDefinition(
                column.name(), column.label(), LogicalType.valueOf(column.logicalType()), column.nullable(),
                column.displayOrder(), column.length(), column.precision(), column.scale(),
                listOrEmpty(column.allowedValues()), column.longText() != null && column.longText());
    }

    private static List<String> listOrEmpty(List<String> values) {
        return values == null ? List.of() : values;
    }

    private void addDuplicateApiDiagnostics(List<LoadedDefinition> loaded, List<Diagnostic> diagnostics) {
        Map<String, List<LoadedDefinition>> byApiName = new HashMap<>();
        for (LoadedDefinition value : loaded) {
            byApiName.computeIfAbsent(value.definition().datasetKey().apiName().value(), ignored -> new ArrayList<>()).add(value);
        }
        byApiName.forEach((apiName, definitions) -> {
            if (definitions.size() > 1) {
                definitions.forEach(value -> diagnostics.add(new Diagnostic(value.resourceName(), "duplicate apiName: " + apiName)));
            }
        });
    }

    private static DatasetMisconfiguredException misconfigured(List<Diagnostic> diagnostics) {
        String details = diagnostics.stream().distinct().sorted(Comparator
                        .comparing(Diagnostic::resourceName)
                        .thenComparing(Diagnostic::reason))
                .map(value -> "- " + value.resourceName() + ": " + value.reason())
                .collect(java.util.stream.Collectors.joining("\n"));
        return new DatasetMisconfiguredException("Invalid dataset definitions:\n" + details);
    }

    private static String readReason(Exception exception) {
        if (exception instanceof JsonProcessingException) {
            return safeReason(exception);
        }
        return "resource cannot be read";
    }

    private static String schemaReason(Exception exception) {
        if (exception instanceof JsonProcessingException) {
            return safeReason(exception);
        }
        return "resource cannot be read";
    }

    private static String safeReason(Exception exception) {
        if (exception instanceof JsonProcessingException processingException) {
            return normalize(processingException.getOriginalMessage());
        }
        String message = exception.getMessage();
        return message == null || message.isBlank() ? "resource cannot be read" : normalize(message);
    }

    private static String normalize(String value) {
        return WHITESPACE.matcher(value).replaceAll(" ").trim();
    }

    private record LoadedDefinition(String resourceName, DatasetDefinition definition) {
    }

    private record Diagnostic(String resourceName, String reason) {
    }

    private record RawDefinition(
            String pluginId,
            String apiName,
            String tableName,
            String category,
            String displayName,
            String queryMode,
            List<RawParameter> parameters,
            List<RawColumn> columns,
            RawBusinessKey businessKey,
            List<String> filters,
            String fixedColumn) {
    }

    private record RawParameter(
            String name,
            String label,
            String description,
            String type,
            boolean required,
            String defaultValue,
            List<String> allowedValues,
            String pattern,
            String relatedParameter) {
    }

    private record RawColumn(
            String name,
            String label,
            String logicalType,
            boolean nullable,
            int displayOrder,
            Integer length,
            Integer precision,
            Integer scale,
            List<String> allowedValues,
            Boolean longText) {
    }

    private record RawBusinessKey(String mode, List<String> fields) {
    }

    private static final class DatasetMisconfiguredException extends TensorException {
        DatasetMisconfiguredException(String message) {
            super(ErrorCode.DATASET_MISCONFIGURED, message);
        }
    }
}
