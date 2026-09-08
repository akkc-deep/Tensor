package com.akkc.tensor.core.download;

import com.akkc.tensor.core.validation.ParameterValidator;
import com.akkc.tensor.core.validation.ParameterValidator.ParameterValidationException;
import com.akkc.tensor.core.validation.ValidatedParameters;
import com.akkc.tensor.plugin.api.descriptor.ApiDescriptor;
import com.akkc.tensor.plugin.api.descriptor.ParameterType;
import com.akkc.tensor.plugin.api.download.DownloadPolicy;
import com.akkc.tensor.plugin.api.download.RecoveryPolicy;
import com.akkc.tensor.plugin.api.download.RecoverySelector;
import com.akkc.tensor.plugin.api.download.SourceParameterMapper;
import com.akkc.tensor.plugin.api.error.ErrorCode;
import com.akkc.tensor.plugin.api.error.SourceException;
import com.akkc.tensor.plugin.api.error.TensorException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Freezes display conditions and reconstructs candidate requests; it never authorizes execution. */
public final class DownloadParameterConverter {
    private final ParameterValidator validator;

    public DownloadParameterConverter(ParameterValidator validator) {
        this.validator = Objects.requireNonNull(validator, "validator");
    }

    public ValidatedParameters bindInitial(ApiDescriptor api, Map<String, Object> raw) {
        return validator.validate(api, raw);
    }

    public Map<String, Object> taskParameters(ApiDescriptor api, ValidatedParameters original,
            RecoverySelector.TargetType targetType) {
        var values = new LinkedHashMap<>(validator.validate(api, original.values()).values());
        if (targetType == null) throw incompatible();
        if (targetType == RecoverySelector.TargetType.STOCK) {
            var recovery = api.downloadPolicy().recoveryPolicy();
            if (recovery.mode() != RecoveryPolicy.Mode.STOCK_TIME || !recovery.independentRecoveryVerified()
                    || api.sourceParameters().stream().noneMatch(p -> p.name().equals("ts_code") && p.type() == ParameterType.TS_CODE)) {
                throw incompatible();
            }
            values.remove("ts_code");
        }
        return Map.copyOf(values);
    }

    public MappedInput mapInitial(ApiDescriptor api, ValidatedParameters original, RecoverySelector selector) {
        try {
            checkInitialScope(api, original, selector);
            return reconstruct(api, taskParameters(api, original, selector.targetType()), selector);
        } catch (IllegalArgumentException | ParameterValidationException failure) {
            throw new SourceException(ErrorCode.SOURCE_REQUEST_UNCONFIRMED, "Source request conditions are unconfirmed");
        }
    }

    public MappedInput mapRetry(ApiDescriptor api, Map<String, Object> taskParams, RecoverySelector selector) {
        try {
            return reconstruct(api, taskParams, selector);
        } catch (IllegalArgumentException | ParameterValidationException failure) {
            throw new InvalidSavedParametersException();
        }
    }

    private MappedInput reconstruct(ApiDescriptor api, Map<String, Object> taskParams, RecoverySelector selector) {
        if (taskParams == null || selector == null) throw incompatible();
        var common = new LinkedHashMap<>(taskParams);
        OriginalDateRange display = originalDateRange(api, common);
        common.remove("start_date");
        common.remove("end_date");
        var mapped = SourceParameterMapper.map(api, common, selector);
        return new MappedInput(validator.validate(mapped.descriptors(), mapped.values()), display);
    }

    private OriginalDateRange originalDateRange(ApiDescriptor api, Map<String, Object> values) {
        boolean start = values.containsKey("start_date");
        boolean end = values.containsKey("end_date");
        if (!start && !end) return null;
        if (!start || !end || api.downloadPolicy().mode() == DownloadPolicy.Mode.ORIGINAL_PARAMS) throw incompatible();
        var dates = new LinkedHashMap<String, Object>();
        dates.put("start_date", values.get("start_date"));
        dates.put("end_date", values.get("end_date"));
        var descriptors = api.parameters().stream()
                .filter(p -> p.name().equals("start_date") || p.name().equals("end_date")).toList();
        var validated = validator.validate(descriptors, dates).values();
        return new OriginalDateRange(date((String) validated.get("start_date")), date((String) validated.get("end_date")));
    }

    private void checkInitialScope(ApiDescriptor api, ValidatedParameters original, RecoverySelector selector) {
        if (selector == null) throw incompatible();
        var display = originalDateRange(api, original.values());
        if (selector.targetType() == RecoverySelector.TargetType.STOCK && original.values().containsKey("ts_code")
                && !selector.targetValue().equals(original.values().get("ts_code"))) throw incompatible();
        if (display == null) {
            if (api.downloadPolicy().mode() != DownloadPolicy.Mode.ORIGINAL_PARAMS
                    || selector.timeType() != RecoverySelector.TimeType.NONE) throw incompatible();
            return;
        }
        boolean contained = switch (selector.timeType()) {
            case DATE -> display.contains(LocalDate.parse(selector.timeValue()));
            case RANGE -> {
                var endpoints = selector.timeValue().split("/");
                yield display.contains(LocalDate.parse(endpoints[0])) && display.contains(LocalDate.parse(endpoints[1]));
            }
            case MONTH -> {
                var month = YearMonth.parse(selector.timeValue());
                yield !month.isBefore(YearMonth.from(display.startDate())) && !month.isAfter(YearMonth.from(display.endDate()));
            }
            case NONE -> false;
        };
        if (!contained) throw incompatible();
    }

    private static LocalDate date(String value) {
        return LocalDate.parse(value, DateTimeFormatter.BASIC_ISO_DATE);
    }

    private static IllegalArgumentException incompatible() {
        return new IllegalArgumentException("Download parameters are incompatible");
    }

    public record OriginalDateRange(LocalDate startDate, LocalDate endDate) {
        public OriginalDateRange {
            if (startDate == null || endDate == null || startDate.getYear() < 1 || endDate.getYear() > 9999
                    || endDate.isBefore(startDate) || ChronoUnit.DAYS.between(startDate, endDate) + 1 > 31) throw incompatible();
        }
        private boolean contains(LocalDate date) {
            return !date.isBefore(startDate) && !date.isAfter(endDate);
        }
    }

    public record MappedInput(ValidatedParameters sourceParams, OriginalDateRange originalDateRange) {
        public MappedInput { Objects.requireNonNull(sourceParams, "sourceParams"); }
    }

    private static final class InvalidSavedParametersException extends TensorException {
        private InvalidSavedParametersException() {
            super(ErrorCode.RETRY_TASK_INVALID, "Saved download parameters are incompatible");
        }
    }
}
