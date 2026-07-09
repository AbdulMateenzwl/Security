package com.security.project.exception;

import org.springframework.http.HttpStatus;

/**
 * Authentication failed. The message is intentionally generic ("Invalid username or password") so
 * that it never reveals whether the username exists — preventing account enumeration. Maps to 401.
 */
public class InvalidCredentialsException extends ApiException {

    public InvalidCredentialsException(String message) {
        super(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", message);
    }
}
