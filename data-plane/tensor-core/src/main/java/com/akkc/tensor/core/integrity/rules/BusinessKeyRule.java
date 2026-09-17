package com.akkc.tensor.core.integrity.rules;

import com.akkc.tensor.core.integrity.IntegrityBusinessKeys;
import com.akkc.tensor.core.integrity.IntegrityReadRepository.TargetBatch;
import com.akkc.tensor.plugin.api.dataset.DatasetDefinition;
import com.akkc.tensor.plugin.api.integrity.IntegrityContext;
import com.akkc.tensor.plugin.api.integrity.IntegrityIssue;
import com.akkc.tensor.plugin.api.integrity.IntegrityIssueSink;
import com.akkc.tensor.plugin.api.integrity.IntegrityRule;
import com.akkc.tensor.plugin.api.integrity.IntegrityRuleDescriptor;
import com.akkc.tensor.plugin.api.integrity.IntegrityRuleResult;
import com.akkc.tensor.plugin.api.integrity.IntegrityScope;
import com.akkc.tensor.plugin.api.integrity.IntegrityStatus;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class BusinessKeyRule implements IntegrityRule {
    private final CoreRuleSupport support;
    private final IntegrityRuleDescriptor descriptor;

    public BusinessKeyRule(DatasetDefinition definition, List<TargetBatch> batches) {
        this(definition, batches, () -> {});
    }

    public BusinessKeyRule(DatasetDefinition definition, List<TargetBatch> batches, Runnable budgetCheck) {
        this(definition, batches, budgetCheck, CoreRuleSupport.inferredDateField(definition));
    }

    public BusinessKeyRule(DatasetDefinition definition, List<TargetBatch> batches,
            Runnable budgetCheck, String dateField) {
        support = new CoreRuleSupport(definition, batches, budgetCheck, dateField);
        descriptor = support.descriptor("core.business-key");
    }

    @Override public IntegrityRuleDescriptor descriptor() { return descriptor; }

    @Override
    public IntegrityRuleResult evaluate(IntegrityScope scope, IntegrityContext context, IntegrityIssueSink issues) {
        support.budgetCheck.run();
        if (!support.hasRows()) return result(IntegrityStatus.NOT_APPLICABLE, "NO_ROWS");
        boolean failed = false;
        Set<IntegrityBusinessKeys.Key> seen = new LinkedHashSet<>();
        for (TargetBatch batch : support.batches) {
            for (var row : batch.rows()) {
                support.budgetCheck.run();
                support.definition.businessKey().fields().forEach(ignored -> support.budgetCheck.run());
                IntegrityBusinessKeys.Key logical = null;
                try {
                    logical = support.keys.normalize(row, false);
                } catch (IntegrityBusinessKeys.InvalidKeyException exception) {
                    issues.add(support.issue(scope, row, IntegrityIssue.Type.BUSINESS_KEY_INVALID,
                            IntegrityStatus.FAIL, exception.field(), exception.reasonCode(), "业务键无效"));
                    failed = true;
                    try {
                        logical = support.keys.normalizeLogical(row);
                    } catch (IllegalArgumentException ignored) {
                        // An invalid logical key cannot participate in collision detection.
                    }
                }
                if (logical != null && !seen.add(logical)) {
                    issues.add(support.issue(scope, row, IntegrityIssue.Type.BUSINESS_KEY_INVALID,
                            IntegrityStatus.FAIL, null, "NORMALIZED_KEY_COLLISION", "规范化业务键碰撞"));
                    failed = true;
                }
                support.budgetCheck.run();
            }
        }
        support.budgetCheck.run();
        return failed ? result(IntegrityStatus.FAIL, "BUSINESS_KEY_INVALID")
                : result(IntegrityStatus.PASS, "BUSINESS_KEY_VALID");
    }

    private IntegrityRuleResult result(IntegrityStatus status, String reason) {
        return new IntegrityRuleResult(descriptor, status, reason, "检查完整业务键",
                CoreRuleSupport.statistics(null), List.of());
    }
}
