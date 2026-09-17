package com.akkc.tensor.core.integrity;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.akkc.tensor.core.catalog.DatasetCatalog;
import com.akkc.tensor.core.registry.PluginRegistry;
import com.akkc.tensor.plugin.api.IntegrityCheckSupport;
import com.akkc.tensor.plugin.api.dataset.*;
import com.akkc.tensor.plugin.api.descriptor.*;
import com.akkc.tensor.plugin.api.download.DownloadEnvelope;
import com.akkc.tensor.plugin.api.error.*;
import com.akkc.tensor.plugin.api.integrity.*;
import com.akkc.tensor.plugin.api.model.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;
import org.springframework.dao.DuplicateKeyException;

class IntegrityCheckServiceTest {
    static final PluginId PLUGIN = new PluginId("local");
    static final Instant NOW = Instant.parse("2026-09-16T15:59:59.123456789Z");
    final IntegrityCheckJson json = new IntegrityCheckJson();
    final LocalPlugin plugin = new LocalPlugin();
    final IntegrityCheckRepository repository = mock(IntegrityCheckRepository.class);
    IntegrityCheckQueue queue = new IntegrityCheckQueue(20);
    IntegrityCheckService service;
    final AtomicReference<IntegrityCheckRepository.NewTask> saved = new AtomicReference<>();
    List<IntegrityCheckRepository.NewUnit> units;

    @BeforeEach void setup() {
        service = service(repository, queue, IntegrityCheckService.Settings.defaults());
        doAnswer(call -> {
            saved.set(call.getArgument(0)); units = List.copyOf(call.getArgument(1)); return null;
        }).when(repository).create(any(), anyList());
        when(repository.find(any())).thenAnswer(call -> Optional.of(record(saved.get(), units.size())));
    }

    @Test void closedAdmissionRejectsNewIdsButStillReplaysAcceptedRequests() {
        var request = request(); var accepted = service.submit(request).task();
        when(repository.findBySubmissionId(accepted.submissionId())).thenReturn(Optional.of(accepted));
        service.stopAccepting();
        assertThat(service.submit(request).created()).isFalse();
        rejects(() -> service.submit(request()), ErrorCode.INTEGRITY_UNAVAILABLE);
        service.startAccepting();
        assertThat(service.submit(request()).created()).isTrue();
    }

    @Test void replayPrecedesUnavailablePluginAndFullQueue() throws Exception {
        var request = request();
        var first = service.submit(request);
        var unavailable = mock(PluginRegistry.class);
        var full = new IntegrityCheckQueue(1);
        try (var reservation = full.reserve()) { reservation.publish(first.task().checkId()); }
        var replay = new IntegrityCheckService(unavailable, catalog(plugin.definitions()), repository, json,
                full, Clock.fixed(NOW, ZoneOffset.UTC), IntegrityCheckService.Settings.defaults());
        when(repository.findBySubmissionId(first.task().submissionId())).thenReturn(Optional.of(first.task()));
        assertThat(replay.submit(new TreeMap<>(request))).isEqualTo(new IntegrityCheckService.SubmissionResult(first.task(), false));
        verifyNoInteractions(unavailable);
        verify(repository, times(1)).create(any(), anyList());
        assertThat(full.poll(Duration.ZERO)).contains(first.task().checkId());
        assertThat(full.poll(Duration.ZERO)).isEmpty();
    }

    @Test void freezesCompleteCapabilityAndExactMixedPlanWithoutScanning() throws Exception {
        var request = request();
        request.put("symbols", new ArrayList<>(List.of(" b ", "a", "B")));
        var result = service.submit(request);
        assertThat(result.created()).isTrue();
        assertThat(saved.get().scope().symbols()).containsExactly("B", "A");
        assertThat(saved.get().scope().apiNames()).extracting(ApiName::value)
                .containsExactly("daily", "missing", "non_stock", "snapshot");
        assertThat(units).hasSize(7);
        assertThat(units).extracting(u -> u.scope().symbol()).containsExactly("B", "A", "B", "A", null, "B", "A");
        assertThat(units).allSatisfy(u -> {
            assertThat(u.scope().acceptedAt()).isEqualTo(NOW);
            assertThat(u.scope().snapshotStartedAt()).isNull();
        });
        assertThat(saved.get().createdAt()).isEqualTo(NOW);
        assertThat(saved.get().definitionSnapshot()).hasSize(4);
        assertThat(plugin.acceptedAt).isEqualTo(NOW);
        ((List<String>) request.get("symbols")).clear();
        assertThat(saved.get().originalRequest().get("symbols")).isEqualTo(List.of(" b ", "a", "B"));
        assertThat(queue.poll(Duration.ZERO)).contains(result.task().checkId());
    }

