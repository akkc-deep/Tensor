package com.akkc.tensor.core.integrity;

import com.akkc.tensor.plugin.api.dataset.DatasetDefinition;
import com.akkc.tensor.plugin.api.dataset.LogicalType;
import com.akkc.tensor.plugin.api.integrity.*;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Consumer;

/** A single rule invocation's same-snapshot access and exact set comparison. */
final class DefaultIntegrityContext implements IntegrityContext, AutoCloseable {
    private final IntegrityReadRepository.ReadSession session;
    private final IntegrityRuleDescriptor rule;
    private final IntegrityDescriptor target;
    private final DatasetDefinition definition;
    private final IntegrityBusinessKeys keys;
    private final IntegrityTargetIndex index;
    private final IntegrityReadBudget budget;
    private final IntegrityIssueCollector.Sink sink;
    private final Thread owner = Thread.currentThread();
    private boolean closed, compared;
    private RuntimeException failure;
    private IntegrityStatistics statistics;
    private IntegrityStatus status = IntegrityStatus.UNKNOWN;
    private String reason = "EXPECTED_SET_UNPROVEN";
    private List<IntegrityEvidence> evidence = List.of();

    DefaultIntegrityContext(IntegrityReadRepository.ReadSession session, IntegrityRuleDescriptor rule,
            IntegrityDescriptor target, DatasetDefinition definition, IntegrityTargetIndex index,
            IntegrityReadBudget budget, IntegrityIssueCollector.Sink sink) {
        this.session = session; this.rule = rule; this.target = target; this.definition = definition;
        this.keys = new IntegrityBusinessKeys(definition); this.index = index; this.budget = budget; this.sink = sink;
    }
    void check() {
        if (closed || owner != Thread.currentThread()) throw new IllegalStateException("Integrity rule invocation is closed");
        sink.requireOpen(); budget.check();
        if (failure != null) throw failure;
    }
    @Override public void scan(IntegrityReadRequest request, Consumer<List<Map<String,Object>>> rows) {
        check();
        try {
            session.scan(request, batch -> { check(); rows.accept(batch); check(); });
            check();
        } catch (RuntimeException exception) { failure = exception; throw exception; }
    }
    @Override public IntegrityStatistics compare(IntegrityExpectedKeys expected) {
        check();
        try {
            if (compared || rule.dimension() != IntegrityRuleDescriptor.Dimension.COVERAGE)
                throw new IllegalArgumentException("Invalid comparison invocation");
            compared = true;
            if (!session.scope().equals(expected.scope()) || expected.evidence().stream()
                    .anyMatch(e -> !session.scope().snapshotStartedAt().equals(e.readAt())))
                throw new IllegalArgumentException("INVALID_EXPECTED_KEY");
            var wanted = new LinkedHashSet<IntegrityBusinessKeys.Key>();
            budget.check();
            var iterator = expected.keys().iterator();
            budget.check();
            while (true) {
                budget.check(); boolean more = iterator.hasNext(); budget.check();
                if (!more) break;
                budget.consume(1); budget.check(); var row = iterator.next(); budget.check();
                var key = keys.normalize(row, true);
                if (key.fields().containsKey(target.symbolField())
                        && !Objects.equals(key.fields().get(target.symbolField()), session.scope().symbol()))
                    throw new IllegalArgumentException("INVALID_EXPECTED_KEY");
                if (target.dateField() != null && key.fields().containsKey(target.dateField())) {
                    Object date = key.fields().get(target.dateField());
                    if (!(date instanceof LocalDate day) || day.isBefore(session.scope().startDate())
                            || day.isAfter(session.scope().endDate())) throw new IllegalArgumentException("INVALID_EXPECTED_KEY");
                }
                wanted.add(key); budget.check();
            }
            // No differences are published until the entire expected stream has been validated.
            budget.check();
            boolean proven = expected.basis() == IntegrityExpectedKeys.Basis.PROVEN;
            boolean exact = proven && index.complete();
            long matched = 0, missing = 0, suspected = 0, extra = 0;
            for (var key : wanted) {
                check();
                if (index.actual.containsKey(key)) { matched++; continue; }
                boolean confirmed = proven && !index.invalidKeys && !index.unresolved.contains(key);
                if (confirmed) missing++; else suspected++;
                sink.add(issue(confirmed ? IntegrityIssue.Type.MISSING : IntegrityIssue.Type.SUSPECTED_MISSING,
                        confirmed ? IntegrityStatus.FAIL : IntegrityStatus.WARN, key.fields(), null,
                        confirmed ? "MISSING" : "SUSPECTED_MISSING", expected.evidence()));
            }
            if (proven) for (var entry : index.actual.entrySet()) {
                check();
                if (!wanted.contains(entry.getKey())) {
                    extra++;
                    sink.add(issue(IntegrityIssue.Type.EXTRA, IntegrityStatus.WARN, entry.getKey().fields(),
                            entry.getValue(), "EXTRA", expected.evidence()));
                }
            }
            statistics = new IntegrityStatistics(index.size(), exact ? (long) wanted.size() : null,
                    exact ? matched : null, exact || missing > 0 ? missing : null, suspected,
                    proven ? extra : null, null);
            status = !exact ? IntegrityStatus.UNKNOWN : missing > 0 ? IntegrityStatus.FAIL
                    : extra > 0 ? IntegrityStatus.WARN : IntegrityStatus.PASS;
            reason = !proven ? "EXPECTED_SET_UNPROVEN" : !index.complete() ? "ACTUAL_SET_UNRESOLVED"
                    : wanted.isEmpty() && index.actual.isEmpty() ? "VERIFIED_EMPTY" : "COMPARED";
            evidence = expected.evidence();
            check(); return statistics;
        } catch (IntegrityReadException exception) { failure = exception; throw exception; }
        catch (RuntimeException exception) {
            failure = new IllegalArgumentException("INVALID_EXPECTED_KEY");
            throw failure;
        }
    }
    IntegrityStatistics statistics() { return statistics; }
    IntegrityStatus status() { return status; }
    String reason() { return reason; }
    List<IntegrityEvidence> evidence() { return evidence; }
    @Override public void close() { closed = true; }

    IntegrityIssue issue(IntegrityIssue.Type type, IntegrityStatus status, Map<String,Object> key,
            Map<String,Object> actual, String reason, List<IntegrityEvidence> evidence) {
        Map<String,Object> values = actual == null ? key : actual;
        LocalDate date = target.dateField() != null && values.get(target.dateField()) instanceof LocalDate day ? day : null;
        Map<String,LocalDate> related = new LinkedHashMap<>();
        for (var column : definition.columns()) {
            budget.check();
            if (column.logicalType() == LogicalType.DATE && !column.name().equals(target.dateField())
                    && values.get(column.name()) instanceof LocalDate day) related.put(column.name(), day);
        }
        return new IntegrityIssue(type, status, session.scope().symbol(), session.scope().datasetKey().apiName(),
                target.dateField(), date, key, null, related, reason, reason, evidence, false);
    }
}
