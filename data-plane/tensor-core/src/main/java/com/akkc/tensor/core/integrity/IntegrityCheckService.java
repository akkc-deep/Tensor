package com.akkc.tensor.core.integrity;

import com.akkc.tensor.core.catalog.DatasetCatalog;
import com.akkc.tensor.core.integrity.IntegrityCheckRepository.*;
import com.akkc.tensor.core.registry.PluginRegistry;
import com.akkc.tensor.plugin.api.IntegrityCheckSupport;
import com.akkc.tensor.plugin.api.dataset.DatasetDefinition;
import com.akkc.tensor.plugin.api.error.*;
import com.akkc.tensor.plugin.api.integrity.*;
import com.akkc.tensor.plugin.api.model.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.dao.DuplicateKeyException;

/** Fixes local check plans before admission; never scans data or invokes source rules. */
public final class IntegrityCheckService {
    private static final Set<String> FIELDS = Set.of("submissionId", "pluginId", "capabilityHash", "symbols",
            "startDate", "endDate", "apiNames");
    private final PluginRegistry plugins;
    private final DatasetCatalog catalog;
    private final IntegrityCheckRepository repository;
    private final IntegrityCheckJson json;
    private final IntegrityCheckQueue queue;
    private final Clock clock;
    private final Settings settings;
    private final Object admissionLock = new Object();
    private boolean accepting = true;

    public IntegrityCheckService(PluginRegistry plugins, DatasetCatalog catalog, IntegrityCheckRepository repository,
            IntegrityCheckJson json, IntegrityCheckQueue queue, Clock clock, Settings settings) {
        this.plugins = Objects.requireNonNull(plugins);
        this.catalog = Objects.requireNonNull(catalog);
        this.repository = Objects.requireNonNull(repository);
        this.json = Objects.requireNonNull(json);
        this.queue = Objects.requireNonNull(queue);
        this.clock = Objects.requireNonNull(clock);
        this.settings = Objects.requireNonNull(settings);
    }

    public record Settings(int maxSymbols, int maxRangeDays, int maxUnits, int queueCapacity, int workers,
            int scanBatchSize, long maxScannedRowsPerUnit, int maxIssuesPerUnit,
            long unitTimeoutSeconds, long taskTimeoutSeconds) {
        public Settings {
            if (maxSymbols <= 0 || maxRangeDays <= 0 || maxUnits <= 0 || queueCapacity <= 0 || workers != 1
                    || scanBatchSize <= 0 || maxScannedRowsPerUnit <= 0 || maxIssuesPerUnit <= 0
                    || unitTimeoutSeconds <= 0 || taskTimeoutSeconds <= 0)
                throw new IllegalArgumentException("Invalid integrity check settings");
        }
        public static Settings defaults() { return new Settings(100, 36600, 4000, 20, 1, 500, 500000, 20000, 120, 1800); }
    }
    public record SubmissionResult(TaskRecord task, boolean created) {
        public SubmissionResult { Objects.requireNonNull(task); }
    }
    public record Capability(PluginId pluginId, boolean localCheckAvailable, String unavailableReason,
            String capabilityHash, Settings limits, List<IntegrityCheckJson.ApiSnapshot> apis) {
        public Capability { apis = List.copyOf(apis); }
    }

    public void stopAccepting() { synchronized (admissionLock) { accepting = false; } }
    public void startAccepting() { synchronized (admissionLock) { accepting = true; } }

