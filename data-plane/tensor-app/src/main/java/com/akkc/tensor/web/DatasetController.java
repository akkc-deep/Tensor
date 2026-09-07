package com.akkc.tensor.web;

import com.akkc.tensor.core.catalog.DatasetCatalog;
import com.akkc.tensor.core.query.DatasetPage;
import com.akkc.tensor.core.query.DatasetQueryService;
import com.akkc.tensor.core.query.QueryCriteria;
import com.akkc.tensor.observability.OperationLogger;
import com.akkc.tensor.plugin.api.dataset.DatasetDefinition;
import com.akkc.tensor.plugin.api.error.ErrorCode;
import com.akkc.tensor.plugin.api.error.TensorException;
import com.akkc.tensor.plugin.api.model.ApiName;
import com.akkc.tensor.plugin.api.model.DatasetKey;
import com.akkc.tensor.plugin.api.model.PluginId;
import com.akkc.tensor.web.dto.PageResponse;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.HttpStatus;
import org.springframework.util.MultiValueMap;
import org.springframework.util.NumberUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestController
@RequestMapping("/api/v1/data-sources")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public final class DatasetController {
    private static final Set<String> SUPPORTED_PARAMETERS = Set.of(
            "tsCode", "tradeDateFrom", "tradeDateTo", "annDateFrom", "annDateTo", "page", "pageSize");
    private static final Set<String> SUPPORTED_FILTERS =
            Set.of("ts_code", "trade_date", "ann_date");

    private final DatasetCatalog datasetCatalog;
    private final DatasetQueryService datasetQueryService;
    private final OperationLogger operationLogger;

    public DatasetController(
            DatasetCatalog datasetCatalog,
            DatasetQueryService datasetQueryService,
            OperationLogger operationLogger) {
        this.datasetCatalog = Objects.requireNonNull(datasetCatalog, "datasetCatalog");
        this.datasetQueryService =
                Objects.requireNonNull(datasetQueryService, "datasetQueryService");
        this.operationLogger = Objects.requireNonNull(operationLogger, "operationLogger");
    }

    @GetMapping("/{pluginId}/datasets/{apiName}/records")
    public PageResponse listDatasetRecords(
            @PathVariable("pluginId") String pluginId,
            @PathVariable("apiName") String apiName,
            @RequestParam(value = "tsCode", required = false) String tsCode,
            @RequestParam MultiValueMap<String, String> parameters) {
        String tradeDateFromValue = parameters.getFirst("tradeDateFrom");
        String tradeDateToValue = parameters.getFirst("tradeDateTo");
        String annDateFromValue = parameters.getFirst("annDateFrom");
        String annDateToValue = parameters.getFirst("annDateTo");
        DatasetKey key = key(pluginId, apiName);
        String requestId = MDC.get(RequestIdFilter.MDC_KEY);
        if (requestId == null) {
            throw new IllegalStateException("Request ID is unavailable");
        }
        List<String> filterNames = new ArrayList<>();
        if (tsCode != null) {
            filterNames.add("ts_code");
        }
        if (StringUtils.hasText(tradeDateFromValue) || StringUtils.hasText(tradeDateToValue)) {
            filterNames.add("trade_date");
        }
        if (StringUtils.hasText(annDateFromValue) || StringUtils.hasText(annDateToValue)) {
            filterNames.add("ann_date");
        }
        Integer page = pageNumber(parameters.get("page"), 1);
        Integer pageSize = pageNumber(parameters.get("pageSize"), 50);
        return operationLogger.query(key, filterNames, page, pageSize, () -> {
            LocalDate tradeDateFrom = date(tradeDateFromValue, "tradeDateFrom");
            LocalDate tradeDateTo = date(tradeDateToValue, "tradeDateTo");
            LocalDate annDateFrom = date(annDateFromValue, "annDateFrom");
            LocalDate annDateTo = date(annDateToValue, "annDateTo");
            if (page == null) {
                throw typeMismatch("page", int.class);
            }
            if (pageSize == null) {
                throw typeMismatch("pageSize", int.class);
            }
            if (!SUPPORTED_PARAMETERS.containsAll(parameters.keySet())) {
                throw new InvalidQueryException();
            }
            DatasetDefinition definition = datasetCatalog.find(key)
                    .orElseThrow(DatasetQueryAccessException::new);
            Set<String> filters = definition.filters().stream()
                    .map(filter -> filter.field())
                    .collect(Collectors.toUnmodifiableSet());
            if (!SUPPORTED_FILTERS.containsAll(filters)) {
                throw new DatasetQueryAccessException();
            }
            if ((tsCode != null && !filters.contains("ts_code"))
                    || ((tradeDateFrom != null || tradeDateTo != null)
                            && !filters.contains("trade_date"))
                    || ((annDateFrom != null || annDateTo != null)
                            && !filters.contains("ann_date"))) {
                throw new InvalidQueryException();
            }

            QueryCriteria criteria;
            try {
                criteria = new QueryCriteria(
                        tsCode, tradeDateFrom, tradeDateTo, annDateFrom, annDateTo, page, pageSize);
            } catch (IllegalArgumentException exception) {
                throw new InvalidQueryException();
            }
            try {
                DatasetPage result = datasetQueryService.query(key, criteria);
                return PageResponse.from(requestId, key, result);
            } catch (IllegalArgumentException exception) {
                throw new DatasetQueryAccessException();
            }
        });
    }

    private static Integer pageNumber(List<String> values, int defaultValue) {
        if (values == null || (values.size() == 1 && values.getFirst().isEmpty())) {
            return defaultValue;
        }
        try {
            return NumberUtils.parseNumber(values.getFirst(), Integer.class);
        } catch (IllegalArgumentException exception) {
            return null;
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

    private static DatasetKey key(String pluginId, String apiName) {
        try {
            return DatasetKey.of(PluginId.of(pluginId), ApiName.of(apiName));
        } catch (IllegalArgumentException exception) {
            throw new InvalidQueryException();
        }
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    private static final class InvalidQueryException extends TensorException {
        private InvalidQueryException() {
            super(ErrorCode.PARAM_INVALID, "Query parameters are invalid");
        }
    }

    @ResponseStatus(HttpStatus.CONFLICT)
    private static final class DatasetQueryAccessException extends TensorException {
        private DatasetQueryAccessException() {
            super(ErrorCode.DATASET_MISCONFIGURED, "Dataset metadata is unavailable");
        }
    }
}
