package com.akkc.tensor.core.integrity;

import com.akkc.tensor.core.integrity.IntegrityCheckJson.ApiSnapshot;
import com.akkc.tensor.plugin.api.integrity.*;
import java.util.*;

/** Converts source proposals to immutable permits; the reader still validates the full plan. */
final class IntegrityReadPlanner {
    IntegrityReadPlan plan(IntegrityScope scope, ApiSnapshot target, List<ApiSnapshot> pluginSnapshot,
            List<IntegrityReadRequest> references) {
        try {
            if (!scope.datasetKey().equals(target.definition().datasetKey())) throw new IllegalArgumentException();
            var permits = new ArrayList<IntegrityReadPlan.ReferencePermit>();
            var identities = new HashSet<List<Object>>();
            for (var request : references) {
                if (!request.datasetKey().pluginId().equals(scope.datasetKey().pluginId()) || request.nullDates()
                        || !identities.add(List.of(request.datasetKey(), request.purpose()))) throw new IllegalArgumentException();
                var dependency = target.descriptor().dependencies().stream()
                        .filter(d -> d.datasetKey().equals(request.datasetKey()) && d.purpose().equals(request.purpose()))
                        .findFirst().orElseThrow();
                if (!dependency.columns().containsAll(request.columns())) throw new IllegalArgumentException();
                var descriptor = pluginSnapshot.stream().filter(api -> api.definition().datasetKey().equals(request.datasetKey()))
                        .map(ApiSnapshot::descriptor).filter(Objects::nonNull).findFirst().orElseThrow();
                permits.add(new IntegrityReadPlan.ReferencePermit(descriptor, request.dateField(), request.dateRange(),
                        request.equalities(), request.purpose()));
            }
            return new IntegrityReadPlan(target.descriptor(), permits);
        } catch (RuntimeException failure) {
            throw new IntegrityReadException("INVALID_READ_REQUEST", "Invalid integrity reference request");
        }
    }
}
