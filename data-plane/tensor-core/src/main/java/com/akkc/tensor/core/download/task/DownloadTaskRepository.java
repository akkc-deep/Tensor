package com.akkc.tensor.core.download.task;

import com.akkc.tensor.plugin.api.download.batch.DateRange;
import com.akkc.tensor.plugin.api.download.batch.DownloadMode;
import com.akkc.tensor.plugin.api.error.ErrorCode;
import com.akkc.tensor.plugin.api.error.TensorException;
import com.akkc.tensor.plugin.api.model.ApiName;
import com.akkc.tensor.plugin.api.model.DatasetKey;
import com.akkc.tensor.plugin.api.model.PluginId;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/** Fixed task operations; task rows are always locked before batch rows. */
public final class DownloadTaskRepository {
    private final JdbcTemplate jdbc;
    private final DownloadTaskJson json;
    private final TransactionTemplate write;
    private final TransactionTemplate read;

    public DownloadTaskRepository(
            JdbcTemplate jdbc, PlatformTransactionManager transactions, DownloadTaskJson json) {
        this.jdbc = Objects.requireNonNull(jdbc);
        this.json = Objects.requireNonNull(json);
        write = new TransactionTemplate(transactions);
        write.setTimeout(60);
        read = new TransactionTemplate(transactions);
        read.setTimeout(60);
        read.setReadOnly(true);
        read.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    }

    public record NewTask(
            UUID taskId,
            UUID submissionId,
            DatasetKey datasetKey,
            DownloadMode mode,
            Map<String, Object> normalizedParams,
            String definitionHash,
            String policySnapshot,
            UUID activeRunId,
            Instant now) {
        public NewTask {
            normalizedParams = immutableParams(normalizedParams);
        }
    }

    public record NewBatch(
            UUID batchId, String batchKey, DateRange range, Map<String, Object> sourceParams) {
        public NewBatch {
            sourceParams = immutableParams(sourceParams);
        }
    }

    public record ExecutionPermit(UUID taskId, UUID activeRunId, int runGeneration) {
        public ExecutionPermit {
            require(taskId != null && activeRunId != null && runGeneration >= 1);
        }
    }

    public record StoredError(ErrorCode code) {
        public StoredError {
            require(code != null);
        }

        public String message() {
            return messageFor(code);
        }
    }

    public record Counts(
            long totalBatches,
            long pending,
            long running,
            long succeeded,
            long failed,
            long splitBatches,
            long sourceRows,
            long insertedRows,
            long updatedRows) {}

    public record Page<T>(long total, List<T> items) {
        public Page {
            items = List.copyOf(items);
        }
    }

    public record TaskFilter(
            String pluginId, String apiName, DownloadTask.Status status, UUID submissionId) {}

    public record BatchFilter(DownloadBatch.Status status, boolean includeSplit) {}

    public record TaskSnapshot(Optional<DownloadTask> task, Counts counts) {}

    public enum RequeueMode {
        RETRY,
        RESUME
    }

    public Optional<DownloadTask> findTask(UUID id) {
        return tx(true, () -> task("task_id", id, false));
    }

    public Optional<DownloadTask> findSubmission(UUID id) {
        return tx(true, () -> task("submission_id", id, false));
    }

    public long queuedCount() {
        return tx(
                true,
                () -> number("SELECT COUNT(*) FROM tensor_download_task WHERE status='QUEUED'"));
    }

    public List<DownloadTask> queuedTasks(UUID run, int limit) {
        return tx(
                true,
                () -> {
                    require(run != null && limit > 0);
                    return tasksQuery(
                            "SELECT * FROM tensor_download_task WHERE status='QUEUED' AND"
                                    + " active_run_id=? ORDER BY queued_at,task_id LIMIT ?",
                            run,
                            limit);
                });
    }

    public List<DownloadTask> unfinishedTasks() {
        return tx(
                true,
                () ->
                        tasksQuery(
                                "SELECT * FROM tensor_download_task WHERE status IN"
                                        + " ('QUEUED','RUNNING') ORDER BY queued_at,task_id"));
    }

