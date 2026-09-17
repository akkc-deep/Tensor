package com.akkc.tensor.core.integrity;

import static com.akkc.tensor.core.integrity.IntegrityReadException.INVALID_READ_REQUEST;
import static com.akkc.tensor.core.integrity.IntegrityReadException.READ_FAILED;
import static com.akkc.tensor.core.integrity.IntegrityReadException.READ_SESSION_CLOSED;
import static com.akkc.tensor.core.integrity.IntegrityReadException.UNIT_TIME_BUDGET_EXHAUSTED;

import com.akkc.tensor.core.catalog.DatasetCatalog;
import com.akkc.tensor.core.persistence.JdbcValueBinder;
import com.akkc.tensor.core.persistence.SqlIdentifierPolicy;
import com.akkc.tensor.plugin.api.constant.DatasetFields;
import com.akkc.tensor.plugin.api.dataset.BusinessKeyMode;
import com.akkc.tensor.plugin.api.dataset.ColumnDefinition;
import com.akkc.tensor.plugin.api.dataset.DatasetDefinition;
import com.akkc.tensor.plugin.api.dataset.LogicalType;
import com.akkc.tensor.plugin.api.integrity.IntegrityContracts;
import com.akkc.tensor.plugin.api.integrity.IntegrityDateRange;
import com.akkc.tensor.plugin.api.integrity.IntegrityDependency;
import com.akkc.tensor.plugin.api.integrity.IntegrityDescriptor;
import com.akkc.tensor.plugin.api.integrity.IntegrityReadRequest;
import com.akkc.tensor.plugin.api.integrity.IntegrityScope;
import com.akkc.tensor.plugin.api.model.DatasetKey;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TimeZone;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.sql.DataSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;

public final class IntegrityReadRepository {
    private static final String START_SNAPSHOT = "START TRANSACTION WITH CONSISTENT SNAPSHOT, READ ONLY";
    private static final Set<String> TARGET_METADATA = Set.of(
            DatasetFields.SOURCE_PLUGIN, DatasetFields.SOURCE_API, DatasetFields.INGESTED_AT);

    private final DataSource dataSource;
    private final DatasetCatalog catalog;
    private final Clock clock;
    private final SqlIdentifierPolicy identifiers = new SqlIdentifierPolicy();
    private final JdbcValueBinder binder = new JdbcValueBinder();

