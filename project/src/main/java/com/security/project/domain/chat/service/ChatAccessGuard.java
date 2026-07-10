package com.security.project.domain.chat.service;

import java.util.UUID;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import com.security.project.domain.chat.entity.ChatMember;
import com.security.project.domain.chat.entity.MemberRole;
import com.security.project.domain.chat.repository.ChatMemberRepository;

/**
 * Central authorization checks for chat-scoped access, shared by the chat, message and task services
 * and the WebSocket layer.
 *
 * <p>Keeping the "is this user a member / an admin of this chat?" rule in one place ensures every
 * feature enforces it identically. The throwing methods raise {@link AccessDeniedException} (mapped
 * to 403, never 404) so a caller can never learn whether a chat exists by probing. Callers that need
 * a different failure mode (e.g. the WebSocket layer, which must emit a STOMP ERROR frame, or task
 * assignment, which is a 400) use {@link #isMember} and throw their own exception.</p>
 */
@Component
public class ChatAccessGuard {

    private final ChatMemberRepository chatMemberRepository;

    public ChatAccessGuard(ChatMemberRepository chatMemberRepository) {
        this.chatMemberRepository = chatMemberRepository;
    }

    /** Whether the user is a member of the chat, without throwing. */
    public boolean isMember(UUID chatId, UUID userId) {
        return chatMemberRepository.existsByChatIdAndUserId(chatId, userId);
    }

    /** Require chat membership, returning the membership; throws 403 if the caller is not a member. */
    public ChatMember requireMember(UUID chatId, UUID userId) {
        return chatMemberRepository.findByChatIdAndUserId(chatId, userId)
                .orElseThrow(() -> new AccessDeniedException("Not a member of this chat"));
    }

    /** Require the ADMIN role in the chat; throws 403 if not a member or not an admin. */
    public void requireAdmin(UUID chatId, UUID userId) {
        ChatMember member = requireMember(chatId, userId);
        if (member.getRole() != MemberRole.ADMIN) {
            throw new AccessDeniedException("Admin role required for this action");
        }
    }
}