    public Page<DownloadTask> tasks(TaskFilter filter, int page, int pageSize) {
        return tx(
                true,
                () -> {
                    page(page, pageSize);
                    var args = new ArrayList<Object>();
                    var where = new StringBuilder(" WHERE 1=1");
                    if (filter != null) {
                        if (filter.pluginId() != null) {
                            new PluginId(filter.pluginId());
                            clause(where, args, "plugin_id", filter.pluginId());
                        }
                        if (filter.apiName() != null) {
                            new ApiName(filter.apiName());
                            clause(where, args, "api_name", filter.apiName());
                        }
                        if (filter.status() != null)
                            clause(where, args, "status", filter.status().name());
                        if (filter.submissionId() != null)
                            clause(where, args, "submission_id", filter.submissionId());
                    }
                    long total =
                            number(
                                    "SELECT COUNT(*) FROM tensor_download_task" + where,
                                    args.toArray());
                    args.add(pageSize);
                    args.add((long) (page - 1) * pageSize);
                    return new Page<>(
                            total,
                            tasksQuery(
                                    "SELECT * FROM tensor_download_task"
                                            + where
                                            + " ORDER BY created_at DESC,task_id DESC LIMIT ?"
                                            + " OFFSET ?",
                                    args.toArray()));
                });
    }

    public Page<DownloadBatch> batches(UUID id, BatchFilter filter, int page, int pageSize) {
        return tx(
                true,
                () -> {
                    page(page, pageSize);
                    var args = new ArrayList<Object>();
                    args.add(id);
                    var where = new StringBuilder(" WHERE task_id=?");
                    if (filter == null || !filter.includeSplit())
                        where.append(" AND status<>'SPLIT'");
                    if (filter != null && filter.status() != null)
                        clause(where, args, "status", filter.status().name());
                    long total =
                            number(
                                    "SELECT COUNT(*) FROM tensor_download_batch" + where,
                                    args.toArray());
                    args.add(pageSize);
                    args.add((long) (page - 1) * pageSize);
                    return new Page<>(
                            total,
                            batchQuery(
                                    "SELECT * FROM tensor_download_batch"
                                            + where
                                            + " ORDER BY batch_key LIMIT ? OFFSET ?",
                                    args.toArray()));
                });
    }

    public List<DownloadBatch> pendingBatches(UUID id) {
        return tx(
                true,
                () ->
                        batchQuery(
                                "SELECT * FROM tensor_download_batch WHERE task_id=? AND"
                                        + " status='PENDING' ORDER BY batch_key",
                                id));
    }

    public Counts counts(UUID id) {
        return tx(true, () -> countRows(id));
    }

    public TaskSnapshot snapshot(UUID id) {
        return tx(true, () -> new TaskSnapshot(task("task_id", id, false), countRows(id)));
    }

    public DownloadTask insert(NewTask in) {
        return tx(
                false,
                () -> {
                    require(
                            in != null
                                    && in.taskId() != null
                                    && in.submissionId() != null
                                    && in.datasetKey() != null
                                    && in.mode() != null
                                    && in.activeRunId() != null);
                    hash(in.definitionHash());
                    String params = json.writeTaskParams(in.normalizedParams());
                    String policy = json.validatePolicySnapshot(in.policySnapshot());
                    require(policyMode(policy).equals(in.mode().name()));
                    update(
                            "INSERT INTO tensor_download_task"
                                + " (task_id,submission_id,request_hash,plugin_id,api_name,mode,params,definition_hash,policy_snapshot,status,plan_ready,active_run_id,run_generation,version,request_count,run_request_count,created_at,updated_at,queued_at)"
                                + " VALUES (?,?,?,?,?,?,?,?,?,'QUEUED',false,?,0,1,0,0,?,?,?)",
                            in.taskId(),
                            in.submissionId(),
                            json.requestHash(in.datasetKey(), in.mode(), in.normalizedParams()),
                            in.datasetKey().pluginId().value(),
                            in.datasetKey().apiName().value(),
                            in.mode().name(),
                            params,
                            in.definitionHash(),
                            policy,
                            in.activeRunId(),
                            time(in.now()),
                            time(in.now()),
                            time(in.now()));
                    return requiredTask(in.taskId());
                });
    }

