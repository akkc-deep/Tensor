package com.akkc.tensor.plugin.fixture.integrity;

import com.akkc.tensor.plugin.api.integrity.IntegrityContext;
import com.akkc.tensor.plugin.api.integrity.IntegrityEvidence;
import com.akkc.tensor.plugin.api.integrity.IntegrityIssueSink;
import com.akkc.tensor.plugin.api.integrity.IntegrityRule;
import com.akkc.tensor.plugin.api.integrity.IntegrityRuleDescriptor;
import com.akkc.tensor.plugin.api.integrity.IntegrityRuleResult;
import com.akkc.tensor.plugin.api.integrity.IntegrityScope;
import com.akkc.tensor.plugin.api.integrity.IntegrityStatistics;
import com.akkc.tensor.plugin.api.integrity.IntegrityStatus;
import java.util.List;

/** Synthetic acceptance-only proof that an independent FIELD rule traverses the generic report path. */
public final class FixtureIntegrityExtensionRule implements IntegrityRule {
    static final IntegrityRuleDescriptor DESCRIPTOR = new IntegrityRuleDescriptor(
            "fixture.acceptance.extension", "1", "Fixture 验收扩展",
            IntegrityRuleDescriptor.Dimension.FIELD, List.of(), List.of(),
            "仅证明 acceptance fixture 可注册独立通用规则");

    @Override
    public IntegrityRuleDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public IntegrityRuleResult evaluate(IntegrityScope scope, IntegrityContext context, IntegrityIssueSink issues) {
        var evidence = List.of(new IntegrityEvidence("fixture:synthetic-acceptance-extension", "1", scope.range(),
                scope.snapshotStartedAt(), "验收扩展规则已执行；仅为合成 acceptance 证据"));
        return new IntegrityRuleResult(DESCRIPTOR, IntegrityStatus.PASS, "FIXTURE_EXTENSION_VERIFIED",
                "验收扩展规则已执行", new IntegrityStatistics(null, null, null, null, null, null, 0L), evidence);
    }
}
