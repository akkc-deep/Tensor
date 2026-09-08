package com.akkc.tensor.core.download;

import com.akkc.tensor.core.adapter.BusinessContentCodec;
import com.akkc.tensor.core.adapter.ConversionContext;
import com.akkc.tensor.core.adapter.GenericDatasetAdapter;
import com.akkc.tensor.core.adapter.ValueConverter;
import com.akkc.tensor.core.persistence.BusinessKey;
import com.akkc.tensor.core.persistence.BusinessKeyExtractor;
import com.akkc.tensor.core.validation.ValidatedParameters;
import com.akkc.tensor.plugin.api.dataset.ColumnDefinition;
import com.akkc.tensor.plugin.api.dataset.DatasetDefinition;
import com.akkc.tensor.plugin.api.dataset.LogicalType;
import com.akkc.tensor.plugin.api.descriptor.ApiDescriptor;
import com.akkc.tensor.plugin.api.download.AdaptedBatch;
import com.akkc.tensor.plugin.api.download.DownloadContext;
import com.akkc.tensor.plugin.api.download.DownloadEnvelope;
import com.akkc.tensor.plugin.api.download.DownloadStatus;
import com.akkc.tensor.plugin.api.download.FetchBatch;
import com.akkc.tensor.plugin.api.download.FetchResult;
import com.akkc.tensor.plugin.api.download.RecoveryPolicy;
import com.akkc.tensor.plugin.api.download.RecoverySelector;
import com.akkc.tensor.plugin.api.error.AdapterException;
import com.akkc.tensor.plugin.api.error.ErrorCode;
import com.akkc.tensor.plugin.api.error.SourceException;
import com.akkc.tensor.plugin.api.error.TensorException;
import com.akkc.tensor.plugin.api.model.DatasetKey;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

public final class RecoveryUnitProcessor {
    private static final String SOURCE_FAILED = "Source request failed before complete data was available";
    private static final String KNOWN_SOURCE_FAILED = "Source request failed; complete unit data was not obtained";
    private static final String PAYLOAD_INVALID = "Complete source payload is invalid";
    private static final String COMPLETENESS_UNCONFIRMED = "Complete source coverage is unconfirmed";
    private static final String REQUEST_UNIT_FAILED = "Request contains an explicit source unit failure";
    private static final String ADAPTER_MISSING = "Recovery unit contains missing business values";
    private static final String ADAPTER_INVALID = "Recovery unit contains invalid business values";
    private static final String DATA_CONFLICT = "Recovery unit contains conflicting business content";
    private static final String SESSION_CLOSED = "Batch session is already complete";
    private static final Comparator<RecoverySelector> SELECTOR_ORDER = Comparator
            .comparing((RecoverySelector value) -> value.targetType().name())
            .thenComparing(RecoverySelector::targetValue)
            .thenComparing(value -> value.timeType().name())
            .thenComparing(RecoverySelector::timeValue);

    private final GenericDatasetAdapter adapter;
    private final DownloadParameterConverter converter;
    private final BusinessKeyExtractor keys;
    private final BusinessContentCodec contents;
    private final DatasetDefinition definition;
    private final ValueConverter valueConverter = new ValueConverter();

    public RecoveryUnitProcessor(GenericDatasetAdapter adapter, DownloadParameterConverter converter,
            BusinessKeyExtractor keys, BusinessContentCodec contents) {
        this.adapter = Objects.requireNonNull(adapter, "adapter");
        this.converter = Objects.requireNonNull(converter, "converter");
        this.keys = Objects.requireNonNull(keys, "keys");
        this.contents = Objects.requireNonNull(contents, "contents");
        this.definition = Objects.requireNonNull(adapter.definition(), "adapter definition");
    }

    public BatchSession openInitial(ApiDescriptor api, ValidatedParameters original,
            RecoverySelector scope, FetchBatch batch, DownloadContext context) {
        return openInitial(api, original, scope, batch, context, null);
    }