    @Test void selectedApisKeepFullPluginHashAndFollowCatalogOrder() {
        var request = request(); request.put("apiNames", List.of("snapshot", "daily", "daily"));
        service.submit(request);
        assertThat(saved.get().definitionSnapshot()).hasSize(4);
        assertThat(saved.get().capabilityHash()).isEqualTo(request.get("capabilityHash"));
        assertThat(saved.get().scope().apiNames()).extracting(ApiName::value).containsExactly("daily", "snapshot");
        assertThat(units).hasSize(2);
    }

    @Test void changedRawPayloadConflictsEvenWhenNormalizedScopeWouldMatch() {
        var request = request(); request.put("symbols", List.of("A", "B"));
        var first = service.submit(request);
        when(repository.findBySubmissionId(first.task().submissionId())).thenReturn(Optional.of(first.task()));
        for (Object symbols : List.of(List.of("B", "A"), List.of("a", "B"), List.of(" A", "B"))) {
            var changed = new LinkedHashMap<>(request); changed.put("symbols", symbols);
            rejects(() -> service.submit(changed), ErrorCode.SUBMISSION_CONFLICT);
        }
        var explicit = new LinkedHashMap<>(request);
        explicit.put("apiNames", List.of("daily", "missing", "non_stock", "snapshot"));
        rejects(() -> service.submit(explicit), ErrorCode.SUBMISSION_CONFLICT);
    }

    @Test void numericPayloadCannotReplayPreviouslyAcceptedStringSymbols() {
        for (Object numeric : List.of(2147483648L, new java.math.BigDecimal("1.5"))) {
            var request = request(); request.put("symbols", List.of(numeric.toString()));
            var first = service.submit(request).task();
            when(repository.findBySubmissionId(first.submissionId())).thenReturn(Optional.of(first));
            request.put("symbols", List.of(numeric));
            rejects(() -> service.submit(request), ErrorCode.PARAM_INVALID);
        }
    }

    @Test void declaredReferencesAreDeduplicatedSortedAndDoNotAddPlanUnits() {
        plugin.references = true;
        var request = request(); request.put("apiNames", List.of("daily"));
        service.submit(request);
        assertThat(units).hasSize(1);
        assertThat(units.getFirst().snapshot().referenceDefinitions()).extracting(d -> d.datasetKey().apiName().value())
                .containsExactly("non_stock", "snapshot");
        assertThat(saved.get().scope().apiNames()).extracting(ApiName::value).containsExactly("daily");
    }

    @Test void newlyAddedApiChangesFullHashEvenForUnchangedSelectedSubset() {
        var request = request(); request.put("apiNames", List.of("daily"));
        var first = service.submit(request).task();
        plugin.extraApi = true;
        service = service(repository, queue, IntegrityCheckService.Settings.defaults());
        rejects(() -> service.submit(request), ErrorCode.INTEGRITY_DEFINITION_CHANGED);
        when(repository.findBySubmissionId(first.submissionId())).thenReturn(Optional.of(first));
        assertThat(service.submit(request).created()).isFalse();
    }

    @Test void emptyCatalogAndUndeclaredRulesCannotBeAccepted() {
        var empty = new IntegrityCheckService(new PluginRegistry(List.of(plugin)), catalog(List.of()), repository,
                json, queue, Clock.fixed(NOW, ZoneOffset.UTC), IntegrityCheckService.Settings.defaults());
        assertThat(empty.capability(PLUGIN).localCheckAvailable()).isFalse();
        rejects(() -> empty.submit(request()), ErrorCode.INTEGRITY_UNAVAILABLE);
        plugin.undeclared = true;
        rejects(() -> service.capability(PLUGIN), ErrorCode.DATASET_MISCONFIGURED);
    }

