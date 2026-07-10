package com.security.project.domain.chat.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.security.project.domain.chat.dto.CreateChatRequest;
import com.security.project.domain.chat.entity.Chat;
import com.security.project.domain.chat.entity.ChatType;
import com.security.project.domain.chat.repository.ChatMemberRepository;
import com.security.project.domain.chat.repository.ChatRepository;
import com.security.project.domain.user.entity.User;
import com.security.project.domain.user.repository.UserRepository;
import com.security.project.exception.BadRequestException;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock ChatRepository chatRepository;
    @Mock ChatMemberRepository chatMemberRepository;
    @Mock ChatAccessGuard chatAccessGuard;
    @Mock UserRepository userRepository;

    @InjectMocks ChatService service;

    private final UUID creator = UUID.randomUUID();

    private void stubCreator() {
        User user = new User();
        user.setId(creator);
        when(userRepository.getByIdOrThrow(creator)).thenReturn(user);
    }

    @Test
    void directChat_requiresExactlyOneOtherParticipant() {
        stubCreator();
        CreateChatRequest req = new CreateChatRequest(ChatType.DIRECT, null, List.of());

        assertThatThrownBy(() -> service.createChat(creator, req))
                .isInstanceOf(BadRequestException.class);
        verify(chatRepository, never()).save(any(Chat.class));
    }

    @Test
    void groupChat_requiresName() {
        stubCreator();
        CreateChatRequest req = new CreateChatRequest(ChatType.GROUP, "  ", List.of());

        assertThatThrownBy(() -> service.createChat(creator, req))
                .isInstanceOf(BadRequestException.class);
        verify(chatRepository, never()).save(any(Chat.class));
    }
}