    public Optional<DownloadTask> claimTask(UUID id, UUID run, Instant now, Instant deadline) {
        return tx(
                false,
                () -> {
                    require(run != null && time(deadline).isAfter(time(now)));
                    var found = task("task_id", id, true);
                    if (found.isEmpty()) return Optional.empty();
                    var t = found.get();
                    if (t.status() != DownloadTask.Status.QUEUED || !t.activeRunId().equals(run))
                        return Optional.empty();
                    limit(t.runGeneration() < Integer.MAX_VALUE && t.version() < Long.MAX_VALUE);
                    update(
                            "UPDATE tensor_download_task SET"
                                + " status='RUNNING',run_generation=run_generation+1,version=version+1,started_at=?,deadline_at=?,finished_at=NULL,run_request_count=0,updated_at=?"
                                + " WHERE task_id=?",
                            time(now),
                            time(deadline),
                            time(now),
                            id);
                    return Optional.of(requiredTask(id));
                });
    }

    public Optional<DownloadBatch> claimBatch(ExecutionPermit p, UUID id, Instant now) {
        return tx(
                false,
                () -> {
                    var t = lockedTask(p);
                    deadline(t, now);
                    var found = batch(id, p.taskId(), true);
                    if (found.isEmpty() || found.get().status() != DownloadBatch.Status.PENDING)
                        return Optional.empty();
                    limit(found.get().attemptCount() < Integer.MAX_VALUE);
                    update(
                            "UPDATE tensor_download_batch SET"
                                + " status='RUNNING',attempt_count=attempt_count+1,run_generation=?,started_at=?,finished_at=NULL,error_code=NULL,error_message=NULL,updated_at=?"
                                + " WHERE batch_id=?",
                            p.runGeneration(),
                            time(now),
                            time(now),
                            id);
                    return batch(id, p.taskId(), false);
                });
    }

    public void reserveRequest(ExecutionPermit p, long maxRequests, Instant now) {
        tx(
                false,
                () -> {
                    var t = lockedTask(p);
                    deadline(t, now);
                    limit(
                            maxRequests > 0
                                    && t.runRequestCount() < maxRequests
                                    && t.requestCount() < Long.MAX_VALUE
                                    && t.runRequestCount() < Long.MAX_VALUE);
                    update(
                            "UPDATE tensor_download_task SET"
                                + " request_count=request_count+1,run_request_count=run_request_count+1,updated_at=?"
                                + " WHERE task_id=?",
                            time(now),
                            p.taskId());
                    return null;
                });
    }

    public void savePlan(ExecutionPermit p, List<NewBatch> roots, int maxNodes, Instant now) {
        tx(
                false,
                () -> {
                    var t = lockedTask(p);
                    deadline(t, now);
                    state(!t.planReady() && nodes(p.taskId()) == 0);
                    require(roots != null);
                    limit(maxNodes >= 0 && roots.size() <= maxNodes);
                    require(t.mode() != DownloadMode.SINGLE || roots.size() == 1);
                    var ids = new HashSet<UUID>();
                    for (int i = 0; i < roots.size(); i++) {
                        var b = roots.get(i);
                        validateBatch(t, b);
                        require(
                                b.batchKey().equals(String.format(Locale.ROOT, "%06d", i + 1))
                                        && ids.add(b.batchId()));
                    }
                    for (var b : roots) insertBatch(p.taskId(), null, b, now);
                    update(
                            "UPDATE tensor_download_task SET plan_ready=true,updated_at=? WHERE"
                                    + " task_id=?",
                            time(now),
                            p.taskId());
                    return null;
                });
    }

    public void split(
            ExecutionPermit p,
            UUID parentId,
            NewBatch left,
            NewBatch right,
            int maxNodes,
            Instant now) {
        tx(
                false,
                () -> {
                    var t = lockedTask(p);
                    deadline(t, now);
                    var parent = lockedBatch(p, parentId);
                    require(t.mode() == DownloadMode.RANGE);
                    validateBatch(t, left);
                    validateBatch(t, right);
                    var r = parent.range();
                    require(r != null && r.start().isBefore(r.end()));
                    require(
                            left.batchKey().equals(parent.batchKey() + "/0")
                                    && right.batchKey().equals(parent.batchKey() + "/1")
                                    && !left.batchId().equals(right.batchId()));
                    require(
                            left.range().start().equals(r.start())
                                    && right.range().end().equals(r.end())
                                    && left.range().end().isBefore(r.end())
                                    && right.range()
                                            .start()
                                            .equals(left.range().end().plusDays(1)));
                    limit(maxNodes >= 0 && nodes(p.taskId()) <= (long) maxNodes - 2);
                    update(
                            "UPDATE tensor_download_batch SET"
                                + " status='SPLIT',finished_at=?,updated_at=?,source_rows=0,inserted_rows=0,updated_rows=0"
                                + " WHERE batch_id=?",
                            time(now),
                            time(now),
                            parentId);
                    insertBatch(p.taskId(), parentId, left, now);
                    insertBatch(p.taskId(), parentId, right, now);
                    return null;
                });
    }

