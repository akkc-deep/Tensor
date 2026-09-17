package com.akkc.tensor.core.integrity;

import com.akkc.tensor.core.integrity.IntegrityCheckRepository.*;
import com.akkc.tensor.core.registry.PluginRegistry;
import com.akkc.tensor.plugin.api.error.*;
import com.akkc.tensor.plugin.api.integrity.*;
import java.time.*;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Executes one fixed plan, committing each report only after its read snapshot has closed. */
public final class IntegrityCheckRunner {
    private static final System.Logger LOG = System.getLogger(IntegrityCheckRunner.class.getName());
    private final IntegrityCheckRepository repository;
    private final IntegrityCheckService service;
    private final PluginRegistry plugins;
    private final IntegrityCheckJson json;
    private final IntegrityUnitEvaluator evaluator;
    private final Clock clock;
    private final IntegrityCheckService.Settings settings;
    private final IntegrityReadPlanner planner = new IntegrityReadPlanner();

    public IntegrityCheckRunner(IntegrityCheckRepository repository, IntegrityCheckService service,
            PluginRegistry plugins, IntegrityCheckJson json, IntegrityUnitEvaluator evaluator,
            Clock clock, IntegrityCheckService.Settings settings) {
        this.repository = Objects.requireNonNull(repository); this.service = Objects.requireNonNull(service);
        this.plugins = Objects.requireNonNull(plugins); this.json = Objects.requireNonNull(json);
        this.evaluator = Objects.requireNonNull(evaluator); this.clock = Objects.requireNonNull(clock);
        this.settings = Objects.requireNonNull(settings);
    }

    public void run(UUID checkId, BooleanSupplier stopping) {
        Objects.requireNonNull(checkId); Objects.requireNonNull(stopping);
        try {
            Instant started = clock.instant();
            if (!repository.start(checkId, started)) return;
            var task = repository.find(checkId).orElseThrow();
            Instant deadline = plusSeconds(started, settings.taskTimeoutSeconds());
            for (int page = 1; ; page++) {
                if (interrupt(checkId, deadline, stopping)) return;
                var units = repository.results(checkId, null, page, 100);
                for (var unit : units.items()) {
                    if (interrupt(checkId, deadline, stopping)) return;
                    var evaluation = evaluate(task, unit, deadline, stopping);
                    repository.saveResult(checkId, unit.resultId(), evaluation.result(), evaluation.issues().stream()
                            .map(i -> new NewIssue(i.ruleId(), i.ruleVersion(), i.issue())).toList());
                    if (interrupt(checkId, deadline, stopping)) return;
                }
                if ((long) page * 100 >= units.total()) break;
            }
            repository.complete(checkId, clock.instant());
        } catch (RuntimeException failure) {
            String reason = failure instanceof TensorException classified
                    && Set.of(ErrorCode.QUERY_FAILED, ErrorCode.PERSISTENCE_FAILED).contains(classified.code())
                    ? classified.code().name() : "INTERNAL_ERROR";
            try { repository.terminate(checkId, IntegrityTaskStatus.FAILED, reason, clock.instant()); }
            catch (RuntimeException unavailable) {
                LOG.log(System.Logger.Level.ERROR, "Integrity task terminal state could not be saved");
            }
        }
    }

