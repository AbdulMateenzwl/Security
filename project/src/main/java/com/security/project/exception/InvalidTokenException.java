package com.security.project.exception;

import org.springframework.http.HttpStatus;

/** A JWT is missing, malformed, has a bad signature, wrong type, or a revoked session. Maps to 401. */
public class InvalidTokenException extends ApiException {

    public InvalidTokenException(String message) {
        super(HttpStatus.UNAUTHORIZED, "INVALID_TOKEN", message);
    }
}