    public void failBatch(ExecutionPermit p, UUID id, ErrorCode error, Instant now) {
        tx(
                false,
                () -> {
                    lockedTask(p);
                    lockedBatch(p, id);
                    require(error != null);
                    batchResult(id, "FAILED", error, 0, 0, 0, now);
                    return null;
                });
    }

    public void finishTask(
            ExecutionPermit p, DownloadTask.Status target, ErrorCode error, Instant now) {
        tx(
                false,
                () -> {
                    var t = lockedTask(p);
                    var c = countRows(p.taskId());
                    boolean success = complete(t, c);
                    state(
                            c.running() == 0
                                    && target != null
                                    && switch (target) {
                                        case SUCCEEDED -> success;
                                        case PARTIAL_FAILED ->
                                                c.succeeded() > 0
                                                        && c.succeeded() < c.totalBatches();
                                        case FAILED -> c.succeeded() == 0 && !success;
                                        default -> false;
                                    });
                    endTask(t, target, error, now);
                    return null;
                });
    }

    public void requeue(UUID id, long version, UUID run, RequeueMode mode, Instant now) {
        tx(
                false,
                () -> {
                    require(run != null && mode != null);
                    var t = task("task_id", id, true).orElseThrow(DownloadTaskRepository::conflict);
                    state(
                            t.version() == version
                                    && (mode == RequeueMode.RETRY
                                            ? t.status() == DownloadTask.Status.FAILED
                                                    || t.status()
                                                            == DownloadTask.Status.PARTIAL_FAILED
                                            : t.status() == DownloadTask.Status.INTERRUPTED));
                    limit(t.version() < Long.MAX_VALUE);
                    lockedBatches(id);
                    update(
                            "UPDATE tensor_download_batch SET"
                                + " status='PENDING',error_code=NULL,error_message=NULL,started_at=NULL,finished_at=NULL,updated_at=?"
                                + " WHERE task_id=? AND status='FAILED'"
                                    + (mode == RequeueMode.RESUME
                                            ? " AND error_code='EXECUTION_INTERRUPTED'"
                                            : ""),
                            time(now),
                            id);
                    update(
                            "UPDATE tensor_download_task SET"
                                + " status='QUEUED',active_run_id=?,version=version+1,queued_at=?,updated_at=?,last_error_code=NULL,last_error_message=NULL,started_at=NULL,finished_at=NULL,deadline_at=NULL"
                                + " WHERE task_id=?",
                            run,
                            time(now),
                            time(now),
                            id);
                    return null;
                });
    }

    public DownloadTask recoverStoppedTask(
            UUID id, UUID run, int generation, long version, Instant now) {
        return tx(
                false,
                () -> {
                    var t = task("task_id", id, true).orElseThrow(DownloadTaskRepository::conflict);
                    state(
                            t.activeRunId().equals(run)
                                    && t.runGeneration() == generation
                                    && t.version() == version
                                    && (t.status() == DownloadTask.Status.QUEUED
                                            || t.status() == DownloadTask.Status.RUNNING));
                    var batches = lockedBatches(id);
                    boolean success = complete(t, countRows(id));
                    if (!success)
                        for (var b : batches)
                            if (b.status() == DownloadBatch.Status.RUNNING)
                                batchResult(
                                        b.batchId(),
                                        "FAILED",
                                        ErrorCode.EXECUTION_INTERRUPTED,
                                        0,
                                        0,
                                        0,
                                        now);
                    endTask(
                            t,
                            success
                                    ? DownloadTask.Status.SUCCEEDED
                                    : DownloadTask.Status.INTERRUPTED,
                            success ? null : ErrorCode.EXECUTION_INTERRUPTED,
                            now);
                    return requiredTask(id);
                });
    }

