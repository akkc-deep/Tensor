package com.akkc.tensor.web;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import java.io.IOException;
import java.math.BigDecimal;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public final class JacksonPrecisionConfiguration {
    private static final String MODULE_NAME = "tensor-precision";

    @Bean
    public Module precisionModule() {
        SimpleModule module = new SimpleModule(MODULE_NAME);
        module.addSerializer(Long.class, ToStringSerializer.instance);
        module.addSerializer(BigDecimal.class, new JsonSerializer<>() {
            @Override
            public void serialize(
                    BigDecimal value,
                    JsonGenerator generator,
                    SerializerProvider serializers) throws IOException {
                generator.writeString(value.toPlainString());
            }
        });
        return module;
    }
}
