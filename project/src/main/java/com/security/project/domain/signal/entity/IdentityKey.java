package com.security.project.domain.signal.entity;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import com.security.project.domain.user.entity.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A user's long-lived Signal identity key — one per user.
 *
 * <p>Only the public half is ever stored: the private identity key never leaves the client. The
 * {@code registrationId} is part of the Signal address and is returned alongside the key in a
 * pre-key bundle so a peer can build the correct session.</p>
 */
@Entity
@Table(name = "identity_keys")
@Getter
@Setter
@NoArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class IdentityKey {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    /** Public identity key bytes (DJB / Curve25519). The private half is never sent to the server. */
    @Column(name = "public_key", nullable = false)
    private byte[] publicKey;

    @Column(name = "registration_id", nullable = false)
    private int registrationId;

    @CreatedDate
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;
}
