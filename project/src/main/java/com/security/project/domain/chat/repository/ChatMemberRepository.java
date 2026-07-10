package com.security.project.domain.chat.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.security.project.domain.chat.entity.ChatMember;

public interface ChatMemberRepository extends JpaRepository<ChatMember, UUID> {

    Optional<ChatMember> findByChatIdAndUserId(UUID chatId, UUID userId);

    boolean existsByChatIdAndUserId(UUID chatId, UUID userId);

    long countByChatId(UUID chatId);

    /** Members of a chat, with their user eagerly loaded (to render usernames without N+1). */
    @Query("SELECT cm FROM ChatMember cm JOIN FETCH cm.user WHERE cm.chat.id = :chatId ORDER BY cm.joinedAt ASC")
    List<ChatMember> findByChatIdWithUser(@Param("chatId") UUID chatId);

    /** A user's memberships, with the chat eagerly loaded (for the chat-list screen). */
    @Query("SELECT cm FROM ChatMember cm JOIN FETCH cm.chat WHERE cm.user.id = :userId ORDER BY cm.joinedAt DESC")
    List<ChatMember> findByUserIdWithChat(@Param("userId") UUID userId);
}
