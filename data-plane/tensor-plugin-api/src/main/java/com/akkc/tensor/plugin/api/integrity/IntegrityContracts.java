package com.akkc.tensor.plugin.api.integrity;

import com.akkc.tensor.plugin.api.dataset.ColumnDefinition;
import com.akkc.tensor.plugin.api.dataset.DatasetDefinition;
import com.akkc.tensor.plugin.api.dataset.LogicalType;
import com.akkc.tensor.plugin.api.model.DatasetKey;
import java.util.*;
import static com.akkc.tensor.plugin.api.integrity.IntegrityRuleDescriptor.Dimension.*;

/** Pure metadata checks shared by capability discovery and snapshot creation. */
public final class IntegrityContracts {
    private IntegrityContracts() {}

    public static void validate(IntegrityDescriptor descriptor, List<IntegrityRule> implementations,
            Map<DatasetKey, DatasetDefinition> definitions) {
        validateDescriptor(descriptor, definitions);
        var actual = implementations.stream().map(IntegrityRule::descriptor).toList();
        if (actual.size() != descriptor.rules().size()
                || !new HashSet<>(actual).equals(new HashSet<>(descriptor.rules())))
            throw new IllegalArgumentException("Rule implementations do not match descriptors");
    }

    public static void validateDescriptor(IntegrityDescriptor descriptor,
            Map<DatasetKey, DatasetDefinition> definitions) {
        var definition = definition(descriptor.datasetKey(), definitions);
        var columns = new HashMap<String, ColumnDefinition>();
        definition.columns().forEach(column -> columns.put(column.name(), column));
        if (descriptor.symbolField() != null) {
            var column = columns.get(descriptor.symbolField());
            if (column == null || column.logicalType() != LogicalType.STRING && column.logicalType() != LogicalType.ENUM)
                throw new IllegalArgumentException("Stock column must be STRING or ENUM");
        }
        if (descriptor.dateField() != null) {
            var column = columns.get(descriptor.dateField());
            if (column == null || column.logicalType() != LogicalType.DATE)
                throw new IllegalArgumentException("Date axis must be DATE");
        }
        for (var dependency : descriptor.dependencies()) validateDependency(dependency, definitions);
        for (var rule : descriptor.rules()) {
            if (rule.ruleId().startsWith("core.")) throw new IllegalArgumentException("Reserved core rule ID");
            requireColumns(definition, rule.requiredColumns());
            for (var dependency : rule.dependencies()) {
                validateDependency(dependency, definitions);
                if (descriptor.dependencies().stream().noneMatch(declared ->
                        declared.datasetKey().equals(dependency.datasetKey())
                                && declared.columns().containsAll(dependency.columns())
                                && declared.purpose().equals(dependency.purpose())))
                    throw new IllegalArgumentException("Undeclared rule dependency");
            }
        }
    }

    /** Source rule IDs are unique across the entire plugin, including different APIs. */
    public static void validatePlugin(List<IntegrityDescriptor> descriptors) {
        var ids = new HashSet<String>();
        var datasets = new HashSet<DatasetKey>();
        for (var descriptor : descriptors) {
            if (!datasets.add(descriptor.datasetKey())) throw new IllegalArgumentException("Duplicate dataset");
            for (var rule : descriptor.rules())
                if (!ids.add(rule.ruleId())) throw new IllegalArgumentException("Duplicate plugin rule ID");
        }
    }

    /** Metadata only. Implementations of these rules belong to the core executor (T04). */
    public static List<IntegrityRuleDescriptor> coreRules(DatasetDefinition definition) {
        return List.of(
                new IntegrityRuleDescriptor("core.required-fields", "1", "必填字段", FIELD,
                        definition.columns().stream().filter(c -> !c.nullable()).map(ColumnDefinition::name).toList(),
                        List.of(), "按 nullable 合同检查必填字段；无行时 N/A/NO_ROWS"),
                new IntegrityRuleDescriptor("core.business-key", "1", "完整业务键", KEY,
                        definition.businessKey().fields(), List.of(), "检查完整业务键，指纹复用 FingerprintKeyCodec"),
                new IntegrityRuleDescriptor("core.source-identity", "1", "来源身份", FIELD,
                        List.of(), List.of(), "检查物理元数据 source_plugin/source_api；无行时 N/A/NO_ROWS"));
    }

    private static void validateDependency(IntegrityDependency dependency, Map<DatasetKey, DatasetDefinition> definitions) {
        requireColumns(definition(dependency.datasetKey(), definitions), dependency.columns());
    }
    private static DatasetDefinition definition(DatasetKey key, Map<DatasetKey, DatasetDefinition> definitions) {
        var definition = definitions.get(key);
        if (definition == null || !key.equals(definition.datasetKey()))
            throw new IllegalArgumentException("Missing or mismatched dataset definition");
        return definition;
    }
    private static void requireColumns(DatasetDefinition definition, List<String> columns) {
        var names = definition.columns().stream().map(ColumnDefinition::name).toList();
        if (!names.containsAll(columns)) throw new IllegalArgumentException("Dependency column is missing");
    }
}
