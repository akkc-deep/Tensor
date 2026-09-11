package com.akkc.tensor.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.akkc.tensor.core.download.task.DownloadTaskRunner;
import com.akkc.tensor.core.download.task.DownloadTaskService;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class DownloadTaskPropertiesTest {
    @Test
    void bindsDefaultsAndSharesTheExistingCoreSettings() {
        DownloadTaskProperties properties = bind(Map.of());
        assertThat(properties.toServiceSettings()).isEqualTo(DownloadTaskService.Settings.defaults());
        assertThat(properties.toRunnerSettings()).isEqualTo(DownloadTaskRunner.Settings.defaults());
    }

    @Test
    void overridesEverySettingWithoutLosingDisabledOrTheCommonRangeLimit() {
        DownloadTaskProperties properties = bind(Map.of(
                "enabled", "false", "max-queued-tasks", "7", "max-range-days", "31",
                "max-batch-nodes", "19", "max-requests-per-run", "13", "max-run-duration", "120ms",
                "max-source-rows-per-task", "987"));
        assertThat(properties.toServiceSettings()).isEqualTo(new DownloadTaskService.Settings(false, 7, 31));
        assertThat(properties.toRunnerSettings()).isEqualTo(
                new DownloadTaskRunner.Settings(false, 31, 19, 13, Duration.ofMillis(120), 987));
    }

    @Test
    void rejectsEveryNonPositiveLimitAndAnOutOfRangeNodeBudgetAtBinding() {
        for (String name : new String[] {"max-queued-tasks", "max-range-days", "max-batch-nodes",
                "max-requests-per-run", "max-source-rows-per-task"}) {
            for (String value : new String[] {"0", "-1"}) {
                assertThatThrownBy(() -> bind(Map.of(name, value))).hasRootCauseInstanceOf(IllegalArgumentException.class);
            }
        }
        assertThatThrownBy(() -> bind(Map.of("max-batch-nodes", "1000000")))
                .hasRootCauseMessage("Invalid download runner settings");
    }

    @Test
    void rejectsDurationsThatAreMissingNonPositiveSubMillisecondOrOverflowing() {
        for (Duration duration : new Duration[] {null, Duration.ZERO, Duration.ofMillis(-1),
                Duration.ofNanos(999999), Duration.ofSeconds(Long.MAX_VALUE)}) {
            assertThatThrownBy(() -> new DownloadTaskProperties(true, 100, 36600, 10000, 5000, duration, 1000000))
                    .isInstanceOf(IllegalArgumentException.class).hasMessage("Invalid download runner settings");
        }
        assertThat(bind(Map.of("max-run-duration", "1ms")).maxRunDuration()).isEqualTo(Duration.ofMillis(1));
    }

    private static DownloadTaskProperties bind(Map<String, String> values) {
        MapConfigurationPropertySource source = new MapConfigurationPropertySource();
        values.forEach((key, value) -> source.put("tensor.download-tasks." + key, value));
        return new Binder(source).bindOrCreate("tensor.download-tasks", Bindable.of(DownloadTaskProperties.class));
    }
}
