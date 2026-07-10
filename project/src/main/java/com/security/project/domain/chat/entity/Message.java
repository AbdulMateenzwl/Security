package com.security.project.domain.chat.entity;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import com.security.project.domain.user.entity.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A single message in a chat.
 *
 * <p>The {@link #ciphertext} is Signal Protocol ciphertext stored as an opaque blob — the server
 * never decrypts it (blind relay). {@link #expiresAt}, when set, drives disappearing messages.</p>
 */
@Entity
@Table(name = "messages")
@Getter
@Setter
@NoArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "chat_id", nullable = false)
    private Chat chat;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    /** Signal Protocol ciphertext — stored and forwarded opaquely, never decrypted server-side. */
    @Column(name = "ciphertext", nullable = false)
    private byte[] ciphertext;

    /** Signal message type: 1 = WhisperMessage, 3 = PreKeyWhisperMessage. */
    @Column(name = "ciphertext_type", nullable = false)
    private int ciphertextType;

    /** Optional parent message this one replies to (nulled if the parent is deleted). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reply_to_message_id")
    private Message replyTo;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private MessageStatus status = MessageStatus.SENT;

    /** Absolute expiry for disappearing messages; {@code null} means the message never expires. */
    @Column(name = "expires_at")
    private Instant expiresAt;

    @CreatedDate
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;
}
