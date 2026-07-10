package com.security.project.domain.chat.dto;

import java.time.Instant;
import java.util.UUID;

import com.security.project.domain.chat.entity.ChatMember;
import com.security.project.domain.chat.entity.MemberRole;

/**
 * A member of a chat, as shown in a chat's participant list.
 *
 * @param userId   the member's user id
 * @param username the member's username
 * @param role     ADMIN or MEMBER
 * @param muted    whether this member has muted the chat
 * @param joinedAt when they joined
 */
public record ChatMemberDto(
        UUID userId,
        String username,
        MemberRole role,
        boolean muted,
        Instant joinedAt
) {
    /** Requires {@code member.getUser()} to be loaded (use a fetch-join query). */
    public static ChatMemberDto from(ChatMember member) {
        return new ChatMemberDto(
                member.getUser().getId(),
                member.getUser().getUsername(),
                member.getRole(),
                member.isMuted(),
                member.getJoinedAt());
    }
}
