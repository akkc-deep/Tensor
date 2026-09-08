package com.akkc.tensor.plugin.api.download;

import com.akkc.tensor.plugin.api.descriptor.ParameterDescriptor;
import com.akkc.tensor.plugin.api.descriptor.ParameterType;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class DownloadParameterProjection {
    private DownloadParameterProjection() {}

    public static List<ParameterDescriptor> project(List<ParameterDescriptor> sourceParameters, DownloadPolicy policy) {
        var source = List.copyOf(Objects.requireNonNull(sourceParameters, "sourceParameters"));
        Objects.requireNonNull(policy, "policy");
        if (source.stream().map(ParameterDescriptor::name).distinct().count() != source.size()) throw invalid();
        if (policy.mode() == DownloadPolicy.Mode.ORIGINAL_PARAMS) return source;
        var dates = source.stream().filter(DownloadParameterProjection::isDate).toList();
        var projected = new ArrayList<>(source.stream().filter(p -> !isDate(p)).toList());
        if (policy.mode() == DownloadPolicy.Mode.NATIVE_RANGE) {
            var start = dates.stream().filter(p -> p.name().equals("start_date")).findFirst().orElseThrow(DownloadParameterProjection::invalid);
            var end = dates.stream().filter(p -> p.name().equals("end_date")).findFirst().orElseThrow(DownloadParameterProjection::invalid);
            if (dates.size() != 2 || !validMember(start, "end_date") || !validMember(end, "start_date")) throw invalid();
            projected.add(start); projected.add(end);
        } else {
            String name = switch (policy.mode()) {
                case TRADE_DATE_RANGE -> "trade_date";
                case ANN_DATE_RANGE -> "ann_date";
                case MONTH_RANGE -> "month";
                default -> throw invalid();
            };
            ParameterType type = policy.mode() == DownloadPolicy.Mode.MONTH_RANGE ? ParameterType.MONTH : ParameterType.DATE;
            if (dates.size() != 1 || !dates.getFirst().name().equals(name) || dates.getFirst().type() != type) throw invalid();
            projected.add(member("start_date", "开始日期", "end_date", policy.description()));
            projected.add(member("end_date", "结束日期", "start_date", policy.description()));
        }
        if (projected.stream().map(ParameterDescriptor::name).distinct().count() != projected.size()) throw invalid();
        return List.copyOf(projected);
    }

    private static boolean isDate(ParameterDescriptor parameter) {
        return parameter.type() == ParameterType.DATE || parameter.type() == ParameterType.MONTH
                || parameter.type() == ParameterType.DATE_RANGE_MEMBER;
    }

    private static boolean validMember(ParameterDescriptor parameter, String related) {
        return parameter.type() == ParameterType.DATE_RANGE_MEMBER && parameter.required()
                && related.equals(parameter.relatedParameter()) && parameter.defaultValue() == null;
    }

    private static ParameterDescriptor member(String name, String label, String related, String description) {
        return new ParameterDescriptor(name, label, description, ParameterType.DATE_RANGE_MEMBER,
                true, null, List.of(), null, related);
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Download date parameters do not match policy");
    }
}
