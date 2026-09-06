package com.akkc.tensor.plugin.tushare.client;

import com.akkc.tensor.plugin.api.dataset.ColumnDefinition;
import com.akkc.tensor.plugin.api.dataset.DatasetDefinition;
import com.akkc.tensor.plugin.api.download.DownloadEnvelope;
import com.akkc.tensor.plugin.api.download.DownloadStatus;
import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

final class TushareResponseValidator {
    private static final Pattern ANNOUNCEMENT_DATE_TIME =
            Pattern.compile("[0-9]{4}-[0-9]{2}-[0-9]{2} [0-9]{2}:[0-9]{2}:[0-9]{2}");
    private static final DateTimeFormatter ANNOUNCEMENT_FORMATTER =
            DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss", Locale.ROOT)
                    .withResolverStyle(ResolverStyle.STRICT);

    private TushareResponseValidator() {}

    static DownloadEnvelope validate(
            DatasetDefinition definition,
            Map<String, Object> params,
            TushareResponse response) {
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(params, "params");
        Objects.requireNonNull(response, "response");
        if (response.code() == null) {
            throw TushareErrorClassifier.invalidPayload();
        }
        if (response.code() != 0) {
            throw TushareErrorClassifier.classifyBusiness(response.msg());
        }
        if (response.data() == null) {
            throw TushareErrorClassifier.invalidPayload();
        }

        List<String> fields = response.data().fields();
        if (fields == null) {
            throw TushareErrorClassifier.invalidPayload();
        }
        List<List<Object>> items = response.data().items();
        if (items == null) {
            throw TushareErrorClassifier.invalidPayload();
        }
        if (fields.contains(null) || new HashSet<>(fields).size() != fields.size()) {
            throw TushareErrorClassifier.invalidPayload();
        }

        List<String> expectedFields = definition.columns().stream().map(ColumnDefinition::name).toList();
        if (!expectedFields.equals(fields)) {
            throw TushareErrorClassifier.invalidPayload();
        }
        for (List<Object> row : items) {
            if (row == null) {
                throw TushareErrorClassifier.invalidPayload();
            }
            if (row.size() != fields.size()) {
                throw TushareErrorClassifier.invalidPayload();
            }
        }

        if (definition.datasetKey().apiName().value().equals("stk_holdernumber")) {
            items = normalizeAnnouncementDates(items, fields.indexOf("ann_date"));
        }

        return new DownloadEnvelope(
                definition.datasetKey().pluginId(),
                definition.datasetKey().apiName(),
                params,
                fields,
                items.size(),
                items,
                DownloadStatus.SUCCESS,
                null);
    }

    private static List<List<Object>> normalizeAnnouncementDates(List<List<Object>> items, int columnIndex) {
        if (columnIndex < 0) {
            return items;
        }
        List<List<Object>> normalized = new ArrayList<>(items);
        for (int rowIndex = 0; rowIndex < items.size(); rowIndex++) {
            Object value = items.get(rowIndex).get(columnIndex);
            if (value instanceof String text && ANNOUNCEMENT_DATE_TIME.matcher(text).matches()) {
                try {
                    String date = LocalDateTime.parse(text, ANNOUNCEMENT_FORMATTER).toLocalDate()
                            .format(DateTimeFormatter.BASIC_ISO_DATE);
                    List<Object> row = new ArrayList<>(items.get(rowIndex));
                    row.set(columnIndex, date);
                    normalized.set(rowIndex, row);
                } catch (DateTimeException ignored) {
                    // Leave invalid source values for the strict adapter to reject.
                }
            }
        }
        return normalized;
    }
}
