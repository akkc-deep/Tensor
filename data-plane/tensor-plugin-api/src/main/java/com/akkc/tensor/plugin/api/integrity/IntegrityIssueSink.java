package com.akkc.tensor.plugin.api.integrity;

/** Core binds check/result/rule identity and enforces the cumulative issue budget. */
@FunctionalInterface
public interface IntegrityIssueSink {
    void add(IntegrityIssue issue);
}
