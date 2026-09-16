package com.akkc.tensor.core.download.task;

import com.akkc.tensor.core.persistence.SqlConstants;
import com.akkc.tensor.plugin.api.constant.PaginationConstants;
import com.akkc.tensor.plugin.api.constant.RequestFields;
import com.akkc.tensor.plugin.api.constant.StringConstants;
import com.akkc.tensor.plugin.api.constant.ValidationConstants;
import com.akkc.tensor.plugin.api.download.batch.DateRange;
import com.akkc.tensor.plugin.api.download.batch.DownloadMode;
import com.akkc.tensor.plugin.api.error.ErrorCode;
import com.akkc.tensor.plugin.api.error.TensorException;
import com.akkc.tensor.plugin.api.model.ApiName;
import com.akkc.tensor.plugin.api.model.DatasetKey;
import com.akkc.tensor.plugin.api.model.PluginId;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.function.Function;
import java.util.function.Supplier;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Fixed task operations; task rows are always locked before batch rows. */
public final class DownloadTaskRepository {
    private static final int TOTAL_BATCHES_INDEX = 1;
    private static final int PENDING_INDEX = 2;
    private static final int RUNNING_INDEX = 3;
    private static final int SUCCEEDED_INDEX = 4;
    private static final int FAILED_INDEX = 5;
    private static final int SPLIT_BATCHES_INDEX = 6;
    private static final int SOURCE_ROWS_INDEX = 7;
    private static final int INSERTED_ROWS_INDEX = 8;
    private static final int UPDATED_ROWS_INDEX = 9;

    private static final String UPDATE_BATCH = "UPDATE tensor_download_batch SET";
    private static final String UPDATE_TASK = "UPDATE tensor_download_task SET";
    private static final String BATCH_WHERE = " WHERE batch_id=?";
    private static final String TASK_WHERE = " WHERE task_id=?";
    private static final String TASK_ID = "task_id";
    private static final String API_NAME = "api_name";
    private static final String PLUGIN_ID = "plugin_id";
    private static final String SUBMISSION_ID = "submission_id";
    private static final String DEFINITION_HASH = "definition_hash";
    private static final String REQUEST_HASH = "request_hash";
    private static final String RUN_GENERATION = "run_generation";
    private static final String CREATED_AT = "created_at";
    private static final String UPDATED_AT = "updated_at";
    private static final String STARTED_AT = "started_at";
    private static final String FINISHED_AT = "finished_at";
    private static final String POLICY_SNAPSHOT = "policy_snapshot";
    private static final String PLAN_READY = "plan_ready";
    private static final String ACTIVE_RUN_ID = "active_run_id";
    private static final String VERSION = "version";
    private static final String REQUEST_COUNT = "request_count";
    private static final String RUN_REQUEST_COUNT = "run_request_count";
    private static final String LAST_ERROR_CODE = "last_error_code";
    private static final String LAST_ERROR_MESSAGE = "last_error_message";
    private static final String QUEUED_AT = "queued_at";
    private static final String DEADLINE_AT = "deadline_at";
    private static final String RANGE_START = "range_start";
    private static final String RANGE_END = "range_end";
    private static final String BATCH_ID = "batch_id";
    private static final String PARENT_BATCH_ID = "parent_batch_id";
    private static final String BATCH_KEY = "batch_key";
    private static final String SOURCE_PARAMS = "source_params";
    private static final String ATTEMPT_COUNT = "attempt_count";
    private static final String SOURCE_ROWS = "source_rows";
    private static final String INSERTED_ROWS = "inserted_rows";
    private static final String UPDATED_ROWS = "updated_rows";
    private static final String ERROR_CODE = "error_code";
    private static final String ERROR_MESSAGE = "error_message";

