package com.akkc.tensor.core.integrity;

import com.akkc.tensor.core.persistence.SqlConstants;
import com.akkc.tensor.plugin.api.error.*;
import com.akkc.tensor.plugin.api.integrity.*;
import com.akkc.tensor.plugin.api.model.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.sql.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Immutable accepted plans and append-once reports. No current plugin metadata is consulted. */
public final class IntegrityCheckRepository {
    private static final String TASK = "tensor_integrity_check_task";
    private static final String RESULT = "tensor_integrity_check_result";
    private static final String ISSUE = "tensor_integrity_check_issue";
    private final JdbcTemplate jdbc;
    private final IntegrityCheckJson json;
    private final TransactionTemplate write, read;

    public IntegrityCheckRepository(JdbcTemplate jdbc, PlatformTransactionManager transactions, IntegrityCheckJson json) {
        this.jdbc = Objects.requireNonNull(jdbc);
        this.json = Objects.requireNonNull(json);
        write = new TransactionTemplate(transactions);
        read = new TransactionTemplate(transactions);
        write.setTimeout(SqlConstants.TRANSACTION_TIMEOUT_SECONDS);
        read.setTimeout(SqlConstants.TRANSACTION_TIMEOUT_SECONDS);
        read.setReadOnly(true);
        read.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    }

    public record TaskScope(PluginId pluginId, List<String> symbols, LocalDate startDate, LocalDate endDate,
            List<ApiName> apiNames, Instant acceptedAt) {
        public TaskScope { symbols = immutableList(symbols); apiNames = immutableList(apiNames); }
    }
    public record NewTask(UUID checkId, UUID submissionId, PluginId pluginId, Map<String, Object> originalRequest,
            TaskScope scope, String requestHash, String capabilityHash,
            List<IntegrityCheckJson.ApiSnapshot> definitionSnapshot, Instant createdAt) {
        public NewTask {
            originalRequest = immutableMap(originalRequest);
            definitionSnapshot = immutableList(definitionSnapshot);
        }
    }
    public record NewUnit(UUID resultId, IntegrityScope scope, IntegrityCheckJson.ApiSnapshot snapshot) {}
    public record NewIssue(String ruleId, String ruleVersion, IntegrityIssue issue) {}
    public record TaskFilter(PluginId pluginId, IntegrityTaskStatus status, UUID submissionId) {}
    public record ResultFilter(String symbol, ApiName apiName, IntegrityStatus overallStatus) {}
    public record IssueFilter(UUID resultId, String symbol, ApiName apiName, IntegrityIssue.Type type,
            IntegrityStatus status, LocalDate dateFrom, LocalDate dateTo) {}
    public record Page<T>(int page, int pageSize, long total, List<T> items) {
        public Page { items = immutableList(items); }
    }
    public record TaskRecord(UUID checkId, UUID submissionId, PluginId pluginId, String requestHash,
            String capabilityHash, JsonNode originalRequest, JsonNode normalizedScope, JsonNode definitionSnapshot,
            IntegrityTaskStatus status, int plannedUnits, Instant createdAt, Instant updatedAt,
            Instant startedAt, Instant finishedAt, String errorCode, String errorMessage) {
        public TaskRecord {
            originalRequest = copy(originalRequest); normalizedScope = copy(normalizedScope);
            definitionSnapshot = copy(definitionSnapshot);
        }
        @Override public JsonNode originalRequest() { return copy(originalRequest); }
        @Override public JsonNode normalizedScope() { return copy(normalizedScope); }
        @Override public JsonNode definitionSnapshot() { return copy(definitionSnapshot); }
    }
    public record ResultRecord(UUID resultId, UUID checkId, String unitKey, PluginId pluginId, ApiName apiName,
            String symbol, String definitionHash, JsonNode definitionSnapshot, JsonNode report) {
        public ResultRecord { definitionSnapshot = copy(definitionSnapshot); report = copy(report); }
        @Override public JsonNode definitionSnapshot() { return copy(definitionSnapshot); }
        @Override public JsonNode report() { return copy(report); }
    }
    public record IssueRecord(long issueId, UUID resultId, String ruleId, String ruleVersion, JsonNode issue) {
        public IssueRecord { issue = copy(issue); }
        @Override public JsonNode issue() { return copy(issue); }
    }
    public record Progress(TaskRecord task, long completedUnits, long errorUnits, long notRunUnits,
            Map<IntegrityStatus, Long> statusCounts, IntegrityStatus overallStatus) {
        public Progress {
            Objects.requireNonNull(task); Objects.requireNonNull(overallStatus);
            statusCounts = Map.copyOf(statusCounts);
        }
    }

