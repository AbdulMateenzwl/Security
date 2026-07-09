package com.security.project.domain.user.dto;

import java.time.Instant;
import java.util.UUID;

import com.security.project.domain.user.entity.UserSession;

/**
 * A user's active login session, as shown on the "active sessions / devices" screen.
 *
 * @param id        session id (used to revoke a specific session)
 * @param deviceInfo the User-Agent captured at login
 * @param ipAddress  the IP captured at login
 * @param createdAt  when the session started
 * @param expiresAt  when the session (refresh token) expires
 * @param current    whether this is the session making the current request
 */
public record SessionDto(
        UUID id,
        String deviceInfo,
        String ipAddress,
        Instant createdAt,
        Instant expiresAt,
        boolean current
) {
    public static SessionDto from(UserSession s, boolean current) {
        return new SessionDto(
                s.getId(),
                s.getDeviceInfo(),
                s.getIpAddress(),
                s.getCreatedAt(),
                s.getExpiresAt(),
                current);
    }
}
