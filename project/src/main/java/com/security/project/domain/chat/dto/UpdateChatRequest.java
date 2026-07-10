package com.security.project.domain.chat.dto;

import jakarta.validation.constraints.Size;

/**
 * Update a group chat's display info. Both fields are optional; a {@code null} field is left
 * unchanged.
 */
public record UpdateChatRequest(
        @Size(max = 100)
        String name,

        @Size(max = 500)
        String avatarUrl
) {
}