    private static final String COUNT_QUEUED_TASKS_SQL =
            "SELECT COUNT(*) FROM tensor_download_task WHERE status='QUEUED'";
    private static final String QUEUED_TASKS_SQL =
            "SELECT * FROM tensor_download_task WHERE status='QUEUED' AND active_run_id=? ORDER BY queued_at,task_id LIMIT ?";
    private static final String UNFINISHED_TASKS_SQL =
            "SELECT * FROM tensor_download_task WHERE status IN ('QUEUED','RUNNING') ORDER BY queued_at,task_id";
    private static final String FILTER_WHERE = " WHERE 1=1";
    private static final String COUNT_TASKS_PREFIX = "SELECT COUNT(*) FROM tensor_download_task";
    private static final String SELECT_TASKS_PREFIX = "SELECT * FROM tensor_download_task";
    private static final String TASK_PAGE_ORDER =
            " ORDER BY created_at DESC,task_id DESC LIMIT ? OFFSET ?";
    private static final String EXCLUDE_SPLIT_CLAUSE = " AND status<>'SPLIT'";
    private static final String COUNT_BATCHES_PREFIX = "SELECT COUNT(*) FROM tensor_download_batch";
    private static final String SELECT_BATCHES_PREFIX = "SELECT * FROM tensor_download_batch";
    private static final String BATCH_PAGE_ORDER = " ORDER BY batch_key LIMIT ? OFFSET ?";
    private static final String PENDING_BATCHES_SQL =
            "SELECT * FROM tensor_download_batch WHERE task_id=? AND status='PENDING' ORDER BY batch_key";
    private static final String INSERT_TASK_SQL =
            "INSERT INTO tensor_download_task (task_id,submission_id,request_hash,plugin_id,api_name,mode,params,definition_hash,policy_snapshot,status,plan_ready,active_run_id,run_generation,version,request_count,run_request_count,created_at,updated_at,queued_at) VALUES (?,?,?,?,?,?,?,?,?,'QUEUED',false,?,0,1,0,0,?,?,?)";
    private static final String CLAIM_TASK_SET =
            " status='RUNNING',run_generation=run_generation+1,version=version+1,started_at=?,deadline_at=?,finished_at=NULL,run_request_count=0,updated_at=?";
    private static final String CLAIM_BATCH_SET =
            " status='RUNNING',attempt_count=attempt_count+1,run_generation=?,started_at=?,finished_at=NULL,error_code=NULL,error_message=NULL,updated_at=?";
    private static final String RESERVE_REQUEST_SET =
            " request_count=request_count+1,run_request_count=run_request_count+1,updated_at=?";
    private static final String MARK_PLAN_READY_SQL =
            "UPDATE tensor_download_task SET plan_ready=true,updated_at=? WHERE task_id=?";
    private static final String SPLIT_BATCH_SET =
            " status='SPLIT',finished_at=?,updated_at=?,source_rows=0,inserted_rows=0,updated_rows=0";
    private static final String RESET_FAILED_BATCHES_SET =
            " status='PENDING',error_code=NULL,error_message=NULL,started_at=NULL,finished_at=NULL,updated_at=? WHERE task_id=? AND status='FAILED'";
    private static final String RESUME_INTERRUPTED_CLAUSE =
            " AND error_code='EXECUTION_INTERRUPTED'";
    private static final String REQUEUE_TASK_SET =
            " status='QUEUED',active_run_id=?,version=version+1,queued_at=?,updated_at=?,last_error_code=NULL,last_error_message=NULL,started_at=NULL,finished_at=NULL,deadline_at=NULL";
    private static final String LOCKED_BATCHES_SQL =
            "SELECT * FROM tensor_download_batch WHERE task_id=? ORDER BY batch_key FOR UPDATE";
    private static final String END_TASK_SET =
            " status=?,version=version+1,last_error_code=?,last_error_message=?,finished_at=?,updated_at=?";
    private static final String BATCH_RESULT_SET =
            " status=?,source_rows=?,inserted_rows=?,updated_rows=?,error_code=?,error_message=?,finished_at=?,updated_at=?";
    private static final String BATCH_KEY_REGEX = "[0-9]{6}(?:/[01])*";
    private static final String INSERT_BATCH_SQL =
            "INSERT INTO tensor_download_batch (batch_id,task_id,parent_batch_id,batch_key,range_start,range_end,source_params,status,attempt_count,source_rows,inserted_rows,updated_rows,created_at,updated_at) VALUES (?,?,?,?,?,?,?,'PENDING',0,0,0,0,?,?)";
    private static final String COUNT_BATCH_NODES_SQL =
            "SELECT COUNT(*) FROM tensor_download_batch WHERE task_id=?";
    private static final String COUNT_ROWS_SQL =
            "SELECT COUNT(CASE WHEN status<>'SPLIT' THEN 1 END),COUNT(CASE WHEN status='PENDING' THEN 1 END),COUNT(CASE WHEN status='RUNNING' THEN 1 END),COUNT(CASE WHEN status='SUCCEEDED' THEN 1 END),COUNT(CASE WHEN status='FAILED' THEN 1 END),COUNT(CASE WHEN status='SPLIT' THEN 1 END),COALESCE(SUM(CASE WHEN status='SUCCEEDED' THEN source_rows ELSE 0 END),0),COALESCE(SUM(CASE WHEN status='SUCCEEDED' THEN inserted_rows ELSE 0 END),0),COALESCE(SUM(CASE WHEN status='SUCCEEDED' THEN updated_rows ELSE 0 END),0) FROM tensor_download_batch";
    private static final String TASK_BY_COLUMN_PREFIX = "SELECT * FROM tensor_download_task WHERE ";
    private static final String BATCH_BY_ID_SQL =
            "SELECT * FROM tensor_download_batch WHERE batch_id=? AND task_id=?";

