package com.security.project.domain.chat.dto;

import com.security.project.domain.chat.entity.ReceiptType;

import jakarta.validation.constraints.NotNull;

/**
 * A recipient's receipt for a message: mark it {@code DELIVERED} or {@code READ}.
 */
public record UpdateMessageStatusRequest(
        @NotNull
        ReceiptType status
) {
}
