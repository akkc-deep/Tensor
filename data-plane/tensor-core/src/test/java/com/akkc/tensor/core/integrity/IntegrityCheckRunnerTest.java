package com.akkc.tensor.core.integrity;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static com.akkc.tensor.plugin.api.integrity.IntegrityStatus.UNKNOWN;
import com.akkc.tensor.core.integrity.IntegrityCheckRepository.*;
import com.akkc.tensor.core.registry.PluginRegistry;
import com.akkc.tensor.plugin.api.integrity.*;
import com.akkc.tensor.plugin.api.model.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class IntegrityCheckRunnerTest {
    @Test void largePositiveTimeoutSaturatesInsteadOfWrappingIntoThePast() {
        var now = Instant.parse("2026-09-16T00:00:00Z");
        assertThat(IntegrityCheckRunner.plusSeconds(now, 120)).isEqualTo(Instant.parse("2026-09-16T00:02:00Z"));
        assertThat(IntegrityCheckRunner.plusSeconds(now, Long.MAX_VALUE)).isEqualTo(Instant.MAX);
        assertThat(IntegrityCheckRunner.plusSeconds(Instant.MAX, 1)).isEqualTo(Instant.MAX);
    }

    @ParameterizedTest
    @CsvSource({"1,10,false", "10,1,true"})
    void crossingDeadlineBetweenPrecheckAndBudgetConstructionSavesUnitTimeoutBeforeTaskDecision(
            long unitSeconds, long taskSeconds, boolean taskExpired) {
        var start = Instant.parse("2026-09-16T00:00:00Z");
        var deadline = start.plusSeconds(Math.min(unitSeconds, taskSeconds));
        var clock = new StepClock(start, start, start, start, deadline.minusNanos(1), deadline.plusNanos(1));
        var repository = mock(IntegrityCheckRepository.class);
        var plugin = new IntegrityCheckServiceTest.LocalPlugin();
        var plugins = new PluginRegistry(List.of(plugin));
        var json = new IntegrityCheckJson();
        var settings = new IntegrityCheckService.Settings(100,36600,4000,20,1,500,500000,20000,
                unitSeconds,taskSeconds);
        var service = new IntegrityCheckService(plugins, IntegrityCheckServiceTest.catalog(plugin.definitions()), repository,
                json, new IntegrityCheckQueue(20), Clock.fixed(start, ZoneOffset.UTC), settings);
        var capability = service.capability(IntegrityCheckServiceTest.PLUGIN);
        var target = capability.apis().stream()
                .filter(api -> api.definition().datasetKey().apiName().value().equals("daily")).findFirst().orElseThrow();
        var checkId = UUID.randomUUID(); var resultId = UUID.randomUUID();
        var scope = new IntegrityScope(target.definition().datasetKey(), "A", LocalDate.parse("2026-01-01"),
                LocalDate.parse("2026-01-01"), start, null);
        var pending = new IntegrityUnitResult(scope, target.descriptor(), json.definitionHash(target.definition()), null,
                IntegrityUnitStatus.PENDING, UNKNOWN, UNKNOWN, UNKNOWN,
                new IntegrityStatistics(null,null,null,null,null,null,null), List.of(), List.of(), null,
                true, false, "PENDING", "Waiting");
        var empty = json.readValue("{}");
        var task = new TaskRecord(checkId,UUID.randomUUID(),IntegrityCheckServiceTest.PLUGIN,"r".repeat(64),
                capability.capabilityHash(),empty,empty,empty,IntegrityTaskStatus.RUNNING,1,start,start,start,null,null,null);
        var unit = new ResultRecord(resultId,checkId,json.unitKey(scope.datasetKey().apiName(),scope.symbol()),
                scope.datasetKey().pluginId(),scope.datasetKey().apiName(),scope.symbol(),pending.definitionHash(),
                json.readValue(json.write(target)),json.readDocument(json.writeDocument(pending)));
        when(repository.start(checkId,start)).thenReturn(true);
        when(repository.find(checkId)).thenReturn(Optional.of(task));
        when(repository.results(checkId,null,1,100)).thenReturn(new Page<>(1,100,1,List.of(unit)));

        new IntegrityCheckRunner(repository,service,plugins,json,mock(IntegrityUnitEvaluator.class),clock,settings)
                .run(checkId,() -> false);

        verify(repository).saveResult(eq(checkId),eq(resultId),argThat(result ->
                result.unitStatus() == IntegrityUnitStatus.ERROR
                        && result.reasonCode().equals("UNIT_TIME_BUDGET_EXHAUSTED")),eq(List.of()));
        if (taskExpired) {
            verify(repository).terminate(eq(checkId),eq(IntegrityTaskStatus.INTERRUPTED),
                    eq("TASK_TIME_BUDGET_EXHAUSTED"),any());
            verify(repository,never()).complete(any(),any());
        } else {
            verify(repository).complete(eq(checkId),any());
            verify(repository,never()).terminate(any(),any(),any(),any());
        }
    }

    static final class StepClock extends Clock {
        private final ArrayDeque<Instant> values;
        private Instant last;
        StepClock(Instant... values) { this.values = new ArrayDeque<>(List.of(values)); last = values[0]; }
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { if (!values.isEmpty()) last = values.removeFirst(); return last; }
    }
}
