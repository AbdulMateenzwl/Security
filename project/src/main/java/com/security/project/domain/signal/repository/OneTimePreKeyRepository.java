package com.security.project.domain.signal.repository;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import com.security.project.domain.signal.entity.OneTimePreKey;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;

public interface OneTimePreKeyRepository extends JpaRepository<OneTimePreKey, UUID> {

    /** How many unconsumed OTPKs a user still has — clients top up when this runs low. */
    long countByUserIdAndConsumedFalse(UUID userId);

    /** Key ids already stored for a user — used to skip duplicates on batch upload. */
    @Query("SELECT k.keyId FROM OneTimePreKey k WHERE k.user.id = :userId")
    Set<Integer> findKeyIdsByUserId(@Param("userId") UUID userId);

    /**
     * Fetch unconsumed OTPKs for a user with a pessimistic write lock and {@code SKIP LOCKED}, so a
     * key already being claimed by a concurrent bundle fetch is skipped rather than blocked on.
     *
     * <p>The {@code jakarta.persistence.lock.timeout = -2} hint is Hibernate's marker for
     * {@code SKIP LOCKED}. Call with {@code PageRequest.of(0, 1)} to claim a single key.</p>
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("SELECT k FROM OneTimePreKey k WHERE k.user.id = :userId AND k.consumed = false ORDER BY k.keyId ASC")
    List<OneTimePreKey> findConsumableForUpdate(@Param("userId") UUID userId, Pageable pageable);
}
