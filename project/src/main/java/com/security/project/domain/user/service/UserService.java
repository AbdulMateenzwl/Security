package com.security.project.domain.user.service;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.security.project.config.properties.AppSecurityProperties;
import com.security.project.domain.user.dto.FingerprintResponse;
import com.security.project.domain.user.dto.RegisterRequest;
import com.security.project.domain.user.entity.User;
import com.security.project.domain.user.repository.UserRepository;
import com.security.project.exception.DuplicateResourceException;
import com.security.project.exception.ResourceNotFoundException;

/**
 * User lifecycle and brute-force protection.
 *
 * <p>Lockout uses a hybrid model: the {@code account_locked} flag is the durable source of truth,
 * while a Redis key with a TTL provides automatic unlock after the configured duration. If the flag
 * is set but the Redis key has expired, the account is auto-unlocked on the next attempt.</p>
 */
@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final StringRedisTemplate redis;
    private final AppSecurityProperties securityProps;

    public UserService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       StringRedisTemplate redis,
                       AppSecurityProperties securityProps) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.redis = redis;
        this.securityProps = securityProps;
    }

    /** Register a new user. Pre-checks uniqueness; the DB unique constraints are the final guard. */
    @Transactional
    public User register(RegisterRequest req) {
        if (userRepository.existsByUsername(req.username())) {
            throw new DuplicateResourceException("Username already taken");
        }
        if (userRepository.existsByEmail(req.email())) {
            throw new DuplicateResourceException("Email already registered");
        }
        User user = new User();
        user.setUsername(req.username());
        user.setEmail(req.email());
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        user.setGithubUsername(req.githubUsername());
        User saved = userRepository.save(user);
        log.info("Registered new user id={}", saved.getId());   // id only — never username/email
        return saved;
    }

    @Transactional(readOnly = true)
    public User getById(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    /**
     * A user's identity-key fingerprint (safety number) for out-of-band MITM verification. The
     * fingerprint is {@code null} if the user has not yet published an identity key.
     */
    @Transactional(readOnly = true)
    public FingerprintResponse getFingerprint(UUID userId) {
        User user = getById(userId);
        return new FingerprintResponse(user.getId(), user.getUsername(), user.getIdentityKeyFingerprint());
    }

    /**
     * Whether the account is currently locked. Auto-unlocks (clears the flag and attempt counter)
     * if the durable flag is set but the Redis lockout window has already elapsed.
     */
    @Transactional
    public boolean isLockedOut(User user) {
        if (!user.isAccountLocked()) {
            return false;
        }
        Boolean stillLocked = redis.hasKey(lockoutKey(user.getId()));
        if (Boolean.TRUE.equals(stillLocked)) {
            return true;
        }
        // Lockout window elapsed — auto-unlock.
        user.setAccountLocked(false);
        user.setFailedLoginAttempts(0);
        userRepository.save(user);
        return false;
    }

    /** Record a failed login; lock the account once the threshold is reached. */
    @Transactional
    public void recordFailedAttempt(User user) {
        int attempts = user.getFailedLoginAttempts() + 1;
        user.setFailedLoginAttempts(attempts);
        if (attempts >= securityProps.maxFailedLoginAttempts()) {
            user.setAccountLocked(true);
            redis.opsForValue().set(
                    lockoutKey(user.getId()),
                    Instant.now().toString(),
                    Duration.ofMinutes(securityProps.accountLockDurationMinutes()));
            log.warn("Account locked after {} failed attempts, user id={}", attempts, user.getId());
        }
        userRepository.save(user);
    }

    /** Record a successful login: reset the failure counter and stamp last-login time. */
    @Transactional
    public void recordSuccessfulLogin(User user) {
        user.setFailedLoginAttempts(0);
        user.setLastLoginAt(Instant.now());
        userRepository.save(user);
    }

    public boolean passwordMatches(String rawPassword, User user) {
        return passwordEncoder.matches(rawPassword, user.getPasswordHash());
    }

    private String lockoutKey(UUID userId) {
        return "lockout:" + userId;
    }
}
