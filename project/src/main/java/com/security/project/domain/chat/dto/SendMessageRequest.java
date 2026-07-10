package com.security.project.domain.chat.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

/**
 * Send an encrypted message to a chat.
 *
 * @param ciphertext        Signal ciphertext (Base64 on the wire); the server never decrypts it
 * @param ciphertextType    Signal message type (1 = WhisperMessage, 3 = PreKeyWhisperMessage)
 * @param replyToMessageId  optional id of the message being replied to (must be in the same chat)
 */
public record SendMessageRequest(
        @NotNull
        byte[] ciphertext,

        int ciphertextType,

        UUID replyToMessageId
) {
}
