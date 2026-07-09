package com.security.project.domain.user.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request to mint a new access token from a valid refresh token.
 *
 * @param refreshToken the previously issued refresh token
 */
public record RefreshRequest(
        @NotBlank String refreshToken
) {
}
