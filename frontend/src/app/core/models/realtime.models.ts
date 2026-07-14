/** Realtime (STOMP) payloads. */

/** Typing indicator broadcast on /topic/chat/{chatId}/typing. */
export interface TypingEvent {
  chatId: string;
  userId: string;
  typing: boolean;
}

/** Outbound typing send to /app/chat.typing (server fills in userId). */
export interface TypingSend {
  chatId: string;
  typing: boolean;
}

/**
 * Task-change notification broadcast on /topic/tasks/{chatId}. It carries no task body — it's a
 * lightweight "something changed, refresh the board" signal. Clients emit it on /app/task.update
 * after a REST task change; the server rebroadcasts it (filling in userId).
 */
export interface TaskUpdateEvent {
  chatId: string;
  taskId: string;
  action: string;
  userId: string;
}

/** Outbound task-update send to /app/task.update (server fills in userId). */
export interface TaskUpdateSend {
  chatId: string;
  taskId: string;
  action: string;
}
