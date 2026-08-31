package com.akkc.tensor.plugin.api;

import com.akkc.tensor.plugin.api.descriptor.PluginDescriptor;
import com.akkc.tensor.plugin.api.descriptor.PluginReadiness;
import com.akkc.tensor.plugin.api.download.DownloadEnvelope;
import com.akkc.tensor.plugin.api.model.ApiName;
import java.util.Map;

public interface DataSourcePlugin {
    PluginDescriptor descriptor();

    PluginReadiness readiness();

    DownloadEnvelope download(ApiName apiName, Map<String, Object> params);
}
