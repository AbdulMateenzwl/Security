package com.security.project.domain.signal.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.security.project.domain.signal.entity.SignedPreKey;

public interface SignedPreKeyRepository extends JpaRepository<SignedPreKey, UUID> {

    /** The most recently uploaded signed pre-key for a user — the one served in a bundle. */
    Optional<SignedPreKey> findFirstByUserIdOrderByCreatedAtDesc(UUID userId);

    boolean existsByUserIdAndKeyId(UUID userId, int keyId);
}
