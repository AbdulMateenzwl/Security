package com.security.project.exception;

import org.springframework.http.HttpStatus;

/** The account is temporarily locked after too many failed logins. Maps to 423 (Locked). */
public class AccountLockedException extends ApiException {

    public AccountLockedException(String message) {
        super(HttpStatus.LOCKED, "ACCOUNT_LOCKED", message);
    }
}