    public DownloadTask lockTask(ExecutionPermit p) {
        participating();
        return writeOperation(() -> lockedTask(p));
    }

    public DownloadBatch lockBatch(ExecutionPermit p, UUID id) {
        participating();
        return writeOperation(
                () -> {
                    lockedTask(p);
                    return lockedBatch(p, id);
                });
    }

    public void succeedBatch(
            ExecutionPermit p,
            UUID id,
            long sourceRows,
            long insertedRows,
            long updatedRows,
            Instant now) {
        participating();
        writeOperation(
                () -> {
                    require(sourceRows >= 0 && insertedRows >= 0 && updatedRows >= 0);
                    lockedTask(p);
                    lockedBatch(p, id);
                    // T04 may already have a repeatable-read snapshot: use current locking reads.
                    long remainingSource = Long.MAX_VALUE - sourceRows;
                    long remainingInserted = Long.MAX_VALUE - insertedRows;
                    long remainingUpdated = Long.MAX_VALUE - updatedRows;
                    for (var batch : lockedBatches(p.taskId())) {
                        if (batch.status() != DownloadBatch.Status.SUCCEEDED) continue;
                        limit(
                                batch.sourceRows() <= remainingSource
                                        && batch.insertedRows() <= remainingInserted
                                        && batch.updatedRows() <= remainingUpdated);
                        remainingSource -= batch.sourceRows();
                        remainingInserted -= batch.insertedRows();
                        remainingUpdated -= batch.updatedRows();
                    }
                    batchResult(id, "SUCCEEDED", null, sourceRows, insertedRows, updatedRows, now);
                    return null;
                });
    }

    private DownloadTask lockedTask(ExecutionPermit p) {
        require(p != null);
        var t = task("task_id", p.taskId(), true).orElseThrow(DownloadTaskRepository::conflict);
        state(
                t.status() == DownloadTask.Status.RUNNING
                        && t.activeRunId().equals(p.activeRunId())
                        && t.runGeneration() == p.runGeneration());
        return t;
    }

    private DownloadBatch lockedBatch(ExecutionPermit p, UUID id) {
        var b = batch(id, p.taskId(), true).orElseThrow(DownloadTaskRepository::conflict);
        state(
                b.status() == DownloadBatch.Status.RUNNING
                        && Objects.equals(b.runGeneration(), p.runGeneration()));
        return b;
    }

    private List<DownloadBatch> lockedBatches(UUID id) {
        return batchQuery(
                "SELECT * FROM tensor_download_batch WHERE task_id=? ORDER BY batch_key FOR UPDATE",
                id);
    }

    private void endTask(DownloadTask t, DownloadTask.Status target, ErrorCode error, Instant now) {
        limit(t.version() < Long.MAX_VALUE);
        update(
                "UPDATE tensor_download_task SET"
                    + " status=?,version=version+1,last_error_code=?,last_error_message=?,finished_at=?,updated_at=?"
                    + " WHERE task_id=?",
                target.name(),
                error == null ? null : error.name(),
                error == null ? null : messageFor(error),
                time(now),
                time(now),
                t.taskId());
    }

    private void batchResult(
            UUID id,
            String status,
            ErrorCode error,
            long source,
            long inserted,
            long updated,
            Instant now) {
        update(
                "UPDATE tensor_download_batch SET"
                    + " status=?,source_rows=?,inserted_rows=?,updated_rows=?,error_code=?,error_message=?,finished_at=?,updated_at=?"
                    + " WHERE batch_id=?",
                status,
                source,
                inserted,
                updated,
                error == null ? null : error.name(),
                error == null ? null : messageFor(error),
                time(now),
                time(now),
                id);
    }

    private void validateBatch(DownloadTask t, NewBatch b) {
        require(
                b != null
                        && b.batchId() != null
                        && b.batchKey() != null
                        && b.batchKey().length() <= 128
                        && b.batchKey().matches("[0-9]{6}(?:/[01])*"));
        json.writeBatchParams(b.sourceParams());
        if (t.mode() == DownloadMode.SINGLE)
            require(b.range() == null && b.sourceParams().equals(t.params()));
        else {
            require(b.range() != null);
            try {
                var policy = new ObjectMapper().readTree(t.policySnapshot());
                var start = endpointDate(t.params().get(policy.get("startParameter").textValue()));
                var end = endpointDate(t.params().get(policy.get("endParameter").textValue()));
                require(!b.range().start().isBefore(start) && !b.range().end().isAfter(end));
            } catch (Exception failure) {
                throw invalid();
            }
        }
    }

