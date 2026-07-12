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
