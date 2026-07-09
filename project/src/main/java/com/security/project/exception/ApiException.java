package com.security.project.exception;

import org.springframework.http.HttpStatus;

/**
 * Base type for application exceptions that map to a specific HTTP status and a stable,
 * client-safe error code. Messages must never contain internal details (SQL, stack traces,
 * class names, file paths).
 */
public abstract class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String errorCode;

    protected ApiException(HttpStatus status, String errorCode, String message) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
