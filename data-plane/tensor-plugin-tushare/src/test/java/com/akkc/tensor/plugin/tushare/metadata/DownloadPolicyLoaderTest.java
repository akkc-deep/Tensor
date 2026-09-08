package com.akkc.tensor.plugin.tushare.metadata;

import static org.assertj.core.api.Assertions.*;
import com.akkc.tensor.plugin.api.download.*;
import com.akkc.tensor.plugin.api.descriptor.ParameterDescriptor;
import com.akkc.tensor.plugin.api.error.*;
import com.akkc.tensor.plugin.api.model.ApiName;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.*;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

class DownloadPolicyLoaderTest {
    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());
    private static final ClassPathResource RESOURCE = new ClassPathResource("download/tushare-pro-policies.yaml");
    private final DownloadPolicyLoader loader = new DownloadPolicyLoader();

    @Test void loadsExactly49PoliciesWithFrozenEvidenceAndProjection() {
        var policies = loader.load(RESOURCE);
        var definitions = new DatasetDefinitionLoader().loadAll(new PathMatchingResourcePatternResolver(), "classpath*:datasets/tushare_pro/*.yaml");
        assertThat(policies.keySet()).containsExactlyInAnyOrderElementsOf(definitions.stream().map(d -> d.datasetKey().apiName()).toList());
        assertThat(policies.values()).filteredOn(p -> p.mode() == DownloadPolicy.Mode.TRADE_DATE_RANGE).hasSize(19);
        assertThat(policies.values()).filteredOn(p -> p.mode() == DownloadPolicy.Mode.ANN_DATE_RANGE).hasSize(15);
        assertThat(policies.values()).filteredOn(p -> p.mode() == DownloadPolicy.Mode.MONTH_RANGE).hasSize(1);
        assertThat(policies.values()).filteredOn(p -> p.mode() == DownloadPolicy.Mode.NATIVE_RANGE).hasSize(3);
        assertThat(policies.values()).filteredOn(p -> p.mode() == DownloadPolicy.Mode.ORIGINAL_PARAMS).hasSize(11);
        for (var status : DownloadPolicy.RequestEvidenceStatus.values()) {
            assertThat(policies.values()).filteredOn(p -> p.requestEvidenceStatus() == status)
                    .hasSize(switch(status) {case DOCUMENTED_CANDIDATE -> 40; case UNCONFIRMED -> 8; case CONFLICT -> 1;});
        }
        for (var profile : DownloadPolicy.CalendarProfile.values()) {
            assertThat(policies.values()).filteredOn(p -> p.calendarProfile() == profile)
                    .hasSize(switch(profile) {case C_A -> 12; case C_M, C_X -> 1; case C_S -> 3; case C_N -> 2;});
        }
        for (var status : DownloadPolicy.CalendarEvidenceStatus.values()) {
            assertThat(policies.values()).filteredOn(p -> p.calendarEvidenceStatus() == status)
                    .hasSize(switch(status) {case DOCUMENTED -> 1; case UNCONFIRMED -> 16; case CONFLICT -> 2;});
        }
        assertThat(policies.get(ApiName.of("forecast")).sourceRequestMode()).isEqualTo(DownloadPolicy.SourceRequestMode.DATE);
        assertThat(policies.get(ApiName.of("monthly")).sourceRequestMode()).isEqualTo(DownloadPolicy.SourceRequestMode.DATE);
        assertThat(policies.get(ApiName.of("monthly")).calendarEvidenceStatus()).isEqualTo(DownloadPolicy.CalendarEvidenceStatus.CONFLICT);
        for (var definition : definitions) {
            var api = definition.datasetKey().apiName();
            var policy = policies.get(api);
            assertThat(policy.recoveryPolicy().mode()).isEqualTo(RecoveryPolicy.Mode.REQUEST);
            assertThat(policy.recoveryPolicy().independentRecoveryVerified()).isFalse();
            assertThat(policy.completenessPolicy().status()).isEqualTo(DownloadPolicy.CompletenessStatus.UNCONFIRMED);
            assertThat(policy.completenessPolicy().paginationStatus()).isEqualTo(DownloadPolicy.CompletenessStatus.UNCONFIRMED);
            assertThat(policy.evidenceRefs()).contains("docs/research/RANGE-T01-source-evidence.json#apiName=" + api.value());
            if (policy.requestEvidenceStatus() == DownloadPolicy.RequestEvidenceStatus.UNCONFIRMED) assertThat(policy.sourceRequestMode()).isNull();
            var projected = DownloadParameterProjection.project(definition.parameters(), policy);
            if (policy.mode() == DownloadPolicy.Mode.ORIGINAL_PARAMS) {
                assertThat(projected).isEqualTo(definition.parameters()); assertThat(policy.limits()).isNull();
            } else {
                assertThat(projected).extracting(ParameterDescriptor::name).endsWith("start_date", "end_date");
                assertThat(policy.limits().maxRangeDays()).isEqualTo(31);
                var expectedAdditional = definition.parameters().stream().filter(p -> !Set.of("trade_date", "ann_date", "month", "start_date", "end_date").contains(p.name())).toList();
                assertThat(projected.subList(0, projected.size()-2)).isEqualTo(expectedAdditional);
            }
        }
        assertThatThrownBy(policies::clear).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test void rejectsMalformedAndInconsistentResourcesWithSafeDiagnostics() throws Exception {
        reject(root -> root.withArray("policies").remove(0));
        reject(root -> root.withArray("policies").add(root.withArray("policies").get(0).deepCopy()));
        reject(root -> root.withArray("policies").set(1, root.withArray("policies").get(0).deepCopy()));
        reject(root -> root.put("unknown", "secret-sentinel"));
        reject(root -> first(root).put("unknown", "secret-sentinel"));
        reject(root -> first(root).put("mode", "secret-sentinel"));
        reject(root -> first(root).put("calendarProfile", "C-N"));
        reject(root -> first(root).withObject("limits").put("maxRangeDays", 32));
        reject(root -> first(root).set("evidenceRefs", YAML.createArrayNode()));
        reject(root -> first(root).put("sourceDateParameter", "ann_date"));
        reject(root -> first(root).put("requestEvidenceStatus", "UNCONFIRMED"));
        reject(root -> first(root).withObject("completenessPolicy").put("status", "CONFIRMED"));
        reject(root -> first(root).withObject("recoveryPolicy").put("mode", "STOCK_TIME"));
        reject(root -> { var p = first(root).withObject("recoveryPolicy"); p.put("mode", "STOCK_TIME"); p.put("targetField", "ts_code"); p.put("timeField", "trade_date"); p.put("unitTimeType", "DATE"); p.put("independentRecoveryVerified", true); });
        String resource = RESOURCE.getContentAsString(StandardCharsets.UTF_8);
        rejectText(resource + "\n---\npolicies: []\n");
        rejectText(resource.replaceFirst("policies:", "policies: []\npolicies:"));
        rejectText("policies: [secret-sentinel");
    }

    private void reject(Consumer<ObjectNode> mutation) throws Exception {
        ObjectNode root = (ObjectNode) YAML.readTree(RESOURCE.getInputStream());
        mutation.accept(root); rejectText(YAML.writeValueAsString(root));
    }
    private void rejectText(String yaml) {
        assertThatThrownBy(() -> loader.load(new ByteArrayResource(yaml.getBytes(StandardCharsets.UTF_8))))
                .isInstanceOfSatisfying(TensorException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.DATASET_MISCONFIGURED);
                    assertThat(e.getMessage()).doesNotContain("secret-sentinel");
                    assertThat(e.getCause()).isNull();
                });
    }
    private static ObjectNode first(ObjectNode root) { return (ObjectNode) root.withArray("policies").get(0); }
}
