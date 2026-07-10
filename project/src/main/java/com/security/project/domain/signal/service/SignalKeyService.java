package com.security.project.domain.signal.service;

import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.security.project.domain.signal.dto.IdentityKeyUploadRequest;
import com.security.project.domain.signal.dto.PreKeyBundleDto;
import com.security.project.domain.signal.dto.PreKeyDto;
import com.security.project.domain.signal.dto.PreKeyUploadRequest;
import com.security.project.domain.signal.dto.SignedPreKeyDto;
import com.security.project.domain.signal.entity.IdentityKey;
import com.security.project.domain.signal.entity.OneTimePreKey;
import com.security.project.domain.signal.entity.SignedPreKey;
import com.security.project.domain.signal.repository.IdentityKeyRepository;
import com.security.project.domain.signal.repository.OneTimePreKeyRepository;
import com.security.project.domain.signal.repository.SignedPreKeyRepository;
import com.security.project.domain.user.entity.User;
import com.security.project.domain.user.repository.UserRepository;
import com.security.project.exception.ResourceNotFoundException;

/**
 * Storage and distribution of Signal public key material.
 *
 * <p>The server is a blind relay: it accepts only public keys, stores them as opaque blobs, and
 * serves pre-key bundles so peers can start end-to-end encrypted sessions. It never sees private
 * keys, never decrypts, and never verifies signatures — the fetching client verifies the signed
 * pre-key against the identity key itself.</p>
 */
@Service
public class SignalKeyService {

    private static final Logger log = LoggerFactory.getLogger(SignalKeyService.class);

    private final IdentityKeyRepository identityKeyRepository;
    private final SignedPreKeyRepository signedPreKeyRepository;
    private final OneTimePreKeyRepository oneTimePreKeyRepository;
    private final UserRepository userRepository;

    public SignalKeyService(IdentityKeyRepository identityKeyRepository,
                            SignedPreKeyRepository signedPreKeyRepository,
                            OneTimePreKeyRepository oneTimePreKeyRepository,
                            UserRepository userRepository) {
        this.identityKeyRepository = identityKeyRepository;
        this.signedPreKeyRepository = signedPreKeyRepository;
        this.oneTimePreKeyRepository = oneTimePreKeyRepository;
        this.userRepository = userRepository;
    }

    /**
     * Store (or replace) a user's public identity key, and cache its hex fingerprint on the user for
     * out-of-band MITM ("safety number") verification. Private keys are never accepted.
     */
    @Transactional
    public void uploadIdentityKey(UUID userId, IdentityKeyUploadRequest request) {
        User user = requireUser(userId);

        IdentityKey identityKey = identityKeyRepository.findByUserId(userId)
                .orElseGet(() -> {
                    IdentityKey k = new IdentityKey();
                    k.setUser(user);
                    return k;
                });
        identityKey.setPublicKey(request.publicKey());
        identityKey.setRegistrationId(request.registrationId());
        identityKeyRepository.save(identityKey);

        user.setIdentityKeyFingerprint(fingerprint(request.publicKey()));
        userRepository.save(user);

        log.info("Stored identity key for user id={}", userId);
    }

    /**
     * Batch-store one-time pre-keys, skipping any key ids already present so re-uploads are safe.
     * Optionally rotates the signed pre-key in the same call.
     */
    @Transactional
    public void uploadPreKeys(UUID userId, PreKeyUploadRequest request) {
        User user = requireUser(userId);

        if (request.signedPreKey() != null) {
            storeSignedPreKey(user, request.signedPreKey());
        }

        Set<Integer> existing = oneTimePreKeyRepository.findKeyIdsByUserId(userId);
        List<OneTimePreKey> toSave = request.preKeys().stream()
                .filter(dto -> !existing.contains(dto.keyId()))
                .map(dto -> {
                    OneTimePreKey k = new OneTimePreKey();
                    k.setUser(user);
                    k.setKeyId(dto.keyId());
                    k.setPublicKey(dto.publicKey());
                    return k;
                })
                .toList();
        oneTimePreKeyRepository.saveAll(toSave);

        log.info("Stored {} new one-time pre-keys for user id={} ({} duplicates skipped)",
                toSave.size(), userId, request.preKeys().size() - toSave.size());
    }

    /** Rotate the signed pre-key. The newest is the one served in future bundles. */
    @Transactional
    public void rotateSignedPreKey(UUID userId, SignedPreKeyDto dto) {
        User user = requireUser(userId);
        storeSignedPreKey(user, dto);
        log.info("Rotated signed pre-key (keyId={}) for user id={}", dto.keyId(), userId);
    }

    /**
     * Build a pre-key bundle for {@code targetUserId}, atomically consuming one of their one-time
     * pre-keys. Returns 404-mapped {@link ResourceNotFoundException} if the user has never uploaded
     * an identity key or signed pre-key; a missing OTPK is allowed (returned as {@code null}).
     */
    @Transactional
    public PreKeyBundleDto getPreKeyBundle(UUID targetUserId) {
        IdentityKey identityKey = identityKeyRepository.findByUserId(targetUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User has no published keys"));
        SignedPreKey signedPreKey = signedPreKeyRepository.findFirstByUserIdOrderByCreatedAtDesc(targetUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User has no signed pre-key"));

        PreKeyDto oneTimePreKey = consumeOneTimePreKey(targetUserId).map(PreKeyDto::from).orElse(null);

        return new PreKeyBundleDto(
                targetUserId,
                identityKey.getRegistrationId(),
                identityKey.getPublicKey(),
                SignedPreKeyDto.from(signedPreKey),
                oneTimePreKey);
    }

    /** Remaining unconsumed one-time pre-keys for a user. */
    @Transactional(readOnly = true)
    public long getRemainingPreKeyCount(UUID userId) {
        return oneTimePreKeyRepository.countByUserIdAndConsumedFalse(userId);
    }

    /**
     * CRITICAL: claim exactly one unconsumed one-time pre-key. Uses {@code FOR UPDATE SKIP LOCKED}
     * so two concurrent bundle fetches never hand out the same key. Returns empty when the user has
     * none left.
     */
    private Optional<OneTimePreKey> consumeOneTimePreKey(UUID userId) {
        List<OneTimePreKey> candidates =
                oneTimePreKeyRepository.findConsumableForUpdate(userId, PageRequest.of(0, 1));
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        OneTimePreKey key = candidates.getFirst();
        key.consume();
        return Optional.of(key);
    }

    private void storeSignedPreKey(User user, SignedPreKeyDto dto) {
        if (signedPreKeyRepository.existsByUserIdAndKeyId(user.getId(), dto.keyId())) {
            return; // idempotent — same key id already stored
        }
        SignedPreKey entity = new SignedPreKey();
        entity.setUser(user);
        entity.setKeyId(dto.keyId());
        entity.setPublicKey(dto.publicKey());
        entity.setSignature(dto.signature());
        signedPreKeyRepository.save(entity);
    }

    private User requireUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    /** Lowercase hex of the public identity key — the value compared out-of-band as a safety number. */
    private static String fingerprint(byte[] publicKey) {
        return HexFormat.of().formatHex(publicKey);
    }
}
