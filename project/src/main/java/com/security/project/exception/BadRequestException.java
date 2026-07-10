package com.security.project.exception;

import org.springframework.http.HttpStatus;

/** The request is semantically invalid (beyond field-level validation). Maps to 400. */
public class BadRequestException extends ApiException {

    public BadRequestException(String message) {
        super(HttpStatus.BAD_REQUEST, "BAD_REQUEST", message);
    }
}
