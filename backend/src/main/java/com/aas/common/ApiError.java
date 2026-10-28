package com.aas.common;

import java.util.List;
import java.util.UUID;

/** Uniform error body (SAD 4.4): no stack traces or internals are ever returned. */
public record ApiError(String code, String message, List<FieldError> fieldErrors, String traceId) {

    public record FieldError(String field, String message) {
    }

    public static ApiError of(String code, String message) {
        return new ApiError(code, message, List.of(), UUID.randomUUID().toString());
    }

    public static ApiError of(String code, String message, List<FieldError> fieldErrors) {
        return new ApiError(code, message, fieldErrors, UUID.randomUUID().toString());
    }
}
