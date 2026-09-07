package com.akkc.tensor.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.akkc.tensor.web.dto.DatasetPath;
import com.akkc.tensor.web.dto.DatasetRecordsRequest;
import java.lang.reflect.Method;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.servlet.HandlerMapping;

class DatasetRequestArgumentResolverTest {
    private final DatasetRequestArgumentResolver resolver =
            new DatasetRequestArgumentResolver();

    @Test
    void supportsOnlyDatasetRequestTypes() throws Exception {
        assertThat(resolver.supportsParameter(parameter("path", DatasetPath.class))).isTrue();
        assertThat(resolver.supportsParameter(
                parameter("records", DatasetRecordsRequest.class))).isTrue();
        assertThat(resolver.supportsParameter(parameter("other", String.class))).isFalse();
    }

    @Test
    void resolvesPathsOnlyFromUriVariablesAndPreservesRawQueryValues() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE,
                Map.of("pluginId", "path_plugin", "apiName", "path_api"));
        request.addParameter("pluginId", "query_plugin");
        request.addParameter("apiName", "query_api");
        request.addParameter("tsCode", "000001.SZ", "000002.SZ");
        request.addParameter("tsCode[]", "ignored");
        request.addParameter("tradeDateFrom", "2026-08-07", "2026-08-08");
        request.addParameter("tradeDateTo", "2026-08-09");
        request.addParameter("annDateFrom", "");
        request.addParameter("page", "", "2");
        request.addParameter("pageSize", "20", "50");
        ServletWebRequest webRequest = new ServletWebRequest(request);

        DatasetPath path = (DatasetPath) resolver.resolveArgument(
                parameter("path", DatasetPath.class), null, webRequest, null);
        DatasetRecordsRequest records = (DatasetRecordsRequest) resolver.resolveArgument(
                parameter("records", DatasetRecordsRequest.class), null, webRequest, null);

        assertThat(path).isEqualTo(new DatasetPath("path_plugin", "path_api"));
        assertThat(records.path()).isEqualTo(path);
        assertThat(records.tsCode()).isEqualTo("000001.SZ,000002.SZ");
        assertThat(records.tradeDate()).isEqualTo(
                new DatasetRecordsRequest.DateRange("2026-08-07", "2026-08-09"));
        assertThat(records.annDate()).isEqualTo(
                new DatasetRecordsRequest.DateRange("", null));
        assertThat(records.pagination().page()).containsExactly("", "2");
        assertThat(records.pagination().pageSize()).containsExactly("20", "50");
        assertThat(records.parameterNames()).containsExactlyInAnyOrder(
                "pluginId", "apiName", "tsCode", "tsCode[]", "tradeDateFrom", "tradeDateTo",
                "annDateFrom", "page", "pageSize");
    }

    @Test
    void preservesSpringArraySuffixFallbackForTsCode() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE,
                Map.of("pluginId", "fixture", "apiName", "daily"));
        request.addParameter("tsCode[]", "000001.SZ", "000002.SZ");

        DatasetRecordsRequest records = (DatasetRecordsRequest) resolver.resolveArgument(
                parameter("records", DatasetRecordsRequest.class), null,
                new ServletWebRequest(request), null);

        assertThat(records.tsCode()).isEqualTo("000001.SZ,000002.SZ");
        assertThat(records.parameterNames()).containsExactly("tsCode[]");
    }

    private static MethodParameter parameter(String name, Class<?> type) throws Exception {
        Method method = Fixture.class.getDeclaredMethod(name, type);
        return new MethodParameter(method, 0);
    }

    private static final class Fixture {
        void path(DatasetPath path) {}

        void records(DatasetRecordsRequest request) {}

        void other(String value) {}
    }
}
