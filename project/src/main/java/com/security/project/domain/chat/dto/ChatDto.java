package com.security.project.domain.chat.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.security.project.domain.chat.entity.Chat;
import com.security.project.domain.chat.entity.ChatType;

/**
 * A chat and its participants, as returned to clients.
 *
 * @param id                     chat id
 * @param type                   DIRECT or GROUP
 * @param name                   display name (null for DIRECT)
 * @param avatarUrl              optional avatar
 * @param disappearingMessageTtl disappearing timer in seconds, or null when off
 * @param createdBy              id of the user who created the chat
 * @param createdAt              creation time
 * @param updatedAt              last-updated time
 * @param members                the chat's members
 */
public record ChatDto(
        UUID id,
        ChatType type,
        String name,
        String avatarUrl,
        Integer disappearingMessageTtl,
        UUID createdBy,
        Instant createdAt,
        Instant updatedAt,
        List<ChatMemberDto> members
) {
    public static ChatDto from(Chat chat, List<ChatMemberDto> members) {
        return new ChatDto(
                chat.getId(),
                chat.getType(),
                chat.getName(),
                chat.getAvatarUrl(),
                chat.getDisappearingMessageTtl(),
                chat.getCreatedBy().getId(),
                chat.getCreatedAt(),
                chat.getUpdatedAt(),
                members);
    }
}
