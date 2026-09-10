package com.akkc.tensor.core.download;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.akkc.tensor.core.persistence.PersistenceService;
import com.akkc.tensor.core.registry.AdapterRegistry;
import com.akkc.tensor.core.registry.PluginRegistry;
import com.akkc.tensor.core.validation.ParameterValidator;
import com.akkc.tensor.plugin.api.DataSourcePlugin;
import com.akkc.tensor.plugin.api.DatasetAdapter;
import com.akkc.tensor.plugin.api.dataset.BusinessKeyDefinition;
import com.akkc.tensor.plugin.api.dataset.BusinessKeyMode;
import com.akkc.tensor.plugin.api.dataset.ColumnDefinition;
import com.akkc.tensor.plugin.api.dataset.DatasetDefinition;
import com.akkc.tensor.plugin.api.dataset.LogicalType;
import com.akkc.tensor.plugin.api.descriptor.ApiDescriptor;
import com.akkc.tensor.plugin.api.descriptor.PluginDescriptor;
import com.akkc.tensor.plugin.api.descriptor.PluginReadiness;
import com.akkc.tensor.plugin.api.descriptor.ParameterDescriptor;
import com.akkc.tensor.plugin.api.descriptor.ParameterType;
import com.akkc.tensor.plugin.api.descriptor.QueryMode;
import com.akkc.tensor.plugin.api.download.AdaptedBatch;
import com.akkc.tensor.plugin.api.download.DownloadEnvelope;
import com.akkc.tensor.plugin.api.download.DownloadStatus;
import com.akkc.tensor.plugin.api.error.ErrorCode;
import com.akkc.tensor.plugin.api.error.SourceException;
import com.akkc.tensor.plugin.api.error.TensorException;
import com.akkc.tensor.plugin.api.model.ApiName;
import com.akkc.tensor.plugin.api.model.DatasetKey;
import com.akkc.tensor.plugin.api.model.PluginId;
import com.akkc.tensor.plugin.api.model.RequestId;
import com.akkc.tensor.plugin.api.model.TableName;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.TransactionSystemException;

class DownloadServiceTest {
    private static final PluginId PLUGIN_ID = PluginId.of("download_test");
    private static final ApiName API_NAME = ApiName.of("daily");
    private static final DatasetKey KEY = DatasetKey.of(PLUGIN_ID, API_NAME);
    private static final Instant NOW = Instant.parse("2026-09-07T00:00:00Z");

    @Test
    void mapsDataAndTransactionPersistenceFailuresWithoutChangingEarlierFailureBoundaries() {
        for (RuntimeException failure : List.of(
                new DataAccessResourceFailureException("database failed"),
                new TransactionSystemException("transaction failed"))) {
            PersistenceService persistence = mock(PersistenceService.class);
            when(persistence.persist(org.mockito.ArgumentMatchers.any())).thenThrow(failure);

            assertThatThrownBy(() -> service(persistence).execute(
                            PLUGIN_ID, API_NAME, Map.of(), RequestId.newId()))
                    .isInstanceOfSatisfying(TensorException.class, exception -> {
                        assertThat(exception.code()).isEqualTo(ErrorCode.PERSISTENCE_FAILED);
                        assertThat(exception).hasMessage("Dataset persistence failed").hasCause(failure);
                    });
        }
    }

    @Test
    void rejectsRequiredStockBeforeDownloadAdaptationOrPersistence() {
        ParameterDescriptor stock = new ParameterDescriptor(
                "ts_code", "股票代码", null, ParameterType.TS_CODE, true,
                null, List.of(), null, null);
        Map<String, Object> nullStock = new LinkedHashMap<>();
        nullStock.put("ts_code", null);

        for (Map<String, Object> request : List.<Map<String, Object>>of(
                Map.of(), nullStock, Map.of("ts_code", "   "))) {
            assertRejectedWithoutEffects(stock, request, ErrorCode.PARAM_REQUIRED);
        }
        for (Map<String, Object> request : List.<Map<String, Object>>of(
                Map.of("ts_code", List.of("000001.SZ")), Map.of("ts_code", "multiple,codes"))) {
            assertRejectedWithoutEffects(stock, request, ErrorCode.PARAM_INVALID);
        }
    }