    @Test void rejectsStrictInputWithoutPersistingOrReserving() throws Exception {
        for (String required : List.of("submissionId", "pluginId", "capabilityHash", "symbols", "startDate", "endDate")) {
            var missing = request(); missing.remove(required);
            rejects(() -> service.submit(missing), ErrorCode.PARAM_REQUIRED);
            missing.put(required, null);
            rejects(() -> service.submit(missing), ErrorCode.PARAM_REQUIRED);
        }
        rejects(() -> service.submit(null), ErrorCode.PARAM_REQUIRED);
        var cases = List.<Map.Entry<String, Object>>of(
                Map.entry("submissionId", "1-1-1-1-1"), Map.entry("submissionId", UUID.randomUUID()),
                Map.entry("pluginId", "bad-id"), Map.entry("capabilityHash", "F".repeat(64)),
                Map.entry("capabilityHash", "short"), Map.entry("symbols", "A"), Map.entry("symbols", List.of()),
                Map.entry("symbols", List.of(" ")), Map.entry("symbols", List.of(12)),
                Map.entry("symbols", List.of("X".repeat(256))), Map.entry("startDate", "2026-02-29"),
                Map.entry("startDate", "2026-1-01"), Map.entry("startDate", "0999-01-01"),
                Map.entry("endDate", "2025-12-31"), Map.entry("endDate", "10000-01-01"),
                Map.entry("apiNames", List.of()), Map.entry("apiNames", "daily"),
                Map.entry("apiNames", List.of("unknown")), Map.entry("apiNames", List.of(12)),
                Map.entry("unknown", "value"));
        for (var entry : cases) {
            var bad = request(); bad.put(entry.getKey(), entry.getValue());
            rejects(() -> service.submit(bad), ErrorCode.PARAM_INVALID);
        }
        var bad = request(); bad.put("apiNames", null);
        rejects(() -> service.submit(bad), ErrorCode.PARAM_INVALID);
        bad.put("apiNames", Arrays.asList("daily", null));
        rejects(() -> service.submit(bad), ErrorCode.PARAM_INVALID);
        bad.put("apiNames", new Object());
        rejects(() -> service.submit(bad), ErrorCode.PARAM_INVALID);
        bad.put("apiNames", bad);
        rejects(() -> service.submit(bad), ErrorCode.PARAM_INVALID);
        verify(repository, never()).create(any(), anyList());
        assertThat(queue.poll(Duration.ZERO)).isEmpty();
    }

    @Test void boundariesAreInclusiveAndDoNotShrinkUserScope() {
        var settings = new IntegrityCheckService.Settings(2, 2, 2, 20, 1, 500, 500000, 20000, 120, 1800);
        service = service(repository, queue, settings);
        var request = request(); request.put("symbols", List.of("a", "b", "A"));
        request.put("apiNames", List.of("daily")); request.put("endDate", "2026-01-02");
        service.submit(request);
        assertThat(units).hasSize(2);
        for (var entry : List.<Map.Entry<String, Object>>of(Map.entry("symbols", List.of("A", "B", "C")),
                Map.entry("endDate", "2026-01-03"), Map.entry("apiNames", List.of("daily", "missing")))) {
            var over = new LinkedHashMap<>(request); over.put(entry.getKey(), entry.getValue());
            rejects(() -> service.submit(over), ErrorCode.INTEGRITY_LIMIT_EXCEEDED);
        }
        verify(repository, times(1)).create(any(), anyList());
    }

    @Test void capabilityChangesRejectNewSubmissionsButKeepHistoricalReplay() {
        var request = request(); var first = service.submit(request);
        plugin.version = "2";
        assertThat(service.capability(PLUGIN).capabilityHash()).isNotEqualTo(request.get("capabilityHash"));
        rejects(() -> service.submit(request), ErrorCode.INTEGRITY_DEFINITION_CHANGED);
        when(repository.findBySubmissionId(first.task().submissionId())).thenReturn(Optional.of(first.task()));
        assertThat(service.submit(request).created()).isFalse();
    }

