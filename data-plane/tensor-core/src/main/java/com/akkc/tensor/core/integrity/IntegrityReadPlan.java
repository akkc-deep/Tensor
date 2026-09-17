package com.akkc.tensor.core.integrity;

import com.akkc.tensor.plugin.api.integrity.IntegrityDateRange;
import com.akkc.tensor.plugin.api.integrity.IntegrityDescriptor;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record IntegrityReadPlan(IntegrityDescriptor target, List<ReferencePermit> references) {
    public IntegrityReadPlan {
        Objects.requireNonNull(target, "target");
        references = List.copyOf(Objects.requireNonNull(references, "references"));
    }

    public record ReferencePermit(IntegrityDescriptor descriptor, String dateField,
            IntegrityDateRange range, Map<String, Object> fixedEqualities, String purpose) {
        public ReferencePermit {
            Objects.requireNonNull(descriptor, "descriptor");
            fixedEqualities = Collections.unmodifiableMap(new LinkedHashMap<>(
                    Objects.requireNonNull(fixedEqualities, "fixedEqualities")));
            Objects.requireNonNull(purpose, "purpose");
            if (purpose.isBlank()) throw new IllegalArgumentException("purpose must not be blank");
        }
    }
}
