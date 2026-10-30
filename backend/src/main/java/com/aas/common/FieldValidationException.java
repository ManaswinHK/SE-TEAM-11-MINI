package com.aas.common;

import java.util.List;

/** Business-rule validation failure tied to specific request fields; returned as 400 with fieldErrors. */
public class FieldValidationException extends RuntimeException {

    private final List<ApiError.FieldError> fieldErrors;

    public FieldValidationException(String field, String message) {
        super(message);
        this.fieldErrors = List.of(new ApiError.FieldError(field, message));
    }

    public List<ApiError.FieldError> getFieldErrors() {
        return fieldErrors;
    }
}
