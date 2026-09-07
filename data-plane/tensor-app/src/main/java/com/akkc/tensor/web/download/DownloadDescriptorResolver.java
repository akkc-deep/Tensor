package com.akkc.tensor.web.download;

import com.akkc.tensor.core.registry.AdapterRegistry;
import com.akkc.tensor.core.registry.PluginRegistry;
import com.akkc.tensor.plugin.api.descriptor.ApiDescriptor;
import com.akkc.tensor.plugin.api.descriptor.PluginDescriptor;
import com.akkc.tensor.plugin.api.error.ErrorCode;
import com.akkc.tensor.plugin.api.model.DatasetKey;
import java.util.List;

public final class DownloadDescriptorResolver {
    private final PluginRegistry plugins;
    private final AdapterRegistry adapters;

    public DownloadDescriptorResolver(PluginRegistry plugins, AdapterRegistry adapters) {
        this.plugins = plugins;
        this.adapters = adapters;
    }

    public ApiDescriptor requireApi(DatasetKey dataset) {
        // Keep the same access-error precedence as DownloadService before inspecting parameter JSON.
        if (plugins.find(dataset.pluginId()).isEmpty()) {
            throw new DownloadBindingException(ErrorCode.PLUGIN_DISABLED, List.of());
        }
        List<PluginDescriptor> matches = plugins.descriptors().stream()
                .filter(plugin -> plugin.pluginId().equals(dataset.pluginId()) && plugin.downloadAvailable()).toList();
        if (matches.size() != 1) {
            throw misconfigured();
        }
        ApiDescriptor api = matches.getFirst().apis().stream()
                .filter(candidate -> candidate.apiName().equals(dataset.apiName())).findFirst()
                .orElseThrow(DownloadDescriptorResolver::misconfigured);
        if (adapters.find(dataset).isEmpty()) {
            throw misconfigured();
        }
        return api;
    }

    static DownloadBindingException misconfigured() {
        return new DownloadBindingException(ErrorCode.DATASET_MISCONFIGURED, List.of());
    }
}
