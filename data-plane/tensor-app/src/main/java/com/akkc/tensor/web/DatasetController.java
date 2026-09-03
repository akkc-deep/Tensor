package com.akkc.tensor.web;

import com.akkc.tensor.core.catalog.DatasetCatalog;
import com.akkc.tensor.core.query.DatasetPage;
import com.akkc.tensor.core.query.DatasetQueryService;
import com.akkc.tensor.core.query.QueryCriteria;
import com.akkc.tensor.plugin.api.dataset.DatasetDefinition;
import com.akkc.tensor.plugin.api.error.ErrorCode;
import com.akkc.tensor.plugin.api.error.TensorException;
import com.akkc.tensor.plugin.api.model.ApiName;
import com.akkc.tensor.plugin.api.model.DatasetKey;
import com.akkc.tensor.plugin.api.model.PluginId;
import com.akkc.tensor.web.dto.PageResponse;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/data-sources")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public final class DatasetController {
    private static final Set<String> SUPPORTED_FILTERS =
            Set.of("ts_code", "trade_date", "ann_date");

    private final DatasetCatalog datasetCatalog;
    private final DatasetQueryService datasetQueryService;

    public DatasetController(
            DatasetCatalog datasetCatalog,
            DatasetQueryService datasetQueryService) {
        this.datasetCatalog = Objects.requireNonNull(datasetCatalog, "datasetCatalog");
        this.datasetQueryService =
                Objects.requireNonNull(datasetQueryService, "datasetQueryService");
    }

    @GetMapping("/{pluginId}/datasets/{apiName}/records")
    public PageResponse listDatasetRecords(
            @PathVariable("pluginId") String pluginId,
            @PathVariable("apiName") String apiName,
            @RequestParam(value = "tsCode", required = false) String tsCode,
            @RequestParam(value = "tradeDateFrom", required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate tradeDateFrom,
            @RequestParam(value = "tradeDateTo", required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate tradeDateTo,
            @RequestParam(value = "annDateFrom", required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate annDateFrom,
            @RequestParam(value = "annDateTo", required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate annDateTo,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "pageSize", defaultValue = "50") int pageSize) {
        DatasetKey key = key(pluginId, apiName);
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
                    tsCode,
                    tradeDateFrom,
                    tradeDateTo,
                    annDateFrom,
                    annDateTo,
                    page,
                    pageSize);
        } catch (IllegalArgumentException exception) {
            throw new InvalidQueryException();
        }

        String requestId = MDC.get(RequestIdFilter.MDC_KEY);
        if (requestId == null) {
            throw new IllegalStateException("Request ID is unavailable");
        }
        DatasetPage result;
        try {
            result = datasetQueryService.query(key, criteria);
        } catch (IllegalArgumentException exception) {
            throw new DatasetQueryAccessException();
        }
        return PageResponse.from(requestId, key, result);
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
