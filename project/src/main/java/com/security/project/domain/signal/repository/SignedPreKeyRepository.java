package com.security.project.domain.signal.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.security.project.domain.signal.entity.SignedPreKey;

public interface SignedPreKeyRepository extends JpaRepository<SignedPreKey, UUID> {

    /** The most recently uploaded signed pre-key for a user — the one served in a bundle. */
    Optional<SignedPreKey> findFirstByUserIdOrderByCreatedAtDesc(UUID userId);

    boolean existsByUserIdAndKeyId(UUID userId, int keyId);

    /** Drop all of a user's signed pre-keys — used when their identity key rotates. */
    @Modifying
    @Query("DELETE FROM SignedPreKey k WHERE k.user.id = :userId")
    void deleteAllByUserId(@Param("userId") UUID userId);
}
