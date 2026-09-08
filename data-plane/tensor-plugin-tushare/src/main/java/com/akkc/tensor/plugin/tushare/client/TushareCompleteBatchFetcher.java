package com.akkc.tensor.plugin.tushare.client;

import com.akkc.tensor.plugin.api.dataset.DatasetDefinition;
import com.akkc.tensor.plugin.api.descriptor.ApiDescriptor;
import com.akkc.tensor.plugin.api.download.DownloadContext;
import com.akkc.tensor.plugin.api.download.DownloadEnvelope;
import com.akkc.tensor.plugin.api.download.DownloadPolicy;
import com.akkc.tensor.plugin.api.download.DownloadStatus;
import com.akkc.tensor.plugin.api.download.FetchBatch;
import com.akkc.tensor.plugin.api.download.FetchResult;
import com.akkc.tensor.plugin.api.model.ApiName;
import com.akkc.tensor.plugin.api.model.PluginId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

public final class TushareCompleteBatchFetcher {
    private static final Pattern IDENTIFIER = Pattern.compile("[a-z][a-z0-9_]{1,63}");
    private static final Set<String> RESERVED_PARAMETERS = Set.of(
            "ts_code", "trade_date", "ann_date", "month", "start_date", "end_date",
            "exchange", "exchange_id", "hs_type", "list_status");
    private final TushareProClient client;
    private final Map<ApiName, TushareBatchSource> sources;

    public TushareCompleteBatchFetcher(TushareProClient client, Map<ApiName, TushareBatchSource> sources) {
        if (client == null || sources == null
                || sources.entrySet().stream().anyMatch(entry -> entry.getKey() == null || entry.getValue() == null)) {
            throw new IllegalArgumentException("Invalid Tushare batch sources");
        }
        this.client = client;
        this.sources = Map.copyOf(sources);
    }

    public DownloadPolicy.BatchPlanning plan(
            DatasetDefinition definition, ApiDescriptor api, FetchBatch batch, DownloadContext context) {
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(api, "api");
        Objects.requireNonNull(batch, "batch");
        Objects.requireNonNull(context, "context").checkServerState();
        var advice = source(definition, api, batch).plan(definition, api, batch);
        if (advice == null || advice == DownloadPolicy.BatchPlanning.UNCONFIRMED) {
            throw TushareErrorClassifier.completenessUnconfirmed();
        }
        context.checkServerState();
        return advice;
    }

    private TushareBatchSource source(DatasetDefinition definition, ApiDescriptor api, FetchBatch batch) {
        if (!definition.datasetKey().apiName().equals(api.apiName())
                || !definition.datasetKey().pluginId().equals(PluginId.of("tushare_pro"))
                || !batch.recoveryPolicy().equals(api.downloadPolicy().recoveryPolicy())
                || api.downloadPolicy().requestEvidenceStatus()
                        != DownloadPolicy.RequestEvidenceStatus.DOCUMENTED_CANDIDATE
                || api.downloadPolicy().sourceRequestMode() == null
                || api.apiName().value().equals("trade_cal")
                        && "BSE".equals(batch.sourceParams().get("exchange"))) {
            throw TushareErrorClassifier.requestUnconfirmed();
        }
        TushareBatchSource source = sources.get(api.apiName());
        if (source == null) {
            throw TushareErrorClassifier.completenessUnconfirmed();
        }
        return source;
    }