    private static LocalDate endpointDate(Object value) {
        require(value instanceof String text && text.matches("[0-9]{8}"));
        return LocalDate.parse((String) value, DateTimeFormatter.BASIC_ISO_DATE);
    }

    private void insertBatch(UUID task, UUID parent, NewBatch b, Instant now) {
        update(
                "INSERT INTO tensor_download_batch"
                    + " (batch_id,task_id,parent_batch_id,batch_key,range_start,range_end,source_params,status,attempt_count,source_rows,inserted_rows,updated_rows,created_at,updated_at)"
                    + " VALUES (?,?,?,?,?,?,?,'PENDING',0,0,0,0,?,?)",
                b.batchId(),
                task,
                parent,
                b.batchKey(),
                b.range() == null ? null : b.range().start(),
                b.range() == null ? null : b.range().end(),
                json.writeBatchParams(b.sourceParams()),
                time(now),
                time(now));
    }

    private long nodes(UUID id) {
        return number("SELECT COUNT(*) FROM tensor_download_batch WHERE task_id=?", id);
    }

    private Counts countRows(UUID id) {
        require(id != null);
        return queryOperation(
                () ->
                        jdbc.queryForObject(
                                "SELECT COUNT(CASE WHEN status<>'SPLIT' THEN 1 END),COUNT(CASE WHEN"
                                    + " status='PENDING' THEN 1 END),COUNT(CASE WHEN"
                                    + " status='RUNNING' THEN 1 END),COUNT(CASE WHEN"
                                    + " status='SUCCEEDED' THEN 1 END),COUNT(CASE WHEN"
                                    + " status='FAILED' THEN 1 END),COUNT(CASE WHEN status='SPLIT'"
                                    + " THEN 1 END),COALESCE(SUM(CASE WHEN status='SUCCEEDED' THEN"
                                    + " source_rows ELSE 0 END),0),COALESCE(SUM(CASE WHEN"
                                    + " status='SUCCEEDED' THEN inserted_rows ELSE 0"
                                    + " END),0),COALESCE(SUM(CASE WHEN status='SUCCEEDED' THEN"
                                    + " updated_rows ELSE 0 END),0) FROM tensor_download_batch"
                                    + " WHERE task_id=?",
                                (r, n) ->
                                        new Counts(
                                                r.getLong(1),
                                                r.getLong(2),
                                                r.getLong(3),
                                                r.getLong(4),
                                                r.getLong(5),
                                                r.getLong(6),
                                                r.getBigDecimal(7).longValueExact(),
                                                r.getBigDecimal(8).longValueExact(),
                                                r.getBigDecimal(9).longValueExact()),
                                id.toString()));
    }

    private Optional<DownloadTask> task(String column, UUID id, boolean lock) {
        require(id != null);
        return tasksQuery(
                        "SELECT * FROM tensor_download_task WHERE "
                                + column
                                + "=?"
                                + (lock ? " FOR UPDATE" : ""),
                        id)
                .stream()
                .findFirst();
    }

    private DownloadTask requiredTask(UUID id) {
        return task("task_id", id, false).orElseThrow(DownloadTaskRepository::conflict);
    }

    private Optional<DownloadBatch> batch(UUID id, UUID task, boolean lock) {
        require(id != null);
        return batchQuery(
                        "SELECT * FROM tensor_download_batch WHERE batch_id=? AND task_id=?"
                                + (lock ? " FOR UPDATE" : ""),
                        id,
                        task)
                .stream()
                .findFirst();
    }

    private List<DownloadTask> tasksQuery(String sql, Object... args) {
        return queryOperation(() -> jdbc.query(sql, (r, n) -> mapTask(r), bind(args)));
    }

    private List<DownloadBatch> batchQuery(String sql, Object... args) {
        return queryOperation(() -> jdbc.query(sql, (r, n) -> mapBatch(r), bind(args)));
    }

