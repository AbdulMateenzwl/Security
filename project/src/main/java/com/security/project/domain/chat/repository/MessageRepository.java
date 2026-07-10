package com.security.project.domain.chat.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.security.project.domain.chat.entity.Message;

public interface MessageRepository extends JpaRepository<Message, UUID> {

    /**
     * Hard-delete every disappearing message whose expiry has passed. Their {@code message_receipts}
     * are removed by the database FK's {@code ON DELETE CASCADE}, and any reply pointing at a deleted
     * message is nulled via {@code ON DELETE SET NULL}. Returns the number of messages removed.
     */
    @Modifying
    @Query("DELETE FROM Message m WHERE m.expiresAt IS NOT NULL AND m.expiresAt < :now")
    int deleteExpired(@Param("now") Instant now);

    /**
     * First (newest) page of a chat's history, excluding already-expired disappearing messages.
     * Ordered by {@code (created_at, id)} descending so the cursor is deterministic.
     */
    @Query("""
            SELECT m FROM Message m
             WHERE m.chat.id = :chatId
               AND (m.expiresAt IS NULL OR m.expiresAt > :now)
             ORDER BY m.createdAt DESC, m.id DESC
            """)
    List<Message> findFirstPage(@Param("chatId") UUID chatId,
                                @Param("now") Instant now,
                                Pageable pageable);

    /**
     * Next page: messages strictly older than the {@code (cursorCreatedAt, cursorId)} cursor. The
     * id tie-break keeps pagination stable when two messages share a timestamp.
     */
    @Query("""
            SELECT m FROM Message m
             WHERE m.chat.id = :chatId
               AND (m.expiresAt IS NULL OR m.expiresAt > :now)
               AND (m.createdAt < :cursorCreatedAt
                    OR (m.createdAt = :cursorCreatedAt AND m.id < :cursorId))
             ORDER BY m.createdAt DESC, m.id DESC
            """)
    List<Message> findPageBefore(@Param("chatId") UUID chatId,
                                 @Param("cursorCreatedAt") Instant cursorCreatedAt,
                                 @Param("cursorId") UUID cursorId,
                                 @Param("now") Instant now,
                                 Pageable pageable);
}
