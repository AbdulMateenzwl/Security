package com.security.project.domain.user.dto;

import java.util.UUID;

import com.security.project.domain.user.entity.User;

/**
 * Minimal public projection of a user for search results — id and username only.
 *
 * <p>Deliberately excludes email: search is available to any authenticated user, so returning email
 * addresses would let anyone harvest them. The full {@link UserDto} (with email) is only ever
 * returned to the user about themselves.</p>
 */
public record UserSummaryDto(
        UUID id,
        String username
) {
    public static UserSummaryDto from(User user) {
        return new UserSummaryDto(user.getId(), user.getUsername());
    }
}