    public void create(NewTask task, List<NewUnit> units) {
        ownTransaction();
        var encoded = input(() -> encodePlan(task, units));
        tx(false, () -> {
            update("INSERT INTO " + TASK + " (check_id,submission_id,plugin_id,request_hash,capability_hash,"
                    + "original_request,normalized_scope,definition_snapshot,status,planned_units,created_at,updated_at)"
                    + " VALUES (?,?,?,?,?,?,?,?,'QUEUED',?,?,?)", task.checkId(), task.submissionId(), task.pluginId().value(),
                    task.requestHash(), task.capabilityHash(), encoded.request(), encoded.scope(), encoded.snapshot(),
                    encoded.units().size(), time(task.createdAt()), time(task.createdAt()));
            for (var unit : encoded.units()) {
                var scope = unit.input().scope();
                var report = unit.pending();
                update("INSERT INTO " + RESULT + " (result_id,check_id,unit_key,plugin_id,api_name,symbol,start_date,end_date,"
                        + "date_field,definition_hash,definition_snapshot,report,unit_status,coverage_status,key_status,"
                        + "field_status,overall_status,incomplete,issues_complete)"
                        + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,'PENDING','UNKNOWN','UNKNOWN','UNKNOWN','UNKNOWN',1,0)",
                        unit.input().resultId(), task.checkId(), unit.key(), task.pluginId().value(),
                        scope.datasetKey().apiName().value(), scope.symbol(), scope.startDate(), scope.endDate(),
                        report.descriptor() == null ? null : report.descriptor().dateField(), report.definitionHash(),
                        unit.snapshot(), unit.report());
            }
            return null;
        });
    }

    public Optional<TaskRecord> find(UUID checkId) {
        ownTransaction(); require(checkId != null);
        return tx(true, () -> task("check_id", checkId, false));
    }
    public Optional<TaskRecord> findBySubmissionId(UUID submissionId) {
        ownTransaction(); require(submissionId != null);
        return tx(true, () -> task("submission_id", submissionId, false));
    }
    public Optional<ResultRecord> result(UUID checkId, UUID resultId) {
        ownTransaction(); require(checkId != null && resultId != null);
        return tx(true, () -> resultRow(checkId, resultId, false));
    }

    public boolean start(UUID checkId, Instant startedAt) {
        ownTransaction();
        var at = input(() -> { require(checkId != null && startedAt != null); return time(startedAt); });
        return tx(false, () -> {
            var saved = task("check_id", checkId, true);
            if (saved.isEmpty() || saved.get().status() != IntegrityTaskStatus.QUEUED) return false;
            require(!startedAt.isBefore(saved.get().createdAt()));
            require(jdbc.update("UPDATE " + TASK + " SET status='RUNNING',started_at=?,updated_at=?"
                    + " WHERE check_id=? AND status='QUEUED'", at, at, checkId.toString()) == 1);
            return true;
        });
    }

    public void complete(UUID checkId, Instant finishedAt) {
        ownTransaction();
        var at = input(() -> { require(checkId != null && finishedAt != null); return time(finishedAt); });
        tx(false, () -> {
            var saved = task("check_id", checkId, true).orElseThrow(IntegrityCheckRepository::invalid);
            require(saved.status() == IntegrityTaskStatus.RUNNING && !finishedAt.isBefore(
                    saved.startedAt() == null ? saved.createdAt() : saved.startedAt()));
            long finished = jdbc.queryForObject("SELECT COUNT(*) FROM " + RESULT
                    + " WHERE check_id=? AND unit_status IN ('COMPLETED','ERROR')", Long.class, checkId.toString());
            require(finished == saved.plannedUnits());
            update("UPDATE " + TASK + " SET status='COMPLETED',updated_at=?,finished_at=?,error_code=NULL,error_message=NULL"
                    + " WHERE check_id=?", at, at, checkId);
            return null;
        });
    }

    public void terminate(UUID checkId, IntegrityTaskStatus status, String reasonCode, Instant finishedAt) {
        ownTransaction();
        var input = input(() -> {
            require(checkId != null && status != null && reasonCode != null && finishedAt != null);
            require(status == IntegrityTaskStatus.INTERRUPTED
                    ? Set.of("TASK_TIME_BUDGET_EXHAUSTED", "EXECUTION_INTERRUPTED").contains(reasonCode)
                    : status == IntegrityTaskStatus.FAILED
                            && Set.of("QUERY_FAILED", "PERSISTENCE_FAILED", "INTERNAL_ERROR").contains(reasonCode));
            return new Termination(status, reasonCode, safeMessage(reasonCode), time(finishedAt));
        });
        tx(false, () -> {
            var task = task("check_id", checkId, true).orElseThrow(IntegrityCheckRepository::invalid);
            if (Set.of(IntegrityTaskStatus.COMPLETED, IntegrityTaskStatus.FAILED, IntegrityTaskStatus.INTERRUPTED)
                    .contains(task.status())) return null;
            require(!finishedAt.isBefore(task.startedAt() == null ? task.createdAt() : task.startedAt()));
            var results = jdbc.query("SELECT * FROM " + RESULT + " WHERE check_id=? ORDER BY result_id FOR UPDATE",
                    this::mapResult, checkId.toString());
            require(results.size() == task.plannedUnits());
            for (var saved : results) {
                var unitStatus = stored(() -> IntegrityUnitStatus.valueOf(saved.report().path("unitStatus").asText()));
                if (unitStatus == IntegrityUnitStatus.PENDING || unitStatus == IntegrityUnitStatus.RUNNING) {
                    var result = notRun(saved, reasonCode, input.message(), finishedAt);
                    storeResult(saved, result, encodeResult(checkId, saved.resultId(), result, List.of()));
                }
            }
            update("UPDATE " + TASK + " SET status=?,updated_at=?,finished_at=?,error_code=?,error_message=? WHERE check_id=?",
                    status.name(), input.time(), input.time(), reasonCode, input.message(), checkId);
            return null;
        });
    }

    public int interruptUnfinished(Instant interruptedAt) {
        ownTransaction(); input(() -> { require(interruptedAt != null); time(interruptedAt); return null; });
        int interrupted = 0;
        while (true) {
            var ids = tx(true, () -> jdbc.query("SELECT check_id FROM " + TASK
                    + " WHERE status IN ('QUEUED','RUNNING') ORDER BY created_at,check_id LIMIT 100",
                    (r, row) -> UUID.fromString(r.getString(1))));
            if (ids.isEmpty()) return interrupted;
            for (var id : ids) {
                terminate(id, IntegrityTaskStatus.INTERRUPTED, "EXECUTION_INTERRUPTED", interruptedAt);
                interrupted++;
            }
        }
    }

    public Optional<Progress> progress(UUID checkId) {
        ownTransaction(); require(checkId != null);
        return tx(true, () -> {
            var task = task("check_id", checkId, false);
            if (task.isEmpty()) return Optional.empty();
            var counts = new EnumMap<IntegrityStatus, Long>(IntegrityStatus.class);
            for (var status : IntegrityStatus.values()) counts.put(status, 0L);
            long completed = 0, errors = 0, notRun = 0, total = 0;
            var rows = jdbc.queryForList("SELECT unit_status,overall_status,COUNT(*) AS total FROM " + RESULT
                    + " WHERE check_id=? GROUP BY unit_status,overall_status", checkId.toString());
            for (var row : rows) {
                var units = IntegrityUnitStatus.valueOf((String) row.get("unit_status"));
                var count = ((Number) row.get("total")).longValue(); total += count;
                if (units == IntegrityUnitStatus.COMPLETED || units == IntegrityUnitStatus.ERROR) completed += count;
                if (units == IntegrityUnitStatus.ERROR) errors += count;
                if (units == IntegrityUnitStatus.NOT_RUN) notRun += count;
                var status = units == IntegrityUnitStatus.COMPLETED || units == IntegrityUnitStatus.ERROR
                        ? IntegrityStatus.valueOf((String) row.get("overall_status")) : IntegrityStatus.UNKNOWN;
                counts.put(status, counts.get(status) + count);
            }
            require(total == task.get().plannedUnits());
            var overall = IntegrityStatus.UNKNOWN;
            if (total > 0) for (int index = IntegrityStatus.values().length - 1; index >= 0; index--) {
                var candidate = IntegrityStatus.values()[index];
                if (counts.get(candidate) > 0) { overall = candidate; break; }
            }
            return Optional.of(new Progress(task.get(), completed, errors, notRun, counts, overall));
        });
    }

    public void saveResult(UUID checkId, UUID resultId, IntegrityUnitResult result, List<NewIssue> issues) {
        ownTransaction();
        var encoded = input(() -> encodeResult(checkId, resultId, result, issues));
        tx(false, () -> {
            var task = task("check_id", checkId, true).orElseThrow(IntegrityCheckRepository::invalid);
            require(task.status() == IntegrityTaskStatus.QUEUED || task.status() == IntegrityTaskStatus.RUNNING);
            var saved = resultRow(checkId, resultId, true).orElseThrow(IntegrityCheckRepository::invalid);
            storeResult(saved, result, encoded);
            return null;
        });
    }

    private EncodedResult encodeResult(UUID checkId, UUID resultId, IntegrityUnitResult result, List<NewIssue> issues) {
            require(checkId != null && resultId != null && result != null && issues != null);
            require(Set.of(IntegrityUnitStatus.COMPLETED, IntegrityUnitStatus.ERROR, IntegrityUnitStatus.NOT_RUN)
                    .contains(result.unitStatus()));
            validateScope(result.scope());
            if (result.finishedAt() != null) time(result.finishedAt());
            if (result.incomplete() || result.unitStatus() != IntegrityUnitStatus.COMPLETED) {
                require(result.statistics().expectedCount() == null && result.statistics().matchedCount() == null);
                for (var rule : result.ruleResults())
                    require(rule.statistics().expectedCount() == null && rule.statistics().matchedCount() == null);
            }
            var bound = immutableList(issues);
            for (var item : bound) {
                require(item != null && item.ruleId() != null && !item.ruleId().isBlank()
                        && item.ruleVersion() != null && !item.ruleVersion().isBlank() && item.issue() != null);
                var issue = item.issue();
                length(issue.dateField(), 64); length(issue.field(), 64); date(issue.date());
                require(Objects.equals(issue.symbol(), result.scope().symbol())
                        && issue.apiName().equals(result.scope().datasetKey().apiName())
                        && Objects.equals(issue.dateField(), result.descriptor() == null ? null : result.descriptor().dateField()));
                require(result.unitStatus() != IntegrityUnitStatus.ERROR || issue.incomplete());
            }
            if (result.unitStatus() == IntegrityUnitStatus.ERROR) validateLowerBounds(result, bound);
            return new EncodedResult(json.writeDocument(result), bound.stream().map(item -> new EncodedIssue(item,
                    json.write(item.issue().businessKey()), json.write(item.issue().relatedDates()),
                    json.write(item.issue().evidence()))).toList());
    }

    private void storeResult(ResultRecord saved, IntegrityUnitResult result, EncodedResult encoded) {
            require(Set.of("PENDING", "RUNNING").contains(saved.report().path("unitStatus").asText()));
            validateReport(saved, result, encoded);
            var resultId = saved.resultId();
            var issueRows = new ArrayList<Object[]>();
            for (var item : encoded.issues()) {
                var issue = item.input().issue();
                issueRows.add(new Object[] {resultId.toString(), item.input().ruleId(), item.input().ruleVersion(),
                        issue.type().name(), issue.status().name(), issue.dateField(), issue.date(), item.key(), issue.field(),
                        item.dates(), issue.reasonCode(), issue.message(), item.evidence(), issue.incomplete()});
            }
            if (!issueRows.isEmpty()) jdbc.batchUpdate("INSERT INTO " + ISSUE
                    + " (result_id,rule_id,rule_version,type,status,date_field,issue_date,business_key,field,related_dates,"
                    + "reason_code,message,evidence,incomplete) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)", issueRows);
            var count = result.statistics();
            update("UPDATE " + RESULT + " SET report=?,unit_status=?,coverage_status=?,key_status=?,field_status=?,"
                    + "overall_status=?,actual_count=?,expected_count=?,matched_count=?,missing_count=?,suspected_missing_count=?,"
                    + "extra_count=?,required_field_issue_count=?,snapshot_started_at=?,finished_at=?,incomplete=?,issues_complete=?"
                    + " WHERE check_id=? AND result_id=?", encoded.report(), result.unitStatus().name(), result.coverageStatus().name(),
                    result.keyStatus().name(), result.fieldStatus().name(), result.overallStatus().name(), count.actualCount(),
                    count.expectedCount(), count.matchedCount(), count.missingCount(), count.suspectedMissingCount(),
                    count.extraCount(), count.requiredFieldIssueCount(), time(result.scope().snapshotStartedAt()),
                    time(result.finishedAt()), result.incomplete(), result.issuesComplete(), saved.checkId(), resultId);
    }

    public Page<TaskRecord> tasks(TaskFilter filter, int page, int pageSize) {
        ownTransaction(); pagination(page, pageSize);
        var where = new Where(" WHERE 1=1");
        if (filter != null) {
            where.equal("plugin_id", filter.pluginId() == null ? null : filter.pluginId().value());
            where.equal("status", filter.status() == null ? null : filter.status().name());
            where.equal("submission_id", filter.submissionId());
        }
        return tx(true, () -> page(TASK, "*", where, "created_at DESC,check_id DESC", this::mapTask, page, pageSize));
    }
    public Page<ResultRecord> results(UUID checkId, ResultFilter filter, int page, int pageSize) {
        ownTransaction(); require(checkId != null); pagination(page, pageSize);
        var where = new Where(" WHERE 1=1"); where.equal("check_id", checkId);
        if (filter != null) {
            length(filter.symbol(), 255);
            where.equal("symbol", filter.symbol());
            where.equal("api_name", filter.apiName() == null ? null : filter.apiName().value());
            where.equal("overall_status", filter.overallStatus() == null ? null : filter.overallStatus().name());
        }
        return tx(true, () -> page(RESULT, "*", where, "api_name ASC,symbol ASC,result_id ASC", this::mapResult, page, pageSize));
    }
    public Page<IssueRecord> issues(UUID checkId, IssueFilter filter, int page, int pageSize) {
        ownTransaction(); require(checkId != null); pagination(page, pageSize);
        var where = new Where(" WHERE 1=1"); where.equal("r.check_id", checkId);
        if (filter != null) {
            length(filter.symbol(), 255); date(filter.dateFrom()); date(filter.dateTo());
            require(filter.dateFrom() == null || filter.dateTo() == null || !filter.dateFrom().isAfter(filter.dateTo()));
            where.equal("i.result_id", filter.resultId()); where.equal("r.symbol", filter.symbol());
            where.equal("r.api_name", filter.apiName() == null ? null : filter.apiName().value());
            where.equal("i.type", filter.type() == null ? null : filter.type().name());
            where.equal("i.status", filter.status() == null ? null : filter.status().name());
            where.add("i.issue_date>=?", filter.dateFrom()); where.add("i.issue_date<=?", filter.dateTo());
        }
        return tx(true, () -> page(ISSUE + " i JOIN " + RESULT + " r ON r.result_id=i.result_id",
                "i.*,r.symbol,r.api_name", where, "i.issue_date IS NULL ASC,i.issue_date ASC,i.issue_id ASC",
                this::mapIssue, page, pageSize));
    }

    private EncodedPlan encodePlan(NewTask task, List<NewUnit> units) {
        require(task != null && task.checkId() != null && task.submissionId() != null && task.pluginId() != null
                && task.scope() != null && task.createdAt() != null && units != null);
        var scope = task.scope();
        require(task.pluginId().equals(scope.pluginId()) && scope.acceptedAt() != null);
        date(scope.startDate()); date(scope.endDate());
        require(scope.startDate() != null && scope.endDate() != null && !scope.startDate().isAfter(scope.endDate()));
        time(task.createdAt());
        require(new HashSet<>(scope.symbols()).size() == scope.symbols().size()
                && new HashSet<>(scope.apiNames()).size() == scope.apiNames().size());
        scope.symbols().forEach(symbol -> { require(symbol != null && !symbol.isBlank()); length(symbol, 255); });
        require(task.requestHash() != null && task.requestHash().equals(json.requestHash(task.originalRequest())));
        require(task.capabilityHash() != null && task.capabilityHash().matches("[a-f0-9]{64}"));
        json.capabilitySnapshot(task.pluginId(), task.definitionSnapshot());
        var snapshots = new HashMap<ApiName, IntegrityCheckJson.ApiSnapshot>();
        task.definitionSnapshot().forEach(snapshot -> snapshots.put(snapshot.definition().datasetKey().apiName(), snapshot));
        require(snapshots.keySet().containsAll(scope.apiNames()));
        var keys = new HashSet<String>(); var ids = new HashSet<UUID>(); var plan = new ArrayList<EncodedUnit>();
        for (var unit : units) {
            require(unit != null && unit.resultId() != null && ids.add(unit.resultId()) && unit.scope() != null && unit.snapshot() != null);
            var s = unit.scope(); validateScope(s);
            require(s.snapshotStartedAt() == null && s.datasetKey().pluginId().equals(task.pluginId())
                    && s.startDate().equals(scope.startDate()) && s.endDate().equals(scope.endDate())
                    && s.acceptedAt().equals(scope.acceptedAt()) && scope.apiNames().contains(s.datasetKey().apiName())
                    && unit.snapshot().equals(snapshots.get(s.datasetKey().apiName())));
            var descriptor = unit.snapshot().descriptor();
            require(descriptor != null && descriptor.scopeKind() == IntegrityDescriptor.ScopeKind.NON_STOCK
                    ? s.symbol() == null : s.symbol() != null && scope.symbols().contains(s.symbol()));
            if (descriptor != null) length(descriptor.dateField(), 64);
            var key = json.unitKey(s.datasetKey().apiName(), s.symbol()); require(keys.add(key));
            var pending = new IntegrityUnitResult(s, descriptor, json.definitionHash(unit.snapshot().definition()), null,
                    IntegrityUnitStatus.PENDING, IntegrityStatus.UNKNOWN, IntegrityStatus.UNKNOWN, IntegrityStatus.UNKNOWN,
                    new IntegrityStatistics(null,null,null,null,null,null,null), List.of(), List.of(), null, true, false,
                    "PENDING", "等待检查");
            plan.add(new EncodedUnit(unit, key, pending, json.writeDocument(unit.snapshot()), json.writeDocument(pending)));
        }
        var expected = new HashSet<String>();
        for (var api : scope.apiNames()) {
            var descriptor = snapshots.get(api).descriptor();
            if (descriptor != null && descriptor.scopeKind() == IntegrityDescriptor.ScopeKind.NON_STOCK)
                expected.add(json.unitKey(api, null));
            else for (var symbol : scope.symbols()) expected.add(json.unitKey(api, symbol));
        }
        require(keys.equals(expected));
        return new EncodedPlan(json.writeDocument(task.originalRequest()), json.writeDocument(scope),
                json.writeDocument(task.definitionSnapshot()), List.copyOf(plan));
    }

    private IntegrityUnitResult notRun(ResultRecord saved, String reasonCode, String message, Instant finishedAt) {
        return stored(() -> {
            var report = saved.report();
            return new IntegrityUnitResult(json.readScope(report.get("scope")), json.readDescriptor(report.get("descriptor")),
                    saved.definitionHash(), null, IntegrityUnitStatus.NOT_RUN, IntegrityStatus.UNKNOWN,
                    IntegrityStatus.UNKNOWN, IntegrityStatus.UNKNOWN,
                    new IntegrityStatistics(null,null,null,null,null,null,null), List.of(), List.of(), finishedAt,
                    true, false, reasonCode, message);
        });
    }

    private static String safeMessage(String reasonCode) {
        return switch (reasonCode) {
            case "TASK_TIME_BUDGET_EXHAUSTED" -> "Integrity check time budget was exhausted";
            case "EXECUTION_INTERRUPTED" -> "Integrity check execution was interrupted";
            case "QUERY_FAILED" -> ErrorCode.QUERY_FAILED.message();
            case "PERSISTENCE_FAILED" -> ErrorCode.PERSISTENCE_FAILED.message();
            case "INTERNAL_ERROR" -> ErrorCode.INTERNAL_ERROR.message();
            default -> throw invalid();
        };
    }

    private void validateReport(ResultRecord saved, IntegrityUnitResult report, EncodedResult encoded) {
        var supplied = json.readDocument(encoded.report());
        var oldScope = (ObjectNode) saved.report().path("scope");
        var newScope = (ObjectNode) supplied.path("scope").deepCopy();
        var oldSnapshot = oldScope.get("snapshotStartedAt");
        require(oldSnapshot.isNull() || oldSnapshot.equals(newScope.get("snapshotStartedAt")));
        oldScope.remove("snapshotStartedAt"); newScope.remove("snapshotStartedAt");
        require(oldScope.equals(newScope) && saved.definitionHash().equals(report.definitionHash())
                && saved.definitionSnapshot().path("descriptor").equals(supplied.path("descriptor")));
        var rules = new HashMap<String, JsonNode>();
        saved.definitionSnapshot().path("descriptor").path("rules").forEach(rule -> rules.put(rule.path("ruleId").asText(), rule));
        saved.definitionSnapshot().path("coreRules").forEach(rule -> rules.put(rule.path("ruleId").asText(), rule));
        var returned = new HashSet<String>();
        supplied.path("ruleResults").forEach(result -> {
            var descriptor = result.path("descriptor"); var id = descriptor.path("ruleId").asText();
            require(returned.add(id) && descriptor.equals(rules.get(id)));
        });
        for (var item : encoded.issues()) {
            var rule = rules.get(item.input().ruleId());
            require(rule != null && rule.path("version").asText().equals(item.input().ruleVersion()));
            var status = dimensionStatus(report, IntegrityRuleDescriptor.Dimension.valueOf(rule.path("dimension").asText()));
            require(IntegrityStatus.aggregate(List.of(status, item.input().issue().status())) == status);
        }
        if (report.unitStatus() == IntegrityUnitStatus.ERROR) {
            for (var dimension : List.of(IntegrityRuleDescriptor.Dimension.COVERAGE, IntegrityRuleDescriptor.Dimension.KEY,
                    IntegrityRuleDescriptor.Dimension.FIELD)) {
                var status = dimensionStatus(report, dimension);
                if (status == IntegrityStatus.FAIL) require(encoded.issues().stream().anyMatch(item ->
                        item.input().issue().status() == IntegrityStatus.FAIL
                                && rules.get(item.input().ruleId()).path("dimension").asText().equals(dimension.name())));
            }
        }
    }
    private static void validateLowerBounds(IntegrityUnitResult result, List<NewIssue> issues) {
        validateLowerBounds(result.statistics(), issues);
        for (var rule : result.ruleResults()) {
            var bound = issues.stream().filter(issue -> issue.ruleId().equals(rule.descriptor().ruleId())
                    && issue.ruleVersion().equals(rule.descriptor().version())).toList();
            validateLowerBounds(rule.statistics(), bound);
            if (rule.status() == IntegrityStatus.FAIL)
                require(bound.stream().anyMatch(issue -> issue.issue().status() == IntegrityStatus.FAIL));
        }
    }
    private static IntegrityStatus dimensionStatus(IntegrityUnitResult result, IntegrityRuleDescriptor.Dimension dimension) {
        return switch (dimension) {
            case COVERAGE -> result.coverageStatus(); case KEY -> result.keyStatus(); case FIELD -> result.fieldStatus();
        };
    }
    private static void validateLowerBounds(IntegrityStatistics counts, List<NewIssue> issues) {
        lowerBound(counts.missingCount(), IntegrityIssue.Type.MISSING, issues);
        lowerBound(counts.suspectedMissingCount(), IntegrityIssue.Type.SUSPECTED_MISSING, issues);
        lowerBound(counts.extraCount(), IntegrityIssue.Type.EXTRA, issues);
        lowerBound(counts.requiredFieldIssueCount(), IntegrityIssue.Type.REQUIRED_FIELD_MISSING, issues);
    }
    private static void lowerBound(Long count, IntegrityIssue.Type type, List<NewIssue> issues) {
        require(count == null || count <= issues.stream().filter(issue -> issue.issue().type() == type).count());
    }

    private Optional<TaskRecord> task(String column, UUID id, boolean lock) {
        return jdbc.query("SELECT * FROM " + TASK + " WHERE " + column + "=?" + (lock ? " FOR UPDATE" : ""),
                this::mapTask, id.toString()).stream().findFirst();
    }
    private Optional<ResultRecord> resultRow(UUID checkId, UUID resultId, boolean lock) {
        return jdbc.query("SELECT * FROM " + RESULT + " WHERE check_id=? AND result_id=?" + (lock ? " FOR UPDATE" : ""),
                this::mapResult, checkId.toString(), resultId.toString()).stream().findFirst();
    }
    private <T> Page<T> page(String from, String columns, Where where, String order, RowMapper<T> mapper, int page, int size) {
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM " + from + where.sql, Long.class, bind(where.args));
        var args = new ArrayList<>(where.args); args.add(size); args.add((long) (page - 1) * size);
        var items = jdbc.query("SELECT " + columns + " FROM " + from + where.sql + " ORDER BY " + order + " LIMIT ? OFFSET ?",
                mapper, bind(args));
        return new Page<>(page, size, total, items);
    }
    private TaskRecord mapTask(ResultSet r, int row) throws SQLException {
        return mapping(() -> new TaskRecord(uuid(r,"check_id"), uuid(r,"submission_id"), new PluginId(r.getString("plugin_id")),
                r.getString("request_hash"), r.getString("capability_hash"), json.readDocument(r.getString("original_request")),
                json.readDocument(r.getString("normalized_scope")), json.readDocument(r.getString("definition_snapshot")),
                IntegrityTaskStatus.valueOf(r.getString("status")), r.getInt("planned_units"), instant(r,"created_at"),
                instant(r,"updated_at"), instant(r,"started_at"), instant(r,"finished_at"), r.getString("error_code"), r.getString("error_message")));
    }
    private ResultRecord mapResult(ResultSet r, int row) throws SQLException {
        return mapping(() -> {
            var definitionHash = r.getString("definition_hash");
            var report = json.readDocument(r.getString("report"));
            require(definitionHash.equals(report.path("definitionHash").textValue()));
            return new ResultRecord(uuid(r,"result_id"), uuid(r,"check_id"), r.getString("unit_key"),
                    new PluginId(r.getString("plugin_id")), new ApiName(r.getString("api_name")), r.getString("symbol"),
                    definitionHash, json.readDocument(r.getString("definition_snapshot")), report);
        });
    }
    private IssueRecord mapIssue(ResultSet r, int row) throws SQLException {
        return mapping(() -> {
            var node = JsonNodeFactory.instance.objectNode();
            for (String name : List.of("type", "status", "symbol", "field", "message")) node.put(name, r.getString(name));
            node.put("apiName", r.getString("api_name")); node.put("dateField", r.getString("date_field"));
            var date = r.getObject("issue_date", LocalDate.class); node.put("date", date == null ? null : date.toString());
            node.put("reasonCode", r.getString("reason_code")); node.put("incomplete", r.getBoolean("incomplete"));
            node.set("businessKey", json.readValue(r.getString("business_key")));
            node.set("relatedDates", json.readValue(r.getString("related_dates")));
            node.set("evidence", json.readValue(r.getString("evidence")));
            return new IssueRecord(r.getLong("issue_id"), uuid(r,"result_id"), r.getString("rule_id"), r.getString("rule_version"), node);
        });
    }

    private record EncodedPlan(String request, String scope, String snapshot, List<EncodedUnit> units) {}
    private record EncodedUnit(NewUnit input, String key, IntegrityUnitResult pending, String snapshot, String report) {}
    private record EncodedResult(String report, List<EncodedIssue> issues) {}
    private record EncodedIssue(NewIssue input, String key, String dates, String evidence) {}
    private record Termination(IntegrityTaskStatus status, String reasonCode, String message, LocalDateTime time) {}
    private static final class Where {
        final StringBuilder sql; final List<Object> args = new ArrayList<>();
        Where(String base) { sql = new StringBuilder(base); }
        void equal(String column, Object value) { add(column + "=?", value); }
        void add(String clause, Object value) { if (value != null) { sql.append(" AND ").append(clause); args.add(value); } }
    }
    private <T> T tx(boolean reading, Supplier<T> action) {
        try { return (reading ? read : write).execute(status -> action.get()); }
        catch (DuplicateKeyException | TensorException failure) { throw failure; }
        catch (IllegalArgumentException failure) { if (reading) throw failure(ErrorCode.QUERY_FAILED); throw invalid(); }
        catch (RuntimeException failure) { throw failure(reading ? ErrorCode.QUERY_FAILED : ErrorCode.PERSISTENCE_FAILED); }
    }
    @FunctionalInterface private interface SqlSupplier<T> { T get() throws SQLException; }
    private static <T> T mapping(SqlSupplier<T> mapper) throws SQLException {
        try { return mapper.get(); } catch (RuntimeException failure) { throw failure(ErrorCode.QUERY_FAILED); }
    }
    private static <T> T input(Supplier<T> supplier) {
        try { return supplier.get(); } catch (RuntimeException failure) { throw invalid(); }
    }
    private static <T> T stored(Supplier<T> supplier) {
        try { return supplier.get(); } catch (TensorException failure) { throw failure; }
        catch (RuntimeException failure) { throw failure(ErrorCode.QUERY_FAILED); }
    }
    private static void ownTransaction() {
        if (TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("Repository requires its own transaction");
    }
    private void update(String sql, Object... args) { jdbc.update(sql, bind(Arrays.asList(args))); }
    private static Object[] bind(List<?> values) { return values.stream().map(v -> v instanceof UUID id ? id.toString() : v).toArray(); }
    private static void pagination(int page, int size) { require(page >= 1 && size >= 1 && size <= 100); }
    private static void validateScope(IntegrityScope scope) {
        require(scope != null); length(scope.symbol(), 255);
        length(scope.datasetKey().pluginId().value(), 64); length(scope.datasetKey().apiName().value(), 64);
        date(scope.startDate()); date(scope.endDate()); time(scope.snapshotStartedAt());
    }
    private static void length(String text, int limit) { require(text == null || text.codePointCount(0, text.length()) <= limit); }
    private static void date(LocalDate date) { require(date == null || date.getYear() >= 1000 && date.getYear() <= 9999); }
    private static LocalDateTime time(Instant value) {
        if (value == null) return null;
        var result = LocalDateTime.ofInstant(value.truncatedTo(ChronoUnit.MILLIS), ZoneOffset.UTC); date(result.toLocalDate()); return result;
    }
    private static Instant instant(ResultSet r, String column) throws SQLException {
        var value = r.getObject(column, LocalDateTime.class); return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }
    private static UUID uuid(ResultSet r, String column) throws SQLException { return UUID.fromString(r.getString(column)); }
    private static JsonNode copy(JsonNode value) { require(value != null); return value.deepCopy(); }
    private static <T> List<T> immutableList(List<T> value) { return input(() -> List.copyOf(value)); }
    @SuppressWarnings("unchecked") private static Map<String,Object> immutableMap(Map<String,Object> value) {
        require(value != null); return (Map<String,Object>) immutable(value, 0);
    }
    private static Object immutable(Object value, int depth) {
        require(depth <= 32);
        if (value instanceof Map<?,?> map) {
            var result = new LinkedHashMap<Object,Object>(); map.forEach((k,v) -> result.put(k, immutable(v, depth + 1)));
            return Collections.unmodifiableMap(result);
        }
        if (value instanceof List<?> list) return Collections.unmodifiableList(list.stream().map(item -> immutable(item, depth + 1)).toList());
        return value;
    }
    private static void require(boolean valid) { if (!valid) throw invalid(); }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Invalid integrity check input"); }
    private static TensorException failure(ErrorCode code) { return new RepositoryException(code); }
    private static final class RepositoryException extends TensorException {
        RepositoryException(ErrorCode code) { super(code, code.message()); }
    }
}
