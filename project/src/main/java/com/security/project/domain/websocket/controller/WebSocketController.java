package com.security.project.domain.websocket.controller;

import java.security.Principal;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import com.security.project.config.properties.RateLimitProperties;
import com.security.project.domain.chat.dto.MessageDto;
import com.security.project.domain.chat.dto.SendMessageRequest;
import com.security.project.domain.chat.service.ChatAccessGuard;
import com.security.project.domain.chat.service.MessageService;
import com.security.project.domain.websocket.dto.TaskUpdateEvent;
import com.security.project.domain.websocket.dto.TypingEvent;
import com.security.project.domain.websocket.dto.WebSocketMessage;
import com.security.project.security.ratelimit.RateLimiterService;

import jakarta.validation.Valid;

/**
 * Realtime STOMP handlers. The principal name is the authenticated user id, set by
 * {@link com.security.project.security.websocket.StompAuthChannelInterceptor} at CONNECT.
 *
 * <p>Subscriptions are authorized by the channel interceptor, but a client could still {@code SEND}
 * to a chat it hasn't subscribed to, so every handler re-checks chat membership before broadcasting.
 * Message persistence and its membership check are delegated to {@link MessageService}.</p>
 */
@Controller
public class WebSocketController {

    private static final Logger log = LoggerFactory.getLogger(WebSocketController.class);

    private final MessageService messageService;
    private final ChatAccessGuard chatAccessGuard;
    private final RateLimiterService rateLimiter;
    private final RateLimitProperties rateLimitProperties;
    private final SimpMessagingTemplate broker;

    public WebSocketController(MessageService messageService,
                               ChatAccessGuard chatAccessGuard,
                               RateLimiterService rateLimiter,
                               RateLimitProperties rateLimitProperties,
                               SimpMessagingTemplate broker) {
        this.messageService = messageService;
        this.chatAccessGuard = chatAccessGuard;
        this.rateLimiter = rateLimiter;
        this.rateLimitProperties = rateLimitProperties;
        this.broker = broker;
    }

    /** Persist an encrypted message (membership enforced in the service) and fan it out to the chat. */
    @MessageMapping("/chat.send")
    public void send(@Valid @Payload WebSocketMessage msg, Principal principal) {
        UUID userId = UUID.fromString(principal.getName());
        // Share the same per-user "message-send" bucket as the REST endpoint, so the limit is unified.
        if (!rateLimiter.tryConsume("message-send", userId.toString(), rateLimitProperties.messageSend())
                .isConsumed()) {
            throw new MessagingException("Message rate limit exceeded — slow down");
        }
        MessageDto dto = messageService.sendMessage(userId, msg.chatId(),
                new SendMessageRequest(msg.ciphertext(), msg.ciphertextType(), msg.replyToMessageId()));
        broker.convertAndSend("/topic/chat/" + msg.chatId(), dto);
    }

    /** Broadcast a typing indicator to the chat (not persisted). */
    @MessageMapping("/chat.typing")
    public void typing(@Valid @Payload TypingEvent event, Principal principal) {
        UUID userId = UUID.fromString(principal.getName());
        requireMembership(event.chatId(), userId);
        broker.convertAndSend("/topic/chat/" + event.chatId() + "/typing",
                new TypingEvent(event.chatId(), userId, event.typing()));
    }

    /** Rebroadcast a task-change notification to the chat's task topic (not persisted). */
    @MessageMapping("/task.update")
    public void taskUpdate(@Valid @Payload TaskUpdateEvent event, Principal principal) {
        UUID userId = UUID.fromString(principal.getName());
        requireMembership(event.chatId(), userId);
        broker.convertAndSend("/topic/tasks/" + event.chatId(),
                new TaskUpdateEvent(event.chatId(), event.taskId(), event.action(), userId));
    }

    private void requireMembership(UUID chatId, UUID userId) {
        if (!chatAccessGuard.isMember(chatId, userId)) {
            throw new MessagingException("Not a member of this chat");
        }
    }

    /**
     * Handle rejected inbound messages (validation, rate limit, membership) concisely — this marks the
     * exception handled so Spring doesn't log the whole STOMP frame (which carries session data) at
     * ERROR. The offending message is simply dropped, never persisted or broadcast.
     */
    @MessageExceptionHandler
    public void handleRejectedMessage(Throwable ex) {
        log.debug("Rejected inbound WebSocket message: {}", ex.getMessage());
    }
}