    BatchSession openInitial(ApiDescriptor api, ValidatedParameters original,
            RecoverySelector scope, FetchBatch batch, DownloadContext context, KnownMembers knownMembers) {
        checkContext(context);
        Objects.requireNonNull(api, "api");
        Objects.requireNonNull(original, "original");
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(batch, "batch");
        checkConfiguration(api);
        if (scope.targetType() != RecoverySelector.TargetType.REQUEST
                || !batch.recoveryPolicy().equals(api.downloadPolicy().recoveryPolicy())) {
            throw sourceUnconfirmed();
        }
        Map<String, Object> requestTask = converter.taskParameters(
                api, original, RecoverySelector.TargetType.REQUEST);
        if (!converter.mapInitial(api, original, scope).sourceParams().values().equals(batch.sourceParams())) {
            throw sourceUnconfirmed();
        }
        boolean split = canSplitInitial(api, original, requestTask);
        if (split) checkMappingConfiguration(api.downloadPolicy().recoveryPolicy());
        if (knownMembers != null) {
            if (!split) throw sourceUnconfirmed();
            bindKnownMembers(api, scope, batch, requestTask, knownMembers);
        }
        return new BatchSession(api, scope, batch, requestTask, context, split, knownMembers);
    }

    public BatchSession openRetry(ApiDescriptor api, Map<String, Object> frozenTaskParams,
            RecoverySelector savedSelector, FetchBatch batch, DownloadContext context) {
        checkContext(context);
        Objects.requireNonNull(api, "api");
        Objects.requireNonNull(frozenTaskParams, "frozenTaskParams");
        Objects.requireNonNull(savedSelector, "savedSelector");
        Objects.requireNonNull(batch, "batch");
        checkConfiguration(api);
        if (!batch.recoveryPolicy().equals(api.downloadPolicy().recoveryPolicy())) throw invalidRetry();
        Map<String, Object> frozen = Map.copyOf(frozenTaskParams);
        if (!converter.mapRetry(api, frozen, savedSelector).sourceParams().values().equals(batch.sourceParams())) {
            throw invalidRetry();
        }
        boolean split = savedSelector.targetType() == RecoverySelector.TargetType.STOCK;
        if (split) checkMappingConfiguration(api.downloadPolicy().recoveryPolicy());
        return new BatchSession(api, savedSelector, batch, frozen, context, split, null);
    }

