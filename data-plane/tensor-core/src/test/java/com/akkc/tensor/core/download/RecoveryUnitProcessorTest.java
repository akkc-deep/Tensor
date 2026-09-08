package com.akkc.tensor.core.download;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import com.akkc.tensor.core.adapter.BusinessContentCodec;
import com.akkc.tensor.core.adapter.FingerprintKeyCodec;
import com.akkc.tensor.core.adapter.GenericDatasetAdapter;
import com.akkc.tensor.core.adapter.ValueConverter;
import com.akkc.tensor.core.persistence.BusinessKeyExtractor;
import com.akkc.tensor.core.validation.ParameterValidator;
import com.akkc.tensor.core.validation.ValidatedParameters;
import com.akkc.tensor.plugin.api.dataset.BusinessKeyDefinition;
import com.akkc.tensor.plugin.api.dataset.BusinessKeyMode;
import com.akkc.tensor.plugin.api.dataset.ColumnDefinition;
import com.akkc.tensor.plugin.api.dataset.DatasetDefinition;
import com.akkc.tensor.plugin.api.dataset.LogicalType;
import com.akkc.tensor.plugin.api.descriptor.ApiDescriptor;
import com.akkc.tensor.plugin.api.descriptor.ParameterDescriptor;
import com.akkc.tensor.plugin.api.descriptor.ParameterType;
import com.akkc.tensor.plugin.api.descriptor.QueryMode;
import com.akkc.tensor.plugin.api.download.DownloadContext;
import com.akkc.tensor.plugin.api.download.DownloadEnvelope;
import com.akkc.tensor.plugin.api.download.DownloadParameterProjection;
import com.akkc.tensor.plugin.api.download.DownloadPolicy;
import com.akkc.tensor.plugin.api.download.DownloadStatus;
import com.akkc.tensor.plugin.api.download.FetchBatch;
import com.akkc.tensor.plugin.api.download.FetchResult;
import com.akkc.tensor.plugin.api.download.RecoveryPolicy;
import com.akkc.tensor.plugin.api.download.RecoverySelector;
import com.akkc.tensor.plugin.api.error.ErrorCode;
import com.akkc.tensor.plugin.api.error.SourceException;
import com.akkc.tensor.plugin.api.error.TensorException;
import com.akkc.tensor.plugin.api.model.ApiName;
import com.akkc.tensor.plugin.api.model.DatasetKey;
import com.akkc.tensor.plugin.api.model.PluginId;
import com.akkc.tensor.plugin.api.model.TableName;
import java.lang.reflect.Modifier;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class RecoveryUnitProcessorTest {
    private static final PluginId PLUGIN = new PluginId("fixture");
    private static final ApiName API_NAME = new ApiName("controlled");
    private static final DatasetKey DATASET = new DatasetKey(PLUGIN, API_NAME);
    private static final Instant INGESTED_AT = Instant.parse("2026-09-08T01:02:03Z");
    private static final DownloadContext CONTEXT = () -> {};

    @Test
    void splitsDateSourceForRangeRecoveryAndIsolatesTheBadBAmount() {
        Fixture fixture = dateSourceRangeRecoveryFixture();
        RecoveryUnitProcessor.PreparedBatch prepared = fixture.session().accept(fixture.result(List.of(
                row("a-key", "000001.SZ", "20260903", "1.20"),
                row("b-key", "600000.SH", "20260903", "bad"),
                row("c-key", "000002.SZ", "20260903", "3.40"))));

        assertThat(prepared.failures()).isEmpty();
        assertThat(prepared.units()).extracting(RecoveryUnitProcessor.UnitInput::selector)
                .containsExactly(
                        new RecoverySelector(RecoverySelector.TargetType.STOCK, "000001.SZ",
                                RecoverySelector.TimeType.DATE, "2026-09-03"),
                        new RecoverySelector(RecoverySelector.TargetType.STOCK, "000002.SZ",
                                RecoverySelector.TimeType.DATE, "2026-09-03"),
                        new RecoverySelector(RecoverySelector.TargetType.STOCK, "600000.SH",
                                RecoverySelector.TimeType.DATE, "2026-09-03"));
        Map<String, RecoveryUnitProcessor.Validation> validations = prepared.units().stream().collect(
                java.util.stream.Collectors.toMap(unit -> unit.selector().targetValue(),
                        unit -> fixture.processor().validate(unit, fixture.index(), INGESTED_AT)));
        assertThat(validations.get("000001.SZ")).isInstanceOf(RecoveryUnitProcessor.ReadyUnit.class);
        assertThat(validations.get("000002.SZ")).isInstanceOf(RecoveryUnitProcessor.ReadyUnit.class);
        assertThat(validations.get("600000.SH"))
                .isInstanceOfSatisfying(RecoveryUnitProcessor.RejectedUnit.class,
                        rejected -> assertThat(rejected.failure().errorCode())
                                .isEqualTo(ErrorCode.ADAPTER_TYPE_INVALID));
    }

    @Test
    void retainsTheWholeRequestWhenSourceTimeShapeCannotReconstructDateRecoveryUnits() {
        Fixture fixture = monthSourceDateRecoveryFixture();

        RecoveryUnitProcessor.PreparedBatch prepared = fixture.session().accept(fixture.result(List.of(
                row("a-key", "000001.SZ", "20260903", "1.20"))));

        assertThat(prepared.failures()).isEmpty();
        assertThat(prepared.units()).singleElement()
                .extracting(RecoveryUnitProcessor.UnitInput::selector).isEqualTo(fixture.scope());
    }

    @Test
    void rejectsRowsWhoseStockSelectorCannotBeRebuiltWithFrozenSourceRestrictions() {
        Fixture fixture = patternRestrictedFixture();

        RecoveryUnitProcessor.PreparedBatch prepared = fixture.session().accept(fixture.result(List.of(
                row("a-key", "000001.SZ", "20260903", "1.20"))));

        assertGlobal(prepared, fixture.scope(), ErrorCode.SOURCE_PAYLOAD_INVALID);
    }

    @Test
    void defersWholeRequestFailuresUntilAllRowsKeysAndKnownMembersPassTheGlobalBarrier() {
        List<List<List<Object>>> invalidPayloads = List.of(
                List.of(row("bad-target", "lower.sz", "20260903", "1.20")),
                List.of(row("bad-date", "000001.SZ", "20260911", "1.20")),
                List.of(
                        row("same-key", "000001.SZ", "20260903", "1.20"),
                        row("same-key", "600000.SH", "20260903", "1.20")));
        for (List<List<Object>> rows : invalidPayloads) {
            Fixture fixture = fixture(true);
            RecoveryUnitProcessor.PreparedBatch prepared = fixture.session().accept(
                    fixture.result(rows, List.of(wholeFailure(fixture))));

            assertGlobal(prepared, fixture.scope(), ErrorCode.SOURCE_PAYLOAD_INVALID);
            verify(fixture.adapter(), never()).adaptRows(
                    org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        }

        Fixture external = fixture(true);
        List<RecoverySelector> known = List.of(stock("000001.SZ"), stock("600000.SH"));
        RecoveryUnitProcessor.PreparedBatch outside = external.knownSession(external.known(known)).accept(
                external.result(List.of(row("outside", "000002.SZ", "20260903", "1.20")),
                        List.of(wholeFailure(external))));
        assertGlobal(outside, external.scope(), ErrorCode.SOURCE_PAYLOAD_INVALID);
        verify(external.adapter(), never()).adaptRows(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());

        Fixture covered = fixture(true);
        RecoveryUnitProcessor.PreparedBatch sourceFailure = covered.knownSession(covered.known(known)).accept(
                covered.result(List.of(row("a-key", "000001.SZ", "20260903", "1.20")),
                        List.of(wholeFailure(covered))));
        assertGlobal(sourceFailure, covered.scope(), ErrorCode.SOURCE_TIMEOUT);
        verify(covered.adapter(), never()).adaptRows(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsNullKnownMemberAndEvidenceElementsWithTheSafeSourceConditionError() {
        Fixture fixture = fixture(false);
        List<RecoverySelector> selectors = new ArrayList<>();
        selectors.add(null);
        List<String> evidence = new ArrayList<>();
        evidence.add(null);

        assertCode(() -> new RecoveryUnitProcessor.KnownMembers(
                DATASET, fixture.batch(), selectors, List.of("controlled")),
                ErrorCode.SOURCE_REQUEST_UNCONFIRMED);
        assertCode(() -> new RecoveryUnitProcessor.KnownMembers(
                DATASET, fixture.batch(), List.of(stock("000001.SZ")), evidence),
                ErrorCode.SOURCE_REQUEST_UNCONFIRMED);
        assertCode(() -> new RecoveryUnitProcessor.KnownMembers(
                null, fixture.batch(), List.of(stock("000001.SZ")), List.of("controlled")),
                ErrorCode.SOURCE_REQUEST_UNCONFIRMED);
        assertCode(() -> new RecoveryUnitProcessor.KnownMembers(
                DATASET, null, List.of(stock("000001.SZ")), List.of("controlled")),
                ErrorCode.SOURCE_REQUEST_UNCONFIRMED);
        assertCode(() -> new RecoveryUnitProcessor.KnownMembers(
                DATASET, fixture.batch(), null, List.of("controlled")),
                ErrorCode.SOURCE_REQUEST_UNCONFIRMED);
        assertCode(() -> new RecoveryUnitProcessor.KnownMembers(
                DATASET, fixture.batch(), List.of(stock("000001.SZ")), null),
                ErrorCode.SOURCE_REQUEST_UNCONFIRMED);
    }

    @Test
    void strictCompleteSourceDriverNeverPassesBufferedRowsAfterALaterPageFailure() {
        for (ErrorCode code : List.of(ErrorCode.SOURCE_TIMEOUT, ErrorCode.SOURCE_TRUNCATED)) {
            Fixture fixture = fixture(true);
            StrictSourceDriver source = new StrictSourceDriver(fixture);

            RecoveryUnitProcessor.PreparedBatch prepared = source.collectThenFail(
                    List.of(row("buffered-a", "000001.SZ", "20260903", "1.20")),
                    new SourceException(code, "later-page-secret"));

            assertThat(source.buffered).hasSize(1);
            assertGlobal(prepared, fixture.scope(), code);
            verify(fixture.adapter(), never()).adaptRows(
                    org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        }
    }

    @Test
    void frozenRequestUsesStableFirstCompatibleLocalFailureWithoutPublishingRows() {
        Fixture fixture = fixture(false, false, false);
        FetchResult.UnitFailure later = new FetchResult.UnitFailure(
                stock("600000.SH"), ErrorCode.SOURCE_TIMEOUT, "later-secret");
        FetchResult.UnitFailure first = new FetchResult.UnitFailure(
                stock("000001.SZ"), ErrorCode.SOURCE_RATE_LIMITED, "first-secret");

        RecoveryUnitProcessor.PreparedBatch prepared = fixture.session().accept(fixture.result(
                List.of(row("unpublished", "000002.SZ", "20260903", "1.20")),
                List.of(later, first)));

        assertGlobal(prepared, fixture.scope(), ErrorCode.SOURCE_RATE_LIMITED);
        assertThat(prepared.failures().getFirst().errorMessage()).doesNotContain("secret", "unpublished");
    }

    @Test
    void rejectsOverlappingSubRangeFailurePairsAsOnePayloadError() {
        Fixture fixture = fixture(false);
        FetchResult.UnitFailure first = new FetchResult.UnitFailure(
                new RecoverySelector(RecoverySelector.TargetType.STOCK, "000001.SZ",
                        RecoverySelector.TimeType.RANGE, "2026-09-01/2026-09-05"),
                ErrorCode.SOURCE_TIMEOUT, "safe");
        FetchResult.UnitFailure second = new FetchResult.UnitFailure(
                new RecoverySelector(RecoverySelector.TargetType.STOCK, "000001.SZ",
                        RecoverySelector.TimeType.RANGE, "2026-09-04/2026-09-10"),
                ErrorCode.SOURCE_UNAVAILABLE, "safe");

        assertGlobal(fixture.session().accept(fixture.result(List.of(), List.of(first, second))),
                fixture.scope(), ErrorCode.SOURCE_PAYLOAD_INVALID);
    }

    @Test
    void knownMembersAcceptFullNonemptyCoverageWhenTheMissingMemberHasAnExplicitFailure() {
        Fixture fixture = fixture(false);
        List<RecoverySelector> known = List.of(
                stock("000001.SZ"), stock("600000.SH"), stock("000002.SZ"));
        FetchResult.UnitFailure missing = new FetchResult.UnitFailure(
                stock("600000.SH"), ErrorCode.SOURCE_TIMEOUT, "member-secret");

        RecoveryUnitProcessor.PreparedBatch prepared = fixture.knownSession(fixture.known(known)).accept(
                fixture.result(List.of(
                        row("a-key", "000001.SZ", "20260903", "1.20"),
                        row("c-key", "000002.SZ", "20260903", "3.40")), List.of(missing)));

        assertThat(prepared.units()).extracting(unit -> unit.selector().targetValue())
                .containsExactly("000001.SZ", "000002.SZ");
        assertThat(prepared.failures()).singleElement().satisfies(failure -> {
            assertThat(failure.selector()).isEqualTo(stock("600000.SH"));
            assertThat(failure.errorCode()).isEqualTo(ErrorCode.SOURCE_TIMEOUT);
            assertThat(failure.errorMessage()).doesNotContain("secret");
        });
    }

    @Test
    void rejectsKnownMemberEvidenceAndBindingMismatchesBeforeSourceUse() {
        Fixture fixture = fixture(false);
        List<RecoverySelector> selectors = List.of(stock("000001.SZ"));
        for (List<String> invalidEvidence : List.of(
                List.of(" "), List.of("line\nfeed"), List.of("same", "same"))) {
            assertCode(() -> new RecoveryUnitProcessor.KnownMembers(
                    DATASET, fixture.batch(), selectors, invalidEvidence),
                    ErrorCode.SOURCE_REQUEST_UNCONFIRMED);
        }
        RecoveryUnitProcessor.KnownMembers wrongDataset = new RecoveryUnitProcessor.KnownMembers(
                new DatasetKey(new PluginId("other"), API_NAME), fixture.batch(), selectors,
                List.of("controlled"));
        assertCode(() -> fixture.knownSession(wrongDataset),
                ErrorCode.SOURCE_REQUEST_UNCONFIRMED);
    }

    @Test
    void processorRejectsReorderedFieldsAndEnvelopeConstructorsRejectBrokenStructure() {
        Fixture fixture = fixture(false);
        List<String> reversed = fixture.adapter().definition().columns().stream()
                .map(ColumnDefinition::name).toList().reversed();
        DownloadEnvelope reordered = new DownloadEnvelope(
                PLUGIN, API_NAME, fixture.batch().sourceParams(), reversed, 1,
                List.of(row("1.20", "20260903", "000001.SZ", "a-key")),
                DownloadStatus.SUCCESS, null);
        assertGlobal(fixture.session().accept(new FetchResult(reordered, List.of())),
                fixture.scope(), ErrorCode.SOURCE_PAYLOAD_INVALID);

        List<String> fields = fixture.adapter().definition().columns().stream()
                .map(ColumnDefinition::name).toList();
        assertThatThrownBy(() -> new DownloadEnvelope(
                PLUGIN, API_NAME, fixture.batch().sourceParams(), fields, 2,
                List.of(row("a", "000001.SZ", "20260903", "1.20")),
                DownloadStatus.SUCCESS, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DownloadEnvelope(
                PLUGIN, API_NAME, fixture.batch().sourceParams(), fields, 1,
                List.of(List.of("too", "short")), DownloadStatus.SUCCESS, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DownloadEnvelope(
                PLUGIN, API_NAME, fixture.batch().sourceParams(),
                List.of("record_id", "record_id"), 0, List.of(), DownloadStatus.SUCCESS, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void validatesNullableFingerprintKeysAndRejectsInvalidSharedAttributionKeysGlobally() {
        Fixture fingerprint = nullableFingerprintFixture();
        RecoveryUnitProcessor.PreparedBatch prepared = fingerprint.session().accept(fingerprint.result(
                List.of(Arrays.asList(null, "000001.SZ", "20260903", "1.20"))));
        assertThat(fingerprint.processor().validate(
                prepared.units().getFirst(), fingerprint.index(), INGESTED_AT))
                .isInstanceOf(RecoveryUnitProcessor.ReadyUnit.class);

        Fixture shared = sharedAttributionKeyFixture();
        assertGlobal(shared.session().accept(shared.result(List.of(
                        row("ignored", "000001.SZ", "20260903", "1.20")))),
                shared.scope(), ErrorCode.SOURCE_PAYLOAD_INVALID);
    }

    @Test
    void supportsOriginalRequestNoneAndEqualEndpointNativeDateWithoutChangingScope() {
        Fixture original = originalParamsFixture();
        RecoveryUnitProcessor.PreparedBatch originalPrepared = original.session().accept(
                original.result(List.of(List.of("a-key", "memo"))));
        assertThat(originalPrepared.units()).singleElement()
                .extracting(RecoveryUnitProcessor.UnitInput::selector).isEqualTo(original.scope());

        Fixture sameDate = equalEndpointFixture();
        assertThat(sameDate.batch().sourceParams())
                .containsEntry("start_date", "20260903")
                .containsEntry("end_date", "20260903");
        RecoveryUnitProcessor.PreparedBatch datePrepared = sameDate.session().accept(sameDate.result(List.of(
                row("a-key", "000001.SZ", "20260903", "1.20"))));
        assertThat(datePrepared.units()).singleElement()
                .extracting(RecoveryUnitProcessor.UnitInput::selector)
                .isEqualTo(new RecoverySelector(RecoverySelector.TargetType.STOCK, "000001.SZ",
                        RecoverySelector.TimeType.DATE, "2026-09-03"));
    }

    @Test
    void preservesFrozenScalarAndDateParametersAndKeepsRequestWhenTsCodeIsUndeclared() {
        Fixture scalars = scalarParameterFixture();
        assertThat(scalars.batch().sourceParams())
                .containsEntry("exchange", "SSE")
                .containsEntry("exchange_id", "XSHG")
                .containsEntry("start_date", "20260901")
                .containsEntry("end_date", "20260910");
        assertThat(scalars.session().accept(scalars.result(List.of(
                row("a-key", "000001.SZ", "20260903", "1.20")))).units())
                .singleElement().extracting(RecoveryUnitProcessor.UnitInput::selector)
                .isEqualTo(stock("000001.SZ"));

        Fixture noStockParameter = missingTsCodeParameterFixture();
        assertThat(noStockParameter.session().accept(noStockParameter.result(List.of(
                row("a-key", "000001.SZ", "20260903", "1.20")))).units())
                .singleElement().extracting(RecoveryUnitProcessor.UnitInput::selector)
                .isEqualTo(noStockParameter.scope());
    }

    @Test
    void rejectsWrongPolicyAndDescriptorConfigurationAtOpen() {
        Fixture fixture = fixture(false);
        FetchBatch wrongPolicy = new FetchBatch(
                fixture.batch().sourceParams(), com.akkc.tensor.test.DownloadPolicies.original().recoveryPolicy());
        assertCode(() -> fixture.processor().openInitial(
                fixture.api(), fixture.original(), fixture.scope(), wrongPolicy, CONTEXT),
                ErrorCode.SOURCE_REQUEST_UNCONFIRMED);

        ApiDescriptor wrongQuery = new ApiDescriptor(
                fixture.api().apiName(), fixture.api().displayName(), fixture.api().category(),
                QueryMode.date_range, fixture.api().parameters(), fixture.api().downloadPolicy(),
                fixture.api().sourceParameters());
        assertCode(() -> fixture.processor().openInitial(
                wrongQuery, fixture.original(), fixture.scope(), fixture.batch(), CONTEXT),
                ErrorCode.DATASET_MISCONFIGURED);
    }

    @Test
    void isolatesRequiredNonKeyMissingValuesAndDoesNotExposeRowOrSourceSentinels() {
        Fixture fixture = requiredAmountFixture();
        RecoveryUnitProcessor.PreparedBatch prepared = fixture.session().accept(fixture.result(List.of(
                row("a-key", "000001.SZ", "20260903", "1.20"),
                Arrays.asList("b-key", "600000.SH", "20260903", null))));
        Map<String, RecoveryUnitProcessor.Validation> validations = prepared.units().stream().collect(
                java.util.stream.Collectors.toMap(unit -> unit.selector().targetValue(),
                        unit -> fixture.processor().validate(unit, fixture.index(), INGESTED_AT)));
        assertThat(validations.get("000001.SZ")).isInstanceOf(RecoveryUnitProcessor.ReadyUnit.class);
        assertThat(validations.get("600000.SH"))
                .isInstanceOfSatisfying(RecoveryUnitProcessor.RejectedUnit.class, rejected -> {
                    assertThat(rejected.failure().errorCode()).isEqualTo(ErrorCode.ADAPTER_FIELD_MISSING);
                    assertThat(rejected.failure().errorMessage()).doesNotContain("b-key", "600000.SH");
                });

        Fixture unsafe = fixture(true);
        RecoveryUnitProcessor.UnitInput unit = unsafe.session().accept(unsafe.result(List.of(
                row("row-sentinel", "000001.SZ", "20260903", "bad-secret")))).units().getFirst();
        RecoveryUnitProcessor.RejectedUnit rejected = (RecoveryUnitProcessor.RejectedUnit)
                unsafe.processor().validate(unit, unsafe.index(), INGESTED_AT);
        assertThat(rejected.failure().errorMessage()).doesNotContain("row-sentinel", "bad-secret");
    }

    @Test
    void propagatesUnexpectedLocalFailuresByIdentityAndClosesSuccessfulSessionsAfterConfirmation() {
        Fixture unexpectedFixture = fixture(true);
        RecoveryUnitProcessor.UnitInput unexpectedUnit = unexpectedFixture.session().accept(
                unexpectedFixture.result(List.of(
                        row("a-key", "000001.SZ", "20260903", "1.20")))).units().getFirst();
        RuntimeException unexpected = new RuntimeException("exact-unexpected");
        org.mockito.Mockito.doThrow(unexpected).when(unexpectedFixture.adapter()).adaptRows(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        assertThatThrownBy(() -> unexpectedFixture.processor().validate(
                unexpectedUnit, unexpectedFixture.index(), INGESTED_AT)).isSameAs(unexpected);

        Fixture complete = fixture(false);
        RecoveryUnitProcessor.BatchSession session = complete.session();
        RecoveryUnitProcessor.UnitInput unit = session.accept(complete.result(List.of(
                row("committed", "000001.SZ", "20260903", "1.20")))).units().getFirst();
        RecoveryUnitProcessor.ReadyUnit ready = (RecoveryUnitProcessor.ReadyUnit)
                complete.processor().validate(unit, complete.index(), INGESTED_AT);
        complete.index().confirmCommitted(ready);
        assertThatThrownBy(() -> session.accept(complete.result(List.of())))
                .isInstanceOf(IllegalStateException.class).hasMessage("Batch session is already complete");
        assertThatThrownBy(() -> session.sourceFailed(
                new SourceException(ErrorCode.SOURCE_TIMEOUT, "source-secret")))
                .isInstanceOf(IllegalStateException.class).hasMessage("Batch session is already complete");
    }

    @Test
    void rejectsForeignProcessorUnitsAndCrossDatasetIndexes() {
        Fixture owner = fixture(false);
        RecoveryUnitProcessor.UnitInput unit = owner.session().accept(owner.result(List.of(
                row("a-key", "000001.SZ", "20260903", "1.20")))).units().getFirst();
        Fixture foreign = fixture(false);

        assertThatThrownBy(() -> foreign.processor().validate(unit, foreign.index(), INGESTED_AT))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("unit belongs to another processor");
        CommittedKeyIndex wrongDataset = new CommittedKeyIndex(
                new DatasetKey(new PluginId("other"), API_NAME));
        assertThatThrownBy(() -> owner.processor().validate(unit, wrongDataset, INGESTED_AT))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("committed index dataset mismatch");
    }

    @Test
    void keepsValidatedTicketConstructionPrivateAndKnownMembersPackageOnly() throws Exception {
        assertThat(RecoveryUnitProcessor.UnitInput.class.getDeclaredConstructors())
                .singleElement().satisfies(constructor ->
                        assertThat(Modifier.isPrivate(constructor.getModifiers())).isTrue());
        assertThat(RecoveryUnitProcessor.ReadyUnit.class.getDeclaredConstructors())
                .singleElement().satisfies(constructor ->
                        assertThat(Modifier.isPrivate(constructor.getModifiers())).isTrue());
        assertThat(Modifier.isPublic(RecoveryUnitProcessor.KnownMembers.class.getModifiers())).isFalse();
        assertThat(RecoveryUnitProcessor.class.getDeclaredMethod("openInitial",
                ApiDescriptor.class, ValidatedParameters.class, RecoverySelector.class,
                FetchBatch.class, DownloadContext.class, RecoveryUnitProcessor.KnownMembers.class)
                .getModifiers()).satisfies(modifiers -> {
                    assertThat(Modifier.isPublic(modifiers)).isFalse();
                    assertThat(Modifier.isProtected(modifiers)).isFalse();
                    assertThat(Modifier.isPrivate(modifiers)).isFalse();
                });
    }

    @Test
    void isolatesANonKeyAdapterFailureToBAndKeepsAAndCReady() {
        Fixture fixture = fixture(false);
        FetchResult result = fixture.result(List.of(
                row("a-key", "000001.SZ", "20260903", "1.20"),
                row("b-key", "600000.SH", "20260903", "not-a-decimal"),
                row("c-key", "000002.SZ", "20260903", "3.40")));

        RecoveryUnitProcessor.PreparedBatch prepared = fixture.session().accept(result);
        Map<String, RecoveryUnitProcessor.Validation> validations = prepared.units().stream().collect(
                java.util.stream.Collectors.toMap(unit -> unit.selector().targetValue(),
                        unit -> fixture.processor().validate(unit, fixture.index(), INGESTED_AT)));

        assertThat(prepared.failures()).isEmpty();
        assertThat(validations).hasSize(3);
        assertThat(validations.get("000001.SZ")).isInstanceOf(RecoveryUnitProcessor.ReadyUnit.class);
        assertThat(validations.get("000002.SZ")).isInstanceOf(RecoveryUnitProcessor.ReadyUnit.class);
        assertThat(validations.get("600000.SH"))
                .isInstanceOfSatisfying(RecoveryUnitProcessor.RejectedUnit.class,
                        rejected -> assertThat(rejected.failure().errorCode())
                                .isEqualTo(ErrorCode.ADAPTER_TYPE_INVALID));
    }

    @Test
    void rejectsTheWholeScopeBeforeAdaptingWhenTheLastRowCannotBeAttributed() {
        Fixture fixture = fixture(true);
        FetchResult result = fixture.result(List.of(
                row("a-key", "000001.SZ", "20260903", "1.20"),
                row("bad-key", null, "20260903", "2.30")));

        RecoveryUnitProcessor.PreparedBatch prepared = fixture.session().accept(result);

        assertThat(prepared.units()).isEmpty();
        assertThat(prepared.failures()).singleElement().satisfies(failure -> {
            assertThat(failure.selector()).isEqualTo(fixture.scope());
            assertThat(failure.errorCode()).isEqualTo(ErrorCode.SOURCE_PAYLOAD_INVALID);
        });
        verify(fixture.adapter(), never()).adaptRows(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsTheWholeUnitWhenOneBusinessKeyHasDifferentCanonicalContent() {
        Fixture fixture = fixture(false);
        FetchResult result = fixture.result(List.of(
                row("same-key", "000001.SZ", "20260903", "1.20"),
                row("same-key", "000001.SZ", "20260903", "2.30")));
        RecoveryUnitProcessor.UnitInput unit = fixture.session().accept(result).units().getFirst();

        RecoveryUnitProcessor.Validation validation =
                fixture.processor().validate(unit, fixture.index(), INGESTED_AT);

        assertThat(validation).isInstanceOfSatisfying(RecoveryUnitProcessor.RejectedUnit.class,
                rejected -> assertThat(rejected.failure().errorCode()).isEqualTo(ErrorCode.DATA_CONFLICT));
        assertThat(fixture.index().size()).isZero();
    }

    @Test
    void leavesTheCommittedIndexEmptyUntilTheReadyUnitIsExplicitlyConfirmed() {
        Fixture fixture = fixture(false);
        RecoveryUnitProcessor.UnitInput unit = fixture.session()
                .accept(fixture.result(List.of(row("a-key", "000001.SZ", "20260903", "1.20"))))
                .units().getFirst();

        RecoveryUnitProcessor.Validation validation =
                fixture.processor().validate(unit, fixture.index(), INGESTED_AT);

        assertThat(validation).isInstanceOfSatisfying(RecoveryUnitProcessor.ReadyUnit.class, ready -> {
            assertThat(ready.batch().rows()).hasSize(1);
            assertThat(ready.sourceRowCount()).isEqualTo(1);
        });
        assertThat(fixture.index().size()).isZero();
    }

    @Test
    void removesOnlyByteIdenticalDuplicatesAfterPreservingTheSourceRowCount() {
        Fixture fixture = fixture(false);
        RecoveryUnitProcessor.UnitInput unit = fixture.session().accept(fixture.result(List.of(
                row("same-key", "000001.SZ", "20260903", "1.2"),
                row("same-key", "000001.SZ", "20260903", "1.20"),
                row("same-key", "000001.SZ", "20260903", "1.200")))).units().getFirst();

        RecoveryUnitProcessor.Validation validation =
                fixture.processor().validate(unit, fixture.index(), INGESTED_AT);

        assertThat(validation).isInstanceOfSatisfying(RecoveryUnitProcessor.ReadyUnit.class, ready -> {
            assertThat(ready.batch().rows()).hasSize(1);
            assertThat(ready.sourceRowCount()).isEqualTo(3);
        });
    }

    @Test
    void keepsTheWholeRequestWhenIndependentRecoveryIsUnavailableOrItsCommonMapDiffers() {
        for (Fixture fixture : List.of(fixture(false, false, false), fixture(false, true, true))) {
            RecoveryUnitProcessor.PreparedBatch prepared = fixture.session().accept(fixture.result(List.of(
                    row("a-key", "000001.SZ", "20260903", "1.20"),
                    row("b-key", "000001.SZ", "20260903", "bad"))));

            assertThat(prepared.units()).singleElement()
                    .extracting(RecoveryUnitProcessor.UnitInput::selector).isEqualTo(fixture.scope());
            assertThat(fixture.processor().validate(prepared.units().getFirst(), fixture.index(), INGESTED_AT))
                    .isInstanceOfSatisfying(RecoveryUnitProcessor.RejectedUnit.class,
                            rejected -> assertThat(rejected.failure().errorCode())
                                    .isEqualTo(ErrorCode.ADAPTER_TYPE_INVALID));
        }
    }

    @Test
    void rejectsAKeyThatWouldBelongToTwoDifferentUnitsBeforePublishingEitherUnit() {
        Fixture fixture = fixture(false);

        RecoveryUnitProcessor.PreparedBatch prepared = fixture.session().accept(fixture.result(List.of(
                row("same-key", "000001.SZ", "20260903", "1.20"),
                row("same-key", "600000.SH", "20260903", "1.20"))));

        assertGlobal(prepared, fixture.scope(), ErrorCode.SOURCE_PAYLOAD_INVALID);
    }

    @Test
    void keepsExplicitStockFailuresSeparateButStillScansTheirRowsForGlobalAttribution() {
        Fixture fixture = fixture(false);
        RecoverySelector b = stock("600000.SH");
        FetchResult.UnitFailure failure = new FetchResult.UnitFailure(
                b, ErrorCode.SOURCE_TIMEOUT, "upstream secret must not escape");

        RecoveryUnitProcessor.PreparedBatch prepared = fixture.session().accept(fixture.result(List.of(
                row("a-key", "000001.SZ", "20260903", "1.20"),
                row("b-key", "600000.SH", "20260903", "bad")), List.of(failure)));

        assertThat(prepared.units()).singleElement()
                .extracting(unit -> unit.selector().targetValue()).isEqualTo("000001.SZ");
        assertThat(prepared.failures()).singleElement().satisfies(value -> {
            assertThat(value.selector()).isEqualTo(b);
            assertThat(value.errorCode()).isEqualTo(ErrorCode.SOURCE_TIMEOUT);
            assertThat(value.errorMessage()).doesNotContain("secret");
        });

        Fixture invalid = fixture(false);
        RecoveryUnitProcessor.PreparedBatch globallyRejected = invalid.session().accept(invalid.result(List.of(
                row("a-key", "000001.SZ", "20260903", "1.20"),
                row("b-key", "bad-code", "20260903", "bad")), List.of(failure)));
        assertGlobal(globallyRejected, invalid.scope(), ErrorCode.SOURCE_PAYLOAD_INVALID);
    }

    @Test
    void rejectsNonCanonicalRangeFailuresInsteadOfTreatingThemAsIndependentUnits() {
        for (RecoverySelector invalid : List.of(
                new RecoverySelector(RecoverySelector.TargetType.STOCK, "000001.SZ",
                        RecoverySelector.TimeType.DATE, "2026-09-03"),
                new RecoverySelector(RecoverySelector.TargetType.STOCK, "000001.SZ",
                        RecoverySelector.TimeType.RANGE, "2026-09-01/2026-09-05"))) {
            Fixture fixture = fixture(false);
            FetchResult.UnitFailure failure = new FetchResult.UnitFailure(
                    invalid, ErrorCode.SOURCE_TIMEOUT, "safe");
            assertGlobal(fixture.session().accept(fixture.result(List.of(), List.of(failure))),
                    fixture.scope(), ErrorCode.SOURCE_PAYLOAD_INVALID);
        }
    }

    @Test
    void returnsOnlyTheFrozenBoundaryForUnknownWholeSourceFailuresAndClosesTheSession() {
        Fixture fixture = fixture(false);
        RecoveryUnitProcessor.BatchSession session = fixture.session();
        SourceException failure = new SourceException(ErrorCode.SOURCE_TIMEOUT, "token=secret");

        RecoveryUnitProcessor.PreparedBatch prepared = session.sourceFailed(failure);

        assertThat(prepared.units()).isEmpty();
        assertThat(prepared.failures()).singleElement().satisfies(value -> {
            assertThat(value.selector()).isEqualTo(fixture.scope());
            assertThat(value.errorCode()).isEqualTo(ErrorCode.SOURCE_TIMEOUT);
            assertThat(value.errorMessage()).doesNotContain("secret", "token");
        });
        assertThatThrownBy(() -> session.accept(fixture.result(List.of())))
                .isInstanceOf(IllegalStateException.class).hasMessage("Batch session is already complete");
        assertThatThrownBy(() -> session.sourceFailed(failure))
                .isInstanceOf(IllegalStateException.class).hasMessage("Batch session is already complete");
    }

    @Test
    void propagatesUnconfirmedSourceRequestsWithoutRegisteringThemAsBusinessFailures() {
        Fixture fixture = fixture(false);
        SourceException failure = new SourceException(ErrorCode.SOURCE_REQUEST_UNCONFIRMED, "exact instance");

        assertThatThrownBy(() -> fixture.session().sourceFailed(failure)).isSameAs(failure);
    }

    @Test
    void treatsACompleteEmptyInitialResponseAsOneEmptyRequestUnitEvenWithKnownMembers() {
        Fixture fixture = fixture(false);
        RecoveryUnitProcessor.KnownMembers known = fixture.known(List.of(
                stock("000001.SZ"), stock("600000.SH"), stock("000002.SZ")));

        RecoveryUnitProcessor.PreparedBatch prepared = fixture.knownSession(known).accept(fixture.result(List.of()));

        assertThat(prepared.failures()).isEmpty();
        assertThat(prepared.units()).singleElement()
                .extracting(RecoveryUnitProcessor.UnitInput::selector).isEqualTo(fixture.scope());
        assertThat(fixture.processor().validate(prepared.units().getFirst(), fixture.index(), INGESTED_AT))
                .isInstanceOfSatisfying(RecoveryUnitProcessor.ReadyUnit.class, ready -> {
                    assertThat(ready.batch().rows()).isEmpty();
                    assertThat(ready.sourceRowCount()).isZero();
                });
    }

    @Test
    void knownMembersExpandOnlyWholeSourceFailureAndRejectMissingOrOutsideCoverage() {
        List<RecoverySelector> selectors = List.of(
                stock("000001.SZ"), stock("600000.SH"), stock("000002.SZ"));
        Fixture failed = fixture(false);
        RecoveryUnitProcessor.PreparedBatch sourceFailure = failed.knownSession(failed.known(selectors))
                .sourceFailed(new SourceException(ErrorCode.SOURCE_TIMEOUT, "unsafe"));
        assertThat(sourceFailure.failures()).extracting(value -> value.selector().targetValue())
                .containsExactly("000001.SZ", "000002.SZ", "600000.SH");

        Fixture missing = fixture(false);
        assertGlobal(missing.knownSession(missing.known(selectors)).accept(missing.result(List.of(
                        row("a-key", "000001.SZ", "20260903", "1.20"),
                        row("c-key", "000002.SZ", "20260903", "3.40")))),
                missing.scope(), ErrorCode.SOURCE_COMPLETENESS_UNCONFIRMED);

        Fixture outside = fixture(false);
        assertGlobal(outside.knownSession(outside.known(selectors)).accept(outside.result(List.of(
                        row("d-key", "300001.SZ", "20260903", "1.20")))),
                outside.scope(), ErrorCode.SOURCE_PAYLOAD_INVALID);
    }

    @Test
    void rejectsKnownMembersThatAreSubRangesDatesDuplicatesOrBoundToAnotherBatch() {
        Fixture fixture = fixture(false);
        List<List<RecoverySelector>> invalidLists = List.of(
                List.of(new RecoverySelector(RecoverySelector.TargetType.STOCK, "000001.SZ",
                        RecoverySelector.TimeType.DATE, "2026-09-03")),
                List.of(new RecoverySelector(RecoverySelector.TargetType.STOCK, "000001.SZ",
                        RecoverySelector.TimeType.RANGE, "2026-09-01/2026-09-05")),
                List.of(
                        new RecoverySelector(RecoverySelector.TargetType.STOCK, "000001.SZ",
                                RecoverySelector.TimeType.DATE, "2026-09-03"),
                        stock("000001.SZ")),
                List.of(
                        new RecoverySelector(RecoverySelector.TargetType.STOCK, "000001.SZ",
                                RecoverySelector.TimeType.RANGE, "2026-09-01/2026-09-05"),
                        new RecoverySelector(RecoverySelector.TargetType.STOCK, "000001.SZ",
                                RecoverySelector.TimeType.RANGE, "2026-09-04/2026-09-10")));
        for (List<RecoverySelector> values : invalidLists) {
            RecoveryUnitProcessor.KnownMembers known = fixture.known(values);
            assertCode(() -> fixture.knownSession(known), ErrorCode.SOURCE_REQUEST_UNCONFIRMED);
        }
        assertCode(() -> new RecoveryUnitProcessor.KnownMembers(DATASET, fixture.batch(),
                List.of(stock("000001.SZ"), stock("000001.SZ")), List.of("controlled")),
                ErrorCode.SOURCE_REQUEST_UNCONFIRMED);
        RecoveryUnitProcessor.KnownMembers wrongBatch = new RecoveryUnitProcessor.KnownMembers(
                DATASET, new FetchBatch(Map.of("start_date", "20260902", "end_date", "20260910"),
                fixture.batch().recoveryPolicy()), List.of(stock("000001.SZ")), List.of("controlled"));
        assertCode(() -> fixture.knownSession(wrongBatch), ErrorCode.SOURCE_REQUEST_UNCONFIRMED);
    }

    @Test
    void preservesSavedRequestAndStockRangeBoundariesOnRetry() {
        Fixture request = fixture(false);
        RecoveryUnitProcessor.PreparedBatch requestPrepared = request.processor().openRetry(
                request.api(), request.original().values(), request.scope(), request.batch(), CONTEXT)
                .accept(request.result(List.of(
                        row("a-key", "000001.SZ", "20260903", "1.20"),
                        row("b-key", "600000.SH", "20260907", "2.30"))));
        assertThat(requestPrepared.units()).singleElement()
                .extracting(RecoveryUnitProcessor.UnitInput::selector).isEqualTo(request.scope());

        Fixture stockFixture = fixture(false);
        RecoverySelector saved = stock("000001.SZ");
        FetchBatch stockBatch = new FetchBatch(Map.of("ts_code", "000001.SZ",
                "start_date", "20260901", "end_date", "20260910"),
                stockFixture.batch().recoveryPolicy());
        RecoveryUnitProcessor.PreparedBatch stockPrepared = stockFixture.processor().openRetry(
                stockFixture.api(), stockFixture.original().values(), saved, stockBatch, CONTEXT)
                .accept(stockFixture.resultFor(stockBatch, List.of(
                        row("first", "000001.SZ", "20260903", "1.20"),
                        row("second", "000001.SZ", "20260907", "2.30"))));
        assertThat(stockPrepared.units()).singleElement()
                .extracting(RecoveryUnitProcessor.UnitInput::selector).isEqualTo(saved);
        assertThat(stockPrepared.units().getFirst().selector().timeValue())
                .isEqualTo("2026-09-01/2026-09-10");

        RecoveryUnitProcessor.PreparedBatch sourceFailure = stockFixture.processor().openRetry(
                stockFixture.api(), stockFixture.original().values(), saved, stockBatch, CONTEXT)
                .sourceFailed(new SourceException(ErrorCode.SOURCE_TIMEOUT, "source-secret"));
        assertGlobal(sourceFailure, saved, ErrorCode.SOURCE_TIMEOUT);

        RecoveryUnitProcessor.PreparedBatch globalFailure = stockFixture.processor().openRetry(
                stockFixture.api(), stockFixture.original().values(), saved, stockBatch, CONTEXT)
                .accept(stockFixture.resultFor(stockBatch, List.of(
                        row("outside", "600000.SH", "20260903", "1.20"))));
        assertGlobal(globalFailure, saved, ErrorCode.SOURCE_PAYLOAD_INVALID);
    }

    @Test
    void preservesASavedStockSelectorForACompleteEmptyRetry() {
        Fixture fixture = fixture(false);
        RecoverySelector saved = stock("000001.SZ");
        FetchBatch stockBatch = new FetchBatch(Map.of(
                "ts_code", "000001.SZ", "start_date", "20260901", "end_date", "20260910"),
                fixture.batch().recoveryPolicy());

        RecoveryUnitProcessor.PreparedBatch prepared = fixture.processor().openRetry(
                fixture.api(), fixture.original().values(), saved, stockBatch, CONTEXT)
                .accept(fixture.resultFor(stockBatch, List.of()));

        assertThat(prepared.failures()).isEmpty();
        assertThat(prepared.units()).singleElement()
                .extracting(RecoveryUnitProcessor.UnitInput::selector).isEqualTo(saved);
        assertThat(fixture.processor().validate(prepared.units().getFirst(), fixture.index(), INGESTED_AT))
                .isInstanceOfSatisfying(RecoveryUnitProcessor.ReadyUnit.class, ready -> {
                    assertThat(ready.selector()).isEqualTo(saved);
                    assertThat(ready.batch().rows()).isEmpty();
                    assertThat(ready.sourceRowCount()).isZero();
                });
    }

    @Test
    void rejectsEveryGlobalAttributionBreakWithTheOriginalFrozenScope() {
        for (List<Object> invalid : List.of(
                row(null, "000001.SZ", "20260903", "1.20"),
                row("key", "lower.sz", "20260903", "1.20"),
                row("key", "000001.SZ", "20260230", "1.20"),
                row("key", "000001.SZ", "20260911", "1.20"))) {
            Fixture fixture = fixture(false);
            assertGlobal(fixture.session().accept(fixture.result(List.of(invalid))),
                    fixture.scope(), ErrorCode.SOURCE_PAYLOAD_INVALID);
        }
    }

    @Test
    void treatsNullMappingValuesAsGlobalPayloadFailuresRatherThanInternalErrors() {
        for (List<Object> invalid : List.of(
                Arrays.<Object>asList("key", null, "20260903", "1.20"),
                Arrays.<Object>asList("key", "000001.SZ", null, "1.20"))) {
            Fixture fixture = fixture(false);
            assertGlobal(fixture.session().accept(fixture.result(List.of(invalid))),
                    fixture.scope(), ErrorCode.SOURCE_PAYLOAD_INVALID);
        }
    }

    @Test
    void partitionsDateAndMonthPoliciesWithoutChangingTheirCanonicalGranularity() {
        TimeFixture date = timeFixture(RecoverySelector.TimeType.DATE);
        RecoveryUnitProcessor.PreparedBatch dates = date.session().accept(date.result(List.of(
                timeRow("a", "000001.SZ", "20260903", "1.20"),
                timeRow("b", "600000.SH", "20260903", "2.30"))));
        assertThat(dates.units()).extracting(RecoveryUnitProcessor.UnitInput::selector)
                .containsExactly(
                        new RecoverySelector(RecoverySelector.TargetType.STOCK, "000001.SZ",
                                RecoverySelector.TimeType.DATE, "2026-09-03"),
                        new RecoverySelector(RecoverySelector.TargetType.STOCK, "600000.SH",
                                RecoverySelector.TimeType.DATE, "2026-09-03"));

        TimeFixture month = timeFixture(RecoverySelector.TimeType.MONTH);
        RecoveryUnitProcessor.PreparedBatch months = month.session().accept(month.result(List.of(
                timeRow("a", "000001.SZ", "202609", "1.20"))));
        assertThat(months.units()).singleElement().extracting(RecoveryUnitProcessor.UnitInput::selector)
                .isEqualTo(new RecoverySelector(RecoverySelector.TargetType.STOCK, "000001.SZ",
                        RecoverySelector.TimeType.MONTH, "2026-09"));
    }

    @Test
    void preservesContextFailuresAtOpenGlobalCompletionAndLocalValidationBoundaries() {
        Fixture fixture = fixture(false);
        RuntimeException initialFailure = new RuntimeException("initial");
        assertThatThrownBy(() -> fixture.processor().openInitial(null, null, null, null,
                () -> { throw initialFailure; })).isSameAs(initialFailure);

        AtomicInteger globalChecks = new AtomicInteger();
        RuntimeException globalFailure = new RuntimeException("global");
        DownloadContext globalContext = () -> {
            if (globalChecks.incrementAndGet() == 3) throw globalFailure;
        };
        RecoveryUnitProcessor.BatchSession session = fixture.processor().openInitial(
                fixture.api(), fixture.original(), fixture.scope(), fixture.batch(), globalContext);
        assertThatThrownBy(() -> session.accept(fixture.result(List.of()))).isSameAs(globalFailure);
        assertThatThrownBy(() -> session.sourceFailed(new SourceException(ErrorCode.SOURCE_TIMEOUT, "safe")))
                .isInstanceOf(IllegalStateException.class);

        AtomicInteger localChecks = new AtomicInteger();
        RuntimeException localFailure = new RuntimeException("local");
        DownloadContext localContext = () -> {
            if (localChecks.incrementAndGet() == 4) throw localFailure;
        };
        RecoveryUnitProcessor.UnitInput unit = fixture.processor().openInitial(
                fixture.api(), fixture.original(), fixture.scope(), fixture.batch(), localContext)
                .accept(fixture.result(List.of(row("a", "000001.SZ", "20260903", "1.20"))))
                .units().getFirst();
        assertThatThrownBy(() -> fixture.processor().validate(unit, fixture.index(), INGESTED_AT))
                .isSameAs(localFailure);
    }

    @Test
    void rejectsIncompatibleInitialAndRetryScopesBeforeSourceConsumption() {
        Fixture fixture = fixture(false);
        RecoverySelector stock = stock("000001.SZ");
        assertCode(() -> fixture.processor().openInitial(fixture.api(), fixture.original(), stock,
                fixture.batch(), CONTEXT), ErrorCode.SOURCE_REQUEST_UNCONFIRMED);
        FetchBatch wrongParams = new FetchBatch(Map.of("start_date", "20260902", "end_date", "20260910"),
                fixture.batch().recoveryPolicy());
        assertCode(() -> fixture.processor().openInitial(fixture.api(), fixture.original(), fixture.scope(),
                wrongParams, CONTEXT), ErrorCode.SOURCE_REQUEST_UNCONFIRMED);
        assertCode(() -> fixture.processor().openRetry(fixture.api(), fixture.original().values(),
                fixture.scope(), wrongParams, CONTEXT), ErrorCode.RETRY_TASK_INVALID);
    }

    @Test
    void rejectsMismatchedAndFailureEnvelopesWithoutEchoingTheirPayload() {
        Fixture fixture = fixture(false);
        List<String> fields = definition().columns().stream().map(ColumnDefinition::name).toList();
        List<DownloadEnvelope> invalid = List.of(
                new DownloadEnvelope(new PluginId("other"), API_NAME, fixture.batch().sourceParams(), fields,
                        0, List.of(), DownloadStatus.SUCCESS, null),
                new DownloadEnvelope(PLUGIN, new ApiName("other"), fixture.batch().sourceParams(), fields,
                        0, List.of(), DownloadStatus.SUCCESS, null),
                new DownloadEnvelope(PLUGIN, API_NAME, Map.of("start_date", "20260902", "end_date", "20260910"),
                        fields, 0, List.of(), DownloadStatus.SUCCESS, null),
                new DownloadEnvelope(PLUGIN, API_NAME, fixture.batch().sourceParams(),
                        List.of("record_id"), 0, List.of(), DownloadStatus.SUCCESS, null),
                new DownloadEnvelope(PLUGIN, API_NAME, fixture.batch().sourceParams(), List.of(),
                        0, List.of(), DownloadStatus.FAILURE, "secret"));
        for (DownloadEnvelope envelope : invalid) {
            Fixture candidate = fixture(false);
            RecoveryUnitProcessor.PreparedBatch prepared = candidate.session().accept(new FetchResult(envelope, List.of()));
            assertGlobal(prepared, candidate.scope(), ErrorCode.SOURCE_PAYLOAD_INVALID);
            assertThat(prepared.failures().getFirst().errorMessage()).doesNotContain("secret");
        }
    }

    static Fixture fixture(boolean spyAdapter) {
        return fixture(spyAdapter, true, false);
    }

    private static Fixture fixture(boolean spyAdapter, boolean independent, boolean originalStock) {
        DatasetDefinition definition = definition();
        GenericDatasetAdapter real = new GenericDatasetAdapter(
                definition, new ValueConverter(), new FingerprintKeyCodec());
        GenericDatasetAdapter adapter = spyAdapter ? spy(real) : real;
        DownloadParameterConverter converter = new DownloadParameterConverter(new ParameterValidator());
        ApiDescriptor api = api(definition, independent);
        Map<String, Object> raw = new java.util.LinkedHashMap<>(
                Map.of("start_date", "20260901", "end_date", "20260910"));
        if (originalStock) raw.put("ts_code", "000001.SZ");
        ValidatedParameters original = converter.bindInitial(api, raw);
        RecoverySelector scope = new RecoverySelector(RecoverySelector.TargetType.REQUEST, "",
                RecoverySelector.TimeType.RANGE, "2026-09-01/2026-09-10");
        Map<String, Object> source = new java.util.LinkedHashMap<>(
                Map.of("start_date", "20260901", "end_date", "20260910"));
        if (originalStock) source.put("ts_code", "000001.SZ");
        FetchBatch batch = new FetchBatch(source,
                api.downloadPolicy().recoveryPolicy());
        RecoveryUnitProcessor processor = new RecoveryUnitProcessor(adapter, converter,
                new BusinessKeyExtractor(), new BusinessContentCodec());
        return new Fixture(adapter, processor, api, original, scope, batch,
                new CommittedKeyIndex(DATASET));
    }

    private static DatasetDefinition definition() {
        List<ColumnDefinition> columns = List.of(
                column("record_id", LogicalType.STRING, false, 32, null, null),
                column("ts_code", LogicalType.STRING, false, 16, null, null),
                column("ann_date", LogicalType.DATE, false, null, null, null),
                column("amount", LogicalType.DECIMAL, true, null, 12, 2));
        List<ParameterDescriptor> source = sourceParameters();
        return new DatasetDefinition(DATASET, "Controlled", "test", QueryMode.snapshot, source,
                TableName.from(DATASET), columns,
                new BusinessKeyDefinition(BusinessKeyMode.COMPOSITE, List.of("record_id")),
                List.of(), null, 100);
    }

    private static ApiDescriptor api(DatasetDefinition definition, boolean independent) {
        DownloadPolicy base = com.akkc.tensor.test.DownloadPolicies.original();
        RecoveryPolicy recovery = independent
                ? new RecoveryPolicy(RecoveryPolicy.Mode.STOCK_TIME, "ts_code", "ann_date",
                RecoverySelector.TimeType.RANGE, true,
                List.of("data-plane/tensor-core/src/test/java/com/akkc/tensor/core/download/RecoveryUnitProcessorTest.java"))
                : base.recoveryPolicy();
        DownloadPolicy policy = new DownloadPolicy(DownloadPolicy.Mode.ANN_DATE_RANGE,
                DownloadPolicy.DateSemantic.ANN_DATE, "Controlled recovery", null,
                new DownloadPolicy.Limits(31), DownloadPolicy.SourceRequestMode.RANGE, null,
                DownloadPolicy.RequestEvidenceStatus.DOCUMENTED_CANDIDATE,
                DownloadPolicy.BatchPlanning.SOURCE_RANGE, recovery, base.completenessPolicy(), null,
                List.of("docs/test.md"));
        return new ApiDescriptor(API_NAME, "Controlled", "test", QueryMode.snapshot,
                DownloadParameterProjection.project(sourceParameters(), policy), policy,
                definition.parameters());
    }

    private static List<ParameterDescriptor> sourceParameters() {
        return List.of(
                parameter("ts_code", ParameterType.TS_CODE, false, null),
                parameter("ann_date", ParameterType.DATE, false, null));
    }

    private static ParameterDescriptor parameter(
            String name, ParameterType type, boolean required, String related) {
        return new ParameterDescriptor(name, name, name, type, required, null, List.of(), null, related);
    }

    private static ColumnDefinition column(String name, LogicalType type, boolean nullable,
            Integer length, Integer precision, Integer scale) {
        return new ColumnDefinition(name, name, type, nullable, 0, length, precision, scale,
                List.of(), false);
    }

    static List<Object> row(String id, String code, String date, String amount) {
        return java.util.Arrays.asList(id, code, date, amount);
    }

    private static RecoverySelector stock(String code) {
        return new RecoverySelector(RecoverySelector.TargetType.STOCK, code,
                RecoverySelector.TimeType.RANGE, "2026-09-01/2026-09-10");
    }

    private static FetchResult.UnitFailure wholeFailure(Fixture fixture) {
        return new FetchResult.UnitFailure(
                fixture.scope(), ErrorCode.SOURCE_TIMEOUT, "source-secret");
    }

    private static Fixture monthSourceDateRecoveryFixture() {
        List<ParameterDescriptor> source = List.of(
                parameter("ts_code", ParameterType.TS_CODE, false, null),
                parameter("month", ParameterType.MONTH, false, null));
        List<ColumnDefinition> columns = List.of(
                column("record_id", LogicalType.STRING, false, 32, null, null),
                column("ts_code", LogicalType.STRING, false, 16, null, null),
                column("trade_date", LogicalType.DATE, false, null, null, null),
                column("amount", LogicalType.DECIMAL, true, null, 12, 2));
        RecoveryPolicy recovery = new RecoveryPolicy(
                RecoveryPolicy.Mode.STOCK_TIME, "ts_code", "trade_date",
                RecoverySelector.TimeType.DATE, true, List.of("docs/test.md"));
        DownloadPolicy base = com.akkc.tensor.test.DownloadPolicies.original();
        DownloadPolicy policy = new DownloadPolicy(
                DownloadPolicy.Mode.MONTH_RANGE, DownloadPolicy.DateSemantic.COVERED_MONTH,
                "Controlled", null, new DownloadPolicy.Limits(31),
                DownloadPolicy.SourceRequestMode.MONTH, "month",
                DownloadPolicy.RequestEvidenceStatus.DOCUMENTED_CANDIDATE,
                DownloadPolicy.BatchPlanning.SINGLE_MONTH, recovery, base.completenessPolicy(),
                null, List.of("docs/test.md"));
        return customFixture(source, columns, new BusinessKeyDefinition(
                        BusinessKeyMode.COMPOSITE, List.of("record_id")), policy,
                Map.of("start_date", "20260901", "end_date", "20260930"),
                new RecoverySelector(RecoverySelector.TargetType.REQUEST, "",
                        RecoverySelector.TimeType.MONTH, "2026-09"), false);
    }

    private static Fixture patternRestrictedFixture() {
        List<ParameterDescriptor> source = List.of(
                new ParameterDescriptor("ts_code", "ts_code", "ts_code", ParameterType.TS_CODE,
                        false, null, List.of(), "[0-9]+\\.SH", null),
                parameter("ann_date", ParameterType.DATE, false, null));
        RecoveryPolicy recovery = new RecoveryPolicy(
                RecoveryPolicy.Mode.STOCK_TIME, "ts_code", "ann_date",
                RecoverySelector.TimeType.RANGE, true, List.of("docs/test.md"));
        DownloadPolicy base = com.akkc.tensor.test.DownloadPolicies.original();
        DownloadPolicy policy = new DownloadPolicy(
                DownloadPolicy.Mode.ANN_DATE_RANGE, DownloadPolicy.DateSemantic.ANN_DATE,
                "Controlled", null, new DownloadPolicy.Limits(31),
                DownloadPolicy.SourceRequestMode.RANGE, null,
                DownloadPolicy.RequestEvidenceStatus.DOCUMENTED_CANDIDATE,
                DownloadPolicy.BatchPlanning.SOURCE_RANGE, recovery, base.completenessPolicy(),
                null, List.of("docs/test.md"));
        return customFixture(source, definition().columns(), definition().businessKey(), policy,
                Map.of("start_date", "20260901", "end_date", "20260910"),
                new RecoverySelector(RecoverySelector.TargetType.REQUEST, "",
                        RecoverySelector.TimeType.RANGE, "2026-09-01/2026-09-10"), false);
    }

    private static Fixture customFixture(List<ParameterDescriptor> source,
            List<ColumnDefinition> columns, BusinessKeyDefinition businessKey,
            DownloadPolicy policy, Map<String, Object> raw, RecoverySelector scope,
            boolean spyAdapter) {
        DatasetDefinition definition = new DatasetDefinition(DATASET, "Controlled", "test",
                QueryMode.snapshot, source, TableName.from(DATASET), columns, businessKey,
                List.of(), null, 100);
        ApiDescriptor api = new ApiDescriptor(API_NAME, "Controlled", "test", QueryMode.snapshot,
                DownloadParameterProjection.project(source, policy), policy, source);
        DownloadParameterConverter converter = new DownloadParameterConverter(new ParameterValidator());
        ValidatedParameters original = converter.bindInitial(api, raw);
        FetchBatch batch = new FetchBatch(
                converter.mapInitial(api, original, scope).sourceParams().values(),
                policy.recoveryPolicy());
        GenericDatasetAdapter real = new GenericDatasetAdapter(
                definition, new ValueConverter(), new FingerprintKeyCodec());
        GenericDatasetAdapter adapter = spyAdapter ? spy(real) : real;
        RecoveryUnitProcessor processor = new RecoveryUnitProcessor(adapter, converter,
                new BusinessKeyExtractor(), new BusinessContentCodec());
        return new Fixture(adapter, processor, api, original, scope, batch,
                new CommittedKeyIndex(DATASET));
    }

    private static Fixture nullableFingerprintFixture() {
        List<ColumnDefinition> columns = List.of(
                column("record_id", LogicalType.STRING, true, 32, null, null),
                column("ts_code", LogicalType.STRING, false, 16, null, null),
                column("ann_date", LogicalType.DATE, false, null, null, null),
                column("amount", LogicalType.DECIMAL, true, null, 12, 2));
        return customFixture(sourceParameters(), columns,
                new BusinessKeyDefinition(BusinessKeyMode.FINGERPRINT, List.of("record_id")),
                rangePolicy(stockRangeRecovery()),
                Map.of("start_date", "20260901", "end_date", "20260910"),
                requestRange(), false);
    }

    private static Fixture dateSourceRangeRecoveryFixture() {
        DownloadPolicy base = com.akkc.tensor.test.DownloadPolicies.original();
        DownloadPolicy policy = new DownloadPolicy(
                DownloadPolicy.Mode.ANN_DATE_RANGE, DownloadPolicy.DateSemantic.ANN_DATE,
                "Controlled", null, new DownloadPolicy.Limits(31),
                DownloadPolicy.SourceRequestMode.DATE, "ann_date",
                DownloadPolicy.RequestEvidenceStatus.DOCUMENTED_CANDIDATE,
                DownloadPolicy.BatchPlanning.SINGLE_DATE, stockRangeRecovery(),
                base.completenessPolicy(), null, List.of("docs/test.md"));
        return customFixture(sourceParameters(), definition().columns(), definition().businessKey(),
                policy, Map.of("start_date", "20260903", "end_date", "20260903"),
                new RecoverySelector(RecoverySelector.TargetType.REQUEST, "",
                        RecoverySelector.TimeType.DATE, "2026-09-03"), false);
    }

    private static Fixture sharedAttributionKeyFixture() {
        List<ColumnDefinition> columns = List.of(
                column("record_id", LogicalType.STRING, false, 32, null, null),
                column("ts_code", LogicalType.STRING, false, 3, null, null),
                column("ann_date", LogicalType.DATE, false, null, null, null),
                column("amount", LogicalType.DECIMAL, true, null, 12, 2));
        return customFixture(sourceParameters(), columns,
                new BusinessKeyDefinition(BusinessKeyMode.COMPOSITE, List.of("ts_code")),
                rangePolicy(stockRangeRecovery()),
                Map.of("start_date", "20260901", "end_date", "20260910"),
                requestRange(), false);
    }

    private static Fixture originalParamsFixture() {
        List<ParameterDescriptor> source = List.of(
                parameter("list_status", ParameterType.TEXT, false, null));
        List<ColumnDefinition> columns = List.of(
                column("record_id", LogicalType.STRING, false, 32, null, null),
                column("memo", LogicalType.TEXT, true, null, null, null));
        DownloadPolicy base = com.akkc.tensor.test.DownloadPolicies.original();
        DownloadPolicy policy = new DownloadPolicy(
                DownloadPolicy.Mode.ORIGINAL_PARAMS, DownloadPolicy.DateSemantic.NONE,
                "Controlled", null, null, DownloadPolicy.SourceRequestMode.NONE, null,
                DownloadPolicy.RequestEvidenceStatus.DOCUMENTED_CANDIDATE,
                DownloadPolicy.BatchPlanning.ORIGINAL_PARAMS, base.recoveryPolicy(),
                base.completenessPolicy(), null, List.of("docs/test.md"));
        return customFixture(source, columns,
                new BusinessKeyDefinition(BusinessKeyMode.COMPOSITE, List.of("record_id")), policy,
                Map.of("list_status", "L"),
                new RecoverySelector(RecoverySelector.TargetType.REQUEST, "",
                        RecoverySelector.TimeType.NONE, ""), false);
    }

    private static Fixture equalEndpointFixture() {
        return customFixture(sourceParameters(), definition().columns(), definition().businessKey(),
                rangePolicy(stockRangeRecovery()),
                Map.of("start_date", "20260903", "end_date", "20260903"),
                new RecoverySelector(RecoverySelector.TargetType.REQUEST, "",
                        RecoverySelector.TimeType.DATE, "2026-09-03"), false);
    }

    private static Fixture scalarParameterFixture() {
        List<ParameterDescriptor> source = List.of(
                parameter("ts_code", ParameterType.TS_CODE, false, null),
                parameter("ann_date", ParameterType.DATE, false, null),
                parameter("exchange", ParameterType.TEXT, true, null),
                parameter("exchange_id", ParameterType.TEXT, true, null));
        return customFixture(source, definition().columns(), definition().businessKey(),
                rangePolicy(stockRangeRecovery()),
                Map.of("start_date", "20260901", "end_date", "20260910",
                        "exchange", "SSE", "exchange_id", "XSHG"),
                requestRange(), false);
    }

    private static Fixture missingTsCodeParameterFixture() {
        List<ParameterDescriptor> source = List.of(
                parameter("ann_date", ParameterType.DATE, false, null));
        return customFixture(source, definition().columns(), definition().businessKey(),
                rangePolicy(stockRangeRecovery()),
                Map.of("start_date", "20260901", "end_date", "20260910"),
                requestRange(), false);
    }

    private static Fixture requiredAmountFixture() {
        List<ColumnDefinition> columns = List.of(
                column("record_id", LogicalType.STRING, false, 32, null, null),
                column("ts_code", LogicalType.STRING, false, 16, null, null),
                column("ann_date", LogicalType.DATE, false, null, null, null),
                column("amount", LogicalType.DECIMAL, false, null, 12, 2));
        return customFixture(sourceParameters(), columns, definition().businessKey(),
                rangePolicy(stockRangeRecovery()),
                Map.of("start_date", "20260901", "end_date", "20260910"),
                requestRange(), false);
    }

    private static RecoveryPolicy stockRangeRecovery() {
        return new RecoveryPolicy(
                RecoveryPolicy.Mode.STOCK_TIME, "ts_code", "ann_date",
                RecoverySelector.TimeType.RANGE, true, List.of("docs/test.md"));
    }

    private static DownloadPolicy rangePolicy(RecoveryPolicy recovery) {
        DownloadPolicy base = com.akkc.tensor.test.DownloadPolicies.original();
        return new DownloadPolicy(
                DownloadPolicy.Mode.ANN_DATE_RANGE, DownloadPolicy.DateSemantic.ANN_DATE,
                "Controlled", null, new DownloadPolicy.Limits(31),
                DownloadPolicy.SourceRequestMode.RANGE, null,
                DownloadPolicy.RequestEvidenceStatus.DOCUMENTED_CANDIDATE,
                DownloadPolicy.BatchPlanning.SOURCE_RANGE, recovery, base.completenessPolicy(),
                null, List.of("docs/test.md"));
    }

    private static RecoverySelector requestRange() {
        return new RecoverySelector(RecoverySelector.TargetType.REQUEST, "",
                RecoverySelector.TimeType.RANGE, "2026-09-01/2026-09-10");
    }

    private static void assertGlobal(RecoveryUnitProcessor.PreparedBatch prepared,
            RecoverySelector scope, ErrorCode code) {
        assertThat(prepared.units()).isEmpty();
        assertThat(prepared.failures()).singleElement().satisfies(failure -> {
            assertThat(failure.selector()).isEqualTo(scope);
            assertThat(failure.errorCode()).isEqualTo(code);
            assertThat(failure.errorMessage()).isNotBlank().doesNotContain("secret");
        });
    }

    private static void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, ErrorCode code) {
        assertThatThrownBy(call).isInstanceOfSatisfying(TensorException.class,
                failure -> assertThat(failure.code()).isEqualTo(code));
    }

    private static TimeFixture timeFixture(RecoverySelector.TimeType unit) {
        String timeField = unit == RecoverySelector.TimeType.DATE ? "trade_date" : "month";
        LogicalType logicalType = unit == RecoverySelector.TimeType.DATE ? LogicalType.DATE : LogicalType.MONTH;
        ParameterType parameterType = unit == RecoverySelector.TimeType.DATE ? ParameterType.DATE : ParameterType.MONTH;
        List<ParameterDescriptor> source = List.of(
                parameter("ts_code", ParameterType.TS_CODE, false, null),
                parameter(timeField, parameterType, false, null));
        List<ColumnDefinition> columns = List.of(
                column("record_id", LogicalType.STRING, false, 32, null, null),
                column("ts_code", LogicalType.STRING, false, 16, null, null),
                column(timeField, logicalType, false, null, null, null),
                column("amount", LogicalType.DECIMAL, true, null, 12, 2));
        DatasetDefinition definition = new DatasetDefinition(DATASET, "Controlled", "test", QueryMode.snapshot,
                source, TableName.from(DATASET), columns,
                new BusinessKeyDefinition(BusinessKeyMode.COMPOSITE, List.of("record_id")), List.of(), null, 100);
        DownloadPolicy base = com.akkc.tensor.test.DownloadPolicies.original();
        RecoveryPolicy recovery = new RecoveryPolicy(RecoveryPolicy.Mode.STOCK_TIME, "ts_code", timeField, unit,
                true, List.of("data-plane/tensor-core/src/test/java/com/akkc/tensor/core/download/RecoveryUnitProcessorTest.java"));
        DownloadPolicy policy = unit == RecoverySelector.TimeType.DATE
                ? new DownloadPolicy(DownloadPolicy.Mode.TRADE_DATE_RANGE, DownloadPolicy.DateSemantic.TRADE_DATE,
                "Controlled", DownloadPolicy.CalendarProfile.C_A, new DownloadPolicy.Limits(31),
                DownloadPolicy.SourceRequestMode.DATE, timeField,
                DownloadPolicy.RequestEvidenceStatus.DOCUMENTED_CANDIDATE,
                DownloadPolicy.BatchPlanning.SINGLE_DATE, recovery, base.completenessPolicy(),
                DownloadPolicy.CalendarEvidenceStatus.UNCONFIRMED, List.of("docs/test.md"))
                : new DownloadPolicy(DownloadPolicy.Mode.MONTH_RANGE, DownloadPolicy.DateSemantic.COVERED_MONTH,
                "Controlled", null, new DownloadPolicy.Limits(31), DownloadPolicy.SourceRequestMode.MONTH, timeField,
                DownloadPolicy.RequestEvidenceStatus.DOCUMENTED_CANDIDATE,
                DownloadPolicy.BatchPlanning.SINGLE_MONTH, recovery, base.completenessPolicy(), null,
                List.of("docs/test.md"));
        ApiDescriptor api = new ApiDescriptor(API_NAME, "Controlled", "test", QueryMode.snapshot,
                DownloadParameterProjection.project(source, policy), policy, source);
        DownloadParameterConverter converter = new DownloadParameterConverter(new ParameterValidator());
        ValidatedParameters original = converter.bindInitial(api,
                Map.of("start_date", "20260901", "end_date", "20260910"));
        RecoverySelector scope = new RecoverySelector(RecoverySelector.TargetType.REQUEST, "", unit,
                unit == RecoverySelector.TimeType.DATE ? "2026-09-03" : "2026-09");
        Map<String, Object> sourceParams = converter.mapInitial(api, original, scope).sourceParams().values();
        FetchBatch batch = new FetchBatch(sourceParams, recovery);
        GenericDatasetAdapter adapter = new GenericDatasetAdapter(
                definition, new ValueConverter(), new FingerprintKeyCodec());
        RecoveryUnitProcessor processor = new RecoveryUnitProcessor(adapter, converter,
                new BusinessKeyExtractor(), new BusinessContentCodec());
        return new TimeFixture(definition, processor, api, original, scope, batch);
    }

    private static List<Object> timeRow(String id, String code, String time, String amount) {
        return Arrays.asList(id, code, time, amount);
    }

    static record Fixture(GenericDatasetAdapter adapter, RecoveryUnitProcessor processor,
            ApiDescriptor api, ValidatedParameters original, RecoverySelector scope,
            FetchBatch batch, CommittedKeyIndex index) {
        RecoveryUnitProcessor.BatchSession session() {
            return processor.openInitial(api, original, scope, batch, CONTEXT);
        }

        RecoveryUnitProcessor.BatchSession knownSession(RecoveryUnitProcessor.KnownMembers known) {
            return processor.openInitial(api, original, scope, batch, CONTEXT, known);
        }

        RecoveryUnitProcessor.KnownMembers known(List<RecoverySelector> selectors) {
            return new RecoveryUnitProcessor.KnownMembers(DATASET, batch, selectors,
                    List.of("RecoveryUnitProcessorTest.controlledKnownMembers"));
        }

        FetchResult result(List<List<Object>> rows) {
            return result(rows, List.of());
        }

        FetchResult result(List<List<Object>> rows, List<FetchResult.UnitFailure> failures) {
            DownloadEnvelope envelope = new DownloadEnvelope(PLUGIN, API_NAME, batch.sourceParams(),
                    adapter.definition().columns().stream().map(ColumnDefinition::name).toList(),
                    rows.size(), rows, DownloadStatus.SUCCESS, null);
            return new FetchResult(envelope, failures);
        }

        FetchResult resultFor(FetchBatch sourceBatch, List<List<Object>> rows) {
            DownloadEnvelope envelope = new DownloadEnvelope(PLUGIN, API_NAME, sourceBatch.sourceParams(),
                    adapter.definition().columns().stream().map(ColumnDefinition::name).toList(),
                    rows.size(), rows, DownloadStatus.SUCCESS, null);
            return new FetchResult(envelope, List.of());
        }
    }

    private record TimeFixture(DatasetDefinition definition, RecoveryUnitProcessor processor,
            ApiDescriptor api, ValidatedParameters original, RecoverySelector scope, FetchBatch batch) {
        RecoveryUnitProcessor.BatchSession session() {
            return processor.openInitial(api, original, scope, batch, CONTEXT);
        }

        FetchResult result(List<List<Object>> rows) {
            DownloadEnvelope envelope = new DownloadEnvelope(PLUGIN, API_NAME, batch.sourceParams(),
                    definition.columns().stream().map(ColumnDefinition::name).toList(), rows.size(), rows,
                    DownloadStatus.SUCCESS, null);
            return new FetchResult(envelope, List.of());
        }
    }

    private static final class StrictSourceDriver {
        private final RecoveryUnitProcessor.BatchSession session;
        private final List<List<Object>> buffered = new ArrayList<>();

        private StrictSourceDriver(Fixture fixture) {
            this.session = fixture.session();
        }

        private RecoveryUnitProcessor.PreparedBatch collectThenFail(
                List<List<Object>> page, SourceException failure) {
            buffered.addAll(page);
            return session.sourceFailed(failure);
        }
    }
}