    private DownloadTask mapTask(ResultSet r) throws SQLException {
        try {
            var mode = DownloadMode.valueOf(r.getString("mode"));
            var policy = json.validatePolicySnapshot(r.getString("policy_snapshot"));
            require(policyMode(policy).equals(mode.name()));
            hash(r.getString("request_hash"));
            hash(r.getString("definition_hash"));
            return new DownloadTask(
                    uuid(r, "task_id"),
                    uuid(r, "submission_id"),
                    r.getString("request_hash"),
                    new DatasetKey(
                            new PluginId(r.getString("plugin_id")),
                            new ApiName(r.getString("api_name"))),
                    mode,
                    json.readTaskParams(r.getString("params")),
                    r.getString("definition_hash"),
                    policy,
                    DownloadTask.Status.valueOf(r.getString("status")),
                    r.getBoolean("plan_ready"),
                    uuid(r, "active_run_id"),
                    r.getInt("run_generation"),
                    r.getLong("version"),
                    r.getLong("request_count"),
                    r.getLong("run_request_count"),
                    error(r, "last_error_code", "last_error_message"),
                    instant(r, "created_at"),
                    instant(r, "updated_at"),
                    instant(r, "queued_at"),
                    instant(r, "started_at"),
                    instant(r, "finished_at"),
                    instant(r, "deadline_at"));
        } catch (RuntimeException failure) {
            throw failure(ErrorCode.QUERY_FAILED);
        }
    }

    private DownloadBatch mapBatch(ResultSet r) throws SQLException {
        try {
            var start = r.getObject("range_start", LocalDate.class);
            var end = r.getObject("range_end", LocalDate.class);
            require((start == null) == (end == null));
            return new DownloadBatch(
                    uuid(r, "batch_id"),
                    uuid(r, "task_id"),
                    uuid(r, "parent_batch_id"),
                    r.getString("batch_key"),
                    start == null ? null : new DateRange(start, end),
                    json.readBatchParams(r.getString("source_params")),
                    DownloadBatch.Status.valueOf(r.getString("status")),
                    r.getInt("attempt_count"),
                    r.getObject("run_generation", Integer.class),
                    r.getLong("source_rows"),
                    r.getLong("inserted_rows"),
                    r.getLong("updated_rows"),
                    error(r, "error_code", "error_message"),
                    instant(r, "created_at"),
                    instant(r, "updated_at"),
                    instant(r, "started_at"),
                    instant(r, "finished_at"));
        } catch (RuntimeException failure) {
            throw failure(ErrorCode.QUERY_FAILED);
        }
    }

