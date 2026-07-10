package com.security.project.domain.chat.service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.security.project.domain.chat.dto.AddMemberRequest;
import com.security.project.domain.chat.dto.ChatDto;
import com.security.project.domain.chat.dto.ChatMemberDto;
import com.security.project.domain.chat.dto.CreateChatRequest;
import com.security.project.domain.chat.dto.DisappearingTtlRequest;
import com.security.project.domain.chat.dto.UpdateChatRequest;
import com.security.project.domain.chat.entity.Chat;
import com.security.project.domain.chat.entity.ChatMember;
import com.security.project.domain.chat.entity.ChatType;
import com.security.project.domain.chat.entity.MemberRole;
import com.security.project.domain.chat.repository.ChatMemberRepository;
import com.security.project.domain.chat.repository.ChatRepository;
import com.security.project.domain.user.entity.User;
import com.security.project.domain.user.repository.UserRepository;
import com.security.project.exception.BadRequestException;
import com.security.project.exception.ResourceNotFoundException;

/**
 * Chat and membership management.
 *
 * <p>Authorization is enforced here, in the service layer — never inferred by controllers. Every
 * operation first proves the caller is a member (and, for administrative actions, an ADMIN) of the
 * chat. Authorization failures throw {@link AccessDeniedException} (mapped to 403) and never reveal
 * whether the chat exists, so probing for chat ids leaks nothing.</p>
 */