    public Validation validate(UnitInput unit, CommittedKeyIndex committed, Instant ingestedAt) {
        Objects.requireNonNull(unit, "unit");
        Objects.requireNonNull(committed, "committed");
        Objects.requireNonNull(ingestedAt, "ingestedAt");
        if (unit.owner != this) throw new IllegalArgumentException("unit belongs to another processor");
        unit.context.checkServerState();
        if (!committed.datasetKey().equals(definition.datasetKey())) {
            throw new IllegalArgumentException("committed index dataset mismatch");
        }
        AdaptedBatch adapted;
        try {
            adapted = adapter.adaptRows(unit.envelope, ingestedAt);
        } catch (AdapterException failure) {
            String message = failure.code() == ErrorCode.ADAPTER_FIELD_MISSING ? ADAPTER_MISSING : ADAPTER_INVALID;
            return new RejectedUnit(new Failure(unit.selector, failure.code(), message));
        }

        LinkedHashMap<BusinessKey, Candidate> unique = new LinkedHashMap<>();
        for (Map<String, Object> row : adapted.rows()) {
            BusinessKey key = keys.extract(definition, row);
            byte[] canonical = contents.encode(definition, row);
            Candidate candidate = new Candidate(row, canonical,
                    new CommittedKeyIndex.ContentDigest(BusinessContentCodec.VERSION, contents.sha256(canonical)));
            Candidate previous = unique.putIfAbsent(key, candidate);
            if (previous != null && !Arrays.equals(previous.canonical, canonical)) {
                return conflict(unit.selector);
            }
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        LinkedHashMap<BusinessKey, CommittedKeyIndex.ContentDigest> pending = new LinkedHashMap<>();
        for (Map.Entry<BusinessKey, Candidate> entry : unique.entrySet()) {
            CommittedKeyIndex.ContentDigest existing = committed.lookup(entry.getKey());
            if (existing == null) {
                rows.add(entry.getValue().row);
                pending.put(entry.getKey(), entry.getValue().digest);
            } else {
                if (existing.version() != BusinessContentCodec.VERSION) {
                    throw new IllegalStateException("committed content version mismatch");
                }
                if (!existing.equals(entry.getValue().digest)) return conflict(unit.selector);
            }
        }
        AdaptedBatch output = new AdaptedBatch(adapted.datasetKey(), adapted.tableName(), adapted.columns(), rows,
                adapted.businessKeyDefinition(), adapted.ingestedAt());
        return new ReadyUnit(unit.selector, output, unit.sourceRowCount, committed, pending);
    }

    private RejectedUnit conflict(RecoverySelector selector) {
        return new RejectedUnit(new Failure(selector, ErrorCode.DATA_CONFLICT, DATA_CONFLICT));
    }

    private boolean canSplitInitial(ApiDescriptor api, ValidatedParameters original,
            Map<String, Object> requestTask) {
        RecoveryPolicy recovery = api.downloadPolicy().recoveryPolicy();
        if (recovery.mode() != RecoveryPolicy.Mode.STOCK_TIME || !recovery.independentRecoveryVerified()
                || !compatibleSourceShape(api)) return false;
        try {
            return converter.taskParameters(api, original, RecoverySelector.TargetType.STOCK).equals(requestTask);
        } catch (RuntimeException failure) {
            return false;
        }
    }

    private static boolean compatibleSourceShape(ApiDescriptor api) {
        RecoverySelector.TimeType unit = api.downloadPolicy().recoveryPolicy().unitTimeType();
        return switch (api.downloadPolicy().sourceRequestMode()) {
            case DATE -> unit == RecoverySelector.TimeType.DATE || unit == RecoverySelector.TimeType.RANGE;
            case MONTH -> unit == RecoverySelector.TimeType.MONTH;
            case RANGE -> unit == RecoverySelector.TimeType.DATE || unit == RecoverySelector.TimeType.RANGE;
            case NONE -> false;
        };
    }

    private void checkConfiguration(ApiDescriptor api) {
        if (!api.apiName().equals(definition.datasetKey().apiName())
                || api.queryMode() != definition.queryMode()
                || !api.sourceParameters().equals(definition.parameters())) throw misconfigured();
        RecoveryPolicy recovery = api.downloadPolicy().recoveryPolicy();
        if (recovery.mode() == RecoveryPolicy.Mode.STOCK_TIME) checkMappingConfiguration(recovery);
    }

    private void checkMappingConfiguration(RecoveryPolicy recovery) {
        if (recovery.mode() != RecoveryPolicy.Mode.STOCK_TIME) throw misconfigured();
        ColumnDefinition target = column(recovery.targetField());
        ColumnDefinition time = column(recovery.timeField());
        if (target == null || (target.logicalType() != LogicalType.STRING
                && target.logicalType() != LogicalType.ENUM)) throw misconfigured();
        LogicalType expected = recovery.unitTimeType() == RecoverySelector.TimeType.MONTH
                ? LogicalType.MONTH : LogicalType.DATE;
        if (time == null || time.logicalType() != expected) throw misconfigured();
    }

    private ColumnDefinition column(String name) {
        return definition.columns().stream().filter(value -> value.name().equals(name)).findFirst().orElse(null);
    }

    private void bindKnownMembers(ApiDescriptor api, RecoverySelector scope, FetchBatch batch,
            Map<String, Object> taskParams, KnownMembers known) {
        if (!known.datasetKey().equals(definition.datasetKey()) || !known.batch().equals(batch)) throw sourceUnconfirmed();
        validateIndependentSelectors(api, scope, taskParams, known.selectors(), true);
    }

    private void validateIndependentSelectors(ApiDescriptor api, RecoverySelector scope,
            Map<String, Object> taskParams, List<RecoverySelector> selectors, boolean failOpen) {
        try {
            Set<RecoverySelector> seen = new HashSet<>();
            List<RecoverySelector> accepted = new ArrayList<>();
            for (RecoverySelector selector : selectors) {
                if (!seen.add(selector) || !canonicalUnit(selector, scope, api.downloadPolicy().recoveryPolicy())
                        || !compatible(selector, scope, taskParams)) throw new IllegalArgumentException();
                converter.mapRetry(api, taskParams, selector);
                for (RecoverySelector previous : accepted) {
                    if (previous.targetValue().equals(selector.targetValue()) && overlaps(previous, selector)) {
                        throw new IllegalArgumentException();
                    }
                }
                accepted.add(selector);
            }
        } catch (RuntimeException failure) {
            if (failOpen) throw sourceUnconfirmed();
            throw failure;
        }
    }

    private static boolean canonicalUnit(RecoverySelector selector, RecoverySelector boundary,
            RecoveryPolicy recovery) {
        if (selector.targetType() != RecoverySelector.TargetType.STOCK) return false;
        return switch (recovery.unitTimeType()) {
            case DATE -> selector.timeType() == RecoverySelector.TimeType.DATE && compatibleTime(selector, boundary);
            case MONTH -> selector.timeType() == RecoverySelector.TimeType.MONTH && compatibleTime(selector, boundary);
            case RANGE -> selector.timeType() == boundary.timeType()
                    && selector.timeValue().equals(boundary.timeValue())
                    && (boundary.timeType() == RecoverySelector.TimeType.RANGE
                    || boundary.timeType() == RecoverySelector.TimeType.DATE);
            case NONE -> false;
        };
    }

    private static boolean compatible(RecoverySelector selector, RecoverySelector boundary,
            Map<String, Object> taskParams) {
        if (selector.targetType() == RecoverySelector.TargetType.REQUEST) return selector.equals(boundary);
        if (boundary.targetType() == RecoverySelector.TargetType.STOCK
                && !selector.targetValue().equals(boundary.targetValue())) return false;
        Object frozenStock = taskParams.get("ts_code");
        if (frozenStock != null && !frozenStock.equals(selector.targetValue())) return false;
        return compatibleTime(selector, boundary);
    }

    private static boolean compatibleTime(RecoverySelector selector, RecoverySelector boundary) {
        if (boundary.timeType() == RecoverySelector.TimeType.NONE) return selector.timeType() == RecoverySelector.TimeType.NONE;
        DateInterval child = interval(selector);
        DateInterval parent = interval(boundary);
        return child != null && parent != null && !child.start.isBefore(parent.start)
                && !child.end.isAfter(parent.end);
    }

    private static boolean overlaps(RecoverySelector first, RecoverySelector second) {
        DateInterval left = interval(first);
        DateInterval right = interval(second);
        return left == null || right == null
                || !left.end.isBefore(right.start) && !right.end.isBefore(left.start);
    }

    private static DateInterval interval(RecoverySelector selector) {
        return switch (selector.timeType()) {
            case DATE -> {
                LocalDate date = LocalDate.parse(selector.timeValue());
                yield new DateInterval(date, date);
            }
            case MONTH -> {
                YearMonth month = YearMonth.parse(selector.timeValue());
                yield new DateInterval(month.atDay(1), month.atEndOfMonth());
            }
            case RANGE -> {
                String[] values = selector.timeValue().split("/", -1);
                yield new DateInterval(LocalDate.parse(values[0]), LocalDate.parse(values[1]));
            }
            case NONE -> null;
        };
    }

    private static void checkContext(DownloadContext context) {
        Objects.requireNonNull(context, "context").checkServerState();
    }

    private static SourceException sourceUnconfirmed() {
        return new SourceException(ErrorCode.SOURCE_REQUEST_UNCONFIRMED, "Source request conditions are unconfirmed");
    }

    private static TensorException invalidRetry() {
        return new ProcessingException(ErrorCode.RETRY_TASK_INVALID, "Saved download parameters are incompatible");
    }

    private static TensorException misconfigured() {
        return new ProcessingException(ErrorCode.DATASET_MISCONFIGURED, "Recovery dataset configuration is invalid");
    }

    public final class BatchSession {
        private final ApiDescriptor api;
        private final RecoverySelector scope;
        private final FetchBatch batch;
        private final Map<String, Object> taskParams;
        private final DownloadContext context;
        private final boolean split;
        private final KnownMembers knownMembers;
        private boolean complete;

        private BatchSession(ApiDescriptor api, RecoverySelector scope, FetchBatch batch,
                Map<String, Object> taskParams, DownloadContext context, boolean split, KnownMembers knownMembers) {
            this.api = api;
            this.scope = scope;
            this.batch = batch;
            this.taskParams = Map.copyOf(taskParams);
            this.context = context;
            this.split = split;
            this.knownMembers = knownMembers;
        }

        public PreparedBatch accept(FetchResult completeResult) {
            finish();
            context.checkServerState();
            PreparedBatch prepared = prepare(completeResult);
            context.checkServerState();
            return prepared;
        }

        public PreparedBatch sourceFailed(SourceException failure) {
            finish();
            Objects.requireNonNull(failure, "failure");
            if (failure.code() == ErrorCode.SOURCE_REQUEST_UNCONFIRMED) throw failure;
            List<Failure> failures = knownMembers == null
                    ? List.of(new Failure(scope, failure.code(), SOURCE_FAILED))
                    : knownMembers.selectors().stream().sorted(SELECTOR_ORDER)
                    .map(selector -> new Failure(selector, failure.code(), KNOWN_SOURCE_FAILED)).toList();
            return new PreparedBatch(List.of(), failures);
        }

        private void finish() {
            if (complete) throw new IllegalStateException(SESSION_CLOSED);
            complete = true;
        }

        private PreparedBatch prepare(FetchResult result) {
            if (result == null) return global(ErrorCode.SOURCE_PAYLOAD_INVALID, PAYLOAD_INVALID);
            DownloadEnvelope envelope = result.envelope();
            if (envelope.status() != DownloadStatus.SUCCESS
                    || !envelope.pluginId().equals(definition.datasetKey().pluginId())
                    || !envelope.apiName().equals(definition.datasetKey().apiName())
                    || !envelope.params().equals(batch.sourceParams())
                    || !envelope.fields().equals(definition.columns().stream().map(ColumnDefinition::name).toList())) {
                return global(ErrorCode.SOURCE_PAYLOAD_INVALID, PAYLOAD_INVALID);
            }
            List<FetchResult.UnitFailure> explicit = new ArrayList<>(result.failures());
            explicit.sort(Comparator.comparing(FetchResult.UnitFailure::selector, SELECTOR_ORDER));
            if (!validFailures(explicit)) return global(ErrorCode.SOURCE_PAYLOAD_INVALID, PAYLOAD_INVALID);
            if (!split) {
                if (!explicit.isEmpty()) return global(explicit.getFirst().errorCode(), REQUEST_UNIT_FAILED);
                return units(List.of(new UnitInput(scope, envelope, envelope.rowCount(), context)));
            }
            return split(envelope, explicit);
        }

        private boolean validFailures(List<FetchResult.UnitFailure> failures) {
            List<RecoverySelector> stock = new ArrayList<>();
            for (FetchResult.UnitFailure failure : failures) {
                RecoverySelector selector = failure.selector();
                if (!compatible(selector, scope, taskParams)) return false;
                if (selector.targetType() == RecoverySelector.TargetType.REQUEST) continue;
                if (split) {
                    try {
                        validateIndependentSelectors(api, scope, taskParams, List.of(selector), false);
                    } catch (RuntimeException invalid) {
                        return false;
                    }
                }
                for (RecoverySelector previous : stock) {
                    if (previous.targetValue().equals(selector.targetValue()) && overlaps(previous, selector)) return false;
                }
                stock.add(selector);
            }
            return true;
        }

        private PreparedBatch split(DownloadEnvelope envelope, List<FetchResult.UnitFailure> failures) {
            FetchResult.UnitFailure requestFailure = failures.stream()
                    .filter(failure -> failure.selector().targetType() == RecoverySelector.TargetType.REQUEST)
                    .findFirst().orElse(null);
            TreeMap<RecoverySelector, List<List<Object>>> grouped = new TreeMap<>(SELECTOR_ORDER);
            Map<BusinessKey, RecoverySelector> owners = new HashMap<>();
            try {
                for (int index = 0; index < envelope.data().size(); index++) {
                    RecoverySelector selector = selector(envelope, index);
                    BusinessKey key = keys.extract(definition, adapter.adaptKeyRow(envelope, index));
                    RecoverySelector previous = owners.putIfAbsent(key, selector);
                    if (previous != null && !previous.equals(selector)) return global(ErrorCode.SOURCE_PAYLOAD_INVALID, PAYLOAD_INVALID);
                    grouped.computeIfAbsent(selector, ignored -> new ArrayList<>()).add(envelope.data().get(index));
                }
            } catch (AdapterException | IllegalArgumentException failure) {
                return global(ErrorCode.SOURCE_PAYLOAD_INVALID, PAYLOAD_INVALID);
            }

            Set<RecoverySelector> observed = new HashSet<>(grouped.keySet());
            failures.stream()
                    .map(FetchResult.UnitFailure::selector)
                    .filter(selector -> selector.targetType() == RecoverySelector.TargetType.STOCK)
                    .forEach(observed::add);
            if (knownMembers != null) {
                Set<RecoverySelector> known = Set.copyOf(knownMembers.selectors());
                if (!known.containsAll(observed)) return global(ErrorCode.SOURCE_PAYLOAD_INVALID, PAYLOAD_INVALID);
                if (requestFailure == null && (!envelope.data().isEmpty() || !failures.isEmpty())
                        && !observed.containsAll(known)) {
                    return global(ErrorCode.SOURCE_COMPLETENESS_UNCONFIRMED, COMPLETENESS_UNCONFIRMED);
                }
            }
            if (requestFailure != null) return global(requestFailure.errorCode(), REQUEST_UNIT_FAILED);
            if (envelope.data().isEmpty() && failures.isEmpty()) {
                return units(List.of(new UnitInput(scope, envelope, 0, context)));
            }

            Map<RecoverySelector, FetchResult.UnitFailure> rejected = new HashMap<>();
            failures.forEach(failure -> rejected.put(failure.selector(), failure));
            List<UnitInput> units = new ArrayList<>();
            for (Map.Entry<RecoverySelector, List<List<Object>>> entry : grouped.entrySet()) {
                if (rejected.containsKey(entry.getKey())) continue;
                DownloadEnvelope slice = new DownloadEnvelope(envelope.pluginId(), envelope.apiName(), envelope.params(),
                        envelope.fields(), entry.getValue().size(), entry.getValue(), DownloadStatus.SUCCESS, null);
                units.add(new UnitInput(entry.getKey(), slice, entry.getValue().size(), context));
            }
            List<Failure> outputFailures = failures.stream()
                    .map(failure -> new Failure(failure.selector(), failure.errorCode(), SOURCE_FAILED)).toList();
            return new PreparedBatch(units, outputFailures);
        }

        private RecoverySelector selector(DownloadEnvelope envelope, int rowIndex) {
            RecoveryPolicy recovery = api.downloadPolicy().recoveryPolicy();
            ColumnDefinition target = column(recovery.targetField());
            ColumnDefinition time = column(recovery.timeField());
            int targetIndex = envelope.fields().indexOf(target.name());
            int timeIndex = envelope.fields().indexOf(time.name());
            ConversionContext conversion = new ConversionContext(envelope.apiName(), rowIndex);
            Object targetValue = valueConverter.convert(envelope.data().get(rowIndex).get(targetIndex), target, conversion);
            Object timeValue = valueConverter.convert(envelope.data().get(rowIndex).get(timeIndex), time, conversion);
            if (!(targetValue instanceof String code) || timeValue == null) throw new IllegalArgumentException();
            RecoverySelector selector = switch (recovery.unitTimeType()) {
                case DATE -> new RecoverySelector(RecoverySelector.TargetType.STOCK, code,
                        RecoverySelector.TimeType.DATE, ((LocalDate) timeValue).toString());
                case MONTH -> {
                    String month = (String) timeValue;
                    yield new RecoverySelector(RecoverySelector.TargetType.STOCK, code, RecoverySelector.TimeType.MONTH,
                            YearMonth.of(Integer.parseInt(month.substring(0, 4)), Integer.parseInt(month.substring(4, 6))).toString());
                }
                case RANGE -> new RecoverySelector(RecoverySelector.TargetType.STOCK, code, scope.timeType(), scope.timeValue());
                case NONE -> throw new IllegalArgumentException();
            };
            if (recovery.unitTimeType() == RecoverySelector.TimeType.RANGE) {
                RecoverySelector rowTime = new RecoverySelector(RecoverySelector.TargetType.STOCK, code,
                        RecoverySelector.TimeType.DATE, ((LocalDate) timeValue).toString());
                if (!compatibleTime(rowTime, scope)) throw new IllegalArgumentException();
            }
            if (!canonicalUnit(selector, scope, recovery) || !compatible(selector, scope, taskParams)) throw new IllegalArgumentException();
            try {
                converter.mapRetry(api, taskParams, selector);
            } catch (TensorException failure) {
                throw new IllegalArgumentException();
            }
            return selector;
        }

        private PreparedBatch global(ErrorCode code, String message) {
            return new PreparedBatch(List.of(), List.of(new Failure(scope, code, message)));
        }

        private PreparedBatch units(List<UnitInput> units) { return new PreparedBatch(units, List.of()); }
    }

    public record PreparedBatch(List<UnitInput> units, List<Failure> failures) {
        public PreparedBatch {
            units = List.copyOf(Objects.requireNonNull(units, "units"));
            failures = List.copyOf(Objects.requireNonNull(failures, "failures"));
        }
    }

    public final class UnitInput {
        private final RecoveryUnitProcessor owner;
        private final RecoverySelector selector;
        private final DownloadEnvelope envelope;
        private final long sourceRowCount;
        private final DownloadContext context;

        private UnitInput(RecoverySelector selector, DownloadEnvelope envelope, long sourceRowCount, DownloadContext context) {
            this.owner = RecoveryUnitProcessor.this;
            this.selector = Objects.requireNonNull(selector, "selector");
            this.envelope = Objects.requireNonNull(envelope, "envelope");
            this.sourceRowCount = sourceRowCount;
            this.context = Objects.requireNonNull(context, "context");
        }

        public RecoverySelector selector() { return selector; }
    }

    public sealed interface Validation permits ReadyUnit, RejectedUnit {}

    public static final class ReadyUnit implements Validation {
        private final RecoverySelector selector;
        private final AdaptedBatch batch;
        private final long sourceRowCount;
        private final CommittedKeyIndex index;
        private final Map<BusinessKey, CommittedKeyIndex.ContentDigest> pending;
        private boolean confirmed;

        private ReadyUnit(RecoverySelector selector, AdaptedBatch batch, long sourceRowCount,
                CommittedKeyIndex index, Map<BusinessKey, CommittedKeyIndex.ContentDigest> pending) {
            this.selector = Objects.requireNonNull(selector, "selector");
            this.batch = Objects.requireNonNull(batch, "batch");
            this.sourceRowCount = sourceRowCount;
            this.index = index;
            this.pending = Collections.unmodifiableMap(new LinkedHashMap<>(pending));
        }

        public RecoverySelector selector() { return selector; }
        public AdaptedBatch batch() { return batch; }
        public long sourceRowCount() { return sourceRowCount; }
        CommittedKeyIndex index() { return index; }
        Map<BusinessKey, CommittedKeyIndex.ContentDigest> pending() { return pending; }
        boolean confirmed() { return confirmed; }
        void markConfirmed() { confirmed = true; }
    }

    public record RejectedUnit(Failure failure) implements Validation {
        public RejectedUnit { Objects.requireNonNull(failure, "failure"); }
    }

    public record Failure(RecoverySelector selector, ErrorCode errorCode, String errorMessage) {
        public Failure {
            Objects.requireNonNull(selector, "selector");
            Objects.requireNonNull(errorCode, "errorCode");
            Objects.requireNonNull(errorMessage, "errorMessage");
            if (errorMessage.isBlank() || errorMessage.length() > 512
                    || errorMessage.chars().anyMatch(Character::isISOControl)) {
                throw new IllegalArgumentException("Invalid recovery failure summary");
            }
        }
    }

    record KnownMembers(DatasetKey datasetKey, FetchBatch batch,
            List<RecoverySelector> selectors, List<String> evidenceRefs) {
        KnownMembers {
            if (datasetKey == null || batch == null || selectors == null || evidenceRefs == null) throw sourceUnconfirmed();
            if (selectors.stream().anyMatch(Objects::isNull)
                    || evidenceRefs.stream().anyMatch(Objects::isNull)) throw sourceUnconfirmed();
            selectors = List.copyOf(selectors);
            evidenceRefs = List.copyOf(evidenceRefs);
            if (selectors.isEmpty()
                    || selectors.stream().anyMatch(value -> value.targetType() != RecoverySelector.TargetType.STOCK)
                    || new HashSet<>(selectors).size() != selectors.size()
                    || evidenceRefs.isEmpty() || evidenceRefs.stream().anyMatch(value -> value.isBlank()
                    || value.chars().anyMatch(Character::isISOControl))
                    || new HashSet<>(evidenceRefs).size() != evidenceRefs.size()) throw sourceUnconfirmed();
        }
    }

    private record Candidate(Map<String, Object> row, byte[] canonical, CommittedKeyIndex.ContentDigest digest) {}
    private record DateInterval(LocalDate start, LocalDate end) {}

    private static final class ProcessingException extends TensorException {
        private ProcessingException(ErrorCode code, String message) { super(code, message); }
    }
}
