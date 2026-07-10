package com.security.project.domain.websocket.dto;

import java.util.UUID;

/**
 * Realtime task-change notification for {@code SEND /app/task.update}, rebroadcast to
 * {@code /topic/tasks/{chatId}}. The server stamps {@code actorId} from the authenticated principal.
 *
 * @param chatId  chat the task belongs to
 * @param taskId  task that changed
 * @param action  a short action label (e.g. STATUS_CHANGED)
 * @param actorId who made the change (filled in by the server)
 */
public record TaskUpdateEvent(
        UUID chatId,
        UUID taskId,
        String action,
        UUID actorId
) {
}
