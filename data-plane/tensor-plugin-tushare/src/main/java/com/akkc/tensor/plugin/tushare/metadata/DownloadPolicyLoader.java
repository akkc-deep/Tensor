package com.akkc.tensor.plugin.tushare.metadata;

import com.akkc.tensor.plugin.api.download.DownloadPolicy;
import com.akkc.tensor.plugin.api.download.DownloadPolicy.*;
import com.akkc.tensor.plugin.api.download.RecoveryPolicy;
import com.akkc.tensor.plugin.api.error.ErrorCode;
import com.akkc.tensor.plugin.api.error.TensorException;
import com.akkc.tensor.plugin.api.model.ApiName;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion.VersionFlag;
import com.networknt.schema.resource.DisallowSchemaLoader;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.core.io.Resource;

public final class DownloadPolicyLoader {
    private static final String SCHEMA = "download/tushare-pro-policies.schema.json";
    private static final ObjectMapper YAML = new ObjectMapper(YAMLFactory.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build())
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    public Map<ApiName, DownloadPolicy> load(Resource resource) {
        Objects.requireNonNull(resource, "resource");
        try (InputStream input = resource.getInputStream();
                InputStream schemaInput = DownloadPolicyLoader.class.getClassLoader().getResourceAsStream(SCHEMA)) {
            if (schemaInput == null) throw invalid("schema resource");
            var schema = JsonSchemaFactory.getInstance(VersionFlag.V202012,
                    builder -> builder.schemaLoaders(loaders -> loaders.values(values -> {
                        values.clear(); values.add(DisallowSchemaLoader.getInstance());
                    }))).getSchema(new ObjectMapper().readTree(schemaInput));
            var node = YAML.readTree(input);
            if (!schema.validate(node).isEmpty()) throw invalid("schema rules");
            var raw = YAML.treeToValue(node, RawPolicies.class);
            Map<ApiName, DownloadPolicy> policies = new LinkedHashMap<>();
            for (RawPolicy value : raw.policies()) {
                ApiName api = ApiName.of(value.apiName());
                DownloadPolicy policy = new DownloadPolicy(value.mode(), value.dateSemantic(), value.description(),
                        value.calendarProfile() == null ? null : CalendarProfile.valueOf(value.calendarProfile().replace('-', '_')),
                        value.limits(), value.sourceRequestMode(), value.sourceDateParameter(), value.requestEvidenceStatus(),
                        value.batchPlanning(), value.recoveryPolicy(), value.completenessPolicy(), value.calendarEvidenceStatus(), value.evidenceRefs());
                if (policies.putIfAbsent(api, policy) != null) throw invalid("duplicate apiName " + api.value());
            }
            return Map.copyOf(policies);
        } catch (PolicyMisconfiguredException exception) {
            throw exception;
        } catch (Exception exception) {
            // Never expose parser snippets, resource paths or arbitrary input values.
            throw invalid("resource syntax or policy invariants");
        }
    }

    private static PolicyMisconfiguredException invalid(String rule) {
        return new PolicyMisconfiguredException("Invalid tushare-pro-policies.yaml: " + rule);
    }

    private record RawPolicies(List<RawPolicy> policies) {}
    private record RawPolicy(String apiName, Mode mode, DateSemantic dateSemantic, String description,
            String calendarProfile, Limits limits, SourceRequestMode sourceRequestMode, String sourceDateParameter,
            RequestEvidenceStatus requestEvidenceStatus, BatchPlanning batchPlanning, RecoveryPolicy recoveryPolicy,
            CompletenessPolicy completenessPolicy, CalendarEvidenceStatus calendarEvidenceStatus, List<String> evidenceRefs) {}

    private static final class PolicyMisconfiguredException extends TensorException {
        private PolicyMisconfiguredException(String message) { super(ErrorCode.DATASET_MISCONFIGURED, message); }
    }
}
