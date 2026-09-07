package com.akkc.tensor.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.akkc.tensor.core.download.DownloadService;
import com.akkc.tensor.core.query.DatasetPage;
import com.akkc.tensor.core.query.DatasetQueryService;
import com.akkc.tensor.core.query.QueryCriteria;
import com.akkc.tensor.observability.OperationLogger;
import com.akkc.tensor.plugin.api.download.DownloadOutcome;
import com.akkc.tensor.plugin.api.download.DownloadResult;
import com.akkc.tensor.plugin.api.model.ApiName;
import com.akkc.tensor.plugin.api.model.DatasetKey;
import com.akkc.tensor.plugin.api.model.PluginId;
import com.akkc.tensor.plugin.api.model.RequestId;
import com.akkc.tensor.web.download.DownloadParameterResolver;
import com.akkc.tensor.web.download.DownloadParameters.TradeDateParameters;
import com.akkc.tensor.web.dto.DatasetPath;
import com.akkc.tensor.web.dto.DatasetRecordsRequest;
import com.akkc.tensor.web.dto.DatasetRecordsRequest.DateRange;
import com.akkc.tensor.web.dto.DatasetRecordsRequest.Pagination;
import com.akkc.tensor.web.dto.DownloadRequest;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;

class ControllerUseCaseTest {
    private static final DatasetKey KEY = DatasetKey.of(PluginId.of("fixture"), ApiName.of("records"));
    private static final RequestId REQUEST_ID = new RequestId(
            UUID.fromString("c52bce3d-5aa5-4c8e-ae64-e73cb76d8f33"));
    private final OperationLogger operations = mock(OperationLogger.class);
    private final DatasetQueryService queries = mock(DatasetQueryService.class);
    private final DownloadService downloads = mock(DownloadService.class);
    private final DownloadParameterResolver parameters = mock(DownloadParameterResolver.class);

    @BeforeEach
    void requestId() {
        MDC.put(RequestIdFilter.MDC_KEY, REQUEST_ID.value().toString());
    }

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void convertsQueryInputAndLogsTheCoreResultAfterProjection() {
        QueryCriteria criteria = new QueryCriteria("000001.SZ", LocalDate.of(2026, 9, 7), null,
                null, null, 99, 20);
        DatasetPage page = new DatasetPage(List.of("ts_code"), List.of(Map.of("ts_code", "000001.SZ")),
                1, 20, 1, 1);
        when(queries.query(KEY, criteria)).thenReturn(page);

        var response = new DatasetController(queries, operations).listDatasetRecords(queryRequest());

        assertThat(response.requestId()).isEqualTo(REQUEST_ID.value().toString());
        assertThat(response.pluginId()).isEqualTo("fixture");
        assertThat(response.apiName()).isEqualTo("records");
        assertThat(response.page()).isEqualTo(1);
        assertThat(response.items()).containsExactly(Map.of("ts_code", "000001.SZ"));
        ArgumentCaptor<Duration> duration = ArgumentCaptor.forClass(Duration.class);
        verify(operations).recordQuerySuccess(eq(REQUEST_ID), eq(KEY), eq(criteria), eq(page), duration.capture());
        assertThat(duration.getValue().isNegative()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void neverLogsQuerySuccessWhenServiceOrProjectionFails(boolean projectionFailure) {
        RuntimeException failure = new IllegalStateException("query failure");
        if (projectionFailure) {
            when(queries.query(eq(KEY), any())).thenReturn(mock(DatasetPage.class));
        } else {
            when(queries.query(eq(KEY), any())).thenThrow(failure);
        }

        var thrown = assertThatThrownBy(() -> new DatasetController(queries, operations)
                .listDatasetRecords(queryRequest()));
        if (projectionFailure) {
            thrown.isInstanceOf(IllegalArgumentException.class);
        } else {
            thrown.isSameAs(failure);
        }
        verifyNoInteractions(operations);
    }

    @Test
    void convertsDownloadInputAndLogsTheCoreResultAfterProjection() {
        DownloadRequest request = downloadRequest();
        Map<String, Object> raw = Map.of("trade_date", "20260907");
        when(parameters.toRawValues(request.params(), request.suppliedFields())).thenReturn(raw);
        DownloadResult result = new DownloadResult(REQUEST_ID, DownloadOutcome.SUCCESS,
                KEY.pluginId(), KEY.apiName(), 3, 2, 1, "下载成功");
        when(downloads.execute(KEY.pluginId(), KEY.apiName(), raw, REQUEST_ID)).thenReturn(result);

        var response = new DownloadController(downloads, operations, parameters).download(request);

        assertThat(response.requestId()).isEqualTo(REQUEST_ID.value().toString());
        assertThat(response.outcome()).isEqualTo(DownloadOutcome.SUCCESS);
        assertThat(response.sourceRowCount()).isEqualTo(3);
        assertThat(response.insertedRows()).isEqualTo(2);
        assertThat(response.updatedRows()).isEqualTo(1);
        ArgumentCaptor<Duration> duration = ArgumentCaptor.forClass(Duration.class);
        verify(operations).recordDownloadSuccess(eq(REQUEST_ID), eq(KEY), eq(raw), eq(result), duration.capture());
        assertThat(duration.getValue().isNegative()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void neverLogsDownloadSuccessWhenServiceOrProjectionFails(boolean projectionFailure) {
        DownloadRequest request = downloadRequest();
        Map<String, Object> raw = Map.of("trade_date", "20260907");
        when(parameters.toRawValues(request.params(), request.suppliedFields())).thenReturn(raw);
        RuntimeException failure = new IllegalStateException("download failure");
        if (projectionFailure) {
            when(downloads.execute(KEY.pluginId(), KEY.apiName(), raw, REQUEST_ID))
                    .thenReturn(mock(DownloadResult.class));
        } else {
            when(downloads.execute(KEY.pluginId(), KEY.apiName(), raw, REQUEST_ID)).thenThrow(failure);
        }

        var thrown = assertThatThrownBy(() -> new DownloadController(downloads, operations, parameters)
                .download(request));
        if (projectionFailure) {
            thrown.isInstanceOf(NullPointerException.class);
        } else {
            thrown.isSameAs(failure);
        }
        verifyNoInteractions(operations);
    }

    private static DatasetRecordsRequest queryRequest() {
        return new DatasetRecordsRequest(new DatasetPath("fixture", "records"), " 000001.sz ",
                new DateRange("2026-09-07", null), new DateRange(null, null),
                new Pagination(List.of("99"), List.of("20")), Set.of("tsCode", "tradeDateFrom", "page", "pageSize"));
    }

    private static DownloadRequest downloadRequest() {
        return new DownloadRequest(KEY, new TradeDateParameters("20260907"), Set.of("trade_date"));
    }
}
