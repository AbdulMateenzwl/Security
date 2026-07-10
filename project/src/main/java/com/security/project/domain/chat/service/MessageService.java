package com.security.project.domain.chat.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.security.project.domain.chat.dto.MessageDto;
import com.security.project.domain.chat.dto.SendMessageRequest;
import com.security.project.domain.chat.entity.Chat;
import com.security.project.domain.chat.entity.Message;
import com.security.project.domain.chat.entity.MessageReceipt;
import com.security.project.domain.chat.entity.MessageStatus;
import com.security.project.domain.chat.entity.ReceiptType;
import com.security.project.domain.chat.repository.ChatRepository;
import com.security.project.domain.chat.repository.MessageReceiptRepository;
import com.security.project.domain.chat.repository.MessageRepository;
import com.security.project.domain.user.entity.User;
import com.security.project.domain.user.repository.UserRepository;
import com.security.project.exception.BadRequestException;
import com.security.project.exception.ResourceNotFoundException;

/**
 * Sending, reading and managing messages.
 *
 * <p>The server is a blind relay: {@code ciphertext} is stored and forwarded verbatim and never
 * decrypted or logged. Authorization is enforced here — only chat members may read or send, and only
 * the sender may delete. Membership failures throw {@link AccessDeniedException} (403) without
 * revealing whether the chat or message exists.</p>
 */
@Service
public class MessageService {

    private static final Logger log = LoggerFactory.getLogger(MessageService.class);

    private static final int MAX_PAGE_SIZE = 100;

    private final MessageRepository messageRepository;
    private final MessageReceiptRepository receiptRepository;
    private final ChatRepository chatRepository;
    private final ChatAccessGuard chatAccessGuard;
    private final UserRepository userRepository;

    public MessageService(MessageRepository messageRepository,
                          MessageReceiptRepository receiptRepository,
                          ChatRepository chatRepository,
                          ChatAccessGuard chatAccessGuard,
                          UserRepository userRepository) {
        this.messageRepository = messageRepository;
        this.receiptRepository = receiptRepository;
        this.chatRepository = chatRepository;
        this.chatAccessGuard = chatAccessGuard;
        this.userRepository = userRepository;
    }

    /** Send an encrypted message to a chat the caller belongs to. */
    @Transactional
    public MessageDto sendMessage(UUID senderId, UUID chatId, SendMessageRequest req) {
        chatAccessGuard.requireMember(chatId, senderId);
        Chat chat = chatRepository.findById(chatId)
                .orElseThrow(() -> new ResourceNotFoundException("Chat not found"));

        Message message = new Message();
        message.setChat(chat);
        message.setSender(userRepository.getReferenceById(senderId));
        message.setCiphertext(req.ciphertext());
        message.setCiphertextType(req.ciphertextType());
        message.setStatus(MessageStatus.SENT);

        if (req.replyToMessageId() != null) {
            Message parent = messageRepository.findById(req.replyToMessageId())
                    .orElseThrow(() -> new BadRequestException("Replied-to message does not exist"));
            if (!parent.getChat().getId().equals(chatId)) {
                throw new BadRequestException("Cannot reply to a message from another chat");
            }
            message.setReplyTo(parent);
        }

        Integer ttl = chat.getDisappearingMessageTtl();
        if (ttl != null) {
            message.setExpiresAt(Instant.now().plus(ttl, ChronoUnit.SECONDS));
        }

        Message saved = messageRepository.save(message);
        log.info("Message id={} sent to chat id={} by user id={}", saved.getId(), chatId, senderId);  // never log ciphertext
        return MessageDto.from(saved);
    }

    /**
     * Newest-first page of a chat's history. {@code before} is the id of the oldest message the
     * caller already has (the cursor); {@code null} fetches the first page.
     */
    @Transactional(readOnly = true)
    public List<MessageDto> getMessages(UUID userId, UUID chatId, UUID before, int limit) {
        chatAccessGuard.requireMember(chatId, userId);
        int size = Math.clamp(limit, 1, MAX_PAGE_SIZE);
        Instant now = Instant.now();

        List<Message> page;
        if (before == null) {
            page = messageRepository.findFirstPage(chatId, now, PageRequest.of(0, size));
        } else {
            Message cursor = messageRepository.findById(before)
                    .filter(m -> m.getChat().getId().equals(chatId))
                    .orElseThrow(() -> new BadRequestException("Invalid pagination cursor"));
            page = messageRepository.findPageBefore(
                    chatId, cursor.getCreatedAt(), cursor.getId(), now, PageRequest.of(0, size));
        }
        return page.stream().map(MessageDto::from).toList();
    }

    /**
     * Record a delivery/read receipt for a message and advance its rollup status. A message only
     * moves forward ({@code SENT → DELIVERED → READ}). Recipients only — the sender cannot receipt
     * their own message.
     */
    @Transactional
    public void updateStatus(UUID userId, UUID messageId, ReceiptType receiptType) {
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new ResourceNotFoundException("Message not found"));
        chatAccessGuard.requireMember(message.getChat().getId(), userId);
        if (message.getSender().getId().equals(userId)) {
            throw new BadRequestException("Cannot mark your own message as delivered or read");
        }

        if (!receiptRepository.existsByMessageIdAndUserIdAndType(messageId, userId, receiptType)) {
            MessageReceipt receipt = new MessageReceipt();
            receipt.setMessage(message);
            receipt.setUser(userRepository.getReferenceById(userId));
            receipt.setType(receiptType);
            receiptRepository.save(receipt);
        }

        MessageStatus newStatus = MessageStatus.valueOf(receiptType.name());
        if (newStatus.ordinal() > message.getStatus().ordinal()) {
            message.setStatus(newStatus);
        }
        log.info("User id={} marked message id={} as {}", userId, messageId, receiptType);
    }

    /** Delete a message — sender only. Cascades to its receipts. */
    @Transactional
    public void deleteMessage(UUID userId, UUID messageId) {
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new ResourceNotFoundException("Message not found"));
        if (!message.getSender().getId().equals(userId)) {
            throw new AccessDeniedException("Only the sender may delete this message");
        }
        messageRepository.delete(message);
        log.info("Message id={} deleted by sender id={}", messageId, userId);
    }
}
