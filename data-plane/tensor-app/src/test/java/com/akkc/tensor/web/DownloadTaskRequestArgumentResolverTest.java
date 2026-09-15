package com.akkc.tensor.web;

import static org.assertj.core.api.Assertions.*;
import com.akkc.tensor.plugin.api.error.ErrorCode;
import com.akkc.tensor.web.download.DownloadBindingException;
import com.akkc.tensor.web.dto.DownloadTaskQuery;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.MethodParameter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.servlet.HandlerMapping;

class DownloadTaskRequestArgumentResolverTest {
    private final DownloadTaskRequestArgumentResolver resolver = new DownloadTaskRequestArgumentResolver();

    @Test
    void usesDefaultsAndPreservesIndependentFiltersAndBeyondTailPage() throws Exception {
        var request = new MockHttpServletRequest();
        var defaults = (DownloadTaskQuery.Tasks) resolve("tasks", DownloadTaskQuery.Tasks.class, request);
        assertThat(defaults.page()).isEqualTo(1);
        assertThat(defaults.pageSize()).isEqualTo(20);
        request.addParameter("page", "2147483647");
        request.addParameter("pageSize", "100");
        request.addParameter("apiName", "daily");
        request.addParameter("status", "FAILED");
        request.addParameter("submissionId", "ABCDEF00-1234-1234-1234-123456789ABC");
        var query = (DownloadTaskQuery.Tasks) resolve("tasks", DownloadTaskQuery.Tasks.class, request);
        assertThat(query.page()).isEqualTo(Integer.MAX_VALUE);
        assertThat(query.pageSize()).isEqualTo(100);
        assertThat(query.filter().pluginId()).isNull();
        assertThat(query.filter().apiName()).isEqualTo("daily");
        assertThat(query.filter().submissionId().toString()).isEqualTo("abcdef00-1234-1234-1234-123456789abc");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "+1", "-1", "0", "1.0", "1e2", "2147483648", "１２"})
    void rejectsInvalidPageWithoutCoercion(String value) {
        var request = new MockHttpServletRequest(); request.addParameter("page", value);
        invalid(() -> resolve("tasks", DownloadTaskQuery.Tasks.class, request));
    }

    @ParameterizedTest
    @ValueSource(strings = {"pageSize=10", "pageSize=020", "status=failed", "submissionId=1-1-1-1-1",
            "pluginId=INVALID", "apiName=", "includeSplit=true", "page[]=1", "extra=secret"})
    void rejectsInvalidFiltersAndUnknownQueryFields(String parameter) {
        var pair = parameter.split("=", -1);
        var request = new MockHttpServletRequest(); request.addParameter(pair[0], pair[1]);
        invalid(() -> resolve("tasks", DownloadTaskQuery.Tasks.class, request));
    }

    @Test
    void rejectsDuplicateEvenEquivalentQueryValues() {
        var request = new MockHttpServletRequest(); request.addParameter("page", "1", "1");
        invalid(() -> resolve("tasks", DownloadTaskQuery.Tasks.class, request));
    }

    @Test
    void splitFilterIsIndependentAndUuidIsStrict() throws Exception {
        var request = path("ABCDEF00-1234-1234-1234-123456789ABC");
        request.addParameter("status", "SPLIT");
        var query = (DownloadTaskQuery.Batches) resolve("batches", DownloadTaskQuery.Batches.class, request);
        assertThat(query.filter().includeSplit()).isFalse();
        assertThat(query.filter().status().name()).isEqualTo("SPLIT");
        request.addParameter("includeSplit", "true");
        assertThat(((DownloadTaskQuery.Batches) resolve("batches", DownloadTaskQuery.Batches.class, request))
                .filter().includeSplit()).isTrue();
        request.setParameter("includeSplit", "TRUE");
        invalid(() -> resolve("batches", DownloadTaskQuery.Batches.class, request));
        invalid(() -> resolve("task", DownloadTaskQuery.TaskId.class, path("1-1-1-1-1")));
    }

    @Test
    void detailCapabilityAndPostsRejectAnyQueryParameters() {
        var request = path("abcdef00-1234-1234-1234-123456789abc");
        request.addParameter("page", "1");
        invalid(() -> resolve("task", DownloadTaskQuery.TaskId.class, request));
        invalid(() -> resolve("empty", DownloadTaskQuery.NoQuery.class, request));
        invalid(() -> resolve("dataset", DownloadTaskQuery.Dataset.class, request));
    }

    private Object resolve(String name, Class<?> type, MockHttpServletRequest request) throws Exception {
        var parameter = new MethodParameter(getClass().getDeclaredMethod(name, type), 0);
        return resolver.resolveArgument(parameter, null, new ServletWebRequest(request), null);
    }
    private static MockHttpServletRequest path(String id) {
        var request = new MockHttpServletRequest();
        request.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE,
                Map.of("taskId", id, "pluginId", "tushare_pro", "apiName", "daily"));
        return request;
    }
    private static void invalid(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOfSatisfying(DownloadBindingException.class,
                failure -> assertThat(failure.code()).isEqualTo(ErrorCode.PARAM_INVALID));
    }
    void tasks(DownloadTaskQuery.Tasks query) {}
    void batches(DownloadTaskQuery.Batches query) {}
    void task(DownloadTaskQuery.TaskId query) {}
    void empty(DownloadTaskQuery.NoQuery query) {}
    void dataset(DownloadTaskQuery.Dataset query) {}
}
