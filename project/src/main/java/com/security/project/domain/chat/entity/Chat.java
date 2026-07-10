package com.security.project.domain.chat.entity;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
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
 * A conversation — either a 1:1 {@link ChatType#DIRECT} chat or a named {@link ChatType#GROUP} chat.
 *
 * <p>Membership and roles live in {@link ChatMember}. Authorization (who may read, post, or
 * administer) is enforced in the service layer, never inferred from this entity alone.</p>
 */
@Entity
@Table(name = "chats")
@Getter
@Setter
@NoArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class Chat {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 10)
    private ChatType type;

    /** Display name — set for GROUP chats, {@code null} for DIRECT chats. */
    @Column(name = "name", length = 100)
    private String name;

    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    /** Disappearing-message timer in seconds, or {@code null} when disabled. */
    @Column(name = "disappearing_message_ttl")
    private Integer disappearingMessageTtl;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @CreatedDate
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
