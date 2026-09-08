package com.akkc.tensor.core.metadata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.akkc.tensor.core.catalog.DatasetCatalog;
import com.akkc.tensor.core.registry.PluginRegistry;
import com.akkc.tensor.plugin.api.DataSourcePlugin;
import com.akkc.tensor.plugin.api.dataset.BusinessKeyDefinition;
import com.akkc.tensor.plugin.api.dataset.BusinessKeyMode;
import com.akkc.tensor.plugin.api.dataset.ColumnDefinition;
import com.akkc.tensor.plugin.api.dataset.DatasetDefinition;
import com.akkc.tensor.plugin.api.dataset.FilterDefinition;
import com.akkc.tensor.plugin.api.dataset.LogicalType;
import com.akkc.tensor.plugin.api.descriptor.ApiDescriptor;
import com.akkc.tensor.plugin.api.descriptor.PluginDescriptor;
import com.akkc.tensor.plugin.api.descriptor.PluginReadiness;
import com.akkc.tensor.plugin.api.descriptor.QueryMode;
import com.akkc.tensor.plugin.api.download.DownloadEnvelope;
import com.akkc.tensor.plugin.api.download.FetchResult;
import com.akkc.tensor.plugin.api.download.DownloadContext;
import com.akkc.tensor.plugin.api.error.ErrorCode;
import com.akkc.tensor.plugin.api.error.TensorException;
import com.akkc.tensor.plugin.api.model.ApiName;
import com.akkc.tensor.plugin.api.model.DatasetKey;
import com.akkc.tensor.plugin.api.model.PluginId;
import com.akkc.tensor.plugin.api.model.TableName;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MetadataQueryServiceTest {
    private static final PluginId PLUGIN_ID = PluginId.of("metadata_test");
    private static final ApiName API_NAME = ApiName.of("daily");
    private static final DatasetKey KEY = DatasetKey.of(PLUGIN_ID, API_NAME);

    @Test
    void listsTheRegistrySnapshotIncludingDisabledAndDuplicateDescriptors() {
        DataSourcePlugin disabled = plugin("Disabled", false);
        DataSourcePlugin duplicateA = plugin("Duplicate A", true);
        DataSourcePlugin duplicateB = plugin("Duplicate B", true);
        PluginRegistry registry = new PluginRegistry(List.of(disabled, duplicateA, duplicateB));

        List<PluginDescriptor> result = service(registry, mock(DatasetCatalog.class)).listDataSources();

        assertThat(result).hasSize(3);
        assertThat(result).extracting(PluginDescriptor::displayName)
                .containsExactly("Disabled", "Duplicate A", "Duplicate B");
        assertThat(result).noneMatch(PluginDescriptor::downloadAvailable);
    }

    @Test
    void listsApisOnlyForExactlyOneDownloadableDescriptor() {
        PluginRegistry registry = new PluginRegistry(List.of(plugin("Available", true)));

        assertThat(service(registry, mock(DatasetCatalog.class)).listApis(PLUGIN_ID))
                .containsExactly(api());
    }

    @Test
    void rejectsMissingDisabledAndDuplicatePluginsForApiListing() {
        List<PluginRegistry> registries = List.of(
                new PluginRegistry(List.of()),
                new PluginRegistry(List.of(plugin("Disabled", false))),
                new PluginRegistry(List.of(plugin("Duplicate A", true), plugin("Duplicate B", true))));

        for (PluginRegistry registry : registries) {
            assertCode(() -> service(registry, mock(DatasetCatalog.class)).listApis(PLUGIN_ID),
                    ErrorCode.PLUGIN_DISABLED);
        }
    }

    @Test
    void exposesAdmittedDatasetsForDisabledAndDuplicatePlugins() {
        DatasetCatalog catalog = mock(DatasetCatalog.class);
        DatasetDefinition definition = definition(List.of("ts_code"));
        when(catalog.list(PLUGIN_ID)).thenReturn(List.of(definition));
        when(catalog.find(KEY)).thenReturn(java.util.Optional.of(definition));

        for (PluginRegistry registry : List.of(
                new PluginRegistry(List.of(plugin("Disabled", false))),
                new PluginRegistry(List.of(plugin("Duplicate A", true), plugin("Duplicate B", true))))) {
            MetadataQueryService service = service(registry, catalog);
            assertThat(service.listDatasets(PLUGIN_ID)).containsExactly(definition);
            assertThat(service.getDataset(KEY)).isSameAs(definition);
        }
    }

    @Test
    void rejectsUnknownPluginsMissingDatasetsAndUnsupportedFilterMetadata() {
        DatasetCatalog empty = mock(DatasetCatalog.class);
        MetadataQueryService unknown = service(new PluginRegistry(List.of()), empty);
        assertCode(() -> unknown.listDatasets(PLUGIN_ID), ErrorCode.DATASET_MISCONFIGURED);
        assertCode(() -> unknown.getDataset(KEY), ErrorCode.DATASET_MISCONFIGURED);

        DatasetCatalog catalog = mock(DatasetCatalog.class);
        when(catalog.find(KEY)).thenReturn(java.util.Optional.empty());
        MetadataQueryService registered = service(
                new PluginRegistry(List.of(plugin("Disabled", false))), catalog);
        assertCode(() -> registered.getDataset(KEY), ErrorCode.DATASET_MISCONFIGURED);

        DatasetDefinition unsupported = definition(List.of("close"));
        when(catalog.list(PLUGIN_ID)).thenReturn(List.of(unsupported));
        when(catalog.find(KEY)).thenReturn(java.util.Optional.of(unsupported));
        assertCode(() -> registered.listDatasets(PLUGIN_ID), ErrorCode.DATASET_MISCONFIGURED);
        assertCode(() -> registered.getDataset(KEY), ErrorCode.DATASET_MISCONFIGURED);
    }

    private static MetadataQueryService service(PluginRegistry registry, DatasetCatalog catalog) {
        return new MetadataQueryService(registry, catalog);
    }

    private static void assertCode(Runnable action, ErrorCode code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(
                TensorException.class, exception -> assertThat(exception.code()).isEqualTo(code));
    }

    private static DataSourcePlugin plugin(String displayName, boolean available) {
        PluginDescriptor descriptor = new PluginDescriptor(
                PLUGIN_ID, displayName, "Metadata test", available, available, available,
                available ? null : "disabled", List.of(api()), List.of(KEY));
        PluginReadiness readiness = available
                ? new PluginReadiness(true, true, true, null)
                : new PluginReadiness(false, false, false, "disabled");
        return new DataSourcePlugin() {
            @Override
            public PluginDescriptor descriptor() {
                return descriptor;
            }

            @Override
            public PluginReadiness readiness() {
                return readiness;
            }

            @Override
            public FetchResult download(ApiName apiName, Map<String, Object> params, DownloadContext context) {
                throw new AssertionError("download must not be called");
            }
        };
    }

    private static ApiDescriptor api() {
        return new ApiDescriptor(API_NAME, "Daily", "market", QueryMode.trade_date, List.of(), com.akkc.tensor.test.DownloadPolicies.original(), List.of());
    }

    private static DatasetDefinition definition(List<String> filters) {
        List<String> names = new ArrayList<>(List.of("ts_code"));
        filters.stream().filter(name -> !names.contains(name)).forEach(names::add);
        return new DatasetDefinition(
                KEY, "Daily", "market", QueryMode.trade_date, List.of(), TableName.from(KEY),
                names.stream().map(name -> new ColumnDefinition(
                        name, name, LogicalType.STRING, false, 0, 64, null, null, List.of(), false)).toList(),
                new BusinessKeyDefinition(BusinessKeyMode.COMPOSITE, List.of("ts_code")),
                filters.stream().map(FilterDefinition::new).toList(), null, 500);
    }
}
