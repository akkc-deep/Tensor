package com.akkc.tensor.observability;

import java.util.Map;
import java.util.Set;
import org.springframework.boot.actuate.autoconfigure.health.HealthEndpointProperties;
import org.springframework.boot.actuate.endpoint.ApiVersion;
import org.springframework.boot.actuate.endpoint.web.annotation.EndpointWebExtension;
import org.springframework.boot.actuate.health.HealthComponent;
import org.springframework.boot.actuate.health.HealthContributorRegistry;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.actuate.health.HealthEndpointGroups;
import org.springframework.boot.actuate.health.HealthEndpointWebExtension;
import org.springframework.boot.actuate.health.StatusAggregator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EndpointWebExtension(endpoint = HealthEndpoint.class)
public final class StatusOnlyHealthEndpointWebExtension extends HealthEndpointWebExtension {
    public StatusOnlyHealthEndpointWebExtension(
            HealthContributorRegistry registry,
            HealthEndpointGroups groups,
            HealthEndpointProperties properties) {
        super(registry, groups, properties.getLogging().getSlowIndicatorThreshold());
    }

    @Override
    protected HealthComponent aggregateContributions(
            ApiVersion apiVersion,
            Map<String, HealthComponent> contributions,
            StatusAggregator statusAggregator,
            boolean showComponents,
            Set<String> groupNames) {
        // Boot includes root group names even when components and details are hidden.
        return super.aggregateContributions(
                apiVersion, contributions, statusAggregator, showComponents, null);
    }
}
