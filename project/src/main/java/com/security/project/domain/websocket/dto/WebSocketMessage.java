package com.security.project.domain.websocket.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

/**
 * Inbound payload for {@code SEND /app/chat.send}: an encrypted message to relay to a chat.
 * {@code ciphertext} is Base64 on the wire and is never decrypted server-side.
 *
 * @param chatId           target chat
 * @param ciphertext       Signal ciphertext (Base64)
 * @param ciphertextType   Signal message type (1 = WhisperMessage, 3 = PreKeyWhisperMessage)
 * @param replyToMessageId optional parent message id
 */
public record WebSocketMessage(
        @NotNull
        UUID chatId,

        @NotEmpty
        byte[] ciphertext,

        int ciphertextType,

        UUID replyToMessageId
) {
}