    private <T> T tx(boolean reading, Supplier<T> action) {
        if (TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("Repository requires its own transaction");
        try {
            return (reading ? read : write).execute(s -> action.get());
        } catch (DuplicateKeyException | TensorException failure) {
            throw failure;
        } catch (IllegalArgumentException failure) {
            throw invalid();
        } catch (RuntimeException failure) {
            throw failure(reading ? ErrorCode.QUERY_FAILED : ErrorCode.PERSISTENCE_FAILED);
        }
    }

    private <T> T queryOperation(Supplier<T> action) {
        try {
            return action.get();
        } catch (DataAccessException | ArithmeticException failure) {
            throw failure(ErrorCode.QUERY_FAILED);
        }
    }

    private <T> T writeOperation(Supplier<T> action) {
        try {
            return action.get();
        } catch (DataAccessException failure) {
            throw failure(ErrorCode.PERSISTENCE_FAILED);
        }
    }

    private void update(String sql, Object... args) {
        jdbc.update(sql, bind(args));
    }

    private long number(String sql, Object... args) {
        return queryOperation(() -> jdbc.queryForObject(sql, Long.class, bind(args)));
    }

    private static Object[] bind(Object[] args) {
        return Arrays.stream(args).map(v -> v instanceof UUID id ? id.toString() : v).toArray();
    }

    private static void participating() {
        if (!TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("Active transaction required");
    }

    private static void clause(StringBuilder sql, List<Object> args, String column, Object value) {
        sql.append(" AND ").append(column).append("=?");
        args.add(value);
    }

    private static void page(int page, int size) {
        require(page >= 1 && (size == 20 || size == 50 || size == 100));
    }

    private static boolean complete(DownloadTask t, Counts c) {
        return t.planReady() && c.succeeded() == c.totalBatches();
    }

    private static void deadline(DownloadTask t, Instant now) {
        limit(t.deadlineAt() != null && time(now).isBefore(time(t.deadlineAt())));
    }

    private static LocalDateTime time(Instant value) {
        require(value != null);
        return LocalDateTime.ofInstant(value.truncatedTo(ChronoUnit.MILLIS), ZoneOffset.UTC);
    }

    private static Instant instant(ResultSet r, String name) throws SQLException {
        var v = r.getObject(name, LocalDateTime.class);
        return v == null ? null : v.toInstant(ZoneOffset.UTC);
    }

    private static UUID uuid(ResultSet r, String name) throws SQLException {
        var v = r.getString(name);
        if (v == null) return null;
        var id = UUID.fromString(v);
        require(id.toString().equals(v));
        return id;
    }

    private static StoredError error(ResultSet r, String code, String message) throws SQLException {
        var c = r.getString(code);
        require((c == null) == (r.getString(message) == null));
        return c == null ? null : new StoredError(ErrorCode.valueOf(c));
    }

    private static String policyMode(String policy) {
        try {
            return new ObjectMapper().readTree(policy).get("mode").textValue();
        } catch (Exception e) {
            throw invalid();
        }
    }

    private static void hash(String v) {
        require(v != null && v.matches("[0-9a-f]{64}"));
    }

    private static Map<String, Object> immutableParams(Map<String, Object> values) {
        require(values != null);
        try {
            return Map.copyOf(values);
        } catch (RuntimeException failure) {
            throw invalid();
        }
    }

    private static void require(boolean value) {
        if (!value) throw invalid();
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Invalid download task input");
    }

    private static void state(boolean value) {
        if (!value) throw conflict();
    }

    private static void limit(boolean value) {
        if (!value) throw failure(ErrorCode.TASK_LIMIT_EXCEEDED);
    }

    private static TensorException conflict() {
        return failure(ErrorCode.TASK_STATE_CONFLICT);
    }

    private static TensorException failure(ErrorCode code) {
        return new RepositoryException(code);
    }

    private static final class RepositoryException extends TensorException {
        RepositoryException(ErrorCode code) {
            super(code, messageFor(code));
        }
    }

    private static String messageFor(ErrorCode code) {
        return switch (code) {
            case PARAM_REQUIRED -> "Required parameters are missing";
            case PARAM_INVALID -> "Parameters are invalid";
            case PLUGIN_DISABLED -> "Plugin is unavailable";
            case DATASET_MISCONFIGURED -> "Dataset metadata is unavailable";
            case SOURCE_AUTH_FAILED -> "Source authentication failed";
            case SOURCE_PERMISSION_DENIED -> "Source permission denied";
            case SOURCE_RATE_LIMITED -> "Source rate limit exceeded";
            case SOURCE_UNAVAILABLE -> "Source is unavailable";
            case SOURCE_NETWORK_ERROR -> "Source network request failed";
            case SOURCE_TIMEOUT -> "Source request timed out";
            case SOURCE_PAYLOAD_INVALID -> "Source returned an invalid payload";
            case ADAPTER_FIELD_MISSING -> "Source data is missing a required field";
            case ADAPTER_TYPE_INVALID -> "Source data contains an invalid value";
            case PERSISTENCE_FAILED -> "Persistence failed";
            case QUERY_FAILED -> "Query failed";
            case INTERNAL_ERROR -> "Internal server error";
            case TASK_NOT_FOUND -> "Download task was not found";
            case SUBMISSION_CONFLICT -> "Submission ID belongs to a different request";
            case TASK_STATE_CONFLICT -> "Download task state has changed";
            case TASK_DEFINITION_CHANGED -> "Download task definition has changed";
            case BATCH_DOWNLOAD_UNAVAILABLE -> "Batch download is unavailable";
            case TASK_QUEUE_FULL -> "Download task queue is full";
            case BATCH_COMPLETENESS_UNCONFIRMED -> "Batch completeness is unconfirmed";
            case SOURCE_RANGE_MISMATCH -> "Source data is outside the requested range";
            case TASK_LIMIT_EXCEEDED -> "Download task limit exceeded";
            case EXECUTION_INTERRUPTED -> "Download task execution was interrupted";
        };
    }
}
