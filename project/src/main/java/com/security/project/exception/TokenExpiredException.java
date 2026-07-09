package com.security.project.exception;

import org.springframework.http.HttpStatus;

/** A JWT is well-formed and correctly signed but past its expiry. Maps to 401. */
public class TokenExpiredException extends ApiException {

    public TokenExpiredException(String message) {
        super(HttpStatus.UNAUTHORIZED, "TOKEN_EXPIRED", message);
    }
}
