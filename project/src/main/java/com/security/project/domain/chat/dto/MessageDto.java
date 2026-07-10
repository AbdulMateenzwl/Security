package com.security.project.domain.chat.dto;

import java.time.Instant;
import java.util.UUID;

import com.security.project.domain.chat.entity.Message;
import com.security.project.domain.chat.entity.MessageStatus;

/**
 * A message as returned to clients. {@code ciphertext} is Base64-encoded for JSON transport; the
 * client decrypts it locally with its Signal session.
 *
 * @param id               message id
 * @param chatId           owning chat
 * @param senderId         author
 * @param ciphertext       Signal ciphertext (Base64)
 * @param ciphertextType   Signal message type
 * @param replyToMessageId parent message id, or null
 * @param status           delivery status
 * @param expiresAt        disappearing-message expiry, or null
 * @param createdAt        when it was sent
 */
public record MessageDto(
        UUID id,
        UUID chatId,
        UUID senderId,
        byte[] ciphertext,
        int ciphertextType,
        UUID replyToMessageId,
        MessageStatus status,
        Instant expiresAt,
        Instant createdAt
) {
    public static MessageDto from(Message m) {
        return new MessageDto(
                m.getId(),
                m.getChat().getId(),
                m.getSender().getId(),
                m.getCiphertext(),
                m.getCiphertextType(),
                m.getReplyTo() == null ? null : m.getReplyTo().getId(),
                m.getStatus(),
                m.getExpiresAt(),
                m.getCreatedAt());
    }
}
