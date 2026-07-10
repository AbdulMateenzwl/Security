package com.security.project.domain.chat.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.security.project.domain.chat.dto.MessageDto;
import com.security.project.domain.chat.dto.SendMessageRequest;
import com.security.project.domain.chat.dto.UpdateMessageStatusRequest;
import com.security.project.domain.chat.service.MessageService;
import com.security.project.domain.user.entity.User;

import jakarta.validation.Valid;

/**
 * Message endpoints. All require authentication; membership and sender authorization are enforced in
 * {@link MessageService}. History uses cursor pagination (newest first) — pass the oldest message id
 * you hold as {@code before} to page backwards.
 */
@RestController
public class MessageController {

    private final MessageService messageService;

    public MessageController(MessageService messageService) {
        this.messageService = messageService;
    }

    @PostMapping("/api/chats/{chatId}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    public MessageDto send(@AuthenticationPrincipal User user,
                           @PathVariable UUID chatId,
                           @Valid @RequestBody SendMessageRequest request) {
        return messageService.sendMessage(user.getId(), chatId, request);
    }

    @GetMapping("/api/chats/{chatId}/messages")
    public List<MessageDto> history(@AuthenticationPrincipal User user,
                                    @PathVariable UUID chatId,
                                    @RequestParam(required = false) UUID before,
                                    @RequestParam(defaultValue = "30") int limit) {
        return messageService.getMessages(user.getId(), chatId, before, limit);
    }

    @PutMapping("/api/messages/{messageId}/status")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateStatus(@AuthenticationPrincipal User user,
                             @PathVariable UUID messageId,
                             @Valid @RequestBody UpdateMessageStatusRequest request) {
        messageService.updateStatus(user.getId(), messageId, request.status());
    }

    @DeleteMapping("/api/messages/{messageId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal User user, @PathVariable UUID messageId) {
        messageService.deleteMessage(user.getId(), messageId);
    }
}
