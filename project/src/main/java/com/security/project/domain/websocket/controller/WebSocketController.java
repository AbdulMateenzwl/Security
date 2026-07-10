package com.security.project.domain.websocket.controller;

import java.security.Principal;
import java.util.UUID;

import org.springframework.messaging.MessagingException;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import com.security.project.domain.chat.dto.MessageDto;
import com.security.project.domain.chat.dto.SendMessageRequest;
import com.security.project.domain.chat.repository.ChatMemberRepository;
import com.security.project.domain.chat.service.MessageService;
import com.security.project.domain.websocket.dto.TaskUpdateEvent;
import com.security.project.domain.websocket.dto.TypingEvent;
import com.security.project.domain.websocket.dto.WebSocketMessage;

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

    private final MessageService messageService;
    private final ChatMemberRepository chatMemberRepository;
    private final SimpMessagingTemplate broker;

    public WebSocketController(MessageService messageService,
                               ChatMemberRepository chatMemberRepository,
                               SimpMessagingTemplate broker) {
        this.messageService = messageService;
        this.chatMemberRepository = chatMemberRepository;
        this.broker = broker;
    }

    /** Persist an encrypted message (membership enforced in the service) and fan it out to the chat. */
    @MessageMapping("/chat.send")
    public void send(@Payload WebSocketMessage msg, Principal principal) {
        UUID userId = UUID.fromString(principal.getName());
        MessageDto dto = messageService.sendMessage(userId, msg.chatId(),
                new SendMessageRequest(msg.ciphertext(), msg.ciphertextType(), msg.replyToMessageId()));
        broker.convertAndSend("/topic/chat/" + msg.chatId(), dto);
    }

    /** Broadcast a typing indicator to the chat (not persisted). */
    @MessageMapping("/chat.typing")
    public void typing(@Payload TypingEvent event, Principal principal) {
        UUID userId = UUID.fromString(principal.getName());
        requireMembership(event.chatId(), userId);
        broker.convertAndSend("/topic/chat/" + event.chatId() + "/typing",
                new TypingEvent(event.chatId(), userId, event.typing()));
    }

    /** Rebroadcast a task-change notification to the chat's task topic (not persisted). */
    @MessageMapping("/task.update")
    public void taskUpdate(@Payload TaskUpdateEvent event, Principal principal) {
        UUID userId = UUID.fromString(principal.getName());
        requireMembership(event.chatId(), userId);
        broker.convertAndSend("/topic/tasks/" + event.chatId(),
                new TaskUpdateEvent(event.chatId(), event.taskId(), event.action(), userId));
    }

    private void requireMembership(UUID chatId, UUID userId) {
        if (!chatMemberRepository.existsByChatIdAndUserId(chatId, userId)) {
            throw new MessagingException("Not a member of this chat");
        }
    }
}
