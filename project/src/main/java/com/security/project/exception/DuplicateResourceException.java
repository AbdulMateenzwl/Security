package com.security.project.exception;

import org.springframework.http.HttpStatus;

/** A uniqueness constraint would be violated (e.g. username/email already taken). Maps to 409. */
public class DuplicateResourceException extends ApiException {

    public DuplicateResourceException(String message) {
        super(HttpStatus.CONFLICT, "CONFLICT", message);
    }
}
