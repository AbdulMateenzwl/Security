package com.security.project.domain.user.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.security.project.domain.user.entity.User;
import com.security.project.exception.ResourceNotFoundException;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    /** Fetch a user by id or throw a 404-mapped {@link ResourceNotFoundException}. */
    default User getByIdOrThrow(UUID id) {
        return findById(id).orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }
}
