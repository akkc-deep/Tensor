package com.akkc.tensor.plugin.tushare.integrity;

import com.akkc.tensor.plugin.api.integrity.*;
import com.akkc.tensor.plugin.api.model.DatasetKey;
import java.util.List;

/** No expected-set evidence is inferred from local rows or successful downloads. */
final class TushareIntegrityCoverageRule implements IntegrityRule {
    private final DatasetKey datasetKey;
    private final IntegrityRuleDescriptor descriptor;
    private final String reason;

    TushareIntegrityCoverageRule(DatasetKey datasetKey, IntegrityRuleDescriptor descriptor, String reason) {
        this.datasetKey = datasetKey;
        this.descriptor = descriptor;
        this.reason = reason;
    }

    @Override
    public IntegrityRuleDescriptor descriptor() { return descriptor; }

    @Override
    public IntegrityRuleResult evaluate(IntegrityScope scope, IntegrityContext context, IntegrityIssueSink issues) {
        if (!datasetKey.equals(scope.datasetKey()) || scope.symbol() == null || scope.snapshotStartedAt() == null)
            throw new IllegalArgumentException("Coverage requires a started stock scope for its dataset");
        return new IntegrityRuleResult(descriptor, IntegrityStatus.UNKNOWN, reason, descriptor.description(),
                new IntegrityStatistics(null, null, null, null, null, null, null),
                List.of(new IntegrityEvidence("tushare-local-policy:" + datasetKey.apiName().value(), descriptor.version(),
                        scope.range(), scope.snapshotStartedAt(), descriptor.description())));
    }
}
