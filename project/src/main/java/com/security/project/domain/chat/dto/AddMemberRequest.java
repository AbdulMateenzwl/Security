package com.security.project.domain.chat.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

/** Add a user to a group chat. */
public record AddMemberRequest(
        @NotNull
        UUID userId
) {
}
