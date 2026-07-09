package com.security.project.exception;

import java.time.Instant;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Uniform error body returned for every failed request.
 *
 * <p>Deliberately minimal: it exposes a stable error code, a human-readable message, and (only for
 * validation failures) per-field details. It never carries stack traces, SQL, or internal class
 * names.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        String error,
        String message,
        Instant timestamp,
        Map<String, String> fieldErrors
) {
    public static ErrorResponse of(String error, String message) {
        return new ErrorResponse(error, message, Instant.now(), null);
    }

    public static ErrorResponse of(String error, String message, Map<String, String> fieldErrors) {
        return new ErrorResponse(error, message, Instant.now(), fieldErrors);
    }
}
