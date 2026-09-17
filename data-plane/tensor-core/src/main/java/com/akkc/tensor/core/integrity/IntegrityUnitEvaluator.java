package com.akkc.tensor.core.integrity;

import com.akkc.tensor.core.integrity.rules.*;
import com.akkc.tensor.plugin.api.dataset.DatasetDefinition;
import com.akkc.tensor.plugin.api.integrity.*;
import java.time.Clock;
import java.util.*;

/** Synchronous, isolated unit evaluation. Persistence and task scheduling belong to the caller. */
public final class IntegrityUnitEvaluator {
    private final IntegrityReadRepository repository;
    private final Clock clock;
    public IntegrityUnitEvaluator(IntegrityReadRepository repository, Clock clock) {
        this.repository = Objects.requireNonNull(repository); this.clock = Objects.requireNonNull(clock);
    }
    public record Evaluation(IntegrityUnitResult result, List<BoundIssue> issues) {
        public Evaluation { Objects.requireNonNull(result); issues = List.copyOf(issues); }
    }
    public record BoundIssue(String ruleId, String ruleVersion, IntegrityIssue issue) {
        public BoundIssue { Objects.requireNonNull(ruleId); Objects.requireNonNull(ruleVersion); Objects.requireNonNull(issue); }
    }

    public Evaluation evaluate(IntegrityScope scope, IntegrityReadPlan plan, DatasetDefinition definition,
            List<IntegrityRule> sourceRules, IntegrityReadBudget budget, int batchSize, int maxIssues) {
        validate(scope, plan, definition, sourceRules, budget, batchSize, maxIssues);
        State state = new State(scope, plan.target(), definition, budget, maxIssues);
        try {
            repository.withSnapshot(scope, plan, budget, batchSize, session -> {
                state.scope = session.scope();
                var input = new ArrayList<IntegrityReadRepository.TargetBatch>();
                session.scanTarget(batch -> {
                    state.check();
                    var rows = new ArrayList<Map<String,Object>>();
                    for (var row : batch.rows()) {
                        state.check(); rows.add(Collections.unmodifiableMap(new LinkedHashMap<>(row))); state.check();
                    }
                    input.add(new IntegrityReadRepository.TargetBatch(batch.dateScopeUnresolved(), rows));
                });
                state.check();
                List<IntegrityReadRepository.TargetBatch> batches = List.copyOf(input);
                state.index = new IntegrityTargetIndex(definition, batches, budget);
                state.collector = new IntegrityIssueCollector(state.scope, plan.target(), definition, budget, maxIssues);
                var rules = new ArrayList<IntegrityRule>(sourceRules);
                String axis = plan.target().dateField();
                rules.add(new RequiredFieldsRule(definition, batches, state::check, axis));
                rules.add(new BusinessKeyRule(definition, batches, state::check, axis));
                rules.add(new SourceIdentityRule(definition, batches, state::check, axis));
                rules.sort(Comparator.comparing(rule -> rule.descriptor().ruleId()));
                for (var rule : rules) {
                    state.check();
                    var descriptor = rule.descriptor();
                    try (var sink = state.collector.forRule(descriptor);
                         var context = new DefaultIntegrityContext(session, descriptor, plan.target(), definition, state.index, budget, sink)) {
                        if (descriptor.dimension() == IntegrityRuleDescriptor.Dimension.COVERAGE)
                            unresolvedDates(state, batches, context, sink);
                        IntegrityRuleResult result;
                        try {
                            result = Objects.requireNonNull(rule.evaluate(state.scope, context, sink));
                            context.check(); state.check();
                            if (!descriptor.equals(result.descriptor())) throw new IllegalArgumentException("Invalid rule result descriptor");
                            if (result.statistics().actualCount() != null && result.statistics().actualCount() != state.index.size())
                                throw new IllegalArgumentException("Invalid actual count");
                            if (descriptor.dimension() == IntegrityRuleDescriptor.Dimension.COVERAGE) {
                                IntegrityStatistics compared = context.statistics();
                                if (compared != null) {
                                    // A source cannot replace framework-owned exact counts with its own statistics.
                                    state.coverage = compared;
                                    var evidence = new LinkedHashSet<>(result.evidence()); evidence.addAll(context.evidence());
                                    IntegrityStatus status = IntegrityStatus.aggregate(List.of(result.status(), context.status()));
                                    result = new IntegrityRuleResult(descriptor, status,
                                            result.status() == IntegrityStatus.PASS ? context.reason() : result.reasonCode(),
                                            result.message(), compared, List.copyOf(evidence));
                                } else {
                                    var stats = result.statistics();
                                    if (stats.expectedCount() != null || stats.matchedCount() != null || stats.extraCount() != null)
                                        throw new IllegalArgumentException("Formal coverage requires comparison");
                                    result = new IntegrityRuleResult(descriptor,
                                            IntegrityStatus.aggregate(List.of(result.status(), IntegrityStatus.UNKNOWN)),
                                            result.status() == IntegrityStatus.PASS ? "EXPECTED_SET_UNPROVEN" : result.reasonCode(),
                                            result.message(), stats, result.evidence());
                                }
                            }
                        } catch (IntegrityReadException exception) { throw exception; }
                        catch (RuntimeException exception) {
                            state.check();
                            // Use a fresh framework sink because an invalid plugin call poisons its own sink.
                            try (var failureSink = state.collector.forRule(descriptor)) {
                                String message = "INVALID_EXPECTED_KEY".equals(exception.getMessage())
                                        ? "INVALID_EXPECTED_KEY: expected keys could not be validated" : "Integrity rule execution failed";
                                failureSink.add(new IntegrityIssue(IntegrityIssue.Type.RULE_EXECUTION_FAILED, IntegrityStatus.UNKNOWN,
                                        state.scope.symbol(), state.scope.datasetKey().apiName(), axis, null, Map.of(), null,
                                        Map.of(), "RULE_EXECUTION_FAILED", message, List.of(), false));
                                result = new IntegrityRuleResult(descriptor, IntegrityStatus.UNKNOWN, "RULE_EXECUTION_FAILED", message,
                                        emptyStatistics(), List.of());
                            }
                        }
                        state.results.add(result);
                    }
                    state.check();
                }
                state.reads = session.readRequests();
                state.check();
                // Aggregate while still inside the snapshot so CPU work observes the shared budget.
                state.completed = state.result(null);
                state.check(); return null;
            });
            state.check();
            return new Evaluation(state.completed, state.collector.snapshot(false));
        } catch (IntegrityReadException exception) {
            IntegrityReadException failure = state.collector != null && state.collector.failure() != null
                    ? state.collector.failure() : exception;
            return new Evaluation(state.result(failure), state.collector == null ? List.of() : state.collector.snapshot(true));
        }
    }

