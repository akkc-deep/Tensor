package com.akkc.tensor.plugin.fixture.integrity;

import com.akkc.tensor.plugin.api.integrity.*;
import com.akkc.tensor.plugin.api.model.DatasetKey;
import java.time.*;
import java.util.*;

/** Synthetic local-only cases, registered solely by the existing acceptance fixture. */
public final class FixtureIntegrityRules implements IntegrityRule {
    private static final IntegrityDateRange PROVEN_WINDOW = new IntegrityDateRange(
            LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 20));
    private static final IntegrityDateRange EXTENDED_WINDOW = new IntegrityDateRange(
            LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 21));
    private final IntegrityDescriptor capability;
    private final IntegrityRuleDescriptor rule;

    public FixtureIntegrityRules(DatasetKey key) {
        this(key, 2);
    }

    public FixtureIntegrityRules(DatasetKey key, int version) {
        if (version != 2 && version != 3) throw new IllegalArgumentException("Fixture integrity version must be 2 or 3");
        String value = Integer.toString(version);
        rule = new IntegrityRuleDescriptor("fixture.coverage.fixture_daily", value, "Fixture 覆盖集合",
                IntegrityRuleDescriptor.Dimension.COVERAGE, List.of("ts_code", "trade_date"), List.of(),
                "PROVEN、PROVEN_EXTRA、PROVEN_EMPTY 为固定验收窗口，UNCONFIRMED 为候选集合");
        var rules = version == 3 ? List.of(rule, FixtureIntegrityExtensionRule.DESCRIPTOR) : List.of(rule);
        capability = new IntegrityDescriptor(key, IntegrityDescriptor.ScopeKind.STOCK_DATE, "ts_code", "trade_date",
                "合成日期", ZoneId.of("Asia/Shanghai"), value, List.of(), rules,
                List.of("仅 acceptance fixture，非 Tushare 生产基线"));
    }

    public IntegrityDescriptor capability() { return capability; }
    @Override public IntegrityRuleDescriptor descriptor() { return rule; }

    @Override
    public IntegrityRuleResult evaluate(IntegrityScope scope, IntegrityContext context, IntegrityIssueSink issues) {
        if (!scope.datasetKey().equals(capability.datasetKey()) || scope.symbol() == null || scope.snapshotStartedAt() == null)
            throw new IllegalArgumentException("Invalid fixture execution scope");
        IntegrityDateRange reliableWindow = switch (scope.symbol()) {
            case "PROVEN" -> PROVEN_WINDOW;
            case "PROVEN_EXTRA", "PROVEN_EMPTY" -> EXTENDED_WINDOW;
            default -> null;
        };
        boolean proven = reliableWindow != null && reliableWindow.contains(scope.range());
        var basis = proven ? IntegrityExpectedKeys.Basis.PROVEN : IntegrityExpectedKeys.Basis.UNCONFIRMED;
        IntegrityDateRange evidenceWindow = reliableWindow == null ? PROVEN_WINDOW : reliableWindow;
        var evidence = List.of(new IntegrityEvidence("fixture:synthetic-acceptance-window", rule.version(), evidenceWindow,
                scope.snapshotStartedAt(), "fixture 验收合成全集，非 Tushare 生产基线；仅固定窗口有可靠证据"));
        Iterable<Map<String, Object>> keys = scope.symbol().equals("PROVEN_EMPTY") ? List.of() : () ->
                PROVEN_WINDOW.startDate().datesUntil(PROVEN_WINDOW.endDate().plusDays(1))
                .filter(date -> !date.isBefore(scope.startDate()) && !date.isAfter(scope.endDate()))
                .<Map<String, Object>>map(date -> Map.of("ts_code", scope.symbol(), "trade_date", date)).iterator();
        var statistics = context.compare(new IntegrityExpectedKeys(scope, basis, keys, evidence));
        IntegrityStatus status = IntegrityStatus.UNKNOWN;
        String reason = "EXPECTED_SET_UNPROVEN";
        if (statistics.missingCount() != null && statistics.missingCount() > 0) {
            status = IntegrityStatus.FAIL;
            reason = "CONFIRMED_MISSING";
        } else if (proven) {
            reason = "COMPARISON_INCOMPLETE";
            if (statistics.actualCount() != null && statistics.expectedCount() != null
                    && statistics.matchedCount() != null && statistics.missingCount() != null
                    && statistics.extraCount() != null) {
                status = statistics.extraCount() > 0 ? IntegrityStatus.WARN : IntegrityStatus.PASS;
                reason = statistics.expectedCount() == 0 && statistics.actualCount() == 0
                        ? "VERIFIED_EMPTY" : "PROVEN_FIXTURE_SET";
            }
        }
        return new IntegrityRuleResult(rule, status, reason, "固定 fixture 集合比较", statistics, evidence);
    }
}
