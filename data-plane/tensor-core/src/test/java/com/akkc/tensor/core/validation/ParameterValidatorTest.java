package com.akkc.tensor.core.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.akkc.tensor.core.validation.ParameterValidator.FieldError;
import com.akkc.tensor.core.validation.ParameterValidator.ParameterValidationException;
import com.akkc.tensor.plugin.api.descriptor.ApiDescriptor;
import com.akkc.tensor.plugin.api.descriptor.ParameterDescriptor;
import com.akkc.tensor.plugin.api.descriptor.ParameterType;
import com.akkc.tensor.plugin.api.descriptor.QueryMode;
import com.akkc.tensor.plugin.api.error.ErrorCode;
import com.akkc.tensor.plugin.api.error.TensorException;
import com.akkc.tensor.plugin.api.model.ApiName;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ParameterValidatorTest {
    private final ParameterValidator validator = new ParameterValidator();

    @Test
    void normalizesAllParameterTypesInDescriptorOrderAndReturnsImmutableSnapshots() {
        ApiDescriptor api = api(
                parameter("text", ParameterType.TEXT, true, null, List.of(), null, null),
                parameter("date", ParameterType.DATE, true, null, List.of(), null, null),
                parameter("start", ParameterType.DATE_RANGE_MEMBER, true, null, List.of(), null, "end"),
                parameter("end", ParameterType.DATE_RANGE_MEMBER, true, null, List.of(), null, "start"),
                parameter("month", ParameterType.MONTH, true, null, List.of(), null, null),
                parameter("ts_code", ParameterType.TS_CODE, true, null, List.of(), null, null),
                parameter("state", ParameterType.ENUM, true, null, List.of("L", "P"), null, null));
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("state", "L");
        raw.put("ts_code", " 000001.sz ");
        raw.put("month", "202402");
        raw.put("end", "20240229");
        raw.put("start", "20240201");
        raw.put("date", "20240229");
        raw.put("text", "  alpha beta  ");

        ValidatedParameters result = validator.validate(api, raw);

        Map<String, Object> expected = new LinkedHashMap<>();
        expected.put("text", "alpha beta");
        expected.put("date", "20240229");
        expected.put("start", "20240201");
        expected.put("end", "20240229");
        expected.put("month", "202402");
        expected.put("ts_code", "000001.SZ");
        expected.put("state", "L");
        assertThat(result.values()).containsExactlyEntriesOf(expected);

        raw.put("text", "changed");
        assertThat(result.values().get("text")).isEqualTo("alpha beta");
        assertThatThrownBy(() -> result.values().put("new_key", "value"))
                .isInstanceOf(UnsupportedOperationException.class);

        Map<String, Object> directInput = new LinkedHashMap<>();
        directInput.put("alpha", "one");
        ValidatedParameters direct = new ValidatedParameters(directInput);
        directInput.put("alpha", "two");
        assertThat(direct.values()).containsExactly(Map.entry("alpha", "one"));
        assertThatThrownBy(() -> direct.values().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void omitsOptionalValuesAndNormalizesDefaultsAfterBlankTextReentersPresenceRules() {
        ApiDescriptor api = api(
                parameter("optional_text", ParameterType.TEXT, false, null, List.of(), null, null),
                parameter("default_code", ParameterType.TS_CODE, false, " 000001.sz ", List.of(), null, null),
                parameter("default_text", ParameterType.TEXT, false, "  fallback  ", List.of(), null, null),
                parameter("blank_optional", ParameterType.TEXT, false, null, List.of(), null, null));

        ValidatedParameters result = validator.validate(api, Map.of("blank_optional", " \u2003 "));

        assertThat(result.values()).containsExactly(
                Map.entry("default_code", "000001.SZ"),
                Map.entry("default_text", "fallback"));

        ApiDescriptor required = api(
                parameter("required_text", ParameterType.TEXT, true, null, List.of(), null, null));
        assertThatThrownBy(() -> validator.validate(required, Map.of("required_text", " \u2003 ")))
                .isInstanceOfSatisfying(ParameterValidationException.class, exception -> {
                    assertThat(exception.code()).isEqualTo(ErrorCode.PARAM_REQUIRED);
                    assertThat(exception.fieldErrors())
                            .containsExactly(new FieldError("required_text", "is required"));
                });
    }

    @Test
    void reportsEveryMissingRequiredFieldBeforeAnyInvalidField() {
        ApiDescriptor api = api(
                parameter("first_required", ParameterType.TEXT, true, null, List.of(), null, null),
                parameter("second_required", ParameterType.DATE, true, null, List.of(), null, null),
                parameter("declared_optional", ParameterType.TEXT, false, null, List.of(), null, null));
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("first_required", null);
        raw.put("second_required", " \t ");
        raw.put("declared_optional", 7);
        raw.put("unknown_field", "secret-value");

        assertThatThrownBy(() -> validator.validate(api, raw))
                .isInstanceOfSatisfying(ParameterValidationException.class, exception -> {
                    assertThat(exception.code()).isEqualTo(ErrorCode.PARAM_REQUIRED);
                    assertThat(exception.retryable()).isFalse();
                    assertThat(exception).hasMessage("Required parameters are missing").hasNoCause();
                    assertThat(exception.fieldErrors()).containsExactly(
                            new FieldError("first_required", "is required"),
                            new FieldError("second_required", "is required"));
                    assertThatThrownBy(() -> exception.fieldErrors().clear())
                            .isInstanceOf(UnsupportedOperationException.class);
                });
    }

    @Test
    void ordersUnknownAndDeclaredInvalidFieldsWithoutEchoingUnsafeKeysOrValues() {
        ApiDescriptor api = api(
                parameter("second", ParameterType.TEXT, false, null, List.of(), null, null),
                parameter("first", ParameterType.TEXT, false, null, List.of(), null, null));
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("zeta", "zeta-secret");
        raw.put(null, "null-key-secret");
        raw.put("BAD KEY", "unsafe-key-secret");
        raw.put("token", "credential-secret");
        raw.put("omega", "omega-secret");
        raw.put("first", 1);
        raw.put("second", false);

        assertThatThrownBy(() -> validator.validate(api, raw))
                .isInstanceOfSatisfying(ParameterValidationException.class, exception -> {
                    assertThat(exception.code()).isEqualTo(ErrorCode.PARAM_INVALID);
                    assertThat(exception).hasMessage("Parameters are invalid").hasNoCause();
                    assertThat(exception.fieldErrors()).containsExactly(
                            new FieldError("params", "contains an invalid field name"),
                            new FieldError("omega", "is not declared"),
                            new FieldError("token", "is not declared"),
                            new FieldError("zeta", "is not declared"),
                            new FieldError("second", "has invalid value"),
                            new FieldError("first", "has invalid value"));
                    assertThat(exception.getMessage()).doesNotContain("secret", "BAD KEY");
                    assertThat(exception.fieldErrors()).extracting(FieldError::message)
                            .allSatisfy(message -> assertThat(message).doesNotContain("secret", "BAD KEY"));
                });
    }

    @Test
    void normalizesValidSecurityCodesAndRejectsMalformedCodes() {
        ParameterDescriptor descriptor =
                parameter("ts_code", ParameterType.TS_CODE, true, null, List.of(), null, null);
        ApiDescriptor api = api(descriptor);

        assertThat(validator.validate(api, Map.of("ts_code", "000001.SZ")).values())
                .containsExactly(Map.entry("ts_code", "000001.SZ"));
        assertThat(validator.validate(api, Map.of("ts_code", "\u2003000001.sz\t")).values())
                .containsExactly(Map.entry("ts_code", "000001.SZ"));

        for (String invalid : List.of(
                "000001", ".SZ", "000001.", "000001.SZ.EXTRA", "000 001.SZ", "000001.S-Z")) {
            assertInvalid(descriptor, invalid);
        }
    }

    @Test
    void parsesDatesAndRangeMembersWithStrictRealCalendarSemantics() {
        ParameterDescriptor descriptor =
                parameter("trade_date", ParameterType.DATE, true, null, List.of(), null, null);
        ApiDescriptor api = api(descriptor);

        assertThat(validator.validate(api, Map.of("trade_date", "20240229")).values())
                .containsExactly(Map.entry("trade_date", "20240229"));

        for (String invalid : List.of(
                "2024-02-29", " 20240229", "20240229 ", "20240230", "20230229", "20241301")) {
            assertInvalid(descriptor, invalid);
        }
    }

    @Test
    void parsesMonthsWithStrictYearMonthSemantics() {
        ParameterDescriptor descriptor =
                parameter("month", ParameterType.MONTH, true, null, List.of(), null, null);
        ApiDescriptor api = api(descriptor);

        assertThat(validator.validate(api, Map.of("month", "202402")).values())
                .containsExactly(Map.entry("month", "202402"));

        for (String invalid : List.of("2024-02", " 202402", "202402 ", "20242", "202400", "202413")) {
            assertInvalid(descriptor, invalid);
        }
    }

    @Test
    void matchesEnumsExactlyAndAppliesPatternsToNormalizedWholeValues() {
        ApiDescriptor api = api(
                parameter("state", ParameterType.ENUM, true, null, List.of("L", "P"), null, null),
                parameter("ts_code", ParameterType.TS_CODE, true, null, List.of(), "[0-9]{6}\\.SZ", null),
                parameter("text", ParameterType.TEXT, true, null, List.of(), "ABC", null));

        assertThat(validator.validate(api, Map.of(
                        "state", "L", "ts_code", "000001.sz", "text", " ABC ")).values())
                .containsExactly(
                        Map.entry("state", "L"),
                        Map.entry("ts_code", "000001.SZ"),
                        Map.entry("text", "ABC"));

        assertInvalid(
                parameter("state", ParameterType.ENUM, true, null, List.of("L", "P"), null, null), "l");
        assertInvalid(
                parameter("text", ParameterType.TEXT, true, null, List.of(), "ABC", null), "XABCY");
    }

    @Test
    void checksEachReciprocalDateRangeOnceInDeclarationOrder() {
        ParameterDescriptor lower =
                parameter("lower", ParameterType.DATE_RANGE_MEMBER, false, null, List.of(), null, "upper");
        ParameterDescriptor upper =
                parameter("upper", ParameterType.DATE_RANGE_MEMBER, false, null, List.of(), null, "lower");
        ApiDescriptor api = api(lower, upper);

        assertThat(validator.validate(api, Map.of("lower", "20240101", "upper", "20240201")).values())
                .containsExactly(Map.entry("lower", "20240101"), Map.entry("upper", "20240201"));
        assertThat(validator.validate(api, Map.of("lower", "20240201", "upper", "20240201")).values())
                .containsExactly(Map.entry("lower", "20240201"), Map.entry("upper", "20240201"));
        assertThat(validator.validate(api, Map.of("lower", "20240101")).values())
                .containsExactly(Map.entry("lower", "20240101"));

        assertThatThrownBy(() -> validator.validate(
                        api, Map.of("lower", "20240202", "upper", "20240201")))
                .isInstanceOfSatisfying(ParameterValidationException.class, exception ->
                        assertThat(exception.fieldErrors()).containsExactly(
                                new FieldError("lower", "must not be after upper")));
        assertThatThrownBy(() -> validator.validate(
                        api, Map.of("lower", "20240230", "upper", "20240201")))
                .isInstanceOfSatisfying(ParameterValidationException.class, exception ->
                        assertThat(exception.fieldErrors()).containsExactly(
                                new FieldError("lower", "has invalid value")));
    }

    @Test
    void rejectsInvalidCallsMetadataAndPublicValueObjectsAtTheirBoundaries() throws Exception {
        assertThatNullPointerException().isThrownBy(() -> validator.validate(null, Map.of()));
        assertThatNullPointerException().isThrownBy(() -> validator.validate(api(), null));

        assertInvalidMetadata(
                parameter("bad_pattern", ParameterType.TEXT, false, null, List.of(), "[", null),
                "bad_pattern");
        assertInvalidMetadata(
                parameter("bad_default", ParameterType.DATE, false, "20240230", List.of(), null, null),
                "bad_default");
        ApiDescriptor badRange = api(
                parameter("range_start", ParameterType.DATE_RANGE_MEMBER, false, null, List.of(), null, "range_end"),
                parameter("range_end", ParameterType.TEXT, false, null, List.of(), null, "range_start"));
        assertThatThrownBy(() -> validator.validate(badRange, Map.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Invalid parameter metadata: range_start")
                .hasNoCause();

        assertThat(Modifier.isFinal(ParameterValidator.class.getModifiers())).isTrue();
        assertThat(ParameterValidator.class.getConstructors()).singleElement().satisfies(constructor ->
                assertThat(constructor.getParameterTypes()).isEmpty());
        Method validate = ParameterValidator.class.getDeclaredMethod("validate", ApiDescriptor.class, Map.class);
        assertThat(validate.getReturnType()).isEqualTo(ValidatedParameters.class);
        assertThat(Modifier.isPublic(validate.getModifiers())).isTrue();
        assertThat(ParameterValidator.class.getDeclaredMethods())
                .filteredOn(method -> Modifier.isPublic(method.getModifiers()))
                .containsExactly(validate);

        assertThat(ValidatedParameters.class.isRecord()).isTrue();
        assertThat(ValidatedParameters.class.getRecordComponents()).extracting(component -> component.getName())
                .containsExactly("values");
        assertThatNullPointerException().isThrownBy(() -> new ValidatedParameters(null));
        assertInvalidValidatedParameters(null, "value");
        assertInvalidValidatedParameters("alpha", null);
        assertInvalidValidatedParameters("alpha", 1);
        assertInvalidValidatedParameters("Bad", "value");

        assertThat(Modifier.isFinal(ParameterValidationException.class.getModifiers())).isTrue();
        assertThat(ParameterValidationException.class.getSuperclass()).isEqualTo(TensorException.class);
        assertThat(ParameterValidationException.class.getDeclaredFields()).extracting(field -> field.getName())
                .containsExactly("fieldErrors");
        Constructor<ParameterValidationException> exceptionConstructor =
                ParameterValidationException.class.getDeclaredConstructor(ErrorCode.class, List.class);
        assertThat(Modifier.isPrivate(exceptionConstructor.getModifiers())).isTrue();
        assertThat(ParameterValidationException.class.getDeclaredConstructors()).containsExactly(exceptionConstructor);
        exceptionConstructor.setAccessible(true);
        assertThatThrownBy(() -> exceptionConstructor.newInstance(
                        ErrorCode.INTERNAL_ERROR, List.of(new FieldError("field", "message"))))
                .isInstanceOf(InvocationTargetException.class)
                .hasCauseInstanceOf(IllegalArgumentException.class);

        assertThat(FieldError.class.isRecord()).isTrue();
        assertThat(FieldError.class.getRecordComponents()).extracting(component -> component.getName())
                .containsExactly("field", "message");
        assertThatNullPointerException().isThrownBy(() -> new FieldError(null, "message"));
        assertThatNullPointerException().isThrownBy(() -> new FieldError("field", null));
        assertThatThrownBy(() -> new FieldError(" ", "message")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FieldError("field", " ")).isInstanceOf(IllegalArgumentException.class);
    }

    private void assertInvalid(ParameterDescriptor descriptor, String rawValue) {
        assertThatThrownBy(() -> validator.validate(api(descriptor), Map.of(descriptor.name(), rawValue)))
                .isInstanceOfSatisfying(ParameterValidationException.class, exception -> {
                    assertThat(exception.code()).isEqualTo(ErrorCode.PARAM_INVALID);
                    assertThat(exception.fieldErrors())
                            .containsExactly(new FieldError(descriptor.name(), "has invalid value"));
                });
    }

    private void assertInvalidMetadata(ParameterDescriptor descriptor, String safeName) {
        assertThatThrownBy(() -> validator.validate(api(descriptor), Map.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Invalid parameter metadata: " + safeName)
                .hasNoCause();
    }

    private static void assertInvalidValidatedParameters(String key, Object value) {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put(key, value);
        assertThatThrownBy(() -> new ValidatedParameters(raw))
                .isInstanceOfAny(NullPointerException.class, IllegalArgumentException.class);
    }

    private static ApiDescriptor api(ParameterDescriptor... parameters) {
        return new ApiDescriptor(
                new ApiName("contract_api"), "Contract API", "test", QueryMode.snapshot, List.of(parameters));
    }

    private static ParameterDescriptor parameter(
            String name,
            ParameterType type,
            boolean required,
            String defaultValue,
            List<String> allowedValues,
            String pattern,
            String relatedParameter) {
        return new ParameterDescriptor(
                name, name, null, type, required, defaultValue, allowedValues, pattern, relatedParameter);
    }
}
