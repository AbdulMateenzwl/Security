package com.security.project.domain.chat.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.security.project.domain.chat.entity.Chat;

public interface ChatRepository extends JpaRepository<Chat, UUID> {

    /**
     * Existing DIRECT chat(s) shared by two users. A direct chat always has exactly two members, so
     * a chat that contains both users is the 1:1 conversation between them. Returns a list (not a
     * single Optional) purely to be robust against duplicates created before dedup existed; callers
     * take the first.
     */
    @Query("""
            SELECT c FROM Chat c
            WHERE c.type = com.security.project.domain.chat.entity.ChatType.DIRECT
              AND EXISTS (SELECT 1 FROM ChatMember m1 WHERE m1.chat = c AND m1.user.id = :userA)
              AND EXISTS (SELECT 1 FROM ChatMember m2 WHERE m2.chat = c AND m2.user.id = :userB)
            ORDER BY c.createdAt ASC
            """)
    List<Chat> findDirectChatsBetween(@Param("userA") UUID userA, @Param("userB") UUID userB);
}
