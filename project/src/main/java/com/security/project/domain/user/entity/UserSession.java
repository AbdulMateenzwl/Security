package com.security.project.domain.user.entity;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

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
 * A single authenticated login (device) session.
 *
 * <p>Every JWT issued for a login carries this session's id as its {@code sid} claim. Validating a
 * request re-checks the session on every call, so revoking a session ({@link #revoked}) invalidates
 * its access token immediately — well before the token's own 15-minute expiry.</p>
 */
@Entity
@Table(name = "user_sessions")
@Getter
@Setter
@NoArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class UserSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** Session identifier embedded in the JWT (the {@code sid}/jti). Unique, used for revocation lookups. */
    @Column(name = "token_jti", unique = true, nullable = false, length = 36)
    private String tokenJti;

    @Column(name = "device_info", length = 255)
    private String deviceInfo;

    /** Client IP at login time (stored as text for portability). */
    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @CreatedDate
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    /** Absolute expiry — mirrors the refresh-token lifetime. */
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked", nullable = false)
    private boolean revoked = false;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    /** True when the session may still mint/validate tokens. */
    public boolean isActive() {
        return !revoked && expiresAt.isAfter(Instant.now());
    }
}