    public FetchResult fetch(
            DatasetDefinition definition, ApiDescriptor api, FetchBatch batch, DownloadContext context) {
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(api, "api");
        Objects.requireNonNull(batch, "batch");
        Objects.requireNonNull(context, "context").checkServerState();
        TushareBatchSource source = source(definition, api, batch);
        Map<String, Object> baseParams = Map.copyOf(batch.sourceParams());
        TushareBatchSource.Session session = source.open(definition, api, batch);
        if (session == null) {
            throw TushareErrorClassifier.invalidPayload();
        }
        Set<String> paginationParameters = freezePaginationParameters(
                session.paginationParameters(), definition, api, baseParams);
        List<String> fields = definition.columns().stream().map(column -> column.name()).toList();
        List<List<Object>> rows = new ArrayList<>();
        Set<String> usedCursors = new HashSet<>();
        Set<Map<String, Object>> sentParameters = new HashSet<>();
        String cursor = null;
        Long expectedTotal = null;

        while (true) {
            Map<String, Object> pageParameters = freezePageParameters(
                    session.pageParameters(cursor), paginationParameters);
            Map<String, Object> actualParameters = merge(baseParams, pageParameters);
            if (!sentParameters.add(actualParameters)) {
                throw TushareErrorClassifier.invalidPayload();
            }
            context.checkServerState();
            DownloadEnvelope page = client.execute(definition, actualParameters);
            context.checkServerState();
            validatePage(page, definition, actualParameters, fields);
            TushareBatchSource.Observation observation = session.observe(cursor, page);
            if (observation == null || observation.end() == null) {
                throw TushareErrorClassifier.invalidPayload();
            }
            long accumulated = (long) rows.size() + page.data().size();
            if (accumulated > Integer.MAX_VALUE) {
                throw TushareErrorClassifier.invalidPayload();
            }
            rows.addAll(page.data());
            if (observation.totalRows() != null) {
                if (expectedTotal != null && !expectedTotal.equals(observation.totalRows())) {
                    throw TushareErrorClassifier.invalidPayload();
                }
                expectedTotal = observation.totalRows();
            }
            if (expectedTotal != null && accumulated > expectedTotal) {
                throw TushareErrorClassifier.invalidPayload();
            }
            switch (observation.end()) {
                case CONTINUE -> {
                    if (paginationParameters.isEmpty()) {
                        throw TushareErrorClassifier.completenessUnconfirmed();
                    }
                    String nextCursor = observation.nextCursor();
                    if (nextCursor == null || nextCursor.isBlank()
                            || nextCursor.equals(cursor) || !usedCursors.add(nextCursor)) {
                        throw TushareErrorClassifier.invalidPayload();
                    }
                    cursor = nextCursor;
                }
                case TRUNCATED -> throw TushareErrorClassifier.truncated();
                case UNCONFIRMED -> throw TushareErrorClassifier.completenessUnconfirmed();
                case COMPLETE -> {
                    if (expectedTotal != null && expectedTotal != accumulated) {
                        throw TushareErrorClassifier.invalidPayload();
                    }
                    DownloadEnvelope complete = new DownloadEnvelope(
                            definition.datasetKey().pluginId(), definition.datasetKey().apiName(), baseParams,
                            fields, rows.size(), rows, DownloadStatus.SUCCESS, null);
                    session.validateComplete(complete);
                    context.checkServerState();
                    return new FetchResult(complete, List.of());
                }
            }
        }
    }

    private static Set<String> freezePaginationParameters(
            Set<String> parameters, DatasetDefinition definition, ApiDescriptor api,
            Map<String, Object> baseParams) {
        if (parameters == null) {
            throw TushareErrorClassifier.invalidPayload();
        }
        Set<String> forbidden = new HashSet<>(RESERVED_PARAMETERS);
        forbidden.addAll(baseParams.keySet());
        definition.parameters().forEach(parameter -> forbidden.add(parameter.name()));
        api.sourceParameters().forEach(parameter -> forbidden.add(parameter.name()));
        api.parameters().forEach(parameter -> forbidden.add(parameter.name()));
        Set<String> frozen = new HashSet<>();
        for (String parameter : parameters) {
            if (parameter == null || !IDENTIFIER.matcher(parameter).matches()
                    || forbidden.contains(parameter)) {
                throw TushareErrorClassifier.invalidPayload();
            }
            frozen.add(parameter);
        }
        return Set.copyOf(frozen);
    }

    private static Map<String, Object> freezePageParameters(
            Map<String, Object> parameters, Set<String> declared) {
        if (parameters == null) {
            throw TushareErrorClassifier.invalidPayload();
        }
        Map<String, Object> frozen = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : parameters.entrySet()) {
            if (entry.getKey() == null || !declared.contains(entry.getKey())
                    || !(entry.getValue() instanceof String)) {
                throw TushareErrorClassifier.invalidPayload();
            }
            frozen.put(entry.getKey(), entry.getValue());
        }
        return Map.copyOf(frozen);
    }

    private static Map<String, Object> merge(
            Map<String, Object> baseParams, Map<String, Object> pageParams) {
        Map<String, Object> merged = new LinkedHashMap<>(baseParams);
        for (Map.Entry<String, Object> entry : pageParams.entrySet()) {
            if (merged.putIfAbsent(entry.getKey(), entry.getValue()) != null) {
                throw TushareErrorClassifier.invalidPayload();
            }
        }
        return Map.copyOf(merged);
    }

    private static void validatePage(
            DownloadEnvelope page, DatasetDefinition definition, Map<String, Object> parameters,
            List<String> fields) {
        if (page == null
                || page.status() != DownloadStatus.SUCCESS
                || !definition.datasetKey().pluginId().equals(page.pluginId())
                || !definition.datasetKey().apiName().equals(page.apiName())
                || !parameters.equals(page.params())
                || !fields.equals(page.fields())
                || page.rowCount() != page.data().size()
                || page.error() != null
                || page.data().stream().anyMatch(row -> row == null || row.size() != fields.size())) {
            throw TushareErrorClassifier.invalidPayload();
        }
    }
}