    private final JdbcTemplate jdbc;
    private final DownloadTaskJson json;
    private final TransactionTemplate write;
    private final TransactionTemplate read;
    private final DownloadTaskObserver observer;

    public DownloadTaskRepository(
            JdbcTemplate jdbc, PlatformTransactionManager transactions, DownloadTaskJson json) {
        this(jdbc, transactions, json, DownloadTaskObserver.NOOP);
    }

    public DownloadTaskRepository(JdbcTemplate jdbc, PlatformTransactionManager transactions,
            DownloadTaskJson json, DownloadTaskObserver observer) {
        this.jdbc = Objects.requireNonNull(jdbc);
        this.json = Objects.requireNonNull(json);
        this.observer = Objects.requireNonNull(observer);
        write = new TransactionTemplate(transactions);
        write.setTimeout(SqlConstants.TRANSACTION_TIMEOUT_SECONDS);
        read = new TransactionTemplate(transactions);
        read.setTimeout(SqlConstants.TRANSACTION_TIMEOUT_SECONDS);
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
            return code.message();
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

    public enum TaskStatusGroup {
        ACTIVE(List.of(DownloadTask.Status.QUEUED, DownloadTask.Status.RUNNING)),
        DONE(List.of(DownloadTask.Status.SUCCEEDED)),
        ERROR(List.of(DownloadTask.Status.PARTIAL_FAILED, DownloadTask.Status.FAILED,
                DownloadTask.Status.INTERRUPTED));

        private final List<DownloadTask.Status> statuses;

        TaskStatusGroup(List<DownloadTask.Status> statuses) {
            this.statuses = statuses;
        }
    }

    public record TaskFilter(String pluginId, String apiName, DownloadTask.Status status,
            UUID submissionId, TaskStatusGroup statusGroup) {
        public TaskFilter(String pluginId, String apiName, DownloadTask.Status status,
                UUID submissionId) {
            this(pluginId, apiName, status, submissionId, null);
        }

        public TaskFilter {
            require(status == null || statusGroup == null);
        }
    }

    public record BatchFilter(DownloadBatch.Status status, boolean includeSplit) {}

    public record TaskSnapshot(Optional<DownloadTask> task, Counts counts) {}

    public enum RequeueMode {
        RETRY,
        RESUME
    }

    public Optional<DownloadTask> findTask(UUID id) {
        return tx(true, () -> task(TASK_ID, id, false));
    }

    public Optional<DownloadTask> findSubmission(UUID id) {
        return tx(true, () -> task(SUBMISSION_ID, id, false));
    }

    public long queuedCount() {
        return tx(
                true,
                () -> number(COUNT_QUEUED_TASKS_SQL));
    }

    public List<DownloadTask> queuedTasks(UUID run, int limit) {
        return tx(
                true,
                () -> {
                    require(run != null && limit > 0);
                    return tasksQuery(QUEUED_TASKS_SQL, run, limit);
                });
    }

    public List<DownloadTask> unfinishedTasks() {
        return tx(
                true,
                () ->
                        tasksQuery(UNFINISHED_TASKS_SQL));
    }

    public Page<DownloadTask> tasks(TaskFilter filter, int page, int pageSize) {
        return tx(true, () -> taskPage(filter, page, pageSize, Function.identity()));
    }

    public Page<TaskSnapshot> taskSnapshots(TaskFilter filter, int page, int pageSize) {
        return tx(true, () -> taskPage(filter, page, pageSize, tasks -> tasks.stream()
                .map(task -> new TaskSnapshot(Optional.of(task), countRows(task.taskId())))
                .toList()));
    }

    public Page<DownloadBatch> batches(UUID id, BatchFilter filter, int page, int pageSize) {
        return tx(
                true,
                () -> {
                    page(page, pageSize);
                    var args = new ArrayList<Object>();
                    args.add(id);
                    var where = new StringBuilder(TASK_WHERE);
                    if (filter == null || !filter.includeSplit())
                        where.append(EXCLUDE_SPLIT_CLAUSE);
                    if (filter != null && filter.status() != null)
                        clause(where, args, RequestFields.STATUS, filter.status().name());
                    long total =
                            number(
                                    COUNT_BATCHES_PREFIX + where,
                                    args.toArray());
                    args.add(pageSize);
                    args.add((long) (page - 1) * pageSize);
                    return new Page<>(
                            total,
                            batchQuery(
                                    SELECT_BATCHES_PREFIX + where + BATCH_PAGE_ORDER,
                                    args.toArray()));
                });
    }

    public List<DownloadBatch> pendingBatches(UUID id) {
        return tx(
                true,
                () ->
                        batchQuery(PENDING_BATCHES_SQL, id));
    }

    public Counts counts(UUID id) {
        return tx(true, () -> countRows(id));
    }

    public TaskSnapshot snapshot(UUID id) {
        return tx(true, () -> new TaskSnapshot(task(TASK_ID, id, false), countRows(id)));
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
                            INSERT_TASK_SQL,
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
                    var found = task(TASK_ID, id, true);
                    if (found.isEmpty()) return Optional.empty();
                    var t = found.get();
                    if (t.status() != DownloadTask.Status.QUEUED || !t.activeRunId().equals(run))
                        return Optional.empty();
                    limit(t.runGeneration() < Integer.MAX_VALUE && t.version() < Long.MAX_VALUE);
                    update(
                            UPDATE_TASK
                                + CLAIM_TASK_SET
                                + TASK_WHERE,
                            time(now),
                            time(deadline),
                            time(now),
                            id);
                    var event = new DownloadTaskObserver.TaskStarted(id, t.datasetKey(), t.runGeneration() + 1);
                    observe(() -> observer.taskStarted(event));
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
                            UPDATE_BATCH
                                + CLAIM_BATCH_SET
                                + BATCH_WHERE,
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
                            UPDATE_TASK
                                + RESERVE_REQUEST_SET
                                + TASK_WHERE,
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
                                b.batchKey().equals(String.format(Locale.ROOT, DownloadTaskConstants.BATCH_KEY_FORMAT, i + 1))
                                        && ids.add(b.batchId()));
                    }
                    for (var b : roots) insertBatch(p.taskId(), null, b, now);
                    update(
                            MARK_PLAN_READY_SQL,
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
                            left.batchKey().equals(parent.batchKey() + DownloadTaskConstants.LEFT_BATCH_SUFFIX)
                                    && right.batchKey().equals(parent.batchKey() + DownloadTaskConstants.RIGHT_BATCH_SUFFIX)
                                    && !left.batchId().equals(right.batchId()));
                    require(
                            left.range().start().equals(r.start())
                                    && right.range().end().equals(r.end())
                                    && left.range().end().isBefore(r.end())
                                    && right.range()
                                            .start()
                                            .equals(left.range().end().plusDays(1)));
                    limit(maxNodes >= 0 && nodes(p.taskId()) <= (long) maxNodes - DownloadTaskConstants.SPLIT_CHILD_COUNT);
                    update(
                            UPDATE_BATCH
                                + SPLIT_BATCH_SET
                                + BATCH_WHERE,
                            time(now),
                            time(now),
                            parentId);
                    insertBatch(p.taskId(), parentId, left, now);
                    insertBatch(p.taskId(), parentId, right, now);
                    batchFinished(t, parent, DownloadBatch.Status.SPLIT, 0, 0, 0, null, now);
                    return null;
                });
    }

    public void failBatch(ExecutionPermit p, UUID id, ErrorCode error, Instant now) {
        tx(
                false,
                () -> {
                    var t = lockedTask(p);
                    var b = lockedBatch(p, id);
                    require(error != null);
                    batchResult(id, DownloadBatch.Status.FAILED.name(), error, 0, 0, 0, now);
                    batchFinished(t, b, DownloadBatch.Status.FAILED, 0, 0, 0, error, now);
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
                    taskFinished(t, target, false, c, error, now);
                    return null;
                });
    }

    public void requeue(UUID id, long version, UUID run, RequeueMode mode, Instant now) {
        tx(
                false,
                () -> {
                    require(run != null && mode != null);
                    var t = task(TASK_ID, id, true).orElseThrow(DownloadTaskRepository::conflict);
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
                            UPDATE_BATCH
                                + RESET_FAILED_BATCHES_SET
                                    + (mode == RequeueMode.RESUME
                                            ? RESUME_INTERRUPTED_CLAUSE
                                            : StringConstants.EMPTY),
                            time(now),
                            id);
                    update(
                            UPDATE_TASK
                                + REQUEUE_TASK_SET
                                + TASK_WHERE,
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
                    var t = task(TASK_ID, id, true).orElseThrow(DownloadTaskRepository::conflict);
                    state(
                            t.activeRunId().equals(run)
                                    && t.runGeneration() == generation
                                    && t.version() == version
                                    && (t.status() == DownloadTask.Status.QUEUED
                                            || t.status() == DownloadTask.Status.RUNNING));
                    var batches = lockedBatches(id);
                    var counts = countRows(id);
                    boolean success = complete(t, counts);
                    if (!success)
                        for (var b : batches)
                            if (b.status() == DownloadBatch.Status.RUNNING) {
                                batchResult(
                                        b.batchId(),
                                        DownloadBatch.Status.FAILED.name(),
                                        ErrorCode.EXECUTION_INTERRUPTED,
                                        0,
                                        0,
                                        0,
                                        now);
                                batchFinished(t, b, DownloadBatch.Status.FAILED, 0, 0, 0,
                                        ErrorCode.EXECUTION_INTERRUPTED, now);
                            }
                    var target = success ? DownloadTask.Status.SUCCEEDED : DownloadTask.Status.INTERRUPTED;
                    var error = success ? null : ErrorCode.EXECUTION_INTERRUPTED;
                    endTask(t, target, error, now);
                    var recoveredCounts = success ? counts : new Counts(counts.totalBatches(), counts.pending(),
                            0, counts.succeeded(), counts.failed() + counts.running(), counts.splitBatches(),
                            counts.sourceRows(), counts.insertedRows(), counts.updatedRows());
                    taskFinished(t, target, true, recoveredCounts, error, now);
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
                    var t = lockedTask(p);
                    var b = lockedBatch(p, id);
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
                    batchResult(id, DownloadBatch.Status.SUCCEEDED.name(), null,
                            sourceRows, insertedRows, updatedRows, now);
                    batchFinished(t, b, DownloadBatch.Status.SUCCEEDED,
                            sourceRows, insertedRows, updatedRows, null, now);
                    return null;
                });
    }

    private void batchFinished(DownloadTask task, DownloadBatch batch, DownloadBatch.Status status,
            long sourceRows, long insertedRows, long updatedRows, ErrorCode error, Instant now) {
        var event = new DownloadTaskObserver.BatchFinished(task.taskId(), task.datasetKey(), task.runGeneration(),
                batch.batchId(), status, batch.attemptCount(), duration(batch.startedAt(), now),
                sourceRows, insertedRows, updatedRows, error);
        observe(() -> observer.batchFinished(event));
    }

    private void taskFinished(DownloadTask task, DownloadTask.Status status, boolean recovered,
            Counts counts, ErrorCode error, Instant now) {
        var event = new DownloadTaskObserver.TaskFinished(task.taskId(), task.datasetKey(), task.runGeneration(),
                status, recovered, duration(task.startedAt(), now), task.requestCount(), task.runRequestCount(),
                counts, error);
        observe(() -> observer.taskFinished(event));
    }

    private void observe(Runnable callback) {
        if (observer == DownloadTaskObserver.NOOP) return;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() {
                try {
                    callback.run();
                } catch (RuntimeException ignored) {
                    // Observation must never turn a confirmed commit into a retryable business failure.
                }
            }
        });
    }

    private static long duration(Instant startedAt, Instant now) {
        return startedAt == null ? 0 : Math.max(0, ChronoUnit.MILLIS.between(startedAt, now));
    }

    private DownloadTask lockedTask(ExecutionPermit p) {
        require(p != null);
        var t = task(TASK_ID, p.taskId(), true).orElseThrow(DownloadTaskRepository::conflict);
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
        return batchQuery(LOCKED_BATCHES_SQL, id);
    }

    private void endTask(DownloadTask t, DownloadTask.Status target, ErrorCode error, Instant now) {
        limit(t.version() < Long.MAX_VALUE);
        update(
                UPDATE_TASK
                    + END_TASK_SET
                    + TASK_WHERE,
                target.name(),
                error == null ? null : error.name(),
                error == null ? null : error.message(),
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
                UPDATE_BATCH
                    + BATCH_RESULT_SET
                    + BATCH_WHERE,
                status,
                source,
                inserted,
                updated,
                error == null ? null : error.name(),
                error == null ? null : error.message(),
                time(now),
                time(now),
                id);
    }

    private void validateBatch(DownloadTask t, NewBatch b) {
        require(
                b != null
                        && b.batchId() != null
                        && b.batchKey() != null
                        && b.batchKey().length() <= DownloadTaskConstants.MAX_BATCH_KEY_LENGTH
                        && b.batchKey().matches(BATCH_KEY_REGEX));
        json.writeBatchParams(b.sourceParams());
        if (t.mode() == DownloadMode.SINGLE)
            require(b.range() == null && b.sourceParams().equals(t.params()));
        else {
            require(b.range() != null);
            try {
                var policy = new ObjectMapper().readTree(t.policySnapshot());
                var start = endpointDate(t.params().get(policy.get(DownloadTaskJson.START_PARAMETER).textValue()));
                var end = endpointDate(t.params().get(policy.get(DownloadTaskJson.END_PARAMETER).textValue()));
                require(!b.range().start().isBefore(start) && !b.range().end().isAfter(end));
            } catch (Exception failure) {
                throw invalid();
            }
        }
    }

    private static LocalDate endpointDate(Object value) {
        require(value instanceof String text && text.matches(ValidationConstants.DATE_REGEX));
        return LocalDate.parse((String) value, DateTimeFormatter.BASIC_ISO_DATE);
    }

    private void insertBatch(UUID task, UUID parent, NewBatch b, Instant now) {
        update(
                INSERT_BATCH_SQL,
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
        return number(COUNT_BATCH_NODES_SQL, id);
    }

    private Counts countRows(UUID id) {
        require(id != null);
        return queryOperation(
                () ->
                        jdbc.queryForObject(
                                COUNT_ROWS_SQL + TASK_WHERE,
                                (r, n) ->
                                        new Counts(
                                                r.getLong(TOTAL_BATCHES_INDEX),
                                                r.getLong(PENDING_INDEX),
                                                r.getLong(RUNNING_INDEX),
                                                r.getLong(SUCCEEDED_INDEX),
                                                r.getLong(FAILED_INDEX),
                                                r.getLong(SPLIT_BATCHES_INDEX),
                                                r.getBigDecimal(SOURCE_ROWS_INDEX).longValueExact(),
                                                r.getBigDecimal(INSERTED_ROWS_INDEX).longValueExact(),
                                                r.getBigDecimal(UPDATED_ROWS_INDEX).longValueExact()),
                                id.toString()));
    }

    private Optional<DownloadTask> task(String column, UUID id, boolean lock) {
        require(id != null);
        return tasksQuery(
                        TASK_BY_COLUMN_PREFIX + column
                                + SqlConstants.EQUALS_PARAMETER
                                + (lock ? SqlConstants.FOR_UPDATE : StringConstants.EMPTY),
                        id)
                .stream()
                .findFirst();
    }

    private DownloadTask requiredTask(UUID id) {
        return task(TASK_ID, id, false).orElseThrow(DownloadTaskRepository::conflict);
    }

    private Optional<DownloadBatch> batch(UUID id, UUID task, boolean lock) {
        require(id != null);
        return batchQuery(
                        BATCH_BY_ID_SQL
                                + (lock ? SqlConstants.FOR_UPDATE : StringConstants.EMPTY),
                        id,
                        task)
                .stream()
                .findFirst();
    }

    private List<DownloadTask> tasksQuery(String sql, Object... args) {
        return queryOperation(() -> jdbc.query(sql, (r, n) -> mapTask(r), bind(args)));
    }

    private <T> Page<T> taskPage(TaskFilter filter, int page, int pageSize,
            Function<List<DownloadTask>, List<T>> mapper) {
        page(page, pageSize);
        var args = new ArrayList<Object>();
        var where = new StringBuilder(FILTER_WHERE);
        if (filter != null) {
            if (filter.pluginId() != null) {
                new PluginId(filter.pluginId());
                clause(where, args, PLUGIN_ID, filter.pluginId());
            }
            if (filter.apiName() != null) {
                new ApiName(filter.apiName());
                clause(where, args, API_NAME, filter.apiName());
            }
            if (filter.status() != null)
                clause(where, args, RequestFields.STATUS, filter.status().name());
            if (filter.statusGroup() != null) {
                where.append(SqlConstants.AND).append(RequestFields.STATUS).append(" IN (")
                        .append("?,".repeat(filter.statusGroup().statuses.size()));
                where.setLength(where.length() - 1);
                where.append(')');
                filter.statusGroup().statuses.forEach(status -> args.add(status.name()));
            }
            if (filter.submissionId() != null)
                clause(where, args, SUBMISSION_ID, filter.submissionId());
        }
        long total = number(COUNT_TASKS_PREFIX + where, args.toArray());
        args.add(pageSize);
        args.add((long) (page - 1) * pageSize);
        return new Page<>(total, mapper.apply(tasksQuery(
                SELECT_TASKS_PREFIX + where + TASK_PAGE_ORDER, args.toArray())));
    }

    private List<DownloadBatch> batchQuery(String sql, Object... args) {
        return queryOperation(() -> jdbc.query(sql, (r, n) -> mapBatch(r), bind(args)));
    }

    private DownloadTask mapTask(ResultSet r) throws SQLException {
        try {
            var mode = DownloadMode.valueOf(r.getString(RequestFields.MODE));
            var policy = json.validatePolicySnapshot(r.getString(POLICY_SNAPSHOT));
            require(policyMode(policy).equals(mode.name()));
            hash(r.getString(REQUEST_HASH));
            hash(r.getString(DEFINITION_HASH));
            return new DownloadTask(
                    uuid(r, TASK_ID),
                    uuid(r, SUBMISSION_ID),
                    r.getString(REQUEST_HASH),
                    new DatasetKey(
                            new PluginId(r.getString(PLUGIN_ID)),
                            new ApiName(r.getString(API_NAME))),
                    mode,
                    json.readTaskParams(r.getString(RequestFields.PARAMS)),
                    r.getString(DEFINITION_HASH),
                    policy,
                    DownloadTask.Status.valueOf(r.getString(RequestFields.STATUS)),
                    r.getBoolean(PLAN_READY),
                    uuid(r, ACTIVE_RUN_ID),
                    r.getInt(RUN_GENERATION),
                    r.getLong(VERSION),
                    r.getLong(REQUEST_COUNT),
                    r.getLong(RUN_REQUEST_COUNT),
                    error(r, LAST_ERROR_CODE, LAST_ERROR_MESSAGE),
                    instant(r, CREATED_AT),
                    instant(r, UPDATED_AT),
                    instant(r, QUEUED_AT),
                    instant(r, STARTED_AT),
                    instant(r, FINISHED_AT),
                    instant(r, DEADLINE_AT));
        } catch (RuntimeException failure) {
            throw failure(ErrorCode.QUERY_FAILED);
        }
    }

    private DownloadBatch mapBatch(ResultSet r) throws SQLException {
        try {
            var start = r.getObject(RANGE_START, LocalDate.class);
            var end = r.getObject(RANGE_END, LocalDate.class);
            require((start == null) == (end == null));
            return new DownloadBatch(
                    uuid(r, BATCH_ID),
                    uuid(r, TASK_ID),
                    uuid(r, PARENT_BATCH_ID),
                    r.getString(BATCH_KEY),
                    start == null ? null : new DateRange(start, end),
                    json.readBatchParams(r.getString(SOURCE_PARAMS)),
                    DownloadBatch.Status.valueOf(r.getString(RequestFields.STATUS)),
                    r.getInt(ATTEMPT_COUNT),
                    r.getObject(RUN_GENERATION, Integer.class),
                    r.getLong(SOURCE_ROWS),
                    r.getLong(INSERTED_ROWS),
                    r.getLong(UPDATED_ROWS),
                    error(r, ERROR_CODE, ERROR_MESSAGE),
                    instant(r, CREATED_AT),
                    instant(r, UPDATED_AT),
                    instant(r, STARTED_AT),
                    instant(r, FINISHED_AT));
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
        sql.append(SqlConstants.AND).append(column).append(SqlConstants.EQUALS_PARAMETER);
        args.add(value);
    }

    private static void page(int page, int size) {
        require(page >= PaginationConstants.FIRST_PAGE && PaginationConstants.PAGE_SIZES.contains(size));
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
            return new ObjectMapper().readTree(policy).get(RequestFields.MODE).textValue();
        } catch (Exception e) {
            throw invalid();
        }
    }

    private static void hash(String v) {
        require(v != null && v.matches(ValidationConstants.FINGERPRINT_REGEX));
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
            super(code, code.message());
        }
    }
}
