package com.akkc.tensor.plugin.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import com.akkc.tensor.plugin.api.dataset.DatasetDefinition;
import com.akkc.tensor.plugin.api.descriptor.PluginDescriptor;
import com.akkc.tensor.plugin.api.descriptor.PluginReadiness;
import com.akkc.tensor.plugin.api.download.AdaptedBatch;
import com.akkc.tensor.plugin.api.download.DownloadEnvelope;
import com.akkc.tensor.plugin.api.error.AdapterException;
import com.akkc.tensor.plugin.api.error.ErrorCode;
import com.akkc.tensor.plugin.api.error.SourceException;
import com.akkc.tensor.plugin.api.error.TensorException;
import com.akkc.tensor.plugin.api.model.ApiName;
import com.akkc.tensor.plugin.api.model.DatasetKey;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.time.Instant;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class PluginApiSurfaceTest {

    private static final Set<ErrorCode> SOURCE_CODES = EnumSet.of(
            ErrorCode.SOURCE_AUTH_FAILED,
            ErrorCode.SOURCE_PERMISSION_DENIED,
            ErrorCode.SOURCE_RATE_LIMITED,
            ErrorCode.SOURCE_UNAVAILABLE,
            ErrorCode.SOURCE_NETWORK_ERROR,
            ErrorCode.SOURCE_TIMEOUT,
            ErrorCode.SOURCE_PAYLOAD_INVALID);
    private static final Set<ErrorCode> ADAPTER_CODES = EnumSet.of(
            ErrorCode.ADAPTER_FIELD_MISSING,
            ErrorCode.ADAPTER_TYPE_INVALID);

    @Test
    void exposesExactDataSourcePluginMethods() throws Exception {
        assertThat(DataSourcePlugin.class.isInterface()).isTrue();
        assertThat(Modifier.isPublic(DataSourcePlugin.class.getModifiers())).isTrue();
        assertExactInterface(DataSourcePlugin.class, "descriptor", "download", "readiness");

        Method descriptor = DataSourcePlugin.class.getDeclaredMethod("descriptor");
        Method readiness = DataSourcePlugin.class.getDeclaredMethod("readiness");
        Method download = DataSourcePlugin.class.getDeclaredMethod("download", ApiName.class, Map.class);

        assertThat(descriptor.getReturnType()).isEqualTo(PluginDescriptor.class);
        assertThat(descriptor.getParameterTypes()).isEmpty();
        assertThat(readiness.getReturnType()).isEqualTo(PluginReadiness.class);
        assertThat(readiness.getParameterTypes()).isEmpty();
        assertThat(download.getReturnType()).isEqualTo(DownloadEnvelope.class);
        assertThat(download.getParameterTypes()).containsExactly(ApiName.class, Map.class);
        assertMapOfStringObject(download.getGenericParameterTypes()[1]);
    }

    @Test
    void exposesExactDatasetAdapterMethods() throws Exception {
        assertThat(DatasetAdapter.class.isInterface()).isTrue();
        assertThat(Modifier.isPublic(DatasetAdapter.class.getModifiers())).isTrue();
        assertExactInterface(DatasetAdapter.class, "adapt", "datasetKey", "definition");

        Method datasetKey = DatasetAdapter.class.getDeclaredMethod("datasetKey");
        Method definition = DatasetAdapter.class.getDeclaredMethod("definition");
        Method adapt = DatasetAdapter.class.getDeclaredMethod(
                "adapt", DownloadEnvelope.class, Instant.class);

        assertThat(datasetKey.getReturnType()).isEqualTo(DatasetKey.class);
        assertThat(datasetKey.getParameterTypes()).isEmpty();
        assertThat(definition.getReturnType()).isEqualTo(DatasetDefinition.class);
        assertThat(definition.getParameterTypes()).isEmpty();
        assertThat(adapt.getReturnType()).isEqualTo(AdaptedBatch.class);
        assertThat(adapt.getParameterTypes()).containsExactly(DownloadEnvelope.class, Instant.class);
    }

    @Test
    void exposesFrozenErrorCodesAndRetryableValues() throws Exception {
        assertThat(ErrorCode.values()).extracting(Enum::name).containsExactly(
                "PARAM_REQUIRED",
                "PARAM_INVALID",
                "PLUGIN_DISABLED",
                "DATASET_MISCONFIGURED",
                "SOURCE_AUTH_FAILED",
                "SOURCE_PERMISSION_DENIED",
                "SOURCE_RATE_LIMITED",
                "SOURCE_UNAVAILABLE",
                "SOURCE_NETWORK_ERROR",
                "SOURCE_TIMEOUT",
                "SOURCE_PAYLOAD_INVALID",
                "ADAPTER_FIELD_MISSING",
                "ADAPTER_TYPE_INVALID",
                "PERSISTENCE_FAILED",
                "QUERY_FAILED",
                "INTERNAL_ERROR");
        assertThat(ErrorCode.values()).extracting(ErrorCode::retryable).containsExactly(
                false, false, false, false, false, false, true, true,
                true, true, true, false, false, true, true, false);

        assertThat(ErrorCode.class.getDeclaredConstructors()).hasSize(1);
        assertThat(ErrorCode.class.getDeclaredMethod("retryable").getReturnType())
                .isEqualTo(boolean.class);
        assertThat(publicDeclaredMethodNames(ErrorCode.class))
                .containsExactlyInAnyOrder("retryable", "valueOf", "values");
    }

    @Test
    void exposesMinimalTensorExceptionShape() throws Exception {
        assertThat(TensorException.class.getSuperclass()).isEqualTo(RuntimeException.class);
        assertThat(Modifier.isAbstract(TensorException.class.getModifiers())).isTrue();

        Constructor<?>[] constructors = TensorException.class.getDeclaredConstructors();
        assertThat(constructors).hasSize(1);
        assertThat(constructors[0].getParameterTypes()).containsExactly(ErrorCode.class, String.class);
        assertThat(Modifier.isProtected(constructors[0].getModifiers())).isTrue();

        Field[] fields = Arrays.stream(TensorException.class.getDeclaredFields())
                .filter(field -> !field.isSynthetic())
                .toArray(Field[]::new);
        assertThat(fields).hasSize(1);
        assertThat(fields[0].getName()).isEqualTo("code");
        assertThat(fields[0].getType()).isEqualTo(ErrorCode.class);
        assertThat(Modifier.isPrivate(fields[0].getModifiers())).isTrue();
        assertThat(Modifier.isFinal(fields[0].getModifiers())).isTrue();

        assertFinalAccessor("code", ErrorCode.class);
        assertFinalAccessor("retryable", boolean.class);
        assertThat(publicDeclaredMethodNames(TensorException.class))
                .containsExactlyInAnyOrder("code", "retryable");
    }

    @Test
    void exposesMinimalFinalCategoryExceptions() {
        assertCategoryExceptionShape(SourceException.class);
        assertCategoryExceptionShape(AdapterException.class);
    }

    @Test
    void preservesAuthorizedSourceAndAdapterFailures() {
        for (ErrorCode code : SOURCE_CODES) {
            SourceException exception = new SourceException(code, " source failed ");
            assertThat(exception.code()).isEqualTo(code);
            assertThat(exception.getMessage()).isEqualTo(" source failed ");
            assertThat(exception.retryable()).isEqualTo(code.retryable());
        }
        for (ErrorCode code : ADAPTER_CODES) {
            AdapterException exception = new AdapterException(code, " adaptation failed ");
            assertThat(exception.code()).isEqualTo(code);
            assertThat(exception.getMessage()).isEqualTo(" adaptation failed ");
            assertThat(exception.retryable()).isEqualTo(code.retryable());
        }
    }

    @Test
    void rejectsCrossCategoryCodesAndInvalidMessages() {
        for (ErrorCode code : ErrorCode.values()) {
            if (!SOURCE_CODES.contains(code)) {
                assertThatIllegalArgumentException()
                        .isThrownBy(() -> new SourceException(code, "source failed"));
            }
            if (!ADAPTER_CODES.contains(code)) {
                assertThatIllegalArgumentException()
                        .isThrownBy(() -> new AdapterException(code, "adaptation failed"));
            }
        }

        assertThatNullPointerException().isThrownBy(() -> new SourceException(null, "source failed"));
        assertThatNullPointerException().isThrownBy(() -> new AdapterException(null, "adaptation failed"));
        assertThatNullPointerException()
                .isThrownBy(() -> new SourceException(ErrorCode.SOURCE_TIMEOUT, null));
        assertThatNullPointerException()
                .isThrownBy(() -> new AdapterException(ErrorCode.ADAPTER_TYPE_INVALID, null));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new SourceException(ErrorCode.SOURCE_TIMEOUT, ""));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new SourceException(ErrorCode.SOURCE_TIMEOUT, " \t"));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new AdapterException(ErrorCode.ADAPTER_TYPE_INVALID, ""));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new AdapterException(ErrorCode.ADAPTER_TYPE_INVALID, " \t"));
    }

    @Test
    void addsNoFrameworkOrSensitiveDiagnosticSurface() {
        List<Class<?>> types = List.of(
                DataSourcePlugin.class,
                DatasetAdapter.class,
                ErrorCode.class,
                TensorException.class,
                SourceException.class,
                AdapterException.class);
        String surface = types.stream()
                .flatMap(PluginApiSurfaceTest::declaredSurface)
                .map(String::toLowerCase)
                .reduce("", (left, right) -> left + " " + right);

        assertThat(surface).doesNotContain(
                "throwable",
                "http",
                "spring",
                "jdbc",
                "sql",
                "java.sql",
                "javax.sql",
                "jakarta.persistence",
                "java.net.http",
                "restclient",
                "jdbctemplate",
                "token",
                "credential",
                "response",
                "rawresponse",
                "requestheader",
                "requestid",
                "fielderrors",
                "stacktrace",
                "path");
    }

    private static void assertExactInterface(Class<?> type, String... methodNames) {
        assertThat(type.getDeclaredMethods()).extracting(Method::getName).containsExactlyInAnyOrder(methodNames);
        assertThat(type.getDeclaredMethods()).allSatisfy(method -> {
            assertThat(Modifier.isPublic(method.getModifiers())).isTrue();
            assertThat(Modifier.isAbstract(method.getModifiers())).isTrue();
            assertThat(Modifier.isStatic(method.getModifiers())).isFalse();
            assertThat(method.isDefault()).isFalse();
        });
    }

    private static void assertMapOfStringObject(Type type) {
        assertThat(type).isInstanceOf(ParameterizedType.class);
        ParameterizedType mapType = (ParameterizedType) type;
        assertThat(mapType.getRawType()).isEqualTo(Map.class);
        assertThat(mapType.getActualTypeArguments()).containsExactly(String.class, Object.class);
    }

    private static void assertFinalAccessor(String name, Class<?> returnType) throws Exception {
        Method method = TensorException.class.getDeclaredMethod(name);
        assertThat(method.getReturnType()).isEqualTo(returnType);
        assertThat(method.getParameterTypes()).isEmpty();
        assertThat(Modifier.isPublic(method.getModifiers())).isTrue();
        assertThat(Modifier.isFinal(method.getModifiers())).isTrue();
    }

    private static void assertCategoryExceptionShape(Class<? extends TensorException> type) {
        assertThat(type.getSuperclass()).isEqualTo(TensorException.class);
        assertThat(Modifier.isFinal(type.getModifiers())).isTrue();
        assertThat(type.getDeclaredConstructors()).hasSize(1).allSatisfy(constructor -> {
            assertThat(constructor.getParameterTypes()).containsExactly(ErrorCode.class, String.class);
            assertThat(Modifier.isPublic(constructor.getModifiers())).isTrue();
        });
        assertThat(Arrays.stream(type.getDeclaredFields()).filter(field -> !field.isSynthetic())).isEmpty();
        assertThat(Arrays.stream(type.getDeclaredMethods()).filter(method -> Modifier.isPublic(method.getModifiers())))
                .isEmpty();
    }

    private static Stream<String> declaredSurface(Class<?> type) {
        Stream<String> fields = Arrays.stream(type.getDeclaredFields())
                .flatMap(field -> Stream.of(
                        memberName(field), field.getType().getName(), field.getGenericType().getTypeName()));
        Stream<String> constructors = Arrays.stream(type.getDeclaredConstructors())
                .flatMap(constructor -> Stream.of(
                                Stream.of(memberName(constructor)),
                                Arrays.stream(constructor.getGenericParameterTypes()).map(Type::getTypeName),
                                Arrays.stream(constructor.getExceptionTypes()).map(Class::getName))
                        .flatMap(stream -> stream));
        Stream<String> methods = Arrays.stream(type.getDeclaredMethods())
                .flatMap(method -> Stream.of(
                                Stream.of(memberName(method), method.getGenericReturnType().getTypeName()),
                                Arrays.stream(method.getGenericParameterTypes()).map(Type::getTypeName),
                                Arrays.stream(method.getExceptionTypes()).map(Class::getName))
                        .flatMap(stream -> stream));
        return Stream.of(fields, constructors, methods).flatMap(stream -> stream);
    }

    private static List<String> publicDeclaredMethodNames(Class<?> type) {
        return Arrays.stream(type.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .map(Method::getName)
                .toList();
    }

    private static String memberName(Member member) {
        return member.getName();
    }
}
