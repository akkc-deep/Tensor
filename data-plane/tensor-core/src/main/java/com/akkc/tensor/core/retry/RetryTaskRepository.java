package com.akkc.tensor.core.retry;

import com.akkc.tensor.core.persistence.JdbcValueBinder;
import com.akkc.tensor.plugin.api.download.RecoverySelector;
import com.akkc.tensor.plugin.api.download.RecoverySelector.TargetType;
import com.akkc.tensor.plugin.api.download.RecoverySelector.TimeType;
import com.akkc.tensor.plugin.api.error.ErrorCode;
import com.akkc.tensor.plugin.api.error.TensorException;
import com.akkc.tensor.plugin.api.model.ApiName;
import com.akkc.tensor.plugin.api.model.DatasetKey;
import com.akkc.tensor.plugin.api.model.PluginId;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TimeZone;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.ConnectionHolder;
import org.springframework.transaction.support.TransactionSynchronizationManager;

public final class RetryTaskRepository {
    private static final TaskParametersJson RECORD_JSON = new TaskParametersJson();
    private static final String HEADER_COLUMNS =
            "task_id, plugin_id, api_name, task_params, created_at, updated_at";
    private static final String ITEM_COLUMNS =
            "task_id, target_type, target_value, time_type, time_value, error_code, error_message, updated_at";
    private static final String ITEM_KEY_PREDICATE =
            "task_id=? AND target_type=? AND target_value=? AND time_type=? AND time_value=?";
    private static final String ITEM_ORDER =
            " ORDER BY task_id ASC, target_type ASC, target_value ASC, time_type ASC, time_value ASC";

    private final JdbcTemplate jdbc;
    private final TaskParametersJson json;
    private final JdbcValueBinder binder = new JdbcValueBinder();

