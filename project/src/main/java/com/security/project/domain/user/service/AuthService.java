package com.security.project.domain.user.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.security.project.domain.user.dto.AuthResponse;
import com.security.project.domain.user.dto.LoginRequest;
import com.security.project.domain.user.dto.RefreshRequest;
import com.security.project.domain.user.dto.RegisterRequest;
import com.security.project.domain.user.dto.SessionDto;
import com.security.project.domain.user.dto.UserDto;
import com.security.project.domain.user.entity.User;
import com.security.project.domain.user.entity.UserSession;
import com.security.project.domain.user.repository.UserRepository;
import com.security.project.domain.user.repository.UserSessionRepository;
import com.security.project.exception.InvalidCredentialsException;
import com.security.project.exception.InvalidTokenException;
import com.security.project.exception.ResourceNotFoundException;
import com.security.project.config.properties.JwtProperties;
import com.security.project.security.jwt.JwtTokenProvider;

import io.jsonwebtoken.Claims;

/**
 * Orchestrates authentication: registration, login (with lockout), token refresh, logout, and
 * session management. Token/session issuance is centralised in {@link #issueTokens}.
 */
@Service
public class AuthService {

    private final UserService userService;
    private final UserRepository userRepository;
    private final UserSessionRepository sessionRepository;
    private final JwtTokenProvider tokenProvider;
    private final JwtProperties jwtProps;

    public AuthService(UserService userService,
                       UserRepository userRepository,
                       UserSessionRepository sessionRepository,
                       JwtTokenProvider tokenProvider,
                       JwtProperties jwtProps) {
        this.userService = userService;
        this.userRepository = userRepository;
        this.sessionRepository = sessionRepository;
        this.tokenProvider = tokenProvider;
        this.jwtProps = jwtProps;
    }

    @Transactional
    public AuthResponse register(RegisterRequest req, String ipAddress, String deviceInfo) {
        User user = userService.register(req);
        return issueTokens(user, ipAddress, deviceInfo);
    }

    @Transactional
    public AuthResponse login(LoginRequest req, String ipAddress, String deviceInfo) {
        // Generic failure for both "no such user" and "wrong password" — prevents account enumeration.
        User user = userRepository.findByUsername(req.username())
                .orElseThrow(() -> new InvalidCredentialsException("Invalid username or password"));

        if (userService.isLockedOut(user)) {
            throw new com.security.project.exception.AccountLockedException(
                    "Account is temporarily locked due to too many failed login attempts");
        }

        if (!userService.passwordMatches(req.password(), user)) {
            userService.recordFailedAttempt(user);
            throw new InvalidCredentialsException("Invalid username or password");
        }

        userService.recordSuccessfulLogin(user);
        return issueTokens(user, ipAddress, deviceInfo);
    }

    /** Exchange a valid refresh token for a fresh access token (the refresh token is reused). */
    @Transactional(readOnly = true)
    public AuthResponse refresh(RefreshRequest req) {
        Claims claims = tokenProvider.parse(req.refreshToken());
        if (!JwtTokenProvider.TYPE_REFRESH.equals(tokenProvider.getTokenType(claims))) {
            throw new InvalidTokenException("Not a refresh token");
        }

        UUID sessionId = tokenProvider.getSessionId(claims);
        UserSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new InvalidTokenException("Invalid token"));
        if (!session.isActive()) {
            throw new InvalidTokenException("Session revoked or expired");
        }

        User user = userService.getById(tokenProvider.getUserId(claims));
        String accessToken = tokenProvider.generateAccessToken(user, session.getId());
        return AuthResponse.of(accessToken, req.refreshToken(),
                jwtProps.accessExpirationMs(), UserDto.from(user));
    }

    /** Revoke the caller's current session (logout). */
    @Transactional
    public void logout(UUID sessionId) {
        sessionRepository.findById(sessionId).ifPresent(this::revoke);
    }

    @Transactional(readOnly = true)
    public List<SessionDto> listSessions(UUID userId, UUID currentSessionId) {
        return sessionRepository
                .findByUserIdAndRevokedFalseAndExpiresAtAfterOrderByCreatedAtDesc(userId, Instant.now())
                .stream()
                .map(s -> SessionDto.from(s, s.getId().equals(currentSessionId)))
                .toList();
    }

    /** Revoke a specific session. 404 if it does not exist; 403 if it belongs to another user. */
    @Transactional
    public void revokeSession(UUID userId, UUID sessionId) {
        UserSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found"));
        if (!session.getUser().getId().equals(userId)) {
            throw new AccessDeniedException("Cannot revoke another user's session");
        }
        revoke(session);
    }

    /** Revoke every active session for the user ("logout everywhere"). */
    @Transactional
    public void revokeAllSessions(UUID userId) {
        sessionRepository.revokeAllForUser(userId, Instant.now());
    }

    // --- helpers -----------------------------------------------------------

    private AuthResponse issueTokens(User user, String ipAddress, String deviceInfo) {
        UserSession session = new UserSession();
        session.setUser(user);
        session.setTokenJti(UUID.randomUUID().toString());
        session.setDeviceInfo(truncate(deviceInfo, 255));
        session.setIpAddress(truncate(ipAddress, 45));
        session.setExpiresAt(Instant.now().plusMillis(jwtProps.refreshExpirationMs()));
        session = sessionRepository.save(session);

        String accessToken = tokenProvider.generateAccessToken(user, session.getId());
        String refreshToken = tokenProvider.generateRefreshToken(user, session.getId());
        return AuthResponse.of(accessToken, refreshToken,
                jwtProps.accessExpirationMs(), UserDto.from(user));
    }

    private void revoke(UserSession session) {
        if (!session.isRevoked()) {
            session.setRevoked(true);
            session.setRevokedAt(Instant.now());
            sessionRepository.save(session);
        }
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
