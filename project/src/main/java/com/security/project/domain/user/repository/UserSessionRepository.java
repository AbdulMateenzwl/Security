package com.security.project.domain.user.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.security.project.domain.user.entity.UserSession;

public interface UserSessionRepository extends JpaRepository<UserSession, UUID> {

    Optional<UserSession> findByTokenJti(String tokenJti);

    /** Active (non-revoked, unexpired) sessions for a user, newest first. */
    List<UserSession> findByUserIdAndRevokedFalseAndExpiresAtAfterOrderByCreatedAtDesc(
            UUID userId, Instant now);

    /** Revoke every active session for a user ("logout everywhere"). */
    @Modifying
    @Query("""
            UPDATE UserSession s
               SET s.revoked = true, s.revokedAt = :now
             WHERE s.user.id = :userId AND s.revoked = false
            """)
    int revokeAllForUser(@Param("userId") UUID userId, @Param("now") Instant now);
}
