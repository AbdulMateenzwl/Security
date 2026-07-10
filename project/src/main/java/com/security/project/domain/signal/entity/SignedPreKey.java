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
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A signed pre-key: a medium-lived public key signed by the user's identity key.
 *
 * <p>Rotated periodically. The newest one is included in a pre-key bundle. The server stores the
 * {@code signature} but never verifies it — the fetching client verifies it against the identity
 * key to detect tampering (the blind-relay principle).</p>
 */
@Entity
@Table(name = "signed_pre_keys")
@Getter
@Setter
@NoArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class SignedPreKey {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** Client-assigned key id, unique per user. */
    @Column(name = "key_id", nullable = false)
    private int keyId;

    @Column(name = "public_key", nullable = false)
    private byte[] publicKey;

    /** Signature over the public key, produced by the user's identity key. Client-verified. */
    @Column(name = "signature", nullable = false)
    private byte[] signature;

    @CreatedDate
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;
}
