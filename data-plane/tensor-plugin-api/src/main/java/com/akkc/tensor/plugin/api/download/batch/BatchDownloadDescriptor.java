package com.akkc.tensor.plugin.api.download.batch;

import com.akkc.tensor.plugin.api.descriptor.ParameterDescriptor;
import com.akkc.tensor.plugin.api.descriptor.ParameterType;
import java.util.List;
import java.util.Objects;

/** Versioned range semantics, independent of a plugin's SINGLE parameter metadata. */
public record BatchDownloadDescriptor(
        List<ParameterDescriptor> parameters,
        String startParameter,
        String endParameter,
        DateAxis dateAxis,
        String dateLabel,
        PlanningMode planningMode,
        boolean splittable,
        Availability availability,
        String unavailableReason,
        String policyVersion,
        CompletenessRule completenessRule) {

    public BatchDownloadDescriptor {
        parameters = List.copyOf(parameters);
        Objects.requireNonNull(availability, "availability");
        Objects.requireNonNull(completenessRule, "completenessRule");
        requireText(policyVersion, "policyVersion");
        if (parameters.stream().map(ParameterDescriptor::name).distinct().count() != parameters.size()) {
            throw new IllegalArgumentException("parameters must not contain duplicate names");
        }
        if (availability == Availability.AVAILABLE) {
            if (unavailableReason != null || completenessRule.kind() == CompletenessRule.Kind.UNKNOWN) {
                throw new IllegalArgumentException("AVAILABLE requires confirmed completeness and no unavailable reason");
            }
        } else {
            requireText(unavailableReason, "unavailableReason");
        }
        if (availability == Availability.UNSUPPORTED) {
            if (!parameters.isEmpty() || startParameter != null || endParameter != null
                    || dateAxis != null || dateLabel != null || planningMode != null || splittable
                    || completenessRule.kind() != CompletenessRule.Kind.UNKNOWN) {
                throw new IllegalArgumentException("UNSUPPORTED must not expose range semantics");
            }
        } else {
            Objects.requireNonNull(dateAxis, "dateAxis");
            Objects.requireNonNull(planningMode, "planningMode");
            requireText(dateLabel, "dateLabel");
            var endpoints = parameters.stream()
                    .filter(parameter -> parameter.type() == ParameterType.DATE_RANGE_MEMBER).toList();
            if (endpoints.size() != 2
                    || !endpoints.getFirst().name().equals(startParameter)
                    || !endpoints.getLast().name().equals(endParameter)
                    || !endpoints.getFirst().required() || !endpoints.getLast().required()
                    || !endpoints.getFirst().relatedParameter().equals(endParameter)
                    || !endpoints.getLast().relatedParameter().equals(startParameter)) {
                throw new IllegalArgumentException("range requires ordered, mutually related, required endpoints");
            }
            if (splittable && planningMode != PlanningMode.NATIVE_RANGE) {
                throw new IllegalArgumentException("daily planning cannot be split");
            }
        }
    }

    public enum DateAxis {
        TRADE_DATE, ANNOUNCEMENT_DATE, REPORT_PERIOD, CALENDAR_DATE, ISSUE_DATE
    }

    public enum PlanningMode {
        NATIVE_RANGE, CALENDAR_DAYS, TRADING_DAYS
    }

    public enum Availability {
        AVAILABLE, NEEDS_VERIFICATION, UNSUPPORTED
    }

    /** Evidence describes a verified source contract, never a database insert batch size. */
    public record CompletenessRule(Kind kind, Long rowLimit, String evidence) {
        public CompletenessRule {
            Objects.requireNonNull(kind, "kind");
            if (kind == Kind.CONFIRMED_ROW_LIMIT) {
                if (rowLimit == null || rowLimit <= 0) {
                    throw new IllegalArgumentException("confirmed row limit must be positive");
                }
            } else if (rowLimit != null) {
                throw new IllegalArgumentException("only a confirmed row limit may specify rowLimit");
            }
            if (kind == Kind.UNKNOWN) {
                if (evidence != null) {
                    throw new IllegalArgumentException("unknown completeness must not claim evidence");
                }
            } else {
                requireText(evidence, "evidence");
            }
        }

        public enum Kind { CONFIRMED_ROW_LIMIT, VERIFIED_RULE, UNKNOWN }
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