    public RetryTaskRepository(JdbcTemplate jdbc, TaskParametersJson json) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.json = Objects.requireNonNull(json, "json");
    }

    public Optional<Header> lockTask(UUID taskId) {
        requireValue(taskId);
        requireTransaction();
        return queryHeader("SELECT " + HEADER_COLUMNS
                + " FROM tensor_download_task WHERE task_id=? FOR UPDATE", taskId);
    }

    public boolean containsItem(ItemKey key) {
        requireValue(key);
        requireTransaction();
        return !jdbc.query("SELECT 1 FROM tensor_download_task_item WHERE " + ITEM_KEY_PREDICATE,
                statement -> bindKey(statement, key, 1), (resultSet, row) -> 1).isEmpty();
    }

    public int deleteItem(ItemKey key) {
        requireValue(key);
        requireTransaction();
        return jdbc.update("DELETE FROM tensor_download_task_item WHERE " + ITEM_KEY_PREDICATE,
                statement -> bindKey(statement, key, 1));
    }

    public boolean hasItems(UUID taskId) {
        requireValue(taskId);
        requireTransaction();
        return !jdbc.query("SELECT 1 FROM tensor_download_task_item WHERE task_id=? LIMIT 1",
                statement -> statement.setString(1, id(taskId)), (resultSet, row) -> 1).isEmpty();
    }

    public int touchTask(UUID taskId, Instant updatedAt) {
        requireValue(taskId);
        requireValue(updatedAt);
        requireTransaction();
        return jdbc.update("UPDATE tensor_download_task SET updated_at=? WHERE task_id=?", statement -> {
            binder.bind(statement, 1, updatedAt, Types.TIMESTAMP);
            statement.setString(2, id(taskId));
        });
    }

    public int deleteEmptyTask(UUID taskId) {
        requireValue(taskId);
        requireTransaction();
        return jdbc.update("DELETE FROM tensor_download_task WHERE task_id=? AND NOT EXISTS "
                        + "(SELECT 1 FROM tensor_download_task_item WHERE task_id=?)",
                statement -> {
                    statement.setString(1, id(taskId));
                    statement.setString(2, id(taskId));
                });
    }

    Map<String, Object> freezeTaskParams(Map<String, Object> taskParams) {
        return json.freeze(taskParams);
    }

    void insertHeader(Header header) {
        Objects.requireNonNull(header, "header");
        requireTransaction();
        jdbc.update("INSERT INTO tensor_download_task "
                        + "(task_id, plugin_id, api_name, task_params, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                statement -> {
                    statement.setString(1, id(header.taskId()));
                    statement.setString(2, header.datasetKey().pluginId().value());
                    statement.setString(3, header.datasetKey().apiName().value());
                    statement.setString(4, json.write(header.taskParams()));
                    binder.bind(statement, 5, header.createdAt(), Types.TIMESTAMP);
                    binder.bind(statement, 6, header.updatedAt(), Types.TIMESTAMP);
                });
    }

    Map<String, Object> readTaskParams(UUID taskId) {
        Objects.requireNonNull(taskId, "taskId");
        requireTransaction();
        String saved = jdbc.queryForObject(
                "SELECT task_params FROM tensor_download_task WHERE task_id=?",
                String.class, id(taskId));
        try {
            return json.read(saved);
        } catch (IllegalArgumentException exception) {
            throw invalidSaved();
        }
    }

    void insertItem(ItemKey key, ErrorCode errorCode, Instant updatedAt) {
        writeItem("INSERT INTO tensor_download_task_item "
                + "(task_id, target_type, target_value, time_type, time_value, error_code, error_message, updated_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)", key, errorCode, updatedAt);
    }

    void upsertItem(ItemKey key, ErrorCode errorCode, Instant updatedAt) {
        writeItem("INSERT INTO tensor_download_task_item "
                + "(task_id, target_type, target_value, time_type, time_value, error_code, error_message, updated_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE "
                + "error_code=VALUES(error_code), error_message=VALUES(error_message), updated_at=VALUES(updated_at)",
                key, errorCode, updatedAt);
    }

    void updateItemReason(ItemKey key, ErrorCode errorCode, Instant updatedAt) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(updatedAt, "updatedAt");
        String message = errorMessage(errorCode);
        requireTransaction();
        jdbc.update("UPDATE tensor_download_task_item SET error_code=?, error_message=?, updated_at=? WHERE "
                        + ITEM_KEY_PREDICATE,
                statement -> {
                    statement.setString(1, errorCode.name());
                    statement.setString(2, message);
                    binder.bind(statement, 3, updatedAt, Types.TIMESTAMP);
                    bindKey(statement, key, 4);
                });
    }

    Optional<Header> findHeader(UUID taskId) {
        Objects.requireNonNull(taskId, "taskId");
        requireTransaction();
        return queryHeader("SELECT " + HEADER_COLUMNS
                + " FROM tensor_download_task WHERE task_id=?", taskId);
    }

    List<Item> findItems(UUID taskId) {
        Objects.requireNonNull(taskId, "taskId");
        requireTransaction();
        return jdbc.query("SELECT " + ITEM_COLUMNS
                        + " FROM tensor_download_task_item WHERE task_id=?" + ITEM_ORDER,
                statement -> statement.setString(1, id(taskId)), this::item);
    }

    long count(Criteria criteria) {
        Objects.requireNonNull(criteria, "criteria");
        requireTransaction();
        QueryParts parts = filters(criteria);
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM tensor_download_task" + parts.where(),
                Long.class, parts.values().toArray());
        return Objects.requireNonNull(count, "count");
    }

    List<Header> findHeaders(Criteria criteria, int limit, long offset) {
        Objects.requireNonNull(criteria, "criteria");
        if (limit < 1 || limit > 100 || offset < 0) {
            throw new IllegalArgumentException("Invalid retry task page bounds");
        }
        requireTransaction();
        QueryParts parts = filters(criteria);
        String sql = "SELECT " + HEADER_COLUMNS + " FROM tensor_download_task" + parts.where()
                + " ORDER BY updated_at DESC, task_id DESC LIMIT ? OFFSET ?";
        return jdbc.query(sql, statement -> {
            int parameter = bindStrings(statement, parts.values(), 1);
            statement.setInt(parameter++, limit);
            statement.setLong(parameter, offset);
        }, this::header);
    }

    List<Item> findItems(List<UUID> taskIds) {
        Objects.requireNonNull(taskIds, "taskIds");
        if (taskIds.isEmpty()) {
            return List.of();
        }
        if (taskIds.size() > 100 || taskIds.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Invalid retry task page identifiers");
        }
        requireTransaction();
        String placeholders = String.join(",", Collections.nCopies(taskIds.size(), "?"));
        return jdbc.query("SELECT " + ITEM_COLUMNS
                        + " FROM tensor_download_task_item WHERE task_id IN (" + placeholders + ")" + ITEM_ORDER,
                statement -> {
                    for (int index = 0; index < taskIds.size(); index++) {
                        statement.setString(index + 1, id(taskIds.get(index)));
                    }
                }, this::item);
    }

    static String errorMessage(ErrorCode errorCode) {
        requireValue(errorCode);
        return switch (errorCode) {
            case SOURCE_AUTH_FAILED -> "Source authentication failed";
            case SOURCE_PERMISSION_DENIED -> "Source permission denied";
            case SOURCE_RATE_LIMITED -> "Source rate limit exceeded";
            case SOURCE_UNAVAILABLE -> "Source is unavailable";
            case SOURCE_NETWORK_ERROR -> "Source network request failed";
            case SOURCE_TIMEOUT -> "Source request timed out";
            case SOURCE_PAYLOAD_INVALID -> "Source returned an invalid payload";
            case SOURCE_TRUNCATED -> "Source response is truncated";
            case SOURCE_COMPLETENESS_UNCONFIRMED -> "Source response completeness is unconfirmed";
            case ADAPTER_FIELD_MISSING -> "Source data is missing a required field";
            case ADAPTER_TYPE_INVALID -> "Source data contains an invalid value";
            case DATA_CONFLICT -> "Source data contains conflicting values";
            case PERSISTENCE_FAILED -> "Persistence failed";
            default -> throw new IllegalArgumentException("Unsupported retry failure error code");
        };
    }

    private Optional<Header> queryHeader(String sql, UUID taskId) {
        List<Header> headers = jdbc.query(sql,
                statement -> statement.setString(1, id(taskId)), this::header);
        return headers.stream().findFirst();
    }

    private void writeItem(String sql, ItemKey key, ErrorCode errorCode, Instant updatedAt) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(updatedAt, "updatedAt");
        String message = errorMessage(errorCode);
        requireTransaction();
        jdbc.update(sql, statement -> {
            int parameter = bindKey(statement, key, 1);
            statement.setString(parameter++, errorCode.name());
            statement.setString(parameter++, message);
            binder.bind(statement, parameter, updatedAt, Types.TIMESTAMP);
        });
    }

    private Header header(ResultSet resultSet, int row) throws SQLException {
        try {
            UUID taskId = savedId(resultSet.getString(1));
            DatasetKey key = new DatasetKey(new PluginId(resultSet.getString(2)), new ApiName(resultSet.getString(3)));
            Map<String, Object> params = json.read(resultSet.getString(4));
            Instant createdAt = instant(resultSet, 5);
            Instant updatedAt = instant(resultSet, 6);
            return new Header(taskId, key, params, createdAt, updatedAt);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw invalidSaved();
        }
    }

    private Item item(ResultSet resultSet, int row) throws SQLException {
        try {
            UUID taskId = savedId(resultSet.getString(1));
            RecoverySelector selector = new RecoverySelector(
                    TargetType.valueOf(resultSet.getString(2)), resultSet.getString(3),
                    TimeType.valueOf(resultSet.getString(4)), resultSet.getString(5));
            ErrorCode errorCode = ErrorCode.valueOf(resultSet.getString(6));
            RetryTaskRepository.errorMessage(errorCode);
            String message = resultSet.getString(7);
            validateSavedMessage(message);
            return new Item(new ItemKey(taskId, selector), errorCode, message, instant(resultSet, 8));
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw invalidSaved();
        }
    }

    private static Instant instant(ResultSet resultSet, int index) throws SQLException {
        Timestamp timestamp = resultSet.getTimestamp(index, utc());
        if (timestamp == null) {
            throw invalidSaved();
        }
        return timestamp.toInstant();
    }

    private static void validateSavedMessage(String message) {
        if (!validMessage(message)) {
            throw invalidSaved();
        }
    }

    private static boolean validMessage(String message) {
        return message != null && !message.isBlank() && message.length() <= 512
                && message.chars().noneMatch(Character::isISOControl);
    }

    private static QueryParts filters(Criteria criteria) {
        List<String> predicates = new ArrayList<>(2);
        List<String> values = new ArrayList<>(2);
        if (criteria.pluginId() != null) {
            predicates.add("plugin_id = ?");
            values.add(criteria.pluginId().value());
        }
        if (criteria.apiName() != null) {
            predicates.add("api_name = ?");
            values.add(criteria.apiName().value());
        }
        return new QueryParts(predicates.isEmpty() ? "" : " WHERE " + String.join(" AND ", predicates), values);
    }

    private static int bindStrings(PreparedStatement statement, List<String> values, int parameter)
            throws SQLException {
        for (String value : values) {
            statement.setString(parameter++, value);
        }
        return parameter;
    }

    private static int bindKey(PreparedStatement statement, ItemKey key, int parameter) throws SQLException {
        RecoverySelector selector = key.selector();
        statement.setString(parameter++, id(key.taskId()));
        statement.setString(parameter++, selector.targetType().name());
        statement.setString(parameter++, selector.targetValue());
        statement.setString(parameter++, selector.timeType().name());
        statement.setString(parameter++, selector.timeValue());
        return parameter;
    }

    private static Calendar utc() {
        return Calendar.getInstance(TimeZone.getTimeZone("UTC"));
    }

    private static String id(UUID value) {
        return value.toString().toLowerCase(java.util.Locale.ROOT);
    }

    static UUID savedId(String text) {
        try {
            UUID value = UUID.fromString(text);
            if (!value.toString().equals(text)) {
                throw invalidSaved();
            }
            return value;
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw invalidSaved();
        }
    }

    private void requireTransaction() {
        DataSource dataSource = jdbc.getDataSource();
        Object resource = dataSource == null ? null : TransactionSynchronizationManager.getResource(dataSource);
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || dataSource == null
                || !(resource instanceof ConnectionHolder holder)
                || holder.getConnection() == null) {
            throw new IllegalStateException("Retry task repository requires a bound transaction connection");
        }
    }

    private static <T> T requireValue(T value) {
        if (value == null) {
            throw new IllegalArgumentException("Invalid retry task value");
        }
        return value;
    }

    private static InvalidSavedTaskException invalidSaved() {
        return new InvalidSavedTaskException();
    }

    public record ItemKey(UUID taskId, RecoverySelector selector) {
        public ItemKey {
            requireValue(taskId);
            requireValue(selector);
        }
    }

    public record Failure(RecoverySelector selector, ErrorCode errorCode) {
        public Failure {
            requireValue(selector);
            errorMessage(errorCode);
        }
    }

    public record Header(
            UUID taskId, DatasetKey datasetKey, Map<String, Object> taskParams,
            Instant createdAt, Instant updatedAt) {
        public Header {
            requireValue(taskId);
            requireValue(datasetKey);
            taskParams = RECORD_JSON.freeze(taskParams);
            requireValue(createdAt);
            requireValue(updatedAt);
        }
    }

    public record Item(ItemKey key, ErrorCode errorCode, String errorMessage, Instant updatedAt) {
        public Item {
            requireValue(key);
            RetryTaskRepository.errorMessage(errorCode);
            if (!validMessage(errorMessage)) {
                throw new IllegalArgumentException("Invalid retry task item");
            }
            requireValue(updatedAt);
        }
    }

    public record Task(Header header, List<Item> items) {
        public Task {
            requireValue(header);
            if (items == null || items.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("Invalid retry task items");
            }
            items = List.copyOf(items);
            Set<ItemKey> keys = new HashSet<>();
            for (Item item : items) {
                if (!item.key().taskId().equals(header.taskId()) || !keys.add(item.key())) {
                    throw new IllegalArgumentException("Invalid retry task items");
                }
            }
        }
    }

    public record Criteria(PluginId pluginId, ApiName apiName, int page, int pageSize) {
        public Criteria {
            if (page < 1 || (pageSize != 20 && pageSize != 50 && pageSize != 100)) {
                throw new IllegalArgumentException("Invalid retry task page");
            }
        }

        public static Criteria defaults() {
            return new Criteria(null, null, 1, 20);
        }
    }

    public record Page(List<Task> items, int page, int pageSize, long totalElements, long totalPages) {
        public Page {
            if (items == null || items.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("Invalid retry task page");
            }
            items = List.copyOf(items);
            if (page < 1 || (pageSize != 20 && pageSize != 50 && pageSize != 100)
                    || totalElements < 0 || totalPages < 0) {
                throw new IllegalArgumentException("Invalid retry task page");
            }
        }
    }

    public record SavedFailure(ItemKey key) {
        public SavedFailure {
            requireValue(key);
        }
    }

    private record QueryParts(String where, List<String> values) {}

    private static final class InvalidSavedTaskException extends TensorException {
        private InvalidSavedTaskException() {
            super(ErrorCode.RETRY_TASK_INVALID, "Saved retry task is invalid");
        }
    }
}
