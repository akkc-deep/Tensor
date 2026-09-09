package com.akkc.tensor.core.download;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.akkc.tensor.core.persistence.BusinessKey;
import com.akkc.tensor.plugin.api.error.ErrorCode;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class CommittedKeyIndexTest {
    @Test
    void preflightDoesNotConsumeTicketOrPublishPendingKeys() {
        var fixture = RecoveryUnitProcessorTest.fixture(false);
        var ready = ready(fixture, List.of(RecoveryUnitProcessorTest.row("new", "000001.SZ", "20260903", "1")));
        fixture.index().checkConfirmable(ready);
        fixture.index().checkConfirmable(ready);
        assertThat(fixture.index().size()).isZero();
        assertThat(ready.confirmed()).isFalse();
        fixture.index().confirmCommitted(ready);
        assertThat(fixture.index().size()).isOne();
        assertThatThrownBy(() -> fixture.index().checkConfirmable(ready)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void serialCommitDriverKeepsRollbacksUnconfirmedAndStopsAfterUnknownBeforeC() {
        RecoveryUnitProcessorTest.Fixture rollback = RecoveryUnitProcessorTest.fixture(false);
        RecoveryUnitProcessor.PreparedBatch rollbackBatch = rollback.session().accept(rollback.result(List.of(
                RecoveryUnitProcessorTest.row("rolled-back", "000001.SZ", "20260903", "1.20"))));
        assertThat(drive(rollback, rollbackBatch, Map.of("000001.SZ", CommitResult.ROLLBACK)))
                .containsExactly("000001.SZ");
        assertThat(rollback.index().size()).isZero();

        RecoveryUnitProcessorTest.Fixture stopped = RecoveryUnitProcessorTest.fixture(false);
        RecoveryUnitProcessor.PreparedBatch batch = stopped.session().accept(stopped.result(List.of(
                RecoveryUnitProcessorTest.row("a-key", "000001.SZ", "20260903", "1.20"),
                RecoveryUnitProcessorTest.row("b-key", "000002.SZ", "20260903", "2.30"),
                RecoveryUnitProcessorTest.row("c-key", "600000.SH", "20260903", "3.40"))));

        assertThat(drive(stopped, batch, Map.of(
                "000001.SZ", CommitResult.SUCCESS,
                "000002.SZ", CommitResult.UNKNOWN,
                "600000.SH", CommitResult.SUCCESS)))
                .containsExactly("000001.SZ", "000002.SZ");
        assertThat(stopped.index().size()).isOne();
        assertThat(stopped.index().lookup(new BusinessKey(List.of("a-key")))).isNotNull();
        assertThat(stopped.index().lookup(new BusinessKey(List.of("b-key")))).isNull();
        assertThat(stopped.index().lookup(new BusinessKey(List.of("c-key")))).isNull();
    }

    @Test
    void addsOnlyExplicitlyConfirmedReadyDigestsAndRejectsDuplicateOrForeignTickets() {
        RecoveryUnitProcessorTest.Fixture fixture = RecoveryUnitProcessorTest.fixture(false);
        RecoveryUnitProcessor.ReadyUnit ready = ready(fixture,
                List.of(RecoveryUnitProcessorTest.row("a-key", "000001.SZ", "20260903", "1.20")));

        assertThat(fixture.index().size()).isZero();
        fixture.index().confirmCommitted(ready);

        assertThat(fixture.index().size()).isOne();
        assertThat(fixture.index().lookup(new BusinessKey(List.of("a-key"))))
                .satisfies(digest -> {
                    assertThat(digest.version()).isEqualTo(1);
                    assertThat(digest.sha256Hex()).matches("[0-9a-f]{64}");
                });
        assertThatThrownBy(() -> fixture.index().confirmCommitted(ready))
                .isInstanceOf(IllegalStateException.class).hasMessage("ready unit was already confirmed");
        assertThatThrownBy(() -> new CommittedKeyIndex(fixture.adapter().datasetKey()).confirmCommitted(ready))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("ready unit belongs to another committed index");
    }

    @Test
    void confirmedIdenticalContentSkipsWritingWhileDifferentContentRejectsOnlyTheLaterUnit() {
        RecoveryUnitProcessorTest.Fixture fixture = RecoveryUnitProcessorTest.fixture(false);
        RecoveryUnitProcessor.ReadyUnit first = ready(fixture,
                List.of(RecoveryUnitProcessorTest.row("same", "000001.SZ", "20260903", "1.20")));
        fixture.index().confirmCommitted(first);

        RecoveryUnitProcessor.ReadyUnit duplicate = ready(fixture,
                List.of(RecoveryUnitProcessorTest.row("same", "000001.SZ", "20260903", "1.2")));
        assertThat(duplicate.batch().rows()).isEmpty();
        assertThat(duplicate.sourceRowCount()).isEqualTo(1);

        RecoveryUnitProcessor.Validation conflict = validation(fixture,
                List.of(RecoveryUnitProcessorTest.row("same", "000001.SZ", "20260903", "2.30")));
        assertThat(conflict).isInstanceOfSatisfying(RecoveryUnitProcessor.RejectedUnit.class,
                rejected -> assertThat(rejected.failure().errorCode()).isEqualTo(ErrorCode.DATA_CONFLICT));
        assertThat(fixture.index().size()).isOne();
    }

    @Test
    void discardedReadyUnitsDoNotReserveKeysAndANewExecutionStartsEmpty() {
        RecoveryUnitProcessorTest.Fixture fixture = RecoveryUnitProcessorTest.fixture(false);
        ready(fixture, List.of(RecoveryUnitProcessorTest.row(
                "rolled-back", "000001.SZ", "20260903", "1.20")));

        RecoveryUnitProcessor.ReadyUnit retried = ready(fixture,
                List.of(RecoveryUnitProcessorTest.row("rolled-back", "000001.SZ", "20260903", "1.20")));

        assertThat(retried.batch().rows()).hasSize(1);
        assertThat(fixture.index().size()).isZero();
        assertThat(new CommittedKeyIndex(fixture.adapter().datasetKey()).size()).isZero();
    }

    @Test
    void preflightsEveryPendingDigestBeforeAtomicallyChangingTheIndex() {
        RecoveryUnitProcessorTest.Fixture fixture = RecoveryUnitProcessorTest.fixture(false);
        RecoveryUnitProcessor.ReadyUnit twoKeys = ready(fixture, List.of(
                RecoveryUnitProcessorTest.row("a-key", "000001.SZ", "20260903", "1.20"),
                RecoveryUnitProcessorTest.row("b-key", "000001.SZ", "20260903", "2.30")));
        RecoveryUnitProcessor.ReadyUnit changedB = ready(fixture,
                List.of(RecoveryUnitProcessorTest.row("b-key", "000001.SZ", "20260903", "9.90")));
        fixture.index().confirmCommitted(changedB);

        assertThatThrownBy(() -> fixture.index().confirmCommitted(twoKeys))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("committed index changed after validation");
        assertThat(fixture.index().size()).isOne();
        assertThat(fixture.index().lookup(new BusinessKey(List.of("a-key")))).isNull();
    }

    @Test
    void rejectsAMixedCommittedDuplicateNewKeyAndConflictWithoutAddingTheNewKey() {
        RecoveryUnitProcessorTest.Fixture fixture = RecoveryUnitProcessorTest.fixture(false);
        RecoveryUnitProcessor.ReadyUnit committed = ready(fixture, List.of(
                RecoveryUnitProcessorTest.row("same", "000001.SZ", "20260903", "1.20"),
                RecoveryUnitProcessorTest.row("conflict", "000001.SZ", "20260903", "3.40")));
        fixture.index().confirmCommitted(committed);

        RecoveryUnitProcessor.Validation validation = validation(fixture, List.of(
                RecoveryUnitProcessorTest.row("same", "000001.SZ", "20260903", "1.2"),
                RecoveryUnitProcessorTest.row("new", "000001.SZ", "20260903", "2.30"),
                RecoveryUnitProcessorTest.row("conflict", "000001.SZ", "20260903", "9.90")));

        assertThat(validation).isInstanceOfSatisfying(RecoveryUnitProcessor.RejectedUnit.class,
                rejected -> assertThat(rejected.failure().errorCode()).isEqualTo(ErrorCode.DATA_CONFLICT));
        assertThat(fixture.index().size()).isEqualTo(2);
        assertThat(fixture.index().lookup(new BusinessKey(List.of("new")))).isNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void stopsValidationWhenTheCommittedDigestUsesAnotherCodecVersion() throws Exception {
        RecoveryUnitProcessorTest.Fixture fixture = RecoveryUnitProcessorTest.fixture(false);
        Field values = CommittedKeyIndex.class.getDeclaredField("values");
        values.setAccessible(true);
        Map<BusinessKey, CommittedKeyIndex.ContentDigest> stored =
                (Map<BusinessKey, CommittedKeyIndex.ContentDigest>) values.get(fixture.index());
        stored.put(new BusinessKey(List.of("versioned")),
                new CommittedKeyIndex.ContentDigest(2, "0".repeat(64)));

        assertThatThrownBy(() -> validation(fixture, List.of(
                RecoveryUnitProcessorTest.row("versioned", "000001.SZ", "20260903", "1.20"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("committed content version mismatch");
    }

    @Test
    void rejectsCrossThreadUseInsteadOfSharingAnExecutionIndex() throws Exception {
        RecoveryUnitProcessorTest.Fixture fixture = RecoveryUnitProcessorTest.fixture(false);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread thread = new Thread(() -> {
            try {
                fixture.index().size();
            } catch (Throwable thrown) {
                failure.set(thrown);
            }
        });
        thread.start();
        thread.join();

        assertThat(failure.get()).isInstanceOf(IllegalStateException.class)
                .hasMessage("committed index cannot be shared across threads");
        assertThat(fixture.index().size()).isZero();
    }

    private static RecoveryUnitProcessor.ReadyUnit ready(
            RecoveryUnitProcessorTest.Fixture fixture, List<List<Object>> rows) {
        return (RecoveryUnitProcessor.ReadyUnit) validation(fixture, rows);
    }

    private static RecoveryUnitProcessor.Validation validation(
            RecoveryUnitProcessorTest.Fixture fixture, List<List<Object>> rows) {
        RecoveryUnitProcessor.UnitInput unit = fixture.session().accept(fixture.result(rows)).units().getFirst();
        return fixture.processor().validate(unit, fixture.index(),
                java.time.Instant.parse("2026-09-08T01:02:03Z"));
    }

    private static List<String> drive(RecoveryUnitProcessorTest.Fixture fixture,
            RecoveryUnitProcessor.PreparedBatch prepared, Map<String, CommitResult> outcomes) {
        List<String> visited = new ArrayList<>();
        for (RecoveryUnitProcessor.UnitInput unit : prepared.units()) {
            String target = unit.selector().targetValue();
            visited.add(target);
            RecoveryUnitProcessor.ReadyUnit ready = (RecoveryUnitProcessor.ReadyUnit)
                    fixture.processor().validate(unit, fixture.index(),
                            java.time.Instant.parse("2026-09-08T01:02:03Z"));
            switch (outcomes.get(target)) {
                case SUCCESS -> fixture.index().confirmCommitted(ready);
                case ROLLBACK -> { }
                case UNKNOWN -> { return visited; }
            }
        }
        return visited;
    }

    private enum CommitResult { SUCCESS, ROLLBACK, UNKNOWN }
}
