package com.akkc.tensor.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.akkc.tensor.core.adapter.FingerprintKeyCodec;
import com.akkc.tensor.core.adapter.GenericDatasetAdapter;
import com.akkc.tensor.core.adapter.ValueConverter;
import com.akkc.tensor.core.catalog.DatasetCatalog;
import com.akkc.tensor.core.download.task.DownloadTask;
import com.akkc.tensor.core.download.task.DownloadTaskJson;
import com.akkc.tensor.core.download.task.DownloadTaskRepository;
import com.akkc.tensor.core.download.task.DownloadTaskService;
import com.akkc.tensor.core.registry.AdapterRegistry;
import com.akkc.tensor.core.registry.PluginRegistry;
import com.akkc.tensor.core.validation.ParameterValidator;
import com.akkc.tensor.plugin.api.DatasetAdapter;
import com.akkc.tensor.plugin.api.dataset.DatasetDefinition;
import com.akkc.tensor.plugin.api.download.batch.BatchDownloadDescriptor.Availability;
import com.akkc.tensor.plugin.api.download.batch.BatchDownloadDescriptor.CompletenessRule.Kind;
import com.akkc.tensor.plugin.api.download.batch.DownloadMode;
import com.akkc.tensor.plugin.api.error.ErrorCode;
import com.akkc.tensor.plugin.api.error.TensorException;
import com.akkc.tensor.plugin.tushare.TusharePluginConfiguration;
import com.akkc.tensor.plugin.tushare.TushareProPlugin;
import com.akkc.tensor.plugin.tushare.client.TushareProClient;
import com.akkc.tensor.plugin.tushare.config.TushareProperties;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class TushareBatchAvailabilityTest {
    private static final Instant NOW = Instant.parse("2026-09-11T00:00:00Z");
    private static final List<DatasetDefinition> DEFINITIONS =
            new TusharePluginConfiguration().tushareDatasetDefinitions();
    private static final Set<String> SINGLE_ONLY = Set.of(
            "pledge_stat", "stk_rewards", "stock_basic", "stock_company", "index_classify", "index_member_all");
    private static final Map<String, String> VALUES = Map.of(
            "ts_code", "000001.SZ", "trade_date", "20260903", "ann_date", "20260903",
            "exchange", "SSE", "exchange_id", "SSE", "start_date", "20260901",
            "end_date", "20260903", "list_status", "L");

    @Test
    void refusesAllUnverifiedProductionRangesBeforeStorageOrUpstreamWithConfiguredCredentials() {
        var h = harness();
        List<String> candidates = new ArrayList<>();
        for (var definition : DEFINITIONS) {
            var key = definition.datasetKey();
            var capability = h.service().capabilities(key);
            assertThat(capability.single().available()).isTrue();
            if (SINGLE_ONLY.contains(key.apiName().value())) {
                assertThat(capability.range().availability()).isEqualTo(Availability.UNSUPPORTED);
                assertThat(capability.range().parameters()).isEmpty();
            } else {
                candidates.add(key.apiName().value());
                assertThat(capability.range().availability()).isEqualTo(Availability.NEEDS_VERIFICATION);
                assertThat(capability.range().completenessRule().kind()).isEqualTo(Kind.UNKNOWN);
                var params = new LinkedHashMap<String, Object>();
                capability.range().parameters().forEach(p -> params.put(p.name(), VALUES.get(p.name())));
                assertThatThrownBy(() -> h.service().submit(new DownloadTaskService.Submission(
                        UUID.randomUUID(), key, DownloadMode.RANGE, params)))
                        .isInstanceOfSatisfying(TensorException.class,
                                e -> assertThat(e.code()).isEqualTo(ErrorCode.BATCH_DOWNLOAD_UNAVAILABLE));
            }
        }
        assertThat(candidates).hasSize(34).doesNotHaveDuplicates();
        assertThat(h.plugin().descriptor().apis()).hasSize(40);
        verify(h.repository(), never()).insert(any());
        verify(h.repository(), never()).queuedCount();
        h.upstream().verify(); // Any HTTP would fail: no expectations are registered.
    }

    @Test
    void locallyAcceptsAllFortySingleShapesAndKeepsMainBusinessAsStockSnapshot() {
        var h = harness();
        var accepted = new ArrayList<DownloadTask>();
        when(h.repository().insert(any())).thenAnswer(call -> {
            DownloadTaskRepository.NewTask request = call.getArgument(0);
            return new DownloadTask(request.taskId(), request.submissionId(), "request-hash", request.datasetKey(),
                    request.mode(), request.normalizedParams(), request.definitionHash(), request.policySnapshot(),
                    DownloadTask.Status.QUEUED, false, request.activeRunId(), 0, 1, 0, 0, null,
                    NOW, NOW, NOW, null, null, null);
        });
        for (var definition : DEFINITIONS) {
            var params = new LinkedHashMap<String, Object>();
            definition.parameters().forEach(p -> params.put(p.name(), VALUES.get(p.name())));
            var result = h.service().submit(new DownloadTaskService.Submission(
                    UUID.randomUUID(), definition.datasetKey(), DownloadMode.SINGLE, params));
            assertThat(result.created()).isTrue();
            assertThat(result.task().mode()).isEqualTo(DownloadMode.SINGLE);
            assertThat(result.task().status()).isEqualTo(DownloadTask.Status.QUEUED);
            assertThat(result.task().planReady()).isFalse();
            accepted.add(result.task());
        }
        assertThat(accepted).hasSize(40);
        assertThat(accepted.stream().filter(t -> t.datasetKey().apiName().value().equals("fina_mainbz")))
                .singleElement().satisfies(task -> assertThat(task.params()).isEqualTo(Map.of("ts_code", "000001.SZ")));
        h.upstream().verify();
    }

    private static Harness harness() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://batch-admission.invalid");
        MockRestServiceServer upstream = MockRestServiceServer.bindTo(builder).build();
        var properties = new TushareProperties(true, URI.create("https://batch-admission.invalid"),
                new TushareProperties.Credential("controlled-admission-token"), Duration.ofSeconds(1),
                Duration.ofSeconds(1), 1_048_576, Duration.ZERO);
        var plugin = new TushareProPlugin(properties, new TushareProClient(builder.build(), properties), DEFINITIONS);
        // Catalog and repository are the database boundaries; plugin, adapters and admission are real.
        var catalog = mock(DatasetCatalog.class);
        for (var definition : DEFINITIONS) when(catalog.find(definition.datasetKey())).thenReturn(Optional.of(definition));
        var adapters = new AdapterRegistry(DEFINITIONS.stream().<DatasetAdapter>map(d ->
                new GenericDatasetAdapter(d, new ValueConverter(), new FingerprintKeyCodec())).toList());
        var repository = mock(DownloadTaskRepository.class);
        var service = new DownloadTaskService(new PluginRegistry(List.of(plugin)), catalog, adapters,
                new ParameterValidator(), repository, new DownloadTaskJson(), Clock.fixed(NOW, ZoneOffset.UTC),
                UUID.randomUUID(), DownloadTaskService.Settings.defaults());
        return new Harness(service, plugin, repository, upstream);
    }

    private record Harness(DownloadTaskService service, TushareProPlugin plugin,
            DownloadTaskRepository repository, MockRestServiceServer upstream) {}
}