    @Test void unavailableAndInvalidMetadataDoNotProducePartialCapabilities() {
        var noPlugin = new IntegrityCheckService(new PluginRegistry(List.of()), catalog(plugin.definitions()),
                repository, json, queue, Clock.fixed(NOW, ZoneOffset.UTC), IntegrityCheckService.Settings.defaults());
        assertThat(noPlugin.capability(PLUGIN).localCheckAvailable()).isFalse();
        assertThat(noPlugin.capability(PLUGIN).capabilityHash()).isNull();
        rejects(() -> noPlugin.submit(request()), ErrorCode.INTEGRITY_UNAVAILABLE);
        plugin.badRules = true;
        rejects(() -> service.capability(PLUGIN), ErrorCode.DATASET_MISCONFIGURED);
    }

    @Test void failedCreateReleasesReservationAndReadFailureDoesNotUnpublish() throws Exception {
        queue = new IntegrityCheckQueue(1); service = service(repository, queue, IntegrityCheckService.Settings.defaults());
        var request = request();
        doThrow(new TestFailure(ErrorCode.PERSISTENCE_FAILED)).when(repository).create(any(), anyList());
        rejects(() -> service.submit(request), ErrorCode.PERSISTENCE_FAILED);
        try (var available = queue.reserve()) { assertThat(available).isNotNull(); }
        doAnswer(call -> { saved.set(call.getArgument(0)); units = call.getArgument(1); return null; })
                .when(repository).create(any(), anyList());
        doThrow(new TestFailure(ErrorCode.QUERY_FAILED)).when(repository).find(any());
        rejects(() -> service.submit(request), ErrorCode.QUERY_FAILED);
        var task = record(saved.get(), units.size());
        when(repository.findBySubmissionId(task.submissionId())).thenReturn(Optional.of(task));
        assertThat(service.submit(request).task().checkId()).isEqualTo(task.checkId());
        assertThat(queue.poll(Duration.ZERO)).contains(task.checkId());
        assertThat(queue.poll(Duration.ZERO)).isEmpty();
    }

    @Test void duplicateConflictRequiresWinnerWithMatchingHashAndReleasesSlot() {
        var request = request(); var winner = service.submit(request).task();
        queue = new IntegrityCheckQueue(1); service = service(repository, queue, IntegrityCheckService.Settings.defaults());
        doThrow(new DuplicateKeyException("private SQL")).when(repository).create(any(), anyList());
        when(repository.findBySubmissionId(winner.submissionId())).thenReturn(Optional.empty(), Optional.of(winner));
        assertThat(service.submit(request).created()).isFalse();
        try (var slot = queue.reserve()) { assertThat(slot).isNotNull(); }
        when(repository.findBySubmissionId(winner.submissionId())).thenReturn(Optional.empty());
        rejects(() -> service.submit(request), ErrorCode.PERSISTENCE_FAILED);
    }

