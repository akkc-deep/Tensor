package com.akkc.tensor.plugin.api.descriptor;

public record PluginReadiness(
        boolean enabled,
        boolean credentialConfigured,
        boolean downloadAvailable,
        String unavailableReason
) {
    public PluginReadiness {
        if (downloadAvailable) {
            if (!enabled || !credentialConfigured) {
                throw new IllegalArgumentException("downloadAvailable requires enabled and credentialConfigured");
            }
            if (unavailableReason != null) {
                throw new IllegalArgumentException("downloadAvailable requires no unavailableReason");
            }
        } else if (unavailableReason == null || unavailableReason.isBlank()) {
            throw new IllegalArgumentException("unavailable downloads require a non-blank reason");
        }
    }
}