    public IntegrityReadRepository(DataSource dataSource, DatasetCatalog catalog, Clock clock) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public <T> T withSnapshot(IntegrityScope scope, IntegrityReadPlan plan,
            IntegrityReadBudget budget, int batchSize, Function<ReadSession, T> action) {
        try {
            requireEntry(scope, plan, budget, batchSize, action);
        } catch (IntegrityReadException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw invalid(exception);
        }

        Connection connection = null;
        Session session = null;
        Integer originalIsolation = null;
        Boolean originalReadOnly = null;
        Boolean originalAutoCommit = null;
        T result = null;
        IntegrityReadException failure = null;
        try {
            connection = dataSource.getConnection();
            originalIsolation = connection.getTransactionIsolation();
            originalReadOnly = connection.isReadOnly();
            originalAutoCommit = connection.getAutoCommit();
            connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            connection.setReadOnly(true);
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                statement.setQueryTimeout(queryTimeout(budget));
                statement.execute(START_SNAPSHOT);
            }
            IntegrityScope snapshotScope = new IntegrityScope(scope.datasetKey(), scope.symbol(),
                    scope.startDate(), scope.endDate(), scope.acceptedAt(), clock.instant());
            session = new Session(connection, snapshotScope, plan, budget, batchSize);
            result = action.apply(session);
            budget.check();
            if (session.failure != null) throw session.failure;
        } catch (IntegrityReadException exception) {
            failure = exception;
        } catch (SQLException | RuntimeException exception) {
            failure = failed(exception);
        } finally {
            if (session != null) session.close();
            IntegrityReadException cleanup = cleanup(connection, originalIsolation, originalReadOnly, originalAutoCommit);
            if (failure == null) failure = cleanup;
        }
        if (failure != null) throw failure;
        return result;
    }

    public interface ReadSession {
        IntegrityScope scope();
        void scan(IntegrityReadRequest request, Consumer<List<Map<String, Object>>> rows);
        void scanTarget(Consumer<TargetBatch> rows);
        List<IntegrityReadRequest> readRequests();
    }

    public record TargetBatch(boolean dateScopeUnresolved, List<Map<String, Object>> rows) {
        public TargetBatch {
            rows = List.copyOf(Objects.requireNonNull(rows, "rows"));
        }
    }

    private void requireEntry(IntegrityScope scope, IntegrityReadPlan plan, IntegrityReadBudget budget,
            int batchSize, Function<ReadSession, ?> action) {
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(budget, "budget");
        Objects.requireNonNull(action, "action");
        if (batchSize <= 0 || scope.symbol() == null || scope.symbol().isBlank()
                || scope.snapshotStartedAt() != null || !scope.datasetKey().equals(plan.target().datasetKey())
                || TransactionSynchronizationManager.isActualTransactionActive()) {
            throw invalid(null);
        }
        validatePlan(scope, plan);
        budget.check();
    }

    private void validatePlan(IntegrityScope scope, IntegrityReadPlan plan) {
        IntegrityDescriptor target = plan.target();
        if (target.scopeKind() != IntegrityDescriptor.ScopeKind.STOCK_DATE
                && target.scopeKind() != IntegrityDescriptor.ScopeKind.STOCK_SNAPSHOT) throw invalid(null);
        if (target.dependencies().stream().anyMatch(dependency ->
                !dependency.datasetKey().pluginId().equals(target.datasetKey().pluginId()))) throw invalid(null);
        LinkedHashMap<DatasetKey, DatasetDefinition> definitions = new LinkedHashMap<>();
        DatasetDefinition targetDefinition = definition(target.datasetKey());
        definitions.put(target.datasetKey(), targetDefinition);
        for (IntegrityDependency dependency : target.dependencies()) {
            definitions.put(dependency.datasetKey(), definition(dependency.datasetKey()));
        }
        for (IntegrityReadPlan.ReferencePermit permit : plan.references()) {
            if (!permit.descriptor().datasetKey().pluginId().equals(target.datasetKey().pluginId())) throw invalid(null);
            definitions.put(permit.descriptor().datasetKey(), definition(permit.descriptor().datasetKey()));
        }
        IntegrityContracts.validateDescriptor(target, definitions);

        Set<String> permits = new HashSet<>();
        for (IntegrityReadPlan.ReferencePermit permit : plan.references()) {
            IntegrityDescriptor descriptor = permit.descriptor();
            IntegrityContracts.validateDescriptor(descriptor, definitions);
            String identity = descriptor.datasetKey() + "\0" + permit.purpose();
            if (!permits.add(identity)) throw invalid(null);
            IntegrityDependency dependency = target.dependencies().stream()
                    .filter(value -> value.datasetKey().equals(descriptor.datasetKey())
                            && value.purpose().equals(permit.purpose()))
                    .findFirst().orElseThrow(() -> invalid(null));
            DatasetDefinition reference = definitions.get(descriptor.datasetKey());
            validatePermit(scope, permit, dependency, reference);
        }
        for (IntegrityDependency dependency : target.dependencies()) {
            long matches = plan.references().stream().filter(permit ->
                    permit.descriptor().datasetKey().equals(dependency.datasetKey())
                            && permit.purpose().equals(dependency.purpose())).count();
            if (matches > 1) throw invalid(null);
        }
    }

    private void validatePermit(IntegrityScope scope, IntegrityReadPlan.ReferencePermit permit,
            IntegrityDependency dependency, DatasetDefinition definition) {
        IntegrityDescriptor descriptor = permit.descriptor();
        if (!dependency.columns().containsAll(permit.fixedEqualities().keySet())) throw invalid(null);
        for (Map.Entry<String, Object> equality : permit.fixedEqualities().entrySet()) {
            validateValue(field(definition, equality.getKey(), false), equality.getValue(), false);
        }
        switch (descriptor.scopeKind()) {
            case STOCK_DATE -> {
                if (!Objects.equals(permit.dateField(), descriptor.dateField())
                        || !Objects.equals(permit.range(), scope.range())
                        || !permit.fixedEqualities().isEmpty()) throw invalid(null);
            }
            case STOCK_SNAPSHOT -> {
                if (permit.dateField() != null || permit.range() != null
                        || !permit.fixedEqualities().isEmpty()) throw invalid(null);
            }
            case NON_STOCK -> {
                if (definition.businessKey().mode() != BusinessKeyMode.COMPOSITE
                        || permit.dateField() == null || permit.range() == null
                        || !dependency.columns().contains(permit.dateField())
                        || field(definition, permit.dateField(), false).type != LogicalType.DATE
                        || !allowedReferenceRange(scope.range(), permit.range())) throw invalid(null);
                for (String key : definition.businessKey().fields()) {
                    if (!key.equals(permit.dateField()) && (!permit.fixedEqualities().containsKey(key)
                            || permit.fixedEqualities().get(key) == null)) throw invalid(null);
                }
            }
        }
    }

    private static boolean allowedReferenceRange(IntegrityDateRange scope, IntegrityDateRange permit) {
        if (permit.equals(scope)) return true;
        if (!permit.contains(scope)) return false;
        boolean week = permit.startDate().equals(scope.startDate().with(
                TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)))
                && permit.endDate().equals(scope.endDate().with(
                        TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)));
        boolean month = permit.startDate().equals(scope.startDate().withDayOfMonth(1))
                && permit.endDate().equals(scope.endDate().with(TemporalAdjusters.lastDayOfMonth()));
        return week || month;
    }

    private DatasetDefinition definition(DatasetKey key) {
        return catalog.find(key).orElseThrow(() -> invalid(null));
    }

    private final class Session implements ReadSession {
        private final Connection connection;
        private final IntegrityScope scope;
        private final IntegrityReadPlan plan;
        private final IntegrityReadBudget budget;
        private final int batchSize;
        private final Thread owner = Thread.currentThread();
        private final List<IntegrityReadRequest> requests = new ArrayList<>();
        private boolean active;
        private volatile boolean closed;
        private volatile IntegrityReadException failure;

        private Session(Connection connection, IntegrityScope scope, IntegrityReadPlan plan,
                IntegrityReadBudget budget, int batchSize) {
            this.connection = connection;
            this.scope = scope;
            this.plan = plan;
            this.budget = budget;
            this.batchSize = batchSize;
        }

        @Override
        public IntegrityScope scope() {
            requireOpen();
            return scope;
        }

        @Override
        public void scan(IntegrityReadRequest request, Consumer<List<Map<String, Object>>> rows) {
            requireOpen();
            if (rows == null) throw poison(invalid(null));
            if (active) throw poison(invalid(null));
            active = true;
            try {
                Authorized authorized = authorize(request);
                requests.add(authorized.recorded);
                execute(authorized, rows);
            } catch (IntegrityReadException exception) {
                throw poison(exception);
            } catch (SQLException | RuntimeException exception) {
                throw poison(failed(exception));
            } finally {
                active = false;
            }
        }

        @Override
        public void scanTarget(Consumer<TargetBatch> rows) {
            requireOpen();
            if (rows == null) throw poison(invalid(null));
            DatasetDefinition target = definition(plan.target().datasetKey());
            ArrayList<String> columns = new ArrayList<>();
            target.columns().forEach(column -> columns.add(column.name()));
            columns.addAll(TARGET_METADATA);
            if (target.businessKey().mode() == BusinessKeyMode.FINGERPRINT) columns.add(DatasetFields.BUSINESS_KEY);
            if (plan.target().scopeKind() == IntegrityDescriptor.ScopeKind.STOCK_DATE) {
                scan(new IntegrityReadRequest(target.datasetKey(), columns, Map.of(), plan.target().dateField(),
                        scope.range(), false, "target"), batch -> rows.accept(new TargetBatch(false, batch)));
                scan(new IntegrityReadRequest(target.datasetKey(), columns, Map.of(), plan.target().dateField(),
                        null, true, "target"), batch -> rows.accept(new TargetBatch(true, batch)));
            } else {
                scan(new IntegrityReadRequest(target.datasetKey(), columns, Map.of(), null,
                        null, false, "target"), batch -> rows.accept(new TargetBatch(false, batch)));
            }
        }

        @Override
        public List<IntegrityReadRequest> readRequests() {
            requireOpen();
            return List.copyOf(requests);
        }

        private Authorized authorize(IntegrityReadRequest request) {
            if (request == null) throw invalid(null);
            DatasetDefinition selected = definition(request.datasetKey());
            if (request.datasetKey().equals(plan.target().datasetKey())) {
                return authorizeTarget(request, selected);
            }
            IntegrityReadPlan.ReferencePermit permit = plan.references().stream()
                    .filter(value -> value.descriptor().datasetKey().equals(request.datasetKey())
                            && value.purpose().equals(request.purpose()))
                    .findFirst().orElseThrow(() -> invalid(null));
            IntegrityDependency dependency = plan.target().dependencies().stream()
                    .filter(value -> value.datasetKey().equals(request.datasetKey())
                            && value.purpose().equals(request.purpose()))
                    .findFirst().orElseThrow(() -> invalid(null));
            if (!dependency.columns().containsAll(request.columns()) || request.nullDates()
                    || !Objects.equals(request.dateField(), permit.dateField())
                    || !Objects.equals(request.dateRange(), permit.range())) throw invalid(null);
            LinkedHashMap<String, Object> equalities = checkedEqualities(request, selected, dependency.columns());
            for (Map.Entry<String, Object> fixed : permit.fixedEqualities().entrySet()) {
                Object supplied = equalities.putIfAbsent(fixed.getKey(), fixed.getValue());
                if (supplied != null && !supplied.equals(fixed.getValue())) throw invalid(null);
            }
            IntegrityDescriptor descriptor = permit.descriptor();
            if (descriptor.scopeKind() != IntegrityDescriptor.ScopeKind.NON_STOCK) {
                Object supplied = equalities.putIfAbsent(descriptor.symbolField(), scope.symbol());
                if (supplied != null && !supplied.equals(scope.symbol())) throw invalid(null);
            }
            IntegrityReadRequest recorded = new IntegrityReadRequest(request.datasetKey(), request.columns(),
                    equalities, request.dateField(), request.dateRange(), false, request.purpose());
            return authorized(selected, recorded);
        }

        private Authorized authorizeTarget(IntegrityReadRequest request, DatasetDefinition definition) {
            IntegrityDescriptor descriptor = plan.target();
            if (!"target".equals(request.purpose())) throw invalid(null);
            LinkedHashSet<String> allowed = new LinkedHashSet<>();
            definition.columns().forEach(column -> allowed.add(column.name()));
            allowed.addAll(TARGET_METADATA);
            if (definition.businessKey().mode() == BusinessKeyMode.FINGERPRINT) allowed.add(DatasetFields.BUSINESS_KEY);
            if (!allowed.containsAll(request.columns())) throw invalid(null);
            if (descriptor.scopeKind() == IntegrityDescriptor.ScopeKind.STOCK_DATE) {
                if (!Objects.equals(request.dateField(), descriptor.dateField())
                        || request.nullDates() && request.dateRange() != null
                        || !request.nullDates() && !Objects.equals(request.dateRange(), scope.range())) throw invalid(null);
            } else if (request.dateField() != null || request.dateRange() != null || request.nullDates()) {
                throw invalid(null);
            }
            LinkedHashMap<String, Object> equalities = checkedEqualities(request, definition, allowed);
            Object supplied = equalities.putIfAbsent(descriptor.symbolField(), scope.symbol());
            if (supplied != null && !supplied.equals(scope.symbol())) throw invalid(null);
            IntegrityReadRequest recorded = new IntegrityReadRequest(request.datasetKey(), request.columns(),
                    equalities, request.dateField(), request.dateRange(), request.nullDates(), request.purpose());
            return authorized(definition, recorded);
        }

        private LinkedHashMap<String, Object> checkedEqualities(IntegrityReadRequest request,
                DatasetDefinition definition, java.util.Collection<String> allowed) {
            LinkedHashMap<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<String, Object> equality : request.equalities().entrySet()) {
                if (!allowed.contains(equality.getKey())) throw invalid(null);
                Field field = field(definition, equality.getKey(), false);
                validateValue(field, equality.getValue(), false);
                result.put(equality.getKey(), equality.getValue());
            }
            return result;
        }

        private Authorized authorized(DatasetDefinition definition, IntegrityReadRequest recorded) {
            List<String> cursor = definition.businessKey().mode() == BusinessKeyMode.FINGERPRINT
                    ? List.of(DatasetFields.BUSINESS_KEY) : definition.businessKey().fields();
            LinkedHashSet<String> selected = new LinkedHashSet<>(recorded.columns());
            selected.addAll(cursor);
            List<Field> fields = selected.stream().map(name -> field(definition, name, true)).toList();
            return new Authorized(definition, recorded, List.copyOf(selected), fields, cursor);
        }

        private void execute(Authorized authorized, Consumer<List<Map<String, Object>>> consumer)
                throws SQLException {
            List<Object> cursor = null;
            while (true) {
                budget.check();
                long remaining = budget.remainingItems();
                long limit = Math.min((long) batchSize, remaining == Long.MAX_VALUE ? remaining : remaining + 1);
                PageSql page = sql(authorized, cursor, limit);
                ArrayList<Map<String, Object>> batch = new ArrayList<>();
                List<Object> lastCursor = null;
                try (PreparedStatement statement = connection.prepareStatement(page.sql)) {
                    for (int index = 0; index < page.values.size(); index++) {
                        Bound value = page.values.get(index);
                        binder.bind(statement, index + 1, value.value, value.jdbcType);
                    }
                    statement.setQueryTimeout(queryTimeout(budget));
                    budget.check();
                    try (ResultSet resultSet = statement.executeQuery()) {
                        while (resultSet.next()) {
                            budget.check();
                            List<Object> values = readValues(resultSet, authorized.fields);
                            budget.consume(1);
                            LinkedHashMap<String, Object> row = new LinkedHashMap<>();
                            for (int index = 0; index < authorized.recorded.columns().size(); index++) {
                                String name = authorized.recorded.columns().get(index);
                                int selectedIndex = authorized.selected.indexOf(name);
                                row.put(name, values.get(selectedIndex));
                            }
                            batch.add(Collections.unmodifiableMap(row));
                            ArrayList<Object> keys = new ArrayList<>();
                            for (String name : authorized.cursor) {
                                Object value = values.get(authorized.selected.indexOf(name));
                                if (value == null) throw failed(null);
                                keys.add(value);
                            }
                            lastCursor = List.copyOf(keys);
                        }
                    }
                }
                budget.check();
                if (!batch.isEmpty()) consumer.accept(List.copyOf(batch));
                if (batch.size() < limit) return;
                cursor = lastCursor;
            }
        }

        private PageSql sql(Authorized authorized, List<Object> cursor, long limit) {
            StringBuilder sql = new StringBuilder("SELECT ");
            sql.append(authorized.selected.stream().map(identifiers::quote)
                    .collect(java.util.stream.Collectors.joining(",")));
            sql.append(" FROM ").append(identifiers.quote(authorized.definition.tableName().value()));
            ArrayList<String> predicates = new ArrayList<>();
            ArrayList<Bound> values = new ArrayList<>();
            for (Map.Entry<String, Object> equality : authorized.recorded.equalities().entrySet()) {
                Field field = field(authorized.definition, equality.getKey(), false);
                predicates.add(identifiers.quote(equality.getKey()) + " = ?");
                values.add(bound(field, equality.getValue()));
            }
            if (authorized.recorded.dateField() != null) {
                String date = identifiers.quote(authorized.recorded.dateField());
                if (authorized.recorded.nullDates()) {
                    predicates.add(date + " IS NULL");
                } else {
                    predicates.add(date + " BETWEEN ? AND ?");
                    Field field = field(authorized.definition, authorized.recorded.dateField(), false);
                    values.add(bound(field, authorized.recorded.dateRange().startDate()));
                    values.add(bound(field, authorized.recorded.dateRange().endDate()));
                }
            }
            if (cursor != null) {
                if (authorized.cursor.size() == 1) {
                    predicates.add(identifiers.quote(authorized.cursor.getFirst()) + " > ?");
                } else {
                    String tuple = authorized.cursor.stream().map(identifiers::quote)
                            .collect(java.util.stream.Collectors.joining(",", "(", ")"));
                    String marks = authorized.cursor.stream().map(unused -> "?")
                            .collect(java.util.stream.Collectors.joining(",", "(", ")"));
                    predicates.add(tuple + " > " + marks);
                }
                for (int index = 0; index < cursor.size(); index++) {
                    values.add(bound(field(authorized.definition, authorized.cursor.get(index), true), cursor.get(index)));
                }
            }
            if (!predicates.isEmpty()) sql.append(" WHERE ").append(String.join(" AND ", predicates));
            sql.append(" ORDER BY ").append(authorized.cursor.stream()
                    .map(name -> identifiers.quote(name) + " ASC")
                    .collect(java.util.stream.Collectors.joining(","))).append(" LIMIT ?");
            values.add(new Bound(limit, Types.BIGINT));
            return new PageSql(sql.toString(), List.copyOf(values));
        }

        private IntegrityReadException poison(IntegrityReadException exception) {
            if (failure == null) failure = exception;
            return exception;
        }

        private void requireOpen() {
            if (closed) {
                throw new IntegrityReadException(READ_SESSION_CLOSED, "Integrity read session is closed");
            }
            if (Thread.currentThread() != owner) {
                IntegrityReadException exception = new IntegrityReadException(
                        READ_SESSION_CLOSED, "Integrity read session is closed");
                if (failure == null) failure = exception;
                throw exception;
            }
            if (failure != null) throw failure;
        }

        private void close() {
            closed = true;
        }
    }

    private record Authorized(DatasetDefinition definition, IntegrityReadRequest recorded,
            List<String> selected, List<Field> fields, List<String> cursor) {}
    private record Field(String name, LogicalType type, Integer length, Integer precision,
            Integer scale, List<String> allowedValues, int jdbcType) {}
    private record Bound(Object value, int jdbcType) {}
    private record PageSql(String sql, List<Bound> values) {}

    private static List<Object> readValues(ResultSet resultSet, List<Field> fields) throws SQLException {
        ArrayList<Object> values = new ArrayList<>();
        for (int index = 0; index < fields.size(); index++) {
            int column = index + 1;
            Field field = fields.get(index);
            Object value = switch (field.type) {
                case STRING, TEXT, MONTH, ENUM -> resultSet.getString(column);
                case DATE -> {
                    Date date = resultSet.getDate(column);
                    yield date == null ? null : date.toLocalDate();
                }
                case LONG -> {
                    long number = resultSet.getLong(column);
                    yield resultSet.wasNull() ? null : number;
                }
                case DECIMAL -> resultSet.getBigDecimal(column);
                case null -> {
                    if (field.name.equals(DatasetFields.INGESTED_AT)) {
                        Calendar utc = Calendar.getInstance(TimeZone.getTimeZone(ZoneOffset.UTC));
                        Timestamp timestamp = resultSet.getTimestamp(column, utc);
                        yield timestamp == null ? null : timestamp.toInstant();
                    }
                    yield resultSet.getString(column);
                }
            };
            values.add(normalizeReadValue(field, value));
        }
        return Collections.unmodifiableList(values);
    }

    private static Object normalizeReadValue(Field field, Object value) {
        if (value == null) return null;
        try {
            validateValue(field, value, false);
            return field.type == LogicalType.DECIMAL
                    ? ((BigDecimal) value).setScale(field.scale, RoundingMode.UNNECESSARY) : value;
        } catch (RuntimeException exception) {
            throw failed(null);
        }
    }

    private static Field field(DatasetDefinition definition, String name, boolean cursor) {
        for (ColumnDefinition column : definition.columns()) {
            if (column.name().equals(name)) return new Field(name, column.logicalType(), column.length(),
                    column.precision(), column.scale(), column.allowedValues(), jdbcType(column.logicalType()));
        }
        if (name.equals(DatasetFields.BUSINESS_KEY) && cursor
                && definition.businessKey().mode() == BusinessKeyMode.FINGERPRINT) {
            return new Field(name, LogicalType.STRING, 64, null, null, List.of(), Types.CHAR);
        }
        if (TARGET_METADATA.contains(name)) {
            if (name.equals(DatasetFields.INGESTED_AT)) return new Field(name, null, null, null, null, List.of(), Types.TIMESTAMP);
            return new Field(name, LogicalType.STRING, 64, null, null, List.of(), Types.VARCHAR);
        }
        throw invalid(null);
    }

    private static int jdbcType(LogicalType type) {
        return switch (type) {
            case STRING, ENUM, MONTH -> Types.VARCHAR;
            case TEXT -> Types.LONGVARCHAR;
            case DATE -> Types.DATE;
            case LONG -> Types.BIGINT;
            case DECIMAL -> Types.DECIMAL;
        };
    }

    private static void validateValue(Field field, Object value, boolean nullable) {
        if (value == null) {
            if (!nullable) throw invalid(null);
            return;
        }
        try {
            switch (field.type) {
                case STRING, TEXT, MONTH -> {
                    if (!(value instanceof String text) || field.length != null
                            && text.codePointCount(0, text.length()) > field.length) throw invalid(null);
                }
                case ENUM -> {
                    if (!(value instanceof String text) || text.codePointCount(0, text.length()) > field.length
                            || !field.allowedValues.isEmpty() && !field.allowedValues.contains(text)) throw invalid(null);
                }
                case DATE -> { if (!(value instanceof LocalDate)) throw invalid(null); }
                case LONG -> { if (!(value instanceof Long)) throw invalid(null); }
                case DECIMAL -> {
                    if (!(value instanceof BigDecimal decimal)) throw invalid(null);
                    BigDecimal scaled = decimal.setScale(field.scale, RoundingMode.UNNECESSARY);
                    if (scaled.precision() > field.precision) throw invalid(null);
                }
                case null -> {
                    if (field.name.equals(DatasetFields.INGESTED_AT) && !(value instanceof Instant)) throw invalid(null);
                    if (!field.name.equals(DatasetFields.INGESTED_AT) && !(value instanceof String)) throw invalid(null);
                }
            }
        } catch (ArithmeticException exception) {
            throw invalid(exception);
        }
    }

    private static Bound bound(Field field, Object value) {
        validateValue(field, value, false);
        if (field.type == LogicalType.DECIMAL) {
            value = ((BigDecimal) value).setScale(field.scale, RoundingMode.UNNECESSARY);
        }
        return new Bound(value, field.jdbcType);
    }

    private static IntegrityReadException invalid(Throwable cause) {
        return new IntegrityReadException(INVALID_READ_REQUEST, "Invalid integrity read request");
    }

    private static IntegrityReadException failed(Throwable cause) {
        return new IntegrityReadException(READ_FAILED, "Integrity read failed");
    }

    private static int queryTimeout(IntegrityReadBudget budget) {
        budget.check();
        long seconds = budget.remainingTime().getSeconds();
        if (seconds < 1) {
            throw new IntegrityReadException(UNIT_TIME_BUDGET_EXHAUSTED,
                    "Integrity unit time budget is too short for another query");
        }
        return Math.toIntExact(Math.min(Integer.MAX_VALUE, seconds));
    }

    private static IntegrityReadException cleanup(Connection connection, Integer isolation,
            Boolean readOnly, Boolean autoCommit) {
        if (connection == null) return null;
        SQLException failure = null;
        try { connection.rollback(); } catch (SQLException exception) { failure = exception; }
        if (isolation != null) try { connection.setTransactionIsolation(isolation); }
        catch (SQLException exception) { if (failure == null) failure = exception; }
        if (readOnly != null) try { connection.setReadOnly(readOnly); }
        catch (SQLException exception) { if (failure == null) failure = exception; }
        if (autoCommit != null) try { connection.setAutoCommit(autoCommit); }
        catch (SQLException exception) { if (failure == null) failure = exception; }
        try { connection.close(); } catch (SQLException exception) { if (failure == null) failure = exception; }
        return failure == null ? null : failed(failure);
    }
}
