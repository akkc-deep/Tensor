package com.akkc.tensor.core.download.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.akkc.tensor.core.catalog.DatasetCatalog;
import com.akkc.tensor.core.registry.AdapterRegistry;
import com.akkc.tensor.core.registry.PluginRegistry;
import com.akkc.tensor.core.validation.ParameterValidator;
import com.akkc.tensor.plugin.api.BatchDownloadSupport;
import com.akkc.tensor.plugin.api.DataSourcePlugin;
import com.akkc.tensor.plugin.api.DatasetAdapter;
import com.akkc.tensor.plugin.api.dataset.*;
import com.akkc.tensor.plugin.api.descriptor.*;
import com.akkc.tensor.plugin.api.download.*;
import com.akkc.tensor.plugin.api.download.batch.*;
import com.akkc.tensor.plugin.api.error.ErrorCode;
import com.akkc.tensor.plugin.api.error.TensorException;
import com.akkc.tensor.plugin.api.model.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class DownloadTaskServiceTest {
    private static final PluginId PLUGIN_ID = PluginId.of("task_test");
    private static final ApiName API_NAME = ApiName.of("daily");
    private static final DatasetKey KEY = DatasetKey.of(PLUGIN_ID, API_NAME);
    private static final UUID SUBMISSION_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID RUN_ID = UUID.fromString("20000000-0000-0000-0000-000000000002");
    private static final Instant NOW = Instant.parse("2026-09-11T01:02:03Z");
    private static final ParameterDescriptor STOCK = new ParameterDescriptor(
            "symbol", "Symbol", null, ParameterType.TS_CODE, true, null, List.of(), null, null);
    private static final ParameterDescriptor KIND = new ParameterDescriptor(
            "kind", "Kind", null, ParameterType.TEXT, false, "basic", List.of(), null, null);

    @Test
    void preparesNormalizedSubmissionWithoutAdmissionOrCapacityMutation() {
        Harness h = new Harness();
        h.queued = Long.MAX_VALUE;
        var binding = h.service().prepareSubmission(single(SUBMISSION_ID, " 000001.sz "));
        assertThat(binding.replay()).isFalse();
        assertThat(binding.api()).isEqualTo(h.plugin.single);
        assertThat(binding.submission().params()).containsExactlyInAnyOrderEntriesOf(
                Map.of("symbol", "000001.SZ", "kind", "basic"));
        assertThat(h.stored.get()).isNull();
        assertThat(h.plugin.executions.get()).isZero();
        org.mockito.Mockito.verify(h.repository, org.mockito.Mockito.never()).queuedCount();
    }

    @Test
    void preparedReplayUsesStoredValuesBeforeCurrentAvailabilityAndKeepsConflictsStable() {
        Harness h = new Harness();
        var original = h.service().submit(single(SUBMISSION_ID, "000001.SZ")).task();
        h.plugin.available = false;
        h.queued = Long.MAX_VALUE;
        var service = h.service(new DownloadTaskService.Settings(false, 1, 1));
        var binding = service.prepareSubmission(single(SUBMISSION_ID, " 000001.sz "));
        assertThat(binding.replay()).isTrue();
        assertThat(binding.api()).isNull();
        assertThat(binding.submission()).isEqualTo(new DownloadTaskService.Submission(
                SUBMISSION_ID, KEY, DownloadMode.SINGLE, original.params()));
        code(ErrorCode.SUBMISSION_CONFLICT,
                () -> service.prepareSubmission(single(SUBMISSION_ID, "000002.SZ")));
        assertThat(h.stored.get()).isEqualTo(original);
    }

    @Test
    void preparationIsNotAuthorizationAndFinalSubmitRevalidates() {
        Harness h = new Harness();
        var service = h.service();
        var binding = service.prepareSubmission(single(SUBMISSION_ID, "000001.SZ"));
        h.queued = Long.MAX_VALUE;
        code(ErrorCode.TASK_QUEUE_FULL, () -> service.submit(binding.submission()));
        assertThat(h.stored.get()).isNull();
        code(ErrorCode.PARAM_INVALID, () -> service.prepareSubmission(null));
        assertThatThrownBy(() -> new DownloadTaskService.SubmissionBinding(binding.submission(), null, false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DownloadTaskService.SubmissionBinding(binding.submission(), binding.api(), true))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DownloadTaskService.SubmissionBinding(null, binding.api(), false))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void preparationHonorsCoordinatorAdmissionForNewKeysButKeepsHistoricalReplayAndQueryErrors() {
        Harness h = new Harness();
        var service = h.service();
        var original = service.submit(single(SUBMISSION_ID, "000001.SZ")).task();
        var coordinator = mock(DownloadTaskCoordinator.class);
        doThrow(new DownloadTaskService.TaskException(ErrorCode.TASK_STATE_CONFLICT))
                .when(coordinator).checkAdmission();
        service.bindCoordinator(coordinator);
        code(ErrorCode.TASK_STATE_CONFLICT,
                () -> service.prepareSubmission(single(UUID.randomUUID(), "000001.SZ")));
        assertThat(service.prepareSubmission(single(SUBMISSION_ID, " 000001.sz ")).submission().params())
                .isEqualTo(original.params());
        var unreadable = UUID.randomUUID();
        when(h.repository.findSubmission(unreadable)).thenThrow(
                new DownloadTaskService.TaskException(ErrorCode.QUERY_FAILED));
        code(ErrorCode.QUERY_FAILED, () -> service.prepareSubmission(single(unreadable, "000001.SZ")));
        assertThat(h.stored.get()).isEqualTo(original);
    }

    @Test
    void createsSingleSubmissionAndReturnsSameTaskForNormalizedEquivalentReplay() {
        Harness h = new Harness();
        var created = h.service().submit(single(SUBMISSION_ID, " 000001.sz "));
        var replayed = h.service().submit(single(SUBMISSION_ID, "000001.SZ"));

        assertThat(created.created()).isTrue();
        assertThat(created.task().params()).containsExactlyInAnyOrderEntriesOf(
                Map.of("symbol", "000001.SZ", "kind", "basic"));
        assertThat(replayed).isEqualTo(new DownloadTaskService.SubmissionResult(created.task(), false));
        assertThat(h.plugin.executions.get()).isZero();
    }

    @Test
    void settingsAndSubmissionDefensivelyValidateTheirShape() {
        assertThat(DownloadTaskService.Settings.defaults())
                .isEqualTo(new DownloadTaskService.Settings(true, 100, 36_600));
        assertThatThrownBy(() -> new DownloadTaskService.Settings(true, 0, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DownloadTaskService.Settings(true, 1, 0))
                .isInstanceOf(IllegalArgumentException.class);
        Map<String, Object> mutable = new LinkedHashMap<>(Map.of("symbol", "000001.SZ"));
        var submission = new DownloadTaskService.Submission(SUBMISSION_ID, KEY, DownloadMode.SINGLE, mutable);
        mutable.put("symbol", "000002.SZ");
        assertThat(submission.params()).containsEntry("symbol", "000001.SZ");

        Harness h = new Harness();
        Map<Object, Object> badKey = new LinkedHashMap<>();
        badKey.put(1, "private-value");
        Map<String, Object> nullValue = new LinkedHashMap<>();
        nullValue.put("symbol", null);
        for (org.assertj.core.api.ThrowableAssert.ThrowingCallable invalid
                : List.<org.assertj.core.api.ThrowableAssert.ThrowingCallable>of(
                        () -> h.service().submit(null),
                        () -> h.service().submit(new DownloadTaskService.Submission(
                                null, KEY, DownloadMode.SINGLE, Map.of())),
                        () -> h.service().submit(new DownloadTaskService.Submission(
                                SUBMISSION_ID, null, DownloadMode.SINGLE, Map.of())),
                        () -> h.service().submit(new DownloadTaskService.Submission(
                                SUBMISSION_ID, KEY, null, Map.of())),
                        () -> h.service().submit(new DownloadTaskService.Submission(
                                SUBMISSION_ID, KEY, DownloadMode.SINGLE, null)),
                        () -> h.service().submit(new DownloadTaskService.Submission(
                                SUBMISSION_ID, KEY, DownloadMode.SINGLE, (Map) badKey)),
                        () -> h.service().submit(new DownloadTaskService.Submission(
                                SUBMISSION_ID, KEY, DownloadMode.SINGLE, nullValue)),
                        () -> h.service().submit(new DownloadTaskService.Submission(
                                SUBMISSION_ID, KEY, DownloadMode.SINGLE,
                                Map.of("symbol", List.of("private-value")))))) {
            code(ErrorCode.PARAM_INVALID, invalid);
        }
    }

    @Test
    void capabilitiesExposeOriginalMetadataIndependentlyOfAdmissionSettings() {
        Harness h = new Harness();
        var capabilities = h.service(new DownloadTaskService.Settings(false, 1, 1)).capabilities(KEY);
        assertThat(capabilities.single()).isEqualTo(new DownloadTaskService.SingleCapability(true, h.plugin.single));
        assertThat(capabilities.range()).isEqualTo(h.plugin.range);
        assertThat(h.plugin.combinations.get()).isZero();
        assertThat(h.plugin.executions.get()).isZero();
    }

    @Test
    void capabilitiesReturnExplicitUnsupportedDescriptorsAndPreserveUnknownAvailability() {
        Harness unavailable = new Harness();
        unavailable.plugin.available = false;
        var unavailableResult = unavailable.service().capabilities(KEY);
        assertThat(unavailableResult.single().available()).isFalse();
        assertThat(unavailableResult.range().availability()).isEqualTo(BatchDownloadDescriptor.Availability.UNSUPPORTED);
        assertThat(unavailableResult.range().unavailableReason()).isEqualTo("Plugin or dataset is unavailable");

        Harness ordinary = new Harness();
        var ordinaryResult = ordinary.service(ordinary.plugin.asOrdinary(), ordinary.catalog, ordinary.adapters(),
                DownloadTaskService.Settings.defaults()).capabilities(KEY);
        assertThat(ordinaryResult.single().available()).isTrue();
        assertThat(ordinaryResult.range().parameters()).isEmpty();
        assertThat(ordinaryResult.range().unavailableReason()).isEqualTo("Range download is not supported");
        assertThat(ordinaryResult.range().policyVersion()).isEqualTo("unsupported-v1");

        Harness empty = new Harness();
        empty.plugin.range = null;
        assertThat(empty.service().capabilities(KEY).range().unavailableReason())
                .isEqualTo("Range download is not supported");

        Harness unknown = new Harness();
        unknown.plugin.range = policy(BatchDownloadDescriptor.Availability.NEEDS_VERIFICATION, "Needs proof", "v1");
        assertThat(unknown.service().capabilities(KEY).range()).isEqualTo(unknown.plugin.range);
    }

    @Test
    void capabilitiesRejectMissingDuplicateAndBrokenMetadataWithoutLeakingCauses() {
        Harness h = new Harness();
        h.plugin.nullDescriptor = true;
        code(ErrorCode.DATASET_MISCONFIGURED, () -> h.service().capabilities(KEY));
        h.plugin.nullDescriptor = false;
        h.plugin.descriptorFailure = new IllegalStateException("private-metadata");
        code(ErrorCode.DATASET_MISCONFIGURED, () -> h.service().capabilities(KEY));
        var classified = new DownloadTaskService.TaskException(ErrorCode.BATCH_DOWNLOAD_UNAVAILABLE);
        h.plugin.descriptorFailure = classified;
        assertThatThrownBy(() -> h.service().capabilities(KEY)).isSameAs(classified);
        code(ErrorCode.DATASET_MISCONFIGURED,
                () -> h.service(new PluginRegistry(List.of()), h.catalog, h.adapters(),
                        DownloadTaskService.Settings.defaults()).capabilities(KEY));
        code(ErrorCode.DATASET_MISCONFIGURED,
                () -> h.service(new PluginRegistry(List.of(h.plugin, h.plugin)), h.catalog, h.adapters(),
                        DownloadTaskService.Settings.defaults()).capabilities(KEY));
    }

    @Test
    void admissionMapsAvailabilityCapacityAndRegistrationFailuresToSpecificCodes() {
        Harness disabled = new Harness();
        code(ErrorCode.PLUGIN_DISABLED, () -> disabled.service(new DownloadTaskService.Settings(false, 1, 1))
                .submit(single(SUBMISSION_ID, "000001.SZ")));
        Harness full = new Harness();
        full.queued = 1;
        code(ErrorCode.TASK_QUEUE_FULL, () -> full.service(new DownloadTaskService.Settings(true, 1, 100))
                .submit(single(SUBMISSION_ID, "000001.SZ")));
        Harness rangeUnavailable = new Harness();
        rangeUnavailable.plugin.range = policy(
                BatchDownloadDescriptor.Availability.NEEDS_VERIFICATION, "Needs proof", "v1");
        code(ErrorCode.BATCH_DOWNLOAD_UNAVAILABLE,
                () -> rangeUnavailable.service().submit(rangeSubmission(SUBMISSION_ID, "20280228", "20280301")));
        Harness missing = new Harness();
        code(ErrorCode.DATASET_MISCONFIGURED,
                () -> missing.service(missing.plugin, mock(DatasetCatalog.class), missing.adapters(),
                        DownloadTaskService.Settings.defaults()).submit(single(SUBMISSION_ID, "000001.SZ")));
        code(ErrorCode.DATASET_MISCONFIGURED,
                () -> missing.service(missing.plugin, missing.catalog, new AdapterRegistry(List.of()),
                        DownloadTaskService.Settings.defaults()).submit(single(SUBMISSION_ID, "000001.SZ")));
    }

    @Test
    void rangeUsesDescriptorNamesClosedDatesLimitsAndPureSourceValidation() {
        Harness h = new Harness();
        var accepted = h.service(new DownloadTaskService.Settings(true, 100, 3))
                .submit(rangeSubmission(SUBMISSION_ID, "20280228", "20280301"));
        assertThat(accepted.task().params()).containsEntry("from", "20280228").containsEntry("to", "20280301");
        assertThat(h.plugin.lastRange).isEqualTo(new DateRange(
                LocalDate.of(2028, 2, 28), LocalDate.of(2028, 3, 1)));
        assertThat(h.plugin.combinations.get()).isOne();
        assertThat(h.plugin.executions.get()).isZero();

        assertThat(new Harness().service(new DownloadTaskService.Settings(true, 100, 1))
                .submit(rangeSubmission(UUID.randomUUID(), "20991231", "20991231")).created()).isTrue();
        code(ErrorCode.TASK_LIMIT_EXCEEDED,
                () -> new Harness().service(new DownloadTaskService.Settings(true, 100, 3))
                        .submit(rangeSubmission(UUID.randomUUID(), "20280228", "20280302")));
        for (Map<String, Object> invalid : List.<Map<String, Object>>of(
                Map.of("symbol", "000001.SZ", "from", "20280230", "to", "20280301"),
                Map.of("symbol", "000001.SZ", "from", "20280301", "to", "20280228"),
                Map.of("symbol", "000001.SZ", "from", "20280228"),
                Map.of("symbol", "000001.SZ", "from", "20280228", "to", "20280301", "extra", "x"))) {
            code(invalid.containsKey("to") ? ErrorCode.PARAM_INVALID : ErrorCode.PARAM_REQUIRED,
                    () -> new Harness().service().submit(new DownloadTaskService.Submission(
                            UUID.randomUUID(), KEY, DownloadMode.RANGE, invalid)));
        }
        Harness rejected = new Harness();
        var classified = new DownloadTaskService.TaskException(ErrorCode.BATCH_DOWNLOAD_UNAVAILABLE);
        rejected.plugin.sourceFailure = classified;
        assertThatThrownBy(() -> rejected.service().submit(
                rangeSubmission(UUID.randomUUID(), "20280228", "20280301"))).isSameAs(classified);
        assertThat(rejected.stored.get()).isNull();
        Harness malformed = new Harness();
        malformed.plugin.sourceResult = Map.of("api_key", "private-value");
        code(ErrorCode.DATASET_MISCONFIGURED,
                () -> malformed.service().submit(rangeSubmission(UUID.randomUUID(), "20280228", "20280301")));
    }

    @Test
    void allFourStandardRangeShapesNormalizeThroughTheSelectedDescriptor() {
        for (String prefix : Arrays.asList(null, "exchange", "exchange_id", "ts_code")) {
            Harness h = new Harness();
            h.plugin.range = standardPolicy(prefix);
            Map<String, Object> raw = new LinkedHashMap<>();
            if (prefix != null) raw.put(prefix, prefix.equals("ts_code") ? " 000001.sz " : "SSE");
            raw.put("start_date", "20280228");
            raw.put("end_date", "20280301");

            var request = new DownloadTaskService.Submission(UUID.randomUUID(), KEY, DownloadMode.RANGE, raw);
            var prepared = h.service().prepareSubmission(request);
            assertThat(prepared.replay()).isFalse();
            assertThat(prepared.api().queryMode()).isEqualTo(QueryMode.date_range);
            assertThat(prepared.api().parameters()).isEqualTo(h.plugin.range.parameters());
            DownloadTask task = h.service().submit(prepared.submission()).task();

            assertThat(task.params()).containsEntry("start_date", "20280228").containsEntry("end_date", "20280301");
            if (prefix != null) assertThat(task.params()).containsEntry(
                    prefix, prefix.equals("ts_code") ? "000001.SZ" : "SSE");
            assertThat(h.plugin.executions.get()).isZero();
        }
    }

    @Test
    void normalizedTaskSizeLimitIsCheckedBeforeInsert() {
        Harness h = new Harness();
        h.plugin.single = new ApiDescriptor(API_NAME, "Daily", "market", QueryMode.snapshot,
                List.of(new ParameterDescriptor("payload", "Payload", null, ParameterType.TEXT,
                        true, null, List.of(), null, null)));
        h.definition = dataset(h.plugin.single.parameters(), false);
        assertThat(h.service().submit(new DownloadTaskService.Submission(
                SUBMISSION_ID, KEY, DownloadMode.SINGLE, Map.of("payload", "p".repeat(8_178))))
                .task().params().get("payload")).isEqualTo("p".repeat(8_178));

        Harness oversized = new Harness();
        oversized.plugin.single = h.plugin.single;
        oversized.definition = h.definition;
        code(ErrorCode.PARAM_INVALID, () -> oversized.service().submit(new DownloadTaskService.Submission(
                UUID.randomUUID(), KEY, DownloadMode.SINGLE, Map.of("payload", "p".repeat(8_179)))));
        code(ErrorCode.PARAM_INVALID, () -> oversized.service().prepareSubmission(new DownloadTaskService.Submission(
                UUID.randomUUID(), KEY, DownloadMode.SINGLE, Map.of("payload", "p".repeat(8_179)))));
        assertThat(oversized.stored.get()).isNull();
    }

    @Test
    void malformedParameterMetadataMapsToSanitizedDatasetError() {
        Harness h = new Harness();
        h.plugin.single = new ApiDescriptor(API_NAME, "Daily", "market", QueryMode.snapshot,
                List.of(new ParameterDescriptor("symbol", "Symbol", null, ParameterType.TS_CODE,
                        true, null, List.of(), "[private", null)));
        h.definition = dataset(h.plugin.single.parameters(), false);

        code(ErrorCode.DATASET_MISCONFIGURED,
                () -> h.service().submit(single(SUBMISSION_ID, "000001.SZ")));
        assertThat(h.stored.get()).isNull();
    }

    @Test
    void replayPrecedesSettingsPluginCapacityAndStatusWhileConflictsRemainStable() {
        Harness h = new Harness();
        DownloadTask original = h.service().submit(single(SUBMISSION_ID, "000001.SZ")).task();
        h.plugin.available = false;
        h.queued = Long.MAX_VALUE;
        DownloadTask terminal = withStatus(original, DownloadTask.Status.FAILED);
        h.stored.set(terminal);
        assertThat(h.service(new DownloadTaskService.Settings(false, 1, 1))
                .submit(single(SUBMISSION_ID, " 000001.sz ")))
                .isEqualTo(new DownloadTaskService.SubmissionResult(terminal, false));
        code(ErrorCode.SUBMISSION_CONFLICT, () -> h.service().submit(single(SUBMISSION_ID, "000002.SZ")));
        code(ErrorCode.SUBMISSION_CONFLICT, () -> h.service().submit(new DownloadTaskService.Submission(
                SUBMISSION_ID, KEY, DownloadMode.RANGE,
                Map.of("symbol", "000001.SZ", "from", "20280228", "to", "20280301"))));
        for (DatasetKey different : List.of(
                DatasetKey.of(PluginId.of("other_source"), API_NAME),
                DatasetKey.of(PLUGIN_ID, ApiName.of("other_api")))) {
            code(ErrorCode.SUBMISSION_CONFLICT, () -> h.service().submit(new DownloadTaskService.Submission(
                    SUBMISSION_ID, different, DownloadMode.SINGLE, Map.of("symbol", "000001.SZ"))));
        }
    }

    @Test
    void rangeReplayUsesStoredPolicyAndRemovedSingleMetadataOnlyAllowsExactSnapshot() {
        Harness range = new Harness();
        DownloadTask rangeTask = range.service().submit(
                rangeSubmission(SUBMISSION_ID, "20280228", "20280301")).task();
        range.plugin.range = policy(BatchDownloadDescriptor.Availability.UNSUPPORTED, "Removed", "v2");
        assertThat(range.service(new DownloadTaskService.Settings(false, 1, 1)).submit(
                new DownloadTaskService.Submission(SUBMISSION_ID, KEY, DownloadMode.RANGE,
                        Map.of("symbol", " 000001.sz ", "from", "20280228", "to", "20280301"))))
                .isEqualTo(new DownloadTaskService.SubmissionResult(rangeTask, false));

        Harness single = new Harness();
        DownloadTask singleTask = single.service().submit(single(SUBMISSION_ID, "000001.SZ")).task();
        DownloadTaskService removed = single.service(new PluginRegistry(List.of()), single.catalog,
                new AdapterRegistry(List.of()), new DownloadTaskService.Settings(false, 1, 1));
        assertThat(removed.submit(new DownloadTaskService.Submission(
                SUBMISSION_ID, KEY, DownloadMode.SINGLE, singleTask.params())).task()).isEqualTo(singleTask);
        code(ErrorCode.SUBMISSION_CONFLICT, () -> removed.submit(single(SUBMISSION_ID, " 000001.sz ")));
    }

    @Test
    void duplicateInsertOnlyReReadsSubmissionIdAndOtherwiseMapsPersistenceFailure() {
        Harness equivalent = new Harness();
        DownloadTask winner = equivalent.synthetic(Map.of("symbol", "000001.SZ", "kind", "basic"),
                DownloadMode.SINGLE, equivalent.plugin.single, null);
        AtomicInteger reads = new AtomicInteger();
        when(equivalent.repository.findSubmission(SUBMISSION_ID))
                .thenAnswer(ignored -> reads.incrementAndGet() == 1 ? Optional.empty() : Optional.of(winner));
        doThrow(new DuplicateKeyException("private-db")).when(equivalent.repository).insert(any());
        assertThat(equivalent.service().submit(single(SUBMISSION_ID, " 000001.sz ")))
                .isEqualTo(new DownloadTaskService.SubmissionResult(winner, false));

        Harness otherUnique = new Harness();
        doThrow(new DuplicateKeyException("private-db")).when(otherUnique.repository).insert(any());
        code(ErrorCode.PERSISTENCE_FAILED,
                () -> otherUnique.service().submit(single(SUBMISSION_ID, "000001.SZ")));
    }

    @Test
    void validateReplayChecksDefinitionBeforeParametersAndIgnoresPresentationStatusAndRetryable() {
        Harness changed = new Harness();
        DownloadTask task = changed.service().submit(single(SUBMISSION_ID, "000001.SZ")).task();
        changed.service().validateReplay(task);
        changed.definition = dataset(List.of(STOCK, KIND), true);
        code(ErrorCode.TASK_DEFINITION_CHANGED, () -> changed.service().validateReplay(task));

        Harness renamed = new Harness();
        DownloadTask oldParameter = renamed.service().submit(single(SUBMISSION_ID, "000001.SZ")).task();
        renamed.plugin.single = new ApiDescriptor(API_NAME, "Daily", "market", QueryMode.snapshot,
                List.of(new ParameterDescriptor("ticker", "Ticker", null, ParameterType.TS_CODE,
                        true, null, List.of(), null, null), KIND));
        renamed.definition = dataset(renamed.plugin.single.parameters(), false);
        code(ErrorCode.TASK_DEFINITION_CHANGED, () -> renamed.service().validateReplay(oldParameter));

        Harness presentation = new Harness();
        DownloadTask stable = presentation.service().submit(single(SUBMISSION_ID, "000001.SZ")).task();
        presentation.plugin.single = new ApiDescriptor(API_NAME, "Renamed", "market", QueryMode.snapshot,
                presentation.plugin.single.parameters());
        presentation.service().validateReplay(withStatus(stable, DownloadTask.Status.FAILED));

        Harness range = new Harness();
        DownloadTask failedRange = withStatus(range.service().submit(
                rangeSubmission(SUBMISSION_ID, "20280228", "20280301")).task(), DownloadTask.Status.FAILED);
        range.plugin.sourceFailure = new DownloadTaskService.TaskException(ErrorCode.BATCH_DOWNLOAD_UNAVAILABLE);
        code(ErrorCode.BATCH_DOWNLOAD_UNAVAILABLE, () -> range.service().validateReplay(failedRange));
        assertThat(range.stored.get()).isNotNull();

        Harness policyVersion = new Harness();
        DownloadTask oldPolicy = policyVersion.service().submit(
                rangeSubmission(SUBMISSION_ID, "20280228", "20280301")).task();
        policyVersion.plugin.range = policy(BatchDownloadDescriptor.Availability.AVAILABLE, null, "v2");
        code(ErrorCode.TASK_DEFINITION_CHANGED, () -> policyVersion.service().validateReplay(oldPolicy));

        Harness businessKey = new Harness();
        DownloadTask oldKey = businessKey.service().submit(single(SUBMISSION_ID, "000001.SZ")).task();
        DatasetDefinition original = businessKey.definition;
        businessKey.definition = new DatasetDefinition(original.datasetKey(), original.displayName(),
                original.category(), original.queryMode(), original.parameters(), original.tableName(),
                original.columns(), new BusinessKeyDefinition(BusinessKeyMode.FINGERPRINT, List.of("value")),
                original.filters(), original.fixedColumn(), original.batchSize());
        code(ErrorCode.TASK_DEFINITION_CHANGED, () -> businessKey.service().validateReplay(oldKey));

        Harness unavailable = new Harness();
        DownloadTask unavailableTask = unavailable.service().submit(single(SUBMISSION_ID, "000001.SZ")).task();
        unavailable.plugin.available = false;
        code(ErrorCode.PLUGIN_DISABLED, () -> unavailable.service().validateReplay(unavailableTask));
    }

    @Test
    void publicOperationsRejectCallerTransactionsAndAdmissionLockReleasesAfterFailure() {
        Harness h = new Harness();
        DownloadTaskService service = h.service();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            for (org.assertj.core.api.ThrowableAssert.ThrowingCallable action
                    : List.<org.assertj.core.api.ThrowableAssert.ThrowingCallable>of(
                    () -> service.capabilities(KEY),
                    () -> service.retry(UUID.randomUUID(), 1),
                    () -> service.resume(UUID.randomUUID(), 1),
                    () -> service.controls(null),
                    () -> service.prepareSubmission(single(SUBMISSION_ID, "000001.SZ")),
                    () -> service.submit(single(SUBMISSION_ID, "000001.SZ")),
                    () -> service.validateReplay(h.synthetic(Map.of("symbol", "000001.SZ", "kind", "basic"),
                            DownloadMode.SINGLE, h.plugin.single, null)))) {
                assertThatThrownBy(action).isInstanceOf(IllegalStateException.class);
            }
        } finally {
            TransactionSynchronizationManager.clear();
        }
        h.insertFailure = new DownloadTaskService.TaskException(ErrorCode.PERSISTENCE_FAILED);
        code(ErrorCode.PERSISTENCE_FAILED, () -> service.submit(single(SUBMISSION_ID, "000001.SZ")));
        h.insertFailure = null;
        assertThat(service.submit(single(SUBMISSION_ID, "000001.SZ")).created()).isTrue();
    }

    @Test
    void constructorRejectsEveryNullDependency() {
        Harness h = new Harness();
        List<Object> values = new ArrayList<>(List.of(new PluginRegistry(List.of(h.plugin)), h.catalog, h.adapters(),
                new ParameterValidator(), h.repository, h.json, Clock.fixed(NOW, ZoneOffset.UTC), RUN_ID,
                DownloadTaskService.Settings.defaults()));
        for (int i = 0; i < values.size(); i++) {
            List<Object> args = new ArrayList<>(values);
            args.set(i, null);
            assertThatThrownBy(() -> new DownloadTaskService((PluginRegistry) args.get(0),
                    (DatasetCatalog) args.get(1), (AdapterRegistry) args.get(2), (ParameterValidator) args.get(3),
                    (DownloadTaskRepository) args.get(4), (DownloadTaskJson) args.get(5), (Clock) args.get(6),
                    (UUID) args.get(7), (DownloadTaskService.Settings) args.get(8)))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Test
    void ordinaryAndEmptyBatchPluginsStillAcceptSingleSubmissions() {
        Harness ordinary = new Harness();
        assertThat(ordinary.service(ordinary.plugin.asOrdinary(), ordinary.catalog, ordinary.adapters(),
                DownloadTaskService.Settings.defaults()).submit(single(SUBMISSION_ID, "000001.SZ")).created()).isTrue();
        Harness empty = new Harness();
        empty.plugin.range = null;
        assertThat(empty.service().submit(single(SUBMISSION_ID, "000001.SZ")).created()).isTrue();
    }

    @Test
    void executionDefinitionReusesValidatedObjectsAndRejectsChangedMeaningBeforeNormalization() {
        Harness h = new Harness();
        DownloadTaskService service = h.service();
        DownloadTask single = service.submit(single(SUBMISSION_ID, "000001.SZ")).task();
        var execution = service.executionDefinition(single);
        assertThat(execution.plugin()).isSameAs(h.plugin);
        assertThat(execution.adapter()).isSameAs(h.plugin);
        assertThat(execution.dataset()).isSameAs(h.definition);
        assertThat(execution.range()).isNull();
        DownloadTask range = h.synthetic(Map.of("symbol", "000001.SZ", "from", "20280228", "to", "20280301"),
                DownloadMode.RANGE, new ApiDescriptor(API_NAME, "Daily", "market", QueryMode.date_range,
                        h.plugin.range.parameters()), h.plugin.range);
        assertThat(service.executionDefinition(range).range()).isEqualTo(h.plugin.range);
        h.plugin.range = policy(BatchDownloadDescriptor.Availability.AVAILABLE, null, "v2");
        h.plugin.sourceFailure = new DownloadTaskService.TaskException(ErrorCode.PARAM_INVALID);
        code(ErrorCode.TASK_DEFINITION_CHANGED, () -> service.executionDefinition(range));
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertThatThrownBy(() -> service.executionDefinition(single)).isInstanceOf(IllegalStateException.class);
        } finally {
            TransactionSynchronizationManager.clear();
        }
    }

    @Test
    void controlValidationClassifiesInvalidMissingVersionAndStateBeforeCoordinator() {
        Harness h = new Harness();
        DownloadTaskService service = h.service();
        DownloadTask queued = service.submit(single(SUBMISSION_ID, "000001.SZ")).task();
        for (boolean retry : List.of(true, false)) {
            code(ErrorCode.PARAM_INVALID, () -> control(service, retry, null, 1));
            code(ErrorCode.PARAM_INVALID, () -> control(service, retry, queued.taskId(), 0));
            code(ErrorCode.TASK_NOT_FOUND, () -> control(service, retry, UUID.randomUUID(), 1));
            code(ErrorCode.TASK_STATE_CONFLICT, () -> control(service, retry, queued.taskId(), 1));
            h.stored.set(withStatus(queued, retry ? DownloadTask.Status.FAILED : DownloadTask.Status.INTERRUPTED));
            code(ErrorCode.TASK_STATE_CONFLICT, () -> control(service, retry, queued.taskId(), 2));
            code(ErrorCode.TASK_STATE_CONFLICT, () -> control(service, retry, queued.taskId(), 1));
            h.stored.set(queued);
        }
        code(ErrorCode.PARAM_INVALID, () -> service.controls(null));
        assertThat(service.controls(withStatus(queued, DownloadTask.Status.FAILED)))
                .isEqualTo(new DownloadTaskService.ControlAvailability(false, false));
    }

    @Test
    void controlsArePureHintsAndReadinessFailuresAreSanitizedForBothOperations() {
        for (boolean retry : List.of(true, false)) {
            Harness h = new Harness();
            DownloadTaskService service = h.service();
            DownloadTask accepted = service.submit(single(SUBMISSION_ID, "000001.SZ")).task();
            DownloadTask terminal = withStatus(accepted,
                    retry ? DownloadTask.Status.FAILED : DownloadTask.Status.INTERRUPTED);
            h.stored.set(terminal);
            DownloadTaskCoordinator coordinator = mock(DownloadTaskCoordinator.class);
            when(coordinator.controlAllowed()).thenReturn(true);
            service.bindCoordinator(coordinator);
            assertThatThrownBy(() -> service.bindCoordinator(mock(DownloadTaskCoordinator.class)))
                    .isInstanceOf(IllegalStateException.class);
            h.plugin.readinessFailure = new IllegalStateException("private-readiness");
            org.mockito.Mockito.clearInvocations(h.repository);
            assertThat(service.controls(terminal))
                    .isEqualTo(new DownloadTaskService.ControlAvailability(retry, !retry));
            org.mockito.Mockito.verifyNoInteractions(h.repository);
            code(ErrorCode.DATASET_MISCONFIGURED,
                    () -> control(service, retry, terminal.taskId(), terminal.version()));
            h.plugin.readinessFailure = null;
            h.plugin.nullReadiness = true;
            code(ErrorCode.DATASET_MISCONFIGURED,
                    () -> control(service, retry, terminal.taskId(), terminal.version()));
            h.plugin.nullReadiness = false;
            h.plugin.available = false;
            code(ErrorCode.PLUGIN_DISABLED,
                    () -> control(service, retry, terminal.taskId(), terminal.version()));
            h.plugin.available = true;
            DownloadTaskService disabled = h.service(new DownloadTaskService.Settings(false, 100, 36600));
            disabled.bindCoordinator(coordinator);
            code(ErrorCode.PLUGIN_DISABLED,
                    () -> control(disabled, retry, terminal.taskId(), terminal.version()));
            assertThat(disabled.controls(terminal))
                    .isEqualTo(new DownloadTaskService.ControlAvailability(false, false));
            assertThat(h.stored.get()).isEqualTo(terminal);
            assertThat(h.plugin.executions.get()).isZero();
            when(coordinator.controlAllowed()).thenReturn(false);
            assertThat(service.controls(terminal))
                    .isEqualTo(new DownloadTaskService.ControlAvailability(false, false));
        }
    }

    private static DownloadTask control(DownloadTaskService service, boolean retry, UUID id, long version) {
        return retry ? service.retry(id, version) : service.resume(id, version);
    }

    private static DownloadTaskService.Submission single(UUID id, String symbol) {
        return new DownloadTaskService.Submission(id, KEY, DownloadMode.SINGLE, Map.of("symbol", symbol));
    }

    private static DownloadTaskService.Submission rangeSubmission(UUID id, String from, String to) {
        return new DownloadTaskService.Submission(id, KEY, DownloadMode.RANGE,
                Map.of("symbol", "000001.SZ", "from", from, "to", to));
    }

    private static void code(ErrorCode expected, org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action).isInstanceOfSatisfying(TensorException.class, exception -> {
            assertThat(exception.code()).isEqualTo(expected);
            assertThat(exception).hasNoCause().hasMessageNotContaining("private");
        });
    }

    private static BatchDownloadDescriptor policy(
            BatchDownloadDescriptor.Availability availability, String reason, String version) {
        if (availability == BatchDownloadDescriptor.Availability.UNSUPPORTED)
            return new BatchDownloadDescriptor(List.of(), null, null, null, null, null, false, availability, reason,
                    version, new BatchDownloadDescriptor.CompletenessRule(
                            BatchDownloadDescriptor.CompletenessRule.Kind.UNKNOWN, null, null));
        return new BatchDownloadDescriptor(List.of(STOCK, endpoint("from", "to"), endpoint("to", "from")),
                "from", "to", BatchDownloadDescriptor.DateAxis.TRADE_DATE, "Trade date",
                BatchDownloadDescriptor.PlanningMode.NATIVE_RANGE, true, availability, reason, version,
                new BatchDownloadDescriptor.CompletenessRule(
                        BatchDownloadDescriptor.CompletenessRule.Kind.VERIFIED_RULE, null, "verified"));
    }

    private static BatchDownloadDescriptor standardPolicy(String prefix) {
        List<ParameterDescriptor> parameters = new ArrayList<>();
        if (prefix != null) {
            parameters.add(prefix.equals("ts_code")
                    ? new ParameterDescriptor(prefix, prefix, null, ParameterType.TS_CODE,
                            false, null, List.of(), null, null)
                    : new ParameterDescriptor(prefix, prefix, null, ParameterType.ENUM,
                            false, null, List.of("SSE", "SZSE", "BSE"), null, null));
        }
        parameters.add(endpoint("start_date", "end_date"));
        parameters.add(endpoint("end_date", "start_date"));
        return new BatchDownloadDescriptor(parameters, "start_date", "end_date",
                BatchDownloadDescriptor.DateAxis.TRADE_DATE, "Trade date",
                BatchDownloadDescriptor.PlanningMode.NATIVE_RANGE, true,
                BatchDownloadDescriptor.Availability.AVAILABLE, null, "standard-v1",
                new BatchDownloadDescriptor.CompletenessRule(
                        BatchDownloadDescriptor.CompletenessRule.Kind.VERIFIED_RULE, null, "verified"));
    }

    private static ParameterDescriptor endpoint(String name, String related) {
        return new ParameterDescriptor(name, name, null, ParameterType.DATE_RANGE_MEMBER,
                true, null, List.of(), null, related);
    }

    private static DatasetDefinition dataset(List<ParameterDescriptor> parameters, boolean nullable) {
        return new DatasetDefinition(KEY, "Daily", "market", QueryMode.snapshot, parameters, TableName.from(KEY),
                List.of(new ColumnDefinition("value", "Value", LogicalType.STRING, nullable,
                        0, 64, null, null, List.of(), false)),
                new BusinessKeyDefinition(BusinessKeyMode.COMPOSITE, List.of("value")), List.of(), null, 500);
    }

    private static DownloadTask withStatus(DownloadTask task, DownloadTask.Status status) {
        return new DownloadTask(task.taskId(), task.submissionId(), task.requestHash(), task.datasetKey(), task.mode(),
                task.params(), task.definitionHash(), task.policySnapshot(), status, task.planReady(), task.activeRunId(),
                task.runGeneration(), task.version(), task.requestCount(), task.runRequestCount(), task.lastError(),
                task.createdAt(), task.updatedAt(), task.queuedAt(), task.startedAt(), task.finishedAt(), task.deadlineAt());
    }

    private static final class Harness {
        final DownloadTaskJson json = new DownloadTaskJson();
        final DownloadTaskRepository repository = mock(DownloadTaskRepository.class);
        final AtomicReference<DownloadTask> stored = new AtomicReference<>();
        final TestPlugin plugin = new TestPlugin();
        final DatasetCatalog catalog = mock(DatasetCatalog.class);
        DatasetDefinition definition = dataset(List.of(STOCK, KIND), false);
        long queued;
        RuntimeException insertFailure;

        Harness() {
            when(catalog.find(KEY)).thenAnswer(ignored -> Optional.ofNullable(definition));
            when(repository.findSubmission(any())).thenAnswer(invocation -> {
                DownloadTask task = stored.get();
                return task != null && task.submissionId().equals(invocation.getArgument(0))
                        ? Optional.of(task) : Optional.empty();
            });
            when(repository.queuedCount()).thenAnswer(ignored -> queued);
            when(repository.findTask(any())).thenAnswer(invocation -> {
                DownloadTask task = stored.get();
                return task != null && task.taskId().equals(invocation.getArgument(0))
                        ? Optional.of(task) : Optional.empty();
            });
            when(repository.insert(any())).thenAnswer(invocation -> {
                if (insertFailure != null) throw insertFailure;
                DownloadTask task = task(invocation.getArgument(0));
                stored.set(task);
                queued++;
                return task;
            });
        }

        DownloadTaskService service() { return service(DownloadTaskService.Settings.defaults()); }
        DownloadTaskService service(DownloadTaskService.Settings settings) {
            return service(plugin, catalog, adapters(), settings);
        }
        DownloadTaskService service(DataSourcePlugin source, DatasetCatalog definitions,
                AdapterRegistry adapterRegistry, DownloadTaskService.Settings settings) {
            return service(new PluginRegistry(List.of(source)), definitions, adapterRegistry, settings);
        }
        DownloadTaskService service(PluginRegistry registry, DatasetCatalog definitions,
                AdapterRegistry adapterRegistry, DownloadTaskService.Settings settings) {
            return new DownloadTaskService(registry, definitions, adapterRegistry, new ParameterValidator(), repository,
                    json, Clock.fixed(NOW, ZoneOffset.UTC), RUN_ID, settings);
        }
        AdapterRegistry adapters() { return new AdapterRegistry(List.of(plugin)); }
        DownloadTask synthetic(Map<String, Object> normalized, DownloadMode mode,
                ApiDescriptor api, BatchDownloadDescriptor range) {
            return task(new DownloadTaskRepository.NewTask(UUID.randomUUID(), SUBMISSION_ID, KEY, mode, normalized,
                    json.definitionHash(api, definition, mode, range), json.policySnapshot(mode, range), RUN_ID, NOW));
        }
        private DownloadTask task(DownloadTaskRepository.NewTask input) {
            return new DownloadTask(input.taskId(), input.submissionId(),
                    json.requestHash(input.datasetKey(), input.mode(), input.normalizedParams()), input.datasetKey(),
                    input.mode(), input.normalizedParams(), input.definitionHash(), input.policySnapshot(),
                    DownloadTask.Status.QUEUED, false, input.activeRunId(), 0, 1, 0, 0, null,
                    input.now(), input.now(), input.now(), null, null, null);
        }
    }

    private static final class TestPlugin implements BatchDownloadSupport, DatasetAdapter {
        ApiDescriptor single = new ApiDescriptor(API_NAME, "Daily", "market", QueryMode.snapshot,
                List.of(STOCK, KIND));
        BatchDownloadDescriptor range = policy(BatchDownloadDescriptor.Availability.AVAILABLE, null, "v1");
        boolean available = true;
        boolean nullDescriptor;
        boolean nullReadiness;
        RuntimeException readinessFailure;
        RuntimeException descriptorFailure;
        RuntimeException sourceFailure;
        Map<String, Object> sourceResult;
        DateRange lastRange;
        final AtomicInteger combinations = new AtomicInteger();
        final AtomicInteger executions = new AtomicInteger();

        @Override public PluginDescriptor descriptor() {
            return new PluginDescriptor(PLUGIN_ID, "Task test", "Task test", available, available, available,
                    available ? null : "disabled", List.of(single), List.of(KEY));
        }
        @Override public PluginReadiness readiness() {
            if (readinessFailure != null) throw readinessFailure;
            if (nullReadiness) return null;
            return new PluginReadiness(available, available, available, available ? null : "disabled");
        }
        @Override public Optional<BatchDownloadDescriptor> batchDescriptor(ApiName apiName) {
            if (descriptorFailure != null) throw descriptorFailure;
            return nullDescriptor ? null : Optional.ofNullable(range);
        }
        @Override public Map<String, Object> sourceParameters(ApiName apiName, Map<String, Object> params, DateRange range) {
            combinations.incrementAndGet();
            if (sourceFailure != null) throw sourceFailure;
            lastRange = range;
            return sourceResult == null ? params : sourceResult;
        }
        @Override public DatasetKey datasetKey() { return KEY; }
        @Override public DatasetDefinition definition() { return dataset(single.parameters(), false); }
        @Override public DownloadEnvelope download(ApiName apiName, Map<String, Object> params) { throw executed(); }
        @Override public List<DateRange> plan(ApiName apiName, Map<String, Object> params, BatchCallContext context) {
            throw executed();
        }
        @Override public DownloadEnvelope downloadBatch(ApiName apiName, Map<String, Object> params,
                BatchCallContext context) { throw executed(); }
        @Override public BatchAssessment assess(ApiName apiName, DateRange range, DownloadEnvelope envelope) {
            throw executed();
        }
        @Override public AdaptedBatch adapt(DownloadEnvelope envelope, Instant ingestedAt) { throw executed(); }
        DataSourcePlugin asOrdinary() {
            TestPlugin self = this;
            return new DataSourcePlugin() {
                @Override public PluginDescriptor descriptor() { return self.descriptor(); }
                @Override public PluginReadiness readiness() { return self.readiness(); }
                @Override public DownloadEnvelope download(ApiName api, Map<String, Object> params) {
                    return self.download(api, params);
                }
            };
        }
        private AssertionError executed() {
            executions.incrementAndGet();
            return new AssertionError("upstream/adaptation must not run");
        }
    }
}
