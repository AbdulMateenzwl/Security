package com.security.project.domain.signal.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.security.project.domain.signal.entity.IdentityKey;

public interface IdentityKeyRepository extends JpaRepository<IdentityKey, UUID> {

    Optional<IdentityKey> findByUserId(UUID userId);
}
