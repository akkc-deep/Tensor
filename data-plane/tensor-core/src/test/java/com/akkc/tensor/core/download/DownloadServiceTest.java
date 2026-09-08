package com.akkc.tensor.core.download;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
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
import com.akkc.tensor.plugin.api.descriptor.QueryMode;
import com.akkc.tensor.plugin.api.download.AdaptedBatch;
import com.akkc.tensor.plugin.api.download.DownloadEnvelope;
import com.akkc.tensor.plugin.api.download.FetchResult;
import com.akkc.tensor.plugin.api.download.DownloadContext;
import com.akkc.tensor.plugin.api.download.DownloadStatus;
import com.akkc.tensor.plugin.api.error.ErrorCode;
import com.akkc.tensor.plugin.api.error.TensorException;
import com.akkc.tensor.plugin.api.model.ApiName;
import com.akkc.tensor.plugin.api.model.DatasetKey;
import com.akkc.tensor.plugin.api.model.PluginId;
import com.akkc.tensor.plugin.api.model.RequestId;
import com.akkc.tensor.plugin.api.model.TableName;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
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
    void sourceParametersRemainTheOnlyCurrentExecutionInputs() {
        var source = List.of(new com.akkc.tensor.plugin.api.descriptor.ParameterDescriptor("trade_date", "Trade date", null,
                com.akkc.tensor.plugin.api.descriptor.ParameterType.DATE, true, null, List.of(), null, null));
        var policy = com.akkc.tensor.test.DownloadPolicies.tradeRange();
        var api = new ApiDescriptor(API_NAME, "Daily", "Market", QueryMode.trade_date,
                com.akkc.tensor.plugin.api.download.DownloadParameterProjection.project(source, policy), policy, source);
        var params = Map.<String,Object>of("trade_date", "20260903");
        var plugin = mock(DataSourcePlugin.class);
        when(plugin.descriptor()).thenReturn(new PluginDescriptor(PLUGIN_ID, "Test", "Test", true, true, true, null, List.of(api), List.of(KEY)));
        when(plugin.readiness()).thenReturn(new PluginReadiness(true, true, true, null));
        var envelope = new DownloadEnvelope(PLUGIN_ID, API_NAME, params, List.of("value"), 0, List.of(), DownloadStatus.SUCCESS, null);
        when(plugin.download(org.mockito.ArgumentMatchers.eq(API_NAME), org.mockito.ArgumentMatchers.eq(params), org.mockito.ArgumentMatchers.any()))
                .thenReturn(new FetchResult(envelope, List.of()));
        var adapter = mock(DatasetAdapter.class);
        when(adapter.datasetKey()).thenReturn(KEY);
        when(adapter.definition()).thenReturn(definition());
        var persistence = mock(PersistenceService.class);
        var service = new DownloadService(new PluginRegistry(List.of(plugin)), new AdapterRegistry(List.of(adapter)),
                new ParameterValidator(), persistence, Clock.fixed(NOW, ZoneOffset.UTC));
        assertThat(service.execute(PLUGIN_ID, API_NAME, params, RequestId.newId()).outcome())
                .isEqualTo(com.akkc.tensor.plugin.api.download.DownloadOutcome.EMPTY);
        org.mockito.Mockito.verify(plugin).download(org.mockito.ArgumentMatchers.eq(API_NAME), org.mockito.ArgumentMatchers.eq(params), org.mockito.ArgumentMatchers.any());
        org.mockito.Mockito.verify(adapter, org.mockito.Mockito.never()).adapt(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        org.mockito.Mockito.verifyNoInteractions(persistence);
        assertThatThrownBy(() -> new ParameterValidator().validate(api, params))
                .isInstanceOfSatisfying(TensorException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.PARAM_REQUIRED));
        assertThat(new ParameterValidator().validate(api.sourceParameters(), params).values()).isEqualTo(params);
    }

    @Test
    void rejectsNullOrExplicitUnitFailuresBeforeAdaptationOrPersistence() {
        var envelope = new DownloadEnvelope(PLUGIN_ID, API_NAME, Map.of(), List.of("value"), 1, List.of(List.of("row")), DownloadStatus.SUCCESS, null);
        var failure = new FetchResult.UnitFailure(new com.akkc.tensor.plugin.api.download.RecoverySelector(
                com.akkc.tensor.plugin.api.download.RecoverySelector.TargetType.REQUEST, "",
                com.akkc.tensor.plugin.api.download.RecoverySelector.TimeType.NONE, ""), ErrorCode.SOURCE_TRUNCATED, "Source is truncated");
        var missingEnvelope = mock(FetchResult.class);
        for (FetchResult fetched : java.util.Arrays.asList(null, missingEnvelope, new FetchResult(envelope, List.of(failure)))) {
            var plugin = mock(DataSourcePlugin.class);
            var api = new ApiDescriptor(API_NAME, "Daily", "Market", QueryMode.snapshot, List.of(), com.akkc.tensor.test.DownloadPolicies.original(), List.of());
            when(plugin.descriptor()).thenReturn(new PluginDescriptor(PLUGIN_ID, "Test", "Test", true, true, true, null, List.of(api), List.of(KEY)));
            when(plugin.readiness()).thenReturn(new PluginReadiness(true, true, true, null));
            when(plugin.download(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenReturn(fetched);
            var adapter = mock(DatasetAdapter.class);
            when(adapter.datasetKey()).thenReturn(KEY); when(adapter.definition()).thenReturn(definition());
            var persistence = mock(PersistenceService.class);
            var service = new DownloadService(new PluginRegistry(List.of(plugin)), new AdapterRegistry(List.of(adapter)),
                    new ParameterValidator(), persistence, Clock.fixed(NOW, ZoneOffset.UTC));
            assertThatThrownBy(() -> service.execute(PLUGIN_ID, API_NAME, Map.of(), RequestId.newId()))
                    .isInstanceOfSatisfying(TensorException.class, e -> {
                        assertThat(e.code()).isEqualTo(ErrorCode.SOURCE_PAYLOAD_INVALID);
                        assertThat(e.getMessage()).isEqualTo("Source returned an invalid payload");
                    });
            org.mockito.Mockito.verify(adapter, org.mockito.Mockito.never()).adapt(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
            org.mockito.Mockito.verifyNoInteractions(persistence);
        }
    }

    private static DownloadService service(PersistenceService persistence) {
        DatasetDefinition definition = definition();
        AdaptedBatch batch = new AdaptedBatch(
                KEY, TableName.from(KEY), List.of("value"), List.of(Map.of("value", "row")),
                definition.businessKey(), NOW);
        DataSourcePlugin plugin = new DataSourcePlugin() {
            @Override
            public PluginDescriptor descriptor() {
                return new PluginDescriptor(
                        PLUGIN_ID, "Download test", "Download test", true, true, true, null,
                        List.of(new ApiDescriptor(API_NAME, "Daily", "market", QueryMode.snapshot, List.of(), com.akkc.tensor.test.DownloadPolicies.original(), List.of())),
                        List.of(KEY));
            }

            @Override
            public PluginReadiness readiness() {
                return new PluginReadiness(true, true, true, null);
            }

            @Override
            public FetchResult download(ApiName apiName, Map<String, Object> params, DownloadContext context) {
                return new FetchResult(new DownloadEnvelope(
                        PLUGIN_ID, API_NAME, Map.of(), List.of("value"), 1, List.of(List.of("row")),
                        DownloadStatus.SUCCESS, null), List.of());
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
        return new DatasetDefinition(
                KEY, "Daily", "market", QueryMode.snapshot, List.of(), TableName.from(KEY),
                List.of(new ColumnDefinition(
                        "value", "Value", LogicalType.STRING, false, 0, 64, null, null, List.of(), false)),
                new BusinessKeyDefinition(BusinessKeyMode.COMPOSITE, List.of("value")),
                List.of(), null, 500);
    }
}
