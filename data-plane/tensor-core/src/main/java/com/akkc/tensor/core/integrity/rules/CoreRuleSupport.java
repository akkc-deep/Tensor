package com.akkc.tensor.core.integrity.rules;

import com.akkc.tensor.core.integrity.IntegrityBusinessKeys;
import com.akkc.tensor.core.integrity.IntegrityReadRepository.TargetBatch;
import com.akkc.tensor.plugin.api.dataset.DatasetDefinition;
import com.akkc.tensor.plugin.api.dataset.LogicalType;
import com.akkc.tensor.plugin.api.integrity.IntegrityIssue;
import com.akkc.tensor.plugin.api.integrity.IntegrityRuleDescriptor;
import com.akkc.tensor.plugin.api.integrity.IntegrityScope;
import com.akkc.tensor.plugin.api.integrity.IntegrityStatistics;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class CoreRuleSupport {
    final DatasetDefinition definition;
    final List<TargetBatch> batches;
    final Runnable budgetCheck;
    final String dateField;
    final IntegrityBusinessKeys keys;

    CoreRuleSupport(DatasetDefinition definition, List<TargetBatch> batches,
            Runnable budgetCheck, String dateField) {
        this.definition = Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(batches, "batches");
        this.budgetCheck = Objects.requireNonNull(budgetCheck, "budgetCheck");
        this.dateField = dateField;
        this.keys = new IntegrityBusinessKeys(definition);
        ArrayList<TargetBatch> copied = new ArrayList<>();
        for (TargetBatch batch : batches) {
            this.budgetCheck.run();
            ArrayList<Map<String, Object>> rows = new ArrayList<>();
            for (Map<String, Object> row : batch.rows()) {
                this.budgetCheck.run();
                rows.add(Collections.unmodifiableMap(new LinkedHashMap<>(row)));
                this.budgetCheck.run();
            }
            copied.add(new TargetBatch(batch.dateScopeUnresolved(), rows));
        }
        this.batches = List.copyOf(copied);
    }

    static String inferredDateField(DatasetDefinition definition) {
        return definition.columns().stream().filter(column -> column.logicalType() == LogicalType.DATE)
                .map(column -> column.name()).findFirst().orElse(null);
    }

    boolean hasRows() {
        return batches.stream().anyMatch(batch -> !batch.rows().isEmpty());
    }

    IntegrityRuleDescriptor descriptor(String id) {
        return com.akkc.tensor.plugin.api.integrity.IntegrityContracts.coreRules(definition).stream()
                .filter(rule -> rule.ruleId().equals(id)).findFirst().orElseThrow();
    }

    Map<String, Object> businessKey(Map<String, Object> row) {
        try {
            return keys.normalize(row, false).fields();
        } catch (IllegalArgumentException ignored) {
            definition.businessKey().fields().forEach(field -> budgetCheck.run());
            return keys.locatableFields(row);
        }
    }

    IntegrityIssue issue(IntegrityScope scope, Map<String, Object> row, IntegrityIssue.Type type,
            com.akkc.tensor.plugin.api.integrity.IntegrityStatus status, String field,
            String reasonCode, String message) {
        LocalDate date = dateField != null && row.get(dateField) instanceof LocalDate value ? value : null;
        LinkedHashMap<String, LocalDate> related = new LinkedHashMap<>();
        definition.columns().stream().filter(column -> column.logicalType() == LogicalType.DATE)
                .filter(column -> !column.name().equals(dateField)).forEach(column -> {
                    budgetCheck.run();
                    if (row.get(column.name()) instanceof LocalDate value) related.put(column.name(), value);
                });
        return new IntegrityIssue(type, status, scope.symbol(), definition.datasetKey().apiName(), dateField,
                date, businessKey(row), field, related, reasonCode, message, List.of(), false);
    }

    static IntegrityStatistics statistics(Long requiredFields) {
        return new IntegrityStatistics(null, null, null, null, null, null, requiredFields);
    }
}
