package com.akkc.tensor.test;

import com.akkc.tensor.plugin.api.download.DownloadPolicy;
import com.akkc.tensor.plugin.api.download.RecoveryPolicy;
import java.util.List;

public final class DownloadPolicies {
    private DownloadPolicies() {}
    public static DownloadPolicy original() {
        return new DownloadPolicy(DownloadPolicy.Mode.ORIGINAL_PARAMS, DownloadPolicy.DateSemantic.NONE,
                "Controlled original-parameter test", null, null, DownloadPolicy.SourceRequestMode.NONE, null,
                DownloadPolicy.RequestEvidenceStatus.DOCUMENTED_CANDIDATE, DownloadPolicy.BatchPlanning.ORIGINAL_PARAMS,
                new RecoveryPolicy(RecoveryPolicy.Mode.REQUEST, null, null, null, false, List.of("docs/test.md")),
                new DownloadPolicy.CompletenessPolicy(DownloadPolicy.CompletenessStatus.UNCONFIRMED,
                        DownloadPolicy.CompletenessStatus.UNCONFIRMED, "Unconfirmed", "Controlled test only"),
                null, List.of("docs/test.md"));
    }
    public static DownloadPolicy tradeRange() {
        var original = original();
        return new DownloadPolicy(DownloadPolicy.Mode.TRADE_DATE_RANGE, DownloadPolicy.DateSemantic.TRADE_DATE,
                "Controlled trade range", DownloadPolicy.CalendarProfile.C_A, new DownloadPolicy.Limits(31),
                DownloadPolicy.SourceRequestMode.DATE, "trade_date", DownloadPolicy.RequestEvidenceStatus.DOCUMENTED_CANDIDATE,
                DownloadPolicy.BatchPlanning.SINGLE_DATE, original.recoveryPolicy(), original.completenessPolicy(),
                DownloadPolicy.CalendarEvidenceStatus.UNCONFIRMED, original.evidenceRefs());
    }
}
