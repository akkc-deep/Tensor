package com.akkc.tensor.core.validation;

import com.akkc.tensor.plugin.api.descriptor.ApiDescriptor;
import com.akkc.tensor.plugin.api.descriptor.ParameterDescriptor;
import com.akkc.tensor.plugin.api.descriptor.ParameterType;
import com.akkc.tensor.plugin.api.error.ErrorCode;
import com.akkc.tensor.plugin.api.error.TensorException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public final class ParameterValidator {
    private static final Pattern PARAMETER_NAME = Pattern.compile("^[a-z][a-z0-9_]{1,63}$");
    private static final Pattern TS_CODE = Pattern.compile("[A-Z0-9]+\\.[A-Z0-9]+");
    private static final DateTimeFormatter DATE = new DateTimeFormatterBuilder()
            .appendPattern("uuuuMMdd")
            .toFormatter(Locale.ROOT)
            .withResolverStyle(ResolverStyle.STRICT);
    private static final DateTimeFormatter MONTH = new DateTimeFormatterBuilder()
            .appendPattern("uuuuMM")
            .toFormatter(Locale.ROOT)
            .withResolverStyle(ResolverStyle.STRICT);

    public ValidatedParameters validate(ApiDescriptor api, Map<String, Object> raw) {
        Objects.requireNonNull(api, "api");
        Objects.requireNonNull(raw, "raw");
        List<ParameterDescriptor> parameters = api.parameters();
        Metadata metadata = validateMetadata(parameters);
        Map<String, String> normalized = new LinkedHashMap<>();
        Set<String> invalid = new HashSet<>();
        List<FieldError> requiredErrors = new ArrayList<>();

        for (ParameterDescriptor parameter : parameters) {
            Object value = raw.get(parameter.name());
            if (value == null || value instanceof String stringValue && stringValue.isBlank()) {
                applyMissing(parameter, metadata, normalized, requiredErrors);
            } else if (!(value instanceof String stringValue)) {
                invalid.add(parameter.name());
            } else {
                String result = normalize(parameter, stringValue, metadata.patterns().get(parameter.name()));
                if (result == null) {
                    invalid.add(parameter.name());
                } else if (parameter.type() == ParameterType.TEXT && result.isEmpty()) {
                    applyMissing(parameter, metadata, normalized, requiredErrors);
                } else {
                    normalized.put(parameter.name(), result);
                }
            }
        }

        if (!requiredErrors.isEmpty()) {
            throw new ParameterValidationException(ErrorCode.PARAM_REQUIRED, requiredErrors);
        }

        List<FieldError> invalidErrors = invalidRawKeys(raw, parameters);
        for (ParameterDescriptor parameter : parameters) {
            if (invalid.contains(parameter.name())) {
                invalidErrors.add(new FieldError(parameter.name(), "has invalid value"));
            }
        }
        appendRangeErrors(parameters, normalized, invalidErrors);
        if (!invalidErrors.isEmpty()) {
            throw new ParameterValidationException(ErrorCode.PARAM_INVALID, invalidErrors);
        }

        Map<String, Object> values = new LinkedHashMap<>();
        normalized.forEach(values::put);
        return new ValidatedParameters(values);
    }

    private static Metadata validateMetadata(List<ParameterDescriptor> parameters) {
        Map<String, ParameterDescriptor> byName = new HashMap<>();
        parameters.forEach(parameter -> byName.put(parameter.name(), parameter));
        Map<String, Pattern> patterns = new HashMap<>();
        Map<String, String> defaults = new HashMap<>();

        for (ParameterDescriptor parameter : parameters) {
            Pattern pattern = compilePattern(parameter);
            if (pattern != null) {
                patterns.put(parameter.name(), pattern);
            }
            if (parameter.defaultValue() != null) {
                String value = normalize(parameter, parameter.defaultValue(), pattern);
                if (value == null || parameter.type() == ParameterType.TEXT && value.isEmpty()) {
                    throw invalidMetadata(parameter);
                }
                defaults.put(parameter.name(), value);
            }
            if (parameter.type() == ParameterType.DATE_RANGE_MEMBER) {
                ParameterDescriptor related = byName.get(parameter.relatedParameter());
                if (related == null
                        || related.type() != ParameterType.DATE_RANGE_MEMBER
                        || !parameter.name().equals(related.relatedParameter())) {
                    throw invalidMetadata(parameter);
                }
            }
        }
        return new Metadata(Map.copyOf(patterns), Map.copyOf(defaults));
    }

    private static Pattern compilePattern(ParameterDescriptor parameter) {
        if (parameter.pattern() == null) {
            return null;
        }
        try {
            return Pattern.compile(parameter.pattern());
        } catch (PatternSyntaxException exception) {
            throw invalidMetadata(parameter);
        }
    }

    private static IllegalStateException invalidMetadata(ParameterDescriptor parameter) {
        return new IllegalStateException("Invalid parameter metadata: " + parameter.name());
    }

    private static void applyMissing(
            ParameterDescriptor parameter,
            Metadata metadata,
            Map<String, String> normalized,
            List<FieldError> requiredErrors) {
        String defaultValue = metadata.defaults().get(parameter.name());
        if (defaultValue != null) {
            normalized.put(parameter.name(), defaultValue);
        } else if (parameter.required()) {
            requiredErrors.add(new FieldError(parameter.name(), "is required"));
        }
    }

    private static String normalize(ParameterDescriptor parameter, String value, Pattern pattern) {
        String normalized;
        try {
            normalized = switch (parameter.type()) {
                case DATE, DATE_RANGE_MEMBER -> {
                    LocalDate.parse(value, DATE);
                    yield value;
                }
                case MONTH -> {
                    YearMonth.parse(value, MONTH);
                    yield value;
                }
                case TS_CODE -> {
                    String code = value.strip().toUpperCase(Locale.ROOT);
                    yield TS_CODE.matcher(code).matches() ? code : null;
                }
                case ENUM -> parameter.allowedValues().contains(value) ? value : null;
                case TEXT -> value.strip();
            };
        } catch (DateTimeParseException exception) {
            return null;
        }
        if (normalized == null || pattern != null && !pattern.matcher(normalized).matches()) {
            return null;
        }
        return normalized;
    }

    private static List<FieldError> invalidRawKeys(
            Map<String, Object> raw, List<ParameterDescriptor> parameters) {
        Set<String> declared = new HashSet<>();
        parameters.forEach(parameter -> declared.add(parameter.name()));
        boolean unsafe = false;
        Set<String> unknown = new TreeSet<>();
        for (Object rawKey : raw.keySet()) {
            if (!(rawKey instanceof String key) || !PARAMETER_NAME.matcher(key).matches()) {
                unsafe = true;
            } else if (!declared.contains(key)) {
                unknown.add(key);
            }
        }

        List<FieldError> errors = new ArrayList<>();
        if (unsafe) {
            errors.add(new FieldError("params", "contains an invalid field name"));
        }
        unknown.forEach(key -> errors.add(new FieldError(key, "is not declared")));
        return errors;
    }

    private static void appendRangeErrors(
            List<ParameterDescriptor> parameters,
            Map<String, String> normalized,
            List<FieldError> errors) {
        Map<String, Integer> positions = new HashMap<>();
        for (int index = 0; index < parameters.size(); index++) {
            positions.put(parameters.get(index).name(), index);
        }
        for (int index = 0; index < parameters.size(); index++) {
            ParameterDescriptor lower = parameters.get(index);
            if (lower.type() != ParameterType.DATE_RANGE_MEMBER
                    || index >= positions.get(lower.relatedParameter())) {
                continue;
            }
            String lowerValue = normalized.get(lower.name());
            String upperValue = normalized.get(lower.relatedParameter());
            if (lowerValue != null
                    && upperValue != null
                    && LocalDate.parse(lowerValue, DATE).isAfter(LocalDate.parse(upperValue, DATE))) {
                errors.add(new FieldError(
                        lower.name(), "must not be after " + lower.relatedParameter()));
            }
        }
    }

    private record Metadata(Map<String, Pattern> patterns, Map<String, String> defaults) {
    }

    public static final class ParameterValidationException extends TensorException {
        private final List<FieldError> fieldErrors;

        private ParameterValidationException(ErrorCode code, List<FieldError> fieldErrors) {
            super(requireCode(code), message(code));
            this.fieldErrors = List.copyOf(Objects.requireNonNull(fieldErrors, "fieldErrors"));
            if (this.fieldErrors.isEmpty()) {
                throw new IllegalArgumentException("fieldErrors must not be empty");
            }
        }

        public List<FieldError> fieldErrors() {
            return fieldErrors;
        }

        private static ErrorCode requireCode(ErrorCode code) {
            Objects.requireNonNull(code, "code");
            if (code != ErrorCode.PARAM_REQUIRED && code != ErrorCode.PARAM_INVALID) {
                throw new IllegalArgumentException("Unsupported parameter error code");
            }
            return code;
        }

        private static String message(ErrorCode code) {
            return code == ErrorCode.PARAM_REQUIRED
                    ? "Required parameters are missing"
                    : "Parameters are invalid";
        }
    }

    public record FieldError(String field, String message) {
        public FieldError {
            Objects.requireNonNull(field, "field");
            Objects.requireNonNull(message, "message");
            if (field.isBlank() || message.isBlank()) {
                throw new IllegalArgumentException("field and message must not be blank");
            }
        }
    }
}
