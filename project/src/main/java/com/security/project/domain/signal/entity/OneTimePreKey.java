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
 * A one-time pre-key (OTPK): consumed exactly once when a peer initiates a Signal session.
 *
 * <p>Consumption must be atomic — see {@code SignalKeyService#consumeOneTimePreKey}, which selects
 * an unconsumed key {@code FOR UPDATE SKIP LOCKED} so two concurrent initiations can never claim the
 * same key. Consumed keys are kept (marked, not deleted) for auditing.</p>
 */
@Entity
@Table(name = "one_time_pre_keys")
@Getter
@Setter
@NoArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class OneTimePreKey {

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

    @Column(name = "consumed", nullable = false)
    private boolean consumed = false;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @CreatedDate
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    /** Mark this key as spent. Called under a pessimistic write lock. */
    public void consume() {
        this.consumed = true;
        this.consumedAt = Instant.now();
    }
}
