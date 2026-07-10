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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.security.project.domain.chat.dto.AddMemberRequest;
import com.security.project.domain.chat.dto.ChatDto;
import com.security.project.domain.chat.dto.CreateChatRequest;
import com.security.project.domain.chat.dto.DisappearingTtlRequest;
import com.security.project.domain.chat.dto.UpdateChatRequest;
import com.security.project.domain.chat.service.ChatService;
import com.security.project.domain.user.entity.User;

import jakarta.validation.Valid;

/**
 * Chat and membership endpoints. All require authentication; per-chat authorization (membership /
 * admin) is enforced in {@link ChatService}.
 */
@RestController
@RequestMapping("/api/chats")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ChatDto create(@AuthenticationPrincipal User user, @Valid @RequestBody CreateChatRequest request) {
        return chatService.createChat(user.getId(), request);
    }

    @GetMapping
    public List<ChatDto> list(@AuthenticationPrincipal User user) {
        return chatService.listChats(user.getId());
    }

    @GetMapping("/{chatId}")
    public ChatDto get(@AuthenticationPrincipal User user, @PathVariable UUID chatId) {
        return chatService.getChat(user.getId(), chatId);
    }

    @PutMapping("/{chatId}")
    public ChatDto update(@AuthenticationPrincipal User user,
                          @PathVariable UUID chatId,
                          @Valid @RequestBody UpdateChatRequest request) {
        return chatService.updateChat(user.getId(), chatId, request);
    }

    @DeleteMapping("/{chatId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal User user, @PathVariable UUID chatId) {
        chatService.deleteChat(user.getId(), chatId);
    }

    @PostMapping("/{chatId}/members")
    public ChatDto addMember(@AuthenticationPrincipal User user,
                             @PathVariable UUID chatId,
                             @Valid @RequestBody AddMemberRequest request) {
        return chatService.addMember(user.getId(), chatId, request);
    }

    @DeleteMapping("/{chatId}/members/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeMember(@AuthenticationPrincipal User user,
                             @PathVariable UUID chatId,
                             @PathVariable UUID userId) {
        chatService.removeMember(user.getId(), chatId, userId);
    }

    @PutMapping("/{chatId}/disappearing-ttl")
    public ChatDto setDisappearingTtl(@AuthenticationPrincipal User user,
                                      @PathVariable UUID chatId,
                                      @Valid @RequestBody DisappearingTtlRequest request) {
        return chatService.setDisappearingTtl(user.getId(), chatId, request);
    }
}
