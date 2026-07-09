package com.security.project.security;

import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Convenience accessors for the current authentication, populated by
 * {@link com.security.project.security.jwt.JwtAuthenticationFilter}.
 */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    /** The session id ({@code sid}) of the token that authenticated the current request. */
    public static UUID currentSessionId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getDetails() instanceof UUID sessionId) {
            return sessionId;
        }
        return null;
    }
}