    @Test void exposesHistoricalPagesAndProgressWithoutConsultingCurrentPlugins() {
        var plugins = mock(PluginRegistry.class);
        var catalog = mock(DatasetCatalog.class);
        var historical = new IntegrityCheckService(plugins, catalog, repository, json, queue,
                Clock.fixed(NOW, ZoneOffset.UTC), IntegrityCheckService.Settings.defaults());
        var checkId = UUID.randomUUID();
        var task = mock(IntegrityCheckRepository.TaskRecord.class);
        var progress = mock(IntegrityCheckRepository.Progress.class);
        var taskFilter = new IntegrityCheckRepository.TaskFilter(PLUGIN, IntegrityTaskStatus.COMPLETED, UUID.randomUUID());
        var resultFilter = new IntegrityCheckRepository.ResultFilter(" old ", new ApiName("daily"), IntegrityStatus.UNKNOWN);
        var issueFilter = new IntegrityCheckRepository.IssueFilter(UUID.randomUUID(), " old ", new ApiName("daily"),
                IntegrityIssue.Type.REFERENCE_INCOMPLETE, IntegrityStatus.UNKNOWN, LocalDate.of(2026, 1, 1), null);
        var tasks = new IntegrityCheckRepository.Page<IntegrityCheckRepository.TaskRecord>(2, 7, 1, List.of(task));
        var results = new IntegrityCheckRepository.Page<IntegrityCheckRepository.ResultRecord>(3, 8, 0, List.of());
        var issues = new IntegrityCheckRepository.Page<IntegrityCheckRepository.IssueRecord>(4, 9, 0, List.of());
        when(repository.tasks(taskFilter, 2, 7)).thenReturn(tasks);
        when(repository.progress(checkId)).thenReturn(Optional.of(progress));
        doReturn(Optional.of(task)).when(repository).find(checkId);
        when(repository.results(checkId, resultFilter, 3, 8)).thenReturn(results);
        when(repository.issues(checkId, issueFilter, 4, 9)).thenReturn(issues);

        assertThat(historical.tasks(taskFilter, 2, 7)).isSameAs(tasks);
        assertThat(historical.progress(checkId)).isSameAs(progress);
        assertThat(historical.results(checkId, resultFilter, 3, 8)).isSameAs(results);
        assertThat(historical.issues(checkId, issueFilter, 4, 9)).isSameAs(issues);
        verifyNoInteractions(plugins, catalog);
    }

    @Test void historicalDetailAndChildrenUseTheIntegrityNotFoundError() {
        var missing = UUID.randomUUID();
        when(repository.progress(missing)).thenReturn(Optional.empty());
        doReturn(Optional.empty()).when(repository).find(missing);
        rejects(() -> service.progress(missing), ErrorCode.INTEGRITY_CHECK_NOT_FOUND);
        rejects(() -> service.results(missing, null, 1, 20), ErrorCode.INTEGRITY_CHECK_NOT_FOUND);
        rejects(() -> service.issues(missing, null, 1, 20), ErrorCode.INTEGRITY_CHECK_NOT_FOUND);
        verify(repository, never()).results(any(), any(), anyInt(), anyInt());
        verify(repository, never()).issues(any(), any(), anyInt(), anyInt());
    }

