package com.akkc.tensor.core.integrity.rules;

import com.akkc.tensor.core.integrity.IntegrityReadRepository.TargetBatch;
import com.akkc.tensor.plugin.api.constant.DatasetFields;
import com.akkc.tensor.plugin.api.dataset.DatasetDefinition;
import com.akkc.tensor.plugin.api.integrity.IntegrityContext;
import com.akkc.tensor.plugin.api.integrity.IntegrityIssue;
import com.akkc.tensor.plugin.api.integrity.IntegrityIssueSink;
import com.akkc.tensor.plugin.api.integrity.IntegrityRule;
import com.akkc.tensor.plugin.api.integrity.IntegrityRuleDescriptor;
import com.akkc.tensor.plugin.api.integrity.IntegrityRuleResult;
import com.akkc.tensor.plugin.api.integrity.IntegrityScope;
import com.akkc.tensor.plugin.api.integrity.IntegrityStatus;
import java.util.List;

public final class SourceIdentityRule implements IntegrityRule {
    private final CoreRuleSupport support;
    private final IntegrityRuleDescriptor descriptor;

    public SourceIdentityRule(DatasetDefinition definition, List<TargetBatch> batches) {
        this(definition, batches, () -> {});
    }

    public SourceIdentityRule(DatasetDefinition definition, List<TargetBatch> batches, Runnable budgetCheck) {
        this(definition, batches, budgetCheck, CoreRuleSupport.inferredDateField(definition));
    }

    public SourceIdentityRule(DatasetDefinition definition, List<TargetBatch> batches,
            Runnable budgetCheck, String dateField) {
        support = new CoreRuleSupport(definition, batches, budgetCheck, dateField);
        descriptor = support.descriptor("core.source-identity");
    }

    @Override public IntegrityRuleDescriptor descriptor() { return descriptor; }

    @Override
    public IntegrityRuleResult evaluate(IntegrityScope scope, IntegrityContext context, IntegrityIssueSink issues) {
        support.budgetCheck.run();
        if (!support.hasRows()) return result(IntegrityStatus.NOT_APPLICABLE, "NO_ROWS");
        boolean failed = false;
        for (TargetBatch batch : support.batches) {
            for (var row : batch.rows()) {
                support.budgetCheck.run();
                failed |= mismatch(scope, row, issues, DatasetFields.SOURCE_PLUGIN,
                        support.definition.datasetKey().pluginId().value());
                failed |= mismatch(scope, row, issues, DatasetFields.SOURCE_API,
                        support.definition.datasetKey().apiName().value());
                support.budgetCheck.run();
            }
        }
        support.budgetCheck.run();
        return failed ? result(IntegrityStatus.FAIL, "SOURCE_IDENTITY_MISMATCH")
                : result(IntegrityStatus.PASS, "SOURCE_IDENTITY_VALID");
    }

    private boolean mismatch(IntegrityScope scope, java.util.Map<String, Object> row,
            IntegrityIssueSink issues, String field, String expected) {
        support.budgetCheck.run();
        if (expected.equals(row.get(field))) return false;
        issues.add(support.issue(scope, row, IntegrityIssue.Type.SOURCE_IDENTITY_MISMATCH,
                IntegrityStatus.FAIL, field, "SOURCE_IDENTITY_MISMATCH", "来源身份不匹配"));
        return true;
    }

    private IntegrityRuleResult result(IntegrityStatus status, String reason) {
        return new IntegrityRuleResult(descriptor, status, reason, "检查物理来源身份",
                CoreRuleSupport.statistics(null), List.of());
    }
}
