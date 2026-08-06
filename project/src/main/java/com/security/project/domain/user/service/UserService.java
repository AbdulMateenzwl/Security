package com.security.project.domain.user.service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.data.domain.Limit;

import com.security.project.config.properties.AppSecurityProperties;
import com.security.project.domain.user.dto.FingerprintResponse;
import com.security.project.domain.user.dto.RegisterRequest;
import com.security.project.domain.user.dto.UserSummaryDto;
import com.security.project.domain.user.entity.User;
import com.security.project.domain.user.repository.UserRepository;
import com.security.project.exception.BadRequestException;
import com.security.project.exception.DuplicateResourceException;

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

    /** At least 8 chars including a letter and a digit — enforced server-side (client checks are bypassable). */
    private static final Pattern PASSWORD_POLICY = Pattern.compile("^(?=.*[A-Za-z])(?=.*\\d).{8,128}$");
    /** A few of the most-guessed passwords, rejected outright regardless of the pattern above. */
    private static final Set<String> COMMON_PASSWORDS = Set.of(
            "password", "password1", "12345678", "123456789", "1234567890",
            "qwerty123", "password123", "11111111", "abcd1234", "iloveyou1");

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final StringRedisTemplate redis;
    private final AppSecurityProperties securityProps;
    /** A valid BCrypt hash used only to spend the same time on a login for a non-existent user. */
    private final String dummyHash;

    public UserService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       StringRedisTemplate redis,
                       AppSecurityProperties securityProps) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.redis = redis;
        this.securityProps = securityProps;
        this.dummyHash = passwordEncoder.encode("timing-equalizer-not-a-real-password");
    }

    /** Register a new user. Enforces the password policy, then pre-checks uniqueness (DB constraints
     *  are the final guard). */
    @Transactional
    public User register(RegisterRequest req) {
        validatePasswordStrength(req.password());
        // Generic message for both checks so registration can't be used to enumerate which usernames
        // or emails already exist (the DB unique constraints are the final, race-safe guard).
        if (userRepository.existsByUsername(req.username()) || userRepository.existsByEmail(req.email())) {
            throw new DuplicateResourceException("That username or email is already in use");
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
        return userRepository.getByIdOrThrow(id);
    }

    /** Shortest query accepted — below this the caller is fishing, not looking for someone. */
    public static final int MIN_QUERY_LENGTH = 3;

    /** Hard cap on results, so a broad term reveals as little of the user table as possible. */
    private static final int MAX_RESULTS = 3;

    /**
     * Search users by username or email (case-insensitive substring) to start a chat, excluding the
     * caller. Queries shorter than {@link #MIN_QUERY_LENGTH} characters return nothing, and results
     * are capped at {@link #MAX_RESULTS} — together these keep the endpoint from being walked to
     * enumerate the user table (see also the per-user {@code user-search} rate limit). The term is
     * escaped so {@code %} and {@code _} typed by the user are treated literally.
     */
    @Transactional(readOnly = true)
    public List<UserSummaryDto> search(String query, UUID currentUserId) {
        String trimmed = query == null ? "" : query.trim();
        if (trimmed.length() < MIN_QUERY_LENGTH) {
            return List.of();
        }
        String term = "%" + trimmed.toLowerCase().replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        return userRepository
                .searchByUsernameOrEmail(term, currentUserId, Limit.of(MAX_RESULTS))
                .stream()
                .map(UserSummaryDto::from)
                .toList();
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

    /**
     * Spend roughly the same time hashing as a real password check would, without a user. Called on a
     * login for a non-existent username so response time doesn't reveal whether the account exists.
     */
    public void wastePasswordCompare(String rawPassword) {
        passwordEncoder.matches(rawPassword == null ? "" : rawPassword, dummyHash);
    }

    /** Reject weak passwords server-side (length + a letter + a digit, and not a common password). */
    private void validatePasswordStrength(String password) {
        if (password == null || !PASSWORD_POLICY.matcher(password).matches()
                || COMMON_PASSWORDS.contains(password.toLowerCase())) {
            throw new BadRequestException(
                    "Password must be 8–128 characters and include at least one letter and one number");
        }
    }

    private String lockoutKey(UUID userId) {
        return "lockout:" + userId;
    }
}
