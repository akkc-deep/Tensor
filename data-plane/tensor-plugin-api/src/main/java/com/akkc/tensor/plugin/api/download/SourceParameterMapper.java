package com.akkc.tensor.plugin.api.download;

import com.akkc.tensor.plugin.api.descriptor.ApiDescriptor;
import com.akkc.tensor.plugin.api.descriptor.ParameterDescriptor;
import com.akkc.tensor.plugin.api.descriptor.ParameterType;
import com.akkc.tensor.plugin.api.error.ErrorCode;
import com.akkc.tensor.plugin.api.error.SourceException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class SourceParameterMapper {
    private static final String INCOMPATIBLE = "Source parameters are incompatible";
    private static final Set<String> TIME_PARAMETERS =
            Set.of("start_date", "end_date", "trade_date", "ann_date", "month");

    private SourceParameterMapper() {}

    public static MappedParameters map(ApiDescriptor api, Map<String, Object> commonParams,
            RecoverySelector selector) {
        if (api == null) throw incompatible();
        DownloadPolicy policy = api.downloadPolicy();
        if (policy.requestEvidenceStatus() != DownloadPolicy.RequestEvidenceStatus.DOCUMENTED_CANDIDATE
                || policy.sourceRequestMode() == null) {
            throw new SourceException(ErrorCode.SOURCE_REQUEST_UNCONFIRMED,
                    "Source request conditions are unconfirmed");
        }
        if (commonParams == null || selector == null) throw incompatible();

        List<ParameterDescriptor> descriptors = policy.sourceRequestMode() == DownloadPolicy.SourceRequestMode.RANGE
                ? api.parameters() : api.sourceParameters();
        Set<String> allowed = descriptors.stream().map(ParameterDescriptor::name)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        var values = copyCommon(commonParams, allowed);

        mapTarget(api, selector, values);
        mapTime(policy, selector, values);
        if (!allowed.containsAll(values.keySet())) throw incompatible();
        return new MappedParameters(values, descriptors);
    }

    private static LinkedHashMap<String, Object> copyCommon(Map<String, Object> commonParams,
            Set<String> allowed) {
        var result = new LinkedHashMap<String, Object>();
        for (Map.Entry<?, ?> entry : commonParams.entrySet()) {
            if (!(entry.getKey() instanceof String key) || !validIdentifier(key)
                    || TIME_PARAMETERS.contains(key) || !allowed.contains(key)
                    || !(entry.getValue() instanceof String)) {
                throw incompatible();
            }
            result.put(key, entry.getValue());
        }
        return result;
    }

    private static void mapTarget(ApiDescriptor api, RecoverySelector selector,
            Map<String, Object> values) {
        RecoveryPolicy recovery = api.downloadPolicy().recoveryPolicy();
        if (selector.targetType() == RecoverySelector.TargetType.REQUEST) {
            return;
        }
        if (recovery.mode() != RecoveryPolicy.Mode.STOCK_TIME || !recovery.independentRecoveryVerified()
                || values.containsKey("ts_code") || api.sourceParameters().stream().noneMatch(parameter ->
                        parameter.name().equals("ts_code") && parameter.type() == ParameterType.TS_CODE)) {
            throw incompatible();
        }
        RecoverySelector.TimeType unit = recovery.unitTimeType();
        if (selector.timeType() != unit
                && !(unit == RecoverySelector.TimeType.RANGE
                && selector.timeType() == RecoverySelector.TimeType.DATE)) {
            throw incompatible();
        }
        put(values, "ts_code", selector.targetValue());
    }

    private static void mapTime(DownloadPolicy policy, RecoverySelector selector,
            Map<String, Object> values) {
        switch (policy.sourceRequestMode()) {
            case DATE -> {
                if (selector.timeType() != RecoverySelector.TimeType.DATE) throw incompatible();
                put(values, policy.sourceDateParameter(), compact(selector.timeValue()));
            }
            case MONTH -> {
                if (selector.timeType() != RecoverySelector.TimeType.MONTH) throw incompatible();
                put(values, policy.sourceDateParameter(), compact(selector.timeValue()));
            }
            case RANGE -> {
                if (selector.timeType() == RecoverySelector.TimeType.DATE) {
                    String date = compact(selector.timeValue());
                    put(values, "start_date", date);
                    put(values, "end_date", date);
                } else if (selector.timeType() == RecoverySelector.TimeType.RANGE) {
                    String[] dates = selector.timeValue().split("/", -1);
                    put(values, "start_date", compact(dates[0]));
                    put(values, "end_date", compact(dates[1]));
                } else {
                    throw incompatible();
                }
            }
            case NONE -> {
                if (selector.timeType() != RecoverySelector.TimeType.NONE) throw incompatible();
            }
        }
    }

    private static void put(Map<String, Object> values, String key, String value) {
        if (key == null || values.putIfAbsent(key, value) != null) throw incompatible();
    }

    private static String compact(String value) {
        return value.replace("-", "");
    }

    private static boolean validIdentifier(String value) {
        return value.matches("[a-z][a-z0-9_]{1,63}");
    }

    private static IllegalArgumentException incompatible() {
        return new IllegalArgumentException(INCOMPATIBLE);
    }

    public record MappedParameters(Map<String, Object> values,
            List<ParameterDescriptor> descriptors) {
        public MappedParameters {
            if (values == null || descriptors == null) throw incompatible();
            var copiedValues = new LinkedHashMap<String, Object>();
            for (Map.Entry<?, ?> entry : values.entrySet()) {
                if (!(entry.getKey() instanceof String key) || !validIdentifier(key)
                        || !(entry.getValue() instanceof String)) {
                    throw incompatible();
                }
                copiedValues.put(key, entry.getValue());
            }
            if (descriptors.stream().anyMatch(java.util.Objects::isNull)) throw incompatible();
            values = Collections.unmodifiableMap(copiedValues);
            descriptors = List.copyOf(descriptors);
        }
    }
}
