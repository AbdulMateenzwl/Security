package com.security.project.domain.chat.entity;

/**
 * Delivery lifecycle of a message. Ordinal order is meaningful: a message only ever advances
 * {@code SENT → DELIVERED → READ}, never backwards.
 */
public enum MessageStatus {
    SENT,
    DELIVERED,
    READ
}
