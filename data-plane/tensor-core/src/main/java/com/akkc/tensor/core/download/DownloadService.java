package com.akkc.tensor.core.download;

import com.akkc.tensor.core.persistence.PersistenceService;
import com.akkc.tensor.core.persistence.WriteCounts;
import com.akkc.tensor.core.registry.AdapterRegistry;
import com.akkc.tensor.core.registry.PluginRegistry;
import com.akkc.tensor.core.validation.ParameterValidator;
import com.akkc.tensor.core.validation.ValidatedParameters;
import com.akkc.tensor.plugin.api.DataSourcePlugin;
import com.akkc.tensor.plugin.api.DatasetAdapter;
import com.akkc.tensor.plugin.api.descriptor.ApiDescriptor;
import com.akkc.tensor.plugin.api.descriptor.PluginDescriptor;
import com.akkc.tensor.plugin.api.download.AdaptedBatch;
import com.akkc.tensor.plugin.api.download.DownloadEnvelope;
import com.akkc.tensor.plugin.api.download.DownloadOutcome;
import com.akkc.tensor.plugin.api.download.DownloadResult;
import com.akkc.tensor.plugin.api.download.DownloadStatus;
import com.akkc.tensor.plugin.api.error.ErrorCode;
import com.akkc.tensor.plugin.api.error.SourceException;
import com.akkc.tensor.plugin.api.error.TensorException;
import com.akkc.tensor.plugin.api.model.ApiName;
import com.akkc.tensor.plugin.api.model.DatasetKey;
import com.akkc.tensor.plugin.api.model.PluginId;
import com.akkc.tensor.plugin.api.model.RequestId;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.transaction.support.TransactionSynchronizationManager;

public final class DownloadService {
    private final PluginRegistry pluginRegistry;
    private final AdapterRegistry adapterRegistry;
    private final ParameterValidator parameterValidator;
    private final PersistenceService persistenceService;
    private final Clock clock;

    public DownloadService(
            PluginRegistry pluginRegistry,
            AdapterRegistry adapterRegistry,
            ParameterValidator parameterValidator,
            PersistenceService persistenceService,
            Clock clock) {
        this.pluginRegistry = Objects.requireNonNull(pluginRegistry, "pluginRegistry");
        this.adapterRegistry = Objects.requireNonNull(adapterRegistry, "adapterRegistry");
        this.parameterValidator = Objects.requireNonNull(parameterValidator, "parameterValidator");
        this.persistenceService = Objects.requireNonNull(persistenceService, "persistenceService");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public DownloadResult execute(
            PluginId pluginId,
            ApiName apiName,
            Map<String, Object> params,
            RequestId requestId) {
        Objects.requireNonNull(pluginId, "pluginId");
        Objects.requireNonNull(apiName, "apiName");
        Objects.requireNonNull(params, "params");
        Objects.requireNonNull(requestId, "requestId");
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Download orchestration must not run in a transaction");
        }

        DataSourcePlugin plugin = pluginRegistry.find(pluginId)
                .orElseThrow(() -> access(ErrorCode.PLUGIN_DISABLED));
        ApiDescriptor api = descriptor(pluginId).apis().stream()
                .filter(candidate -> candidate.apiName().equals(apiName))
                .findFirst()
                .orElseThrow(() -> access(ErrorCode.DATASET_MISCONFIGURED));
        DatasetKey key = DatasetKey.of(pluginId, apiName);
        DatasetAdapter adapter = adapterRegistry.find(key)
                .orElseThrow(() -> access(ErrorCode.DATASET_MISCONFIGURED));
        ValidatedParameters validated = parameterValidator.validate(api, params);
        DownloadEnvelope envelope = plugin.download(apiName, validated.values());
        if (envelope == null) {
            throw invalidPayload();
        }
        if (envelope.status() == DownloadStatus.FAILURE) {
            throw new SourceException(ErrorCode.SOURCE_PAYLOAD_INVALID, envelope.error());
        }
        if (!envelope.pluginId().equals(pluginId)
                || !envelope.apiName().equals(apiName)
                || !envelope.params().equals(validated.values())) {
            throw invalidPayload();
        }
        if (envelope.rowCount() == 0) {
            return new DownloadResult(
                    requestId, DownloadOutcome.EMPTY, pluginId, apiName, 0, 0, 0,
                    "下载成功，0 条数据");
        }

        AdaptedBatch batch = adapter.adapt(envelope, clock.instant());
        WriteCounts counts = persistenceService.persist(batch);
        return new DownloadResult(
                requestId,
                DownloadOutcome.SUCCESS,
                pluginId,
                apiName,
                envelope.rowCount(),
                counts.insertedRows(),
                counts.updatedRows(),
                "下载成功");
    }

    private PluginDescriptor descriptor(PluginId pluginId) {
        List<PluginDescriptor> matches = pluginRegistry.descriptors().stream()
                .filter(candidate -> candidate.pluginId().equals(pluginId))
                .filter(PluginDescriptor::downloadAvailable)
                .toList();
        if (matches.size() != 1) {
            throw access(ErrorCode.DATASET_MISCONFIGURED);
        }
        return matches.get(0);
    }

    private static DownloadAccessException access(ErrorCode code) {
        return new DownloadAccessException(
                code,
                code == ErrorCode.PLUGIN_DISABLED
                        ? "Download plugin is unavailable"
                        : "Download dataset is unavailable");
    }

    private static SourceException invalidPayload() {
        return new SourceException(
                ErrorCode.SOURCE_PAYLOAD_INVALID, "Source returned an invalid payload");
    }

    private static final class DownloadAccessException extends TensorException {
        private DownloadAccessException(ErrorCode code, String message) {
            super(requireAccessCode(code), message);
        }

        private static ErrorCode requireAccessCode(ErrorCode code) {
            if (code != ErrorCode.PLUGIN_DISABLED && code != ErrorCode.DATASET_MISCONFIGURED) {
                throw new IllegalArgumentException("Unsupported download access error code");
            }
            return code;
        }
    }
}
