package com.security.project.domain.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import com.security.project.domain.chat.entity.ChatMember;
import com.security.project.domain.chat.entity.MemberRole;
import com.security.project.domain.chat.repository.ChatMemberRepository;

@ExtendWith(MockitoExtension.class)
class ChatAccessGuardTest {

    @Mock
    ChatMemberRepository chatMemberRepository;

    @InjectMocks
    ChatAccessGuard guard;

    private final UUID chatId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @Test
    void requireMember_returnsMembership_whenPresent() {
        ChatMember member = new ChatMember();
        when(chatMemberRepository.findByChatIdAndUserId(chatId, userId)).thenReturn(Optional.of(member));

        assertThat(guard.requireMember(chatId, userId)).isSameAs(member);
    }

    @Test
    void requireMember_throwsForbidden_whenNotAMember() {
        when(chatMemberRepository.findByChatIdAndUserId(chatId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> guard.requireMember(chatId, userId))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void requireAdmin_passes_forAdmin() {
        ChatMember admin = new ChatMember();
        admin.setRole(MemberRole.ADMIN);
        when(chatMemberRepository.findByChatIdAndUserId(chatId, userId)).thenReturn(Optional.of(admin));

        assertThatCode(() -> guard.requireAdmin(chatId, userId)).doesNotThrowAnyException();
    }

    @Test
    void requireAdmin_throwsForbidden_forNonAdminMember() {
        ChatMember member = new ChatMember();
        member.setRole(MemberRole.MEMBER);
        when(chatMemberRepository.findByChatIdAndUserId(chatId, userId)).thenReturn(Optional.of(member));

        assertThatThrownBy(() -> guard.requireAdmin(chatId, userId))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void isMember_delegatesToRepository() {
        when(chatMemberRepository.existsByChatIdAndUserId(chatId, userId)).thenReturn(true);

        assertThat(guard.isMember(chatId, userId)).isTrue();
    }
}
