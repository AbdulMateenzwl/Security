package com.security.project.domain.user.dto;

/**
 * Successful authentication result: a short-lived access token, a longer-lived refresh token, and a
 * safe view of the authenticated user.
 *
 * @param accessToken  RS256-signed JWT for API calls (send as {@code Authorization: Bearer ...})
 * @param refreshToken RS256-signed JWT used only against {@code /api/auth/refresh}
 * @param tokenType    always {@code "Bearer"}
 * @param expiresInMs  access-token lifetime in milliseconds
 * @param user         the authenticated user
 */
public record AuthResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresInMs,
        UserDto user
) {
    public static AuthResponse of(String accessToken, String refreshToken, long expiresInMs, UserDto user) {
        return new AuthResponse(accessToken, refreshToken, "Bearer", expiresInMs, user);
    }
}
