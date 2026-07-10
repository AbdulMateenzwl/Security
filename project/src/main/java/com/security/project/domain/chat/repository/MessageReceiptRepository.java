package com.security.project.domain.chat.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.security.project.domain.chat.entity.MessageReceipt;
import com.security.project.domain.chat.entity.ReceiptType;

public interface MessageReceiptRepository extends JpaRepository<MessageReceipt, UUID> {

    boolean existsByMessageIdAndUserIdAndType(UUID messageId, UUID userId, ReceiptType type);
}
