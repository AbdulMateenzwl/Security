package com.security.project.domain.chat.service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.security.project.domain.chat.dto.ChatDto;
import com.security.project.domain.chat.dto.ChatMemberDto;
import com.security.project.domain.chat.dto.CreateChatRequest;
import com.security.project.domain.chat.dto.DisappearingTtlRequest;
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
    private final ChatAccessGuard chatAccessGuard;
    private final UserRepository userRepository;

    public ChatService(ChatRepository chatRepository,
                       ChatMemberRepository chatMemberRepository,
                       ChatAccessGuard chatAccessGuard,
                       UserRepository userRepository) {
        this.chatRepository = chatRepository;
        this.chatMemberRepository = chatMemberRepository;
        this.chatAccessGuard = chatAccessGuard;
        this.userRepository = userRepository;
    }

    /**
     * Create a DIRECT chat with the caller as its first (ADMIN) member.
     *
     * <p>Only direct chats are supported: {@code memberIds} must contain exactly one other
     * participant. Group chats are not implemented and are rejected. If a direct chat between the
     * two users already exists it is returned as-is rather than creating a duplicate.</p>
     */
    @Transactional
    public ChatDto createChat(UUID creatorId, CreateChatRequest req) {
        User creator = userRepository.getByIdOrThrow(creatorId);

        if (req.type() != ChatType.DIRECT) {
            throw new BadRequestException("Only direct chats are supported");
        }

        // Distinct member ids, excluding the creator (added separately as ADMIN).
        Set<UUID> memberIds = new LinkedHashSet<>(req.memberIds());
        memberIds.remove(creatorId);
        if (memberIds.size() != 1) {
            throw new BadRequestException("A direct chat must have exactly one other participant");
        }
        UUID otherId = memberIds.iterator().next();

        // Reuse the existing 1:1 conversation instead of creating a duplicate.
        var existing = chatRepository.findDirectChatsBetween(creatorId, otherId).stream().findFirst();
        if (existing.isPresent()) {
            log.info("Reusing existing DIRECT chat id={} between user id={} and id={}",
                    existing.get().getId(), creatorId, otherId);
            return toDto(existing.get());
        }

        Chat chat = new Chat();
        chat.setType(ChatType.DIRECT);
        chat.setCreatedBy(creator);
        Chat saved = chatRepository.save(chat);

        addMemberInternal(saved, creator, MemberRole.ADMIN);
        addMemberInternal(saved, userRepository.getByIdOrThrow(otherId), MemberRole.MEMBER);

        log.info("Created DIRECT chat id={} by user id={} with user id={}", saved.getId(), creatorId, otherId);
        return toDto(saved);
    }

    /** All chats the caller is a member of. Loads all members in a single batched query (no N+1). */
    @Transactional(readOnly = true)
    public List<ChatDto> listChats(UUID userId) {
        List<Chat> chats = chatMemberRepository.findByUserIdWithChat(userId).stream()
                .map(ChatMember::getChat)
                .toList();
        if (chats.isEmpty()) {
            return List.of();
        }
        List<UUID> chatIds = chats.stream().map(Chat::getId).toList();
        Map<UUID, List<ChatMemberDto>> membersByChat =
                chatMemberRepository.findByChatIdInWithUser(chatIds).stream()
                        .collect(Collectors.groupingBy(
                                m -> m.getChat().getId(),
                                Collectors.mapping(ChatMemberDto::from, Collectors.toList())));
        return chats.stream()
                .map(chat -> ChatDto.from(chat, membersByChat.getOrDefault(chat.getId(), List.of())))
                .toList();
    }

    /** Chat details — caller must be a member. */
    @Transactional(readOnly = true)
    public ChatDto getChat(UUID userId, UUID chatId) {
        chatAccessGuard.requireMember(chatId, userId);
        return toDto(loadChat(chatId));
    }

    /** Delete a chat (cascades to members and messages) — ADMIN only. */
    @Transactional
    public void deleteChat(UUID userId, UUID chatId) {
        chatAccessGuard.requireAdmin(chatId, userId);
        chatRepository.deleteById(chatId);
        log.info("Deleted chat id={} by admin id={}", chatId, userId);
    }

    /** Set or clear the disappearing-message timer — ADMIN only. */
    @Transactional
    public ChatDto setDisappearingTtl(UUID userId, UUID chatId, DisappearingTtlRequest req) {
        chatAccessGuard.requireAdmin(chatId, userId);
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

    private Chat loadChat(UUID chatId) {
        return chatRepository.findById(chatId)
                .orElseThrow(() -> new ResourceNotFoundException("Chat not found"));
    }

    private ChatDto toDto(Chat chat) {
        List<ChatMemberDto> members = chatMemberRepository.findByChatIdWithUser(chat.getId()).stream()
                .map(ChatMemberDto::from)
                .toList();
        return ChatDto.from(chat, members);
    }
}
