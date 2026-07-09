package com.security.project.domain.user.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Login payload.
 *
 * @param username the account username
 * @param password the plaintext password (checked against the stored BCrypt hash, never stored)
 */
public record LoginRequest(
        @NotBlank String username,
        @NotBlank String password
) {
}
