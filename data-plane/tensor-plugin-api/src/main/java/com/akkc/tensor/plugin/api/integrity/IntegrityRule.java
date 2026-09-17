package com.akkc.tensor.plugin.api.integrity;

public interface IntegrityRule {
    IntegrityRuleDescriptor descriptor();
    IntegrityRuleResult evaluate(IntegrityScope scope, IntegrityContext context, IntegrityIssueSink issues);
}
