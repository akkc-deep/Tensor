package com.akkc.tensor.config;

import com.akkc.tensor.core.registry.AdapterRegistry;
import com.akkc.tensor.core.registry.PluginRegistry;
import com.akkc.tensor.core.validation.ParameterValidator;
import com.akkc.tensor.web.download.DownloadDescriptorResolver;
import com.akkc.tensor.web.download.DownloadParameterResolver;
import com.akkc.tensor.web.download.DownloadRequestDeserializer;
import com.akkc.tensor.web.dto.DownloadRequest;
import com.akkc.tensor.core.download.task.DownloadTaskService;
import com.akkc.tensor.web.download.DownloadTaskRequestDeserializer;
import com.akkc.tensor.web.download.DownloadTaskControlRequestDeserializer;
import com.akkc.tensor.web.dto.DownloadTaskRequest;
import com.akkc.tensor.web.dto.DownloadTaskControlRequest;
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
            ParameterValidator validator) {
        return new DownloadParameterResolver(descriptors, validator);
    }

    @Bean
    public DownloadRequestDeserializer downloadRequestDeserializer(DownloadParameterResolver resolver) {
        return new DownloadRequestDeserializer(resolver);
    }

    @Bean
    public Module downloadRequestJacksonModule(DownloadRequestDeserializer deserializer) {
        return new SimpleModule("tensor-download-request").addDeserializer(DownloadRequest.class, deserializer);
    }

    @Bean
    public DownloadTaskRequestDeserializer downloadTaskRequestDeserializer(
            DownloadTaskService tasks, DownloadParameterResolver resolver) {
        return new DownloadTaskRequestDeserializer(tasks, resolver);
    }

    @Bean
    public DownloadTaskControlRequestDeserializer downloadTaskControlRequestDeserializer() {
        return new DownloadTaskControlRequestDeserializer();
    }

    @Bean
    public Module downloadTaskRequestJacksonModule(DownloadTaskRequestDeserializer submission,
            DownloadTaskControlRequestDeserializer control) {
        return new SimpleModule("tensor-download-task-request")
                .addDeserializer(DownloadTaskRequest.class, submission)
                .addDeserializer(DownloadTaskControlRequest.class, control);
    }
}
