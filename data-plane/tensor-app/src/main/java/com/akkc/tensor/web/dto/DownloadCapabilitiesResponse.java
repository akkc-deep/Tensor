package com.akkc.tensor.web.dto;

import com.akkc.tensor.core.download.task.DownloadTaskService;
import com.akkc.tensor.plugin.api.download.batch.BatchDownloadDescriptor;
import com.akkc.tensor.web.dto.ApiDescriptorResponse.ParameterResponse;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.io.IOException;
import java.util.List;

public record DownloadCapabilitiesResponse(Single single, Range range) {
    public static DownloadCapabilitiesResponse from(DownloadTaskService.DownloadCapabilities capabilities) {
        var range = capabilities.range();
        return new DownloadCapabilitiesResponse(new Single(capabilities.single().available(),
                capabilities.single().api().parameters().stream().map(ParameterResponse::from).toList()),
                new Range(range.availability(), range.unavailableReason(), range.dateAxis(), range.dateLabel(),
                        range.startParameter(), range.endParameter(), range.parameters().stream().map(ParameterResponse::from).toList(),
                        range.planningMode(), range.splittable(), range.policyVersion(), new CompletenessRule(
                                range.completenessRule().kind(), range.completenessRule().rowLimit(), range.completenessRule().evidence())));
    }
    public record Single(boolean available, List<ParameterResponse> parameters) {
        public Single { parameters = List.copyOf(parameters); }
    }
    public record Range(BatchDownloadDescriptor.Availability availability, String unavailableReason,
            BatchDownloadDescriptor.DateAxis dateAxis, String dateLabel, String startParameter, String endParameter,
            List<ParameterResponse> parameters, BatchDownloadDescriptor.PlanningMode planningMode,
            boolean splittable, String policyVersion, CompletenessRule completenessRule) {
        public Range { parameters = List.copyOf(parameters); }
    }
    public record CompletenessRule(BatchDownloadDescriptor.CompletenessRule.Kind kind,
            @JsonSerialize(using = RowLimitSerializer.class) Long rowLimit, String evidence) {}

    public static final class RowLimitSerializer extends JsonSerializer<Long> {
        @Override
        public void serialize(Long value, JsonGenerator generator, SerializerProvider serializers) throws IOException {
            generator.writeNumber(value);
        }
    }
}