    IntegrityCheckService service(IntegrityCheckRepository repo, IntegrityCheckQueue q, IntegrityCheckService.Settings settings) {
        return new IntegrityCheckService(new PluginRegistry(List.of(plugin)), catalog(plugin.definitions()), repo,
                json, q, Clock.fixed(NOW, ZoneOffset.UTC), settings);
    }
    Map<String, Object> request() {
        return new LinkedHashMap<>(Map.of("submissionId", UUID.randomUUID().toString(), "pluginId", "local",
                "capabilityHash", service.capability(PLUGIN).capabilityHash(), "symbols", List.of("A"),
                "startDate", "2026-01-01", "endDate", "2026-01-01"));
    }
    static void rejects(Runnable action, ErrorCode code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(TensorException.class, e -> {
            assertThat(e.code()).isEqualTo(code); assertThat(e.getMessage()).isEqualTo(code.message());
            assertThat(e.getCause()).isNull();
        });
    }
    static IntegrityCheckRepository.TaskRecord record(IntegrityCheckRepository.NewTask task, int size) {
        var json = new IntegrityCheckJson();
        return new IntegrityCheckRepository.TaskRecord(task.checkId(), task.submissionId(), task.pluginId(),
                task.requestHash(), task.capabilityHash(), json.readValue(json.write(task.originalRequest())),
                json.readValue(json.write(task.scope())), json.readValue(json.capabilitySnapshot(task.pluginId(), task.definitionSnapshot())),
                IntegrityTaskStatus.QUEUED, size, NOW, NOW, null, null, null, null);
    }
    static DatasetCatalog catalog(List<DatasetDefinition> definitions) {
        try {
            var ctor = DatasetCatalog.class.getDeclaredConstructor(List.class); ctor.setAccessible(true);
            return ctor.newInstance(definitions);
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    static class TestFailure extends TensorException { TestFailure(ErrorCode code) { super(code, code.message()); } }

    static class LocalPlugin implements IntegrityCheckSupport {
        String version = "1"; boolean badRules, references, extraApi, undeclared; Instant acceptedAt;
        public PluginDescriptor descriptor() {
            return new PluginDescriptor(PLUGIN, "Local", "Local", true, false, false, "No token", List.of(), List.of());
        }
        public PluginReadiness readiness() { return new PluginReadiness(true, false, false, "No token"); }
        public String normalizeIntegritySymbol(String symbol) {
            if (symbol.isBlank()) throw new IllegalArgumentException("private input");
            return symbol.trim().toUpperCase(Locale.ROOT);
        }
        public void validateIntegrityRange(IntegrityDateRange range, Instant acceptedAt) { this.acceptedAt = acceptedAt; }
        public DownloadEnvelope download(ApiName api, Map<String, Object> params) { throw new AssertionError("No upstream"); }
        List<DatasetDefinition> definitions() {
            var names = new ArrayList<>(List.of("daily", "missing", "non_stock", "snapshot"));
            if (extraApi) names.add("new_api");
            return names.stream().map(api -> {
                var key = new DatasetKey(PLUGIN, new ApiName(api));
                return new DatasetDefinition(key, api, "local", QueryMode.trade_date, List.of(), TableName.from(key), List.of(
                        new ColumnDefinition("symbol", "Symbol", LogicalType.STRING, false, 0, 255, null, null, List.of(), false),
                        new ColumnDefinition("day", "Day", LogicalType.DATE, false, 1, null, null, null, List.of(), false)),
                        new BusinessKeyDefinition(BusinessKeyMode.COMPOSITE, List.of("symbol", "day")), List.of(), null);
            }).toList();
        }
        public Optional<IntegrityDescriptor> integrityDescriptor(ApiName api) {
            if (api.value().equals("missing")) return Optional.empty();
            var kind = switch (api.value()) {
                case "non_stock" -> IntegrityDescriptor.ScopeKind.NON_STOCK;
                case "snapshot" -> IntegrityDescriptor.ScopeKind.STOCK_SNAPSHOT;
                default -> IntegrityDescriptor.ScopeKind.STOCK_DATE;
            };
            var rules = kind == IntegrityDescriptor.ScopeKind.NON_STOCK ? List.<IntegrityRuleDescriptor>of() : List.of(rule(api));
            return Optional.of(new IntegrityDescriptor(new DatasetKey(PLUGIN, api), kind,
                    kind == IntegrityDescriptor.ScopeKind.NON_STOCK ? null : "symbol",
                    kind == IntegrityDescriptor.ScopeKind.STOCK_DATE ? "day" : null,
                    "Date", ZoneOffset.UTC, version, dependencies(api), rules, List.of()));
        }
        List<IntegrityDependency> dependencies(ApiName api) {
            if (!references || !api.value().equals("daily")) return List.of();
            return List.of(new IntegrityDependency(new DatasetKey(PLUGIN, new ApiName("snapshot")), List.of("symbol"), "stock"),
                    new IntegrityDependency(new DatasetKey(PLUGIN, new ApiName("non_stock")), List.of("day"), "dates"),
                    new IntegrityDependency(new DatasetKey(PLUGIN, new ApiName("snapshot")), List.of("day"), "date"));
        }
        IntegrityRuleDescriptor rule(ApiName api) {
            return new IntegrityRuleDescriptor("local." + api.value(), version, "Coverage", IntegrityRuleDescriptor.Dimension.COVERAGE,
                    List.of("symbol", "day"), List.of(), "Local only");
        }
        public List<IntegrityRule> integrityRules(ApiName api) {
            if (badRules) return List.of();
            var descriptors = undeclared && api.value().equals("missing") ? List.of(rule(api))
                    : integrityDescriptor(api).stream().flatMap(d -> d.rules().stream()).toList();
            return descriptors.stream().map(r -> (IntegrityRule) new IntegrityRule() {
                public IntegrityRuleDescriptor descriptor() { return r; }
                public IntegrityRuleResult evaluate(IntegrityScope s, IntegrityContext c, IntegrityIssueSink i) {
                    throw new AssertionError("No execution during admission");
                }
            }).toList();
        }
    }
}
