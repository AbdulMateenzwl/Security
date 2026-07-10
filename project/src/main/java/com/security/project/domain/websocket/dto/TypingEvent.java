package com.security.project.domain.websocket.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

/**
 * Typing indicator. On the inbound {@code /app/chat.typing} send, only {@code chatId} and
 * {@code typing} matter; the server fills in {@code userId} from the authenticated principal before
 * broadcasting to {@code /topic/chat/{chatId}/typing}.
 */
public record TypingEvent(
        @NotNull
        UUID chatId,
        UUID userId,
        boolean typing
) {
}
