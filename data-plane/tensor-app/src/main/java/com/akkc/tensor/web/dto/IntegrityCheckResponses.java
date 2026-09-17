package com.akkc.tensor.web.dto;

import com.akkc.tensor.core.download.task.DownloadTaskService;
import com.akkc.tensor.core.integrity.*;
import com.akkc.tensor.core.integrity.IntegrityCheckRepository.*;
import com.akkc.tensor.plugin.api.integrity.*;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.*;
import java.util.*;
import java.util.function.Function;

/** Frozen public HTTP shapes for local integrity checks. */
public final class IntegrityCheckResponses {
    private IntegrityCheckResponses() {}

    public record Receipt(String requestId, UUID checkId, UUID submissionId, String pluginId,
            IntegrityTaskStatus status, int plannedUnits, Instant createdAt) {
        public static Receipt from(String requestId, TaskRecord task) {
            return new Receipt(requestId, task.checkId(), task.submissionId(), task.pluginId().value(),
                    task.status(), task.plannedUnits(), task.createdAt());
        }
    }

    public record Capability(String pluginId, boolean localCheckAvailable, String unavailableReason,
            String capabilityHash, Limits limits, List<Api> apis) {
        public Capability { apis = List.copyOf(apis); }
        public static Capability from(IntegrityCheckService.Capability value, DownloadTaskService downloads) {
            return new Capability(value.pluginId().value(), value.localCheckAvailable(), value.unavailableReason(),
                    value.capabilityHash(), Limits.from(value.limits()), value.apis().stream().map(api ->
                    Api.from(api, downloads)).toList());
        }
    }

    public record Limits(int maxSymbols, int maxRangeDays, int maxUnits, int queueCapacity, int workers,
            int scanBatchSize, Long maxScannedRowsPerUnit, int maxIssuesPerUnit,
            Long unitTimeoutSeconds, Long taskTimeoutSeconds) {
        public static Limits from(IntegrityCheckService.Settings value) {
            return new Limits(value.maxSymbols(), value.maxRangeDays(), value.maxUnits(), value.queueCapacity(),
                    value.workers(), value.scanBatchSize(), value.maxScannedRowsPerUnit(), value.maxIssuesPerUnit(),
                    value.unitTimeoutSeconds(), value.taskTimeoutSeconds());
        }
    }

    public record Api(String apiName, String displayName, Descriptor descriptor,
            DownloadCapabilitiesResponse downloadAvailability) {
        public static Api from(IntegrityCheckJson.ApiSnapshot value, DownloadTaskService downloads) {
            var definition = value.definition();
            return new Api(definition.datasetKey().apiName().value(), definition.displayName(),
                    Descriptor.from(value.descriptor()), DownloadCapabilitiesResponse.from(
                    downloads.capabilities(definition.datasetKey())));
        }
    }

    public record Descriptor(Dataset datasetKey, IntegrityDescriptor.ScopeKind scopeKind, String symbolField,
            String dateField, String dateLabel, String marketZone, String capabilityVersion,
            List<Dependency> dependencies, List<Rule> rules, List<String> limitations) {
        public Descriptor {
            dependencies = List.copyOf(dependencies); rules = List.copyOf(rules); limitations = List.copyOf(limitations);
        }
        public static Descriptor from(IntegrityDescriptor value) {
            return value == null ? null : new Descriptor(Dataset.from(value.datasetKey()), value.scopeKind(),
                    value.symbolField(), value.dateField(), value.dateLabel(), value.marketZone().getId(),
                    value.capabilityVersion(), value.dependencies().stream().map(Dependency::from).toList(),
                    value.rules().stream().map(Rule::from).toList(), value.limitations());
        }
    }

    public record Dataset(String pluginId, String apiName) {
        public static Dataset from(com.akkc.tensor.plugin.api.model.DatasetKey value) {
            return new Dataset(value.pluginId().value(), value.apiName().value());
        }
    }

