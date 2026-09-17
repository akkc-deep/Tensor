package com.akkc.tensor.config;

import static org.assertj.core.api.Assertions.*;
import com.akkc.tensor.core.integrity.IntegrityCheckService;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.boot.context.properties.bind.*;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class IntegrityCheckPropertiesTest {
    @Test void bindsDefaultsAndEveryCustomValue() {
        assertThat(bind(Map.of()).toSettings()).isEqualTo(IntegrityCheckService.Settings.defaults());
        assertThat(bind(Map.of("max-symbols", "3", "max-range-days", "2", "max-units", "7", "queue-capacity", "4",
                "workers", "1", "scan-batch-size", "8", "max-scanned-rows-per-unit", "9", "max-issues-per-unit", "10",
                "unit-timeout-seconds", "11", "task-timeout-seconds", "12")).toSettings())
                .isEqualTo(new IntegrityCheckService.Settings(3, 2, 7, 4, 1, 8, 9, 10, 11, 12));
    }
    @ParameterizedTest
    @ValueSource(strings = {"max-symbols", "max-range-days", "max-units", "queue-capacity", "workers", "scan-batch-size",
            "max-scanned-rows-per-unit", "max-issues-per-unit", "unit-timeout-seconds", "task-timeout-seconds"})
    void everyNonPositiveSettingPreventsStartup(String name) {
        for (var value : new String[]{"0", "-1"}) failsStartup(name, value);
    }
    @Test void multipleWorkersPreventStartup() { failsStartup("workers", "2"); }
    private static void failsStartup(String name, String value) {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test",
                    Map.of("tensor.integrity." + name, value)));
            context.register(Binding.class);
            assertThatThrownBy(context::refresh).hasRootCauseInstanceOf(IllegalArgumentException.class);
        }
    }
    @EnableConfigurationProperties(IntegrityCheckProperties.class) static class Binding {}
    private static IntegrityCheckProperties bind(Map<String, String> values) {
        var source = new MapConfigurationPropertySource();
        values.forEach((key, value) -> source.put("tensor.integrity." + key, value));
        return new Binder(source).bindOrCreate("tensor.integrity", Bindable.of(IntegrityCheckProperties.class));
    }
}
