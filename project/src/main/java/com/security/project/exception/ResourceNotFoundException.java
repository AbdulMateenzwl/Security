package com.security.project.exception;

import org.springframework.http.HttpStatus;

/** A requested resource does not exist (or the caller may not see it). Maps to 404. */
public class ResourceNotFoundException extends ApiException {

    public ResourceNotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, "NOT_FOUND", message);
    }
}