    public SubmissionResult submit(Map<String, Object> originalRequest) {
        if (originalRequest == null) throw failure(ErrorCode.PARAM_REQUIRED);
        var request = input(() -> copyMap(originalRequest, 0));
        var submissionId = input(() -> {
            var value = text(required(request, "submissionId"));
            if (!value.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))
                throw failure(ErrorCode.PARAM_INVALID);
            return UUID.fromString(value);
        });
        var hash = input(() -> json.requestHash(request));
        synchronized (admissionLock) {
            var previous = repository.findBySubmissionId(submissionId);
            if (previous.isPresent()) return replay(previous.get(), hash);
            if (!accepting) throw failure(ErrorCode.INTEGRITY_UNAVAILABLE);
            var task = plan(submissionId, hash, request);
            var units = units(task);
            try (var reservation = queue.reserve()) {
                repository.create(task, units);
                reservation.publish(task.checkId());
            } catch (DuplicateKeyException exception) {
                // The losing reservation is released before querying the committed winner.
                return replay(repository.findBySubmissionId(submissionId)
                        .orElseThrow(() -> failure(ErrorCode.PERSISTENCE_FAILED)), hash);
            }
            return new SubmissionResult(repository.find(task.checkId())
                    .orElseThrow(() -> failure(ErrorCode.QUERY_FAILED)), true);
        }
    }

    public Capability capability(PluginId pluginId) {
        if (pluginId == null) throw failure(ErrorCode.PARAM_REQUIRED);
        var availability = plugins.findIntegrity(pluginId);
        if (!availability.available()) return unavailable(pluginId, availability.unavailableReason());
        return capability(pluginId, availability.support());
    }

    public Page<TaskRecord> tasks(TaskFilter filter, int page, int pageSize) {
        return repository.tasks(filter, page, pageSize);
    }

    public Progress progress(UUID checkId) {
        return repository.progress(checkId).orElseThrow(() -> failure(ErrorCode.INTEGRITY_CHECK_NOT_FOUND));
    }

    public Page<ResultRecord> results(UUID checkId, ResultFilter filter, int page, int pageSize) {
        requireTask(checkId);
        return repository.results(checkId, filter, page, pageSize);
    }

    public Page<IssueRecord> issues(UUID checkId, IssueFilter filter, int page, int pageSize) {
        requireTask(checkId);
        return repository.issues(checkId, filter, page, pageSize);
    }

    private void requireTask(UUID checkId) {
        if (repository.find(checkId).isEmpty()) throw failure(ErrorCode.INTEGRITY_CHECK_NOT_FOUND);
    }

    private Capability capability(PluginId pluginId, IntegrityCheckSupport support) {
        try {
            var definitions = catalog.list(pluginId);
            if (definitions.isEmpty()) return unavailable(pluginId, "No local datasets available");
            var snapshots = new ArrayList<IntegrityCheckJson.ApiSnapshot>();
            for (var definition : definitions) {
                var api = definition.datasetKey().apiName();
                var descriptor = support.integrityDescriptor(api).orElse(null);
                var rules = List.copyOf(support.integrityRules(api));
                if (descriptor == null) {
                    if (!rules.isEmpty()) throw new IllegalArgumentException("Undeclared rules");
                    snapshots.add(new IntegrityCheckJson.ApiSnapshot(definition, null, List.of(), List.of()));
                    continue;
                }
                var references = new TreeMap<DatasetKey, DatasetDefinition>(Comparator
                        .comparing((DatasetKey key) -> key.pluginId().value()).thenComparing(key -> key.apiName().value()));
                for (var dependency : descriptor.dependencies()) references.put(dependency.datasetKey(),
                        catalog.find(dependency.datasetKey()).orElseThrow());
                var all = new HashMap<>(references); all.put(definition.datasetKey(), definition);
                IntegrityContracts.validate(descriptor, rules, all);
                var core = descriptor.scopeKind() == IntegrityDescriptor.ScopeKind.NON_STOCK
                        ? List.<IntegrityRuleDescriptor>of() : IntegrityContracts.coreRules(definition);
                snapshots.add(new IntegrityCheckJson.ApiSnapshot(definition, descriptor,
                        List.copyOf(references.values()), core));
            }
            return new Capability(pluginId, true, null, json.capabilityHash(pluginId, snapshots), settings, snapshots);
        } catch (RuntimeException exception) { throw failure(ErrorCode.DATASET_MISCONFIGURED); }
    }

    private Capability unavailable(PluginId id, String reason) {
        return new Capability(id, false, reason, null, settings, List.of());
    }

    private NewTask plan(UUID submissionId, String hash, Map<String, Object> request) {
        if (!FIELDS.containsAll(request.keySet())) throw failure(ErrorCode.PARAM_INVALID);
        for (var name : List.of("pluginId", "capabilityHash", "symbols", "startDate", "endDate")) required(request, name);
        var pluginId = input(() -> new PluginId(text(request.get("pluginId"))));
        var expectedHash = text(request.get("capabilityHash"));
        if (!expectedHash.matches("[0-9a-f]{64}")) throw failure(ErrorCode.PARAM_INVALID);
        var availability = plugins.findIntegrity(pluginId);
        if (!availability.available()) throw failure(ErrorCode.INTEGRITY_UNAVAILABLE);
        var support = availability.support();
        var acceptedAt = clock.instant();
        var symbols = new LinkedHashSet<String>();
        for (var symbol : strings(request.get("symbols"))) {
            var normalized = input(() -> text(support.normalizeIntegritySymbol(symbol)));
            if (normalized.codePointCount(0, normalized.length()) > 255) throw failure(ErrorCode.PARAM_INVALID);
            symbols.add(normalized);
        }
        var range = input(() -> new IntegrityDateRange(date(request.get("startDate")), date(request.get("endDate"))));
        input(() -> { support.validateIntegrityRange(range, acceptedAt); return null; });
        limit(symbols.size(), settings.maxSymbols());
        limit(ChronoUnit.DAYS.between(range.startDate(), range.endDate()) + 1, settings.maxRangeDays());
        var capability = capability(pluginId, support);
        if (!capability.localCheckAvailable()) throw failure(ErrorCode.INTEGRITY_UNAVAILABLE);
        if (!expectedHash.equals(capability.capabilityHash())) throw failure(ErrorCode.INTEGRITY_DEFINITION_CHANGED);
        var available = capability.apis().stream().map(api -> api.definition().datasetKey().apiName()).toList();
        var selected = request.containsKey("apiNames")
                ? strings(request.get("apiNames")).stream().map(value -> input(() -> new ApiName(value))).toList() : available;
        if (!available.containsAll(selected)) throw failure(ErrorCode.PARAM_INVALID);
        var selectedSet = new HashSet<>(selected);
        var apiNames = available.stream().filter(selectedSet::contains).toList();
        long count = 0;
        for (var api : capability.apis()) if (selectedSet.contains(api.definition().datasetKey().apiName()))
            count += nonStock(api) ? 1 : symbols.size();
        limit(count, settings.maxUnits());
        return new NewTask(UUID.randomUUID(), submissionId, pluginId, request,
                new TaskScope(pluginId, List.copyOf(symbols), range.startDate(), range.endDate(), apiNames, acceptedAt),
                hash, capability.capabilityHash(), capability.apis(), acceptedAt);
    }

    private static List<NewUnit> units(NewTask task) {
        var result = new ArrayList<NewUnit>();
        var scope = task.scope();
        for (var api : task.definitionSnapshot()) {
            if (!scope.apiNames().contains(api.definition().datasetKey().apiName())) continue;
            var symbols = nonStock(api) ? Collections.<String>singletonList(null) : scope.symbols();
            for (var symbol : symbols) result.add(new NewUnit(UUID.randomUUID(), new IntegrityScope(
                    api.definition().datasetKey(), symbol, scope.startDate(), scope.endDate(), scope.acceptedAt(), null), api));
        }
        return List.copyOf(result);
    }

    private static boolean nonStock(IntegrityCheckJson.ApiSnapshot api) {
        return api.descriptor() != null && api.descriptor().scopeKind() == IntegrityDescriptor.ScopeKind.NON_STOCK;
    }
    private static SubmissionResult replay(TaskRecord task, String hash) {
        if (!task.requestHash().equals(hash)) throw failure(ErrorCode.SUBMISSION_CONFLICT);
        return new SubmissionResult(task, false);
    }
    private static void limit(long count, long max) { if (count > max) throw failure(ErrorCode.INTEGRITY_LIMIT_EXCEEDED); }
    private static Object required(Map<String, Object> request, String field) {
        var value = request.get(field);
        if (value == null) throw failure(ErrorCode.PARAM_REQUIRED);
        return value;
    }
    private static String text(Object value) {
        if (!(value instanceof String text) || text.isBlank()) throw failure(ErrorCode.PARAM_INVALID);
        return text;
    }
    private static List<String> strings(Object value) {
        if (!(value instanceof List<?> list) || list.isEmpty()) throw failure(ErrorCode.PARAM_INVALID);
        return list.stream().map(IntegrityCheckService::text).toList();
    }
    private static LocalDate date(Object value) {
        var text = text(value);
        if (!text.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) throw failure(ErrorCode.PARAM_INVALID);
        var date = LocalDate.parse(text);
        if (date.getYear() < 1000) throw failure(ErrorCode.PARAM_INVALID);
        return date;
    }
    private static Map<String, Object> copyMap(Map<?, ?> value, int depth) {
        if (depth > 32) throw failure(ErrorCode.PARAM_INVALID);
        var result = new LinkedHashMap<String, Object>();
        value.forEach((key, item) -> result.put(text(key), copy(item, depth + 1)));
        return Collections.unmodifiableMap(result);
    }
    private static Object copy(Object value, int depth) {
        if (depth > 32) throw failure(ErrorCode.PARAM_INVALID);
        if (value instanceof Map<?, ?> map) return copyMap(map, depth);
        if (value instanceof List<?> list) return list.stream().map(item -> copy(item, depth + 1)).toList();
        // The report codec stringifies exact numbers; none is a valid submission field.
        // Reject them before hashing so they cannot replay a previously accepted string.
        if (value == null || value instanceof String || value instanceof Boolean || value instanceof Integer) return value;
        throw failure(ErrorCode.PARAM_INVALID);
    }
    private static <T> T input(Supplier<T> action) {
        try { return action.get(); }
        catch (AdmissionException exception) { throw exception; }
        catch (RuntimeException exception) { throw failure(ErrorCode.PARAM_INVALID); }
    }
    private static AdmissionException failure(ErrorCode code) { return new AdmissionException(code); }
    private static final class AdmissionException extends TensorException {
        AdmissionException(ErrorCode code) { super(code, code.message()); }
    }
}
