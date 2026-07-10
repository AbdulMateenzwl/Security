package com.security.project.domain.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import com.security.project.domain.chat.entity.Chat;
import com.security.project.domain.chat.entity.Message;
import com.security.project.domain.chat.entity.MessageStatus;
import com.security.project.domain.chat.entity.ReceiptType;
import com.security.project.domain.chat.repository.MessageReceiptRepository;
import com.security.project.domain.chat.repository.MessageRepository;
import com.security.project.domain.user.entity.User;
import com.security.project.domain.user.repository.UserRepository;
import com.security.project.exception.BadRequestException;

@ExtendWith(MockitoExtension.class)
class MessageServiceTest {

    @Mock MessageRepository messageRepository;
    @Mock MessageReceiptRepository receiptRepository;
    @Mock com.security.project.domain.chat.repository.ChatRepository chatRepository;
    @Mock ChatAccessGuard chatAccessGuard;
    @Mock UserRepository userRepository;

    @InjectMocks MessageService service;

    private Message messageFrom(UUID senderId) {
        Chat chat = new Chat();
        chat.setId(UUID.randomUUID());
        User sender = new User();
        sender.setId(senderId);
        Message m = new Message();
        m.setId(UUID.randomUUID());
        m.setChat(chat);
        m.setSender(sender);
        m.setStatus(MessageStatus.SENT);
        return m;
    }

    @Test
    void updateStatus_advancesStatus_andNeverMovesBackward() {
        UUID reader = UUID.randomUUID();
        Message m = messageFrom(UUID.randomUUID());   // sender != reader
        when(messageRepository.findById(m.getId())).thenReturn(Optional.of(m));
        when(userRepository.getReferenceById(reader)).thenReturn(new User());

        service.updateStatus(reader, m.getId(), ReceiptType.READ);
        assertThat(m.getStatus()).isEqualTo(MessageStatus.READ);

        // A later DELIVERED must not regress a READ message.
        service.updateStatus(reader, m.getId(), ReceiptType.DELIVERED);
        assertThat(m.getStatus()).isEqualTo(MessageStatus.READ);
    }

    @Test
    void updateStatus_rejectsSenderReceiptingOwnMessage() {
        UUID sender = UUID.randomUUID();
        Message m = messageFrom(sender);
        when(messageRepository.findById(m.getId())).thenReturn(Optional.of(m));

        assertThatThrownBy(() -> service.updateStatus(sender, m.getId(), ReceiptType.READ))
                .isInstanceOf(BadRequestException.class);
        verify(receiptRepository, never()).save(any());
    }

    @Test
    void deleteMessage_allowsOnlyTheSender() {
        UUID sender = UUID.randomUUID();
        Message m = messageFrom(sender);
        when(messageRepository.findById(m.getId())).thenReturn(Optional.of(m));

        assertThatThrownBy(() -> service.deleteMessage(UUID.randomUUID(), m.getId()))
                .isInstanceOf(AccessDeniedException.class);
        verify(messageRepository, never()).delete(any());

        service.deleteMessage(sender, m.getId());
        verify(messageRepository).delete(m);
    }
}
