package com.security.project.domain.user.dto;

import java.time.Instant;
import java.util.UUID;

import com.security.project.domain.user.entity.User;

/**
 * Public, safe projection of a {@link User}. Never includes the password hash or lockout internals.
 */
public record UserDto(
        UUID id,
        String username,
        String email,
        String githubUsername,
        Instant createdAt
) {
    public static UserDto from(User user) {
        return new UserDto(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getGithubUsername(),
                user.getCreatedAt());
    }
}