    private static void unresolvedDates(State state, List<IntegrityReadRepository.TargetBatch> batches,
            DefaultIntegrityContext context, IntegrityIssueSink sink) {
        for (var batch : batches) if (batch.dateScopeUnresolved()) for (var row : batch.rows()) {
            state.check();
            Map<String,Object> key = new LinkedHashMap<>();
            for (String field : state.definition.businessKey().fields()) if (row.containsKey(field)) key.put(field, row.get(field));
            IntegrityIssue base = context.issue(IntegrityIssue.Type.DATE_SCOPE_UNRESOLVED, IntegrityStatus.UNKNOWN, key, row,
                    "DATE_SCOPE_UNRESOLVED", List.of(new IntegrityEvidence("local snapshot", "1", state.scope.range(),
                    state.scope.snapshotStartedAt(), "Target row has no date; its membership in the requested range is unknown")));
            sink.add(new IntegrityIssue(base.type(), base.status(), base.symbol(), base.apiName(), base.dateField(), null,
                    base.businessKey(), state.target.dateField(), base.relatedDates(), base.reasonCode(), base.message(), base.evidence(), false));
        }
    }

    private final class State {
        IntegrityScope scope;
        final IntegrityDescriptor target;
        final DatasetDefinition definition;
        final IntegrityReadBudget budget;
        IntegrityIssueCollector collector;
        IntegrityTargetIndex index;
        IntegrityStatistics coverage;
        List<IntegrityReadRequest> reads = List.of();
        final List<IntegrityRuleResult> results = new ArrayList<>();
        IntegrityUnitResult completed;
        State(IntegrityScope scope, IntegrityDescriptor target, DatasetDefinition definition, IntegrityReadBudget budget, int maxIssues) {
            this.scope = scope; this.target = target; this.definition = definition; this.budget = budget;
        }
        void check() { if (collector != null) collector.check(); else budget.check(); }
        IntegrityUnitResult result(IntegrityReadException error) {
            boolean failed = error != null;
            if (!failed) check();
            List<BoundIssue> issues = collector == null ? List.of() : collector.snapshot(failed);
            var dimensions = new EnumMap<IntegrityRuleDescriptor.Dimension,List<IntegrityStatus>>(IntegrityRuleDescriptor.Dimension.class);
            for (var dimension : IntegrityRuleDescriptor.Dimension.values()) dimensions.put(dimension, new ArrayList<>());
            var descriptions = new ArrayList<>(target.rules()); descriptions.addAll(IntegrityContracts.coreRules(definition));
            var byId = new HashMap<String,IntegrityRuleDescriptor>(); descriptions.forEach(d -> byId.put(d.ruleId(), d));
            for (var result : results) { if (!failed) check(); dimensions.get(result.descriptor().dimension()).add(result.status()); }
            long missing = 0, suspected = 0, extra = 0, fields = 0;
            for (var bound : issues) {
                if (!failed) check();
                IntegrityIssue issue = bound.issue();
                var dimension = byId.get(bound.ruleId()).dimension();
                dimensions.get(dimension).add(issue.status());
                if (dimension == IntegrityRuleDescriptor.Dimension.COVERAGE) {
                    if (issue.type() == IntegrityIssue.Type.MISSING) missing++;
                    if (issue.type() == IntegrityIssue.Type.SUSPECTED_MISSING) suspected++;
                    if (issue.type() == IntegrityIssue.Type.EXTRA) extra++;
                }
                if (issue.type() == IntegrityIssue.Type.REQUIRED_FIELD_MISSING) fields++;
            }
            if (failed) dimensions.values().forEach(s -> s.add(IntegrityStatus.UNKNOWN));
            if (index != null && !index.complete()) dimensions.get(IntegrityRuleDescriptor.Dimension.COVERAGE).add(IntegrityStatus.UNKNOWN);
            Long expected = !failed && coverage != null ? coverage.expectedCount() : null;
            Long matched = !failed && coverage != null ? coverage.matchedCount() : null;
            IntegrityStatistics statistics = new IntegrityStatistics(index == null ? null : index.size(), expected, matched,
                    expected != null ? coverage.missingCount() : missing > 0 ? missing : null,
                    failed && suspected == 0 ? null : suspected,
                    !failed && coverage != null && coverage.extraCount() != null ? coverage.extraCount() : extra > 0 ? extra : null,
                    failed && fields == 0 ? null : fields);
            var evidence = new LinkedHashSet<IntegrityEvidence>();
            for (var result : results) { if (!failed) check(); evidence.addAll(result.evidence()); }
            if (scope.snapshotStartedAt() != null) {
                evidence.add(new IntegrityEvidence("local snapshot", "1", scope.range(), scope.snapshotStartedAt(),
                        failed ? "Evaluation incomplete; retained issues and counts are discovered lower bounds"
                                : expected == null && missing > 0 ? "Only local missing keys confirmed; remaining range unknown"
                                : "Same-snapshot local reads: " + reads.size()));
            }
            String reason = failed ? error.reasonCode() : coverage != null && expected != null && expected == 0 && index.size() == 0
                    ? "VERIFIED_EMPTY" : "EVALUATED";
            return new IntegrityUnitResult(scope, target, new IntegrityCheckJson().definitionHash(definition), null,
                    failed ? IntegrityUnitStatus.ERROR : IntegrityUnitStatus.COMPLETED,
                    aggregate(dimensions.get(IntegrityRuleDescriptor.Dimension.COVERAGE)),
                    aggregate(dimensions.get(IntegrityRuleDescriptor.Dimension.KEY)), aggregate(dimensions.get(IntegrityRuleDescriptor.Dimension.FIELD)),
                    statistics, failed ? incompleteRuleResults(issues) : results, List.copyOf(evidence), clock.instant(), failed, !failed, reason,
                    failed ? "Integrity evaluation interrupted; retained details are incomplete" : "Integrity evaluation completed");
        }
        private List<IntegrityRuleResult> incompleteRuleResults(List<BoundIssue> issues) {
            return results.stream().map(result -> {
                long missing = 0, suspected = 0, extra = 0, fields = 0;
                for (var bound : issues) if (bound.ruleId().equals(result.descriptor().ruleId())
                        && bound.ruleVersion().equals(result.descriptor().version())) {
                    switch (bound.issue().type()) {
                        case MISSING -> missing++;
                        case SUSPECTED_MISSING -> suspected++;
                        case EXTRA -> extra++;
                        case REQUIRED_FIELD_MISSING -> fields++;
                        default -> { }
                    }
                }
                boolean coverageRule = result.descriptor().dimension() == IntegrityRuleDescriptor.Dimension.COVERAGE;
                var statistics = new IntegrityStatistics(index == null ? null : result.statistics().actualCount(), null, null,
                        coverageRule && missing > 0 ? missing : null, coverageRule && suspected > 0 ? suspected : null,
                        coverageRule && extra > 0 ? extra : null, fields > 0 ? fields : null);
                return new IntegrityRuleResult(result.descriptor(), result.status(), result.reasonCode(), result.message(),
                        statistics, result.evidence());
            }).toList();
        }
    }
    private static IntegrityStatus aggregate(List<IntegrityStatus> values) {
        return values.isEmpty() ? IntegrityStatus.NOT_APPLICABLE : IntegrityStatus.aggregate(values);
    }
    private static IntegrityStatistics emptyStatistics() { return new IntegrityStatistics(null, null, null, null, null, null, null); }
    private static void validate(IntegrityScope scope, IntegrityReadPlan plan, DatasetDefinition definition,
            List<IntegrityRule> sourceRules, IntegrityReadBudget budget, int batchSize, int maxIssues) {
        if (scope == null || plan == null || definition == null || sourceRules == null || budget == null
                || batchSize <= 0 || maxIssues <= 0 || scope.snapshotStartedAt() != null || scope.symbol() == null
                || plan.target().scopeKind() == IntegrityDescriptor.ScopeKind.NON_STOCK
                || !scope.datasetKey().equals(plan.target().datasetKey()) || !definition.datasetKey().equals(plan.target().datasetKey()))
            throw new IllegalArgumentException("Invalid integrity evaluation request");
        Set<IntegrityRuleDescriptor> expected = new HashSet<>(plan.target().rules());
        Set<String> ids = new HashSet<>();
        IntegrityContracts.coreRules(definition).forEach(rule -> ids.add(rule.ruleId()));
        for (var rule : sourceRules) if (rule == null || !expected.remove(rule.descriptor()) || !ids.add(rule.descriptor().ruleId()))
            throw new IllegalArgumentException("Invalid source rule implementations");
        if (!expected.isEmpty()) throw new IllegalArgumentException("Missing source rule implementation");
    }
}
