package com.akkc.tensor.config;

import com.akkc.tensor.core.integrity.IntegrityCheckService.Settings;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("tensor.integrity")
public record IntegrityCheckProperties(
        @DefaultValue("100") int maxSymbols,
        @DefaultValue("36600") int maxRangeDays,
        @DefaultValue("4000") int maxUnits,
        @DefaultValue("20") int queueCapacity,
        @DefaultValue("1") int workers,
        @DefaultValue("500") int scanBatchSize,
        @DefaultValue("500000") long maxScannedRowsPerUnit,
        @DefaultValue("20000") int maxIssuesPerUnit,
        @DefaultValue("120") long unitTimeoutSeconds,
        @DefaultValue("1800") long taskTimeoutSeconds) {
    public IntegrityCheckProperties {
        new Settings(maxSymbols, maxRangeDays, maxUnits, queueCapacity, workers, scanBatchSize,
                maxScannedRowsPerUnit, maxIssuesPerUnit, unitTimeoutSeconds, taskTimeoutSeconds);
    }
    public Settings toSettings() {
        return new Settings(maxSymbols, maxRangeDays, maxUnits, queueCapacity, workers, scanBatchSize,
                maxScannedRowsPerUnit, maxIssuesPerUnit, unitTimeoutSeconds, taskTimeoutSeconds);
    }
}
