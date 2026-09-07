package com.akkc.tensor.config;

import com.akkc.tensor.core.registry.AdapterRegistry;
import com.akkc.tensor.core.registry.PluginRegistry;
import com.akkc.tensor.core.validation.ParameterValidator;
import com.akkc.tensor.observability.OperationLogger;
import com.akkc.tensor.web.download.DownloadDescriptorResolver;
import com.akkc.tensor.web.download.DownloadParameterResolver;
import com.akkc.tensor.web.download.DownloadRequestDeserializer;
import com.akkc.tensor.web.dto.DownloadRequest;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public final class DownloadBindingConfiguration {
    @Bean
    public DownloadDescriptorResolver downloadDescriptorResolver(PluginRegistry plugins, AdapterRegistry adapters) {
        return new DownloadDescriptorResolver(plugins, adapters);
    }

    @Bean
    public DownloadParameterResolver downloadParameterResolver(DownloadDescriptorResolver descriptors,
            ParameterValidator validator, OperationLogger operations) {
        return new DownloadParameterResolver(descriptors, validator, operations);
    }

    @Bean
    public DownloadRequestDeserializer downloadRequestDeserializer(DownloadParameterResolver resolver) {
        return new DownloadRequestDeserializer(resolver);
    }

    @Bean
    public Module downloadRequestJacksonModule(DownloadRequestDeserializer deserializer) {
        return new SimpleModule("tensor-download-request").addDeserializer(DownloadRequest.class, deserializer);
    }
}
