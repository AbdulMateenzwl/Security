package com.security.project.domain.chat.dto;

import java.util.List;
import java.util.UUID;

import com.security.project.domain.chat.entity.ChatType;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request to create a chat.
 *
 * <p>For {@link ChatType#DIRECT}: {@code memberIds} must contain exactly one other user and
 * {@code name} is ignored. For {@link ChatType#GROUP}: {@code name} is required and {@code memberIds}
 * lists the initial members (the creator is always added as ADMIN). Cross-field rules are enforced in
 * the service layer.</p>
 */
public record CreateChatRequest(
        @NotNull
        ChatType type,

        @Size(max = 100)
        String name,

        @NotNull
        List<UUID> memberIds
) {
}
