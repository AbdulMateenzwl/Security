package com.security.project.domain.user.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.security.project.domain.user.entity.User;
import com.security.project.exception.ResourceNotFoundException;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByUsername(String username);

    /**
     * Case-insensitive substring search over username <em>and</em> email, excluding one user (the
     * caller). Used to find people to start a chat with. {@code term} must already be lower-cased and
     * wrapped in {@code %…%}; bounded by {@code limit} so a broad query can't return the whole table.
     */
    @Query("""
            select u from User u
            where u.id <> :excludeId
              and (lower(u.username) like :term escape '!' or lower(u.email) like :term escape '!')
            order by u.username asc
            """)
    List<User> searchByUsernameOrEmail(@Param("term") String term,
                                       @Param("excludeId") UUID excludeId,
                                       Limit limit);

    Optional<User> findByEmail(String email);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    /** Fetch a user by id or throw a 404-mapped {@link ResourceNotFoundException}. */
    default User getByIdOrThrow(UUID id) {
        return findById(id).orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }
}