    private IntegrityUnitEvaluator.Evaluation evaluate(TaskRecord task, ResultRecord unit, Instant taskDeadline,
            BooleanSupplier stopping) {
        var report = unit.report();
        // Decode outside source error handling: corrupt stored JSON is a report query failure.
        var scope = json.readScope(report.get("scope"));
        var descriptor = json.readDescriptor(report.get("descriptor"));
        IntegrityCheckService.Capability capability;
        List<IntegrityRule> rules;
        IntegrityCheckJson.ApiSnapshot target;
        com.akkc.tensor.plugin.api.IntegrityCheckSupport support;
        try {
            capability = service.capability(task.pluginId());
            var availability = plugins.findIntegrity(task.pluginId());
            if (!capability.localCheckAvailable() || !availability.available())
                return unscanned(unit, scope, descriptor, "INTEGRITY_UNAVAILABLE", true);
            support = availability.support();
            if (!task.capabilityHash().equals(capability.capabilityHash()))
                return unscanned(unit, scope, descriptor, "DEFINITION_CHANGED", true);
            target = capability.apis().stream().filter(a -> a.definition().datasetKey().equals(scope.datasetKey()))
                    .findFirst().orElseThrow();
            if (!json.readValue(json.write(target)).equals(unit.definitionSnapshot()))
                return unscanned(unit, scope, descriptor, "DEFINITION_CHANGED", true);
            rules = List.copyOf(support.integrityRules(unit.apiName()));
            var expected = new HashSet<>(descriptor == null ? List.<IntegrityRuleDescriptor>of() : descriptor.rules());
            for (var rule : rules) if (!expected.remove(rule.descriptor())) throw new IllegalArgumentException();
            if (!expected.isEmpty()) throw new IllegalArgumentException();
        } catch (RuntimeException invalid) {
            return unscanned(unit, scope, descriptor, "DEFINITION_CHANGED", true);
        }
        if (descriptor == null) return unscanned(unit, scope, null, "RULE_NOT_IMPLEMENTED", false);
        if (descriptor.scopeKind() == IntegrityDescriptor.ScopeKind.NON_STOCK)
            return unscanned(unit, scope, descriptor, "NON_STOCK_SCOPE", false);
        Instant unitDeadline = plusSeconds(clock.instant(), settings.unitTimeoutSeconds());
        Instant deadline = unitDeadline.isBefore(taskDeadline) ? unitDeadline : taskDeadline;
        IntegrityReadPlan plan;
        try { plan = planner.plan(scope, target, capability.apis(), support.integrityReferenceReads(scope)); }
        catch (RuntimeException invalid) { return unscanned(unit, scope, descriptor, "INVALID_READ_REQUEST", true); }
        if (!clock.instant().isBefore(deadline))
            return unscanned(unit, scope, descriptor, "UNIT_TIME_BUDGET_EXHAUSTED", true);
        IntegrityReadBudget budget;
        try { budget = new IntegrityReadBudget(settings.maxScannedRowsPerUnit(), deadline, clock, stopping); }
        catch (IllegalArgumentException expired) {
            if (!clock.instant().isBefore(deadline))
                return unscanned(unit, scope, descriptor, "UNIT_TIME_BUDGET_EXHAUSTED", true);
            throw expired;
        }
        return evaluator.evaluate(scope, plan, target.definition(), rules, budget,
                settings.scanBatchSize(), settings.maxIssuesPerUnit());
    }

    private IntegrityUnitEvaluator.Evaluation unscanned(ResultRecord unit, IntegrityScope scope,
            IntegrityDescriptor descriptor, String reason, boolean error) {
        var status = "NON_STOCK_SCOPE".equals(reason) ? IntegrityStatus.NOT_APPLICABLE : IntegrityStatus.UNKNOWN;
        return new IntegrityUnitEvaluator.Evaluation(new IntegrityUnitResult(scope, descriptor, unit.definitionHash(), null,
                error ? IntegrityUnitStatus.ERROR : IntegrityUnitStatus.COMPLETED, status, status, status,
                new IntegrityStatistics(null, null, null, null, null, null, null), List.of(), List.of(), clock.instant(),
                error, !error, reason, "Integrity unit: " + reason), List.of());
    }

    private boolean interrupt(UUID checkId, Instant deadline, BooleanSupplier stopping) {
        String reason = !clock.instant().isBefore(deadline) ? "TASK_TIME_BUDGET_EXHAUSTED"
                : stopping.getAsBoolean() || Thread.currentThread().isInterrupted() ? "EXECUTION_INTERRUPTED" : null;
        if (reason == null) return false;
        repository.terminate(checkId, IntegrityTaskStatus.INTERRUPTED, reason, clock.instant());
        return true;
    }
    static Instant plusSeconds(Instant instant, long seconds) {
        try { return instant.plusSeconds(seconds); }
        catch (DateTimeException | ArithmeticException overflow) { return Instant.MAX; }
    }
}
