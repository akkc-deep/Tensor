package com.akkc.tensor.core.download.task;

import com.akkc.tensor.core.catalog.DatasetCatalog;
import com.akkc.tensor.core.registry.*;
import com.akkc.tensor.core.validation.ParameterValidator;
import com.akkc.tensor.core.download.task.DownloadTaskRepository.*;
import com.akkc.tensor.core.persistence.WriteCounts;
import com.akkc.tensor.plugin.api.*;
import com.akkc.tensor.plugin.api.dataset.*;
import com.akkc.tensor.plugin.api.descriptor.*;
import com.akkc.tensor.plugin.api.download.*;
import com.akkc.tensor.plugin.api.download.batch.*;
import com.akkc.tensor.plugin.api.error.*;
import com.akkc.tensor.plugin.api.model.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class DownloadTaskRunnerTest {
    static final DatasetKey KEY = DatasetKey.of(PluginId.of("runner_test"), ApiName.of("prices"));
    static final Instant NOW = Instant.parse("2026-09-11T00:00:00Z");
    static final UUID RUN = UUID.randomUUID();
    static final DateRange RANGE = dates("20240228", "20240301");

    @Test void singleEmptyResultStillAdaptsAndAtomicallyCommits() throws Exception {
        Harness h = new Harness();
        h.source.response = p -> h.source.envelope(p, 0);
        h.submit(DownloadMode.SINGLE);
        var result = h.runner().runNext(() -> false);
        assertThat(result.disposition()).isEqualTo(DownloadTaskRunner.Disposition.FINISHED);
        assertThat(h.task.status()).isEqualTo(DownloadTask.Status.SUCCEEDED);
        assertThat(h.source.requests).hasSize(1);
        assertThat(h.source.contexts).hasSize(1);
        assertThat(h.source.plans).isZero();
        assertThat(h.source.assessments).isZero();
        assertThat(h.source.adaptations).isOne();
        assertThat(h.committed).hasSize(1);
        assertThat(h.counts().sourceRows()).isZero();
        assertThat(h.reservations).isOne();
    }

    @Test void failedSecondBatchDoesNotPreventThirdBatch() throws Exception {
        Harness h = new Harness();
        h.source.mode = BatchDownloadDescriptor.PlanningMode.CALENDAR_DAYS;
        h.source.response = p -> {
            if (p.get("from").equals("20240229")) throw failure(ErrorCode.SOURCE_NETWORK_ERROR);
            return h.source.envelope(p, 1);
        };
        h.submit(DownloadMode.RANGE);
        var result = h.runner().runNext(() -> false);
        assertThat(result.error()).isEqualTo(ErrorCode.SOURCE_NETWORK_ERROR);
        assertThat(h.task.status()).isEqualTo(DownloadTask.Status.PARTIAL_FAILED);
        assertThat(h.source.requests).extracting(p -> p.get("from"))
                .containsExactly("20240228", "20240229", "20240301");
        assertThat(h.counts()).isEqualTo(new Counts(3, 0, 0, 2, 1, 0, 2, 2, 0));
        assertThat(h.batches.values()).extracting(DownloadBatch::attemptCount).containsOnly(1);
    }

    @ParameterizedTest @EnumSource(ErrorCode.class)
    void classifiesEveryFailureWithoutAutomaticRetry(ErrorCode error) throws Exception {
        Harness h = new Harness();
        h.source.mode = BatchDownloadDescriptor.PlanningMode.CALENDAR_DAYS;
        h.source.response = p -> { if(p.get("from").equals("20240229")) throw failure(error); return h.source.envelope(p,1); };
        h.submit(DownloadMode.RANGE);
        var result=h.runner().runNext(() -> false);
        boolean continues=Set.of(ErrorCode.SOURCE_UNAVAILABLE,ErrorCode.SOURCE_NETWORK_ERROR,ErrorCode.SOURCE_TIMEOUT,
                ErrorCode.SOURCE_PAYLOAD_INVALID,ErrorCode.SOURCE_RANGE_MISMATCH,ErrorCode.ADAPTER_FIELD_MISSING,
                ErrorCode.ADAPTER_TYPE_INVALID,ErrorCode.BATCH_COMPLETENESS_UNCONFIRMED).contains(error);
        var disposition=switch(error) {
            case QUERY_FAILED,PERSISTENCE_FAILED,EXECUTION_INTERRUPTED -> DownloadTaskRunner.Disposition.NEEDS_RECOVERY;
            case TASK_STATE_CONFLICT -> DownloadTaskRunner.Disposition.PERMIT_LOST;
            default -> DownloadTaskRunner.Disposition.FINISHED;
        };
        assertThat(result.disposition()).isEqualTo(disposition);
        assertThat(result.error()).isEqualTo(error);
        assertThat(h.source.requests).hasSize(continues?3:2);
        assertThat(h.committed).hasSize(continues?2:1);
        assertThat(result.toString()).doesNotContain("private");
        if(disposition==DownloadTaskRunner.Disposition.FINISHED) {
            assertThat(h.counts().failed()).isOne();
            assertThat(h.counts().pending()).isEqualTo(continues?0:1);
        }
    }

    @Test void ordinarySingleReservesExactlyOnceAndBypassesRangeMethods() throws Exception {
        Harness h=new Harness(true); h.submit(DownloadMode.SINGLE);
        assertThat(h.runner().runNext(() -> false).error()).isNull();
        assertThat(h.reservations).isOne(); assertThat(h.source.contexts).isEmpty();
        assertThat(h.source.plans+h.source.assessments+h.source.mappings).isZero();
    }

    @Test void splitParentDoesNotAdaptOrConsumeSuccessfulRowBudget() throws Exception {
        Harness h=new Harness();
        h.settings=new DownloadTaskRunner.Settings(true,36600,10000,5000,Duration.ofMinutes(30),2);
        h.source.assessment=r -> r.equals(RANGE)?BatchAssessment.SPLIT_REQUIRED:BatchAssessment.COMPLETE;
        h.source.response=p -> h.source.envelope(p,p.get("from").equals("20240228")&&p.get("to").equals("20240301")?3:1);
        h.submit(DownloadMode.RANGE);
        assertThat(h.runner().runNext(() -> false).error()).isNull();
        assertThat(h.batches.keySet()).containsExactly("000001","000001/0","000001/1");
        assertThat(h.source.adaptations).isEqualTo(2);
        assertThat(h.counts()).isEqualTo(new Counts(2,0,0,2,0,1,2,2,0));
        assertThat(h.reservations).isEqualTo(3);
        assertThat(h.source.contexts).allMatch(c -> c==h.source.contexts.getFirst());
    }

    @Test void invalidPlansAreRejectedBeforeSavingAnyRoot() throws Exception {
        List<List<DateRange>> plans=Arrays.asList(null,Arrays.asList((DateRange)null),
                List.of(dates("20240227","20240227")),List.of(RANGE,RANGE),List.of(dates("20240228","20240229")),List.of());
        List<ErrorCode> codes=List.of(ErrorCode.DATASET_MISCONFIGURED,ErrorCode.DATASET_MISCONFIGURED,
                ErrorCode.SOURCE_RANGE_MISMATCH,ErrorCode.BATCH_COMPLETENESS_UNCONFIRMED,
                ErrorCode.BATCH_COMPLETENESS_UNCONFIRMED,ErrorCode.BATCH_COMPLETENESS_UNCONFIRMED);
        for(int i=0;i<plans.size();i++) {
            Harness h=new Harness(); var plan=plans.get(i); h.source.planOverride=r -> plan;
            h.submit(DownloadMode.RANGE);
            assertThat(h.runner().runNext(() -> false).error()).isEqualTo(codes.get(i));
            assertThat(h.task.planReady()).isFalse(); assertThat(h.batches).isEmpty(); assertThat(h.committed).isEmpty();
        }
    }

    @Test void dailyPlansRejectDuplicatesReorderingMissingAndMultiDayEntries() throws Exception {
        for(var mode:List.of(BatchDownloadDescriptor.PlanningMode.CALENDAR_DAYS,BatchDownloadDescriptor.PlanningMode.TRADING_DAYS)) {
            for(var plan:List.of(List.of(RANGE),List.of(dates("20240228","20240228"),dates("20240228","20240228")),
                    List.of(dates("20240301","20240301"),dates("20240228","20240228")))) {
                Harness h=new Harness(); h.source.mode=mode; h.source.planOverride=r -> plan; h.submit(DownloadMode.RANGE);
                assertThat(h.runner().runNext(() -> false).error()).isEqualTo(ErrorCode.BATCH_COMPLETENESS_UNCONFIRMED);
                assertThat(h.batches).isEmpty();
            }
        }
        Harness missing=new Harness(); missing.source.mode=BatchDownloadDescriptor.PlanningMode.CALENDAR_DAYS;
        missing.source.planOverride=r -> List.of(dates("20240228","20240228"),dates("20240301","20240301")); missing.submit(DownloadMode.RANGE);
        assertThat(missing.runner().runNext(() -> false).error()).isEqualTo(ErrorCode.BATCH_COMPLETENESS_UNCONFIRMED);
    }

    @Test void verifiedEmptyTradingPlanSucceedsWithoutSourceRequests() throws Exception {
        Harness h=new Harness(); h.source.mode=BatchDownloadDescriptor.PlanningMode.TRADING_DAYS;
        h.source.planOverride=r -> List.of(); h.submit(DownloadMode.RANGE);
        assertThat(h.runner().runNext(() -> false).error()).isNull();
        assertThat(h.task.status()).isEqualTo(DownloadTask.Status.SUCCEEDED);
        assertThat(h.task.planReady()).isTrue(); assertThat(h.source.requests).isEmpty();
    }

    @Test void storedPlanAndParametersAreReusedDespiteChangedPureMapping() throws Exception {
        Harness h=new Harness(); h.submit(DownloadMode.RANGE);
        h.add(new NewBatch(UUID.randomUUID(),"000001",RANGE,Map.of("symbol","000001.SZ","from","20240228","to","20240301","marker","saved")),null);
        h.task=h.copyTask(DownloadTask.Status.QUEUED,true,0,null); h.source.mappingMarker="new";
        assertThat(h.runner().runNext(() -> false).error()).isNull();
        assertThat(h.source.requests.getFirst()).containsEntry("marker","saved"); assertThat(h.source.plans).isZero();
        assertThat(h.source.mappings).isGreaterThan(1);
    }

    @Test void changesBeforeAndDuringDownloadCannotCommitNewMeaning() throws Exception {
        Harness queued=new Harness(); queued.submit(DownloadMode.RANGE); queued.source.version="v2";
        assertThat(queued.runner().runNext(() -> false).error()).isEqualTo(ErrorCode.TASK_DEFINITION_CHANGED);
        assertThat(queued.source.requests).isEmpty();
        Harness running=new Harness(); running.submit(DownloadMode.RANGE);
        running.source.response=p -> { running.source.version="v2"; return running.source.envelope(p,1); };
        assertThat(running.runner().runNext(() -> false).error()).isEqualTo(ErrorCode.TASK_DEFINITION_CHANGED);
        assertThat(running.committed).isEmpty();
        Harness disabled=new Harness(); disabled.submit(DownloadMode.SINGLE); disabled.source.available=false;
        assertThat(disabled.runner().runNext(() -> false).error()).isEqualTo(ErrorCode.PLUGIN_DISABLED);
        assertThat(disabled.source.requests).isEmpty();
    }

    @Test void unknownAndUnsplittableResponsesNeverCommit() throws Exception {
        for(var assessment:List.of(BatchAssessment.UNKNOWN,BatchAssessment.SPLIT_REQUIRED)) {
            Harness h=new Harness(); h.source.assessment=r -> assessment;
            h.submit(DownloadMode.RANGE,dates("20240228","20240228"));
            assertThat(h.runner().runNext(() -> false).error()).isEqualTo(ErrorCode.BATCH_COMPLETENESS_UNCONFIRMED);
            assertThat(h.committed).isEmpty();
        }
    }

    @Test void respectsDefaultRangeAndSuccessfulRowsAtEqualityAndOneOver() throws Exception {
        for(int days:List.of(36600,36601)) {
            Harness h=new Harness(); h.source.response=p -> h.source.envelope(p,0);
            h.submit(DownloadMode.RANGE,new DateRange(LocalDate.of(1900,1,1),LocalDate.of(1900,1,1).plusDays(days-1)));
            assertThat(h.runner().runNext(() -> false).error()).isEqualTo(days==36600?null:ErrorCode.TASK_LIMIT_EXCEEDED);
            assertThat(h.source.requests).hasSize(days==36600?1:0);
        }
        for(long history:List.of(999999L,1000000L,1000001L)) {
            Harness h=new Harness(); h.historicalRows=history; h.submit(DownloadMode.SINGLE);
            assertThat(h.runner().runNext(() -> false).error()).isEqualTo(history==999999?null:ErrorCode.TASK_LIMIT_EXCEEDED);
            assertThat(h.committed).hasSize(history==999999?1:0);
            assertThat(h.source.requests).hasSize(history>1000000?0:1);
        }
        Harness empty=new Harness(); empty.historicalRows=1000000; empty.source.response=p -> empty.source.envelope(p,0);
        empty.submit(DownloadMode.SINGLE); assertThat(empty.runner().runNext(() -> false).error()).isNull();
    }

    @Test void defaultRequestBudgetCountsPlanningRequestsAndRejects5001BeforeSending() throws Exception {
        for(int count:List.of(5000,5001)) {
            Harness h=new Harness(); h.source.mode=BatchDownloadDescriptor.PlanningMode.TRADING_DAYS;
            AtomicInteger sent=new AtomicInteger();
            h.source.beforePlan=c -> { for(int i=0;i<count;i++) { c.beforeRequest(); sent.incrementAndGet(); } };
            h.source.planOverride=r -> List.of(); h.submit(DownloadMode.RANGE);
            assertThat(h.runner().runNext(() -> false).error()).isEqualTo(count==5000?null:ErrorCode.TASK_LIMIT_EXCEEDED);
            assertThat(sent).hasValue(5000); assertThat(h.reservations).isEqualTo(5000);
        }
    }

    @Test void deadlineAndStopRejectLateResponsesAndAdaptedBatches() throws Exception {
        for(boolean stopped:List.of(false,true)) {
            Harness h=new Harness(); AtomicBoolean stop=new AtomicBoolean();
            h.source.response=p -> { if(stopped) stop.set(true); else h.now.set(NOW.plusSeconds(1800)); return h.source.envelope(p,1); };
            h.submit(DownloadMode.SINGLE);
            var r=h.runner().runNext(stop::get);
            assertThat(r.error()).isEqualTo(stopped?ErrorCode.EXECUTION_INTERRUPTED:ErrorCode.TASK_LIMIT_EXCEEDED);
            assertThat(h.source.adaptations).isZero(); assertThat(h.committed).isEmpty();
        }
        Harness adapted=new Harness(); AtomicBoolean stop=new AtomicBoolean();
        adapted.source.afterAdapt=() -> stop.set(true); adapted.submit(DownloadMode.SINGLE);
        assertThat(adapted.runner().runNext(stop::get).disposition()).isEqualTo(DownloadTaskRunner.Disposition.NEEDS_RECOVERY);
        assertThat(adapted.committed).isEmpty();
        Harness before=new Harness(); before.source.response=p -> { before.now.set(NOW.plusMillis(1799999)); return before.source.envelope(p,1); };
        before.submit(DownloadMode.SINGLE); assertThat(before.runner().runNext(() -> false).error()).isNull();
    }

    @Test void idleAndTransactionChecksMakeNoDatabaseCallsAndReentrancyIsRejected() throws Exception {
        Harness h=new Harness(); var runner=h.runner(); assertThat(runner.runNext(() -> true).disposition()).isEqualTo(DownloadTaskRunner.Disposition.IDLE);
        verifyNoInteractions(h.repository);
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try { assertThatThrownBy(() -> runner.runNext(() -> false)).isInstanceOf(IllegalStateException.class); }
        finally { TransactionSynchronizationManager.clear(); }
        h.submit(DownloadMode.SINGLE);
        h.source.response=p -> { assertThatThrownBy(() -> runner.runNext(() -> false)).isInstanceOf(IllegalStateException.class)
                .hasMessage("Download runner is already active"); return h.source.envelope(p,1); };
        assertThat(runner.runNext(() -> false).error()).isNull();
    }

    @Test void defaultNodeBudgetAllowsExactly10000RootsAndRejects10001Atomically() throws Exception {
        for(int nodes:List.of(10000,10001)) {
            Harness h=new Harness(); AtomicBoolean stop=new AtomicBoolean();
            h.source.mode=BatchDownloadDescriptor.PlanningMode.CALENDAR_DAYS;
            h.afterSave=() -> stop.set(true);
            h.submit(DownloadMode.RANGE,new DateRange(LocalDate.of(1990,1,1),LocalDate.of(1990,1,1).plusDays(nodes-1)));
            var result=h.runner().runNext(stop::get);
            assertThat(result.error()).isEqualTo(nodes==10000?ErrorCode.EXECUTION_INTERRUPTED:ErrorCode.TASK_LIMIT_EXCEEDED);
            assertThat(h.batches).hasSize(nodes==10000?10000:0);
            assertThat(h.task.planReady()).isEqualTo(nodes==10000);
            assertThat(h.source.requests).isEmpty();
        }
    }

    @Test void defaultNodeBudgetIncludesSplitParentsAt9998And9999() throws Exception {
        for(int nodes:List.of(9998,9999)) {
            Harness h=new Harness(); h.submit(DownloadMode.RANGE);
            h.historicalNodes=nodes-1;
            h.add(new NewBatch(UUID.randomUUID(),"000001",RANGE,Map.of("symbol","000001.SZ","from","20240228","to","20240301")),null);
            h.task=h.copyTask(DownloadTask.Status.QUEUED,true,0,null);
            h.source.assessment=r -> r.equals(RANGE)?BatchAssessment.SPLIT_REQUIRED:BatchAssessment.COMPLETE;
            assertThat(h.runner().runNext(() -> false).error()).isEqualTo(nodes==9998?null:ErrorCode.TASK_LIMIT_EXCEEDED);
            assertThat(h.batches).hasSize(nodes==9998?3:1);
            assertThat(h.source.requests).hasSize(nodes==9998?3:1);
        }
    }

    @Test void rejectsInvalidEnvelopeIdentityStructureAndNullAssessmentBeforeAdaptation() throws Exception {
        for(int variant=0;variant<6;variant++) {
            Harness h=new Harness(); int selected=variant;
            h.source.response=p -> switch(selected) {
                case 0 -> null;
                case 1 -> new DownloadEnvelope(KEY.pluginId(),KEY.apiName(),p,List.of(),0,List.of(),DownloadStatus.FAILURE,"private-response");
                case 2 -> new DownloadEnvelope(PluginId.of("wrong_source"),KEY.apiName(),p,List.of("symbol","observed_on","amount"),0,List.of(),DownloadStatus.SUCCESS,null);
                case 3 -> new DownloadEnvelope(KEY.pluginId(),ApiName.of("wrong_api"),p,List.of("symbol","observed_on","amount"),0,List.of(),DownloadStatus.SUCCESS,null);
                case 4 -> h.source.envelope(Map.of("symbol","other"),0);
                default -> new DownloadEnvelope(KEY.pluginId(),KEY.apiName(),p,List.of("amount","observed_on","symbol"),0,List.of(),DownloadStatus.SUCCESS,null);
            };
            h.submit(DownloadMode.SINGLE);
            assertThat(h.runner().runNext(() -> false).error()).isEqualTo(variant>=2&&variant<=4?ErrorCode.SOURCE_RANGE_MISMATCH:ErrorCode.SOURCE_PAYLOAD_INVALID);
            assertThat(h.source.adaptations).isZero(); assertThat(h.committed).isEmpty();
        }
        Harness h=new Harness(); h.source.assessment=r -> null; h.submit(DownloadMode.RANGE);
        assertThat(h.runner().runNext(() -> false).error()).isEqualTo(ErrorCode.DATASET_MISCONFIGURED);
        assertThat(h.committed).isEmpty();
    }

    @Test void disabledLostClaimAndDeadlineOverflowDoNotExecute() throws Exception {
        Harness disabled=new Harness(); disabled.settings=new DownloadTaskRunner.Settings(false,36600,10000,5000,Duration.ofMinutes(30),1000000);
        assertThat(disabled.runner().runNext(() -> false).disposition()).isEqualTo(DownloadTaskRunner.Disposition.IDLE);
        verifyNoInteractions(disabled.repository);
        Harness lost=new Harness(); lost.submit(DownloadMode.SINGLE);
        when(lost.repository.claimTask(any(),any(),any(),any())).thenReturn(Optional.empty());
        assertThat(lost.runner().runNext(() -> false).disposition()).isEqualTo(DownloadTaskRunner.Disposition.IDLE);
        assertThat(lost.source.requests).isEmpty();
        Harness overflow=new Harness(); overflow.submit(DownloadMode.SINGLE); overflow.now.set(Instant.MAX);
        var result=overflow.runner().runNext(() -> false);
        assertThat(result.disposition()).isEqualTo(DownloadTaskRunner.Disposition.NEEDS_RECOVERY);
        assertThat(result.error()).isEqualTo(ErrorCode.TASK_LIMIT_EXCEEDED); assertThat(result.permit()).isNull();
        verify(overflow.repository,never()).claimTask(any(),any(),any(),any());
    }

    @Test void completedLastCommitCanFinishAfterDeadlineAndStop() throws Exception {
        Harness h=new Harness(); AtomicBoolean stop=new AtomicBoolean(); h.submit(DownloadMode.SINGLE);
        doAnswer(i -> {
            h.result(i.getArgument(1),DownloadBatch.Status.SUCCEEDED,1,null);
            h.now.set(NOW.plusSeconds(1801)); stop.set(true); return new WriteCounts(1,0);
        }).when(h.commits).commit(any(),any(),any(),anyLong(),any());
        var result=h.runner().runNext(stop::get);
        assertThat(result.disposition()).isEqualTo(DownloadTaskRunner.Disposition.FINISHED);
        assertThat(h.task.status()).isEqualTo(DownloadTask.Status.SUCCEEDED); assertThat(result.error()).isNull();
    }

    @Test void callerCancellationDoesNotReleaseOrdinaryPluginUntilActualReturn() throws Exception {
        Harness h=new Harness(true); h.submit(DownloadMode.SINGLE);
        var entered=new java.util.concurrent.CountDownLatch(1);
        var release=new java.util.concurrent.CountDownLatch(1);
        var exited=new java.util.concurrent.CountDownLatch(1);
        AtomicBoolean lease=new AtomicBoolean(true);
        AtomicReference<DownloadTaskRunner.RunResult> result=new AtomicReference<>();
        var runner=h.runner();
        h.source.response=p -> {
            entered.countDown(); boolean interrupted=false;
            for(;;) try { if(!release.await(5,java.util.concurrent.TimeUnit.SECONDS)) throw new AssertionError("release timed out"); break; }
                catch(InterruptedException ex) { interrupted=true; }
            if(interrupted) Thread.currentThread().interrupt();
            return h.source.envelope(p,1);
        };
        var executor=java.util.concurrent.Executors.newSingleThreadExecutor();
        var future=executor.submit(() -> { try { result.set(runner.runNext(() -> false)); }
            finally { lease.set(false); exited.countDown(); } });
        try {
            assertThat(entered.await(5,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            future.cancel(true); assertThat(future.isCancelled()).isTrue(); assertThat(lease).isTrue();
            assertThatThrownBy(() -> runner.runNext(() -> false)).isInstanceOf(IllegalStateException.class);
            assertThat(h.committed).isEmpty();
        } finally { release.countDown(); executor.shutdown(); }
        assertThat(exited.await(5,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        assertThat(executor.awaitTermination(5,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        assertThat(lease).isFalse(); assertThat(result.get().error()).isEqualTo(ErrorCode.EXECUTION_INTERRUPTED);
        assertThat(h.committed).isEmpty(); assertThat(h.source.adaptations).isZero();
    }

    @Test void helperThreadObservesOwnerInterruptionAndNoRequestIsSent() throws Exception {
        Harness h=new Harness(); AtomicBoolean observed=new AtomicBoolean();
        h.source.beforePlan=c -> {
            Thread.currentThread().interrupt();
            Thread reader=new Thread(() -> observed.set(c.stopRequested())); reader.start();
            long waitUntil=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
            while(reader.isAlive() && System.nanoTime()<waitUntil) Thread.onSpinWait();
            assertThat(reader.isAlive()).isFalse();
            assertThat(observed).isTrue();
        };
        h.submit(DownloadMode.RANGE);
        try { assertThat(h.runner().runNext(() -> false).error()).isEqualTo(ErrorCode.EXECUTION_INTERRUPTED); }
        finally { Thread.interrupted(); }
        assertThat(h.source.requests).isEmpty();
    }

    @Test void unexpectedStorageFailuresHaltBeforeAnyFurtherRequestOrWrite() throws Exception {
        Harness h=new Harness(); h.submit(DownloadMode.RANGE);
        doThrow(new IllegalStateException("private-database")).when(h.repository).savePlan(any(),anyList(),anyInt(),any());
        var result=h.runner().runNext(() -> false);
        assertThat(result.disposition()).isEqualTo(DownloadTaskRunner.Disposition.NEEDS_RECOVERY);
        assertThat(result.error()).isEqualTo(ErrorCode.PERSISTENCE_FAILED); assertThat(h.source.requests).isEmpty();
        verify(h.repository,never()).finishTask(any(),any(),any(),any());
        verify(h.repository,never()).failBatch(any(),any(),any(),any());
    }

    @Test void invalidSettingsCannotCreateAnUnboundedRunner() {
        for(Duration duration:Arrays.asList(null,Duration.ZERO,Duration.ofNanos(999999),Duration.ofSeconds(-1),Duration.ofSeconds(Long.MAX_VALUE)))
            assertThatThrownBy(() -> new DownloadTaskRunner.Settings(true,36600,10000,5000,duration,1000000))
                    .isInstanceOf(IllegalArgumentException.class).hasMessage("Invalid download runner settings").hasNoCause();
        for(int[] values:List.of(new int[]{0,10000},new int[]{1,0},new int[]{1,1000000}))
            assertThatThrownBy(() -> new DownloadTaskRunner.Settings(true,values[0],values[1],5000,Duration.ofMinutes(30),1000000))
                    .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void preservesWeekendCalendarDaysAndAcceptsVerifiedNonWeekdayTradingSubset() throws Exception {
        Harness calendar=new Harness(); calendar.source.mode=BatchDownloadDescriptor.PlanningMode.CALENDAR_DAYS;
        calendar.submit(DownloadMode.RANGE,dates("20240906","20240908"));
        assertThat(calendar.runner().runNext(() -> false).error()).isNull();
        assertThat(calendar.source.requests).extracting(p -> p.get("from"))
                .containsExactly("20240906","20240907","20240908");
        Harness trading=new Harness(); trading.source.mode=BatchDownloadDescriptor.PlanningMode.TRADING_DAYS;
        trading.source.planOverride=r -> List.of(dates("20240907","20240907"));
        trading.submit(DownloadMode.RANGE,dates("20240906","20240908"));
        assertThat(trading.runner().runNext(() -> false).error()).isNull();
        assertThat(trading.source.requests).extracting(p -> p.get("from")).containsExactly("20240907");
    }

    @Test void sourceExceptionSecretsCannotEnterStoredErrorOrResult() throws Exception {
        for(boolean classified:List.of(true,false)) {
            Harness h=new Harness();
            RuntimeException failure=classified ? new TensorException(ErrorCode.SOURCE_NETWORK_ERROR,"private-token-value") {} :
                    new IllegalStateException("private-token-value");
            failure.initCause(new IllegalStateException("private-password"));
            failure.addSuppressed(new IllegalArgumentException("private-response"));
            h.source.response=p -> { throw failure; }; h.submit(DownloadMode.SINGLE);
            var result=h.runner().runNext(() -> false);
            assertThat(result.error()).isEqualTo(classified?ErrorCode.SOURCE_NETWORK_ERROR:ErrorCode.INTERNAL_ERROR);
            assertThat(result.toString()).doesNotContain("private");
            assertThat(h.batches.firstEntry().getValue().error().message()).doesNotContain("private");
        }
    }

    static final class Harness {
        final DownloadTaskRepository repository = mock(DownloadTaskRepository.class);
        final BatchCommitService commits = mock(BatchCommitService.class);
        final DownloadTaskJson json = new DownloadTaskJson();
        final Source source = new Source();
        final TreeMap<String, DownloadBatch> batches = new TreeMap<>();
        final List<AdaptedBatch> committed = new ArrayList<>();
        final AtomicReference<Instant> now = new AtomicReference<>(NOW);
        final Clock clock = new Clock() {
            public ZoneId getZone() { return ZoneOffset.UTC; }
            public Clock withZone(ZoneId zone) { return this; }
            public Instant instant() { return now.get(); }
        };
        final DownloadTaskService service;
        DownloadTask task;
        long reservations;
        long historicalRows;
        long historicalNodes;
        boolean ordinary;
        Runnable afterSave = () -> {};
        DownloadTaskRunner.Settings settings = DownloadTaskRunner.Settings.defaults();

        Harness() throws Exception { this(false); }
        Harness(boolean ordinary) throws Exception {
            this.ordinary = ordinary;
            var constructor = DatasetCatalog.class.getDeclaredConstructor(List.class);
            constructor.setAccessible(true);
            var catalog = constructor.newInstance(List.of(source.definition()));
            DataSourcePlugin plugin = ordinary ? source.ordinary() : source;
            service = new DownloadTaskService(new PluginRegistry(List.of(plugin)), catalog,
                    new AdapterRegistry(List.of(source)), new ParameterValidator(), repository, json, clock, RUN,
                    new DownloadTaskService.Settings(true, 100, 100000));
            when(repository.findSubmission(any())).thenReturn(Optional.empty());
            when(repository.insert(any())).thenAnswer(i -> {
                NewTask in = i.getArgument(0);
                task = new DownloadTask(in.taskId(), in.submissionId(), json.requestHash(KEY, in.mode(), in.normalizedParams()),
                        KEY, in.mode(), in.normalizedParams(), in.definitionHash(), in.policySnapshot(),
                        DownloadTask.Status.QUEUED, false, RUN, 0, 1, 0, 0, null, NOW, NOW, NOW, null, null, null);
                return task;
            });
            when(repository.queuedTasks(RUN, 1)).thenAnswer(i -> task == null ? List.of() : List.of(task));
            when(repository.claimTask(any(), eq(RUN), any(), any())).thenAnswer(i -> {
                task = copyTask(DownloadTask.Status.RUNNING, task.planReady(), task.runGeneration() + 1, i.getArgument(3));
                reservations = 0;
                return Optional.of(task);
            });
            doAnswer(i -> {
                for (NewBatch b : (List<NewBatch>) i.getArgument(1)) add(b, null);
                task = copyTask(task.status(), true, task.runGeneration(), task.deadlineAt());
                afterSave.run();
                return null;
            }).when(repository).savePlan(any(), anyList(), anyInt(), any());
            when(repository.pendingBatches(any())).thenAnswer(i -> batches.values().stream()
                    .filter(b -> b.status() == DownloadBatch.Status.PENDING).toList());
            when(repository.claimBatch(any(), any(), any())).thenAnswer(i -> {
                DownloadBatch b = batch(i.getArgument(1));
                DownloadBatch claimed = new DownloadBatch(b.batchId(), b.taskId(), b.parentBatchId(), b.batchKey(), b.range(),
                        b.sourceParams(), DownloadBatch.Status.RUNNING, b.attemptCount()+1, task.runGeneration(),
                        0, 0, 0, null, NOW, NOW, NOW, null);
                batches.put(b.batchKey(), claimed);
                return Optional.of(claimed);
            });
            doAnswer(i -> {
                long max = i.getArgument(1);
                if (reservations >= max) throw failure(ErrorCode.TASK_LIMIT_EXCEEDED);
                reservations++;
                return null;
            }).when(repository).reserveRequest(any(), anyLong(), any());
            when(repository.counts(any())).thenAnswer(i -> counts());
            when(repository.snapshot(any())).thenAnswer(i -> new TaskSnapshot(Optional.of(task), counts()));
            when(repository.batches(any(), any(), anyInt(), anyInt())).thenAnswer(i -> {
                var failed = batches.values().stream().filter(b -> b.status() == DownloadBatch.Status.FAILED).toList();
                return new Page<>(failed.size(), failed);
            });
            when(commits.commit(any(), any(), any(), anyLong(), any())).thenAnswer(i -> {
                committed.add(i.getArgument(2));
                result(i.getArgument(1), DownloadBatch.Status.SUCCEEDED, i.getArgument(3), null);
                return new WriteCounts(i.getArgument(3), 0);
            });
            doAnswer(i -> { result(i.getArgument(1), DownloadBatch.Status.FAILED, 0, i.getArgument(2)); return null; })
                    .when(repository).failBatch(any(), any(), any(), any());
            doAnswer(i -> {
                result(i.getArgument(1), DownloadBatch.Status.SPLIT, 0, null);
                add(i.getArgument(2), i.getArgument(1)); add(i.getArgument(3), i.getArgument(1)); return null;
            }).when(repository).split(any(), any(), any(), any(), anyInt(), any());
            doAnswer(i -> { task = copyTask(i.getArgument(1), task.planReady(), task.runGeneration(), task.deadlineAt()); return null; })
                    .when(repository).finishTask(any(), any(), nullable(ErrorCode.class), any());
        }
        void submit(DownloadMode mode) { submit(mode, RANGE); }
        void submit(DownloadMode mode, DateRange range) {
            Map<String,Object> params = mode == DownloadMode.SINGLE ? Map.of("symbol", "000001.SZ") :
                    Map.of("symbol", "000001.SZ", "from", date(range.start()), "to", date(range.end()));
            service.submit(new DownloadTaskService.Submission(UUID.randomUUID(), KEY, mode, params));
        }
        DownloadTaskRunner runner() { return new DownloadTaskRunner(service, repository, commits, json, clock, RUN, settings); }
        DownloadTask copyTask(DownloadTask.Status status, boolean ready, int generation, Instant deadline) {
            return new DownloadTask(task.taskId(), task.submissionId(), task.requestHash(), KEY, task.mode(), task.params(),
                    task.definitionHash(), task.policySnapshot(), status, ready, RUN, generation, task.version()+1,
                    reservations, reservations, null, NOW, NOW, NOW, NOW, null, deadline);
        }
        void add(NewBatch b, UUID parent) {
            batches.put(b.batchKey(), new DownloadBatch(b.batchId(), task.taskId(), parent, b.batchKey(), b.range(),
                    b.sourceParams(), DownloadBatch.Status.PENDING, 0, null, 0, 0, 0, null, NOW, NOW, null, null));
        }
        DownloadBatch batch(UUID id) { return batches.values().stream().filter(b -> b.batchId().equals(id)).findFirst().orElseThrow(); }
        void result(UUID id, DownloadBatch.Status status, long rows, ErrorCode error) {
            DownloadBatch b = batch(id);
            batches.put(b.batchKey(), new DownloadBatch(id, task.taskId(), b.parentBatchId(), b.batchKey(), b.range(), b.sourceParams(),
                    status, b.attemptCount(), task.runGeneration(), rows, rows, 0, error == null ? null : new StoredError(error),
                    NOW, NOW, NOW, NOW));
        }
        Counts counts() {
            long pending=0, running=0, succeeded=0, failed=0, split=0, rows=historicalRows;
            for (DownloadBatch b : batches.values()) {
                switch(b.status()) { case PENDING -> pending++; case RUNNING -> running++; case SUCCEEDED -> { succeeded++; rows+=b.sourceRows(); }
                    case FAILED -> failed++; case SPLIT -> split++; }
            }
            return new Counts(pending+running+succeeded+failed, pending, running, succeeded, failed,
                    split+historicalNodes, rows, rows, 0);
        }
    }

    static final class Source implements BatchDownloadSupport, DatasetAdapter {
        BatchDownloadDescriptor.PlanningMode mode = BatchDownloadDescriptor.PlanningMode.NATIVE_RANGE;
        String version = "v1";
        boolean available = true;
        boolean splittable = true;
        int plans, assessments, adaptations, mappings;
        final List<Map<String,Object>> requests = new ArrayList<>();
        final List<BatchCallContext> contexts = new ArrayList<>();
        Function<Map<String,Object>, DownloadEnvelope> response = p -> envelope(p, 1);
        Function<DateRange, List<DateRange>> planOverride;
        Function<DateRange, BatchAssessment> assessment = r -> BatchAssessment.COMPLETE;
        Runnable afterAdapt = () -> {};
        Consumer<BatchCallContext> beforePlan = c -> {};
        String mappingMarker;
        public DatasetKey datasetKey() { return KEY; }
        static ParameterDescriptor symbol() { return new ParameterDescriptor("symbol", "Symbol", null, ParameterType.TS_CODE, true, null, List.of(), null, null); }
        static ParameterDescriptor endpoint(String name, String related) { return new ParameterDescriptor(name, name, null, ParameterType.DATE_RANGE_MEMBER, true, null, List.of(), null, related); }
        public DatasetDefinition definition() {
            return new DatasetDefinition(KEY,"Prices","test",QueryMode.snapshot,List.of(symbol()),TableName.from(KEY),List.of(
                    new ColumnDefinition("symbol","Symbol",LogicalType.STRING,false,0,32,null,null,List.of(),false),
                    new ColumnDefinition("observed_on","Date",LogicalType.DATE,false,1,null,null,null,List.of(),false),
                    new ColumnDefinition("amount","Amount",LogicalType.DECIMAL,false,2,null,18,2,List.of(),false)),
                    new BusinessKeyDefinition(BusinessKeyMode.COMPOSITE,List.of("symbol","observed_on")),List.of(),null,500);
        }
        public PluginDescriptor descriptor() { return new PluginDescriptor(KEY.pluginId(),"Runner","Test",true,true,true,null,
                List.of(new ApiDescriptor(KEY.apiName(),"Prices","test",QueryMode.snapshot,List.of(symbol()))),List.of(KEY)); }
        public PluginReadiness readiness() { return new PluginReadiness(true,true,available,available ? null : "disabled"); }
        public Optional<BatchDownloadDescriptor> batchDescriptor(ApiName api) {
            return Optional.of(new BatchDownloadDescriptor(List.of(symbol(),endpoint("from","to"),endpoint("to","from")),
                    "from","to",BatchDownloadDescriptor.DateAxis.REPORT_PERIOD,"Observed",mode,
                    splittable && mode == BatchDownloadDescriptor.PlanningMode.NATIVE_RANGE,
                    BatchDownloadDescriptor.Availability.AVAILABLE,null,version,
                    new BatchDownloadDescriptor.CompletenessRule(BatchDownloadDescriptor.CompletenessRule.Kind.VERIFIED_RULE,null,"controlled-test-only")));
        }
        public List<DateRange> plan(ApiName api,Map<String,Object> p,BatchCallContext c) {
            outside(); plans++; beforePlan.accept(c); DateRange r=dates((String)p.get("from"),(String)p.get("to"));
            return planOverride != null ? planOverride.apply(r) : mode == BatchDownloadDescriptor.PlanningMode.NATIVE_RANGE ? List.of(r) : DateRangePlanner.calendarDays(r);
        }
        public Map<String,Object> sourceParameters(ApiName api,Map<String,Object> p,DateRange r) {
            outside(); mappings++;
            Map<String,Object> mapped=new HashMap<>(Map.of("symbol",p.get("symbol"),"from",date(r.start()),"to",date(r.end())));
            if(mappingMarker!=null) mapped.put("marker",mappingMarker);
            return mapped;
        }
        public DownloadEnvelope downloadBatch(ApiName api,Map<String,Object> p,BatchCallContext c) {
            outside(); contexts.add(c); c.beforeRequest(); return download(api,p);
        }
        public DownloadEnvelope download(ApiName api,Map<String,Object> p) { outside(); requests.add(p); return response.apply(p); }
        public BatchAssessment assess(ApiName api,DateRange r,DownloadEnvelope e) { outside(); assessments++; return assessment.apply(r); }
        public AdaptedBatch adapt(DownloadEnvelope e,Instant now) {
            outside(); adaptations++; afterAdapt.run();
            return new AdaptedBatch(KEY,TableName.from(KEY),e.fields(),e.data().stream().map(row -> Map.of(
                    "symbol",row.get(0),"observed_on",row.get(1),"amount",row.get(2))).toList(), definition().businessKey(),now);
        }
        DownloadEnvelope envelope(Map<String,Object> p,int count) {
            var rows=new ArrayList<List<Object>>();
            for(int i=0;i<count;i++) rows.add(List.of("000001.SZ",p.getOrDefault("from","20240228"),"1.00"));
            return new DownloadEnvelope(KEY.pluginId(),KEY.apiName(),p,List.of("symbol","observed_on","amount"),count,rows,DownloadStatus.SUCCESS,null);
        }
        DataSourcePlugin ordinary() { return new DataSourcePlugin() {
            public PluginDescriptor descriptor() { return Source.this.descriptor(); }
            public PluginReadiness readiness() { return Source.this.readiness(); }
            public DownloadEnvelope download(ApiName api,Map<String,Object> p) { return Source.this.download(api,p); }
        }; }
        static void outside() { assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse(); }
    }
    static DateRange dates(String a,String b) { return new DateRange(LocalDate.parse(a,DateTimeFormatter.BASIC_ISO_DATE),LocalDate.parse(b,DateTimeFormatter.BASIC_ISO_DATE)); }
    static String date(LocalDate d) { return d.format(DateTimeFormatter.BASIC_ISO_DATE); }
    static TensorException failure(ErrorCode code) { return new DownloadTaskService.TaskException(code); }
}
