package com.akkc.tensor.core.integrity;

import static org.assertj.core.api.Assertions.*;
import static com.akkc.tensor.core.integrity.IntegrityCheckServiceTest.*;
import com.akkc.tensor.plugin.api.integrity.*;
import com.akkc.tensor.plugin.api.model.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class IntegrityReadPlannerTest {
    final LocalPlugin plugin = new LocalPlugin();
    final IntegrityCheckJson json = new IntegrityCheckJson();
    final IntegrityScope scope = new IntegrityScope(new DatasetKey(PLUGIN, new ApiName("daily")), "A",
            LocalDate.parse("2026-01-01"), LocalDate.parse("2026-01-02"), NOW, null);

    @Test void mapsOnlyDeclaredReferencesWithoutChangingSourceRangeOrConditions() {
        var apis = snapshots();
        var request = new IntegrityReadRequest(new DatasetKey(PLUGIN, new ApiName("snapshot")),
                List.of("symbol"), Map.of(), null, null, false, "stock");
        var plan = new IntegrityReadPlanner().plan(scope, apis.getFirst(), apis, List.of(request));
        assertThat(plan.target()).isEqualTo(apis.getFirst().descriptor());
        assertThat(plan.references()).singleElement().satisfies(p -> {
            assertThat(p.descriptor().datasetKey().apiName().value()).isEqualTo("snapshot");
            assertThat(p.range()).isNull(); assertThat(p.fixedEqualities()).isEmpty();
            assertThat(p.purpose()).isEqualTo("stock");
        });
        assertThat(plugin.integrityReferenceReads(scope)).isEmpty();
        assertThatThrownBy(() -> plugin.integrityReferenceReads(null)).isInstanceOf(NullPointerException.class);
    }

    @Test void rejectsUndeclaredProjectionPurposeSourceAndDuplicateOrNullDateProposals() {
        var apis = snapshots(); var target = apis.getFirst();
        var key = new DatasetKey(PLUGIN, new ApiName("snapshot"));
        var good = new IntegrityReadRequest(key, List.of("symbol"), Map.of(), null, null, false, "stock");
        for (var bad : List.of(
                new IntegrityReadRequest(key, List.of("day"), Map.of(), null, null, false, "stock"),
                new IntegrityReadRequest(key, List.of("symbol"), Map.of(), null, null, false, "unknown"),
                new IntegrityReadRequest(new DatasetKey(new PluginId("foreign"), key.apiName()),
                        List.of("symbol"), Map.of(), null, null, false, "stock"),
                new IntegrityReadRequest(key, List.of("symbol"), Map.of(), "day", null, true, "stock")))
            invalid(() -> new IntegrityReadPlanner().plan(scope, target, apis, List.of(bad)));
        invalid(() -> new IntegrityReadPlanner().plan(scope, target, apis, List.of(good, good)));
        invalid(() -> new IntegrityReadPlanner().plan(scope, target, List.of(target), List.of(good)));
    }
    List<IntegrityCheckJson.ApiSnapshot> snapshots() {
        plugin.references = true;
        var definitions = plugin.definitions();
        return definitions.stream().map(d -> new IntegrityCheckJson.ApiSnapshot(d,
                plugin.integrityDescriptor(d.datasetKey().apiName()).orElse(null), List.of(), List.of())).toList();
    }
    static void invalid(Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(IntegrityReadException.class,
                e -> assertThat(e.reasonCode()).isEqualTo("INVALID_READ_REQUEST"));
    }
}
