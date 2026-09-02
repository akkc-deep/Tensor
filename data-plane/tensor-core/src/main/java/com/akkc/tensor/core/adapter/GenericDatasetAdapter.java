package com.akkc.tensor.core.adapter;

import com.akkc.tensor.plugin.api.DatasetAdapter;
import com.akkc.tensor.plugin.api.dataset.BusinessKeyMode;
import com.akkc.tensor.plugin.api.dataset.ColumnDefinition;
import com.akkc.tensor.plugin.api.dataset.DatasetDefinition;
import com.akkc.tensor.plugin.api.download.AdaptedBatch;
import com.akkc.tensor.plugin.api.download.DownloadEnvelope;
import com.akkc.tensor.plugin.api.download.DownloadStatus;
import com.akkc.tensor.plugin.api.error.AdapterException;
import com.akkc.tensor.plugin.api.error.ErrorCode;
import com.akkc.tensor.plugin.api.model.DatasetKey;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class GenericDatasetAdapter implements DatasetAdapter {
    private static final System.Logger LOGGER = System.getLogger(GenericDatasetAdapter.class.getName());
    private static final String BUSINESS_KEY_COLUMN = "business_key";

    private final DatasetDefinition definition;
    private final ValueConverter valueConverter;
    private final FingerprintKeyCodec fingerprintKeyCodec;

    public GenericDatasetAdapter(
            DatasetDefinition definition, ValueConverter valueConverter, FingerprintKeyCodec fingerprintKeyCodec) {
        this.definition = Objects.requireNonNull(definition, "definition");
        this.valueConverter = Objects.requireNonNull(valueConverter, "valueConverter");
        this.fingerprintKeyCodec = Objects.requireNonNull(fingerprintKeyCodec, "fingerprintKeyCodec");
    }

    @Override
    public DatasetKey datasetKey() {
        return definition.datasetKey();
    }

    @Override
    public DatasetDefinition definition() {
        return definition;
    }

    @Override
    public AdaptedBatch adapt(DownloadEnvelope envelope, Instant ingestedAt) {
        Objects.requireNonNull(envelope, "envelope");
        Objects.requireNonNull(ingestedAt, "ingestedAt");
        if (envelope.status() != DownloadStatus.SUCCESS) {
            throw new IllegalArgumentException("envelope must be successful");
        }
        if (!envelope.pluginId().equals(definition.datasetKey().pluginId())
                || !envelope.apiName().equals(definition.datasetKey().apiName())) {
            throw missing("Adapter envelope mismatch: api=" + definition.datasetKey().apiName().value());
        }

        List<String> columns = definition.columns().stream().map(ColumnDefinition::name).toList();
        if (!envelope.fields().equals(columns)) {
            throw missing("Adapter fields do not match: api=" + definition.datasetKey().apiName().value());
        }
        LinkedHashMap<String, Integer> fieldIndexes = new LinkedHashMap<>();
        for (int index = 0; index < envelope.fields().size(); index++) {
            fieldIndexes.put(envelope.fields().get(index), index);
        }

        List<String> batchColumns = new ArrayList<>(columns);
        boolean fingerprint = definition.businessKey().mode() == BusinessKeyMode.FINGERPRINT;
        if (fingerprint) {
            batchColumns.add(BUSINESS_KEY_COLUMN);
        }
        LinkedHashMap<Object, Map<String, Object>> uniqueRows = new LinkedHashMap<>();
        List<String> keyFields = definition.businessKey().fields();
        for (int rowIndex = 0; rowIndex < envelope.data().size(); rowIndex++) {
            List<Object> sourceRow = envelope.data().get(rowIndex);
            LinkedHashMap<String, Object> row = new LinkedHashMap<>();
            ConversionContext context = new ConversionContext(envelope.apiName(), rowIndex);
            for (ColumnDefinition column : definition.columns()) {
                Object converted = valueConverter.convert(sourceRow.get(fieldIndexes.get(column.name())), column, context);
                if (converted == null && (!column.nullable() || keyFields.contains(column.name()))) {
                    throw missing("Missing adapter value: api=" + envelope.apiName().value() + ", row=" + rowIndex
                            + ", field=" + column.name());
                }
                row.put(column.name(), converted);
            }
            Object key;
            if (fingerprint) {
                String fingerprintKey = fingerprintKeyCodec.sha256(keyFields, row);
                row.put(BUSINESS_KEY_COLUMN, fingerprintKey);
                key = fingerprintKey;
            } else {
                key = List.copyOf(keyFields.stream().map(row::get).toList());
            }
            Map<String, Object> previous = uniqueRows.putIfAbsent(key, row);
            if (previous != null) {
                if (previous.equals(row)) {
                    LOGGER.log(System.Logger.Level.WARNING, "Duplicate adapter row discarded");
                } else {
                    throw new AdapterException(ErrorCode.ADAPTER_TYPE_INVALID,
                            "Conflicting adapter key: api=" + envelope.apiName().value() + ", row=" + rowIndex);
                }
            }
        }
        return new AdaptedBatch(
                definition.datasetKey(), definition.tableName(), batchColumns, List.copyOf(uniqueRows.values()),
                definition.businessKey(), ingestedAt);
    }

    private AdapterException missing(String message) {
        return new AdapterException(ErrorCode.ADAPTER_FIELD_MISSING, message);
    }
}