    @Test
    void doesNotPersistWhenThePluginRejectsASourcePayload() {
        PersistenceService persistence = mock(PersistenceService.class);
        int[] downloadCalls = {0};
        SourceException rejection = new SourceException(
                ErrorCode.SOURCE_PAYLOAD_INVALID, "Tushare returned an invalid payload");

        assertThatThrownBy(() -> service(persistence, List.of(), downloadCalls, rejection).execute(
                        PLUGIN_ID, API_NAME, Map.of(), RequestId.newId()))
                .isSameAs(rejection);

        assertThat(downloadCalls[0]).isOne();
        verifyNoInteractions(persistence);
    }

    private static void assertRejectedWithoutEffects(
            ParameterDescriptor stock,
            Map<String, Object> request,
            ErrorCode expectedCode) {
        PersistenceService persistence = mock(PersistenceService.class);
        int[] downloadCalls = {0};

        assertThatThrownBy(() -> service(persistence, List.of(stock), downloadCalls).execute(
                        PLUGIN_ID, API_NAME, request, RequestId.newId()))
                .isInstanceOfSatisfying(TensorException.class, exception -> assertThat(exception.code())
                        .isEqualTo(expectedCode));

        assertThat(downloadCalls[0]).isZero();
        verifyNoInteractions(persistence);
    }

    private static DownloadService service(PersistenceService persistence) {
        return service(persistence, List.of(), new int[1]);
    }

    private static DownloadService service(
            PersistenceService persistence,
            List<ParameterDescriptor> parameters,
            int[] downloadCalls) {
        return service(persistence, parameters, downloadCalls, null);
    }

    private static DownloadService service(
            PersistenceService persistence,
            List<ParameterDescriptor> parameters,
            int[] downloadCalls,
            RuntimeException downloadFailure) {
        DatasetDefinition definition = definition(parameters);
        AdaptedBatch batch = new AdaptedBatch(
                KEY, TableName.from(KEY), List.of("value"), List.of(Map.of("value", "row")),
                definition.businessKey(), NOW);
        DataSourcePlugin plugin = new DataSourcePlugin() {
            @Override
            public PluginDescriptor descriptor() {
                return new PluginDescriptor(
                        PLUGIN_ID, "Download test", "Download test", true, true, true, null,
                        List.of(new ApiDescriptor(API_NAME, "Daily", "market", QueryMode.snapshot, parameters)),
                        List.of(KEY));
            }

            @Override
            public PluginReadiness readiness() {
                return new PluginReadiness(true, true, true, null);
            }

            @Override
            public DownloadEnvelope download(ApiName apiName, Map<String, Object> params) {
                downloadCalls[0]++;
                if (downloadFailure != null) {
                    throw downloadFailure;
                }
                return new DownloadEnvelope(
                        PLUGIN_ID, API_NAME, Map.of(), List.of("value"), 1, List.of(List.of("row")),
                        DownloadStatus.SUCCESS, null);
            }
        };
        DatasetAdapter adapter = new DatasetAdapter() {
            @Override
            public DatasetKey datasetKey() {
                return KEY;
            }

            @Override
            public DatasetDefinition definition() {
                return definition;
            }

            @Override
            public AdaptedBatch adapt(DownloadEnvelope envelope, Instant ingestedAt) {
                return batch;
            }
        };
        return new DownloadService(
                new PluginRegistry(List.of(plugin)), new AdapterRegistry(List.of(adapter)),
                new ParameterValidator(), persistence, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static DatasetDefinition definition() {
        return definition(List.of());
    }

    private static DatasetDefinition definition(List<ParameterDescriptor> parameters) {
        return new DatasetDefinition(
                KEY, "Daily", "market", QueryMode.snapshot, parameters, TableName.from(KEY),
                List.of(new ColumnDefinition(
                        "value", "Value", LogicalType.STRING, false, 0, 64, null, null, List.of(), false)),
                new BusinessKeyDefinition(BusinessKeyMode.COMPOSITE, List.of("value")),
                List.of(), null, 500);
    }
}
