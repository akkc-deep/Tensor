package com.akkc.tensor.core.integrity;

import com.akkc.tensor.plugin.api.dataset.DatasetDefinition;
import com.akkc.tensor.plugin.api.integrity.*;
import java.util.*;

/** Unit-owned issue identities and irreversible issue-limit latch. */
final class IntegrityIssueCollector {
    private final IntegrityScope scope;
    private final IntegrityDescriptor target;
    private final IntegrityBusinessKeys keys;
    private final IntegrityReadBudget budget;
    private final int limit;
    private final LinkedHashMap<Identity, IntegrityUnitEvaluator.BoundIssue> issues = new LinkedHashMap<>();
    private IntegrityReadException failure;

    IntegrityIssueCollector(IntegrityScope scope, IntegrityDescriptor target, DatasetDefinition definition,
            IntegrityReadBudget budget, int limit) {
        this.scope = scope; this.target = target; this.keys = new IntegrityBusinessKeys(definition);
        this.budget = budget; this.limit = limit;
    }

    Sink forRule(IntegrityRuleDescriptor descriptor) { check(); return new Sink(descriptor); }
    void check() { if (failure != null) throw failure; budget.check(); }
    IntegrityReadException failure() { return failure; }
    List<IntegrityUnitEvaluator.BoundIssue> snapshot(boolean incomplete) {
        return issues.values().stream().map(bound -> {
            if (!incomplete) { check(); return bound; }
            return new IntegrityUnitEvaluator.BoundIssue(bound.ruleId(), bound.ruleVersion(),
                    copy(bound.issue(), bound.issue().businessKey(), true));
        }).toList();
    }
    List<IntegrityIssue> forDescriptor(IntegrityRuleDescriptor descriptor) {
        return issues.values().stream().filter(i -> i.ruleId().equals(descriptor.ruleId())
                && i.ruleVersion().equals(descriptor.version())).map(IntegrityUnitEvaluator.BoundIssue::issue).toList();
    }

    final class Sink implements IntegrityIssueSink, AutoCloseable {
        private final IntegrityRuleDescriptor descriptor;
        private final Thread owner = Thread.currentThread();
        private boolean closed;
        private IllegalArgumentException invalid;
        Sink(IntegrityRuleDescriptor descriptor) { this.descriptor = descriptor; }
        void requireOpen() {
            if (closed || owner != Thread.currentThread()) throw new IllegalStateException("Integrity rule invocation is closed");
            check();
            if (invalid != null) throw invalid;
        }
        @Override public void add(IntegrityIssue issue) {
            requireOpen();
            try {
                if (!Objects.equals(issue.symbol(), scope.symbol()) || !issue.apiName().equals(scope.datasetKey().apiName())
                        || !Objects.equals(issue.dateField(), target.dateField()))
                    throw new IllegalArgumentException("Invalid issue ownership");
                Map<String,Object> normalized = issue.businessKey();
                boolean gap = issue.type() == IntegrityIssue.Type.MISSING || issue.type() == IntegrityIssue.Type.SUSPECTED_MISSING
                        || issue.type() == IntegrityIssue.Type.EXTRA;
                if (gap || !normalized.isEmpty()) {
                    try { normalized = keys.normalize(normalized, true).fields(); }
                    catch (IllegalArgumentException exception) {
                        if (gap) throw new IllegalArgumentException("Invalid issue business key");
                    }
                }
                if (gap) {
                    if (normalized.containsKey(target.symbolField())
                            && !Objects.equals(normalized.get(target.symbolField()), scope.symbol()))
                        throw new IllegalArgumentException("Invalid issue key stock");
                    if (target.dateField() != null && normalized.containsKey(target.dateField())) {
                        Object date = normalized.get(target.dateField());
                        if (!(date instanceof java.time.LocalDate day) || !day.equals(issue.date())
                                || day.isBefore(scope.startDate()) || day.isAfter(scope.endDate()))
                            throw new IllegalArgumentException("Invalid issue key date");
                    }
                }
                issue = copy(issue, normalized, issue.incomplete());
                Identity identity = identity(descriptor, issue);
                boolean missing = issue.type() == IntegrityIssue.Type.MISSING;
                boolean suspected = issue.type() == IntegrityIssue.Type.SUSPECTED_MISSING;
                if (missing || suspected) {
                    Identity confirmed = identity.withType(IntegrityIssue.Type.MISSING);
                    Identity candidate = identity.withType(IntegrityIssue.Type.SUSPECTED_MISSING);
                    if (suspected && issues.containsKey(confirmed)) return;
                    if (missing && issues.containsKey(candidate)) issues.remove(candidate);
                }
                if (!issues.containsKey(identity) && issues.size() == limit) {
                    failure = new IntegrityReadException("ISSUE_LIMIT_EXCEEDED", "Integrity issue limit exceeded");
                    throw failure;
                }
                issues.putIfAbsent(identity, new IntegrityUnitEvaluator.BoundIssue(descriptor.ruleId(), descriptor.version(), issue));
                check();
            } catch (IllegalArgumentException exception) { invalid = exception; throw exception; }
        }
        @Override public void close() { closed = true; }
    }

    private record Identity(String id, String version, IntegrityIssue.Type type, String dateField,
            java.time.LocalDate date, Map<String,Object> key, String field, String reason, List<IntegrityEvidence> evidence) {
        Identity withType(IntegrityIssue.Type replacement) { return new Identity(id, version, replacement, dateField, date, key, field, reason, evidence); }
    }
    private static Identity identity(IntegrityRuleDescriptor descriptor, IntegrityIssue issue) {
        boolean range = issue.businessKey().isEmpty();
        return new Identity(descriptor.ruleId(), descriptor.version(), issue.type(), issue.dateField(), issue.date(),
                issue.businessKey(), issue.field(), range ? issue.reasonCode() : null, range ? issue.evidence() : List.of());
    }
    private static IntegrityIssue copy(IntegrityIssue issue, Map<String,Object> key, boolean incomplete) {
        return new IntegrityIssue(issue.type(), issue.status(), issue.symbol(), issue.apiName(), issue.dateField(), issue.date(), key,
                issue.field(), issue.relatedDates(), issue.reasonCode(), issue.message(), issue.evidence(), incomplete);
    }
}
