package com.akkc.tensor.web.download;

import com.akkc.tensor.core.validation.ParameterValidator.ParameterValidationException;
import com.akkc.tensor.plugin.api.error.ErrorCode;
import com.akkc.tensor.plugin.api.error.TensorException;
import com.akkc.tensor.web.dto.FieldErrorResponse;
import java.util.List;

/** The only domain exception that may be unwrapped from a Jackson binding failure. */
public final class DownloadBindingException extends TensorException {
    private final List<FieldErrorResponse> fieldErrors;

    public DownloadBindingException(ErrorCode code, List<FieldErrorResponse> fieldErrors) {
        super(code, "Download request could not be bound");
        this.fieldErrors = List.copyOf(fieldErrors);
    }

    static DownloadBindingException from(TensorException failure) {
        if (failure instanceof DownloadBindingException binding) {
            return binding;
        }
        return new DownloadBindingException(failure.code(),
                failure instanceof ParameterValidationException validation
                        ? validation.fieldErrors().stream()
                                .map(field -> new FieldErrorResponse(field.field(), field.message())).toList()
                        : List.of());
    }

    public List<FieldErrorResponse> fieldErrors() {
        return fieldErrors;
    }
}
