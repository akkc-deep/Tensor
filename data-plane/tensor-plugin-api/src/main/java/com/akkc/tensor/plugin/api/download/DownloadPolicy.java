package com.akkc.tensor.plugin.api.download;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

public record DownloadPolicy(Mode mode, DateSemantic dateSemantic, String description,
        CalendarProfile calendarProfile, Limits limits, SourceRequestMode sourceRequestMode,
        String sourceDateParameter, RequestEvidenceStatus requestEvidenceStatus, BatchPlanning batchPlanning,
        RecoveryPolicy recoveryPolicy, CompletenessPolicy completenessPolicy,
        CalendarEvidenceStatus calendarEvidenceStatus, List<String> evidenceRefs) {
    public enum Mode { TRADE_DATE_RANGE, ANN_DATE_RANGE, MONTH_RANGE, NATIVE_RANGE, ORIGINAL_PARAMS }
    public enum DateSemantic { TRADE_DATE, ANN_DATE, COVERED_MONTH, CALENDAR_DATE, IPO_DATE, NONE }
    public enum SourceRequestMode { DATE, MONTH, RANGE, NONE }
    public enum RequestEvidenceStatus { DOCUMENTED_CANDIDATE, UNCONFIRMED, CONFLICT }
    public enum BatchPlanning { SINGLE_DATE, SINGLE_MONTH, SOURCE_RANGE, ORIGINAL_PARAMS, UNCONFIRMED }
    public enum CalendarEvidenceStatus { DOCUMENTED, UNCONFIRMED, CONFLICT }
    public enum CompletenessStatus { UNCONFIRMED }
    public enum CalendarProfile {
        C_A, C_M, C_S, C_N, C_X;
        public String value() { return name().replace('_', '-'); }
    }
    public record Limits(Integer maxRangeDays) {
        public Limits {
            if (!Integer.valueOf(31).equals(maxRangeDays)) throw new IllegalArgumentException("Range limit must equal 31");
        }
    }
    public record CompletenessPolicy(CompletenessStatus status, CompletenessStatus paginationStatus,
            String documentedLimit, String requirement) {
        public CompletenessPolicy {
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(paginationStatus, "paginationStatus");
            requireText(documentedLimit);
            requireText(requirement);
        }
    }

    public DownloadPolicy {
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(dateSemantic, "dateSemantic");
        requireText(description);
        Objects.requireNonNull(requestEvidenceStatus, "requestEvidenceStatus");
        Objects.requireNonNull(batchPlanning, "batchPlanning");
        Objects.requireNonNull(recoveryPolicy, "recoveryPolicy");
        Objects.requireNonNull(completenessPolicy, "completenessPolicy");
        evidenceRefs = copyEvidence(evidenceRefs);
        if ((mode == Mode.ORIGINAL_PARAMS) != (limits == null)) {
            throw new IllegalArgumentException("Range modes require limits");
        }
        if (mode == Mode.TRADE_DATE_RANGE ? calendarProfile == null || calendarEvidenceStatus == null
                : calendarProfile != null || calendarEvidenceStatus != null) {
            throw new IllegalArgumentException("Only trade date ranges require calendar evidence");
        }
        boolean semanticMatches = switch (mode) {
            case TRADE_DATE_RANGE -> dateSemantic == DateSemantic.TRADE_DATE;
            case ANN_DATE_RANGE -> dateSemantic == DateSemantic.ANN_DATE;
            case MONTH_RANGE -> dateSemantic == DateSemantic.COVERED_MONTH;
            case ORIGINAL_PARAMS -> dateSemantic == DateSemantic.NONE;
            case NATIVE_RANGE -> dateSemantic == DateSemantic.CALENDAR_DATE || dateSemantic == DateSemantic.IPO_DATE || dateSemantic == DateSemantic.ANN_DATE;
        };
        if (!semanticMatches) throw new IllegalArgumentException("Date semantic must match mode");
        if (requestEvidenceStatus == RequestEvidenceStatus.UNCONFIRMED) {
            if (sourceRequestMode != null || sourceDateParameter != null || batchPlanning != BatchPlanning.UNCONFIRMED) {
                throw new IllegalArgumentException("Unconfirmed requests cannot declare a candidate");
            }
        } else {
            Objects.requireNonNull(sourceRequestMode, "sourceRequestMode");
            boolean matches = switch (sourceRequestMode) {
                case DATE -> batchPlanning == BatchPlanning.SINGLE_DATE &&
                        (mode == Mode.TRADE_DATE_RANGE && "trade_date".equals(sourceDateParameter)
                        || mode == Mode.ANN_DATE_RANGE && "ann_date".equals(sourceDateParameter));
                case MONTH -> mode == Mode.MONTH_RANGE && batchPlanning == BatchPlanning.SINGLE_MONTH && "month".equals(sourceDateParameter);
                case RANGE -> mode != Mode.ORIGINAL_PARAMS && mode != Mode.MONTH_RANGE
                        && batchPlanning == BatchPlanning.SOURCE_RANGE && sourceDateParameter == null;
                case NONE -> mode == Mode.ORIGINAL_PARAMS && batchPlanning == BatchPlanning.ORIGINAL_PARAMS && sourceDateParameter == null;
            };
            if (!matches) throw new IllegalArgumentException("Source request mapping must match candidate and mode");
        }
    }

    static List<String> copyEvidence(List<String> refs) {
        refs = List.copyOf(Objects.requireNonNull(refs, "evidenceRefs"));
        if (refs.isEmpty() || new HashSet<>(refs).size() != refs.size()) {
            throw new IllegalArgumentException("Evidence references must be nonempty and unique");
        }
        for (String ref : refs) {
            requireText(ref);
            if (ref.startsWith("/") || ref.contains(":") || ref.contains("..") || ref.contains("\\")) {
                throw new IllegalArgumentException("Evidence must reference repository resources");
            }
        }
        return refs;
    }

    private static void requireText(String value) {
        Objects.requireNonNull(value, "text");
        if (value.isBlank() || value.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Policy text must be a nonblank safe summary");
        }
    }
}