@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private final ChatRepository chatRepository;
    private final ChatMemberRepository chatMemberRepository;
    private final UserRepository userRepository;

    public ChatService(ChatRepository chatRepository,
                       ChatMemberRepository chatMemberRepository,
                       UserRepository userRepository) {
        this.chatRepository = chatRepository;
        this.chatMemberRepository = chatMemberRepository;
        this.userRepository = userRepository;
    }

    /**
     * Create a chat with the caller as its first (ADMIN) member.
     *
     * <p>DIRECT chats require exactly one other participant and carry no name; GROUP chats require a
     * name. Listed members are added as MEMBER (duplicates and the creator are ignored).</p>
     */
    @Transactional
    public ChatDto createChat(UUID creatorId, CreateChatRequest req) {
        User creator = requireUser(creatorId);

        // Distinct member ids, excluding the creator (added separately as ADMIN).
        Set<UUID> memberIds = new LinkedHashSet<>(req.memberIds());
        memberIds.remove(creatorId);

        Chat chat = new Chat();
        chat.setType(req.type());
        chat.setCreatedBy(creator);

        if (req.type() == ChatType.DIRECT) {
            if (memberIds.size() != 1) {
                throw new BadRequestException("A direct chat must have exactly one other participant");
            }
        } else {
            if (req.name() == null || req.name().isBlank()) {
                throw new BadRequestException("A group chat requires a name");
            }
            chat.setName(req.name().trim());
        }
        Chat saved = chatRepository.save(chat);

        addMemberInternal(saved, creator, MemberRole.ADMIN);
        for (UUID memberId : memberIds) {
            addMemberInternal(saved, requireUser(memberId), MemberRole.MEMBER);
        }

        log.info("Created {} chat id={} by user id={} with {} member(s)",
                req.type(), saved.getId(), creatorId, memberIds.size() + 1);
        return toDto(saved);
    }

    /** All chats the caller is a member of. */
    @Transactional(readOnly = true)
    public List<ChatDto> listChats(UUID userId) {
        return chatMemberRepository.findByUserIdWithChat(userId).stream()
                .map(ChatMember::getChat)
                .map(this::toDto)
                .toList();
    }

    /** Chat details — caller must be a member. */
    @Transactional(readOnly = true)
    public ChatDto getChat(UUID userId, UUID chatId) {
        requireMembership(chatId, userId);
        return toDto(loadChat(chatId));
    }

    /** Update a group chat's name/avatar — ADMIN only. */
    @Transactional
    public ChatDto updateChat(UUID userId, UUID chatId, UpdateChatRequest req) {
        requireAdmin(chatId, userId);
        Chat chat = loadChat(chatId);
        if (chat.getType() == ChatType.DIRECT) {
            throw new BadRequestException("Direct chats have no editable name or avatar");
        }
        if (req.name() != null) {
            chat.setName(req.name().isBlank() ? null : req.name().trim());
        }
        if (req.avatarUrl() != null) {
            chat.setAvatarUrl(req.avatarUrl().isBlank() ? null : req.avatarUrl());
        }
        log.info("Updated chat id={} by admin id={}", chatId, userId);
        return toDto(chat);
    }

    /** Delete a chat (cascades to members and messages) — ADMIN only. */
    @Transactional
    public void deleteChat(UUID userId, UUID chatId) {
        requireAdmin(chatId, userId);
        chatRepository.deleteById(chatId);
        log.info("Deleted chat id={} by admin id={}", chatId, userId);
    }

    /** Add a member to a GROUP chat — ADMIN only. */
    @Transactional
    public ChatDto addMember(UUID actorId, UUID chatId, AddMemberRequest req) {
        requireAdmin(chatId, actorId);
        Chat chat = loadChat(chatId);
        if (chat.getType() == ChatType.DIRECT) {
            throw new BadRequestException("Cannot add members to a direct chat");
        }
        if (chatMemberRepository.existsByChatIdAndUserId(chatId, req.userId())) {
            throw new BadRequestException("User is already a member of this chat");
        }
        addMemberInternal(chat, requireUser(req.userId()), MemberRole.MEMBER);
        log.info("Added user id={} to chat id={} by admin id={}", req.userId(), chatId, actorId);
        return toDto(chat);
    }

    /** Remove a member from a chat — ADMIN only. */
    @Transactional
    public void removeMember(UUID actorId, UUID chatId, UUID targetUserId) {
        requireAdmin(chatId, actorId);
        ChatMember target = chatMemberRepository.findByChatIdAndUserId(chatId, targetUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User is not a member of this chat"));
        chatMemberRepository.delete(target);
        log.info("Removed user id={} from chat id={} by admin id={}", targetUserId, chatId, actorId);
    }

    /** Set or clear the disappearing-message timer — ADMIN only. */
    @Transactional
    public ChatDto setDisappearingTtl(UUID userId, UUID chatId, DisappearingTtlRequest req) {
        requireAdmin(chatId, userId);
        Chat chat = loadChat(chatId);
        chat.setDisappearingMessageTtl(req.ttlSeconds());
        log.info("Set disappearing ttl={} on chat id={} by admin id={}", req.ttlSeconds(), chatId, userId);
        return toDto(chat);
    }

    // --- internals ---------------------------------------------------------

    private void addMemberInternal(Chat chat, User user, MemberRole role) {
        ChatMember member = new ChatMember();
        member.setChat(chat);
        member.setUser(user);
        member.setRole(role);
        chatMemberRepository.save(member);
    }

    /** Prove the caller is a member; throws 403 (not 404) so chat existence is never revealed. */
    private ChatMember requireMembership(UUID chatId, UUID userId) {
        return chatMemberRepository.findByChatIdAndUserId(chatId, userId)
                .orElseThrow(() -> new AccessDeniedException("Not a member of this chat"));
    }

    private void requireAdmin(UUID chatId, UUID userId) {
        ChatMember member = requireMembership(chatId, userId);
        if (member.getRole() != MemberRole.ADMIN) {
            throw new AccessDeniedException("Admin role required for this action");
        }
    }

    private Chat loadChat(UUID chatId) {
        return chatRepository.findById(chatId)
                .orElseThrow(() -> new ResourceNotFoundException("Chat not found"));
    }

    private User requireUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private ChatDto toDto(Chat chat) {
        List<ChatMemberDto> members = chatMemberRepository.findByChatIdWithUser(chat.getId()).stream()
                .map(ChatMemberDto::from)
                .toList();
        return ChatDto.from(chat, members);
    }
}
