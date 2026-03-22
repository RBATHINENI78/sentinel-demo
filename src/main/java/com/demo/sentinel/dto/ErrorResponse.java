package com.demo.sentinel.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Unified error response shape for all API errors.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
    String            errorCode,
    String            message,
    LocalDateTime     timestamp,
    Map<String,String> fieldErrors    // populated for validation errors only
) {
    /** Convenience factory for simple errors. */
    public static ErrorResponse of(String errorCode, String message) {
        return new ErrorResponse(errorCode, message, LocalDateTime.now(), null);
    }

    /** Convenience factory for validation errors with field details. */
    public static ErrorResponse validation(Map<String,String> fieldErrors) {
        return new ErrorResponse(
            "VALIDATION_ERROR",
            "Request validation failed",
            LocalDateTime.now(),
            fieldErrors
        );
    }
}
