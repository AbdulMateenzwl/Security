package com.security.project.exception;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Translates exceptions into the uniform {@link ErrorResponse} body.
 *
 * <p>Golden rule: never leak internals. Unexpected exceptions are logged server-side but the client
 * only ever receives a generic 500 message.</p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** All application exceptions carry their own status and safe error code. */
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApiException(ApiException ex) {
        return ResponseEntity.status(ex.getStatus())
                .body(ErrorResponse.of(ex.getErrorCode(), ex.getMessage()));
    }

    /** Bean-validation failures on @Valid request bodies → 400 with per-field detail. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new HashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.putIfAbsent(fe.getField(),
                    fe.getDefaultMessage() == null ? "invalid" : fe.getDefaultMessage());
        }
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of("VALIDATION_FAILED", "Request validation failed", fieldErrors));
    }

    /** Authorization failure — 403, and deliberately no hint about whether the resource exists. */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ErrorResponse.of("FORBIDDEN", "Access denied"));
    }

    /** Unique/foreign-key violations that slipped past pre-checks (race conditions) → 409. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrity(DataIntegrityViolationException ex) {
        log.warn("Data integrity violation", ex);   // detail stays server-side
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of("CONFLICT", "The request conflicts with existing data"));
    }

    /** Catch-all — log the real cause, return a generic message so nothing internal leaks. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of("INTERNAL_ERROR", "An unexpected error occurred"));
    }
}
