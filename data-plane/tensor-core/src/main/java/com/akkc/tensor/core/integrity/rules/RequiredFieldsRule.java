package com.akkc.tensor.core.integrity.rules;

import com.akkc.tensor.core.integrity.IntegrityReadRepository.TargetBatch;
import com.akkc.tensor.plugin.api.dataset.DatasetDefinition;
import com.akkc.tensor.plugin.api.dataset.LogicalType;
import com.akkc.tensor.plugin.api.integrity.IntegrityContext;
import com.akkc.tensor.plugin.api.integrity.IntegrityIssue;
import com.akkc.tensor.plugin.api.integrity.IntegrityIssueSink;
import com.akkc.tensor.plugin.api.integrity.IntegrityRule;
import com.akkc.tensor.plugin.api.integrity.IntegrityRuleDescriptor;
import com.akkc.tensor.plugin.api.integrity.IntegrityRuleResult;
import com.akkc.tensor.plugin.api.integrity.IntegrityScope;
import com.akkc.tensor.plugin.api.integrity.IntegrityStatus;
import java.util.List;

public final class RequiredFieldsRule implements IntegrityRule {
    private final CoreRuleSupport support;
    private final IntegrityRuleDescriptor descriptor;

    public RequiredFieldsRule(DatasetDefinition definition, List<TargetBatch> batches) {
        this(definition, batches, () -> {});
    }

    public RequiredFieldsRule(DatasetDefinition definition, List<TargetBatch> batches, Runnable budgetCheck) {
        this(definition, batches, budgetCheck, CoreRuleSupport.inferredDateField(definition));
    }

    public RequiredFieldsRule(DatasetDefinition definition, List<TargetBatch> batches,
            Runnable budgetCheck, String dateField) {
        support = new CoreRuleSupport(definition, batches, budgetCheck, dateField);
        descriptor = support.descriptor("core.required-fields");
    }

    @Override public IntegrityRuleDescriptor descriptor() { return descriptor; }

    @Override
    public IntegrityRuleResult evaluate(IntegrityScope scope, IntegrityContext context, IntegrityIssueSink issues) {
        support.budgetCheck.run();
        if (!support.hasRows()) {
            return result(IntegrityStatus.NOT_APPLICABLE, "NO_ROWS", 0L);
        }
        long count = 0;
        for (TargetBatch batch : support.batches) {
            for (var row : batch.rows()) {
                support.budgetCheck.run();
                for (var column : support.definition.columns()) {
                    support.budgetCheck.run();
                    if (column.nullable()) continue;
                    Object value = row.get(column.name());
                    boolean missing = value == null || (column.logicalType() == LogicalType.STRING
                            || column.logicalType() == LogicalType.ENUM) && value instanceof String text
                            && text.trim().isEmpty();
                    if (missing) {
                        issues.add(support.issue(scope, row, IntegrityIssue.Type.REQUIRED_FIELD_MISSING,
                                IntegrityStatus.FAIL, column.name(), "REQUIRED_FIELD_MISSING",
                                "必填字段缺失"));
                        count++;
                    }
                }
                support.budgetCheck.run();
            }
        }
        support.budgetCheck.run();
        return count == 0 ? result(IntegrityStatus.PASS, "REQUIRED_FIELDS_VALID", 0L)
                : result(IntegrityStatus.FAIL, "REQUIRED_FIELD_MISSING", count);
    }

    private IntegrityRuleResult result(IntegrityStatus status, String reason, long count) {
        return new IntegrityRuleResult(descriptor, status, reason, "按 nullable 合同检查必填字段",
                CoreRuleSupport.statistics(count), List.of());
    }
}
