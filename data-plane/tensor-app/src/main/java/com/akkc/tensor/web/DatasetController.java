package com.akkc.tensor.web;

import com.akkc.tensor.core.query.DatasetPage;
import com.akkc.tensor.core.query.DatasetQueryService;
import com.akkc.tensor.core.query.QueryCriteria;
import com.akkc.tensor.observability.OperationLogger;
import com.akkc.tensor.plugin.api.model.ApiName;
import com.akkc.tensor.plugin.api.model.DatasetKey;
import com.akkc.tensor.plugin.api.model.PluginId;
import com.akkc.tensor.plugin.api.model.RequestId;
import com.akkc.tensor.web.dto.DatasetRecordsRequest;
import com.akkc.tensor.web.dto.PageResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.util.NumberUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestController
@RequestMapping("/api/v1/data-sources")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public final class DatasetController {
    private static final Set<String> SUPPORTED_PARAMETERS = Set.of(
            "tsCode", "tradeDateFrom", "tradeDateTo", "annDateFrom", "annDateTo", "page", "pageSize");

    private final DatasetQueryService datasetQueryService;
    private final OperationLogger operationLogger;

    public DatasetController(
            DatasetQueryService datasetQueryService,
            OperationLogger operationLogger) {
        this.datasetQueryService =
                Objects.requireNonNull(datasetQueryService, "datasetQueryService");
        this.operationLogger = Objects.requireNonNull(operationLogger, "operationLogger");
    }

    @GetMapping("/{pluginId}/datasets/{apiName}/records")
    public PageResponse listDatasetRecords(DatasetRecordsRequest request) {
        DatasetKey key = DatasetKey.of(
                PluginId.of(request.path().pluginId()), ApiName.of(request.path().apiName()));
        String value = MDC.get(RequestIdFilter.MDC_KEY);
        if (value == null) {
            throw new IllegalStateException("Request ID is unavailable");
        }
        RequestId requestId = new RequestId(UUID.fromString(value));
        LocalDate tradeDateFrom = date(request.tradeDate().from(), "tradeDateFrom");
        LocalDate tradeDateTo = date(request.tradeDate().to(), "tradeDateTo");
        LocalDate annDateFrom = date(request.annDate().from(), "annDateFrom");
        LocalDate annDateTo = date(request.annDate().to(), "annDateTo");
        int page = pageNumber(request.pagination().page(), 1, "page");
        int pageSize = pageNumber(request.pagination().pageSize(), 50, "pageSize");
        if (!SUPPORTED_PARAMETERS.containsAll(request.parameterNames())) {
            throw new IllegalArgumentException("Query parameters are invalid");
        }
        QueryCriteria criteria = new QueryCriteria(
                request.tsCode(), tradeDateFrom, tradeDateTo, annDateFrom, annDateTo, page, pageSize);
        long started = System.nanoTime();
        DatasetPage result = datasetQueryService.query(key, criteria);
        Duration duration = Duration.ofNanos(System.nanoTime() - started);
        PageResponse response = PageResponse.from(requestId.value().toString(), key, result);
        operationLogger.recordQuerySuccess(requestId, key, criteria, result, duration);
        return response;
    }

    private static int pageNumber(List<String> values, int defaultValue, String name) {
        if (values == null || (values.size() == 1 && values.getFirst().isEmpty())) {
            return defaultValue;
        }
        try {
            return NumberUtils.parseNumber(values.getFirst(), Integer.class);
        } catch (IllegalArgumentException exception) {
            throw typeMismatch(name, int.class);
        }
    }

    private static LocalDate date(String value, String name) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return LocalDate.parse(value, DateTimeFormatter.ISO_DATE);
        } catch (DateTimeParseException exception) {
            throw typeMismatch(name, LocalDate.class);
        }
    }

    private static MethodArgumentTypeMismatchException typeMismatch(String name, Class<?> type) {
        return new MethodArgumentTypeMismatchException(null, type, name, null, null);
    }

}