    public record Dependency(Dataset datasetKey, List<String> columns, String purpose) {
        public Dependency { columns = List.copyOf(columns); }
        public static Dependency from(IntegrityDependency value) {
            return new Dependency(Dataset.from(value.datasetKey()), value.columns(), value.purpose());
        }
    }

    public record Rule(String ruleId, String version, String displayName, IntegrityRuleDescriptor.Dimension dimension,
            List<String> requiredColumns, List<Dependency> dependencies, String description) {
        public Rule { requiredColumns = List.copyOf(requiredColumns); dependencies = List.copyOf(dependencies); }
        public static Rule from(IntegrityRuleDescriptor value) {
            return new Rule(value.ruleId(), value.version(), value.displayName(), value.dimension(),
                    value.requiredColumns(), value.dependencies().stream().map(Dependency::from).toList(), value.description());
        }
    }

    public record TaskSummary(UUID checkId, UUID submissionId, String pluginId, JsonNode originalRequest,
            JsonNode scope, String capabilityHash, IntegrityTaskStatus status, int plannedUnits,
            Instant createdAt, Instant updatedAt, Instant startedAt, Instant finishedAt,
            String errorCode, String errorMessage) {
        public static TaskSummary from(TaskRecord value) {
            return new TaskSummary(value.checkId(), value.submissionId(), value.pluginId().value(),
                    value.originalRequest(), value.normalizedScope(), value.capabilityHash(), value.status(),
                    value.plannedUnits(), value.createdAt(), value.updatedAt(), value.startedAt(), value.finishedAt(),
                    value.errorCode(), value.errorMessage());
        }
    }

    public record Detail(UUID checkId, UUID submissionId, String pluginId, JsonNode originalRequest,
            JsonNode scope, String capabilityHash, IntegrityTaskStatus status, int plannedUnits,
            Instant createdAt, Instant updatedAt, Instant startedAt, Instant finishedAt,
            String errorCode, String errorMessage, IntegrityStatus overallStatus,
            Long completedUnits, Long errorUnits, Long notRunUnits, Map<IntegrityStatus, Long> statusCounts) {
        public Detail { statusCounts = Collections.unmodifiableMap(new LinkedHashMap<>(statusCounts)); }
        public static Detail from(Progress value) {
            var task = value.task();
            var counts = new LinkedHashMap<IntegrityStatus, Long>();
            for (var status : List.of(IntegrityStatus.PASS, IntegrityStatus.FAIL, IntegrityStatus.WARN,
                    IntegrityStatus.UNKNOWN, IntegrityStatus.NOT_APPLICABLE)) counts.put(status, value.statusCounts().getOrDefault(status, 0L));
            return new Detail(task.checkId(), task.submissionId(), task.pluginId().value(), task.originalRequest(),
                    task.normalizedScope(), task.capabilityHash(), task.status(), task.plannedUnits(), task.createdAt(),
                    task.updatedAt(), task.startedAt(), task.finishedAt(), task.errorCode(), task.errorMessage(),
                    value.overallStatus(), value.completedUnits(), value.errorUnits(), value.notRunUnits(), counts);
        }
    }

    public record Result(UUID resultId, UUID checkId, JsonNode report) {
        public static Result from(ResultRecord value) { return new Result(value.resultId(), value.checkId(), value.report()); }
    }

    public record Issue(Long issueId, UUID resultId, String ruleId, String ruleVersion, JsonNode issue) {
        public static Issue from(IssueRecord value) {
            return new Issue(value.issueId(), value.resultId(), value.ruleId(), value.ruleVersion(), value.issue());
        }
    }

    public record Page<T>(int page, int pageSize, Long total, List<T> items) {
        public Page { items = List.copyOf(items); }
        public static <S, T> Page<T> from(IntegrityCheckRepository.Page<S> value, Function<S, T> mapping) {
            return new Page<>(value.page(), value.pageSize(), value.total(), value.items().stream().map(mapping).toList());
        }
    }
}
