package com.akkc.tensor.web;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import com.akkc.tensor.core.download.*;
import com.akkc.tensor.core.retry.*;
import com.akkc.tensor.core.registry.*;
import com.akkc.tensor.core.validation.ParameterValidator;
import com.akkc.tensor.observability.OperationLogger;
import java.time.Clock;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class RetryTaskControllerTest {
    @Test void busyPrecedesMissingTaskAndBodyValidationPrecedesBusy() throws Exception {
        var storage = mock(RetryTaskStorageService.class);
        var plugins = new PluginRegistry(List.of());
        var adapters = new AdapterRegistry(List.of());
        var validator = new ParameterValidator();
        var slot = new DownloadExecutionSlot();
        var service = new RetryDownloadService(plugins, adapters, validator, mock(BatchCommitService.class), storage, slot, Clock.systemUTC());
        var queries = new RetryTaskQueryService(storage, plugins, adapters, validator, slot);
        var mvc = MockMvcBuilders.standaloneSetup(new RetryTaskController(queries, service, mock(OperationLogger.class)))
                .setControllerAdvice(new GlobalExceptionHandler()).addFilters(new RequestIdFilter()).build();
        try (var lease = slot.acquire(null)) {
            var path = "/api/v1/retry-tasks/" + UUID.randomUUID() + "/execute";
            assertThat(mvc.perform(post(path)).andReturn().getResponse().getStatus()).isEqualTo(409);
            for (String body : List.of("{}", "null", " ", "\n", "sentinel"))
                assertThat(mvc.perform(post(path).content(body)).andReturn().getResponse().getStatus()).isEqualTo(400);
            verifyNoInteractions(storage);
            assertThat(slot.busy()).isTrue();
        }
    }
    @Test void strictUuidAndEveryNonzeroBodyAreRejectedBeforeAnyReadOrExecution() throws Exception {
        var f = fixture();
        for (String id : List.of("1-1-1-1-1", "00000000-0000-0000-0000-00000000000Z", " 00000000-0000-0000-0000-000000000000")) {
            for (boolean execute : List.of(false, true)) {
                var request = execute ? post("/api/v1/retry-tasks/" + id + "/execute") : get("/api/v1/retry-tasks/" + id);
                var response = f.mvc().perform(request).andReturn().getResponse();
                assertThat(response.getStatus()).isEqualTo(400);
                assertThat(response.getContentAsString()).contains("taskId", "PARAM_INVALID");
            }
        }
        var id = UUID.randomUUID();
        for (String body : List.of("{}", "null", " ", "\n", "not JSON", "\u0000")) {
            var response = f.mvc().perform(post("/api/v1/retry-tasks/" + id + "/execute").contentType("application/octet-stream")
                    .content(body).with(request -> { request.addHeader("Transfer-Encoding", "chunked"); return request; })).andReturn().getResponse();
            assertThat(response.getStatus()).isEqualTo(400);
            assertThat(response.getContentAsString()).contains("request", "PARAM_INVALID");
        }
        var unknownLength = f.mvc().perform(context -> {
            var request = new org.springframework.mock.web.MockHttpServletRequest(context) {
                @Override public int getContentLength() { return -1; }
                @Override public long getContentLengthLong() { return -1; }
            };
            request.setMethod("POST"); request.setRequestURI("/api/v1/retry-tasks/" + id + "/execute");
            request.setContent(new byte[] {1}); request.addHeader("Transfer-Encoding", "chunked");
            return request;
        }).andReturn().getResponse();
        assertThat(unknownLength.getStatus()).isEqualTo(400);
        verifyNoInteractions(f.storage());
    }

    @Test void emptyBodyWithAnyMediaTypeAndUppercaseUuidReachesActualMissingTask() throws Exception {
        var f = fixture();
        var id = UUID.fromString("AABBCCDD-AABB-CCDD-EEFF-001122334455");
        when(f.storage().find(id)).thenReturn(Optional.empty());
        for (String type : List.of("application/json", "text/plain", "application/octet-stream")) {
            var response = f.mvc().perform(post("/api/v1/retry-tasks/" + id.toString().toUpperCase(Locale.ROOT) + "/execute").contentType(type))
                    .andReturn().getResponse();
            assertThat(response.getStatus()).isEqualTo(404);
            assertThat(response.getContentAsString()).contains("RETRY_TASK_NOT_FOUND");
        }
        try (var lease = f.slot().acquire(null)) {
            assertThat(f.mvc().perform(get("/api/v1/retry-tasks/" + id)).andReturn().getResponse().getStatus()).isEqualTo(404);
        }
        verify(f.storage(), times(4)).find(id);
        verifyNoMoreInteractions(f.storage());
    }

    @Test void listValidatesRawSingleValuesAndKeepsIndependentOfflineFilters() throws Exception {
        var f = fixture();
        for (String name : List.of("page", "pageSize")) {
            for (String bad : List.of("", "0", "-1", "1.5", "abc", "2147483648", " 20")) {
                var response = f.mvc().perform(get("/api/v1/retry-tasks").param(name, bad)).andReturn().getResponse();
                assertThat(response.getStatus()).isEqualTo(400);
                assertThat(response.getContentAsString()).contains("\"field\":\"" + name + "\"");
            }
            assertThat(f.mvc().perform(get("/api/v1/retry-tasks").param(name, "20", "20")).andReturn().getResponse().getStatus()).isEqualTo(400);
        }
        for (String name : List.of("pluginId", "apiName")) for (String bad : List.of("", "a", "BAD", "secret/value"))
            assertThat(f.mvc().perform(get("/api/v1/retry-tasks").param(name, bad)).andReturn().getResponse().getStatus()).isEqualTo(400);
        verifyNoInteractions(f.storage());
        for (int size : List.of(20, 50, 100)) for (int filter = 0; filter < 4; filter++) {
            var request = get("/api/v1/retry-tasks").param("pageSize", Integer.toString(size)).param("ignored", "value");
            var plugin = filter % 2 == 1 ? com.akkc.tensor.plugin.api.model.PluginId.of("removed") : null;
            var api = filter >= 2 ? com.akkc.tensor.plugin.api.model.ApiName.of("unknown") : null;
            if (plugin != null) request.param("pluginId", plugin.value());
            if (api != null) request.param("apiName", api.value());
            var criteria = new RetryTaskRepository.Criteria(plugin, api, 1, size);
            when(f.storage().list(criteria)).thenReturn(new RetryTaskRepository.Page(List.of(), 1, size, 0, 0));
            var response = f.mvc().perform(request).andReturn().getResponse();
            assertThat(response.getStatus()).isEqualTo(200);
            var json = RangeResponseContractTest.mapper().readTree(response.getContentAsString());
            assertThat(json.path("page").intValue()).isEqualTo(1);
            assertThat(json.path("items").isArray()).isTrue();
            assertThat(json.path("totalElements").isIntegralNumber()).isTrue();
            assertThat(json.path("requestId").asText()).isEqualTo(response.getHeader("X-Request-Id"));
            verify(f.storage()).list(criteria);
        }
    }
    private static Flow fixture() {
        var storage = mock(RetryTaskStorageService.class);
        var plugins = new PluginRegistry(List.of()); var adapters = new AdapterRegistry(List.of()); var validator = new ParameterValidator();
        var slot = new DownloadExecutionSlot();
        var service = new RetryDownloadService(plugins, adapters, validator, mock(BatchCommitService.class), storage, slot, Clock.systemUTC());
        var queries = new RetryTaskQueryService(storage, plugins, adapters, validator, slot);
        var mvc = MockMvcBuilders.standaloneSetup(new RetryTaskController(queries, service, mock(OperationLogger.class)))
                .setMessageConverters(new org.springframework.http.converter.json.MappingJackson2HttpMessageConverter(RangeResponseContractTest.mapper()))
                .setControllerAdvice(new GlobalExceptionHandler()).addFilters(new RequestIdFilter()).build();
        return new Flow(storage, slot, mvc);
    }
    private record Flow(RetryTaskStorageService storage, DownloadExecutionSlot slot, org.springframework.test.web.servlet.MockMvc mvc) {}

}
