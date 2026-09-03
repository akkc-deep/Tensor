package com.akkc.tensor.web.dto;

import com.akkc.tensor.plugin.api.descriptor.PluginDescriptor;
import java.util.Objects;

public record DataSourceResponse(
        String pluginId,
        String displayName,
        String description,
        boolean enabled,
        boolean credentialConfigured,
        boolean downloadAvailable,
        String unavailableReason) {

    public DataSourceResponse {
        Objects.requireNonNull(pluginId, "pluginId");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(description, "description");
    }

    public static DataSourceResponse from(PluginDescriptor descriptor) {
        Objects.requireNonNull(descriptor, "descriptor");
        return new DataSourceResponse(
                descriptor.pluginId().value(),
                descriptor.displayName(),
                descriptor.description(),
                descriptor.enabled(),
                descriptor.credentialConfigured(),
                descriptor.downloadAvailable(),
                descriptor.unavailableReason());
    }
}
